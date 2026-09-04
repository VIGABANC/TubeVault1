package com.example.data.detector

import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import java.util.UUID

enum class MediaSourceType {
    PLATFORM_PAGE,
    DIRECT_MEDIA,
    HLS,
    DASH,
    HTML5_VIDEO,
    PLAYLIST,
    UNKNOWN
}

data class MediaCandidate(
    val id: String = UUID.randomUUID().toString(),
    val pageUrl: String,
    val mediaUrl: String? = null,
    val canonicalUrl: String = pageUrl,
    val platform: Platform = Platform.OTHER,
    val type: String = "video", // "video" | "audio" | "playlist"
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: String? = null,
    val mimeType: String? = null,
    val extension: String = "mp4",
    val sourceType: MediaSourceType = MediaSourceType.UNKNOWN,
    val detectionConfidence: Float = 1.0f,
    val availableFormats: List<MediaFormat> = emptyList(),
    val isResolved: Boolean = false,
    val detectedAt: Long = System.currentTimeMillis()
)
