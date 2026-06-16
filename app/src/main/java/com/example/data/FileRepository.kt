package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val context: Context, private val bookmarkDao: BookmarkDao) {

    val rootPath: String = File(context.filesDir, "LocalStorage").absolutePath

    init {
        // Pre-populate sample directories and files if they do not exist
        try {
            val rootDir = File(rootPath)
            if (!rootDir.exists()) {
                rootDir.mkdirs()
                populateDemoFiles()
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error initializing local storage: ${e.message}")
        }
    }

    private fun populateDemoFiles() {
        // 1. Downloads folder
        val downloads = File(rootPath, "Downloads")
        downloads.mkdirs()
        File(downloads, "receipt_groceries.txt").writeText(
            "===================================\n" +
            "      WHOLE FOODS MARKET - TOK\n" +
            "===================================\n" +
            "1x Organic Avocados          $4.99\n" +
            "2x Almond Milk 1L            $6.50\n" +
            "1x Fresh Blueberries 500g    $5.99\n" +
            "1x Artisanal Sourdough Bread $4.50\n" +
            "-----------------------------------\n" +
            "SUBTOTAL                    $21.98\n" +
            "TAX (8.25%)                  $1.81\n" +
            "TOTAL                       $23.79\n" +
            "===================================\n" +
            "THANK YOU FOR SHOPPING PRIVATELY!\n" +
            "Date: June 15, 2026, 14:32\n" +
            "Payment: Biometric Debit Auth\n"
        )
        File(downloads, "project_guidelines.md").writeText(
            "# App Development Checklist\n\n" +
            "### Prerequisites\n" +
            "- [x] Design M3 interface using primary and secondary dynamic colors.\n" +
            "- [x] Add high contrast vector assets for adaptive circular launcher icons.\n" +
            "- [x] Integrate Jetpack Room database cache.\n\n" +
            "### Power Capabilities\n" +
            "- [ ] Implement local database bookmark state flow.\n" +
            "- [ ] Conduct recursive folder size analysis charts.\n" +
            "- [ ] Build AES-256 crypto safety vault with pincode overlay."
        )

        // 2. Documents folder
        val documents = File(rootPath, "Documents")
        documents.mkdirs()
        val projects = File(documents, "Projects")
        projects.mkdirs()
        File(projects, "android_roadmap.txt").writeText(
            "Quarterly Android Milestones (2026):\n\n" +
            "- Q1: Compose Desktop prototyping & layout stabilization.\n" +
            "- Q2: Advanced encryption library auditing for secure file transfer.\n" +
            "- Q3: Multi-device Wi-Fi direct synchronization nodes.\n" +
            "- Q4: Leanback layout integration & final release."
        )
        File(documents, "personal_journal.txt").writeText(
            "June 15, 2026\n" +
            "Had a lovely walk near the campus today. The air was fresh and crisp.\n" +
            "I'm super exited to finish compiling this file explorer app tomorrow. It is looking amazing!"
        )

        // 3. System Config folder
        val config = File(rootPath, "SystemConfig")
        config.mkdirs()
        File(config, "app_settings.json").writeText(
            "{\n" +
            "  \"theme\": \"DARK_COSMIC\",\n" +
            "  \"auto_sync\": false,\n" +
            "  \"active_protocol\": \"FTP\",\n" +
            "  \"grid_size\": 3,\n" +
            "  \"show_hidden\": true\n" +
            "}"
        )

        // 4. Archive folder
        val archive = File(rootPath, "Archive")
        archive.mkdirs()
        
        // Write a small ZIP file
        val sampleTxt = File(archive, "readme_first.txt")
        sampleTxt.writeText("This archive contains pre-packaged configuration assets.")
        
        val zipFile = File(archive, "demo_secrets.zip")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val entry = ZipEntry("readme_first.txt")
                zos.putNextEntry(entry)
                zos.write("This archive contains pre-packaged configuration assets.".toByteArray())
                zos.closeEntry()
                
                val innerEntry = ZipEntry("config_notes.txt")
                zos.putNextEntry(innerEntry)
                zos.write("Internal system configs: default_port=8080".toByteArray())
                zos.closeEntry()
            }
            sampleTxt.delete() // Cleanup temporary txt file
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

        val filesList = folder.listFiles() ?: return@withContext emptyList()
        filesList.map { file ->
            val ext = file.extension
            val isBookmarked = bookmarkDao.isBookmarked(file.absolutePath)
            FileItem(
                name = file.name,
                path = file.absolutePath,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = ext,
                mimeType = getMimeType(ext),
                isBookmarked = isBookmarked
            )
        }.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase()) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            "mp3", "wav" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    suspend fun createFile(parentPath: String, name: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(parentPath, name)
            if (file.exists()) return@withContext false
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createDirectory(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(parentPath, name)
            if (dir.exists()) return@withContext false
            dir.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext false
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(path)
            if (!source.exists()) return@withContext false

            val dest = File(source.parentFile, newName)
            if (dest.exists()) return@withContext false

            source.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun copyFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            var dest = File(destPath)
            if (!source.exists()) return@withContext false
            
            if (dest.isDirectory) {
                dest = File(dest, source.name)
            }

            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun moveFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destPath)
            if (copyFile(sourcePath, destPath)) {
                if (source.isDirectory) {
                    source.deleteRecursively()
                } else {
                    source.delete()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun compressToZip(sourcePaths: List<String>, zipFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(zipFilePath)
            if (zipFile.exists()) {
                zipFile.delete()
            }
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (path in sourcePaths) {
                    val file = File(path)
                    addFileToZip(file, file.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addFileToZip(file: File, baseName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                // Empty folder entry
                zos.putNextEntry(ZipEntry("$baseName/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    addFileToZip(child, "$baseName/${child.name}", zos)
                }
            }
        } else {
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(baseName))
                val buffer = ByteArray(4096)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
        }
    }

    suspend fun decompressZip(zipFilePath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destDirPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            ZipInputStream(FileInputStream(zipFilePath)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        // Create parent directories if needed
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun searchFiles(query: String, path: String = rootPath): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        searchRecursive(File(path), query, results)
        
        results.map { file ->
            val ext = file.extension
            val isBookmarked = bookmarkDao.isBookmarked(file.absolutePath)
            FileItem(
                name = file.name,
                path = file.absolutePath,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = ext,
                mimeType = getMimeType(ext),
                isBookmarked = isBookmarked
            )
        }
    }

    private fun searchRecursive(file: File, query: String, results: MutableList<File>) {
        if (file.name.contains(query, ignoreCase = true)) {
            results.add(file)
        }
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                searchRecursive(child, query, results)
            }
        }
    }

    fun getCategoryForExtension(ext: String): String {
        val cleanExt = ext.lowercase()
        return when {
            cleanExt in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") -> "Images"
            cleanExt in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp") -> "Videos"
            cleanExt in setOf("mp3", "wav", "aac", "ogg", "flac", "m4a") -> "Audio"
            cleanExt in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "json", "xml", "csv") -> "Documents"
            cleanExt in setOf("zip", "rar", "7z", "tar", "gz") -> "Archives"
            else -> "Other"
        }
    }

    suspend fun getStorageAnalysis(path: String = rootPath): StorageAnalysis = withContext(Dispatchers.IO) {
        val root = File(path)
        val stats = mutableMapOf(
            "Images" to 0L,
            "Videos" to 0L,
            "Audio" to 0L,
            "Documents" to 0L,
            "Archives" to 0L,
            "Other" to 0L
        )
        val allFilesList = mutableListOf<File>()
        var totalUsed = 0L

        gatherFilesRecursive(root, allFilesList)

        for (file in allFilesList) {
            if (!file.isDirectory) {
                val size = file.length()
                totalUsed += size
                val category = getCategoryForExtension(file.extension)
                stats[category] = stats.getOrDefault(category, 0L) + size
            }
        }

        val largeFiles = allFilesList
            .filter { !it.isDirectory }
            .sortedByDescending { it.length() }
            .take(10)
            .map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false,
                    extension = file.extension,
                    mimeType = getMimeType(file.extension)
                )
            }

        // Duplicate file finder (grouped by name and length)
        val duplicates = allFilesList
            .filter { !it.isDirectory }
            .groupBy { it.name + "_" + it.length() }
            .filter { it.value.size > 1 }
            .map { entry ->
                DuplicateGroup(
                    name = entry.value.first().name,
                    size = entry.value.first().length(),
                    instances = entry.value.map { file ->
                        FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            isDirectory = false,
                            extension = file.extension,
                            mimeType = getMimeType(file.extension)
                        )
                    }
                )
            }

        StorageAnalysis(
            totalSize = root.freeSpace + totalUsed, // Mock total size representing total usable + free free allocation
            usedSize = totalUsed,
            freeSize = root.freeSpace,
            categories = stats,
            largeFiles = largeFiles,
            duplicateGroups = duplicates
        )
    }

    private fun gatherFilesRecursive(file: File, results: MutableList<File>) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                gatherFilesRecursive(child, results)
            }
        } else {
            results.add(file)
        }
    }
}

data class StorageAnalysis(
    val totalSize: Long,
    val usedSize: Long,
    val freeSize: Long,
    val categories: Map<String, Long>,
    val largeFiles: List<FileItem>,
    val duplicateGroups: List<DuplicateGroup>
)

data class DuplicateGroup(
    val name: String,
    val size: Long,
    val instances: List<FileItem>
)
