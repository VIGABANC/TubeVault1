package com.example.util

import android.net.Uri
import com.example.data.model.Platform

/**
 * Information about a detected individual downloadable video on a supported platform.
 */
data class DetectedVideo(
    val originalUrl: String,
    val platform: Platform,
    val mediaId: String? = null
)

/**
 * Precision URL detector that identifies whether an URL points to a specific,
 * single downloadable video (rather than homepages, search results, channel feeds, or profiles).
 */
object VideoUrlDetector {

    /**
     * Examines a URL string and returns a [DetectedVideo] if it corresponds to an individual video
     * on YouTube, TikTok, Instagram, or Twitter/X. Returns null otherwise (no false positives).
     */
    fun detectVideo(rawUrl: String?): DetectedVideo? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()

        // Quick scheme validation
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path ?: ""

            when {
                // --- YOUTUBE ---
                host.contains("youtube.com") || host.contains("youtu.be") -> {
                    detectYouTube(trimmed, host, uri, path)
                }

                // --- TIKTOK ---
                host.contains("tiktok.com") -> {
                    detectTikTok(trimmed, uri, path)
                }

                // --- INSTAGRAM ---
                host.contains("instagram.com") -> {
                    detectInstagram(trimmed, path)
                }

                // --- TWITTER / X ---
                host.contains("twitter.com") || host.contains("x.com") -> {
                    detectTwitter(trimmed, path)
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun detectYouTube(url: String, host: String, uri: Uri, path: String): DetectedVideo? {
        // Exclude general YouTube non-video routes
        if (path.startsWith("/feed", ignoreCase = true) ||
            path.startsWith("/channel", ignoreCase = true) ||
            path.startsWith("/c/", ignoreCase = true) ||
            path.startsWith("/user", ignoreCase = true) ||
            path.startsWith("/results", ignoreCase = true) ||
            path.startsWith("/explore", ignoreCase = true) ||
            path.startsWith("/gaming", ignoreCase = true) ||
            path.startsWith("/trending", ignoreCase = true)
        ) {
            return null
        }

        // 1. youtu.be/{videoId}
        if (host.contains("youtu.be")) {
            val segments = uri.pathSegments
            if (segments.isNotEmpty()) {
                val candidateId = segments[0].trim()
                if (candidateId.length >= 5) {
                    return DetectedVideo(url, Platform.YOUTUBE, candidateId)
                }
            }
            return null
        }

        // 2. youtube.com/watch?v={videoId} (or m.youtube.com/watch)
        if (path.startsWith("/watch", ignoreCase = true)) {
            val videoId = uri.getQueryParameter("v")?.trim()
            if (!videoId.isNullOrBlank() && videoId.length >= 5) {
                return DetectedVideo(url, Platform.YOUTUBE, videoId)
            }
            return null
        }

        // 3. youtube.com/shorts/{videoId}
        if (path.startsWith("/shorts/", ignoreCase = true)) {
            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val candidateId = segments[1].trim()
                if (candidateId.length >= 5) {
                    return DetectedVideo(url, Platform.YOUTUBE, candidateId)
                }
            }
        }

        return null
    }

    private fun detectTikTok(url: String, uri: Uri, path: String): DetectedVideo? {
        // 1. Shortened links: vt.tiktok.com/{id} or vm.tiktok.com/{id} or tiktok.com/t/{id}
        val host = uri.host?.lowercase() ?: ""
        if (host == "vt.tiktok.com" || host == "vm.tiktok.com") {
            val segments = uri.pathSegments
            if (segments.isNotEmpty() && segments[0].isNotBlank()) {
                return DetectedVideo(url, Platform.TIKTOK, segments[0])
            }
        }

        if (path.startsWith("/t/", ignoreCase = true)) {
            val segments = uri.pathSegments
            if (segments.size >= 2 && segments[1].isNotBlank()) {
                return DetectedVideo(url, Platform.TIKTOK, segments[1])
            }
        }

        // 2. Full link: /@username/video/{id}
        val videoPattern = Regex(".*/video/(\\d+).*", RegexOption.IGNORE_CASE)
        val match = videoPattern.find(path)
        if (match != null) {
            val videoId = match.groupValues[1]
            return DetectedVideo(url, Platform.TIKTOK, videoId)
        }

        // 3. /v/{id}
        if (path.startsWith("/v/", ignoreCase = true)) {
            val segments = uri.pathSegments
            if (segments.size >= 2 && segments[1].isNotBlank()) {
                return DetectedVideo(url, Platform.TIKTOK, segments[1])
            }
        }

        return null
    }

    private fun detectInstagram(url: String, path: String): DetectedVideo? {
        // /reel/{shortcode}, /reels/{shortcode}, /p/{shortcode}, /tv/{shortcode}
        val reelPattern = Regex("^/(?:reel|reels|p|tv)/([a-zA-Z0-9_-]+)/?.*", RegexOption.IGNORE_CASE)
        val match = reelPattern.find(path)
        if (match != null) {
            val shortcode = match.groupValues[1]
            if (shortcode.isNotBlank()) {
                return DetectedVideo(url, Platform.INSTAGRAM, shortcode)
            }
        }
        return null
    }

    private fun detectTwitter(url: String, path: String): DetectedVideo? {
        // /{user}/status/{statusId}
        val twitterPattern = Regex("^/[^/]+/status/(\\d+)/?.*", RegexOption.IGNORE_CASE)
        val match = twitterPattern.find(path)
        if (match != null) {
            val statusId = match.groupValues[1]
            if (statusId.isNotBlank()) {
                return DetectedVideo(url, Platform.TWITTER, statusId)
            }
        }
        return null
    }
}
