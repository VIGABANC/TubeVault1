package com.example

import com.example.data.model.Platform
import com.example.ui.screens.resolveBrowserUrl
import com.example.util.VideoUrlDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserIntegrationTest {

    @Test
    fun `resolveBrowserUrl converts text query to Google search by default`() {
        val query = "lofi hip hop beats"
        val resolved = resolveBrowserUrl(query)
        assertTrue(resolved.startsWith("https://www.google.com/search?q="))
        assertTrue(resolved.contains("lofi"))
    }

    @Test
    fun `resolveBrowserUrl converts text query to YouTube search when engine is YouTube`() {
        val query = "lofi hip hop beats"
        val resolved = resolveBrowserUrl(query, searchEngine = "YouTube")
        assertTrue(resolved.startsWith("https://m.youtube.com/results?search_query="))
        assertTrue(resolved.contains("lofi"))
    }

    @Test
    fun `resolveBrowserUrl keeps full URLs intact`() {
        val url = "https://www.tiktok.com/@user/video/123456789"
        val resolved = resolveBrowserUrl(url)
        assertEquals(url, resolved)
    }

    @Test
    fun `resolveBrowserUrl prepends https for domain names`() {
        val domain = "m.youtube.com"
        val resolved = resolveBrowserUrl(domain)
        assertEquals("https://m.youtube.com", resolved)

        val tiktokDomain = "tiktok.com/@creator"
        val resolvedTiktok = resolveBrowserUrl(tiktokDomain)
        assertEquals("https://tiktok.com/@creator", resolvedTiktok)
    }

    @Test
    fun `detectVideo identifies valid YouTube video URLs`() {
        val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val detected = VideoUrlDetector.detectVideo(watchUrl)
        assertNotNull(detected)
        assertEquals(Platform.YOUTUBE, detected?.platform)
        assertEquals("dQw4w9WgXcQ", detected?.mediaId)

        val mobileWatchUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"
        val detectedMobile = VideoUrlDetector.detectVideo(mobileWatchUrl)
        assertNotNull(detectedMobile)
        assertEquals(Platform.YOUTUBE, detectedMobile?.platform)
        assertEquals("dQw4w9WgXcQ", detectedMobile?.mediaId)

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val detectedShort = VideoUrlDetector.detectVideo(shortUrl)
        assertNotNull(detectedShort)
        assertEquals(Platform.YOUTUBE, detectedShort?.platform)
        assertEquals("dQw4w9WgXcQ", detectedShort?.mediaId)

        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val detectedShorts = VideoUrlDetector.detectVideo(shortsUrl)
        assertNotNull(detectedShorts)
        assertEquals(Platform.YOUTUBE, detectedShorts?.platform)
        assertEquals("dQw4w9WgXcQ", detectedShorts?.mediaId)
    }

    @Test
    fun `detectVideo rejects non-video YouTube pages`() {
        // Homepage
        assertNull(VideoUrlDetector.detectVideo("https://www.youtube.com/"))
        assertNull(VideoUrlDetector.detectVideo("https://m.youtube.com"))

        // Search results
        assertNull(VideoUrlDetector.detectVideo("https://m.youtube.com/results?search_query=cat+videos"))

        // Channel or feed
        assertNull(VideoUrlDetector.detectVideo("https://www.youtube.com/feed/trending"))
        assertNull(VideoUrlDetector.detectVideo("https://www.youtube.com/@mkbhd"))
        assertNull(VideoUrlDetector.detectVideo("https://www.youtube.com/channel/UCxxxxxx"))
    }

    @Test
    fun `detectVideo identifies valid TikTok video URLs and rejects profile`() {
        val videoUrl = "https://www.tiktok.com/@user123/video/7123456789012345678"
        val detected = VideoUrlDetector.detectVideo(videoUrl)
        assertNotNull(detected)
        assertEquals(Platform.TIKTOK, detected?.platform)
        assertEquals("7123456789012345678", detected?.mediaId)

        val vtUrl = "https://vt.tiktok.com/ZS2abcXYZ/"
        val detectedVt = VideoUrlDetector.detectVideo(vtUrl)
        assertNotNull(detectedVt)
        assertEquals(Platform.TIKTOK, detectedVt?.platform)

        // Profile without video should be rejected (no false positive)
        assertNull(VideoUrlDetector.detectVideo("https://www.tiktok.com/@user123"))
        assertNull(VideoUrlDetector.detectVideo("https://www.tiktok.com/explore"))
        assertNull(VideoUrlDetector.detectVideo("https://www.tiktok.com/"))
    }

    @Test
    fun `detectVideo identifies valid Instagram reels and posts and rejects explore`() {
        val reelUrl = "https://www.instagram.com/reel/C7abc123DEF/"
        val detectedReel = VideoUrlDetector.detectVideo(reelUrl)
        assertNotNull(detectedReel)
        assertEquals(Platform.INSTAGRAM, detectedReel?.platform)
        assertEquals("C7abc123DEF", detectedReel?.mediaId)

        val postUrl = "https://www.instagram.com/p/C7xyz987GHI/"
        val detectedPost = VideoUrlDetector.detectVideo(postUrl)
        assertNotNull(detectedPost)
        assertEquals(Platform.INSTAGRAM, detectedPost?.platform)
        assertEquals("C7xyz987GHI", detectedPost?.mediaId)

        // Explore or home should be rejected
        assertNull(VideoUrlDetector.detectVideo("https://www.instagram.com/"))
        assertNull(VideoUrlDetector.detectVideo("https://www.instagram.com/explore"))
    }

    @Test
    fun `detectVideo identifies Twitter or X status URLs and rejects feeds`() {
        val statusUrl = "https://x.com/user/status/1789012345678901234"
        val detected = VideoUrlDetector.detectVideo(statusUrl)
        assertNotNull(detected)
        assertEquals(Platform.TWITTER, detected?.platform)
        assertEquals("1789012345678901234", detected?.mediaId)

        val twitterUrl = "https://twitter.com/user/status/1789012345678901234"
        val detectedTwitter = VideoUrlDetector.detectVideo(twitterUrl)
        assertNotNull(detectedTwitter)
        assertEquals(Platform.TWITTER, detectedTwitter?.platform)

        // Feeds or home rejected
        assertNull(VideoUrlDetector.detectVideo("https://x.com/home"))
        assertNull(VideoUrlDetector.detectVideo("https://x.com/explore"))
        assertNull(VideoUrlDetector.detectVideo("https://twitter.com/"))
    }
}
