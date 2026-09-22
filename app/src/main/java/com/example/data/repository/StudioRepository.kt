package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiMessageEntity
import com.example.data.local.entity.AppSettingEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.remote.gemini.ChatMessage
import com.example.data.remote.gemini.GeminiApiClient
import com.example.data.remote.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class StudioRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val projectDao = db.projectDao()
    private val fileDao = db.projectFileDao()
    private val aiDao = db.aiMessageDao()
    private val userDao = db.userProfileDao()
    private val settingDao = db.appSettingDao()

    val supabaseClient = SupabaseClient()
    val geminiClient = GeminiApiClient()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val geminiKey = settingDao.getSetting("gemini_api_key") ?: ""
        val model = settingDao.getSetting("gemini_model") ?: "gemini-2.5-pro"
        val sbUrl = settingDao.getSetting("supabase_url") ?: ""
        val sbKey = settingDao.getSetting("supabase_anon_key") ?: ""
        val sbToken = settingDao.getSetting("supabase_token") ?: ""

        if (geminiKey.isNotEmpty()) geminiClient.setApiKey(geminiKey)
        geminiClient.setModel(model)
        supabaseClient.updateCredentials(sbUrl, sbKey, sbToken)
    }

    suspend fun saveSetting(key: String, value: String) {
        settingDao.setSetting(AppSettingEntity(key, value))
        when (key) {
            "gemini_api_key" -> geminiClient.setApiKey(value)
            "gemini_model" -> geminiClient.setModel(value)
            "supabase_url" -> {
                val sbKey = settingDao.getSetting("supabase_anon_key") ?: ""
                val sbToken = settingDao.getSetting("supabase_token") ?: ""
                supabaseClient.updateCredentials(value, sbKey, sbToken)
            }
            "supabase_anon_key" -> {
                val sbUrl = settingDao.getSetting("supabase_url") ?: ""
                val sbToken = settingDao.getSetting("supabase_token") ?: ""
                supabaseClient.updateCredentials(sbUrl, value, sbToken)
            }
            "supabase_token" -> {
                val sbUrl = settingDao.getSetting("supabase_url") ?: ""
                val sbKey = settingDao.getSetting("supabase_anon_key") ?: ""
                supabaseClient.updateCredentials(sbUrl, sbKey, value)
            }
        }
    }

    suspend fun getSetting(key: String): String? {
        return settingDao.getSetting(key)
    }

    fun getProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProject(id: String): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun createProject(name: String, description: String): ProjectEntity {
        val projectId = UUID.randomUUID().toString()
        val user = userDao.getCurrentUserDirect()
        val project = ProjectEntity(
            id = projectId,
            name = name.ifBlank { "New Application" },
            description = description,
            userId = user?.id ?: ""
        )
        projectDao.insertProject(project)

        // Create default initial files for web app
        val indexHtml = ProjectFileEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            fileName = "index.html",
            fileExtension = "html",
            content = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${name.ifBlank { "AI Studio App" }}</title>
  <link rel="stylesheet" href="style.css" />
</head>
<body>
  <div class="container">
    <header>
      <h1>${name.ifBlank { "V.Live AI Studio" }}</h1>
      <p class="subtitle">Built with intelligent AI execution</p>
    </header>
    <main>
      <div class="card">
        <h2>Live Workspace Ready</h2>
        <p>Edit code or prompt the AI to build complex applications, games, tools or dashboards.</p>
        <button id="actionBtn" class="btn">Execute Action</button>
      </div>
      <div id="output" class="output-box"></div>
    </main>
  </div>
  <script src="script.js"></script>
</body>
</html>
""".trimIndent()
        )

        val styleCss = ProjectFileEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            fileName = "style.css",
            fileExtension = "css",
            content = """
:root {
  --primary: #6366f1;
  --primary-hover: #4f46e5;
  --bg: #0f172a;
  --card-bg: #1e293b;
  --text: #f8fafc;
  --text-muted: #94a3b8;
  --border: #334155;
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  background-color: var(--bg);
  color: var(--text);
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
}

.container {
  width: 100%;
  max-width: 520px;
}

header {
  text-align: center;
  margin-bottom: 24px;
}

h1 {
  font-size: 26px;
  font-weight: 700;
  background: linear-gradient(135deg, #818cf8, #c084fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  color: var(--text-muted);
  font-size: 14px;
  margin-top: 4px;
}

.card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
}

.card h2 {
  font-size: 18px;
  margin-bottom: 8px;
}

.card p {
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 18px;
}

.btn {
  background: var(--primary);
  color: #fff;
  border: none;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  width: 100%;
}

.btn:active {
  transform: scale(0.98);
}

.output-box {
  margin-top: 16px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  border: 1px solid var(--border);
  font-size: 13px;
  display: none;
}
""".trimIndent()
        )

        val scriptJs = ProjectFileEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            fileName = "script.js",
            fileExtension = "js",
            content = """
console.log("App initialized successfully at " + new Date().toLocaleTimeString());

document.getElementById('actionBtn')?.addEventListener('click', () => {
  const out = document.getElementById('output');
  if (out) {
    out.style.display = 'block';
    out.innerHTML = '<strong>Event Fired:</strong> Clicked at ' + new Date().toLocaleTimeString();
  }
  console.log("Interactive button triggered.");
});
""".trimIndent()
        )

        fileDao.insertFiles(listOf(indexHtml, styleCss, scriptJs))

        // Sync with Supabase asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            supabaseClient.upsertProject(project)
            supabaseClient.upsertFile(indexHtml)
            supabaseClient.upsertFile(styleCss)
            supabaseClient.upsertFile(scriptJs)
        }

        return project
    }

    suspend fun deleteProject(id: String) {
        fileDao.deleteFilesByProjectId(id)
        aiDao.clearMessagesForProject(id)
        projectDao.deleteProjectById(id)
    }

    fun getProjectFiles(projectId: String): Flow<List<ProjectFileEntity>> =
        fileDao.getFilesForProject(projectId)

    suspend fun saveFile(file: ProjectFileEntity) {
        fileDao.insertFile(file)
        CoroutineScope(Dispatchers.IO).launch {
            supabaseClient.upsertFile(file)
        }
    }

    suspend fun addNewFile(projectId: String, fileName: String, content: String = ""): ProjectFileEntity {
        val ext = fileName.substringAfterLast('.', "txt")
        val file = ProjectFileEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            fileName = fileName,
            fileExtension = ext,
            content = content
        )
        fileDao.insertFile(file)
        CoroutineScope(Dispatchers.IO).launch {
            supabaseClient.upsertFile(file)
        }
        return file
    }

    suspend fun deleteFile(id: String) {
        fileDao.deleteFileById(id)
    }

    fun getAiMessages(projectId: String): Flow<List<AiMessageEntity>> =
        aiDao.getMessagesForProject(projectId)

    suspend fun sendAiPrompt(projectId: String, userPrompt: String, currentCode: String): Result<String> {
        val userMsg = AiMessageEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            role = "user",
            content = userPrompt
        )
        aiDao.insertMessage(userMsg)

        // Get past messages for conversation context
        val past = aiDao.getMessagesForProject(projectId).firstOrNull() ?: emptyList()
        val history = past.takeLast(6).map { ChatMessage(it.role, it.content) }

        val res = geminiClient.generateCode(
            prompt = userPrompt,
            currentCode = currentCode,
            conversationHistory = history
        )

        return if (res.isSuccess) {
            val reply = res.getOrThrow()
            val aiMsg = AiMessageEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                role = "model",
                content = reply
            )
            aiDao.insertMessage(aiMsg)
            Result.success(reply)
        } else {
            val errorMsg = res.exceptionOrNull()?.message ?: "AI Generation Error"
            val errorEntity = AiMessageEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                role = "system",
                content = "Error: $errorMsg"
            )
            aiDao.insertMessage(errorEntity)
            Result.failure(Exception(errorMsg))
        }
    }

    fun getCurrentUser(): Flow<UserProfileEntity?> = userDao.getCurrentUser()

    suspend fun saveUser(user: UserProfileEntity) {
        userDao.insertUser(user)
    }

    suspend fun clearUser() {
        userDao.clearUser()
        saveSetting("supabase_token", "")
    }

    suspend fun syncWithSupabase(): Result<Unit> {
        if (!supabaseClient.isConfigured()) return Result.success(Unit)
        return try {
            val remoteProjects = supabaseClient.fetchProjects()
            if (remoteProjects.isSuccess) {
                val list = remoteProjects.getOrThrow()
                if (list.isNotEmpty()) {
                    projectDao.insertProjects(list)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
