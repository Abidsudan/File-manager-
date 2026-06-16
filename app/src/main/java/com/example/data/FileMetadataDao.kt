package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileItemEntity>)

    @Update
    suspend fun updateFile(file: FileItemEntity)

    @Delete
    suspend fun deleteFile(file: FileItemEntity)

    @Query("DELETE FROM file_cache WHERE path = :path")
    suspend fun deleteFileByPath(path: String)

    @Query("SELECT * FROM file_cache WHERE path = :path LIMIT 1")
    suspend fun getFileByPath(path: String): FileItemEntity?

    @Query("SELECT * FROM file_cache WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    fun getFilesInDirectory(parentPath: String): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM file_cache WHERE name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC")
    fun searchFiles(query: String): Flow<List<FileItemEntity>>

    @Query("DELETE FROM file_cache")
    suspend fun clearCache()
}
