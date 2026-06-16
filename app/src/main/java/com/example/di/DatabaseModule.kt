package com.example.di

import android.content.Context
import com.example.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideRecentFileDao(database: AppDatabase): RecentFileDao {
        return database.recentFileDao()
    }

    @Provides
    fun provideNetworkConnectionDao(database: AppDatabase): NetworkConnectionDao {
        return database.networkConnectionDao()
    }

    @Provides
    fun provideVaultKeyDao(database: AppDatabase): VaultKeyDao {
        return database.vaultKeyDao()
    }

    @Provides
    fun provideFileCacheDao(database: AppDatabase): FileCacheDao {
        return database.fileCacheDao()
    }

    @Provides
    fun provideVaultSettingsDao(database: AppDatabase): VaultSettingsDao {
        return database.vaultSettingsDao()
    }

    @Provides
    fun provideFileMetadataDao(database: AppDatabase): FileMetadataDao {
        return database.fileMetadataDao()
    }
}
