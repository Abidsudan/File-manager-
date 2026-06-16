package com.example.domain

import com.example.data.model.FileItem
import com.example.data.NetworkConnectionEntity
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface CloudProvider {
    val type: String
    suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem>
    suspend fun generateMockContent(remoteFile: FileItem, connectionName: String): String {
        return "# Cloud Asset: ${remoteFile.name}\n" +
                "Downloaded from $type Connection ($connectionName)\n" +
                "Path: ${remoteFile.path}\n" +
                "File Size: ${remoteFile.size} Bytes\n\n" +
                "Synchronized successfully via Standard Unified Cloud API."
    }
}

class FtpCloudProvider : CloudProvider {
    override val type: String = "FTP"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("public_html", "/public_html", 0, System.currentTimeMillis(), true, remoteType = "FTP"),
                FileItem("logs", "/logs", 0, System.currentTimeMillis() - 86400000, true, remoteType = "FTP"),
                FileItem("server_readme.txt", "/server_readme.txt", 1024, System.currentTimeMillis() - 4000000, false, "txt", remoteType = "FTP")
            )
        } else if (path.endsWith("public_html")) {
            listOf(
                FileItem("assets", "${path}/assets", 0, System.currentTimeMillis(), true, remoteType = "FTP"),
                FileItem("index.html", "${path}/index.html", 4022, System.currentTimeMillis(), false, "html", "text/html", remoteType = "FTP"),
                FileItem("styles.css", "${path}/styles.css", 249, System.currentTimeMillis(), false, "css", "text/css", remoteType = "FTP")
            )
        } else {
            listOf(
                FileItem("backup_dump.sql", "${path}/backup_dump.sql", 154092, System.currentTimeMillis(), false, "sql", remoteType = "FTP")
            )
        }
    }
}

class GoogleDriveCloudProvider : CloudProvider {
    override val type: String = "Google Drive"
    private val client = okhttp3.OkHttpClient()

    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        val accessToken = connection.username
        if (accessToken.isBlank() || accessToken == "0" || accessToken.startsWith("mock")) {
            return getFallbackFiles(path)
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Resolve virtual path to Google Drive Folder ID
                val folderId = resolvePathToFolderId(accessToken, path) ?: return@withContext emptyList()

                // 2. Fetch children of this Folder ID from Google Drive API
                val url = "https://www.googleapis.com/drive/v3/files" +
                        "?q='${folderId}'+in+parents+and+trashed=false" +
                        "&fields=files(id,name,mimeType,size,modifiedTime)" +
                        "&pageSize=100"

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Google Drive API returned code: ${response.code}")
                    }
                    val bodyString = response.body?.string() ?: return@withContext emptyList()
                    val jsonResponse = JSONObject(bodyString)
                    val filesArray = jsonResponse.optJSONArray("files") ?: return@withContext emptyList()

                    val items = mutableListOf<FileItem>()
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        val name = fileObj.getString("name")
                        val mimeType = fileObj.optString("mimeType", "")
                        val size = fileObj.optLong("size", 0L)
                        val modifiedTimeStr = fileObj.optString("modifiedTime", "")
                        val lastModified = parseRfc3339Timestamp(modifiedTimeStr)

                        val isDir = mimeType == "application/vnd.google-apps.folder"
                        val extension = if (isDir) "" else name.substringAfterLast('.', "")

