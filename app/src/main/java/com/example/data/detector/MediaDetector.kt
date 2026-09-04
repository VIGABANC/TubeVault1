package com.example.data.detector

import android.net.Uri
import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import com.example.util.VideoUrlDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MediaDetector {

    private val _detectedCandidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val detectedCandidates: StateFlow<List<MediaCandidate>> = _detectedCandidates.asStateFlow()

    val html5ScannerScript: String = """
        (function() {
            var results = [];
            var seen = {};

            // 1. OpenGraph metadata fallback
            var ogTitle = document.querySelector('meta[property="og:title"]')?.content || document.title || '';
            var ogImage = document.querySelector('meta[property="og:image"]')?.content || '';
            var ogVideo = document.querySelector('meta[property="og:video"]')?.content || '';
            if (ogVideo && !seen[ogVideo]) {
                seen[ogVideo] = true;
                results.push({
                    url: ogVideo,
                    type: 'video',
                    title: ogTitle,
                    thumbnail: ogImage,
                    source: 'HTML5_VIDEO'
                });
            }
            
            function scanNodeForMedia(node) {
                if (!node || !node.querySelectorAll) return;
                
                // Shadow DOM recursion
                if (node.shadowRoot) {
                    scanNodeForMedia(node.shadowRoot);
                }
                
                node.querySelectorAll('*').forEach(function(el) {
                    if (el.shadowRoot) {
                        scanNodeForMedia(el.shadowRoot);
                    }
                });

                // Videos
                var videos = node.querySelectorAll('video');
                videos.forEach(function(v) {
                    var src = v.currentSrc || v.src;
                    if (src && !seen[src] && !src.startsWith('blob:')) {
                        seen[src] = true;
                        results.push({
                            url: src,
                            type: 'video',
                            title: ogTitle,
                            thumbnail: v.poster || ogImage,
                            source: 'HTML5_VIDEO'
                        });
                    }
                    var sources = v.querySelectorAll('source');
                    sources.forEach(function(s) {
                        var sSrc = s.src;
                        if (sSrc && !seen[sSrc] && !sSrc.startsWith('blob:')) {
                            seen[sSrc] = true;
                            results.push({
                                url: sSrc,
                                type: 'video',
                                title: ogTitle,
                                thumbnail: v.poster || ogImage,
                                source: 'HTML5_VIDEO'
                            });
                        }
                    });
                });

                // Iframes
                var iframes = node.querySelectorAll('iframe');
                iframes.forEach(function(f) {
                    var src = f.src;
                    if (src && !seen[src]) {
                        var lower = src.toLowerCase();
                        if (lower.includes('youtube.com/embed/') || lower.includes('vimeo.com/video/') || lower.includes('dailymotion.com/embed/')) {
                            seen[src] = true;
                            results.push({
                                url: src,
                                type: 'video',
                                title: ogTitle,
                                thumbnail: ogImage,
                                source: 'IFRAME'
                            });
                        }
                    }
                });
                
                // Links
                var links = node.querySelectorAll('a[href]');
                var count = 0;
                for (var i = 0; i < links.length && count < 30; i++) {
                    var href = links[i].href;
                    if (!href || seen[href]) continue;
                    var lower = href.toLowerCase();
                    if (lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.m3u8') ||
                        lower.includes('/watch?v=') || lower.includes('/shorts/') || lower.includes('/video/')) {
                        seen[href] = true;
                        count++;
                        results.push({
                            url: href,
                            type: lower.endsWith('.mp3') || lower.endsWith('.m4a') ? 'audio' : 'video',
                            title: links[i].innerText?.trim() || ogTitle,
                            thumbnail: ogImage,
                            source: 'DIRECT_MEDIA'
                        });
                    }
                }
            }

            scanNodeForMedia(document);
            return JSON.stringify(results);
        })();
    """.trimIndent()

    val inlineButtonInjectorScript: String = """
        (function() {
            try {
                if (window._tubevaultScriptInitialized) {
                    if (window._tubevaultScanVideos) window._tubevaultScanVideos();
                    return;
                }
                window._tubevaultScriptInitialized = true;

                function scanAndAttachButtons() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        if (video.dataset.tubevaultBtnInjected === 'true') return;
                        var rect = video.getBoundingClientRect();
                        if (rect.width < 80 || rect.height < 60) return;

                        video.dataset.tubevaultBtnInjected = 'true';

                        var btn = document.createElement('div');
                        btn.className = 'tubevault-inline-dl-badge';
                        btn.setAttribute('role', 'button');
                        btn.setAttribute('aria-label', 'Télécharger cette vidéo');
                        btn.innerHTML = '<span style="font-size: 14px; line-height: 1;">↓</span><span>Download</span>';

                        btn.style.cssText = 'position: absolute; z-index: 2147483640; background: rgba(225, 29, 72, 0.94); color: #ffffff; font-family: system-ui, -apple-system, Roboto, sans-serif; font-size: 12px; font-weight: 700; padding: 10px 16px; min-height: 44px; min-width: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; border-radius: 22px; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.45); cursor: pointer; user-select: none; border: 1.5px solid rgba(255, 255, 255, 0.5); backdrop-filter: blur(8px); transition: transform 0.15s ease, opacity 0.2s ease;';

                        btn.addEventListener('touchstart', function() { btn.style.transform = 'scale(0.95)'; }, { passive: true });
                        btn.addEventListener('touchend', function() { btn.style.transform = 'scale(1)'; }, { passive: true });

                        function syncPosition() {
                            if (!video.isConnected) {
                                btn.remove();
                                return;
                            }
                            var vRect = video.getBoundingClientRect();
                            if (vRect.width < 50 || vRect.height < 40 || vRect.bottom < 0 || vRect.top > window.innerHeight) {
                                btn.style.display = 'none';
                                return;
                            }
                            btn.style.display = 'inline-flex';
                            var top = vRect.top + window.scrollY + 12;
                            var right = (document.documentElement.clientWidth - (vRect.right + window.scrollX)) + 12;
                            if (top < 8) top = 8;
                            if (right < 8) right = 8;
                            btn.style.top = top + 'px';
                            btn.style.right = right + 'px';
                        }

                        btn.addEventListener('click', function(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            var src = video.currentSrc || video.src || '';
                            var poster = video.poster || '';
                            var title = document.title || 'Vidéo web';
                            if (window.TubeVaultBridge && typeof window.TubeVaultBridge.onVideoDownloadClicked === 'function') {
                                window.TubeVaultBridge.onVideoDownloadClicked(src, poster, title, window.location.href);
                            }
                        });

                        document.body.appendChild(btn);
                        syncPosition();

                        window.addEventListener('scroll', syncPosition, { passive: true });
                        window.addEventListener('resize', syncPosition, { passive: true });
                    });
                }

                window._tubevaultScanVideos = scanAndAttachButtons;
                scanAndAttachButtons();

                var timeout = null;
                var observer = new MutationObserver(function() {
                    if (timeout) clearTimeout(timeout);
                    timeout = setTimeout(scanAndAttachButtons, 1500);
                });
                observer.observe(document.body, { childList: true, subtree: true });
            } catch(e) {
                // Ignore
            }
        })();
    """.trimIndent()

    fun onPageUrlChanged(pageUrl: String) {
        clear()
        val detected = VideoUrlDetector.detectVideo(pageUrl)
        if (detected != null) {
            val candidate = MediaCandidate(
                id = "${detected.platform.name}_${detected.mediaId ?: UUID.randomUUID()}",
                pageUrl = pageUrl,
                canonicalUrl = detected.originalUrl,
                platform = detected.platform,
                type = "video",
                sourceType = MediaSourceType.PLATFORM_PAGE,
                detectionConfidence = 1.0f
            )
            addCandidate(candidate)
        }
    }

    fun onNetworkRequest(pageUrl: String, requestUrl: String, mimeType: String? = null) {
        val lower = requestUrl.lowercase()
        if (lower.contains("google-analytics") || lower.contains("doubleclick") || lower.contains(".ts")) {
            return
        }

        val sourceType = when {
            lower.contains(".m3u8") -> MediaSourceType.HLS
            lower.contains(".mpd") -> MediaSourceType.DASH
            lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m4v") || lower.contains(".mov") -> MediaSourceType.DIRECT_MEDIA
            lower.contains(".mp3") || lower.contains(".m4a") -> MediaSourceType.DIRECT_MEDIA
            mimeType?.startsWith("video/") == true -> MediaSourceType.DIRECT_MEDIA
            mimeType?.startsWith("audio/") == true -> MediaSourceType.DIRECT_MEDIA
            else -> return
        }

        val ext = when {
            lower.contains(".mp4") -> "mp4"
            lower.contains(".webm") -> "webm"
            lower.contains(".m3u8") -> "m3u8"
            lower.contains(".mpd") -> "mpd"
            lower.contains(".mp3") -> "mp3"
            lower.contains(".m4a") -> "m4a"
            else -> "mp4"
        }

        val type = if (lower.contains(".mp3") || lower.contains(".m4a") || mimeType?.startsWith("audio/") == true) "audio" else "video"

        val defaultFormat = MediaFormat(
            quality = if (type == "audio") "Audio Direct" else "Direct Media",
            downloadUrl = requestUrl,
            extension = ext,
            container = ext.uppercase()
        )

        val candidate = MediaCandidate(
            id = "NET_${requestUrl.hashCode()}",
            pageUrl = pageUrl,
            mediaUrl = requestUrl,
            canonicalUrl = pageUrl,
            platform = Platform.detect(pageUrl),
            type = type,
            extension = ext,
            sourceType = sourceType,
            detectionConfidence = 0.85f,
            availableFormats = listOf(defaultFormat)
        )

        addCandidate(candidate)
    }

    fun onHtml5ScanResult(pageUrl: String, jsonString: String?) {
        if (jsonString.isNullOrBlank() || jsonString == "null") return

        try {
            val unescaped = if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                org.json.JSONTokener(jsonString).nextValue().toString()
            } else {
                jsonString
            }

            val array = JSONArray(unescaped)
            val newCandidates = mutableListOf<MediaCandidate>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val url = obj.optString("url")
                if (url.isBlank()) continue

                val title = obj.optString("title").ifBlank { null }
                val thumbnail = obj.optString("thumbnail").ifBlank { null }
                val type = obj.optString("type", "video")
                val sourceStr = obj.optString("source", "HTML5_VIDEO")
                val sourceType = try { MediaSourceType.valueOf(sourceStr) } catch(e: Exception) { MediaSourceType.HTML5_VIDEO }

                val detected = VideoUrlDetector.detectVideo(url)
                val platform = detected?.platform ?: Platform.detect(url)

                val candidate = MediaCandidate(
                    id = "DOM_${url.hashCode()}",
                    pageUrl = pageUrl,
                    mediaUrl = if (detected != null) null else url,
                    canonicalUrl = url,
                    platform = platform,
                    type = type,
                    title = title,
                    thumbnail = thumbnail,
                    sourceType = if (detected != null) MediaSourceType.PLATFORM_PAGE else sourceType,
                    detectionConfidence = 0.95f,
                    availableFormats = if (detected == null) {
                        val ext = url.substringAfterLast(".", "mp4").substringBefore("?").lowercase()
                        listOf(MediaFormat(quality = "HTML5 Stream", downloadUrl = url, extension = ext))
                    } else emptyList()
                )
                newCandidates.add(candidate)
            }

            if (newCandidates.isNotEmpty()) {
                _detectedCandidates.update { current ->
                    MediaCandidateDeduplicator.deduplicate(current + newCandidates).take(50)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun addCandidate(candidate: MediaCandidate) {
        _detectedCandidates.update { current ->
            MediaCandidateDeduplicator.deduplicate(listOf(candidate) + current).take(50)
        }
    }

    fun removeCandidate(candidateId: String) {
        _detectedCandidates.update { current ->
            current.filterNot { it.id == candidateId }
        }
    }

    fun clear() {
        _detectedCandidates.value = emptyList()
    }
}
