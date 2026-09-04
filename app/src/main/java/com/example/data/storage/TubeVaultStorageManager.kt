package com.example.data.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.data.model.DownloadedVideo
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class StoragePublishResult(
    val contentUri: Uri,
    val displayName: String,
    val displayPath: String
)

object TubeVaultStorageManager {

    const val SUBFOLDER_NAME = "TubeVault"
    const val RELATIVE_DOWNLOADS_PATH = "Download/TubeVault"

    fun getSafeDisplayName(title: String, quality: String, extension: String): String {
        val sanitizedTitle = title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "Video_${System.currentTimeMillis()}" }
            .take(60)

        val cleanExt = extension.removePrefix(".").trim().ifBlank { "mp4" }
        val qualityTag = if (quality.isNotBlank() &&
            !quality.equals("unknown", ignoreCase = true) &&
            !quality.equals("recommended", ignoreCase = true) &&
            !quality.equals("best", ignoreCase = true)
        ) {
            val qClean = quality.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            " [$qClean]"
        } else ""

        return "$sanitizedTitle$qualityTag.$cleanExt"
    }

    fun getMimeType(extension: String): String {
        val ext = extension.removePrefix(".").lowercase()
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!fromMap.isNullOrBlank()) return fromMap
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            else -> "video/mp4"
        }
    }

    /**
     * Publishes a completed, verified temp file into the public Download/TubeVault folder using MediaStore.
     * Uses Scoped Storage on Android 10+ (API 29+) with IS_PENDING = 1 during writing, and 0 on completion.
     */
    suspend fun publishVerifiedFile(
        context: Context,
        sourceFile: File,
        title: String,
        quality: String,
        extension: String
    ): Result<StoragePublishResult> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Fichier source inexistant ou vide"))
        }

        val displayName = getSafeDisplayName(title, quality, extension)
        val mimeType = getMimeType(extension)
        val contentResolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var insertedUri: Uri? = null
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/$SUBFOLDER_NAME")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                insertedUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Impossible de créer l'entrée MediaStore"))

                contentResolver.openOutputStream(insertedUri, "w")?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                    output.flush()
                } ?: run {
                    contentResolver.delete(insertedUri, null, null)
                    return@withContext Result.failure(IllegalStateException("Impossible d'ouvrir le flux MediaStore en écriture"))
                }

                val finalValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(insertedUri, finalValues, null, null)

                Result.success(
                    StoragePublishResult(
                        contentUri = insertedUri,
                        displayName = displayName,
                        displayPath = "$RELATIVE_DOWNLOADS_PATH/$displayName"
                    )
                )
            } catch (e: Exception) {
                insertedUri?.let {
                    try {
                        contentResolver.delete(it, null, null)
                    } catch (_: Exception) {}
                }
                Result.failure(e)
            }
        } else {
            // Android 9 (API 28) and below fallback
            try {
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SUBFOLDER_NAME
                )
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                val destFile = File(publicDir, displayName)
                sourceFile.copyTo(destFile, overwrite = true)

                val fileUri = Uri.fromFile(destFile)
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                } catch (_: Exception) {}

                Result.success(
                    StoragePublishResult(
                        contentUri = fileUri,
                        displayName = displayName,
                        displayPath = destFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Safely deletes a media item from MediaStore or local filesystem.
     */
    suspend fun deleteVideoMedia(context: Context, contentUriString: String?, filePath: String?) = withContext(Dispatchers.IO) {
        if (!contentUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(contentUriString)
                if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    context.contentResolver.delete(uri, null, null)
                } else if (uri.scheme == "file") {
                    uri.path?.let { File(it).delete() }
                }
            } catch (_: Exception) {
                // Ignore if already deleted or security restriction
            }
        }
        if (!filePath.isNullOrBlank()) {
            try {
                val f = File(filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Migrates legacy downloads stored inside app-private filesDir/downloaded_videos into Download/TubeVault via MediaStore.
     * Never destroys the only valid copy: only removes the private file after successful MediaStore publication and DB update!
     */
    suspend fun migrateLegacyDownloads(context: Context, repository: VideoRepository) = withContext(Dispatchers.IO) {
        try {
            val allVideos = repository.getAllVideosList()
            val privateDir = File(context.filesDir, "downloaded_videos")

            for (video in allVideos) {
                // If already published to MediaStore content URI, skip
                if (!video.contentUri.isNullOrBlank() && video.contentUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    continue
                }

                val localFile = File(video.filePath)
                val candidateFile = if (localFile.exists() && localFile.length() > 0L) {
                    localFile
                } else {
                    val alt = File(privateDir, localFile.name)
                    if (alt.exists() && alt.length() > 0L) alt else null
                }

                if (candidateFile != null && candidateFile.exists() && candidateFile.length() > 0L) {
                    val ext = candidateFile.extension.ifBlank { "mp4" }
                    val pubResult = publishVerifiedFile(
                        context = context,
                        sourceFile = candidateFile,
                        title = video.title,
                        quality = video.resolution,
                        extension = ext
                    )

                    if (pubResult.isSuccess) {
                        val published = pubResult.getOrThrow()
                        val updatedVideo = video.copy(
                            contentUri = published.contentUri.toString(),
                            filePath = published.displayPath
                        )
                        repository.updateVideo(updatedVideo)

                        // Safe to delete legacy private file now that public copy is active
                        try {
                            candidateFile.delete()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal; preserves existing state
        }
    }
}
