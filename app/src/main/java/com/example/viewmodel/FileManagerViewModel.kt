package com.example.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.model.FileItem
import com.example.data.vault.VaultManager
import com.example.domain.CloudProviderRegistry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class FileManagerState(
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<FileItem> = emptySet(),
    val isMultiSelect: Boolean = false,
    val searchQuery: String = "",
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val recentFiles: List<RecentFileEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    
    // Clipboard Operations
    val clipboard: ClipboardAction? = null,
    
    // Storage Analysis
    val storageAnalysis: StorageAnalysis? = null,
    val isAnalyzing: Boolean = false,
    
    // Cloud / Network Connections
    val savedConnections: List<NetworkConnectionEntity> = emptyList(),
    val activeConnection: NetworkConnectionEntity? = null,
    val currentRemotePath: String = "/",
    val remoteFiles: List<FileItem> = emptyList(),
    val isRemoteConnecting: Boolean = false,
    
    // Vault
    val isVaultUnlocked: Boolean = false,
    val isVaultSetup: Boolean = false,
    val vaultFiles: List<FileItem> = emptyList(),
    val vaultError: String? = null
)

data class ClipboardAction(
    val files: List<FileItem>,
    val mode: ClipboardMode
)

enum class ClipboardMode { COPY, MOVE }

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val bookmarkDao = database.bookmarkDao()
    private val recentFileDao = database.recentFileDao()
    private val networkConnectionDao = database.networkConnectionDao()
    private val vaultKeyDao = database.vaultKeyDao()

    val fileRepository = FileRepository(application, bookmarkDao)
    val vaultManager = VaultManager(application, vaultKeyDao)
    private val cloudProviderRegistry = CloudProviderRegistry()

    private val _state = MutableStateFlow(FileManagerState(currentPath = fileRepository.rootPath))
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    init {
        // Observe Bookmark, Recent and Network Connection changes from the Database
        viewModelScope.launch {
            bookmarkDao.getAllBookmarks().collect { bms ->
                _state.update { it.copy(bookmarks = bms) }
                // Refresh directory to update bookmarked icon states
                refreshDirectory()
            }
        }

        viewModelScope.launch {
            recentFileDao.getRecentFiles().collect { recents ->
                _state.update { it.copy(recentFiles = recents) }
            }
        }

        viewModelScope.launch {
            networkConnectionDao.getAllConnections().collect { connections ->
                _state.update { it.copy(savedConnections = connections) }
            }
        }

        viewModelScope.launch {
            val isSetup = vaultManager.isVaultSetup()
            _state.update { it.copy(isVaultSetup = isSetup) }
        }

        loadFiles(fileRepository.rootPath)
        loadStorageAnalysis()
    }

    // --- Directory Navigation ---

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val list = fileRepository.getFiles(path)
                _state.update { 
                    it.copy(
                        currentPath = path,
                        files = list,
                        selectedFiles = emptySet(),
                        isMultiSelect = false,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to read path") }
            }
        }
    }

    fun refreshDirectory() {
        if (_state.value.currentPath.isNotEmpty()) {
            loadFiles(_state.value.currentPath)
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value.currentPath
        if (current == fileRepository.rootPath) return false

        val parent = File(current).parentFile
        if (parent != null && parent.absolutePath.startsWith(fileRepository.rootPath)) {
            loadFiles(parent.absolutePath)
            return true
        }
        return false
    }

    // --- File Manipulation ---

    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = fileRepository.createDirectory(_state.value.currentPath, name)
            if (success) {
                showToast("Folder created successfully")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("Failed to create folder: folder may already exist")
            }
        }
    }

    fun createTextFile(name: String, content: String) {
        viewModelScope.launch {
            val success = fileRepository.createFile(_state.value.currentPath, name, content)
            if (success) {
                showToast("File created successfully")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("Failed to create file: file may already exist")
            }
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val toDelete = _state.value.selectedFiles
            var successCount = 0
            for (file in toDelete) {
                val ok = fileRepository.deleteFile(file.path)
                if (ok) {
                    successCount++
                    bookmarkDao.deleteBookmarkByPath(file.path)
                    recentFileDao.deleteRecentByPath(file.path)
                }
            }
            showToast("Successfully deleted $successCount files")
            refreshDirectory()
            loadStorageAnalysis()
        }
    }

    fun renameSelected(newName: String) {
        viewModelScope.launch {
            val item = _state.value.selectedFiles.firstOrNull() ?: return@launch
            val success = fileRepository.renameFile(item.path, newName)
            if (success) {
                showToast("Renamed successfully")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("Failed to rename file")
            }
        }
    }

    // --- Multi Selection ---

    fun toggleSelection(item: FileItem) {
        _state.update { current ->
            val set = current.selectedFiles.toMutableSet()
            if (set.contains(item)) {
                set.remove(item)
            } else {
                set.add(item)
            }
            val hasSelection = set.isNotEmpty()
            current.copy(
                selectedFiles = set,
                isMultiSelect = hasSelection
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet(), isMultiSelect = false) }
    }

    // --- Bookmark / Recent Actions ---

    fun toggleBookmark(item: FileItem) {
        viewModelScope.launch {
            val alreadyBookmarked = bookmarkDao.isBookmarked(item.path)
            if (alreadyBookmarked) {
                bookmarkDao.deleteBookmarkByPath(item.path)
                showToast("Removed from bookmarks")
            } else {
                val entity = BookmarkEntity(
                    path = item.path,
                    name = item.name,
                    isDirectory = item.isDirectory
                )
                bookmarkDao.insertBookmark(entity)
                showToast("Added to bookmarks")
            }
        }
    }

    fun openFile(item: FileItem, onOpenText: (FileItem, String) -> Unit, onOpenImage: (FileItem) -> Unit) {
        viewModelScope.launch {
            // Save to database's RecentFile cache
            val recent = RecentFileEntity(
                path = item.path,
                name = item.name,
                extension = item.extension,
                size = item.size
            )
            recentFileDao.insertRecent(recent)

            if (item.isText) {
                try {
                    val file = File(item.path)
                    val content = file.readText()
                    onOpenText(item, content)
                } catch (e: Exception) {
                    showToast("Failed to read text file: ${e.message}")
                }
            } else if (item.isImage) {
                onOpenImage(item)
            } else {
                showToast("No default provider config. Native previews support TXT/MD/JSON and PNG/JPG.")
            }
        }
    }

    fun saveTextFileEdit(path: String, newContent: String) {
        viewModelScope.launch {
            try {
                File(path).writeText(newContent)
                showToast("File saved successfully")
                refreshDirectory()
                loadStorageAnalysis()
            } catch (e: java.lang.Exception) {
                showToast("Failed to save text changes: ${e.message}")
            }
        }
    }

    // --- Clipboard Buffer ---

    fun setClipboard(mode: ClipboardMode) {
        _state.update { current ->
            current.copy(
                clipboard = ClipboardAction(
                    files = current.selectedFiles.toList(),
                    mode = mode
                ),
                selectedFiles = emptySet(),
                isMultiSelect = false
            )
        }
        showToast("Copied ${state.value.clipboard?.files?.size} files to clipboard")
    }

    fun pasteClipboard() {
        viewModelScope.launch {
            val clip = _state.value.clipboard ?: return@launch
            val destDir = _state.value.currentPath
            _state.update { it.copy(isLoading = true) }
            
            var successCount = 0
            for (file in clip.files) {
                val ok = if (clip.mode == ClipboardMode.COPY) {
                    fileRepository.copyFile(file.path, destDir)
                } else {
                    fileRepository.moveFile(file.path, destDir)
                }
                
                if (ok) successCount++
            }
            
            _state.update { it.copy(clipboard = null, isLoading = false) }
            showToast("Successfully pasted $successCount items")
            refreshDirectory()
            loadStorageAnalysis()
        }
    }

    fun clearClipboard() {
        _state.update { it.copy(clipboard = null) }
    }

    // --- Archiving (ZIP) ---

    fun compressSelectedToZip(zipName: String) {
        viewModelScope.launch {
            val selected = _state.value.selectedFiles.map { it.path }
            if (selected.isEmpty()) return@launch
            
            val zipFileName = if (zipName.lowercase().endsWith(".zip")) zipName else "$zipName.zip"
            val zipDestPath = File(_state.value.currentPath, zipFileName).absolutePath
            
            _state.update { it.copy(isLoading = true) }
            val ok = fileRepository.compressToZip(selected, zipDestPath)
            _state.update { it.copy(isLoading = false) }

            if (ok) {
                showToast("Compressed successfully to $zipFileName")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("Failed to compress files into archive")
            }
        }
    }

    fun extractZip(fileItem: FileItem) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Extract into folder of same name as archive minus extension
            val parentFile = File(fileItem.path).parentFile ?: File(_state.value.currentPath)
            val destDirName = fileItem.name.removeSuffix(".zip") + "_extracted"
            val destDir = File(parentFile, destDirName)
            
            val ok = fileRepository.decompressZip(fileItem.path, destDir.absolutePath)
            _state.update { it.copy(isLoading = false) }

            if (ok) {
                showToast("Extracted safely to folder /$destDirName")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("Extraction failed")
            }
        }
    }

    // --- Search ---

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                refreshDirectory()
            } else {
                _state.update { it.copy(isLoading = true) }
                val results = fileRepository.searchFiles(query, _state.value.currentPath)
                _state.update { it.copy(files = results, isLoading = false) }
            }
        }
    }

    // --- Storage Analysis ---

    fun loadStorageAnalysis() {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val analysis = fileRepository.getStorageAnalysis(fileRepository.rootPath)
            _state.update { it.copy(storageAnalysis = analysis, isAnalyzing = false) }
        }
    }

    // --- Cloud & Network Prototypes ---

    fun addConnection(name: String, type: String, host: String, port: Int, username: String) {
        viewModelScope.launch {
            val connection = NetworkConnectionEntity(
                name = name,
                type = type,
                host = host,
                port = port,
                username = username
            )
            networkConnectionDao.insertConnection(connection)
            showToast("Connection saved successfully")
        }
    }

    fun removeConnection(connection: NetworkConnectionEntity) {
        viewModelScope.launch {
            if (_state.value.activeConnection?.id == connection.id) {
                _state.update { it.copy(activeConnection = null, remoteFiles = emptyList()) }
            }
            networkConnectionDao.deleteConnection(connection)
            showToast("Connection configuration removed")
        }
    }

    fun connectToNetwork(connection: NetworkConnectionEntity) {
        viewModelScope.launch {
            _state.update { it.copy(isRemoteConnecting = true) }
            // Simulate server connection handshake wait
            kotlinx.coroutines.delay(1200)
            networkConnectionDao.updateConnectionStatus(connection.id, true)
            _state.update { 
                it.copy(
                    activeConnection = connection.copy(isConnected = true),
                    isRemoteConnecting = false,
                    currentRemotePath = "/"
                ) 
            }
            generateVirtualFiles(connection, "/")
            showToast("Connected to external hosting node")
        }
    }

    fun disconnectNetwork() {
        viewModelScope.launch {
            val active = _state.value.activeConnection
            if (active != null) {
                networkConnectionDao.updateConnectionStatus(active.id, false)
            }
            _state.update { it.copy(activeConnection = null, remoteFiles = emptyList()) }
            showToast("Closed remote link")
        }
    }

    fun navigateRemoteFolder(folderName: String) {
        val active = _state.value.activeConnection ?: return
        val newPath = if (_state.value.currentRemotePath == "/") "/$folderName" else "${_state.value.currentRemotePath}/$folderName"
        _state.update { it.copy(currentRemotePath = newPath) }
        generateVirtualFiles(active, newPath)
    }

    fun navigateRemoteUp() {
        val active = _state.value.activeConnection ?: return
        val current = _state.value.currentRemotePath
        if (current == "/") return
        val parts = current.split("/")
        val newPath = if (parts.size <= 2) "/" else parts.dropLast(1).joinToString("/")
        _state.update { it.copy(currentRemotePath = newPath) }
        generateVirtualFiles(active, newPath)
    }

    private fun generateVirtualFiles(connection: NetworkConnectionEntity, path: String) {
        viewModelScope.launch {
            val provider = cloudProviderRegistry.getProvider(connection.type)
            val list = provider?.listFiles(connection, path) ?: emptyList()
            _state.update { it.copy(remoteFiles = list) }
        }
    }

    /**
     * Simulates downloading a remote cloud text file into the local standard LocalStorage downloads folder!
     */
    fun downloadRemoteFile(remoteFile: FileItem) {
        viewModelScope.launch {
            if (remoteFile.isDirectory) {
                showToast("Downloading folders is not supported in this version.")
                return@launch
            }
            
            _state.update { it.copy(isLoading = true) }
            kotlinx.coroutines.delay(1000)
            
            val downloadFolder = File(fileRepository.rootPath, "Downloads")
            if (!downloadFolder.exists()) downloadFolder.mkdirs()

            // Write downloaded mock contents
            val destName = "cloud_" + remoteFile.name
            val activeConn = state.value.activeConnection
            val content = if (activeConn != null) {
                val provider = cloudProviderRegistry.getProvider(activeConn.type)
                provider?.generateMockContent(remoteFile, activeConn.name) ?: ""
            } else {
                ""
            }
            
            val success = fileRepository.createFile(
                parentPath = downloadFolder.absolutePath,
                name = destName,
                content = content
            )
            
            _state.update { it.copy(isLoading = false) }
            if (success) {
                showToast("File downloaded to /Downloads/${destName}")
                refreshDirectory()
                loadStorageAnalysis()
            } else {
                showToast("File already downloaded.")
            }
        }
    }

    // --- Secure Vault ---

    fun checkVaultStatus() {
        viewModelScope.launch {
            val isSetup = vaultManager.isVaultSetup()
            _state.update { it.copy(isVaultSetup = isSetup) }
        }
    }

    fun setupVaultPin(pin: String) {
        viewModelScope.launch {
            val ok = vaultManager.setupVault(pin)
            if (ok) {
                _state.update { it.copy(isVaultSetup = true, isVaultUnlocked = true, vaultError = null) }
                showToast("Vault setup completed securely")
                refreshVaultList()
            } else {
                _state.update { it.copy(vaultError = "Failed to setup Vault") }
            }
        }
    }

    fun unlockVaultPin(pin: String) {
        viewModelScope.launch {
            val ok = vaultManager.authenticate(pin)
            if (ok) {
                _state.update { it.copy(isVaultUnlocked = true, vaultError = null) }
                showToast("Vault decrypted and unlocked")
                refreshVaultList()
            } else {
                _state.update { it.copy(vaultError = "Incorrect security PIN. Access denied.") }
            }
        }
    }

    fun lockVault() {
        _state.update { it.copy(isVaultUnlocked = false, vaultFiles = emptyList()) }
        showToast("Vault locked securely")
    }

    fun clearVaultConfig() {
        viewModelScope.launch {
            vaultManager.clearVault()
            _state.update { it.copy(isVaultUnlocked = false, isVaultSetup = false, vaultFiles = emptyList()) }
            showToast("Vault wiped completely")
        }
    }

    fun refreshVaultList() {
        viewModelScope.launch {
            val items = vaultManager.listVaultFiles()
            _state.update { it.copy(vaultFiles = items) }
        }
    }

    fun encryptLocalFileToVault(fileItem: FileItem, pin: String) {
        viewModelScope.launch {
            if (fileItem.isDirectory) {
                showToast("Vaulting folding structures is not supported. Use zip folders first.")
                return@launch
            }
            _state.update { it.copy(isLoading = true) }
            val ok = vaultManager.encryptFileToVault(fileItem.path, pin)
            _state.update { it.copy(isLoading = false) }

            if (ok) {
                showToast("File securely encrypted into safety vault")
                // Delete references
                bookmarkDao.deleteBookmarkByPath(fileItem.path)
                recentFileDao.deleteRecentByPath(fileItem.path)
                refreshDirectory()
                refreshVaultList()
                loadStorageAnalysis()
            } else {
                showToast("Failed to secure file")
            }
        }
    }

    fun decryptVaultFileToLocal(vaultItem: FileItem, pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Decrypt back to central LocalStorage downloads folder
            val destDir = File(fileRepository.rootPath, "Downloads")
            if (!destDir.exists()) destDir.mkdirs()

            val ok = vaultManager.decryptFileFromVault(
                vaultFileName = vaultItem.name + ".enc",
                destFolder = destDir.absolutePath,
                password = pin
            )
            _state.update { it.copy(isLoading = false) }

            if (ok) {
                showToast("File decrypted back to /Downloads/${vaultItem.name}")
                refreshDirectory()
                refreshVaultList()
                loadStorageAnalysis()
            } else {
                showToast("Decryption failed. Please check key/file integrity.")
            }
        }
    }

    fun deleteFileItemDirectly(path: String) {
        viewModelScope.launch {
            val ok = fileRepository.deleteFile(path)
            if (ok) {
                showToast("Cleaned up successfully")
                loadStorageAnalysis()
                refreshDirectory()
            } else {
                showToast("Failed to clean up target file")
            }
        }
    }

    // --- Helper for UX ---

    fun showToast(text: String) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
    }
}
