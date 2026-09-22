package com.example.ui.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.AiMessageEntity

class WebAppInterface(private val onConsoleLog: (String, String) -> Unit) {
    @JavascriptInterface
    fun postConsoleLog(type: String, message: String) {
        onConsoleLog(type, message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: WorkspaceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiMessages by viewModel.getAiMessages().collectAsState(initial = emptyList())

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileNameInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.initProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.name ?: "Studio Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isSaved) "Saved to Cloud & Storage" else "Saving...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("workspace_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Refresh / Execute in webview
                    IconButton(
                        onClick = {
                            viewModel.triggerRefresh()
                            viewModel.setTabIndex(1) // Open preview
                        },
                        modifier = Modifier.testTag("workspace_run_btn")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Run App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete current active file
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = uiState.files.size > 1,
                        modifier = Modifier.testTag("workspace_delete_file_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete File")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.activeTabIndex == 0,
                    onClick = { viewModel.setTabIndex(0) },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
                    label = { Text("Code") },
                    modifier = Modifier.testTag("tab_editor")
                )
                NavigationBarItem(
                    selected = uiState.activeTabIndex == 1,
                    onClick = { viewModel.setTabIndex(1) },
                    icon = { Icon(Icons.Default.Language, contentDescription = "Preview") },
                    label = { Text("Web Preview") },
                    modifier = Modifier.testTag("tab_preview")
                )
                NavigationBarItem(
                    selected = uiState.activeTabIndex == 2,
                    onClick = { viewModel.setTabIndex(2) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    label = { Text("AI Studio") },
                    modifier = Modifier.testTag("tab_ai")
                )
                NavigationBarItem(
                    selected = uiState.activeTabIndex == 3,
                    onClick = { viewModel.setTabIndex(3) },
                    icon = {
                        BadgedBox(badge = {
                            val errors = uiState.consoleLogs.count { it.type == "error" }
                            if (errors > 0) {
                                Badge { Text("$errors") }
                            }
                        }) {
                            Icon(Icons.Default.Terminal, contentDescription = "Console")
                        }
                    },
                    label = { Text("Console") },
                    modifier = Modifier.testTag("tab_console")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // File tabs strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.files.forEach { file ->
                    val isSelected = file.id == uiState.activeFileId
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectFile(file.id) },
                        label = { Text(file.fileName, fontSize = 13.sp) },
                        leadingIcon = {
                            val icon = when (file.fileExtension) {
                                "html" -> Icons.Default.Html
                                "css" -> Icons.Default.Style
                                "js" -> Icons.Default.Javascript
                                else -> Icons.Default.Description
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("file_chip_${file.fileName}")
                    )
                }

                // Add File Button
                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier.size(36.dp).testTag("add_file_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add File", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Main Active Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTabIndex) {
                    0 -> CodeEditorTab(
                        content = uiState.activeFileContent,
                        fileName = uiState.activeFileName,
                        onContentChange = { viewModel.updateActiveFileContent(it) }
                    )
                    1 -> LiveWebPreviewTab(
                        html = uiState.bundledHtmlForPreview,
                        refreshKey = uiState.refreshCounter,
                        onConsoleLog = { type, msg -> viewModel.addConsoleLog(type, msg) }
                    )
                    2 -> AiStudioChatTab(
                        messages = aiMessages,
                        isGenerating = uiState.isAiGenerating,
                        promptInput = uiState.aiPromptInput,
                        errorMessage = uiState.aiErrorMessage,
                        onPromptChange = { viewModel.onAiPromptChange(it) },
                        onSendPrompt = { viewModel.sendAiPrompt() },
                        onApplyCode = { viewModel.applyAiCodeToActiveFile(it) }
                    )
                    3 -> ConsoleTab(
                        logs = uiState.consoleLogs,
                        onClear = { viewModel.clearConsoleLogs() },
                        onFixWithAi = { viewModel.fixErrorsWithAi() }
                    )
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Add File to Project") },
            text = {
                OutlinedTextField(
                    value = newFileNameInput,
                    onValueChange = { newFileNameInput = it },
                    label = { Text("File Name") },
                    placeholder = { Text("components.js, app.css") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_filename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileNameInput.trim()
                        if (name.isNotBlank()) {
                            viewModel.addNewFile(name)
                            newFileNameInput = ""
                            showNewFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_file_btn")
                ) {
                    Text("Add File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete File Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete ${uiState.activeFileName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentFile()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CodeEditorTab(
    content: String,
    fileName: String,
    onContentChange: (String) -> Unit
) {
    SyntaxHighlightedCodeEditor(
        content = content,
        fileName = fileName,
        onContentChange = onContentChange
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveWebPreviewTab(
    html: String,
    refreshKey: Int,
    onConsoleLog: (String, String) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("webview_runner"),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE

                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()

                    val jsInterface = WebAppInterface(onConsoleLog)
                    addJavascriptInterface(jsInterface, "AndroidBridge")

                    loadDataWithBaseURL("https://local.vlive.studio/", html, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("https://local.vlive.studio/", html, "text/html", "UTF-8", null)
            }
        )
    }
}

@Composable
fun AiStudioChatTab(
    messages: List<AiMessageEntity>,
    isGenerating: Boolean,
    promptInput: String,
    errorMessage: String?,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onApplyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Quick Action Suggestions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { onPromptChange("Build a responsive calculator with glassmorphism UI") },
                label = { Text("Calculator") }
            )
            SuggestionChip(
                onClick = { onPromptChange("Create an interactive 3D physics particle canvas") },
                label = { Text("Physics Sim") }
            )
            SuggestionChip(
                onClick = { onPromptChange("Add a dark/light mode toggle with smooth transitions") },
                label = { Text("Dark Theme") }
            )
            SuggestionChip(
                onClick = { onPromptChange("Refactor code for maximum performance and clean architecture") },
                label = { Text("Refactor") }
            )
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "V.Live Unlimited AI Architect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Prompt the AI to write, modify, debug, or architect complex code for this project. Code can be applied instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            items(messages.reversed(), key = { it.id }) { msg ->
                AiMessageBubble(message = msg, onApplyCode = onApplyCode)
            }
        }

        // Error message if any
        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Prompt input field
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = onPromptChange,
                    placeholder = { Text("Ask AI to generate or modify code...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendPrompt() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSendPrompt,
                    enabled = promptInput.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (promptInput.isNotBlank() && !isGenerating)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("ai_send_prompt_btn")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (promptInput.isNotBlank())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiMessageBubble(
    message: AiMessageEntity,
    onApplyCode: (String) -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "V.Live AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                // If message contains code blocks, show Apply / Copy actions
                if (!isUser && message.content.contains("```")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onApplyCode(message.content) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply to Editor", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleTab(
    logs: List<ConsoleLogItem>,
    onClear: () -> Unit,
    onFixWithAi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(12.dp)
    ) {
        // Console Header toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live Execution Terminal (${logs.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hasErrors = logs.any { it.type == "error" }
                if (hasErrors) {
                    FilledTonalButton(
                        onClick = onFixWithAi,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Fix with AI", fontSize = 11.sp)
                    }
                }

                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear", tint = Color.LightGray)
                }
            }
        }

        Divider(color = Color(0xFF30363D), modifier = Modifier.padding(vertical = 8.dp))

        // Logs Stream
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No runtime logs yet. Execute code in Web Preview to capture logs.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    val color = when (log.type) {
                        "error" -> Color(0xFFF85149)
                        "warn" -> Color(0xFFD29922)
                        "info" -> Color(0xFF58A6FF)
                        else -> Color(0xFFE6EDF3)
                    }
                    Text(
                        text = "[${log.type.uppercase()}] ${log.message}",
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
