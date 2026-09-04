package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.model.Platform
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeDownloadApiClient(private val defaultGatewayUrl: String? = null) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Extracts YouTube video ID from various standard YouTube URL formats.
     */
    fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        val patterns = listOf(
            Pattern.compile("(?:v=|v\\/|vi=|vi\\/|youtu\\.be\\/|embed\\/|shorts\\/)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("^[a-zA-Z0-9_-]{11}$")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(if (matcher.groupCount() >= 1) 1 else 0)
            }
        }
        return null
    }

    /**
     * Extracts media ID across supported platforms (YouTube, TikTok, Instagram, Twitter/X).
     */
    fun extractMediaId(url: String, platform: Platform): String? {
        val trimmed = url.trim()
        return when (platform) {
            Platform.YOUTUBE -> extractYouTubeId(trimmed)
            Platform.TIKTOK -> {
                val pattern = Pattern.compile("(?:video\\/|v\\/|vt\\.tiktok\\.com\\/|vm\\.tiktok\\.com\\/)([a-zA-Z0-9_-]+)")
                val matcher = pattern.matcher(trimmed)
                if (matcher.find()) matcher.group(1) else null
            }
            Platform.INSTAGRAM -> {
                val pattern = Pattern.compile("(?:p|reel|reels|tv)\\/([a-zA-Z0-9_-]+)")
                val matcher = pattern.matcher(trimmed)
                if (matcher.find()) matcher.group(1) else null
            }
            Platform.TWITTER -> {
                val pattern = Pattern.compile("status\\/([0-9]+)")
                val matcher = pattern.matcher(trimmed)
                if (matcher.find()) matcher.group(1) else null
            }
            Platform.OTHER -> null
        }
    }

    /**
     * Fetches metadata from configured HTTPS gateway or RapidAPI service.
     * No fake success or placeholder videos are returned.
     */
    suspend fun fetchMediaInfo(rawUrl: String, customGatewayOverride: String? = null): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        val platform = Platform.detect(trimmedUrl)

        // Check for direct media URL probe
        val lowerUrl = trimmedUrl.lowercase(Locale.US)
        val isDirectMedia = lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") ||
                lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".mp3") ||
                lowerUrl.contains(".mp4?") || lowerUrl.contains(".webm?") ||
                lowerUrl.contains(".m3u8?") || lowerUrl.contains(".mp3?")

        if (isDirectMedia) {
            val ext = when {
                lowerUrl.contains(".m3u8") -> "m3u8"
                lowerUrl.contains(".webm") -> "webm"
                lowerUrl.contains(".mp3") -> "mp3"
                else -> "mp4"
            }
            val fileName = trimmedUrl.substringBefore("?").substringAfterLast("/").ifBlank { "Media_$ext" }
            val directFormat = MediaFormat(
                quality = if (ext == "mp3") "Audio direct ($ext)" else "Flux direct ($ext)",
                downloadUrl = trimmedUrl,
                extension = ext
            )
            return@withContext Result.success(
                MediaInfo(
                    title = fileName,
                    thumbnailUrl = "",
                    durationText = "",
                    author = null,
                    sourceUrl = trimmedUrl,
                    platform = Platform.OTHER,
                    formats = listOf(directFormat)
                )
            )
        }

        val effectivePlatform = if (platform == Platform.OTHER && extractYouTubeId(trimmedUrl) != null) {
            Platform.YOUTUBE
        } else platform

        val gateway = customGatewayOverride?.takeIf { it.isNotBlank() }
            ?: defaultGatewayUrl?.takeIf { it.isNotBlank() }

        val apiKey = BuildConfig.DOWNLOAD_API_KEY.trim()
        val apiHost = BuildConfig.DOWNLOAD_API_HOST.trim()
        val isRapidApiConfigured = apiKey.isNotBlank() &&
                !apiKey.startsWith("YOUR_") &&
                !apiKey.contains("PLACEHOLDER", ignoreCase = true) &&
                apiHost.isNotBlank() &&
                !apiHost.startsWith("YOUR_") &&
                !apiHost.contains("PLACEHOLDER", ignoreCase = true)

        if (gateway.isNullOrBlank() && !isRapidApiConfigured) {
            return@withContext Result.failure(
                IllegalStateException("Service d'extraction non configuré. Veuillez configurer une passerelle d'extraction dans les Paramètres.")
            )
        }

        if (gateway.isNullOrBlank() && effectivePlatform == Platform.OTHER) {
            return@withContext Result.failure(
                IllegalArgumentException("Plateforme non prise en charge. Veuillez configurer une passerelle d'extraction personnalisée pour les sites génériques.")
            )
        }

        val mediaId = extractMediaId(trimmedUrl, effectivePlatform)
        val fallbackThumbnail = when (effectivePlatform) {
            Platform.YOUTUBE -> mediaId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" } ?: ""
            else -> ""
        }

        try {
            val encodedUrl = URLEncoder.encode(trimmedUrl, "UTF-8")
            val requestBuilder = Request.Builder()

            if (!gateway.isNullOrBlank()) {
                val connector = if (gateway.contains("?")) "&" else "?"
                requestBuilder.url("$gateway${connector}url=$encodedUrl")
            } else {
                val endpointUrl = if (apiHost.contains("/")) {
                    "https://$apiHost?url=$encodedUrl"
                } else {
                    "https://$apiHost/dl?url=$encodedUrl"
                }
                requestBuilder.url(endpointUrl)
                    .addHeader("X-RapidAPI-Key", apiKey)
                    .addHeader("X-RapidAPI-Host", apiHost.substringBefore("/"))
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Erreur du service d'extraction (HTTP ${response.code}).")
                )
            }

            val jsonString = response.body?.string() ?: ""
            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Réponse vide reçue du service d'extraction."))
            }

            val metadata = parseMetadataJson(jsonString, trimmedUrl, fallbackThumbnail)
            if (metadata.formats.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Aucun format téléchargeable trouvé pour ce média.")
                )
            }
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(
                Exception("Impossible de récupérer ce média : ${e.localizedMessage ?: "erreur réseau"}")
            )
        }
    }

    /**
     * Resilient parser handling multiple common RapidAPI YouTube downloader response structures.
     */
    fun parseMetadataJson(jsonString: String, originalUrl: String, fallbackThumbnail: String): MediaInfo {
        val root = JSONObject(jsonString)

        // Try direct root or nested under 'data' or 'result'
        val content = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root

        val title = content.optString("title").ifBlank {
            content.optString("video_title").ifBlank {
                content.optString("name").ifBlank {
                    "Vidéo YouTube (${System.currentTimeMillis() % 1000})"
                }
            }
        }

        // Thumbnail extraction
        val thumbnail = content.optString("thumbnail").ifBlank {
            content.optString("picture").ifBlank {
                content.optString("image").ifBlank {
                    content.optString("thumb").ifBlank {
                        val thumbsArray = content.optJSONArray("thumbnails")
                        thumbsArray?.optJSONObject(thumbsArray.length() - 1)?.optString("url") ?: fallbackThumbnail
                    }
                }
            }
        }

        // Duration extraction
        val durationRaw = content.opt("duration") ?: content.opt("lengthSeconds")
        val durationText = when (durationRaw) {
            is Number -> formatSeconds(durationRaw.toLong())
            is String -> if (durationRaw.isNotBlank()) durationRaw else "00:00"
            else -> "00:00"
        }

        // Formats extraction
        val formats = mutableListOf<MediaFormat>()

        // Check common array keys: "formats", "medias", "links", "videos", "download_urls"
        val formatsArray: JSONArray? = content.optJSONArray("formats")
            ?: content.optJSONArray("medias")
            ?: content.optJSONArray("links")
            ?: content.optJSONArray("videos")
            ?: root.optJSONArray("formats")

        if (formatsArray != null && formatsArray.length() > 0) {
            for (i in 0 until formatsArray.length()) {
                val fObj = formatsArray.optJSONObject(i) ?: continue
                val quality = fObj.optString("qualityLabel").ifBlank {
                    fObj.optString("quality").ifBlank {
                        fObj.optString("resolution").ifBlank {
                            fObj.optString("format").ifBlank { "Format #${i + 1}" }
                        }
                    }
                }
                val downloadUrl = fObj.optString("url").ifBlank {
                    fObj.optString("downloadUrl").ifBlank {
                        fObj.optString("link").ifBlank {
                            fObj.optString("download_url")
                        }
                    }
                }
                val ext = fObj.optString("extension").ifBlank {
                    fObj.optString("type").ifBlank { "mp4" }
                }
                val size = fObj.optString("formattedSize").ifBlank {
                    fObj.optString("size").ifBlank { null }
                }

                if (downloadUrl.isNotBlank() && downloadUrl.startsWith("http")) {
                    formats.add(
                        MediaFormat(
                            quality = quality,
                            downloadUrl = downloadUrl,
                            extension = ext,
                            approximateSize = size
                        )
                    )
                }
            }
        }

        // Single direct download url fallback (e.g. Cobalt-style { "url": "https://..." })
        if (formats.isEmpty()) {
            val directUrl = content.optString("url").ifBlank {
                content.optString("link").ifBlank {
                    content.optString("download_url")
                }
            }
            if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                formats.add(
                    MediaFormat(
                        quality = "Téléchargement standard (MP4)",
                        downloadUrl = directUrl,
                        extension = "mp4"
                    )
                )
            }
        }

        return MediaInfo(
            title = title,
            thumbnailUrl = thumbnail.ifBlank { fallbackThumbnail },
            durationText = durationText,
            author = content.optString("author").ifBlank { null },
            sourceUrl = originalUrl,
            platform = Platform.detect(originalUrl),
            formats = formats
        )
    }

    /**
     * Downloads file to app scoped storage with progressive callbacks.
     */
    suspend fun downloadVideoFile(
        context: Context,
        format: MediaFormat,
        title: String,
        onProgress: (progress: Float, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(format.downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Échec du téléchargement (HTTP ${response.code})")
                )
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Corps de réponse vide"))
            val totalBytes = body.contentLength()

            // Scoped storage directory
            val videosDir = File(context.filesDir, "downloaded_videos").apply {
                if (!exists()) mkdirs()
            }

            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(30)
            val extension = if (format.extension.isNotBlank()) format.extension else "mp4"
            val targetFile = File(videosDir, "${sanitizedTitle}_${System.currentTimeMillis()}.$extension")

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress = if (totalBytes > 0) {
                            (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f // Indeterminate fallback if server doesn't send Content-Length
                        }
                        onProgress(progress, totalRead, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(Exception("Erreur pendant le téléchargement : ${e.localizedMessage ?: "connexion interrompue"}"))
        }
    }

    private fun formatSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
