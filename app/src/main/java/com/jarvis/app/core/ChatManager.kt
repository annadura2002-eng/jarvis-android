package com.jarvis.app.core

import com.jarvis.app.MainActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object ChatManager {
    private val chatHistory = mutableListOf<JSONObject>()
    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()

    fun ask(context: MainActivity, prompt: String) {
        context.showSkeletonLoaderPublic()
        executor.execute {
            try {
                chatHistory.add(JSONObject().apply { put("role", "user"); put("content", prompt) })
                while (chatHistory.size > 10) { chatHistory.removeAt(0) }

                val messagesArray = JSONArray()
                messagesArray.put(JSONObject().apply { 
                    put("role", "system"); 
                    put("content", "Ты - голосовой ассистент. Сегодня ${SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())}. Отвечай кратко.") 
                })
                for (msg in chatHistory) messagesArray.put(msg)

                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messagesArray)
                }
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${PrefsManager.apiKey}").post(body).build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answer = JSONObject(response.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        chatHistory.add(JSONObject().apply { put("role", "assistant"); put("content", answer) })
                        context.runOnUiThread { context.hideSkeletonLoaderPublic(); context.animateBotReplyPublic(answer) }
                    } else context.runOnUiThread { context.hideSkeletonLoaderPublic(); context.sendReplyFromCommand("Ошибка ИИ: ${response.code}") }
                }
            } catch (e: Exception) { context.runOnUiThread { context.hideSkeletonLoaderPublic(); context.sendReplyFromCommand("Ошибка сети.") } }
        }
    }
}
