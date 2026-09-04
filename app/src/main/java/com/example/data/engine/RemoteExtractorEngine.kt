package com.example.data.engine

import com.example.data.detector.MediaCandidate
import com.example.data.detector.MediaSourceType
import com.example.data.model.Platform
import com.example.data.model.MediaCollection
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.data.network.YouTubeDownloadApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extraction engine implementation encapsulating the remote multi-platform RapidAPI
 * client used in TubeVault.
 */
class RemoteExtractorEngine(
    private val apiClient: YouTubeDownloadApiClient = YouTubeDownloadApiClient()
) : ExtractorEngine {

    override suspend fun extractInfo(url: String): Result<MediaInfo> {
        val trimmed = url.trim()

        // Handle direct media files if passed directly
        if (isDirectMediaUrl(trimmed)) {
            val extension = trimmed.substringAfterLast(".", "mp4").substringBefore("?").lowercase()
            val fileName = trimmed.substringAfterLast("/").substringBefore("?").ifBlank { "Media_$extension" }
            val directFormat = MediaFormat(
                formatId = "direct",
                quality = "Direct Source",
                downloadUrl = trimmed,
                directUrl = trimmed,
                extension = extension,
                approximateSize = null,
                container = extension.uppercase()
            )
            val directMeta = MediaInfo(
                id = trimmed.hashCode().toString(),
                title = fileName,
                thumbnailUrl = "",
                durationText = "--:--",
                author = "Direct Link",
                sourceUrl = trimmed,
                platform = Platform.OTHER,
                formats = listOf(directFormat)
            )
            return Result.success(directMeta)
        }

        return apiClient.fetchMediaInfo(trimmed)
    }

    override suspend fun extractFormats(url: String): Result<List<MediaFormat>> {
        return extractInfo(url).map { it.formats }
    }

    override suspend fun extractCollection(url: String, limit: Int): Result<MediaCollection?> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.contains("list=") || trimmed.contains("/playlist")) {
            val listId = trimmed.substringAfter("list=").substringBefore("&")
            val baseResult = apiClient.fetchMediaInfo(trimmed)
            if (baseResult.isSuccess) {
                val meta = baseResult.getOrThrow()
                val playlist = MediaCollection(
                    id = listId.ifBlank { "playlist_${System.currentTimeMillis()}" },
                    title = "Playlist • ${meta.title}",
                    author = meta.author,
                    items = listOf(meta),
                    sourceUrl = trimmed
                )
                return@withContext Result.success(playlist)
            }
        }
        Result.success(null)
    }
    
    override suspend fun extractPageCandidates(url: String, limit: Int): Result<List<MediaCandidate>> = withContext(Dispatchers.IO) {
        // Simple mock behavior mapping resolving to candidate list
        val result = extractInfo(url)
        if (result.isSuccess) {
            val meta = result.getOrThrow()
            val candidate = MediaCandidate(
                pageUrl = url,
                canonicalUrl = meta.originalUrl ?: meta.sourceUrl,
                platform = meta.platform,
                title = meta.title,
                thumbnail = meta.thumbnailUrl,
                duration = meta.durationText,
                sourceType = MediaSourceType.PLATFORM_PAGE,
                availableFormats = meta.formats,
                isResolved = true
            )
            Result.success(listOf(candidate))
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Extraction failed"))
        }
    }

    override suspend fun refreshMediaUrl(pageUrl: String, formatId: String): Result<String> = withContext(Dispatchers.IO) {
        val result = extractInfo(pageUrl)
        if (result.isSuccess) {
            val formats = result.getOrThrow().formats
            val targetFormat = formats.find { it.formatId == formatId } ?: formats.firstOrNull()
            if (targetFormat != null && targetFormat.downloadUrl.isNotBlank()) {
                Result.success(targetFormat.downloadUrl)
            } else {
                Result.failure(Exception("Format not found or URL empty"))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Refresh failed"))
        }
    }

    override fun supports(url: String): Boolean {
        val trimmed = url.trim()
        val platform = Platform.detect(trimmed)
        if (platform != Platform.OTHER) return true
        if (apiClient.extractYouTubeId(trimmed) != null) return true
        return isDirectMediaUrl(trimmed)
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m4v") ||
                lower.endsWith(".mov") || lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                lower.contains(".m3u8") || lower.contains(".mpd")
    }
}
