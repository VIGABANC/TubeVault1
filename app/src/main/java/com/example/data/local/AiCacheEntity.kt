package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val inputFingerprint: String,
    val operationType: String, // e.g. "tags", "summary_short", "summary_detailed", "suggested_title", "category_topics", "chapters"
    val providerModelIdentifier: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val result: String // JSON response or plain text
)
