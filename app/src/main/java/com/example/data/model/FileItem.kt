package com.example.data.model

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String = "",
    val mimeType: String = "application/octet-stream",
    val isBookmarked: Boolean = false,
    val isRemote: Boolean = false,
    val remoteType: String? = null // "FTP", "SMB", "GDrive", "Vault"
) {
    val isArchive: Boolean
        get() = extension.lowercase() in setOf("zip", "rar", "7z", "tar", "gz")

    val isImage: Boolean
        get() = extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

    val isVideo: Boolean
        get() = extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp")

    val isAudio: Boolean
        get() = extension.lowercase() in setOf("mp3", "wav", "aac", "ogg", "flac", "m4a")

    val isPdf: Boolean
        get() = extension.lowercase() == "pdf"

    val isText: Boolean
        get() = extension.lowercase() in setOf("txt", "json", "xml", "html", "css", "js", "ts", "kt", "kt", "kts", "properties", "md", "csv")
}
