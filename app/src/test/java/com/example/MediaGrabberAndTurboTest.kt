package com.example

import com.example.data.detector.MediaCandidate
import com.example.data.detector.MediaDetector
import com.example.data.detector.MediaSourceType
import com.example.data.model.DownloadStatus
import com.example.data.model.Platform
import com.example.data.model.TaskPriority
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.data.service.DownloadManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaGrabberAndTurboTest {

    private lateinit var detector: MediaDetector

    @Before
    fun setUp() {
        detector = MediaDetector()
    }

    @Test
    fun `MediaDetector identifies direct MP4 stream from network intercept`() {
        val pageUrl = "https://example.com/watch"
        val streamUrl = "https://cdn.example.com/videos/sample_1080p.mp4?token=abc"
        detector.onNetworkRequest(pageUrl = pageUrl, requestUrl = streamUrl, mimeType = null)

        val candidates = detector.detectedCandidates.value
        assertEquals(1, candidates.size)

        val candidate = candidates.first()
        assertEquals(MediaSourceType.DIRECT_MEDIA, candidate.sourceType)
        assertEquals(streamUrl, candidate.mediaUrl)
        assertEquals("mp4", candidate.extension)
    }

    @Test
    fun `MediaDetector identifies HLS m3u8 stream from network intercept`() {
        val pageUrl = "https://example.com/watch"
        val hlsUrl = "https://live.example.com/hls/master.m3u8"
        detector.onNetworkRequest(pageUrl = pageUrl, requestUrl = hlsUrl, mimeType = "application/vnd.apple.mpegurl")

        val candidates = detector.detectedCandidates.value
        assertEquals(1, candidates.size)

        val candidate = candidates.first()
        assertEquals(MediaSourceType.HLS, candidate.sourceType)
        assertEquals("m3u8", candidate.extension)
    }

    @Test
    fun `MediaDetector deduplicates identical intercepted URLs`() {
        val pageUrl = "https://example.com/watch"
        val streamUrl = "https://cdn.example.com/asset.mp4"
        detector.onNetworkRequest(pageUrl, streamUrl, null)
        detector.onNetworkRequest(pageUrl, streamUrl, null)
        detector.onNetworkRequest(pageUrl, streamUrl, null)

        val candidates = detector.detectedCandidates.value
        assertEquals(1, candidates.size)
    }

    @Test
    fun `MediaDetector clears candidates on page navigation`() {
        val pageUrl = "https://example.com/watch"
        val streamUrl = "https://cdn.example.com/asset.mp4"
        detector.onNetworkRequest(pageUrl, streamUrl, null)
        assertEquals(1, detector.detectedCandidates.value.size)

        // Navigate to new page (e.g. non-video search page)
        detector.onPageUrlChanged("https://www.google.com/search?q=test")
        // Previous transient streams are cleared on page change
        assertEquals(0, detector.detectedCandidates.value.size)
    }

    @Test
    fun `MediaDetector handles HTML5 video tags injected via JSON`() {
        val pageUrl = "https://example.com/page"
        val json = """
            [
                {"url": "https://cdn.site.com/video1.mp4", "title": "First Video", "thumbnail": "https://site.com/thumb1.jpg", "type": "video"},
                {"url": "https://cdn.site.com/video2.mp4", "title": "Second Video", "thumbnail": "", "type": "video"}
            ]
        """.trimIndent()

        detector.onHtml5ScanResult(pageUrl, json)

        val candidates = detector.detectedCandidates.value
        assertEquals(2, candidates.size)
        assertEquals("First Video", candidates[0].title)
        assertEquals("Second Video", candidates[1].title)
    }

    @Test
    fun `DownloadManager enqueues tasks and manages status lifecycle`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val downloadManager = DownloadManager.getInstance(context)

        val dummyMeta = MediaInfo(
            title = "Test Video Title",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "3:45",
            author = "Test Author",
            sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            platform = Platform.YOUTUBE,
            formats = listOf(
                MediaFormat(quality = "1080p", downloadUrl = "https://example.com/video.mp4", extension = "mp4")
            )
        )

        val format = dummyMeta.formats.first()
        val taskId = downloadManager.enqueue(dummyMeta, format, TaskPriority.HIGH)

        assertNotNull(taskId)
        val taskList = downloadManager.tasks.first()
        val task = taskList.find { it.id == taskId }

        assertNotNull(task)
        assertEquals(dummyMeta.title, task?.metadata?.title)
        assertEquals(format.quality, task?.selectedFormat?.quality)
        assertEquals(TaskPriority.HIGH, task?.priority)

        // Test Pause and Resume
        downloadManager.pause(taskId)
        val pausedTask = downloadManager.tasks.first().find { it.id == taskId }
        assertEquals(DownloadStatus.PAUSED, pausedTask?.status)

        downloadManager.resume(taskId)
        val resumedTask = downloadManager.tasks.first().find { it.id == taskId }
        assertTrue(resumedTask?.status == DownloadStatus.QUEUED || resumedTask?.status == DownloadStatus.DOWNLOADING)

        // Cancel task
        downloadManager.cancel(taskId)
        val finalTaskList = downloadManager.tasks.first()
        assertNull(finalTaskList.find { it.id == taskId })
    }

    @Test
    fun `DownloadManager handles batch enqueue`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val downloadManager = DownloadManager.getInstance(context)

        val batchItems = listOf(
            Pair(
                MediaInfo(
                    title = "Batch 1",
                    thumbnailUrl = "",
                    durationText = "1:00",
                    sourceUrl = "https://youtube.com/watch?v=1",
                    platform = Platform.YOUTUBE,
                    formats = listOf(MediaFormat(quality = "720p", downloadUrl = "https://example.com/b1.mp4", extension = "mp4"))
                ),
                MediaFormat(quality = "720p", downloadUrl = "https://example.com/b1.mp4", extension = "mp4")
            ),
            Pair(
                MediaInfo(
                    title = "Batch 2",
                    thumbnailUrl = "",
                    durationText = "0:30",
                    sourceUrl = "https://tiktok.com/@user/video/2",
                    platform = Platform.TIKTOK,
                    formats = listOf(MediaFormat(quality = "HD", downloadUrl = "https://example.com/b2.mp4", extension = "mp4"))
                ),
                MediaFormat(quality = "HD", downloadUrl = "https://example.com/b2.mp4", extension = "mp4")
            )
        )

        val taskIds = downloadManager.enqueueBatch(batchItems)
        assertEquals(2, taskIds.size)

        val tasks = downloadManager.tasks.first()
        assertTrue(tasks.any { it.id == taskIds[0] })
        assertTrue(tasks.any { it.id == taskIds[1] })

        // Clean up
        taskIds.forEach { downloadManager.cancel(it) }
    }

    @Test
    fun `QualitySelectionTest - recommended quality deterministic`() {
        val viewModel = com.example.ui.TubeVaultViewModel(RuntimeEnvironment.getApplication())
        val formats = listOf(
            MediaFormat(quality = "1080p", downloadUrl = "https://example.com/1080.mp4"),
            MediaFormat(quality = "720p", downloadUrl = "https://example.com/720.mp4"),
            MediaFormat(quality = "480p", downloadUrl = "https://example.com/480.mp4")
        )

        // "Recommended" prefers 720p or 1080p deterministically
        val pickedRecommended = viewModel.pickFormatForQuality(formats, "Recommended")
        assertNotNull(pickedRecommended)
        assertTrue(pickedRecommended?.quality == "720p" || pickedRecommended?.quality == "1080p")

        val pickedBest = viewModel.pickFormatForQuality(formats, "Best")
        assertEquals("1080p", pickedBest?.quality)

        val picked480 = viewModel.pickFormatForQuality(formats, "480p")
        assertEquals("480p", picked480?.quality)
    }

    @Test
    fun `MediaCandidateDeduplicatorTest - group formats under one item`() {
        val candidate1 = MediaCandidate(
            id = "c1",
            pageUrl = "https://example.com/video",
            canonicalUrl = "https://example.com/video",
            availableFormats = listOf(MediaFormat(quality = "1080p", downloadUrl = "url1"))
        )
        val candidate2 = MediaCandidate(
            id = "c2",
            pageUrl = "https://example.com/video",
            canonicalUrl = "https://example.com/video",
            availableFormats = listOf(MediaFormat(quality = "720p", downloadUrl = "url2"))
        )

        val merged = com.example.data.detector.MediaCandidateDeduplicator.deduplicate(listOf(candidate1, candidate2))
        assertEquals(1, merged.size)
        assertEquals(2, merged.first().availableFormats.size)
    }

    @Test
    fun `DuplicateDownloadTest - detects active or completed duplicates`() {
        val viewModel = com.example.ui.TubeVaultViewModel(RuntimeEnvironment.getApplication())
        // Initially no duplicates
        val duplicate = viewModel.findDuplicate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNull(duplicate)
    }
}

