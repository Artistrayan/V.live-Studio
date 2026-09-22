package com.example.data.remote.gemini

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String
)

class GeminiApiClient(
    private var customApiKey: String = "",
    private var defaultModel: String = "gemini-2.5-pro"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun setApiKey(key: String) {
        this.customApiKey = key.trim()
    }

    fun setModel(model: String) {
        this.defaultModel = model.trim()
    }

    fun getEffectiveApiKey(): String {
        if (customApiKey.isNotBlank()) return customApiKey
        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun generateCode(
        prompt: String,
        currentCode: String = "",
        conversationHistory: List<ChatMessage> = emptyList(),
        systemPrompt: String = "You are V.Live Senior Full-Stack Architect & AI Studio Engine. Provide complete, bug-free, fully operational code. Include HTML, CSS, and modern JavaScript in executable format ready for live web preview. Never omit code with placeholders.",
        temperature: Float = 0.4f
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemini API Key is not set! Please enter your API key in Settings or AI Studio Secrets.")
            )
        }

        try {
            val root = JSONObject()

            // System instructions
            if (systemPrompt.isNotBlank()) {
                val sysInst = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                }
                root.put("systemInstruction", sysInst)
            }

            // Contents
            val contentsArray = JSONArray()
            // Append history
            for (msg in conversationHistory) {
                val roleName = if (msg.role == "user") "user" else "model"
                val cObj = JSONObject().apply {
                    put("role", roleName)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.content) })
                    })
                }
                contentsArray.put(cObj)
            }

            // Current prompt with code context
            val fullPromptText = if (currentCode.isNotBlank()) {
                "User Request: $prompt\n\nExisting Code / Project Context:\n```\n$currentCode\n```"
            } else {
                prompt
            }

            val userObj = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", fullPromptText) })
                })
            }
            contentsArray.put(userObj)
            root.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
            }
            root.put("generationConfig", genConfig)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$defaultModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(root.toString().toRequestBody(jsonMedia))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = try {
                    val errorJson = JSONObject(responseBody).optJSONObject("error")
                    errorJson?.optString("message", response.message) ?: response.message
                } catch (e: Exception) {
                    "Error ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errMsg))
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No code generated from Gemini API."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val sb = java.lang.StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text", ""))
                }
            }

            val generatedText = sb.toString().trim()
            Result.success(generatedText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
