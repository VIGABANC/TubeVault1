package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DownloadedVideo

@Database(
    entities = [DownloadedVideo::class, AiCacheEntity::class, VaultEntity::class, DownloadTaskEntity::class],
    version = 5,
    exportSchema = false
)
abstract class TubeVaultDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun vaultDao(): VaultDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_cache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` TEXT NOT NULL, `inputFingerprint` TEXT NOT NULL, `operationType` TEXT NOT NULL, `providerModelIdentifier` TEXT, `createdAt` INTEGER NOT NULL, `result` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vault_items` (`vaultId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalMediaId` INTEGER, `encryptedFileReference` TEXT NOT NULL, `encryptedThumbnailReference` TEXT, `encryptedMetadataJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `encryptionVersion` INTEGER NOT NULL, `status` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_tasks` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sourceUrl` TEXT NOT NULL,
                        `canonicalUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `durationText` TEXT NOT NULL,
                        `author` TEXT,
                        `platform` TEXT NOT NULL,
                        `selectedQuality` TEXT NOT NULL,
                        `selectedExtension` TEXT NOT NULL,
                        `selectedDownloadUrl` TEXT NOT NULL,
                        `selectedDirectUrl` TEXT,
                        `status` TEXT NOT NULL,
                        `progress` REAL NOT NULL,
                        `bytesDownloaded` INTEGER NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `priority` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `isSegmented` INTEGER NOT NULL,
                        `segmentsCount` INTEGER NOT NULL,
                        `segmentsCompleted` INTEGER NOT NULL,
                        `localFilePath` TEXT,
                        `tempFilePath` TEXT,
                        `etag` TEXT,
                        `lastModified` TEXT,
                        `errorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: TubeVaultDatabase? = null

        fun getDatabase(context: Context): TubeVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TubeVaultDatabase::class.java,
                    "tubevault.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
