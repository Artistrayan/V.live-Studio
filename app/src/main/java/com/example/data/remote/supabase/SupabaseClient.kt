package com.example.data.remote.supabase

import com.example.data.local.entity.AiMessageEntity
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.ProjectFileEntity
import com.example.data.local.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseClient(
    private var supabaseUrl: String = "",
    private var anonKey: String = "",
    private var accessToken: String = ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun updateCredentials(url: String, key: String, token: String = "") {
        this.supabaseUrl = url.trim().removeSuffix("/")
        this.anonKey = key.trim()
        if (token.isNotEmpty()) {
            this.accessToken = token.trim()
        }
    }

    fun isConfigured(): Boolean {
        return supabaseUrl.isNotBlank() && anonKey.isNotBlank()
    }

    fun getOAuthUrl(provider: String): String {
        if (!isConfigured()) return ""
        return "$supabaseUrl/auth/v1/authorize?provider=$provider"
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Supabase URL and Anon Key are missing."))
        }
        val startTime = System.currentTimeMillis()
        try {
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            if (response.isSuccessful || response.code in 200..299) {
                Result.success("Connected (${latency}ms)")
            } else {
                Result.failure(Exception("Database returned HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun signUp(email: String, password: String): Result<UserProfileEntity> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Supabase URL and Anon Key are required"))
        }
        try {
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("msg", JSONObject(responseBody).optString("error_description", response.message))
                } catch (e: Exception) {
                    response.message
                }
                return@withContext Result.failure(Exception("Sign up failed: $errorMsg"))
            }

            val json = JSONObject(responseBody)
            val userObj = json.optJSONObject("user") ?: json
            val id = userObj.optString("id", System.currentTimeMillis().toString())
            val returnedEmail = userObj.optString("email", email)
            val token = json.optString("access_token", "")
            if (token.isNotEmpty()) {
                accessToken = token
            }

            val profile = UserProfileEntity(
                id = id,
                email = returnedEmail,
                username = returnedEmail.substringBefore("@"),
                planTier = "Unlimited VIP",
                credits = 500000
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Pair<UserProfileEntity, String>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured yet. Please provide your Supabase URL & Key in Settings."))
        }
        try {
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error_description", JSONObject(responseBody).optString("msg", response.message))
                } catch (e: Exception) {
                    response.message
                }
                return@withContext Result.failure(Exception("Authentication error: $errorMsg"))
            }

            val json = JSONObject(responseBody)
            val token = json.optString("access_token", "")
            accessToken = token

            val userObj = json.optJSONObject("user")
            val id = userObj?.optString("id") ?: System.currentTimeMillis().toString()
            val userEmail = userObj?.optString("email") ?: email
            val profile = UserProfileEntity(
                id = id,
                email = userEmail,
                username = userEmail.substringBefore("@"),
                planTier = "Unlimited VIP",
                credits = 500000
            )
            Result.success(Pair(profile, token))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProjects(): Result<List<ProjectEntity>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/projects?select=*")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Supabase fetch failed: ${response.code}"))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<ProjectEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProjectEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis()),
                        userId = obj.optString("user_id", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertProject(project: ProjectEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val body = JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("description", project.description)
                put("created_at", project.createdAt)
                put("updated_at", project.updatedAt)
                put("user_id", project.userId)
            }
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/projects")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code == 201 || response.code == 200 || response.code == 204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save project to Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertFile(file: ProjectFileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val body = JSONObject().apply {
                put("id", file.id)
                put("project_id", file.projectId)
                put("file_name", file.fileName)
                put("file_extension", file.fileExtension)
                put("content", file.content)
                put("updated_at", file.updatedAt)
            }
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/project_files")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save file to Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/projects?id=eq.$projectId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete project from Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFiles(projectId: String): Result<List<ProjectFileEntity>> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(emptyList())
        try {
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/project_files?project_id=eq.$projectId&select=*")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch files from Supabase: ${response.code}"))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<ProjectFileEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProjectFileEntity(
                        id = obj.getString("id"),
                        projectId = obj.getString("project_id"),
                        fileName = obj.getString("file_name"),
                        fileExtension = obj.optString("file_extension", "txt"),
                        content = obj.optString("content", ""),
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/project_files?id=eq.$fileId")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file from Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertAiMessage(message: AiMessageEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val body = JSONObject().apply {
                put("id", message.id)
                put("project_id", message.projectId)
                put("role", message.role)
                put("content", message.content)
                put("timestamp", message.timestamp)
            }
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/ai_messages")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save AI message to Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertUserProfile(profile: UserProfileEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext Result.success(Unit)
        try {
            val body = JSONObject().apply {
                put("id", profile.id)
                put("email", profile.email)
                put("username", profile.username)
                put("avatar_url", profile.avatarUrl)
                put("plan_tier", profile.planTier)
                put("credits", profile.credits)
                put("updated_at", profile.updatedAt)
            }
            val authHeader = if (accessToken.isNotEmpty()) "Bearer $accessToken" else "Bearer $anonKey"
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_profiles")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 200..204) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save user profile to Supabase: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
