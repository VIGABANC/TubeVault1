package com.example.data.engine

import com.example.data.model.MediaFormat

object FormatSanitizer {
    /**
     * Sanitizes raw yt-dlp formats:
     * - removes storyboards, thumbnail-only formats, invalid formats
     * - identifies video-only, audio-only, combined A/V
     * - groups equivalent formats
     * - sorts by quality intelligently
     */
    fun sanitizeAndSort(rawFormats: List<MediaFormat>): List<MediaFormat> {
        return rawFormats.filter { format ->
            // Remove storyboards or purely image formats
            val isStoryboard = format.formatNote?.contains("storyboard", ignoreCase = true) == true
            val isImage = format.vcodec?.contains("mjpeg", ignoreCase = true) == true && format.acodec == "none"
            !isStoryboard && !isImage
        }.sortedWith(compareByDescending<MediaFormat> { 
            // Sort combined > video only > audio only
            when {
                it.hasVideo && it.hasAudio -> 3
                it.hasVideo -> 2
                it.hasAudio -> 1
                else -> 0
            }
        }.thenByDescending {
            it.height ?: 0 // Sort by resolution height
        }.thenByDescending {
            it.totalBitrate ?: (it.videoBitrate ?: 0.0) // Then by bitrate
        }).distinctBy {
            // Group by resolution and whether it has audio to avoid clutter
            "${it.height}_${it.hasAudio}"
        }
    }
}
