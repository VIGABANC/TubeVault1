package com.example.data.service

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.example.data.local.VaultDisplayMetadata
import com.example.data.local.VaultEntity
import com.example.data.model.DownloadedVideo
import com.example.data.repository.VaultRepository
import com.example.data.security.VaultCryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VaultManager(
    private val context: Context,
    private val vaultRepository: VaultRepository
) {
    private val cryptoManager = VaultCryptoManager()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val metadataAdapter = moshi.adapter(VaultDisplayMetadata::class.java)

    private val vaultDir: File = File(context.filesDir, "vault_media").apply {
        if (!exists()) mkdirs()
    }

    private val tempPlaybackDir: File = File(context.cacheDir, "vault_playback").apply {
        if (!exists()) mkdirs()
    }

    private val activePlaybackFiles = java.util.Collections.synchronizedSet(mutableSetOf<File>())

    init {
        // Startup cleanup of stale playback files
        clearPlaybackCache()
    }

    suspend fun moveToVault(video: DownloadedVideo): Result<Long> = withContext(Dispatchers.IO) {
        val sourceFile = File(video.filePath)
        if (!sourceFile.exists()) {
            return@withContext Result.failure(Exception("Original media file not found"))
        }

        // Check free space
        val stat = StatFs(vaultDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        if (availableBytes < sourceFile.length() * 2) {
            return@withContext Result.failure(Exception("Not enough storage space for encryption"))
        }

        val encryptedFileName = "vault_${UUID.randomUUID()}.enc"
        val encryptedFile = File(vaultDir, encryptedFileName)
        val originalLength = sourceFile.length()

        // 1. Serialize & Encrypt Metadata
        val displayMeta = VaultDisplayMetadata(
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            durationText = video.durationText,
            resolution = video.resolution,
            fileSizeBytes = originalLength,
            sourceUrl = video.sourceUrl,
            platform = video.platform,
            notes = video.shortSummary,
            tags = video.userApprovedTags ?: video.aiSuggestedTags
        )
        val metaJson = metadataAdapter.toJson(displayMeta)
        val encryptedMetaJson = cryptoManager.encryptString(metaJson)

        // 2. Persist initial entity state: "ENCRYPTING"
        var vaultEntity = VaultEntity(
            originalMediaId = video.id,
            encryptedFileReference = encryptedFile.absolutePath,
            encryptedThumbnailReference = null,
            encryptedMetadataJson = encryptedMetaJson,
            status = "ENCRYPTING"
        )
        val vaultId = vaultRepository.insertVaultItem(vaultEntity)
        if (vaultId <= 0) {
            return@withContext Result.failure(Exception("Failed to initialize vault record"))
        }
        vaultEntity = vaultEntity.copy(vaultId = vaultId)

        try {
            // 3. Encrypt plaintext input -> encrypted output
            val encryptionSuccess = cryptoManager.encryptFile(sourceFile, encryptedFile)
            if (!encryptionSuccess || !encryptedFile.exists()) {
                encryptedFile.delete()
                vaultRepository.deleteVaultItem(vaultEntity)
                return@withContext Result.failure(Exception("Encryption failed"))
            }

            // 4. Update status to "VERIFYING"
            vaultEntity = vaultEntity.copy(status = "VERIFYING")
            vaultRepository.updateVaultItem(vaultEntity)

            // 5. Authenticate complete ciphertext/tag through AES-GCM doFinal() and verify size
            val isValid = cryptoManager.verifyEncryptedFile(encryptedFile, expectedLength = originalLength)
            if (!isValid) {
                encryptedFile.delete()
                vaultEntity = vaultEntity.copy(status = "ERROR")
                vaultRepository.updateVaultItem(vaultEntity)
                return@withContext Result.failure(Exception("Encrypted file authentication failed. Original preserved."))
            }

            // 6. Update status to "READY"
            vaultEntity = vaultEntity.copy(status = "READY")
            vaultRepository.updateVaultItem(vaultEntity)

            // 7. Only now safely delete original file
            try {
                if (sourceFile.exists()) {
                    sourceFile.delete()
                }
            } catch (e: Exception) {
                Log.w("VaultManager", "Failed to delete original file after moving to vault", e)
            }

            Result.success(vaultId)
        } catch (e: Exception) {
            Log.e("VaultManager", "Error moving to vault: ${e.message}", e)
            try {
                if (encryptedFile.exists()) encryptedFile.delete()
                vaultRepository.deleteVaultItem(vaultEntity)
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    suspend fun decryptToDestination(vaultItem: VaultEntity, destinationFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultItem.encryptedFileReference)
            if (!encryptedFile.exists()) {
                return@withContext Result.failure(Exception("Encrypted vault file missing"))
            }

            FileOutputStream(destinationFile).use { fos ->
                val success = cryptoManager.decryptFileToStream(encryptedFile, fos)
                if (!success) {
                    destinationFile.delete()
                    return@withContext Result.failure(Exception("Decryption failed"))
                }
            }

            // Verify output file
            if (!destinationFile.exists() || destinationFile.length() == 0L) {
                destinationFile.delete()
                return@withContext Result.failure(Exception("Decrypted destination file invalid"))
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e("VaultManager", "Error moving out from vault: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDecryptedPlaybackFile(vaultItem: VaultEntity): File? = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultItem.encryptedFileReference)
            if (!encryptedFile.exists()) return@withContext null

            val tempFile = File(tempPlaybackDir, "vplay_${UUID.randomUUID()}.mp4")
            FileOutputStream(tempFile).use { fos ->
                val success = cryptoManager.decryptFileToStream(encryptedFile, fos)
                if (!success) {
                    tempFile.delete()
                    return@withContext null
                }
            }
            activePlaybackFiles.add(tempFile)
            tempFile
        } catch (e: Exception) {
            Log.e("VaultManager", "Error preparing playback file", e)
            null
        }
    }

    fun releasePlaybackFile(tempFile: File?) {
        if (tempFile == null) return
        try {
            activePlaybackFiles.remove(tempFile)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (_: Exception) {}
    }

    fun clearPlaybackCache() {
        try {
            synchronized(activePlaybackFiles) {
                activePlaybackFiles.forEach { file ->
                    try { if (file.exists()) file.delete() } catch (_: Exception) {}
                }
                activePlaybackFiles.clear()
            }
            if (tempPlaybackDir.exists()) {
                tempPlaybackDir.listFiles()?.forEach { 
                    try { it.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun recoverAndCleanInterruptedVaultItems() = withContext(Dispatchers.IO) {
        try {
            val allItems = vaultRepository.getAllVaultItemsSync()
            allItems.forEach { item ->
                if (item.status != "READY") {
                    val file = File(item.encryptedFileReference)
                    if (file.exists()) file.delete()
                    vaultRepository.deleteVaultItem(item)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteVaultItem(vaultItem: VaultEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(vaultItem.encryptedFileReference)
            if (file.exists()) file.delete()
            vaultItem.encryptedThumbnailReference?.let {
                val thumb = File(it)
                if (thumb.exists()) thumb.delete()
            }
            vaultRepository.deleteVaultItem(vaultItem)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getDecryptedMetadata(vaultItem: VaultEntity): VaultDisplayMetadata? {
        return try {
            val json = cryptoManager.decryptString(vaultItem.encryptedMetadataJson)
            metadataAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
