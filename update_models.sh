#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/data/model/VideoModels.kt
package com.example.data.model

data class ThumbnailInfo(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val resolution: String? = null
)

data class SubtitleTrack(
    val language: String,
    val url: String,
    val ext: String = "vtt",
    val name: String? = null,
    val isAutomatic: Boolean = false
)

data class MediaFormat(
    val formatId: String = "",
    val formatNote: String? = null,
    val quality: String, // e.g., "1080p", "720p", "480p", "360p", "Audio MP3"
    val downloadUrl: String,
    val extension: String = "mp4",
    val approximateSize: String? = null,
    val fps: Int? = null,
    val isHdr: Boolean = false,
    val vcodec: String? = null,
    val acodec: String? = null,
    val container: String? = null,
    val bitrateKbps: Int? = null,
    val hasAudio: Boolean = true,
    val hasVideo: Boolean = true,
    val width: Int? = null,
    val height: Int? = null,
    val resolution: String? = null,
    val audioBitrate: Double? = null,
    val videoBitrate: Double? = null,
    val totalBitrate: Double? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val protocol: String? = null,
    val directUrl: String? = null,
    val manifestUrl: String? = null,
    val language: String? = null
)

data class MediaInfo(
    val id: String = "",
    val title: String,
    val description: String? = null,
    val webpageUrl: String? = null,
    val originalUrl: String? = null,
    val extractor: String? = null,
    val extractorKey: String? = null,
    val thumbnailUrl: String,
    val thumbnails: List<ThumbnailInfo> = emptyList(),
    val durationText: String,
    val duration: Long? = null,
    val author: String? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val channelUrl: String? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val isLive: Boolean = false,
    val sourceUrl: String,
    val platform: Platform = Platform.YOUTUBE,
    val formats: List<MediaFormat>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val automaticCaptions: List<SubtitleTrack> = emptyList()
)

data class MediaCollection(
    val id: String,
    val title: String,
    val author: String? = null,
    val items: List<MediaInfo> = emptyList(),
    val sourceUrl: String
)

enum class DownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED,
    WAITING_FOR_NETWORK,
    RETRYING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH
}

data class DownloadTask(
    val id: String,
    val metadata: MediaInfo,
    val selectedFormat: MediaFormat,
    val platform: Platform,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = "",
    val etaText: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val isSegmented: Boolean = false,
    val segmentsCount: Int = 1,
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    val savedVideo: DownloadedVideo? = null,
    val createdAt: Long = System.currentTimeMillis()
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data object FetchingMetadata : DownloadState
    data class MetadataLoaded(
        val metadata: MediaInfo,
        val selectedFormat: MediaFormat
    ) : DownloadState
    data class Downloading(
        val metadata: MediaInfo,
        val selectedFormat: MediaFormat,
        val progress: Float, // 0.0f to 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState
    data class Success(
        val video: DownloadedVideo
    ) : DownloadState
    data class Error(
        val message: String
    ) : DownloadState
}
INNER_EOF