                        items.add(
                            FileItem(
                                name = name,
                                path = path + (if (path == "/") "" else "/") + name,
                                size = size,
                                lastModified = lastModified,
                                isDirectory = isDir,
                                extension = extension,
                                mimeType = mimeType,
                                remoteType = "GDrive"
                            )
                        )
                    }
                    items
                }
            } catch (e: Exception) {
                e.printStackTrace()
                getFallbackFiles(path)
            }
        }
    }

    private fun parseRfc3339Timestamp(timestamp: String): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.parse(timestamp).toEpochMilli()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private suspend fun resolvePathToFolderId(accessToken: String, path: String): String? {
        val segments = path.split("/").filter { it.isNotEmpty() }
        var currentFolderId = "root"

        for (segment in segments) {
            currentFolderId = fetchFolderIdByName(accessToken, currentFolderId, segment) ?: return null
        }
        return currentFolderId
    }

    private fun fetchFolderIdByName(accessToken: String, parentId: String, folderName: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files" +
                "?q='${parentId}'+in+parents+and+name='${folderName}'+and+mimeType='application/vnd.google-apps.folder'+and+trashed=false" +
                "&fields=files(id)" +
                "&pageSize=1"

        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: return null
                if (files.length() > 0) {
                    files.getJSONObject(0).getString("id")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFallbackFiles(path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("My Documents", "/My Documents", 0, System.currentTimeMillis(), true, remoteType = "GDrive"),
                FileItem("Shared with Me", "/Shared with Me", 0, System.currentTimeMillis(), true, remoteType = "GDrive"),
                FileItem("Taxes 2025.pdf", "/Taxes 2025.pdf", 450198, System.currentTimeMillis() - 30000000, false, "pdf", "application/pdf", remoteType = "GDrive")
            )
        } else {
            listOf(
                FileItem("Project proposal.md", "${path}/Project proposal.md", 2120, System.currentTimeMillis(), false, "md", "text/markdown", remoteType = "GDrive"),
                FileItem("Presentation.ppt", "${path}/Presentation.ppt", 140922, System.currentTimeMillis(), false, "ppt", remoteType = "GDrive")
            )
        }
    }
}

class DropboxCloudProvider : CloudProvider {
    override val type: String = "Dropbox"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("Personal Photos", "/Personal Photos", 0, System.currentTimeMillis(), true, remoteType = "Dropbox"),
                FileItem("Work Sync", "/Work Sync", 0, System.currentTimeMillis(), true, remoteType = "Dropbox"),
                FileItem("Resume_2026.docx", "/Resume_2026.docx", 212500, System.currentTimeMillis() - 10000000, false, "docx", remoteType = "Dropbox")
            )
        } else if (path.endsWith("Personal Photos")) {
            listOf(
                FileItem("vacation_01.jpg", "${path}/vacation_01.jpg", 1250200, System.currentTimeMillis(), false, "jpg", "image/jpeg", remoteType = "Dropbox"),
                FileItem("family_portrait.png", "${path}/family_portrait.png", 3420000, System.currentTimeMillis(), false, "png", "image/png", remoteType = "Dropbox")
            )
        } else {
            listOf(
                FileItem("project_timeline.xlsx", "${path}/project_timeline.xlsx", 54300, System.currentTimeMillis(), false, "xlsx", remoteType = "Dropbox")
            )
        }
    }
}

class OneDriveCloudProvider : CloudProvider {
    override val type: String = "OneDrive"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("Notebooks", "/Notebooks", 0, System.currentTimeMillis(), true, remoteType = "OneDrive"),
                FileItem("Attachments", "/Attachments", 0, System.currentTimeMillis(), true, remoteType = "OneDrive"),
                FileItem("Budget_Forecast.xlsx", "/Budget_Forecast.xlsx", 89200, System.currentTimeMillis() - 5000000, false, "xlsx", remoteType = "OneDrive")
            )
        } else {
            listOf(
                FileItem("meeting_minutes.txt", "${path}/meeting_minutes.txt", 1540, System.currentTimeMillis(), false, "txt", remoteType = "OneDrive")
            )
        }
    }
}

