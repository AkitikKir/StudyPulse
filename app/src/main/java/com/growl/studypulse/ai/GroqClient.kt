package com.growl.studypulse.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqClient(
    private val apiKey: String,
    private val model: String = "llama-3.3-70b-versatile"
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    @Throws(IOException::class)
    fun completeCard(term: String): AiSuggestion {
        val systemPrompt = """
            You are a concise educational assistant.
            Return valid JSON only with fields: definition, translation, example.
            translation should be in Russian.
            Keep each field under 140 characters.
        """.trimIndent()

        val userPrompt = "Generate fields for term: $term"
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            .put("max_tokens", 220)
            .put(
                "response_format",
                JSONObject().put("type", "json_object")
            )
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IOException("Groq HTTP ${response.code}: $errorBody")
            }

            val raw = response.body?.string().orEmpty()
            val root = JSONObject(raw)
            val content = root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val json = JSONObject(content)
            return AiSuggestion(
                definition = json.optString("definition").ifBlank { "No definition" },
                translation = json.optString("translation").ifBlank { "Нет перевода" },
                example = json.optString("example").ifBlank { "No example" },
                fromCache = false
            )
        }
    }
}
