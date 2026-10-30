#!/bin/bash
set -e

echo "=== 1. ВОССТАНАВЛИВАЕМ MainActivity.kt ИЗ GIT (чтобы убрать мусор) ==="
# Откатываем MainActivity до последнего коммита, где он работал
git checkout HEAD -- app/src/main/java/com/jarvis/app/MainActivity.kt

# Удаляем старые сломанные мосты, если они остались в файле
sed -i '/--- МОСТЫ ДЛЯ CommandProcessor ---/,/^}/d' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "=== 2. СОЗДАЕМ TranslationManager.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/TranslationManager.kt
package com.jarvis.app.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.app.MainActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object TranslationManager {
    fun start(context: MainActivity, fromLang: String, toLang: String) {
        context.sendReplyFromCommand("Режим перевода ($fromLang -> $toLang) включен. Скажите 'стоп' чтобы выйти.")
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.lowercase().contains("стоп") || text.lowercase().contains("stop")) {
                    sr.destroy(); context.sendReplyFromCommand("Режим перевода выключен."); return
                }
                if (text.isNotEmpty()) {
                    translate(context, text, fromLang, toLang) { translated ->
                        context.runOnUiThread {
                            context.addBubblePublic("Оригинал: $text", true)
                            context.animateBotReplyPublic(translated)
                        }
                    }
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
                sr.startListening(intent)
            }
            override fun onError(error: Int) { sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
    }

    private fun translate(context: MainActivity, text: String, from: String, to: String, callback: (String) -> Unit) {
        val client = OkHttpClient()
        val apiKey = PrefsManager.apiKey
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        
        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", "Ты переводчик. Переведи текст с $from на $to. Отвечай ТОЛЬКО переводом.") })
                        put(JSONObject().apply { put("role", "user"); put("content", text) })
                    })
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val ans = JSONObject(res.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        callback(ans)
                    }
                }
            } catch (e: Exception) { callback("Ошибка перевода") }
        }
    }
}
INNER

echo "=== 3. СОЗДАЕМ ChatManager.kt (история и запросы к ИИ) ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/ChatManager.kt
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
INNER

echo "=== 4. ПАТЧИМ MainActivity.kt (добавляем публичные методы и связываем) ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Делаем методы публичными для доступа из менеджеров
content = content.replace('private fun showSkeletonLoader()', 'fun showSkeletonLoaderPublic()')
content = content.replace('private fun hideSkeletonLoader()', 'fun hideSkeletonLoaderPublic()')
content = content.replace('private fun addBubble(text: String, isUser: Boolean)', 'fun addBubblePublic(text: String, isUser: Boolean)')
content = content.replace('private fun animateBotReply(fullText: String)', 'fun animateBotReplyPublic(fullText: String)')
content = content.replace('addBubble(text, isUser)', 'addBubblePublic(text, isUser)') # внутри самого класса

# Заменяем старый askChatGPT на вызов ChatManager
old_ask = re.search(r'private fun askChatGPT\(prompt: String\) \{.*?\n    \}', content, re.DOTALL)
if old_ask:
    new_ask = 'private fun askChatGPT(prompt: String) {\n        com.jarvis.app.core.ChatManager.ask(this, prompt)\n    }'
    content = content.replace(old_ask.group(0), new_ask)

# Добавляем мосты (в конец класса)
bridges = '''
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) {
        setJarvisAlarm(hour, minute)
    }
    
    fun sendReplyFromCommand(text: String) {
        reply(text)
    }
    
    fun startTranslationMode(fromLang: String, toLang: String) {
        com.jarvis.app.core.TranslationManager.start(this, fromLang, toLang)
    }
'''
if 'fun createAlarmFromCommand' not in content:
    last_brace = content.rfind('}')
    if last_brace != -1:
        content = content[:last_brace] + bridges + '\n' + content[last_brace:]

# Убеждаемся, что PrefsManager инициализирован
if 'PrefsManager.init(this)' not in content:
    content = content.replace(
        'override fun onCreate(savedInstanceState: Bundle?) {',
        'override fun onCreate(savedInstanceState: Bundle?) {\n        com.jarvis.app.core.PrefsManager.init(this)'
    )

with open(filepath, 'w') as f:
    f.write(content)
print("MainActivity.kt успешно пропатчен (публичные методы, мосты, вызов менеджеров).")
PYEOF

echo "✅ MainActivity разбит на части! Теперь делай git push."
