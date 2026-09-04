package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DownloadedVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadTimestamp DESC")
    fun getAllVideos(): Flow<List<DownloadedVideo>>

    @Query("SELECT * FROM downloaded_videos")
    suspend fun getAllVideosList(): List<DownloadedVideo>

    @Query("SELECT * FROM downloaded_videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): DownloadedVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: DownloadedVideo): Long

    @androidx.room.Update
    suspend fun updateVideo(video: DownloadedVideo)

    @Delete
    suspend fun deleteVideo(video: DownloadedVideo)

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: AiCacheEntity)

    @Query("SELECT * FROM ai_cache WHERE mediaId = :mediaId AND inputFingerprint = :inputFingerprint AND operationType = :operationType LIMIT 1")
    suspend fun getCache(mediaId: String, inputFingerprint: String, operationType: String): AiCacheEntity?

    @Query("DELETE FROM ai_cache")
    suspend fun clearCache()
}
