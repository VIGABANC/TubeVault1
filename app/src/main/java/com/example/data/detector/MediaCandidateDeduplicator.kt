package com.example.data.detector

import com.example.data.model.Platform
import com.example.util.VideoUrlDetector
import java.net.URI

object MediaCandidateDeduplicator {

    /**
     * Deduplicates a list of [MediaCandidate] items using intelligent multi-tier matching:
     * 1. Platform + Video ID
     * 2. Normalized Canonical URL
     * 3. Normalized Direct Media URL
     * 4. Title / URL Hash
     *
     * Multiple qualities/formats detected for the same media item are consolidated
     * under a single candidate rather than creating redundant cards.
     */
    fun deduplicate(candidates: List<MediaCandidate>): List<MediaCandidate> {
        if (candidates.size <= 1) return candidates

        val grouped = LinkedHashMap<String, MutableList<MediaCandidate>>()

        for (candidate in candidates) {
            val key = computeDeduplicationKey(candidate)
            grouped.getOrPut(key) { mutableListOf() }.add(candidate)
        }

        return grouped.values.map { list ->
            mergeCandidates(list)
        }
    }

    private fun computeDeduplicationKey(candidate: MediaCandidate): String {
        // Priority 1: Platform + Video ID
        val detected = VideoUrlDetector.detectVideo(candidate.canonicalUrl.ifBlank { candidate.pageUrl })
        if (detected != null && !detected.mediaId.isNullOrBlank()) {
            return "${detected.platform.name}_${detected.mediaId}"
        }

        // Priority 2: Canonical Page URL (normalized without tracking query params)
        if (candidate.canonicalUrl.isNotBlank() && candidate.canonicalUrl != "about:blank") {
            val normalizedCanonical = normalizeUrl(candidate.canonicalUrl)
            if (normalizedCanonical.isNotBlank()) {
                return "CANONICAL_$normalizedCanonical"
            }
        }

        // Priority 3: Media Stream URL (normalized without query params)
        if (!candidate.mediaUrl.isNullOrBlank()) {
            val normalizedMedia = normalizeUrl(candidate.mediaUrl)
            if (normalizedMedia.isNotBlank()) {
                return "MEDIA_$normalizedMedia"
            }
        }

        // Priority 4: Stable fallback hash
        val titleHash = candidate.title?.trim()?.lowercase().hashCode()
        val pageHash = candidate.pageUrl.trim().lowercase().hashCode()
        return "FALLBACK_${titleHash}_$pageHash"
    }

    private fun normalizeUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl.trim())
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path?.lowercase() ?: ""
            "$host$path"
        } catch (_: Exception) {
            rawUrl.substringBefore("?").trim().lowercase()
        }
    }

    private fun mergeCandidates(candidates: List<MediaCandidate>): MediaCandidate {
        val primary = candidates.maxByOrNull { it.detectionConfidence } ?: candidates.first()

        // Combine formats, keeping only distinct qualities
        val combinedFormats = candidates.flatMap { it.availableFormats }
            .distinctBy { it.quality }

        // Choose richest available title
        val bestTitle = candidates.mapNotNull { it.title }.firstOrNull { it.isNotBlank() } ?: primary.title

        // Choose best thumbnail
        val bestThumbnail = candidates.mapNotNull { it.thumbnail }.firstOrNull { it.isNotBlank() } ?: primary.thumbnail

        // Choose best duration
        val bestDuration = candidates.mapNotNull { it.duration }.firstOrNull { it.isNotBlank() } ?: primary.duration

        val isAnyResolved = candidates.any { it.isResolved }

        return primary.copy(
            title = bestTitle,
            thumbnail = bestThumbnail,
            duration = bestDuration,
            availableFormats = if (combinedFormats.isNotEmpty()) combinedFormats else primary.availableFormats,
            isResolved = primary.isResolved || isAnyResolved
        )
    }
}
