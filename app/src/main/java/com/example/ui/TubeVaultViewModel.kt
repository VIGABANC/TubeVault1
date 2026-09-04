package com.example.ui

import android.app.Application
import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiEngine
import com.example.data.ai.FakeAiEngine
import com.example.data.ai.GeminiRestAiEngine
import com.example.data.detector.MediaCandidate
import com.example.data.detector.MediaDetector
import com.example.data.engine.ExtractorEngine
import com.example.data.engine.RemoteExtractorEngine
import com.example.data.local.BrowserPreferences
import com.example.data.local.DownloadPreferences
import com.example.data.local.DownloadSettings
import com.example.data.local.AiPreferences
import com.example.data.local.AiSettings
import com.example.data.local.TubeVaultDatabase
import com.example.data.model.DownloadState
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.DownloadedVideo
import com.example.data.model.Platform
import com.example.data.model.TaskPriority
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.data.model.AiMediaContext
import com.example.data.model.AiChapter
import com.example.data.model.AiTranscript
import com.example.data.network.YouTubeDownloadApiClient
import com.example.data.repository.VideoRepository
import com.example.data.service.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class AiToolState {
    IDLE,
    PREPARING_CONTEXT,
    GENERATING,
    COMPLETED,
    UNAVAILABLE,
    RATE_LIMITED,
    ERROR
}

enum class LibrarySortOption(val displayName: String) {
    NEWEST("Plus récent"),
    OLDEST("Plus ancien"),
    TITLE_AZ("Titre (A-Z)"),
    TITLE_ZA("Titre (Z-A)"),
    SIZE_DESC("Taille (Plus lourd)")
}

class TubeVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TubeVaultDatabase.getDatabase(application)
    private val repository = VideoRepository(database.videoDao())
    private val vaultRepository = com.example.data.repository.VaultRepository(database.vaultDao())
    private val vaultManager = com.example.data.service.VaultManager(application, vaultRepository)
    val vaultSessionManager = VaultSessionManager.getInstance()
    val isVaultUnlocked: StateFlow<Boolean> = vaultSessionManager.isUnlocked

    val vaultItems: StateFlow<List<com.example.data.local.VaultEntity>> = vaultRepository.allVaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveToVault(video: DownloadedVideo, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = vaultManager.moveToVault(video)
            if (result.isSuccess) {
                repository.deleteVideo(video)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(true) }
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun moveOutFromVault(vaultItem: com.example.data.local.VaultEntity, targetFile: File, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = vaultManager.decryptToDestination(vaultItem, targetFile)
            if (result.isSuccess) {
                val meta = vaultManager.getDecryptedMetadata(vaultItem)
                if (meta != null) {
                    val newVideo = DownloadedVideo(
                        title = meta.title,
                        thumbnailUrl = meta.thumbnailUrl,
                        durationText = meta.durationText,
                        resolution = meta.resolution,
                        filePath = targetFile.absolutePath,
                        fileSizeBytes = targetFile.length(),
                        sourceUrl = meta.sourceUrl,
                        platform = meta.platform,
                        shortSummary = meta.notes,
                        userApprovedTags = meta.tags
                    )
                    repository.saveDownloadedVideo(newVideo)
                }
                vaultManager.deleteVaultItem(vaultItem)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(true) }
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false) }
            }
        }
    }

    fun deleteVaultItem(vaultItem: com.example.data.local.VaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultManager.deleteVaultItem(vaultItem)
        }
    }

    fun getVaultDisplayMetadata(vaultItem: com.example.data.local.VaultEntity): com.example.data.local.VaultDisplayMetadata? {
        return vaultManager.getDecryptedMetadata(vaultItem)
    }

    fun getDecryptedPlaybackFile(vaultItem: com.example.data.local.VaultEntity, onReady: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = vaultManager.getDecryptedPlaybackFile(vaultItem)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onReady(file)
            }
        }
    }

    fun unlockVault() {
        vaultSessionManager.unlockSession()
    }

    fun lockVault() {
        vaultSessionManager.lockSession()
    }
    private val apiClient = YouTubeDownloadApiClient()
    val extractorEngine: ExtractorEngine = RemoteExtractorEngine(apiClient)
    val downloadManager: DownloadManager = DownloadManager.getInstance(application)
    private val browserPreferences = BrowserPreferences.getInstance(application)
    private val downloadPreferences = DownloadPreferences.getInstance(application)

    // AI Preferences & Settings
    val aiPreferences = AiPreferences.getInstance(application)
    val aiSettings: StateFlow<AiSettings> = aiPreferences.settings

    // AI Operation UI States
    private val _currentAiOperationState = MutableStateFlow(AiToolState.IDLE)
    val currentAiOperationState: StateFlow<AiToolState> = _currentAiOperationState.asStateFlow()

    private val _currentAiOperationMessage = MutableStateFlow<String?>(null)
    val currentAiOperationMessage: StateFlow<String?> = _currentAiOperationMessage.asStateFlow()

    private val _currentAiOperationType = MutableStateFlow<String?>(null)
    val currentAiOperationType: StateFlow<String?> = _currentAiOperationType.asStateFlow()

    // Smart Media Detector instance for BrowserScreen
    val mediaDetector = MediaDetector()
    val detectedCandidates: StateFlow<List<MediaCandidate>> = mediaDetector.detectedCandidates

    // Browser settings flow
    val browserSettings = browserPreferences.settings

    fun setBrowserBlockPopups(enabled: Boolean) {
        browserPreferences.setBlockPopups(enabled)
    }

    fun setBrowserBlockAdRedirects(enabled: Boolean) {
        browserPreferences.setBlockAdRedirects(enabled)
    }

    fun setBrowserJavascriptEnabled(enabled: Boolean) {
        browserPreferences.setJavascriptEnabled(enabled)
    }

    fun setTheme(theme: String) {
        browserPreferences.setTheme(theme)
    }

    // Download & Turbo settings flow
    val downloadSettings: StateFlow<DownloadSettings> = downloadPreferences.settings

    fun setQuickDownloadEnabled(enabled: Boolean) {
        downloadPreferences.setQuickDownloadEnabled(enabled)
    }

    fun setDefaultQuality(quality: String) {
        downloadPreferences.setDefaultQuality(quality)
    }

    fun setTurboPartsMode(mode: String) {
        downloadPreferences.setTurboPartsMode(mode)
    }

    fun setMaxConcurrentDownloads(max: Int) {
        downloadPreferences.setMaxConcurrentDownloads(max)
    }

    fun setWifiOnly(enabled: Boolean) {
        downloadPreferences.setWifiOnly(enabled)
    }

    fun setAutoRetry(enabled: Boolean) {
        downloadPreferences.setAutoRetry(enabled)
    }

    fun setDetectClipboardLinks(enabled: Boolean) {
        downloadPreferences.setDetectClipboardLinks(enabled)
    }

    fun setBrowserAutoDetect(enabled: Boolean) {
        downloadPreferences.setBrowserAutoDetect(enabled)
    }

    fun updateDownloadSettings(update: (DownloadSettings) -> DownloadSettings) {
        val newSettings = update(downloadSettings.value)
        downloadPreferences.setQuickDownloadEnabled(newSettings.quickDownloadEnabled)
        downloadPreferences.setDefaultQuality(newSettings.defaultQuality)
        downloadPreferences.setTurboPartsMode(newSettings.turboPartsMode)
        downloadPreferences.setMaxConcurrentDownloads(newSettings.maxConcurrentDownloads)
        downloadPreferences.setWifiOnly(newSettings.wifiOnly)
        downloadPreferences.setAutoRetry(newSettings.autoRetry)
        downloadPreferences.setDetectClipboardLinks(newSettings.detectClipboardLinks)
        downloadPreferences.setBrowserAutoDetect(newSettings.browserAutoDetect)
    }

    fun pauseAll() {
        downloadManager.pauseAll()
    }

    fun resumeAll() {
        downloadManager.resumeAll()
    }

    // Android Share Sheet incoming URL
    private val _sharedIncomingUrl = MutableStateFlow<String?>(null)
    val sharedIncomingUrl: StateFlow<String?> = _sharedIncomingUrl.asStateFlow()

    fun handleIncomingShare(sharedText: String) {
        val urlRegex = Regex("""https?://[^\s]+""")
        val match = urlRegex.find(sharedText)
        val extractedUrl = match?.value ?: sharedText.trim()
        if (extractedUrl.isNotBlank()) {
            _sharedIncomingUrl.value = extractedUrl
            fetchMediaInfo(extractedUrl)
        }
    }

    fun clearSharedIncomingUrl() {
        _sharedIncomingUrl.value = null
    }

    // Download flow state for currently loaded link on Accueil
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // Download Manager queue tasks
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    // Duplicate detection alert state
    private val _duplicateWarningVideo = MutableStateFlow<DownloadedVideo?>(null)
    val duplicateWarningVideo: StateFlow<DownloadedVideo?> = _duplicateWarningVideo.asStateFlow()

    // Library videos from Room Database
    val libraryVideos: StateFlow<List<DownloadedVideo>> = repository.allVideos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Library search, filter and sort states
    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    private val _selectedPlatformFilter = MutableStateFlow<Platform?>(null)
    val selectedPlatformFilter: StateFlow<Platform?> = _selectedPlatformFilter.asStateFlow()

    private val _selectedSortOption = MutableStateFlow(LibrarySortOption.NEWEST)
    val selectedSortOption: StateFlow<LibrarySortOption> = _selectedSortOption.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Filtered & sorted videos flow
    val filteredLibraryVideos: StateFlow<List<DownloadedVideo>> = combine(
        libraryVideos,
        _librarySearchQuery,
        _selectedPlatformFilter,
        _selectedSortOption
    ) { videos, query, platformFilter, sortOption ->
        var list = videos

        if (platformFilter != null) {
            list = list.filter { video ->
                val videoPlatform = Platform.fromId(video.platform)
                videoPlatform == platformFilter
            }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { video ->
                val titleMatch = video.title.lowercase().contains(q)
                val platformMatch = video.platform.lowercase().contains(q)
                
                // approved AI/manual tags match
                val approvedTagsMatch = video.userApprovedTags?.lowercase()?.contains(q) ?: false
                val suggestedTagsMatch = video.aiSuggestedTags?.lowercase()?.contains(q) ?: false
                
                // summaries match
                val shortSummaryMatch = video.shortSummary?.lowercase()?.contains(q) ?: false
                val detailedSummaryMatch = video.detailedSummary?.lowercase()?.contains(q) ?: false
                
                // transcript text match
                val transcriptMatch = if (video.transcriptJson != null) {
                    val transcriptObj = com.example.data.model.AiTranscript.fromJsonString(video.transcriptJson)
                    transcriptObj?.segments?.any { it.text.lowercase().contains(q) } ?: false
                } else false

                titleMatch || platformMatch || approvedTagsMatch || suggestedTagsMatch || shortSummaryMatch || detailedSummaryMatch || transcriptMatch
            }
        }

        when (sortOption) {
            LibrarySortOption.NEWEST -> list.sortedByDescending { it.downloadTimestamp }
            LibrarySortOption.OLDEST -> list.sortedBy { it.downloadTimestamp }
            LibrarySortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
            LibrarySortOption.TITLE_ZA -> list.sortedByDescending { it.title.lowercase() }
            LibrarySortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active full screen video player state
    private val _currentPlayingVideo = MutableStateFlow<DownloadedVideo?>(null)
    val currentPlayingVideo: StateFlow<DownloadedVideo?> = _currentPlayingVideo.asStateFlow()

    init {
        viewModelScope.launch {
            libraryVideos.collect { videos ->
                val settings = aiSettings.value
                if (settings.aiEnabled && (settings.autoTagAfterDownload || settings.autoSummaryAfterDownload)) {
                    val now = System.currentTimeMillis()
                    val eligible = videos.filter { video ->
                        (now - video.downloadTimestamp < 300_000) &&
                        ((video.aiSuggestedTags == null && settings.autoTagAfterDownload) ||
                        (video.shortSummary == null && settings.autoSummaryAfterDownload))
                    }
                    eligible.forEach { video ->
                        launch(Dispatchers.IO) {
                            runAutoAiForVideo(video)
                        }
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _librarySearchQuery.value = query
    }

    fun setPlatformFilter(platform: Platform?) {
        _selectedPlatformFilter.value = platform
    }

    fun setSortOption(option: LibrarySortOption) {
        _selectedSortOption.value = option
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun findDuplicate(url: String): DownloadedVideo? {
        val cleanUrl = url.trim()
        val platform = Platform.detect(cleanUrl)
        val mediaId = apiClient.extractMediaId(cleanUrl, platform)

        return libraryVideos.value.firstOrNull { existing ->
            if (existing.sourceUrl.equals(cleanUrl, ignoreCase = true)) return@firstOrNull true
            if (mediaId != null && existing.sourceUrl.contains(mediaId)) return@firstOrNull true
            false
        }
    }

    fun dismissDuplicateWarning() {
        _duplicateWarningVideo.value = null
    }

    /**
     * Resolves metadata using the modular [ExtractorEngine].
     */
    fun fetchMediaInfo(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            _downloadState.value = DownloadState.Error("Veuillez coller un lien valide.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _downloadState.value = DownloadState.FetchingMetadata
            val result = extractorEngine.resolve(cleanUrl)

            result.fold(
                onSuccess = { metadata ->
                    val defaultFormat = metadata.formats.firstOrNull() ?: MediaFormat(
                        quality = "Standard HD",
                        downloadUrl = cleanUrl
                    )
                    _downloadState.value = DownloadState.MetadataLoaded(
                        metadata = metadata,
                        selectedFormat = defaultFormat
                    )

                    val duplicate = findDuplicate(cleanUrl)
                    if (duplicate != null) {
                        _duplicateWarningVideo.value = duplicate
                    }
                },
                onFailure = { error ->
                    _downloadState.value = DownloadState.Error(
                        error.localizedMessage ?: "Impossible de récupérer cette vidéo, réessaie ou vérifie le lien"
                    )
                }
            )
        }
    }

    fun selectFormat(format: MediaFormat) {
        val currentState = _downloadState.value
        if (currentState is DownloadState.MetadataLoaded) {
            _downloadState.value = currentState.copy(selectedFormat = format)
        }
    }

    fun enqueueCurrentDownload(forceIgnoreDuplicate: Boolean = false) {
        val state = _downloadState.value
        if (state !is DownloadState.MetadataLoaded) return

        if (!forceIgnoreDuplicate) {
            val duplicate = findDuplicate(state.metadata.sourceUrl)
            if (duplicate != null) {
                _duplicateWarningVideo.value = duplicate
                return
            }
        }

        _duplicateWarningVideo.value = null
        downloadManager.enqueue(state.metadata, state.selectedFormat)
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Quick Download: directly enqueues a video with user's preferred quality setting.
     */
    fun quickDownload(metadata: MediaInfo, priority: TaskPriority = TaskPriority.HIGH) {
        val targetQuality = downloadSettings.value.defaultQuality
        val chosenFormat = pickFormatForQuality(metadata.formats, targetQuality)
            ?: metadata.formats.firstOrNull()
            ?: MediaFormat(quality = "Standard", downloadUrl = metadata.sourceUrl)

        downloadManager.enqueue(metadata, chosenFormat, priority)
    }

    /**
     * Batch enqueues multiple items (e.g. from BatchDownloadSheet).
     */
    fun enqueueBatch(
        items: List<Pair<MediaInfo, MediaFormat>>,
        priority: TaskPriority = TaskPriority.NORMAL
    ): List<String> {
        return downloadManager.enqueueBatch(items, priority)
    }

    fun pickFormatForQuality(formats: List<MediaFormat>, preference: String): MediaFormat? {
        if (formats.isEmpty()) return null
        return when (preference) {
            "Audio" -> formats.firstOrNull { it.quality.contains("Audio", ignoreCase = true) || it.quality.contains("MP3", ignoreCase = true) }
                ?: formats.lastOrNull()
            "1080p" -> formats.firstOrNull { it.quality.contains("1080") }
                ?: formats.firstOrNull { it.quality.contains("720") }
                ?: formats.firstOrNull()
            "720p" -> formats.firstOrNull { it.quality.contains("720") }
                ?: formats.firstOrNull { it.quality.contains("480") }
                ?: formats.firstOrNull()
            "480p" -> formats.firstOrNull { it.quality.contains("480") }
                ?: formats.firstOrNull { it.quality.contains("360") }
                ?: formats.firstOrNull()
            "Best" -> formats.firstOrNull()
            else -> formats.firstOrNull { it.quality.contains("720") || it.quality.contains("1080") }
                ?: formats.firstOrNull()
        }
    }

    fun startDownload(context: Context) {
        enqueueCurrentDownload(forceIgnoreDuplicate = true)
    }

    // DownloadManager Queue Pro interactions
    fun pauseDownload(taskId: String) = downloadManager.pause(taskId)
    fun resumeDownload(taskId: String) = downloadManager.resume(taskId)
    fun cancelDownload(taskId: String) = downloadManager.cancel(taskId)
    fun clearCompletedDownloads() = downloadManager.clearCompleted()
    fun pauseAllDownloads() = downloadManager.pauseAll()
    fun resumeAllDownloads() = downloadManager.resumeAll()
    fun retryFailedDownloads() = downloadManager.retryFailed()
    fun cancelAllDownloads() = downloadManager.cancelAll()

    fun deleteDownloadedVideo(video: DownloadedVideo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_currentPlayingVideo.value?.id == video.id) {
                _currentPlayingVideo.value = null
            }
            repository.deleteVideo(video)
        }
    }

    fun openPlayer(video: DownloadedVideo) {
        _currentPlayingVideo.value = video
    }

    fun closePlayer() {
        _currentPlayingVideo.value = null
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun getFreeStorageBytes(): Long {
        return try {
            val stat = StatFs(getApplication<Application>().filesDir.path)
            stat.availableBytes
        } catch (_: Exception) {
            1024L * 1024L * 1024L * 5L // 5GB fallback
        }
    }

    // --- AI Operation Helper Methods ---

    fun getAiEngine(forceLocalHeuristic: Boolean = false): AiEngine {
        if (forceLocalHeuristic) {
            return FakeAiEngine()
        }
        val key = com.example.BuildConfig.GEMINI_API_KEY
        return if (!key.isNullOrBlank() && key != "null" && key != "GEMINI_API_KEY") {
            GeminiRestAiEngine(key)
        } else {
            FakeAiEngine()
        }
    }

    fun prepareContext(video: DownloadedVideo): AiMediaContext {
        val durationSecs = try {
            val parts = video.durationText.split(":")
            if (parts.size == 3) {
                parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
            } else if (parts.size == 2) {
                parts[0].toLong() * 60 + parts[1].toLong()
            } else {
                300L
            }
        } catch (_: Exception) {
            300L
        }

        val transcriptObj = AiTranscript.fromJsonString(video.transcriptJson)
        val fullTranscriptText = transcriptObj?.segments?.joinToString(" ") { it.text }

        return AiMediaContext(
            mediaId = video.id.toString(),
            title = video.title,
            creator = "Unknown",
            platform = video.platform,
            description = "Downloaded media on TubeVault from platform ${video.platform}.",
            duration = durationSecs,
            existingTags = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
            transcript = fullTranscriptText,
            subtitleLanguage = transcriptObj?.language
        )
    }

    fun getFingerprint(context: AiMediaContext): String {
        return "${context.title}:${context.description}:${context.transcript ?: ""}".hashCode().toString()
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val activeNetwork = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                activeNetwork != null && activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Exception) {
            true // fallback to true to not block in test/emulator environments
        }
    }

    fun runAiOperation(video: DownloadedVideo, operation: String, forceLocalHeuristic: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = aiSettings.value
            if (!settings.aiEnabled) {
                _currentAiOperationState.value = AiToolState.UNAVAILABLE
                _currentAiOperationMessage.value = "Les fonctionnalités IA sont désactivées dans les paramètres."
                return@launch
            }

            val engine = getAiEngine(forceLocalHeuristic)

            // Privacy / Vault check:
            if (video.isPrivate && engine.providerSource == com.example.data.ai.AiProviderSource.CLOUD_GEMINI && !settings.allowCloudAiForPrivateContent) {
                _currentAiOperationState.value = AiToolState.ERROR
                _currentAiOperationMessage.value = "Le contenu privé (Vault) est protégé. L'envoi cloud est désactivé par défaut. Utilisez l'heuristique locale ou activez l'option dans les paramètres."
                return@launch
            }

            // Cloud disclosure check
            if (engine.providerSource == com.example.data.ai.AiProviderSource.CLOUD_GEMINI && !settings.cloudAiDisclosureAccepted) {
                _currentAiOperationState.value = AiToolState.ERROR
                _currentAiOperationMessage.value = "Avertissement de confidentialité requis avant d'envoyer les métadonnées vers l'IA cloud."
                return@launch
            }

            // WiFi check:
            if (settings.wifiOnlyForAi && !isWifiConnected() && engine.providerSource == com.example.data.ai.AiProviderSource.CLOUD_GEMINI) {
                _currentAiOperationState.value = AiToolState.ERROR
                _currentAiOperationMessage.value = "Connexion Wi-Fi requise pour l'IA dans les paramètres."
                return@launch
            }

            _currentAiOperationType.value = operation
            _currentAiOperationState.value = AiToolState.PREPARING_CONTEXT
            _currentAiOperationMessage.value = "Préparation du contexte média..."

            try {
                val context = prepareContext(video)
                val fingerprint = getFingerprint(context)

                _currentAiOperationState.value = AiToolState.GENERATING
                val sourceName = if (engine.providerSource == com.example.data.ai.AiProviderSource.CLOUD_GEMINI) "Cloud Gemini" else "Heuristique locale"
                _currentAiOperationMessage.value = "Génération via $sourceName en cours..."

                when (operation) {
                    "tags" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "tags")
                        val tagsStr = if (cached != null) {
                            cached
                        } else {
                            val res = engine.generateTags(context)
                            val joined = res.value.joinToString(",")
                            repository.saveCachedResult(video.id.toString(), fingerprint, "tags", res.modelIdentifier, joined)
                            joined
                        }
                        val updated = video.copy(aiSuggestedTags = tagsStr)
                        repository.updateVideo(updated)
                    }
                    "summary_short" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "summary_short")
                        val summaryText = if (cached != null) {
                            cached
                        } else {
                            val res = engine.summarize(context, "short")
                            repository.saveCachedResult(video.id.toString(), fingerprint, "summary_short", res.modelIdentifier, res.value)
                            res.value
                        }
                        val updated = video.copy(
                            shortSummary = summaryText,
                            summarySource = if (context.transcript.isNullOrBlank()) "metadata" else "transcript"
                        )
                        repository.updateVideo(updated)
                    }
                    "summary_detailed" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "summary_detailed")
                        val summaryText = if (cached != null) {
                            cached
                        } else {
                            val res = engine.summarize(context, "detailed")
                            repository.saveCachedResult(video.id.toString(), fingerprint, "summary_detailed", res.modelIdentifier, res.value)
                            res.value
                        }
                        val updated = video.copy(
                            detailedSummary = summaryText,
                            summarySource = if (context.transcript.isNullOrBlank()) "metadata" else "transcript"
                        )
                        repository.updateVideo(updated)
                    }
                    "suggested_title" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "suggested_title")
                        val titleText = if (cached != null) {
                            cached
                        } else {
                            val res = engine.suggestTitle(context)
                            repository.saveCachedResult(video.id.toString(), fingerprint, "suggested_title", res.modelIdentifier, res.value)
                            res.value
                        }
                        val updated = video.copy(suggestedTitle = titleText)
                        repository.updateVideo(updated)
                    }
                    "classify" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "classify")
                        val resultObj = if (cached != null) {
                            JSONObject(cached)
                        } else {
                            val res = engine.classify(context)
                            val obj = JSONObject().apply {
                                put("category", res.value.first)
                                put("topics", JSONArray().apply { res.value.second.forEach { put(it) } })
                            }
                            repository.saveCachedResult(video.id.toString(), fingerprint, "classify", res.modelIdentifier, obj.toString())
                            obj
                        }
                        val category = resultObj.getString("category")
                        val topicsArr = resultObj.getJSONArray("topics")
                        val topicsList = mutableListOf<String>()
                        for (i in 0 until topicsArr.length()) {
                            topicsList.add(topicsArr.getString(i))
                        }
                        val updated = video.copy(
                            primaryCategory = category,
                            topics = topicsList.joinToString(",")
                        )
                        repository.updateVideo(updated)
                    }
                    "chapters" -> {
                        val cached = repository.getCachedResult(video.id.toString(), fingerprint, "chapters")
                        val chaptersJson = if (cached != null) {
                            cached
                        } else {
                            val res = engine.generateChapters(context)
                            val json = AiChapter.listToJsonString(res.value)
                            repository.saveCachedResult(video.id.toString(), fingerprint, "chapters", res.modelIdentifier, json)
                            json
                        }
                        val updated = video.copy(aiChaptersJson = chaptersJson)
                        repository.updateVideo(updated)
                    }
                }

                _currentAiOperationState.value = AiToolState.COMPLETED
                _currentAiOperationMessage.value = "Opération IA terminée via $sourceName !"
            } catch (e: Exception) {
                Log.e("TubeVaultViewModel", "AI operation failed", e)
                _currentAiOperationState.value = AiToolState.ERROR
                _currentAiOperationMessage.value = "Échec (${e.localizedMessage}). Vous pouvez réessayer avec l'heuristique locale."
            }
        }
    }

    fun clearAiOperationState() {
        _currentAiOperationState.value = AiToolState.IDLE
        _currentAiOperationMessage.value = null
        _currentAiOperationType.value = null
    }

    private suspend fun runAutoAiForVideo(video: DownloadedVideo) {
        val settings = aiSettings.value
        if (settings.wifiOnlyForAi && !isWifiConnected()) {
            return
        }

        var updated = video
        val context = prepareContext(video)
        val fingerprint = getFingerprint(context)
        val engine = getAiEngine()

        if (settings.autoTagAfterDownload && updated.aiSuggestedTags == null) {
            try {
                val tagsResult = engine.generateTags(context)
                updated = updated.copy(aiSuggestedTags = tagsResult.value.joinToString(","))
            } catch (_: Exception) {}
        }

        if (settings.autoSummaryAfterDownload && updated.shortSummary == null && updated.detailedSummary == null) {
            try {
                val summaryResult = engine.summarize(context, settings.preferredSummaryLength)
                updated = if (settings.preferredSummaryLength == "short") {
                    updated.copy(shortSummary = summaryResult.value, summarySource = if (context.transcript.isNullOrBlank()) "metadata" else "transcript")
                } else {
                    updated.copy(detailedSummary = summaryResult.value, summarySource = if (context.transcript.isNullOrBlank()) "metadata" else "transcript")
                }
            } catch (_: Exception) {}
        }

        if (updated != video) {
            repository.updateVideo(updated)
        }
    }

    // --- User approvals & tags management ---

    fun approveTag(video: DownloadedVideo, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val approved = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            if (!approved.contains(tag)) {
                approved.add(tag)
            }
            
            val suggested = video.aiSuggestedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != tag } ?: emptyList()
            
            val updated = video.copy(
                userApprovedTags = approved.joinToString(","),
                aiSuggestedTags = if (suggested.isEmpty()) null else suggested.joinToString(",")
            )
            repository.updateVideo(updated)
        }
    }

    fun approveAllTags(video: DownloadedVideo) {
        viewModelScope.launch(Dispatchers.IO) {
            val approved = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            val suggested = video.aiSuggestedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            
            suggested.forEach { tag ->
                if (!approved.contains(tag)) {
                    approved.add(tag)
                }
            }
            
            val updated = video.copy(
                userApprovedTags = approved.joinToString(","),
                aiSuggestedTags = null
            )
            repository.updateVideo(updated)
        }
    }

    fun removeApprovedTag(video: DownloadedVideo, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val approved = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != tag } ?: emptyList()
            val updated = video.copy(
                userApprovedTags = if (approved.isEmpty()) null else approved.joinToString(",")
            )
            repository.updateVideo(updated)
        }
    }

    fun addManualTag(video: DownloadedVideo, tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val approved = video.userApprovedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            val cleanTag = tag.trim()
            if (!approved.contains(cleanTag)) {
                approved.add(cleanTag)
            }
            val updated = video.copy(
                userApprovedTags = approved.joinToString(",")
            )
            repository.updateVideo(updated)
        }
    }

    fun removeSuggestedTag(video: DownloadedVideo, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val suggested = video.aiSuggestedTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != tag } ?: emptyList()
            val updated = video.copy(
                aiSuggestedTags = if (suggested.isEmpty()) null else suggested.joinToString(",")
            )
            repository.updateVideo(updated)
        }
    }

    fun applySuggestedTitle(video: DownloadedVideo) {
        val suggested = video.suggestedTitle ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = video.copy(
                title = suggested,
                suggestedTitle = null
            )
            repository.updateVideo(updated)
        }
    }

    fun rejectSuggestedTitle(video: DownloadedVideo) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = video.copy(
                suggestedTitle = null
            )
            repository.updateVideo(updated)
        }
    }

    fun applySuggestedCategory(video: DownloadedVideo, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = video.copy(
                primaryCategory = category
            )
            repository.updateVideo(updated)
        }
    }

    fun importTranscript(video: DownloadedVideo, transcriptSrtOrVttContent: String, language: String = "fr") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = com.example.util.TranscriptParser.parse(transcriptSrtOrVttContent, language)
                val updated = video.copy(
                    transcriptJson = parsed.toJsonString()
                )
                repository.updateVideo(updated)
            } catch (e: Exception) {
                Log.e("TubeVaultViewModel", "Failed to parse and import transcript", e)
            }
        }
    }

    fun setPrivateMode(video: DownloadedVideo, isPrivate: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = video.copy(isPrivate = isPrivate)
            repository.updateVideo(updated)
        }
    }

    // --- UI Settings Updates ---

    fun setAiEnabled(enabled: Boolean) = aiPreferences.setAiEnabled(enabled)
    fun setAutoTagAfterDownload(enabled: Boolean) = aiPreferences.setAutoTagAfterDownload(enabled)
    fun setAutoSummaryAfterDownload(enabled: Boolean) = aiPreferences.setAutoSummaryAfterDownload(enabled)
    fun setWifiOnlyForAi(enabled: Boolean) = aiPreferences.setWifiOnlyForAi(enabled)
    fun setPreferredSummaryLength(length: String) = aiPreferences.setPreferredSummaryLength(length)
    fun setAllowCloudAiForPrivateContent(enabled: Boolean) = aiPreferences.setAllowCloudAiForPrivateContent(enabled)
    fun setCloudAiDisclosureAccepted(enabled: Boolean) = aiPreferences.setCloudAiDisclosureAccepted(enabled)

    fun clearAiCache() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCache()
        }
    }
}
