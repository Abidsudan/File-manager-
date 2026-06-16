package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteBookmarkByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    suspend fun isBookmarked(path: String): Boolean
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY timestamp DESC LIMIT 30")
    fun getRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE path = :path")
    suspend fun deleteRecentByPath(path: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}

@Dao
interface NetworkConnectionDao {
    @Query("SELECT * FROM network_connections ORDER BY timestamp DESC")
    fun getAllConnections(): Flow<List<NetworkConnectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: NetworkConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: NetworkConnectionEntity)

    @Query("UPDATE network_connections SET isConnected = :connected WHERE id = :id")
    suspend fun updateConnectionStatus(id: Long, connected: Boolean)
}

@Dao
interface VaultKeyDao {
    @Query("SELECT * FROM vault_keys WHERE id = 'vault_key' LIMIT 1")
    suspend fun getVaultKey(): VaultKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVaultKey(vaultKey: VaultKeyEntity)

    @Query("DELETE FROM vault_keys WHERE id = 'vault_key'")
    suspend fun clearVault()
}

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM file_cache ORDER BY name ASC")
    fun getAllCachedFiles(): Flow<List<FileItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileItemEntity>)

    @Query("DELETE FROM file_cache WHERE path = :path")
    suspend fun deleteFileByPath(path: String)

    @Query("DELETE FROM file_cache")
    suspend fun clearCache()
}

@Dao
interface VaultSettingsDao {
    @Query("SELECT * FROM vault_settings WHERE id = 'vault_settings' LIMIT 1")
    fun getVaultSettingsFlow(): Flow<VaultSettingsEntity?>

    @Query("SELECT * FROM vault_settings WHERE id = 'vault_settings' LIMIT 1")
    suspend fun getVaultSettings(): VaultSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVaultSettings(settings: VaultSettingsEntity)

    @Query("DELETE FROM vault_settings WHERE id = 'vault_settings'")
    suspend fun clearVaultSettings()
}
