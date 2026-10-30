#!/bin/bash
set -e

echo "=== 1. ИСПРАВЛЯЕМ PermissionsActivity.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/PermissionsActivity.kt
package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var btnMic: Button
    private lateinit var btnContacts: Button
    private lateinit var btnPhoneSms: Button
    private lateinit var btnNotifs: Button
    private lateinit var btnAccess: Button
    private lateinit var btnContinue: Button

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        window.statusBarColor = android.graphics.Color.parseColor("#0B0B0E")

        btnMic = findViewById(R.id.btnMic)
        btnContacts = findViewById(R.id.btnContacts)
        btnPhoneSms = findViewById(R.id.btnPhoneSms)
        btnNotifs = findViewById(R.id.btnNotifs)
        btnAccess = findViewById(R.id.btnAccess)
        btnContinue = findViewById(R.id.btnContinue)

        btnMic.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        btnContacts.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
        btnPhoneSms.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE))
        }
        btnNotifs.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnContinue.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasNotifs = isNotificationServiceEnabled()
        val hasAccess = isAccessibilityServiceEnabled()

        if (hasMic) btnMic.text = "Выдано ✓" else btnMic.text = "Выдать"
        if (hasContacts) btnContacts.text = "Выдано ✓" else btnContacts.text = "Выдать"
        if (hasPhone && hasSms) btnPhoneSms.text = "Выдано ✓" else btnPhoneSms.text = "Выдать"
        if (hasNotifs) btnNotifs.text = "Выдано ✓" else btnNotifs.text = "Выдать"
        if (hasAccess) btnAccess.text = "Выдано ✓" else btnAccess.text = "Выдать"

        if (hasMic && hasContacts && hasPhone && hasSms && hasNotifs) {
            btnContinue.isEnabled = true
            btnContinue.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#5B5BFF"))
        } else {
            btnContinue.isEnabled = false
            btnContinue.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2A2A35"))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${JarvisAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled != null && enabled.contains(service)
    }
}
INNER

echo "=== 2. ИСПРАВЛЯЕМ CommandProcessor.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/CommandProcessor.kt
package com.jarvis.app.core

import com.jarvis.app.MainActivity

object CommandProcessor {
    fun process(context: MainActivity, raw: String): Boolean {
        val cmd = raw.lowercase().trim()
        
        if (cmd.startsWith("будильник на") || cmd.startsWith("поставь будильник на")) {
            val timePart = cmd.replace("поставь ", "").replace("будильник на ", "").trim()
            val isDaily = cmd.contains("каждый день") || cmd.contains("ежедневно")
            val regex = Regex("(\\d{1,2})[:. ](\\d{2})")
            val match = regex.find(timePart)
            if (match != null) {
                val h = match.groupValues[1].toIntOrNull() ?: 7
                val m = match.groupValues[2].toIntOrNull() ?: 0
                context.createAlarmFromCommand(h, m, isDaily)
                return true
            }
        }

        if (cmd.contains("список будильников") || cmd.contains("какие будильники")) {
            val alarms = PrefsManager.getAlarms()
            if (alarms.isEmpty()) {
                context.sendReplyFromCommand("У вас нет установленных будильников.")
            } else {
                val text = alarms.joinToString("\n") { 
                    val time = String.format("%02d:%02d", it.hour, it.minute)
                    if (it.daily) "🕒 $time (каждый день)" else "🕒 $time (один раз)"
                }
                context.sendReplyFromCommand("Ваши будильники:\n$text")
            }
            return true
        }

        if (cmd.contains("включи автоответ")) {
            PrefsManager.autoReplyEnabled = true
            context.sendReplyFromCommand("Автоответ включен. Текст: ${PrefsManager.autoReplyText}")
            return true
        }
        if (cmd.contains("выключи автоответ")) {
            PrefsManager.autoReplyEnabled = false
            context.sendReplyFromCommand("Автоответ выключен.")
            return true
        }
        
        if (cmd.contains("переведи с ") && cmd.contains(" на ")) {
            val parts = cmd.split(" на ")
            if (parts.size == 2) {
                val fromLangRaw = parts[0].replace("переведи с ", "").trim()
                val toLangRaw = parts[1].trim()
                context.startTranslationMode(fromLangRaw, toLangRaw)
                return true
            }
        }
        
        return false
    }
}
INNER

echo "=== 3. ДОБАВЛЯЕМ МОСТЫ В MainActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Убираем старые мосты, если они были
content = re.sub(r'// --- МОСТЫ ДЛЯ CommandProcessor ---.*?\n    \}', '', content, flags=re.DOTALL)

bridges = '''
    // --- МОСТЫ ДЛЯ CommandProcessor ---
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) {
        setJarvisAlarm(hour, minute) 
    }
    
    fun sendReplyFromCommand(text: String) {
        reply(text)
    }
    
    fun startTranslationMode(fromLang: String, toLang: String) {
        reply("Режим перевода ($fromLang -> $toLang) включен. Скажите 'стоп' чтобы выйти.")
        val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.lowercase().contains("стоп") || text.lowercase().contains("stop")) {
                    sr.destroy(); reply("Режим перевода выключен."); return
                }
                if (text.isNotEmpty()) {
                    executor.execute {
                        try {
                            val json = org.json.JSONObject().apply {
                                put("model", "gpt-4o-mini")
                                put("messages", org.json.JSONArray().apply {
                                    put(org.json.JSONObject().apply { put("role", "system"); put("content", "Ты переводчик. Переведи текст с $fromLang на $toLang. Отвечай ТОЛЬКО переводом.") })
                                    put(org.json.JSONObject().apply { put("role", "user"); put("content", text) })
                                })
                            }
                            val body = json.toString().toRequestBody("application/json".toMediaType())
                            val req = okhttp3.Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                            client.newCall(req).execute().use { res ->
                                if (res.isSuccessful) {
                                    val ans = org.json.JSONObject(res.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                                    runOnUiThread { addBubble("Оригинал: $text", true); animateBotReply(ans) }
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }
                sr.startListening(intent)
            }
            override fun onError(error: Int) { sr.startListening(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)) }
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        sr.startListening(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
    }
'''

# Вставляем перед последней закрывающей скобкой класса
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
print("MainActivity.kt обновлен (мосты добавлены).")
PYEOF

echo "✅ ВСЁ ГОТОВО! Файлы перезаписаны, мосты добавлены. Делай git push."
