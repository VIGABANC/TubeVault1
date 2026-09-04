package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaFormat
import com.example.data.model.MediaInfo
import com.example.data.model.Platform
import com.example.data.model.TaskPriority

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val sourceUrl: String,
    val canonicalUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val durationText: String,
    val author: String?,
    val platform: String,
    val selectedQuality: String,
    val selectedExtension: String,
    val selectedDownloadUrl: String,
    val selectedDirectUrl: String?,
    val status: String,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val priority: String,
    val retryCount: Int,
    val isSegmented: Boolean,
    val segmentsCount: Int,
    val segmentsCompleted: Int = 0,
    val localFilePath: String?,
    val tempFilePath: String?,
    val etag: String?,
    val lastModified: String?,
    val errorMessage: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDownloadTask(): DownloadTask {
        val meta = MediaInfo(
            title = title,
            thumbnailUrl = thumbnailUrl,
            durationText = durationText,
            author = author,
            sourceUrl = sourceUrl,
            originalUrl = canonicalUrl,
            platform = Platform.fromString(platform),
            formats = listOf(
                MediaFormat(
                    quality = selectedQuality,
                    downloadUrl = selectedDownloadUrl,
                    directUrl = selectedDirectUrl,
                    extension = selectedExtension
                )
            )
        )
        val format = meta.formats.first()
        val mappedStatus = when (status) {
            "DOWNLOADING" -> DownloadStatus.PAUSED
            "QUEUED" -> DownloadStatus.QUEUED
            "PAUSED" -> DownloadStatus.PAUSED
            "WAITING_FOR_NETWORK" -> DownloadStatus.WAITING_FOR_NETWORK
            "WAITING_FOR_WIFI" -> DownloadStatus.WAITING_FOR_WIFI
            "COMPLETED" -> DownloadStatus.COMPLETED
            "FAILED" -> DownloadStatus.FAILED
            "CANCELLED" -> DownloadStatus.CANCELLED
            else -> DownloadStatus.PAUSED
        }
        val mappedPriority = try {
            TaskPriority.valueOf(priority)
        } catch (_: Exception) {
            TaskPriority.NORMAL
        }

        return DownloadTask(
            id = id,
            metadata = meta,
            selectedFormat = format,
            platform = Platform.fromString(platform),
            status = mappedStatus,
            progress = progress,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            priority = mappedPriority,
            retryCount = retryCount,
            isSegmented = isSegmented,
            segmentsCount = segmentsCount,
            localFilePath = localFilePath,
            errorMessage = errorMessage,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromTask(
            task: DownloadTask,
            etag: String? = null,
            lastModified: String? = null,
            tempFilePath: String? = null,
            segmentsCompleted: Int = 0
        ): DownloadTaskEntity {
            return DownloadTaskEntity(
                id = task.id,
                sourceUrl = task.metadata.sourceUrl,
                canonicalUrl = task.metadata.originalUrl ?: task.metadata.sourceUrl,
                title = task.metadata.title,
                thumbnailUrl = task.metadata.thumbnailUrl,
                durationText = task.metadata.durationText,
                author = task.metadata.author,
                platform = task.platform.name,
                selectedQuality = task.selectedFormat.quality,
                selectedExtension = task.selectedFormat.extension,
                selectedDownloadUrl = task.selectedFormat.downloadUrl,
                selectedDirectUrl = task.selectedFormat.directUrl,
                status = task.status.name,
                progress = task.progress,
                bytesDownloaded = task.bytesDownloaded,
                totalBytes = task.totalBytes,
                priority = task.priority.name,
                retryCount = task.retryCount,
                isSegmented = task.isSegmented,
                segmentsCount = task.segmentsCount,
                segmentsCompleted = segmentsCompleted,
                localFilePath = task.localFilePath,
                tempFilePath = tempFilePath,
                etag = etag,
                lastModified = lastModified,
                errorMessage = task.errorMessage,
                createdAt = task.createdAt
            )
        }
    }
}
