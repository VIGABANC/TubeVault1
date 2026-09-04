package com.example.data.engine

import com.example.data.model.MediaFormat

enum class DownloadStrategy {
    DIRECT_ANDROID,
    REMOTE_YTDLP,
    UNSUPPORTED
}

object DownloadStrategySelector {
    
    fun selectStrategy(format: MediaFormat): DownloadStrategy {
        // DRM check
        if (format.formatNote?.contains("DRM", ignoreCase = true) == true) {
            return DownloadStrategy.UNSUPPORTED
        }
        
        // Complex streams requiring backend merge or processing
        if (format.protocol?.contains("m3u8") == true || format.protocol?.contains("dash") == true) {
            return DownloadStrategy.REMOTE_YTDLP
        }
        
        if (format.directUrl == null && format.manifestUrl != null) {
            return DownloadStrategy.REMOTE_YTDLP
        }
        
        // Separate video + audio requiring merge on backend
        if (format.hasVideo && !format.hasAudio && format.formatNote?.contains("merge", ignoreCase = true) == true) {
            return DownloadStrategy.REMOTE_YTDLP
        }
        
        return DownloadStrategy.DIRECT_ANDROID
    }
}
