#!/bin/bash
set -e

echo "=== 1. ПЕРЕЗАПИСЫВАЕМ PermissionsActivity.kt ==="
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

echo "=== 2. ПЕРЕЗАПИСЫВАЕМ CommandProcessor.kt ==="
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

echo "=== 3. ПЕРЕЗАПИСЫВАЕМ ChatManager.kt ==="
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

echo "=== 4. ПЕРЕЗАПИСЫВАЕМ TranslationManager.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/TranslationManager.kt
package com.jarvis.app.core

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

echo "=== 5. ПЕРЕЗАПИСЫВАЕМ SmartServices.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/SmartServices.kt
package com.jarvis.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.RemoteInput
import android.util.Log
import com.jarvis.app.core.PrefsManager

class GeoService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                checkGeofences(loc)
            }
        }
        try { fusedLocationClient.requestLocationUpdates(request, locationCallback, null) } catch (e: SecurityException) {}
    }

    private fun checkGeofences(loc: Location) {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val homeLat = prefs.getString("home_lat", "")?.toDoubleOrNull() ?: return
        val homeLon = prefs.getString("home_lon", "")?.toDoubleOrNull() ?: return
        val results = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, homeLat, homeLon, results)
        if (results[0] < 200f) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (!wifiManager.isWifiEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                Log.d("JarvisGeo", "Пришел домой! Включил Wi-Fi")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class SmartNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        PrefsManager.init(this)
        if (!PrefsManager.autoReplyEnabled) return

        val pkg = sbn.packageName
        if (pkg != "org.telegram.messenger" && pkg != "com.whatsapp") return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Неизвестный"

        val actions = sbn.notification.actions ?: return
        for (action in actions) {
            if (action.remoteInputs != null) {
                val remoteInput = action.remoteInputs.firstOrNull { it.allowFreeFormInput }
                if (remoteInput != null) {
                    val intent = Intent()
                    val bundle = android.os.Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, PrefsManager.autoReplyText)
                    RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
                    try {
                        action.actionIntent.send(this, 0, intent)
                        Log.d("JarvisAutoReply", "Ответил на сообщение от $title")
                    } catch (e: Exception) {
                        Log.e("JarvisAutoReply", "Ошибка: ${e.message}")
                    }
                }
            }
        }
    }
}
INNER

echo "✅ Все критические файлы перезаписаны. Делай пуш."
