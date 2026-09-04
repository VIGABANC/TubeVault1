package com.example.data.detector

import android.net.Uri
import com.example.data.engine.ExtractorEngine
import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import com.example.util.VideoUrlDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MediaDetector {

    private val _detectedCandidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val detectedCandidates: StateFlow<List<MediaCandidate>> = _detectedCandidates.asStateFlow()

    val html5ScannerScript: String = """
        (function() {
            try {
                var results = [];
                var seen = {};
                var baseUri = document.baseURI || window.location.href;

                function toAbsoluteUrl(rel) {
                    if (!rel) return null;
                    try {
                        return new URL(rel, baseUri).href;
                    } catch(e) {
                        return rel;
                    }
                }

                // Layer H: Page Context Fallbacks
                var pageTitle = (document.querySelector('meta[property="og:title"]')?.content ||
                                 document.querySelector('meta[name="twitter:title"]')?.content ||
                                 document.querySelector('h1')?.innerText?.trim() ||
                                 document.title || '').trim();

                var pageThumb = (document.querySelector('meta[property="og:image"]')?.content ||
                                 document.querySelector('meta[name="twitter:image"]')?.content || '').trim();

                function addCandidate(url, type, title, thumb, source, duration, quality) {
                    if (!url) return;
                    var absUrl = toAbsoluteUrl(url);
                    if (!absUrl || seen[absUrl] || absUrl.startsWith('blob:') || absUrl.startsWith('javascript:') || absUrl.startsWith('data:')) return;
                    seen[absUrl] = true;
                    results.push({
                        url: absUrl,
                        type: type || 'video',
                        title: (title && title.trim().length > 0) ? title.trim() : pageTitle,
                        thumbnail: (thumb && thumb.trim().length > 0) ? toAbsoluteUrl(thumb) : (pageThumb ? toAbsoluteUrl(pageThumb) : null),
                        source: source || 'HTML5_VIDEO',
                        duration: duration || null,
                        quality: quality || null
                    });
                }

                // Layer B: OpenGraph & Twitter tags
                var ogVideos = [
                    document.querySelector('meta[property="og:video:secure_url"]')?.content,
                    document.querySelector('meta[property="og:video:url"]')?.content,
                    document.querySelector('meta[property="og:video"]')?.content,
                    document.querySelector('meta[name="twitter:player:stream"]')?.content
                ];
                ogVideos.forEach(function(v) { if (v) addCandidate(v, 'video', pageTitle, pageThumb, 'OPEN_GRAPH'); });

                var ogAudios = [
                    document.querySelector('meta[property="og:audio:secure_url"]')?.content,
                    document.querySelector('meta[property="og:audio"]')?.content
                ];
                ogAudios.forEach(function(a) { if (a) addCandidate(a, 'audio', pageTitle, pageThumb, 'OPEN_GRAPH'); });

                // Layer C: JSON-LD Structured Data
                var jsonLdScripts = document.querySelectorAll('script[type="application/ld+json"]');
                jsonLdScripts.forEach(function(s) {
                    try {
                        var json = JSON.parse(s.innerText);
                        var items = Array.isArray(json) ? json : (json['@graph'] ? json['@graph'] : [json]);
                        items.forEach(function(item) {
                            if (!item) return;
                            var itType = item['@type'];
                            var isVideo = itType === 'VideoObject' || itType === 'Clip';
                            var isAudio = itType === 'AudioObject' || itType === 'MusicRecording';
                            if (isVideo || isAudio) {
                                var cUrl = item.contentUrl || item.embedUrl;
                                var title = item.name || item.headline || pageTitle;
                                var thumb = Array.isArray(item.thumbnailUrl) ? item.thumbnailUrl[0] : item.thumbnailUrl;
                                var dur = item.duration;
                                if (cUrl) {
                                    addCandidate(cUrl, isAudio ? 'audio' : 'video', title, thumb, 'JSON_LD', dur);
                                }
                            }
                        });
                    } catch(e) {}
                });

                // Layer D: JavaScript / Page State & Embeds Regex
                try {
                    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails) {
                        var vd = window.ytInitialPlayerResponse.videoDetails;
                        if (vd.videoId) {
                            addCandidate('https://www.youtube.com/watch?v=' + vd.videoId, 'video', vd.title, vd.thumbnail?.thumbnails?.[0]?.url, 'PLATFORM_PAGE');
                        }
                    }
                } catch(e) {}

                var scripts = document.querySelectorAll('script:not([src])');
                var mediaRegex = /(https?:\/\/[^"'\s\\]+\.(?:mp4|webm|m3u8|mpd)(?:\?[^"'\s\\]*)?)/gi;
                for (var sIdx = 0; sIdx < Math.min(scripts.length, 25); sIdx++) {
                    var text = scripts[sIdx].innerText;
                    if (text && (text.indexOf('.mp4') !== -1 || text.indexOf('.m3u8') !== -1)) {
                        var match;
                        while ((match = mediaRegex.exec(text)) !== null && results.length < 35) {
                            var mUrl = match[1];
                            addCandidate(mUrl, 'video', pageTitle, pageThumb, 'INLINE_JS');
                        }
                    }
                }

                // Layer A, E, F, G: Shadow DOM recursive scanner
                function scanNode(root) {
                    if (!root || !root.querySelectorAll) return;

                    // Layer A: HTML5 Video & Audio (<video>, <audio>, <source>)
                    var videos = root.querySelectorAll('video');
                    videos.forEach(function(v) {
                        var vTitle = v.getAttribute('title') || v.getAttribute('aria-label') || pageTitle;
                        var poster = v.poster || pageThumb;
                        var dur = v.duration && !isNaN(v.duration) ? Math.round(v.duration) + 's' : null;
                        var qual = (v.videoWidth && v.videoHeight) ? v.videoWidth + 'x' + v.videoHeight : null;
                        var src = v.currentSrc || v.src;
                        if (src) addCandidate(src, 'video', vTitle, poster, 'HTML5_VIDEO', dur, qual);

                        var sources = v.querySelectorAll('source');
                        sources.forEach(function(s) {
                            if (s.src) addCandidate(s.src, 'video', vTitle, poster, 'HTML5_VIDEO', dur, s.getAttribute('size') || qual);
                        });
                    });

                    var audios = root.querySelectorAll('audio');
                    audios.forEach(function(a) {
                        var aTitle = a.getAttribute('title') || pageTitle;
                        var src = a.currentSrc || a.src;
                        if (src) addCandidate(src, 'audio', aTitle, pageThumb, 'HTML5_AUDIO');
                        var sources = a.querySelectorAll('source');
                        sources.forEach(function(s) {
                            if (s.src) addCandidate(s.src, 'audio', aTitle, pageThumb, 'HTML5_AUDIO');
                        });
                    });

                    // Layer E: iFrames
                    var iframes = root.querySelectorAll('iframe');
                    iframes.forEach(function(f) {
                        var fSrc = f.src;
                        if (!fSrc) return;
                        var lower = fSrc.toLowerCase();
                        if (lower.includes('youtube.com/embed/') || lower.includes('youtube-nocookie.com/embed/')) {
                            var vId = fSrc.split('/embed/')[1]?.split('?')[0];
                            if (vId) addCandidate('https://www.youtube.com/watch?v=' + vId, 'video', pageTitle, null, 'IFRAME');
                        } else if (lower.includes('vimeo.com/video/')) {
                            var vId = fSrc.split('/video/')[1]?.split('?')[0];
                            if (vId) addCandidate('https://vimeo.com/' + vId, 'video', pageTitle, null, 'IFRAME');
                        } else if (lower.includes('dailymotion.com/embed/video/')) {
                            var vId = fSrc.split('/video/')[1]?.split('?')[0];
                            if (vId) addCandidate('https://www.dailymotion.com/video/' + vId, 'video', pageTitle, null, 'IFRAME');
                        } else if (lower.includes('tiktok.com/embed/')) {
                            addCandidate(fSrc, 'video', pageTitle, null, 'IFRAME');
                        }
                    });

                    // Layer F: Direct Links on page
                    var links = root.querySelectorAll('a[href]');
                    var linkCount = 0;
                    for (var i = 0; i < links.length && linkCount < 50; i++) {
                        var href = links[i].href;
                        if (!href) continue;
                        var lower = href.toLowerCase();
                        var isVid = lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.mkv') ||
                                    lower.endsWith('.m4v') || lower.endsWith('.mov') || lower.endsWith('.m3u8') || lower.endsWith('.mpd');
                        var isAud = lower.endsWith('.mp3') || lower.endsWith('.m4a') || lower.endsWith('.aac') || lower.endsWith('.opus');
                        if (isVid || isAud) {
                            linkCount++;
                            var lTitle = links[i].innerText?.trim() || links[i].getAttribute('title') || pageTitle;
                            addCandidate(href, isAud ? 'audio' : 'video', lTitle, pageThumb, 'DIRECT_MEDIA');
                        }
                    }

                    // Layer G: Recursive Shadow DOM
                    var allElements = root.querySelectorAll('*');
                    for (var e = 0; e < allElements.length; e++) {
                        if (allElements[e].shadowRoot) {
                            scanNode(allElements[e].shadowRoot);
                        }
                    }
                }

                scanNode(document);
                return JSON.stringify(results);
            } catch(err) {
                return JSON.stringify([]);
            }
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

    /**
     * Layer I: Feeds the current page URL through ExtractorEngine.
     * If the engine resolves metadata/formats, it is merged into detected candidates.
     */
    fun scanPageUrlWithEngine(pageUrl: String, engine: ExtractorEngine, scope: CoroutineScope) {
        if (pageUrl.isBlank() || pageUrl == "about:blank") return
        scope.launch(Dispatchers.IO) {
            try {
                val result = engine.extractInfo(pageUrl)
                if (result.isSuccess) {
                    val meta = result.getOrThrow()
                    val candidate = MediaCandidate(
                        id = "ENG_${meta.id}",
                        pageUrl = pageUrl,
                        mediaUrl = meta.formats.firstOrNull()?.downloadUrl,
                        canonicalUrl = meta.originalUrl ?: meta.sourceUrl,
                        platform = meta.platform,
                        type = "video",
                        title = meta.title,
                        thumbnail = meta.thumbnailUrl,
                        duration = meta.durationText,
                        sourceType = MediaSourceType.PLATFORM_PAGE,
                        detectionConfidence = 1.0f,
                        availableFormats = meta.formats,
                        isResolved = true
                    )
                    addCandidate(candidate)
                }
            } catch (_: Exception) {}
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
                val duration = obj.optString("duration").ifBlank { null }
                val quality = obj.optString("quality").ifBlank { "Direct Source" }
                val type = obj.optString("type", "video")
                val sourceStr = obj.optString("source", "HTML5_VIDEO")
                val sourceType = try { MediaSourceType.valueOf(sourceStr) } catch(_: Exception) { MediaSourceType.HTML5_VIDEO }

                val detected = VideoUrlDetector.detectVideo(url)
                val platform = detected?.platform ?: Platform.detect(url)

                val ext = if (type == "audio") {
                    url.substringAfterLast(".", "mp3").substringBefore("?").lowercase()
                } else {
                    url.substringAfterLast(".", "mp4").substringBefore("?").lowercase()
                }

                val candidate = MediaCandidate(
                    id = "DOM_${url.hashCode()}",
                    pageUrl = pageUrl,
                    mediaUrl = if (detected != null) null else url,
                    canonicalUrl = url,
                    platform = platform,
                    type = type,
                    title = title,
                    thumbnail = thumbnail,
                    duration = duration,
                    sourceType = if (detected != null) MediaSourceType.PLATFORM_PAGE else sourceType,
                    detectionConfidence = 0.95f,
                    availableFormats = if (detected == null) {
                        listOf(MediaFormat(quality = quality, downloadUrl = url, extension = ext))
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

