package com.example.data.engine

import com.example.data.model.MediaFormat

object RecommendedFormatSelector {
    
    fun selectRecommended(formats: List<MediaFormat>): MediaFormat? {
        if (formats.isEmpty()) return null
        
        // 1. Try to find a combined format at 1080p
        formats.find { it.hasVideo && it.hasAudio && it.height == 1080 }?.let { return it }
        
        // 2. Try to find a combined format at 720p
        formats.find { it.hasVideo && it.hasAudio && it.height == 720 }?.let { return it }
        
        // 3. Any combined format
        formats.find { it.hasVideo && it.hasAudio }?.let { return it }
        
        // 4. Fallback to the highest quality video
        formats.find { it.hasVideo }?.let { return it }
        
        return formats.first()
    }
    
    fun selectQuality(formats: List<MediaFormat>, targetQuality: String): MediaFormat? {
        val heightMap = mapOf("2160p" to 2160, "1440p" to 1440, "1080p" to 1080, "720p" to 720, "480p" to 480, "360p" to 360)
        
        if (targetQuality == "Recommended" || targetQuality == "Best") {
            return selectRecommended(formats)
        }
        
        if (targetQuality == "Audio" || targetQuality.contains("Audio")) {
            return formats.find { it.hasAudio && !it.hasVideo } ?: formats.find { it.hasAudio }
        }
        
        val targetHeight = heightMap[targetQuality]
        
        // Try exact match with audio
        if (targetHeight != null) {
            formats.find { it.height == targetHeight && it.hasAudio }?.let { return it }
            formats.find { it.height == targetHeight }?.let { return it }
            
            // Fallback to nearest lower resolution
            return formats.filter { it.height != null && it.height <= targetHeight }
                          .maxByOrNull { it.height!! }
        }
        
        return formats.firstOrNull()
    }
}
