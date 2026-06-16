package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val extension: String,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_connections")
data class NetworkConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "FTP", "SMB", "SFTP", "Google Drive", "OneDrive"
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_keys")
data class VaultKeyEntity(
    @PrimaryKey val id: String = "vault_key",
    val passwordHash: String,
    val salt: String,
    val isSetup: Boolean = true
)

@Entity(tableName = "file_cache")
data class FileItemEntity(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String = "",
    val parentPath: String = ""
)

@Entity(tableName = "vault_settings")
data class VaultSettingsEntity(
    @PrimaryKey val id: String = "vault_settings",
    val encryptedConfigData: String,
    val hashedPin: String = "",
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeoutMinutes: Int = 10,
    val isAutoLockEnabled: Boolean = true
)

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long
)

