package com.example.data.engine

import com.example.data.detector.MediaCandidate
import com.example.data.model.MediaCollection
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo

/**
 * Common extraction engine abstraction for resolving media metadata, format streams,
 * and playlists across platforms.
 */
interface ExtractorEngine {

    /**
     * Resolves complete metadata and available formats for a video URL.
     */
    suspend fun extractInfo(url: String): Result<MediaInfo>
    
    // For backwards compatibility:
    suspend fun resolve(url: String): Result<MediaInfo> = extractInfo(url)

    /**
     * Resolves the list of downloadable formats and resolutions for a media URL.
     */
    suspend fun extractFormats(url: String): Result<List<MediaFormat>>
    
    // For backwards compatibility:
    suspend fun resolveFormats(url: String): Result<List<MediaFormat>> = extractFormats(url)

    /**
     * Attempts to resolve a playlist if the given URL corresponds to a multi-video collection.
     * Returns null if the URL is an individual video or collection extraction is not applicable.
     */
    suspend fun extractCollection(url: String, limit: Int = 50): Result<MediaCollection?>
    
    // For backwards compatibility:
    suspend fun resolvePlaylist(url: String): Result<MediaCollection?> = extractCollection(url)

    /**
     * Extracts all page candidates for batch downloading or generic media extraction.
     */
    suspend fun extractPageCandidates(url: String, limit: Int = 50): Result<List<MediaCandidate>>

    /**
     * Refreshes a media URL if it has expired.
     */
    suspend fun refreshMediaUrl(pageUrl: String, formatId: String): Result<String>

    /**
     * Returns true if this engine supports resolving the given URL.
     */
    fun supports(url: String): Boolean
}
