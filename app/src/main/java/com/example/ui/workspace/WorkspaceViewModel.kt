package com.example.ui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AiMessageEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ConsoleLogItem(
    val type: String, // "log", "warn", "error", "info"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkspaceUiState(
    val project: ProjectEntity? = null,
    val files: List<ProjectFileEntity> = emptyList(),
    val activeFileId: String? = null,
    val activeFileContent: String = "",
    val activeFileName: String = "",
    val activeFileExtension: String = "",
    val activeTabIndex: Int = 0, // 0: Editor, 1: Live Preview, 2: AI Architect, 3: Console
    val isAiGenerating: Boolean = false,
    val aiPromptInput: String = "",
    val aiErrorMessage: String? = null,
    val consoleLogs: List<ConsoleLogItem> = emptyList(),
    val isSaved: Boolean = true,
    val bundledHtmlForPreview: String = "",
    val refreshCounter: Int = 0
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = StudioRepository(application)
    private var currentProjectId: String = ""

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState = _uiState.asStateFlow()

    fun initProject(projectId: String) {
        if (currentProjectId == projectId && _uiState.value.project != null) return
        currentProjectId = projectId

        viewModelScope.launch {
            repository.syncProjectFiles(projectId)
        }

        viewModelScope.launch {
            val project = repository.getProject(projectId)
            _uiState.value = _uiState.value.copy(project = project)

            repository.getProjectFiles(projectId).collect { fileList ->
                val currentActiveId = _uiState.value.activeFileId
                val active = if (currentActiveId != null) {
                    fileList.find { it.id == currentActiveId } ?: fileList.firstOrNull()
                } else {
                    fileList.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    files = fileList,
                    activeFileId = active?.id,
                    activeFileContent = active?.content ?: "",
                    activeFileName = active?.fileName ?: "",
                    activeFileExtension = active?.fileExtension ?: ""
                )
                generateBundledHtml(fileList)
            }
        }
    }

    fun getAiMessages(): Flow<List<AiMessageEntity>> {
        return repository.getAiMessages(currentProjectId)
    }

    fun selectFile(fileId: String) {
        val file = _uiState.value.files.find { it.id == fileId } ?: return
        _uiState.value = _uiState.value.copy(
            activeFileId = file.id,
            activeFileContent = file.content,
            activeFileName = file.fileName,
            activeFileExtension = file.fileExtension,
            isSaved = true
        )
    }

    fun updateActiveFileContent(newContent: String) {
        _uiState.value = _uiState.value.copy(
            activeFileContent = newContent,
            isSaved = false
        )
        val activeId = _uiState.value.activeFileId ?: return
        val currentFile = _uiState.value.files.find { it.id == activeId } ?: return

        viewModelScope.launch {
            val updated = currentFile.copy(content = newContent, updatedAt = System.currentTimeMillis())
            repository.saveFile(updated)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun setTabIndex(index: Int) {
        _uiState.value = _uiState.value.copy(activeTabIndex = index)
        if (index == 1) { // Switching to Live Preview
            triggerRefresh()
        }
    }

    fun triggerRefresh() {
        generateBundledHtml(_uiState.value.files)
        _uiState.value = _uiState.value.copy(refreshCounter = _uiState.value.refreshCounter + 1)
    }

    fun addNewFile(fileName: String) {
        if (fileName.isBlank()) return
        viewModelScope.launch {
            val file = repository.addNewFile(currentProjectId, fileName, "")
            selectFile(file.id)
        }
    }

    fun deleteCurrentFile() {
        val fileId = _uiState.value.activeFileId ?: return
        if (_uiState.value.files.size <= 1) return // Keep at least one file
        viewModelScope.launch {
            repository.deleteFile(fileId)
        }
    }

    fun onAiPromptChange(prompt: String) {
        _uiState.value = _uiState.value.copy(aiPromptInput = prompt, aiErrorMessage = null)
    }

    fun sendAiPrompt() {
        val prompt = _uiState.value.aiPromptInput.trim()
        if (prompt.isBlank()) return

        _uiState.value = _uiState.value.copy(isAiGenerating = true, aiErrorMessage = null)
        val currentCode = _uiState.value.activeFileContent

        viewModelScope.launch {
            val result = repository.sendAiPrompt(currentProjectId, prompt, currentCode)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isAiGenerating = false,
                    aiPromptInput = ""
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isAiGenerating = false,
                    aiErrorMessage = result.exceptionOrNull()?.message ?: "AI Generation Error"
                )
            }
        }
    }

    fun applyAiCodeToActiveFile(code: String) {
        val cleanedCode = extractCodeBlock(code)
        updateActiveFileContent(cleanedCode)
        setTabIndex(0) // Switch to editor
    }

    fun applyFullWebSolution(html: String, css: String, js: String) {
        viewModelScope.launch {
            _uiState.value.files.find { it.fileName == "index.html" }?.let {
                repository.saveFile(it.copy(content = html, updatedAt = System.currentTimeMillis()))
            }
            _uiState.value.files.find { it.fileName == "style.css" }?.let {
                repository.saveFile(it.copy(content = css, updatedAt = System.currentTimeMillis()))
            }
            _uiState.value.files.find { it.fileName == "script.js" }?.let {
                repository.saveFile(it.copy(content = js, updatedAt = System.currentTimeMillis()))
            }
            triggerRefresh()
        }
    }

    fun addConsoleLog(type: String, message: String) {
        val newItem = ConsoleLogItem(type, message)
        val current = _uiState.value.consoleLogs.takeLast(100).toMutableList()
        current.add(newItem)
        _uiState.value = _uiState.value.copy(consoleLogs = current)
    }

    fun clearConsoleLogs() {
        _uiState.value = _uiState.value.copy(consoleLogs = emptyList())
    }

    fun fixErrorsWithAi() {
        val errors = _uiState.value.consoleLogs.filter { it.type == "error" }
        if (errors.isEmpty()) return

        val errorPrompt = "Fix the following runtime errors encountered in execution:\n" +
                errors.joinToString("\n") { it.message } +
                "\nPlease provide corrected code for the current file (${_uiState.value.activeFileName})."

        _uiState.value = _uiState.value.copy(aiPromptInput = errorPrompt, activeTabIndex = 2)
        sendAiPrompt()
    }

    private fun generateBundledHtml(files: List<ProjectFileEntity>) {
        val indexHtml = files.find { it.fileName == "index.html" }?.content ?: "<html><body><h3>Workspace Ready</h3></body></html>"
        val styleCss = files.find { it.fileName == "style.css" }?.content ?: ""
        val scriptJs = files.find { it.fileName == "script.js" }?.content ?: ""

        // Console interceptor script injected into head
        val consoleInterceptor = """
<script>
(function() {
  function sendLog(type, args) {
    try {
      const msg = Array.from(args).map(a => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' ');
      if (window.AndroidBridge && window.AndroidBridge.postConsoleLog) {
        window.AndroidBridge.postConsoleLog(type, msg);
      }
    } catch(e) {}
  }
  const origLog = console.log;
  const origWarn = console.warn;
  const origError = console.error;
  const origInfo = console.info;

  console.log = function() { origLog.apply(console, arguments); sendLog('log', arguments); };
  console.warn = function() { origWarn.apply(console, arguments); sendLog('warn', arguments); };
  console.error = function() { origError.apply(console, arguments); sendLog('error', arguments); };
  console.info = function() { origInfo.apply(console, arguments); sendLog('info', arguments); };

  window.onerror = function(message, source, lineno, colno, error) {
    sendLog('error', [message + ' (line ' + lineno + ')']);
    return false;
  };
})();
</script>
""".trimIndent()

        // Inject styles
        var finalHtml = indexHtml
        if (styleCss.isNotBlank()) {
            val styleTag = "<style>\n$styleCss\n</style>"
            finalHtml = if (finalHtml.contains("</head>", ignoreCase = true)) {
                finalHtml.replace("</head>", "$styleTag\n</head>")
            } else {
                "$styleTag\n$finalHtml"
            }
        }

        // Inject scripts
        if (scriptJs.isNotBlank()) {
            val scriptTag = "<script>\n$scriptJs\n</script>"
            finalHtml = if (finalHtml.contains("</body>", ignoreCase = true)) {
                finalHtml.replace("</body>", "$scriptTag\n</body>")
            } else {
                "$finalHtml\n$scriptTag"
            }
        }

        // Inject interceptor at the top of head
        finalHtml = if (finalHtml.contains("<head>", ignoreCase = true)) {
            finalHtml.replace("<head>", "<head>\n$consoleInterceptor")
        } else {
            "$consoleInterceptor\n$finalHtml"
        }

        _uiState.value = _uiState.value.copy(bundledHtmlForPreview = finalHtml)
    }

    private fun extractCodeBlock(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val firstLineEnd = trimmed.indexOf('\n')
            if (firstLineEnd != -1) {
                val lastBackticks = trimmed.lastIndexOf("```")
                if (lastBackticks > firstLineEnd) {
                    return trimmed.substring(firstLineEnd + 1, lastBackticks).trim()
                }
            }
        }
        return trimmed
    }
}
