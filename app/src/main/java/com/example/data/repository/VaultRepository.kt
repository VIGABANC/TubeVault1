package com.example.data.repository

import com.example.data.local.VaultDao
import com.example.data.local.VaultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VaultRepository(
    private val vaultDao: VaultDao
) {
    val allVaultItems: Flow<List<VaultEntity>> = vaultDao.getAllVaultItems()

    suspend fun getVaultItemById(id: Long): VaultEntity? = withContext(Dispatchers.IO) {
        vaultDao.getVaultItemById(id)
    }

    suspend fun insertVaultItem(item: VaultEntity): Long = withContext(Dispatchers.IO) {
        vaultDao.insertVaultItem(item)
    }

    suspend fun updateVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        vaultDao.updateVaultItem(item)
    }

    suspend fun getAllVaultItemsSync(): List<VaultEntity> = withContext(Dispatchers.IO) {
        vaultDao.getAllVaultItemsSync()
    }

    suspend fun deleteVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        vaultDao.deleteVaultItem(item)
    }

    suspend fun deleteVaultItemById(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.deleteVaultItemById(id)
    }

    suspend fun updateStatus(id: Long, status: String) = withContext(Dispatchers.IO) {
        vaultDao.updateStatus(id, status)
    }

    suspend fun updateLastOpened(id: Long) = withContext(Dispatchers.IO) {
        vaultDao.updateLastOpenedAt(id, System.currentTimeMillis())
    }
}