class AmazonS3CloudProvider : CloudProvider {
    override val type: String = "Amazon S3"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("production-assets-bucket", "/production-assets-bucket", 0, System.currentTimeMillis(), true, remoteType = "S3"),
                FileItem("user-database-backups", "/user-database-backups", 0, System.currentTimeMillis(), true, remoteType = "S3"),
                FileItem("s3_hosting_policy.json", "/s3_hosting_policy.json", 1250, System.currentTimeMillis() - 20000000, false, "json", "application/json", remoteType = "S3")
            )
        } else if (path.endsWith("production-assets-bucket")) {
            listOf(
                FileItem("logo_vector.svg", "${path}/logo_vector.svg", 4500, System.currentTimeMillis(), false, "svg", "image/svg+xml", remoteType = "S3"),
                FileItem("header_banner.webp", "${path}/header_banner.webp", 120500, System.currentTimeMillis(), false, "webp", "image/webp", remoteType = "S3")
            )
        } else {
            listOf(
                FileItem("postgres_dump_2026_06_15.sql.gz", "${path}/postgres_dump_2026_06_15.sql.gz", 25400192, System.currentTimeMillis(), false, "gz", remoteType = "S3")
            )
        }
    }
}

class NextcloudCloudProvider : CloudProvider {
    override val type: String = "Nextcloud"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("Documents", "/Documents", 0, System.currentTimeMillis(), true, remoteType = "Nextcloud"),
                FileItem("Music", "/Music", 0, System.currentTimeMillis(), true, remoteType = "Nextcloud"),
                FileItem("Nextcloud_Manual.pdf", "/Nextcloud_Manual.pdf", 1205202, System.currentTimeMillis() - 95000000, false, "pdf", "application/pdf", remoteType = "Nextcloud")
            )
        } else if (path.endsWith("Music")) {
            listOf(
                FileItem("lofi_relax_ambient.mp3", "${path}/lofi_relax_ambient.mp3", 8940023, System.currentTimeMillis(), false, "mp3", "audio/mpeg", remoteType = "Nextcloud")
            )
        } else {
            listOf(
                FileItem("todo_notes.md", "${path}/todo_notes.md", 520, System.currentTimeMillis(), false, "md", "text/markdown", remoteType = "Nextcloud")
            )
        }
    }
}

class WebDavCloudProvider : CloudProvider {
    override val type: String = "WebDAV"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("SharedFolder", "/SharedFolder", 0, System.currentTimeMillis(), true, remoteType = "WebDAV"),
                FileItem("config_backup.ini", "/config_backup.ini", 512, System.currentTimeMillis() - 500000, false, "ini", remoteType = "WebDAV")
            )
        } else {
            listOf(
                FileItem("doc_sharing_info.txt", "${path}/doc_sharing_info.txt", 2304, System.currentTimeMillis(), false, "txt", remoteType = "WebDAV")
            )
        }
    }
}

class SmbCloudProvider : CloudProvider {
    override val type: String = "SMB"
    override suspend fun listFiles(connection: NetworkConnectionEntity, path: String): List<FileItem> {
        return if (path == "/") {
            listOf(
                FileItem("Backup", "/Backup", 0, System.currentTimeMillis(), true, remoteType = "SMB"),
                FileItem("MediaShare", "/MediaShare", 0, System.currentTimeMillis(), true, remoteType = "SMB")
            )
        } else {
            listOf(
                FileItem("episode_1.mp4", "${path}/episode_1.mp4", 543093222, System.currentTimeMillis(), false, "mp4", "video/mp4", remoteType = "SMB")
            )
        }
    }
}

@Singleton
class CloudProviderRegistry @Inject constructor() {
    private val providers = listOf(
        FtpCloudProvider(),
        GoogleDriveCloudProvider(),
        DropboxCloudProvider(),
        OneDriveCloudProvider(),
        AmazonS3CloudProvider(),
        NextcloudCloudProvider(),
        WebDavCloudProvider(),
        SmbCloudProvider()
    ).associateBy { it.type }

    fun getProvider(type: String): CloudProvider? {
        return providers[type]
    }

    fun getSupportedTypes(): List<String> {
        return providers.keys.toList()
    }
}
