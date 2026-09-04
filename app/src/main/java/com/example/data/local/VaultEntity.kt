package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    val vaultId: Long = 0,
    val originalMediaId: Long? = null,
    val encryptedFileReference: String,
    val encryptedThumbnailReference: String? = null,
    val encryptedMetadataJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val encryptionVersion: Int = 1,
    val status: String = "Ready" // "Encrypting", "Ready", "Decrypting", "Error"
)

data class VaultDisplayMetadata(
    val title: String,
    val thumbnailUrl: String,
    val durationText: String,
    val resolution: String,
    val fileSizeBytes: Long,
    val sourceUrl: String,
    val platform: String,
    val notes: String? = null,
    val tags: String? = null
)
