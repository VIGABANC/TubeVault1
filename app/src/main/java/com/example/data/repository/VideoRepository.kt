package com.example.data.repository

import android.content.Context
import com.example.data.local.VideoDao
import com.example.data.local.AiCacheEntity
import com.example.data.model.DownloadedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val videoDao: VideoDao
) {
    val allVideos: Flow<List<DownloadedVideo>> = videoDao.getAllVideos()

    suspend fun saveDownloadedVideo(video: DownloadedVideo): Long = withContext(Dispatchers.IO) {
        videoDao.insertVideo(video)
    }

    suspend fun deleteVideo(video: DownloadedVideo) = withContext(Dispatchers.IO) {
        // Also delete the physical file from internal scoped storage
        try {
            val file = File(video.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore failure if file was already removed
        }
        videoDao.deleteVideo(video)
    }

    suspend fun getVideoById(id: Long): DownloadedVideo? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)
    }

    suspend fun updateVideo(video: DownloadedVideo) = withContext(Dispatchers.IO) {
        videoDao.insertVideo(video)
    }

    suspend fun getCachedResult(mediaId: String, fingerprint: String, operationType: String): String? = withContext(Dispatchers.IO) {
        val cache = videoDao.getCache(mediaId, fingerprint, operationType)
        cache?.result
    }

    suspend fun saveCachedResult(mediaId: String, fingerprint: String, operationType: String, provider: String?, result: String) = withContext(Dispatchers.IO) {
        videoDao.insertCache(
            AiCacheEntity(
                mediaId = mediaId,
                inputFingerprint = fingerprint,
                operationType = operationType,
                providerModelIdentifier = provider,
                result = result
            )
        )
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        videoDao.clearCache()
    }
}
