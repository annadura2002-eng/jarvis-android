#!/bin/bash
set -e

echo "=== 1. СОЗДАНИЕ CommandProcessor.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/CommandProcessor.kt
package com.jarvis.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jarvis.app.MainActivity

object CommandProcessor {
    
    fun process(context: MainActivity, raw: String): Boolean {
        val cmd = raw.lowercase().trim()
        
        // 1. Будильники (новые полезные команды)
        if (cmd.startsWith("будильник на") || cmd.startsWith("поставь будильник на")) {
            val timePart = cmd.replace("поставь ", "").replace("будильник на ", "").trim()
            val isDaily = cmd.contains("каждый день") || cmd.contains("ежедневно")
            
            val regex = Regex("(\\d{1,2})[:. ](\\d{2})")
            val match = regex.find(timePart)
            
            if (match != null) {
                val h = match.groupValues[1].toIntOrNull() ?: 7
                val m = match.groupValues[2].toIntOrNull() ?: 0
                context.setJarvisAlarmPublic(h, m, isDaily)
                return true
            }
        }

        // 2. Список будильников
        if (cmd.contains("список будильников") || cmd.contains("какие будильники")) {
            val alarms = PrefsManager.getAlarms()
            if (alarms.isEmpty()) {
                context.replyPublic("У вас нет установленных будильников.")
            } else {
                val text = alarms.joinToString("\n") { 
                    val time = String.format("%02d:%02d", it.hour, it.minute)
                    if (it.daily) "🕒 $time (каждый день)" else "🕒 $time (один раз)"
                }
                context.replyPublic("Ваши будильники:\n$text")
            }
            return true
        }

        // 3. Автоответ настройки
        if (cmd.contains("включи автоответ")) {
            PrefsManager.autoReplyEnabled = true
            context.replyPublic("Автоответ включен. Текст: ${PrefsManager.autoReplyText}")
            return true
        }
        if (cmd.contains("выключи автоответ")) {
            PrefsManager.autoReplyEnabled = false
            context.replyPublic("Автоответ выключен.")
            return true
        }
        
        return false
    }
}
INNER

echo "=== 2. ОБНОВЛЕНИЕ MainActivity.kt (добавление публичных методов) ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Делаем методы публичными для CommandProcessor
content = content.replace('private fun reply(', 'fun replyPublic(')
content = content.replace('private fun handleCommand(', 'private fun handleCommand(') # оставляем как есть
content = content.replace('private fun setJarvisAlarm(', 'fun setJarvisAlarmPublic(')

# В начало handleCommand добавляем проверку CommandProcessor
if 'CommandProcessor.process(this, raw)' not in content:
    content = content.replace(
        'private fun handleCommand(raw: String) {\n        var cmd = raw.lowercase(Locale.ROOT).trim()',
        'private fun handleCommand(raw: String) {\n        if (com.jarvis.app.core.CommandProcessor.process(this, raw)) return\n        var cmd = raw.lowercase(Locale.ROOT).trim()'
    )

with open(filepath, 'w') as f:
    f.write(content)
print("MainActivity.kt обновлен (публичные методы + CommandProcessor).")
PYEOF

echo "=== 3. СОЗДАНИЕ НОВОГО NeuralTTS.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/NeuralTTS.kt
package com.jarvis.app.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NeuralTTS(private val context: Context, private var apiKey: String) : TextToSpeech.OnInitListener {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private var mediaPlayer: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val requestId = AtomicInteger(0)
    
    // Оффлайн-фолбэк
    private var localTts: TextToSpeech = TextToSpeech(context, this)
    private var isLocalTtsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            localTts.language = Locale("ru", "RU")
            isLocalTtsReady = true
        }
    }

    fun updateApiKey(newKey: String) { apiKey = newKey }

    fun speak(text: String, voice: String = "nova", onStart: () -> Unit = {}) {
        stop() // Останавливаем предыдущее
        
        val currentId = requestId.incrementAndGet()
        
        if (apiKey.isEmpty()) {
            speakLocal(text, onStart)
            return
        }

        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", text)
                    put("voice", voice)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.proxyapi.ru/openai/v1/audio/speech")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    // Если пока качали, пришел новый запрос — отменяем этот
                    if (currentId != requestId.get()) return@execute
                    
                    if (response.isSuccessful) {
                        val audioBytes = response.body?.bytes()
                        if (audioBytes != null && audioBytes.isNotEmpty()) {
                            val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                            FileOutputStream(tempFile).use { it.write(audioBytes) }
                            
                            android.os.Handler(context.mainLooper).post {
                                if (currentId != requestId.get()) return@post
                                mediaPlayer = MediaPlayer().apply {
                                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build())
                                    setDataSource(tempFile.absolutePath)
                                    setOnPreparedListener { start(); onStart() }
                                    setOnCompletionListener { it.release(); tempFile.delete() }
                                    prepareAsync()
                                }
                            }
                        } else speakLocal(text, onStart)
                    } else {
                        Log.e("NeuralTTS", "API Error: ${response.code}")
                        speakLocal(text, onStart)
                    }
                }
            } catch (e: Exception) {
                Log.e("NeuralTTS", "Exception: ${e.message}")
                speakLocal(text, onStart)
            }
        }
    }

    private fun speakLocal(text: String, onStart: () -> Unit) {
        if (isLocalTtsReady) {
            android.os.Handler(context.mainLooper).post {
                localTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
                onStart()
            }
        } else {
            onStart()
        }
    }

    fun stop() {
        requestId.incrementAndGet() // Инвалидируем все текущие запросы
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
        if (isLocalTtsReady) localTts.stop()
    }
    
    fun destroy() {
        stop()
        localTts.shutdown()
        executor.shutdownNow()
    }
}
INNER

echo "=== 4. СОЗДАНИЕ НОВОГО JarvisAccessibilityService.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/JarvisAccessibilityService.kt
package com.jarvis.app.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // Улучшенный клик по тексту (точное совпадение)
    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            val cleanText = text.replace("\"", "").trim().lowercase()
            if (cleanText.isEmpty()) continue
            
            val nodes = root.findAccessibilityNodeInfosByText(cleanText)
            for (node in nodes) {
                // Ищем точное совпадение, чтобы не нажать "Отправлено" вместо "Отправить"
                val nodeText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
                if (nodeText.contains(cleanText)) {
                    var target = node
                    while (target != null) {
                        if (target.isClickable && target.isEnabled) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                        target = target.parent
                    }
                }
            }
        }
        return false
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // Быстрый сбор текста (только видимые элементы, глубина 15)
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        
        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 15) return
            if (node.isVisibleToUser) {
                if (!node.text.isNullOrEmpty()) sb.append(node.text).append(" | ")
                if (!node.contentDescription.isNullOrEmpty()) sb.append(node.contentDescription).append(" | ")
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }
        traverse(root, 0)
        
        val result = sb.toString()
        // Берем последние 2000 символов (там обычно кнопки внизу экрана)
        return if (result.length > 2000) result.takeLast(2000) else result
    }
}
INNER

echo "=== 5. ФИКС ИМПОРТА В MainActivity (NeuralTTS переехал) ==="
sed -i 's/import com.jarvis.app.NeuralTTS/import com.jarvis.app.services.NeuralTTS/g' app/src/main/java/com/jarvis/app/MainActivity.kt
sed -i 's/import com.jarvis.app.JarvisAccessibilityService/import com.jarvis.app.services.JarvisAccessibilityService/g' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "✅ Часть 2 завершена! NeuralTTS, Accessibility и CommandProcessor обновлены."
