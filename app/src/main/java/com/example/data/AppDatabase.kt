package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BookmarkEntity::class,
        RecentFileEntity::class,
        NetworkConnectionEntity::class,
        VaultKeyEntity::class,
        FileItemEntity::class,
        VaultSettingsEntity::class,
        FileEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(UriConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun networkConnectionDao(): NetworkConnectionDao
    abstract fun vaultKeyDao(): VaultKeyDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun vaultSettingsDao(): VaultSettingsDao
    abstract fun fileMetadataDao(): FileMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "file_manager_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
