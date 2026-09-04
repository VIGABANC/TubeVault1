package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun getAllVaultItems(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_items")
    suspend fun getAllVaultItemsSync(): List<VaultEntity>

    @Query("SELECT * FROM vault_items WHERE vaultId = :id LIMIT 1")
    suspend fun getVaultItemById(id: Long): VaultEntity?

    @androidx.room.Update
    suspend fun updateVaultItem(item: VaultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultEntity): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultEntity)

    @Query("DELETE FROM vault_items WHERE vaultId = :id")
    suspend fun deleteVaultItemById(id: Long)

    @Query("UPDATE vault_items SET status = :status WHERE vaultId = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE vault_items SET lastOpenedAt = :timestamp WHERE vaultId = :id")
    suspend fun updateLastOpenedAt(id: Long, timestamp: Long)
}
