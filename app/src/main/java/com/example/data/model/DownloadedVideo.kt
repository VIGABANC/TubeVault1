package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_videos")
data class DownloadedVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val thumbnailUrl: String,
    val durationText: String,
    val resolution: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sourceUrl: String,
    val platform: String = "youtube",
    val downloadTimestamp: Long = System.currentTimeMillis(),
    
    // AI Features fields
    val aiSuggestedTags: String? = null,
    val userApprovedTags: String? = null,
    val shortSummary: String? = null,
    val detailedSummary: String? = null,
    val summarySource: String? = null, // "metadata" or "transcript"
    val suggestedTitle: String? = null,
    val primaryCategory: String? = null,
    val topics: String? = null, // Comma separated list of topics
    val transcriptJson: String? = null, // JSON containing the serialized AiTranscript
    val aiChaptersJson: String? = null, // JSON containing list of AiChapter
    val isPrivate: Boolean = false
)
