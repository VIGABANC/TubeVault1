package com.example

import com.example.data.network.YouTubeDownloadApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeApiClientTest {

    private val apiClient = YouTubeDownloadApiClient()

    @Test
    fun `extract YouTube ID from various URL formats`() {
        val standardUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val embedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val rawId = "dQw4w9WgXcQ"

        assertEquals("dQw4w9WgXcQ", apiClient.extractYouTubeId(standardUrl))
        assertEquals("dQw4w9WgXcQ", apiClient.extractYouTubeId(shortUrl))
        assertEquals("dQw4w9WgXcQ", apiClient.extractYouTubeId(embedUrl))
        assertEquals("dQw4w9WgXcQ", apiClient.extractYouTubeId(shortsUrl))
        assertEquals("dQw4w9WgXcQ", apiClient.extractYouTubeId(rawId))
    }

    @Test
    fun `parse JSON metadata correctly`() {
        val sampleJson = """
            {
                "title": "Rick Astley - Never Gonna Give You Up",
                "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                "lengthSeconds": 213,
                "author": "RickAstleyVEVO",
                "formats": [
                    {
                        "qualityLabel": "1080p (Full HD)",
                        "url": "https://example.com/stream/1080p.mp4",
                        "extension": "mp4",
                        "size": "45 Mo"
                    },
                    {
                        "qualityLabel": "720p (HD)",
                        "url": "https://example.com/stream/720p.mp4",
                        "extension": "mp4",
                        "size": "24 Mo"
                    },
                    {
                        "qualityLabel": "360p",
                        "url": "https://example.com/stream/360p.mp4",
                        "extension": "mp4",
                        "size": "10 Mo"
                    }
                ]
            }
        """.trimIndent()

        val parsed = apiClient.parseMetadataJson(
            jsonString = sampleJson,
            originalUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            fallbackThumbnail = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )

        assertEquals("Rick Astley - Never Gonna Give You Up", parsed.title)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", parsed.thumbnailUrl)
        assertEquals("03:33", parsed.durationText)
        assertEquals(3, parsed.formats.size)
        assertEquals("1080p (Full HD)", parsed.formats[0].quality)
        assertEquals("720p (HD)", parsed.formats[1].quality)
    }

    @Test
    fun `parse alternative RapidAPI JSON structure`() {
        val alternativeJson = """
            {
                "data": {
                    "video_title": "Cool Nature Documentary",
                    "picture": "https://example.com/doc.jpg",
                    "duration": "12:45",
                    "medias": [
                        {
                            "quality": "720p",
                            "downloadUrl": "https://example.com/doc_720.mp4"
                        }
                    ]
                }
            }
        """.trimIndent()

        val parsed = apiClient.parseMetadataJson(
            jsonString = alternativeJson,
            originalUrl = "https://www.youtube.com/watch?v=abcdefghijk",
            fallbackThumbnail = "https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg"
        )

        assertEquals("Cool Nature Documentary", parsed.title)
        assertEquals("https://example.com/doc.jpg", parsed.thumbnailUrl)
        assertEquals("12:45", parsed.durationText)
        assertEquals(1, parsed.formats.size)
        assertEquals("720p", parsed.formats[0].quality)
        assertEquals("https://example.com/doc_720.mp4", parsed.formats[0].downloadUrl)
    }

    @Test
    fun `detect platforms correctly`() {
        val yt = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val tt = "https://www.tiktok.com/@user/video/7123456789012345678"
        val ig = "https://www.instagram.com/reel/C7abc123DEF/"
        val tw = "https://twitter.com/user/status/1789012345678901234"
        val x = "https://x.com/user/status/1789012345678901234"

        assertEquals(com.example.data.model.Platform.YOUTUBE, com.example.data.model.Platform.detect(yt))
        assertEquals(com.example.data.model.Platform.TIKTOK, com.example.data.model.Platform.detect(tt))
        assertEquals(com.example.data.model.Platform.INSTAGRAM, com.example.data.model.Platform.detect(ig))
        assertEquals(com.example.data.model.Platform.TWITTER, com.example.data.model.Platform.detect(tw))
        assertEquals(com.example.data.model.Platform.TWITTER, com.example.data.model.Platform.detect(x))
    }

    @Test
    fun `extract multiplatform media ids correctly`() {
        val tt = "https://www.tiktok.com/@user/video/7123456789012345678"
        val ig = "https://www.instagram.com/reel/C7abc123DEF/"
        val tw = "https://twitter.com/user/status/1789012345678901234"

        assertEquals("7123456789012345678", apiClient.extractMediaId(tt, com.example.data.model.Platform.TIKTOK))
        assertEquals("C7abc123DEF", apiClient.extractMediaId(ig, com.example.data.model.Platform.INSTAGRAM))
        assertEquals("1789012345678901234", apiClient.extractMediaId(tw, com.example.data.model.Platform.TWITTER))
    }
}
