package com.example.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.engine.ExtractorEngine
import com.example.data.engine.RemoteExtractorEngine
import com.example.data.local.DownloadPreferences
import com.example.data.local.DownloadTaskEntity
import com.example.data.local.TubeVaultDatabase
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.DownloadedVideo
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.data.model.Platform
import com.example.data.model.TaskPriority
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Turbo Download & Media Queue Manager with Room persistence and safe resume.
 */
class DownloadManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = TubeVaultDatabase.getDatabase(appContext)
    private val repository = VideoRepository(database.videoDao())
    private val downloadTaskDao = database.downloadTaskDao()
    private val downloadPreferences = DownloadPreferences.getInstance(appContext)
    private val extractorEngine: ExtractorEngine = RemoteExtractorEngine()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, MutableList<Call>>()
    private val partialFiles = ConcurrentHashMap<String, File>()

    // Connectivity listener for automatic recovery
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isNetworkAvailable = true

    init {
        registerNetworkCallback()
        loadPersistedQueue()
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun loadPersistedQueue() {
        scope.launch {
            try {
                val persistedEntities = downloadTaskDao.getAllTasks()
                if (persistedEntities.isNotEmpty()) {
                    val loadedTasks = persistedEntities.map { it.toDownloadTask() }
                    _tasks.update { current ->
                        val currentIds = current.map { it.id }.toSet()
                        val toAdd = loadedTasks.filter { it.id !in currentIds }
                        current + toAdd
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun persistTask(
        task: DownloadTask,
        etag: String? = null,
        lastModified: String? = null,
        tempFilePath: String? = null
    ) {
        scope.launch {
            try {
                downloadTaskDao.insertOrUpdate(
                    DownloadTaskEntity.fromTask(task, etag, lastModified, tempFilePath)
                )
            } catch (_: Exception) {}
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val activeNet = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isNetworkAvailable = true
                    val wifi = isWifiConnected()
                    scope.launch {
                        _tasks.update { list ->
                            list.map { task ->
                                if (task.status == DownloadStatus.WAITING_FOR_NETWORK) {
                                    task.copy(status = DownloadStatus.QUEUED, speedText = "Réseau rétabli, attente...")
                                } else if (wifi && task.status == DownloadStatus.WAITING_FOR_WIFI) {
                                    task.copy(status = DownloadStatus.QUEUED, speedText = "Wi-Fi connecté, attente...")
                                } else task
                            }
                        }
                        processQueue()
                    }
                }

                override fun onLost(network: Network) {
                    isNetworkAvailable = false
                    scope.launch {
                        _tasks.update { list ->
                            list.map { task ->
                                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.RETRYING) {
                                    task.copy(status = DownloadStatus.WAITING_FOR_NETWORK, speedText = "En attente du réseau")
                                } else task
                            }
                        }
                        activeCalls.values.forEach { calls -> calls.forEach { try { it.cancel() } catch (_: Exception) {} } }
                    }
                }
            })
        } catch (_: Exception) {}
    }

    /**
     * Enqueues an individual video download task.
     */
    fun enqueue(
        metadata: MediaInfo,
        format: MediaFormat,
        priority: TaskPriority = TaskPriority.NORMAL
    ): String {
        val taskId = UUID.randomUUID().toString()
        val newTask = DownloadTask(
            id = taskId,
            metadata = metadata,
            selectedFormat = format,
            platform = metadata.platform,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            priority = priority
        )

        _tasks.update { currentList ->
            listOf(newTask) + currentList
        }

        persistTask(newTask)
        DownloadForegroundService.start(appContext)
        processQueue()
        return taskId
    }

    /**
     * Enqueues a batch of videos.
     */
    fun enqueueBatch(
        items: List<Pair<MediaInfo, MediaFormat>>,
        priority: TaskPriority = TaskPriority.NORMAL
    ): List<String> {
        val createdIds = mutableListOf<String>()
        val newTasks = items.map { (meta, format) ->
            val taskId = UUID.randomUUID().toString()
            createdIds.add(taskId)
            val t = DownloadTask(
                id = taskId,
                metadata = meta,
                selectedFormat = format,
                platform = meta.platform,
                status = DownloadStatus.QUEUED,
                priority = priority
            )
            persistTask(t)
            t
        }

        _tasks.update { current ->
            newTasks + current
        }

        DownloadForegroundService.start(appContext)
        processQueue()
        return createdIds
    }

    /**
     * Pauses an ongoing task, safely preserving downloaded bytes.
     */
    fun pause(taskId: String) {
        val job = activeJobs.remove(taskId)
        val calls = activeCalls.remove(taskId)
        calls?.forEach { try { it.cancel() } catch (_: Exception) {} }
        job?.cancel()

        var pausedTask: DownloadTask? = null
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId && task.status != DownloadStatus.COMPLETED) {
                    val updated = task.copy(status = DownloadStatus.PAUSED, speedText = "En pause", etaText = "")
                    pausedTask = updated
                    updated
                } else task
            }
        }

        pausedTask?.let { persistTask(it) }
        updateServiceNotification()
        processQueue()
    }

    /**
     * Pauses all downloading and queued tasks.
     */
    fun pauseAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        activeCalls.values.forEach { calls -> calls.forEach { try { it.cancel() } catch (_: Exception) {} } }
        activeCalls.clear()

        _tasks.update { list ->
            list.map { task ->
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                    val updated = task.copy(status = DownloadStatus.PAUSED, speedText = "En pause", etaText = "")
                    persistTask(updated)
                    updated
                } else task
            }
        }

        updateServiceNotification()
    }

    /**
     * Resumes a paused or failed task.
     */
    fun resume(taskId: String) {
        var resumedTask: DownloadTask? = null
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId && (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED)) {
                    val updated = task.copy(status = DownloadStatus.QUEUED, errorMessage = null, speedText = "En file d'attente...", retryCount = 0)
                    resumedTask = updated
                    updated
                } else task
            }
        }

        resumedTask?.let { persistTask(it) }
        DownloadForegroundService.start(appContext)
        processQueue()
    }

    /**
     * Resumes all paused and failed tasks.
     */
    fun resumeAll() {
        _tasks.update { list ->
            list.map { task ->
                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED || task.status == DownloadStatus.WAITING_FOR_NETWORK || task.status == DownloadStatus.WAITING_FOR_WIFI) {
                    val updated = task.copy(status = DownloadStatus.QUEUED, errorMessage = null, speedText = "En file d'attente...", retryCount = 0)
                    persistTask(updated)
                    updated
                } else task
            }
        }

        DownloadForegroundService.start(appContext)
        processQueue()
    }

    /**
     * Retries failed downloads with expired URL recovery.
     */
    fun retryFailed() {
        _tasks.update { list ->
            list.map { task ->
                if (task.status == DownloadStatus.FAILED) {
                    val updated = task.copy(status = DownloadStatus.QUEUED, errorMessage = null, speedText = "Nouvel essai...", retryCount = 0)
                    persistTask(updated)
                    updated
                } else task
            }
        }

        DownloadForegroundService.start(appContext)
        processQueue()
    }

    /**
     * Cancels a task, deleting any partial file and removing from database.
     */
    fun cancel(taskId: String) {
        val job = activeJobs.remove(taskId)
        val calls = activeCalls.remove(taskId)
        calls?.forEach { try { it.cancel() } catch (_: Exception) {} }
        job?.cancel()

        val partial = partialFiles.remove(taskId)
        if (partial != null) {
            if (partial.exists()) {
                try { partial.delete() } catch (_: Exception) {}
            }
            try {
                partial.parentFile?.listFiles()?.filter { it.name.startsWith(partial.name) }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }

        _tasks.update { list ->
            list.filterNot { it.id == taskId }
        }

        scope.launch {
            try { downloadTaskDao.deleteTaskById(taskId) } catch (_: Exception) {}
        }

        updateServiceNotification()
        processQueue()
    }

    /**
     * Clears completed tasks from list and database.
     */
    fun clearCompleted() {
        _tasks.update { list ->
            list.filter { it.status != DownloadStatus.COMPLETED }
        }
        scope.launch {
            try { downloadTaskDao.deleteCompletedTasks() } catch (_: Exception) {}
        }
        updateServiceNotification()
    }

    /**
     * Cancels all tasks.
     */
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        activeCalls.values.forEach { calls -> calls.forEach { try { it.cancel() } catch (_: Exception) {} } }
        activeCalls.clear()

        partialFiles.values.forEach { file ->
            if (file.exists()) try { file.delete() } catch (_: Exception) {}
            try {
                file.parentFile?.listFiles()?.filter { it.name.startsWith(file.name) }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        partialFiles.clear()

        _tasks.value = emptyList()
        scope.launch {
            try { downloadTaskDao.deleteAllTasks() } catch (_: Exception) {}
        }
        updateServiceNotification()
    }

    @Synchronized
    private fun processQueue() {
        val currentTasks = _tasks.value
        val wifiOnly = downloadPreferences.settings.value.wifiOnly
        if (wifiOnly && !isWifiConnected()) {
            _tasks.update { list ->
                list.map { task ->
                    if (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING) {
                        val updated = task.copy(status = DownloadStatus.WAITING_FOR_WIFI, speedText = "En attente du Wi-Fi")
                        persistTask(updated)
                        updated
                    } else task
                }
            }
            return
        }

        val maxConcurrent = downloadPreferences.settings.value.maxConcurrentDownloads.coerceIn(1, 5)
        val activeCount = currentTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RESOLVING }

        if (activeCount < maxConcurrent) {
            val queued = currentTasks
                .filter { it.status == DownloadStatus.QUEUED }
                .sortedWith(compareByDescending<DownloadTask> { it.priority.ordinal }.thenBy { it.createdAt })

            val slotsAvailable = maxConcurrent - activeCount
            queued.take(slotsAvailable).forEach { task ->
                startTaskExecution(task.id)
            }
        }

        val hasPending = currentTasks.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RESOLVING
        }
        if (!hasPending) {
            DownloadForegroundService.onQueueIdle(appContext)
        }
    }

    private fun startTaskExecution(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) {
                    val updated = task.copy(status = DownloadStatus.DOWNLOADING, speedText = "Connexion...")
                    persistTask(updated)
                    updated
                } else task
            }
        }

        updateServiceNotification()

        val job = scope.launch {
            val task = _tasks.value.firstOrNull { it.id == taskId } ?: return@launch

            try {
                executeTurboDownload(task)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                handleDownloadError(task, e)
            } finally {
                activeJobs.remove(taskId)
                activeCalls.remove(taskId)
                updateServiceNotification()
                processQueue()
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * Executes download with Range detection, Turbo multi-part segmentation, and Safe Resume.
     */
    private suspend fun executeTurboDownload(task: DownloadTask) {
        val videosDir = File(appContext.filesDir, "downloaded_videos").apply {
            if (!exists()) mkdirs()
        }

        val cleanTitle = task.metadata.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(30)
        val ext = task.selectedFormat.extension.ifBlank { "mp4" }
        val targetFile = partialFiles.getOrPut(task.id) {
            File(videosDir, "${cleanTitle}_${task.id.take(8)}.$ext")
        }

        val downloadUrl = task.selectedFormat.downloadUrl

        // Probe Range support and content length
        val serverCap = probeServerCapability(downloadUrl)
        val turboMode = downloadPreferences.settings.value.turboPartsMode
        val partCount = if (serverCap.rangeSupported) determinePartCount(turboMode, serverCap.contentLength) else 1

        if (partCount > 1 && serverCap.contentLength > 2 * 1024 * 1024) {
            try {
                executeMultiPartDownload(
                    task = task,
                    targetFile = targetFile,
                    url = downloadUrl,
                    totalBytes = serverCap.contentLength,
                    partCount = partCount,
                    etag = serverCap.etag,
                    lastModified = serverCap.lastModified
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("TubeVaultTurbo", "Multipart download failed, falling back to single stream: ${e.message}")
                // Clean up any remaining partial segment files
                (0 until 8).forEach { idx ->
                    File(targetFile.parentFile, "${targetFile.name}.part$idx").takeIf { it.exists() }?.delete()
                }
                executeSingleStreamDownload(
                    task = task,
                    targetFile = targetFile,
                    url = downloadUrl,
                    rangeSupported = serverCap.rangeSupported,
                    etag = serverCap.etag,
                    lastModified = serverCap.lastModified
                )
            }
        } else {
            executeSingleStreamDownload(
                task = task,
                targetFile = targetFile,
                url = downloadUrl,
                rangeSupported = serverCap.rangeSupported,
                etag = serverCap.etag,
                lastModified = serverCap.lastModified
            )
        }

        // Finalize completed download into public MediaStore storage (Download/TubeVault)
        val finalFileSize = targetFile.length().coerceAtLeast(task.totalBytes)
        val publishResult = com.example.data.storage.TubeVaultStorageManager.publishVerifiedFile(
            context = appContext,
            sourceFile = targetFile,
            title = task.metadata.title,
            quality = task.selectedFormat.quality,
            extension = ext
        )

        val (finalContentUri, finalDisplayPath) = if (publishResult.isSuccess) {
            val pub = publishResult.getOrThrow()
            // Clean up temporary assembly file now that MediaStore entry is populated
            try { targetFile.delete() } catch (_: Exception) {}
            Pair(pub.contentUri.toString(), pub.displayPath)
        } else {
            Pair(null, targetFile.absolutePath)
        }

        val videoEntity = DownloadedVideo(
            title = task.metadata.title,
            thumbnailUrl = task.metadata.thumbnailUrl,
            durationText = task.metadata.durationText,
            resolution = task.selectedFormat.quality,
            filePath = finalDisplayPath,
            contentUri = finalContentUri,
            fileSizeBytes = finalFileSize,
            sourceUrl = task.metadata.sourceUrl,
            platform = task.platform.id,
            downloadTimestamp = System.currentTimeMillis()
        )

        val savedId = repository.saveDownloadedVideo(videoEntity)
        val finalizedVideo = videoEntity.copy(id = savedId)

        partialFiles.remove(task.id)

        val completedTask = task.copy(
            status = DownloadStatus.COMPLETED,
            progress = 1f,
            bytesDownloaded = finalFileSize,
            totalBytes = finalFileSize,
            speedText = "Terminé",
            etaText = "",
            localFilePath = finalDisplayPath,
            savedVideo = finalizedVideo
        )

        _tasks.update { list ->
            list.map { t -> if (t.id == task.id) completedTask else t }
        }

        persistTask(completedTask)
        updateServiceNotification()
    }

    private suspend fun executeSingleStreamDownload(
        task: DownloadTask,
        targetFile: File,
        url: String,
        rangeSupported: Boolean,
        etag: String?,
        lastModified: String?
    ) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0 && rangeSupported) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            if (!etag.isNullOrBlank()) {
                requestBuilder.addHeader("If-Range", etag)
            } else if (!lastModified.isNullOrBlank()) {
                requestBuilder.addHeader("If-Range", lastModified)
            }
        }

        val request = requestBuilder.build()
        val call = okHttpClient.newCall(request)
        activeCalls.getOrPut(task.id) { mutableListOf() }.add(call)

        val response = call.execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw DownloadHttpException(code, "HTTP $code: ${response.message}")
        }

        val body = response.body ?: throw Exception("Corps de réponse vide")
        val isPartial = response.code == 206

        // Safe Resume: If range requested but server returned 200 (not 206), restart from zero
        if (!isPartial && existingBytes > 0) {
            existingBytes = 0L
            tempFile.delete()
        }

        val responseEtag = response.header("ETag") ?: etag
        val responseLastModified = response.header("Last-Modified") ?: lastModified
        val totalLength = if (isPartial) existingBytes + body.contentLength() else body.contentLength()

        var bytesDownloaded = existingBytes
        var lastUpdateTime = System.currentTimeMillis()
        var lastSavedTime = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        body.byteStream().use { input ->
            FileOutputStream(tempFile, isPartial).use { output ->
                val buffer = ByteArray(16384)
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesDownloaded += read
                    bytesSinceLastUpdate += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUpdateTime
                    if (elapsed >= 300) {
                        val speedBytesPerSec = if (elapsed > 0) (bytesSinceLastUpdate * 1000) / elapsed else 0L
                        val speedText = formatSpeed(speedBytesPerSec)
                        val etaText = formatEta(bytesDownloaded, totalLength, speedBytesPerSec)
                        val progress = if (totalLength > 0) {
                            (bytesDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                        } else 0.5f

                        val updated = task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = progress,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalLength,
                            speedText = speedText,
                            etaText = etaText
                        )
                        _tasks.update { list ->
                            list.map { t -> if (t.id == task.id) updated else t }
                        }

                        if (now - lastSavedTime >= 2000) {
                            persistTask(updated, responseEtag, responseLastModified, tempFile.absolutePath)
                            lastSavedTime = now
                        }

                        lastUpdateTime = now
                        bytesSinceLastUpdate = 0L
                        updateServiceNotification()
                    }
                }
                output.flush()
            }
        }

        // Verify size before renaming to target file
        if (totalLength > 0 && tempFile.length() != totalLength) {
            tempFile.delete()
            throw IllegalStateException("Taille de fichier incomplète : attendu $totalLength octets, reçu ${tempFile.length()} octets")
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    private suspend fun executeMultiPartDownload(
        task: DownloadTask,
        targetFile: File,
        url: String,
        totalBytes: Long,
        partCount: Int,
        etag: String?,
        lastModified: String?
    ) {
        val partSize = totalBytes / partCount
        val tempParts = (0 until partCount).map { index ->
            File(targetFile.parentFile, "${targetFile.name}.part$index")
        }

        val totalBytesDownloadedCounter = AtomicLong(0L)
        var lastSnapshotBytes = 0L
        var lastUpdateTime = System.currentTimeMillis()
        var lastSavedTime = System.currentTimeMillis()

        val progressJob = scope.launch {
            while (activeJobs.containsKey(task.id)) {
                delay(300)
                val sumDownloaded = totalBytesDownloadedCounter.get()
                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateTime
                if (elapsed >= 300) {
                    val delta = sumDownloaded - lastSnapshotBytes
                    val currentSpeed = if (elapsed > 0) (delta * 1000) / elapsed else 0L
                    val speedText = "Turbo (${partCount}x) • " + formatSpeed(currentSpeed)
                    val etaText = formatEta(sumDownloaded, totalBytes, currentSpeed)
                    val progress = (sumDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                    val updated = task.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = progress,
                        bytesDownloaded = sumDownloaded,
                        totalBytes = totalBytes,
                        speedText = speedText,
                        etaText = etaText,
                        isSegmented = true,
                        segmentsCount = partCount
                    )

                    _tasks.update { list ->
                        list.map { t -> if (t.id == task.id) updated else t }
                    }

                    if (now - lastSavedTime >= 2000) {
                        persistTask(updated, etag, lastModified)
                        lastSavedTime = now
                    }

                    lastUpdateTime = now
                    lastSnapshotBytes = sumDownloaded
                    updateServiceNotification()
                }
            }
        }

        try {
            val partJobs = (0 until partCount).map { index ->
                scope.async(Dispatchers.IO) {
                    val start = index * partSize
                    val end = if (index == partCount - 1) totalBytes - 1 else (start + partSize - 1)
                    val partFile = tempParts[index]
                    val expectedPartSize = end - start + 1

                    val partExisting = if (partFile.exists()) partFile.length() else 0L
                    if (partExisting >= expectedPartSize) {
                        totalBytesDownloadedCounter.addAndGet(expectedPartSize)
                        return@async
                    }

                    val actualStart = start + partExisting
                    val isResuming = partExisting > 0L

                    val requestBuilder = Request.Builder()
                        .url(url)
                        .addHeader("Range", "bytes=$actualStart-$end")

                    if (!etag.isNullOrBlank()) {
                        requestBuilder.addHeader("If-Range", etag)
                    } else if (!lastModified.isNullOrBlank()) {
                        requestBuilder.addHeader("If-Range", lastModified)
                    }

                    val call = okHttpClient.newCall(requestBuilder.build())
                    activeCalls.getOrPut(task.id) { mutableListOf() }.add(call)

                    val response = call.execute()
                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        throw DownloadHttpException(code, "Part $index failed: $code")
                    }

                    // Strict requirement: HTTP 206 is REQUIRED for multipart segments!
                    if (response.code != 206) {
                        response.close()
                        throw IllegalStateException("Serveur ne supporte pas le téléchargement segmenté (HTTP ${response.code} au lieu de 206)")
                    }

                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && !contentRange.startsWith("bytes $actualStart-")) {
                        response.close()
                        throw IllegalStateException("Réponse Content-Range invalide pour le segment: $contentRange")
                    }

                    val append = isResuming
                    val body = response.body ?: throw Exception("Empty body on part $index")
                    body.byteStream().use { input ->
                        FileOutputStream(partFile, append).use { output ->
                            val buffer = ByteArray(16384)
                            var read: Int
                            if (isResuming) {
                                totalBytesDownloadedCounter.addAndGet(partExisting)
                            }

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalBytesDownloadedCounter.addAndGet(read.toLong())
                            }
                            output.flush()
                        }
                    }
                }
            }

            partJobs.awaitAll()
        } finally {
            progressJob.cancel()
        }

        // Assemble all part files sequentially into temporary assembled file
        val tempAssembled = File(targetFile.parentFile, "${targetFile.name}.download")
        FileOutputStream(tempAssembled, false).use { out ->
            for (partFile in tempParts) {
                if (partFile.exists()) {
                    FileInputStream(partFile).use { inStream ->
                        inStream.copyTo(out)
                    }
                    partFile.delete()
                }
            }
            out.flush()
        }

        // Final integrity gate: verify assembled byte count
        if (totalBytes > 0 && tempAssembled.length() != totalBytes) {
            tempAssembled.delete()
            throw IllegalStateException("Échec de l'assemblage multipart : taille attendue $totalBytes, obtenue ${tempAssembled.length()}")
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempAssembled.renameTo(targetFile)) {
            tempAssembled.copyTo(targetFile, overwrite = true)
            tempAssembled.delete()
        }
    }

    private fun probeServerCapability(url: String): ServerCapability {
        return try {
            val req = Request.Builder().url(url).addHeader("Range", "bytes=0-0").build()
            val resp = okHttpClient.newCall(req).execute()
            val isPartial = resp.code == 206
            val rangeHeader = resp.header("Content-Range")
            val total = rangeHeader?.substringAfter("/")?.toLongOrNull() ?: resp.body?.contentLength() ?: -1L
            val etag = resp.header("ETag")
            val lastModified = resp.header("Last-Modified")
            resp.close()
            ServerCapability(isPartial, total, etag, lastModified)
        } catch (_: Exception) {
            ServerCapability(false, -1L, null, null)
        }
    }

    private fun determinePartCount(mode: String, totalBytes: Long): Int {
        if (totalBytes < 2 * 1024 * 1024) return 1
        return when (mode) {
            "1" -> 1
            "2" -> 2
            "4" -> 4
            "8" -> 8
            "Auto" -> when {
                totalBytes > 30 * 1024 * 1024 -> 4
                totalBytes > 6 * 1024 * 1024 -> 2
                else -> 1
            }
            else -> 1
        }
    }

    /**
     * Handles retry with backoff and expired URL re-resolution.
     */
    private suspend fun handleDownloadError(task: DownloadTask, error: Throwable) {
        val isHttp403Or410 = (error as? DownloadHttpException)?.statusCode in listOf(403, 410)

        if (isHttp403Or410 && task.retryCount < 2) {
            _tasks.update { list ->
                list.map { t ->
                    if (t.id == task.id) {
                        val updated = t.copy(status = DownloadStatus.RESOLVING, speedText = "Lien expiré, rafraîchissement...")
                        persistTask(updated)
                        updated
                    } else t
                }
            }

            val refreshResult = extractorEngine.extractInfo(task.metadata.sourceUrl)
            if (refreshResult.isSuccess) {
                val newMeta = refreshResult.getOrThrow()
                val matchingFormat = newMeta.formats.firstOrNull { it.quality == task.selectedFormat.quality }
                    ?: newMeta.formats.firstOrNull()

                if (matchingFormat != null) {
                    _tasks.update { list ->
                        list.map { t ->
                            if (t.id == task.id) {
                                val updated = t.copy(
                                    selectedFormat = matchingFormat,
                                    metadata = newMeta,
                                    status = DownloadStatus.QUEUED,
                                    retryCount = task.retryCount + 1,
                                    speedText = "Lien rafraîchi, reprise..."
                                )
                                persistTask(updated)
                                updated
                            } else t
                        }
                    }
                    processQueue()
                    return
                }
            }
        }

        // Retry policy with backoff
        if (downloadPreferences.settings.value.autoRetry && task.retryCount < 4) {
            val nextRetry = task.retryCount + 1
            val delayMs = (1L shl nextRetry) * 1000L // 2s, 4s, 8s, 16s

            _tasks.update { list ->
                list.map { t ->
                    if (t.id == task.id) {
                        val updated = t.copy(
                            status = DownloadStatus.RETRYING,
                            retryCount = nextRetry,
                            speedText = "Nouvel essai dans ${delayMs / 1000}s...",
                            errorMessage = error.localizedMessage
                        )
                        persistTask(updated)
                        updated
                    } else t
                }
            }

            scope.launch {
                delay(delayMs)
                _tasks.update { list ->
                    list.map { t ->
                        if (t.id == task.id && t.status == DownloadStatus.RETRYING) {
                            val updated = t.copy(status = DownloadStatus.QUEUED)
                            persistTask(updated)
                            updated
                        } else t
                    }
                }
                processQueue()
            }
        } else {
            _tasks.update { list ->
                list.map { t ->
                    if (t.id == task.id) {
                        val updated = t.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = error.localizedMessage ?: "Erreur de téléchargement",
                            speedText = "Échec",
                            etaText = ""
                        )
                        persistTask(updated)
                        updated
                    } else t
                }
            }
        }
    }

    private fun updateServiceNotification() {
        DownloadForegroundService.updateNotification(appContext)
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f Mo/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%d Ko/s", bytesPerSec / 1024)
            else -> "$bytesPerSec o/s"
        }
    }

    private fun formatEta(downloaded: Long, total: Long, speedBytesPerSec: Long): String {
        if (total <= 0 || speedBytesPerSec <= 0) return ""
        val remainingBytes = (total - downloaded).coerceAtLeast(0L)
        val remainingSeconds = remainingBytes / speedBytesPerSec
        return when {
            remainingSeconds >= 3600 -> String.format(Locale.US, "%dh %02dm", remainingSeconds / 3600, (remainingSeconds % 3600) / 60)
            remainingSeconds >= 60 -> String.format(Locale.US, "%d min %02ds", remainingSeconds / 60, remainingSeconds % 60)
            else -> "${remainingSeconds}s"
        }
    }
}

data class ServerCapability(
    val rangeSupported: Boolean,
    val contentLength: Long,
    val etag: String?,
    val lastModified: String?
)

class DownloadHttpException(val statusCode: Int, message: String) : Exception(message)
