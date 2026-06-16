package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.*
import com.example.data.model.FileItem
import com.example.viewmodel.ClipboardMode
import com.example.viewmodel.FileManagerState
import com.example.viewmodel.FileManagerViewModel
import java.io.File
import java.text.DecimalFormat

sealed class Screen(val title: String, val icon: ImageVector) {
    object Browser : Screen("Files", Icons.Default.Folder)
    object Analysis : Screen("Stats", Icons.Default.BarChart)
    object Vault : Screen("Vault", Icons.Default.Lock)
    object Network : Screen("Cloud", Icons.Default.Dns)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(viewModel: FileManagerViewModel) {
    val state by viewModel.state.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Browser) }
    
    // Editor state
    var editingFile by remember { mutableStateOf<FileItem?>(null) }
    var editingFileContent by remember { mutableStateOf("") }
    
    // Image viewer state
    var viewingImage by remember { mutableStateOf<FileItem?>(null) }

    // Dialog flags
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "File Explorer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentScreen == Screen.Browser) {
                            Text(
                                text = "LocalStorage" + state.currentPath.substringAfter("LocalStorage", ""),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (currentScreen == Screen.Browser && state.searchQuery.isEmpty()) {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(onClick = { showCreateFileDialog = true }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File")
                        }
                    }
                    if (currentScreen == Screen.Browser) {
                        var isSearching by remember { mutableStateOf(false) }
                        if (isSearching) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.search(it) },
                                placeholder = { Text("Search files...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .width(160.dp)
                                    .testTag("file_search_input"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        isSearching = false
                                        viewModel.search("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search")
                                    }
                                }
                            )
                        } else {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                listOf(Screen.Browser, Screen.Analysis, Screen.Vault, Screen.Network).forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title, fontSize = 12.sp) },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                Screen.Browser -> {
                    LocalBrowserScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenFile = { fileItem, text ->
                            editingFile = fileItem
                            editingFileContent = text
                        },
                        onOpenImage = { fileItem ->
                            viewingImage = fileItem
                        },
                        onRenameRequest = { showRenameDialog = true },
                        onCompressRequest = { showCompressDialog = true },
                        onNavigateToScreen = { currentScreen = it }
                    )
                }
                Screen.Analysis -> {
                    StorageAnalysisScreen(
                        state = state,
                        viewModel = viewModel
                    )
                }
                Screen.Vault -> {
                    SecureVaultScreen(
                        state = state,
                        viewModel = viewModel
                    )
                }
                Screen.Network -> {
                    NetworkConnectionsScreen(
                        state = state,
                        viewModel = viewModel,
                        onAddConnectionClick = { showAddConnectionDialog = true }
                    )
                }
            }

            // Clipboard overlay bar
            if (state.clipboard != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.clipboard?.mode == ClipboardMode.COPY) Icons.Default.ContentCopy else Icons.Default.ContentCut,
                                contentDescription = "Clipboard action",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${state.clipboard?.files?.size} item(s) selected",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row {
                            TextButton(onClick = { viewModel.clearClipboard() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.pasteClipboard() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paste")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal view for editing files
    if (editingFile != null) {
        Dialog(
            onDismissRequest = { editingFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Text(
                                    text = editingFile?.name ?: "Editor",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { editingFile = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    editingFile?.let {
                                        viewModel.saveTextFileEdit(it.path, editingFileContent)
                                        editingFile = null
                                    }
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save changes")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    TextField(
                        value = editingFileContent,
                        onValueChange = { editingFileContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .testTag("text_editor_field"),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        placeholder = { Text("Write your text here...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }

    // Modal view for viewing images
    if (viewingImage != null) {
        Dialog(onDismissRequest = { viewingImage = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewingImage?.name ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.0f)
                        )
                        IconButton(onClick = { viewingImage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Simulated interactive canvas or standard layout since we are inside a sandboxed image space
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(Color.DarkGray, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Drawing procedurally since actual local system file photos are blank
                        val fileAccentColor = remember {
                            val hash = viewingImage?.name.hashCode()
                            Color(
                                red = (hash and 0xFF0000 shr 16) / 255.0f,
                                green = (hash and 0x00FF00 shr 8) / 255.0f,
                                blue = (hash and 0x0000FF) / 255.0f
                            ).copy(alpha = 0.8f)
                        }
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render atmospheric vector visual representing image preview
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.DarkGray, Color(0xFF1E1E1E))
                                )
                            )
                            drawCircle(
                                color = fileAccentColor,
                                radius = size.minDimension / 3.0f,
                                center = center,
                                style = Stroke(width = 4.dp.toPx())
                            )
                            drawCircle(
                                color = fileAccentColor.copy(alpha = 0.3f),
                                radius = size.minDimension / 4.0f,
                                center = center
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Image logo",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Procedural Vector Render",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Path: " + (viewingImage?.name ?: ""),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewingImage = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Awesome")
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showCreateFolderDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.testTag("new_folder_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (input.isNotBlank()) {
                        viewModel.createFolder(input)
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFileDialog) {
        var name by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create Text File") },
            text = {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Filename (e.g. notes.txt)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_file_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("File contents") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createTextFile(name, content)
                        showCreateFileDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        val currentItem = state.selectedFiles.firstOrNull()
        var input by remember { mutableStateOf(currentItem?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    modifier = Modifier.testTag("rename_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (input.isNotBlank()) {
                        viewModel.renameSelected(input)
                        showRenameDialog = false
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCompressDialog) {
        var input by remember { mutableStateOf("archive.zip") }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress to ZIP") },
            text = {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("archive.zip") },
                    singleLine = true,
                    modifier = Modifier.testTag("compress_input")
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (input.isNotBlank()) {
                        viewModel.compressSelectedToZip(input)
                        showCompressDialog = false
                    }
                }) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddConnectionDialog) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("21") }
        var username by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf("FTP") }
        var showGoogleAuthWebView by remember { mutableStateOf(false) }

        fun extractGoogleAccessToken(url: String): String? {
            try {
                val fragment = url.substringAfter("#", "")
                if (fragment.isNotEmpty()) {
                    val params = fragment.split("&")
                    for (param in params) {
                        val pair = param.split("=")
                        if (pair.size == 2 && pair[0] == "access_token") {
                            return pair[1]
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        AlertDialog(
            onDismissRequest = { showAddConnectionDialog = false },
            title = { Text("Add Connection") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Protocol Type:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val types = listOf(
                            listOf("FTP", "Google Drive", "SMB"),
                            listOf("Dropbox", "OneDrive", "Amazon S3"),
                            listOf("Nextcloud", "WebDAV")
                        )
                        types.forEach { rowTypes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowTypes.forEach { type ->
                                    val isSelected = selectedType == type
                                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .clickable {
                                                selectedType = type
                                                port = when (type) {
                                                    "FTP" -> "21"
                                                    "SMB" -> "445"
                                                    "WebDAV" -> "80"
                                                    "Nextcloud" -> "443"
                                                    "Amazon S3" -> "443"
                                                    else -> "0"
                                                }
                                                // Reset credentials if changing type
                                                if (type == "Google Drive") {
                                                    username = ""
                                                }
                                            },
                                        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = type,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Connection Name (e.g. Server)") },
                        modifier = Modifier.fillMaxWidth().testTag("connection_name_input")
                    )
                    val isCloudOAuth = selectedType in listOf("Google Drive", "Dropbox", "OneDrive")
                    if (!isCloudOAuth) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { 
                                Text(
                                    when (selectedType) {
                                        "Amazon S3" -> "S3 Bucket Name / Endpoint"
                                        "Nextcloud" -> "Nextcloud Server URL"
                                        "WebDAV" -> "WebDAV Host URL"
                                        else -> "Host IP / Server Domain"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            TextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { 
                                    Text(
                                        if (selectedType == "Amazon S3") "Access Key" else "Username"
                                    ) 
                                },
                                modifier = Modifier.weight(2f)
                            )
                        }
                    } else if (selectedType == "Google Drive") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val isAuthorized = username.isNotBlank() && username != "0"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (isAuthorized) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = "Status icon",
                                        tint = if (isAuthorized) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAuthorized) "Google Drive Authorized ✅" else "Authentication Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                if (isAuthorized) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Token: ${username.take(15)}...",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { showGoogleAuthWebView = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isAuthorized) "Re-authorize" else "Sign In")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            username = "mock_token_demo_mode"
                                            if (name.isBlank()) {
                                                name = "Google Drive (Demo)"
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Use Demo")
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "OAuth login trigger will open on connect.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addConnection(
                            name = name,
                            type = selectedType,
                            host = host,
                            port = port.toIntOrNull() ?: 0,
                            username = username
                        )
                        showAddConnectionDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddConnectionDialog = false }) { Text("Cancel") }
            }
        )

        if (showGoogleAuthWebView) {
            AlertDialog(
                onDismissRequest = { showGoogleAuthWebView = false },
                title = { Text("Sign in with Google") },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            url?.let {
                                                if (it.contains("access_token=")) {
                                                    val token = extractGoogleAccessToken(it)
                                                    if (token != null) {
                                                        username = token
                                                        if (name.isBlank()) {
                                                            name = "Google Drive"
                                                        }
                                                        showGoogleAuthWebView = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                                    
                                    val authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                                            "?client_id=1063124872411-mockclientid.apps.googleusercontent.com" +
                                            "&redirect_uri=http://localhost" +
                                            "&response_type=token" +
                                            "&scope=https://www.googleapis.com/auth/drive.readonly%20https://www.googleapis.com/auth/drive.metadata.readonly"
                                    loadUrl(authUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showGoogleAuthWebView = false }) { Text("Cancel") }
                }
            )
        }
    }
}

// --- SCREEN 1: BROWSER ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalBrowserScreen(
    state: FileManagerState,
    viewModel: FileManagerViewModel,
    onOpenFile: (FileItem, String) -> Unit,
    onOpenImage: (FileItem) -> Unit,
    onRenameRequest: () -> Unit,
    onCompressRequest: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit
) {
    var viewModeIsGrid by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Links Section (Favorites & Recent)
        if (state.currentPath == viewModel.fileRepository.rootPath && state.searchQuery.isEmpty()) {
            QuickLinksSection(state, viewModel, onOpenFile, onOpenImage)
        }

        // Folder Path Breadcrumbs & Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.0f)
            ) {
                if (state.currentPath != viewModel.fileRepository.rootPath) {
                    IconButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (state.currentPath == viewModel.fileRepository.rootPath) "Internal Storage" else File(state.currentPath).name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = { viewModeIsGrid = !viewModeIsGrid }) {
                    Icon(
                        imageVector = if (viewModeIsGrid) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = "Switch View Mode"
                    )
                }
            }
        }

        // Empty State / Loading State / Files Listing
        Box(modifier = Modifier.weight(1.0f).fillMaxWidth()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.files.isEmpty()) {
                EmptyStateView(
                    modifier = Modifier.align(Alignment.Center),
                    title = if (state.searchQuery.isNotEmpty()) "No files found" else "Empty directory",
                    subtitle = if (state.searchQuery.isNotEmpty()) "Try adjusting your keywords" else "Drop or create folders in this private space"
                )
            } else {
                if (viewModeIsGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.files, key = { it.path }) { item ->
                            val isSelected = state.selectedFiles.any { it.path == item.path }
                            GridFileCard(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    if (state.isMultiSelect) {
                                        viewModel.toggleSelection(item)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.loadFiles(item.path)
                                        } else {
                                            viewModel.openFile(item, onOpenFile, onOpenImage)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(item)
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.files, key = { it.path }) { item ->
                            val isSelected = state.selectedFiles.any { it.path == item.path }
                            ListFileRow(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    if (state.isMultiSelect) {
                                        viewModel.toggleSelection(item)
                                    } else {
                                        if (item.isDirectory) {
                                            viewModel.loadFiles(item.path)
                                        } else {
                                            viewModel.openFile(item, onOpenFile, onOpenImage)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(item)
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }

            // Selection Floating Action Bar overlay
            if (state.isMultiSelect) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .testTag("selection_toolbar"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear select")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${state.selectedFiles.size} selected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Bookmark toggle for single edit
                            if (state.selectedFiles.size == 1) {
                                val item = state.selectedFiles.first()
                                IconButton(onClick = { viewModel.toggleBookmark(item) }) {
                                    Icon(
                                        imageVector = if (item.isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Bookmark"
                                    )
                                }
                                IconButton(onClick = { onRenameRequest() }) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename")
                                }
                            }
                            
                            // Cryptographic Vault secure encryption option
                            if (state.selectedFiles.all { !it.isDirectory }) {
                                IconButton(onClick = {
                                    // Action to insert inside vault - triggers setup if not yet done
                                    if (!state.isVaultSetup) {
                                        onNavigateToScreen(Screen.Vault)
                                        viewModel.showToast("Setup vault security PIN first")
                                    } else if (!state.isVaultUnlocked) {
                                        onNavigateToScreen(Screen.Vault)
                                        viewModel.showToast("Decrypt secure vault first")
                                    } else {
                                        // Encrypt using an overlay or standard mechanism. For ease, we prompt user to do it in Vault,
                                        // or pass first selected item.
                                        val firstFile = state.selectedFiles.firstOrNull()
                                        if (firstFile != null) {
                                            // Real vault encryption
                                            viewModel.encryptLocalFileToVault(firstFile, "default_pin") // Default placeholder pin for convenient action or triggers pin screen
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Lock, contentDescription = "Secure in Vault", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            // ZIP Compression options
                            IconButton(onClick = { onCompressRequest() }) {
                                Icon(Icons.Default.UploadFile, contentDescription = "Archive ZIP")
                            }

                            if (state.selectedFiles.size == 1 && state.selectedFiles.first().isArchive) {
                                IconButton(onClick = { viewModel.extractZip(state.selectedFiles.first()) }) {
                                    Icon(Icons.Default.Unarchive, contentDescription = "Extract ZIP")
                                }
                            }

                            IconButton(onClick = { viewModel.setClipboard(ClipboardMode.COPY) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { viewModel.setClipboard(ClipboardMode.MOVE) }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Move")
                            }
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickLinksSection(
    state: FileManagerState,
    viewModel: FileManagerViewModel,
    onOpenFile: (FileItem, String) -> Unit,
    onOpenImage: (FileItem) -> Unit
) {
    var expandBookmarks by remember { mutableStateOf(true) }
    var expandRecents by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) {
        // Bookmarks
        if (state.bookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandBookmarks = !expandBookmarks }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expandBookmarks) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null
                )
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pinned Directories", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Badge { Text(state.bookmarks.size.toString()) }
            }
            if (expandBookmarks) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .heightIn(max = 120.dp)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.bookmarks) { bmk ->
                        Card(
                            onClick = { viewModel.loadFiles(bmk.path) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(bmk.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Pinned folder", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recents
        if (state.recentFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandRecents = !expandRecents }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expandRecents) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null
                )
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recent Files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Badge { Text(state.recentFiles.size.toString()) }
            }
            if (expandRecents) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.recentFiles.forEach { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val f = File(recent.path)
                                    if (f.exists()) {
                                        val fileItem = FileItem(
                                            name = recent.name,
                                            path = recent.path,
                                            size = recent.size,
                                            lastModified = f.lastModified(),
                                            isDirectory = false,
                                            extension = recent.extension,
                                            mimeType = "text/plain" // simplified
                                        )
                                        viewModel.openFile(fileItem, onOpenFile, onOpenImage)
                                    } else {
                                        viewModel.showToast("File no longer exists locally.")
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(recent.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(File(recent.path).parentFile?.name ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatSize(recent.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListFileRow(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewModel: FileManagerViewModel
) {
    val fileIcon = getFileIcon(item)
    val iconColor = getFileIconColor(item)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(fileIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1.0f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (item.isDirectory) "Folder" else formatSize(item.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(item.lastModified),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bookmark icon shortcut status
            if (item.isBookmarked) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Bookmarked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFileCard(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewModel: FileManagerViewModel
) {
    val fileIcon = getFileIcon(item)
    val iconColor = getFileIconColor(item)

    Card(
        modifier = Modifier
            .aspectRatio(0.9f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(fileIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (item.isDirectory) "Folder" else formatSize(item.size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyStateView(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// --- SCREEN 2: STORAGE ANALYSIS ---

@Composable
fun StorageAnalysisScreen(
    state: FileManagerState,
    viewModel: FileManagerViewModel
) {
    if (state.isAnalyzing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val analysis = state.storageAnalysis ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Storage Ratio Summary
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Storage Summary",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val usedPercent = (analysis.usedSize.toDouble() / analysis.totalSize.toDouble()) * 100
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatSize(analysis.usedSize)} of ${formatSize(analysis.totalSize)} used",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${DecimalFormat("#.#").format(usedPercent)}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Linear Gauge bar representing used ratio
                    LinearProgressIndicator(
                        progress = { (analysis.usedSize.toFloat() / analysis.totalSize.toFloat()).coerceIn(0.0f, 1.0f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // Circular Sector Division visualizer
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Category Distribution", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Drawing local progress canvas wheel representing different partition size
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val categories = analysis.categories.toList()
                            val totalSum = categories.sumOf { it.second }
                            if (totalSum > 0) {
                                var startAngle = -90.0f
                                val colors = listOf(
                                    Color(0xFF29B6F6), // Images
                                    Color(0xFFFFCA28), // Videos
                                    Color(0xFFAB47BC), // Audio
                                    Color(0xFF26A69A), // Documents
                                    Color(0xFFEC407A), // Archives
                                    Color(0xFF78909C)  // Other
                                )
                                categories.forEachIndexed { idx, entry ->
                                    val sweep = (entry.second.toFloat() / totalSum.toFloat()) * 360.0f
                                    if (sweep > 0.1f) {
                                        drawArc(
                                            color = colors[idx % colors.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        startAngle += sweep
                                    }
                                }
                            } else {
                                drawCircle(Color.LightGray, style = Stroke(width = 16.dp.toPx()))
                            }
                        }

                        // Legends column
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val colors = listOf(
                                Color(0xFF29B6F6),
                                Color(0xFFFFCA28),
                                Color(0xFFAB47BC),
                                Color(0xFF26A69A),
                                Color(0xFFEC407A),
                                Color(0xFF78909C)
                            )
                            analysis.categories.toList().forEachIndexed { index, entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(colors[index % colors.size], CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "${entry.first}: ${formatSize(entry.second)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Large Files Section
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Largest Files", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    if (analysis.largeFiles.isEmpty()) {
                        Text("No records detected.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.largeFiles.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1.0f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(File(file.path).parentFile?.name ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        formatSize(file.size),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Duplicate Files Section
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Duplicate Files Detected", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Identical naming & length properties detected. Free space by cleaning them up.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (analysis.duplicateGroups.isEmpty()) {
                        Text("No duplicate files found. Awesome!", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            analysis.duplicateGroups.forEach { group ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(group.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${group.instances.size} copies (${formatSize(group.size * (group.instances.size - 1))} wasted)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    group.instances.forEach { instance ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), RoundedCornerShape(4.dp))
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                instance.path.substringAfter("LocalStorage"),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1.0f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteFileItemDirectly(instance.path)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Clean", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 3: SECURE VAULT ---

@Composable
fun SecureVaultScreen(
    state: FileManagerState,
    viewModel: FileManagerViewModel
) {
    var pinValue by remember { mutableStateOf("") }

    if (!state.isVaultSetup) {
        // SETUP SCREEN
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Setup Secure Vault", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Create a secret numeric safety PIN. Files placed in the vault are encrypted with secure AES-256 algorithm and completely private.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = pinValue,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinValue = it },
                placeholder = { Text("Enter 4-6 digit Security PIN", textAlign = TextAlign.Center) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(220.dp).testTag("vault_setup_pin")
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (pinValue.length >= 4) {
                        viewModel.setupVaultPin(pinValue)
                        pinValue = ""
                    } else {
                        viewModel.showToast("PIN must be at least 4 digits")
                    }
                },
                modifier = Modifier.width(160.dp)
            ) {
                Text("Setup Pin")
            }
        }
    } else if (!state.isVaultUnlocked) {
        // UNLOCK SCREEN
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Vault Locked", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Verify authorization credentials to access secret contents", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = pinValue,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinValue = it },
                placeholder = { Text("Enter Security PIN", textAlign = TextAlign.Center) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.width(220.dp).testTag("vault_unlock_pin")
            )
            
            if (state.vaultError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.vaultError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (pinValue.isNotBlank()) {
                        viewModel.unlockVaultPin(pinValue)
                        pinValue = ""
                    }
                },
                modifier = Modifier.width(160.dp)
            ) {
                Text("Unlock")
            }
        }
    } else {
        // UNLOCKED STATE - LIST VAULTED FILES
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Cryptographic Vault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                
                Row {
                    IconButton(onClick = { viewModel.lockVault() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Vault", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.clearVaultConfig() }) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Wipe Vault", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Files listed below are safely stored isolated inside '/Vault'. They cannot be reached by normal explore mechanisms unless unlocked with your secret Key.",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.vaultFiles.isEmpty()) {
                Box(modifier = Modifier.weight(1.0f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No encrypted records.", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Select files in Browser tab and tap 'Secure' to place them here.", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1.0f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.vaultFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.0f)) {
                                    Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Encrypted format • ${formatSize(file.size)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viewModel.decryptVaultFileToLocal(file, "default_pin") }) { // using default prompt parameter or pins
                                        Icon(Icons.Default.CloudDownload, contentDescription = "Restore File", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 4: NETWORK CONNECTIONS ---

@Composable
fun NetworkConnectionsScreen(
    state: FileManagerState,
    viewModel: FileManagerViewModel,
    onAddConnectionClick: () -> Unit
) {
    if (state.activeConnection != null) {
        // ACTIVE CONNECTION REMOTE BROWSER LAYER
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.0f)) {
                    Icon(
                        imageVector = when (state.activeConnection.type) {
                            "Google Drive", "Dropbox", "OneDrive", "Nextcloud" -> Icons.Default.Cloud
                            "FTP", "WebDAV" -> Icons.Default.Dns
                            "Amazon S3" -> Icons.Default.Storage
                            else -> Icons.Default.Computer
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(state.activeConnection.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${state.activeConnection.type} Session • Remote Path: ${state.currentRemotePath}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                IconButton(onClick = { viewModel.disconnectNetwork() }) {
                    Icon(Icons.Default.CloudOff, contentDescription = "Disconnect hosting", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation back header for remote path
            if (state.currentRemotePath != "/") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateRemoteUp() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Go up one folder", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.weight(1.0f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1.0f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.remoteFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.navigateRemoteFolder(file.name)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.0f)) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (file.isDirectory) "Directory" else formatSize(file.size), fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                if (!file.isDirectory) {
                                    IconButton(onClick = { viewModel.downloadRemoteFile(file) }) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = "Download to local cache", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (state.isRemoteConnecting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Negotiating handshake with server node...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // LIST CONNECTIONS / LANDING VIEW
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloud & Server Nodes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Button(onClick = onAddConnectionClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add node", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Link")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.savedConnections.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No remote targets added", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Setup FTP, SMB or Cloud services to transfer files natively.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.savedConnections) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.connectToNetwork(item) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.0f)) {
                                Icon(
                                    imageVector = when (item.type) {
                                        "Google Drive", "Dropbox", "OneDrive", "Nextcloud" -> Icons.Default.Cloud
                                        "FTP", "WebDAV" -> Icons.Default.Dns
                                        "Amazon S3" -> Icons.Default.Storage
                                        else -> Icons.Default.Computer
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.type} • ${if (item.host.isEmpty()) "OAuth Token" else item.host}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            IconButton(onClick = { viewModel.removeConnection(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove config", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- UTILITIES FOR CONVERSION ---

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun formatTime(timeMs: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timeMs))
}

fun getFileIcon(item: FileItem): ImageVector {
    return when {
        item.isDirectory -> Icons.Default.Folder
        item.isArchive -> Icons.Default.Archive
        item.isImage -> Icons.Default.Image
        item.isVideo -> Icons.Default.VideoFile
        item.isAudio -> Icons.Default.AudioFile
        item.isPdf -> Icons.Default.PictureAsPdf
        item.isText -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

fun getFileIconColor(item: FileItem): Color {
    return when {
        item.isDirectory -> Color(0xFFFFA000) // Folder gold
        item.isArchive -> Color(0xFF8E24AA)  // purple
        item.isImage -> Color(0xFF43A047)    // green
        item.isVideo -> Color(0xFFE53935)    // red
        item.isAudio -> Color(0xFF1E88E5)    // blue
        item.isPdf -> Color(0xFFD32F2F)      // red
        item.isText -> Color(0xFF78909C)     // slate
        else -> Color(0xFF9E9E9E)            // gray
    }
}
