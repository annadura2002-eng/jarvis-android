#!/bin/bash
set -e

echo "=== 1. ВОССТАНАВЛИВАЕМ PrefsManager.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/PrefsManager.kt
package com.jarvis.app.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PrefsManager {
    private const val PREFS_NAME = "JarvisPrefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var assistantName: String
        get() = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        set(value) = prefs.edit().putString("assistant_name", value).apply()

    var selectedVoice: String
        get() = prefs.getString("neural_voice", "nova") ?: "nova"
        set(value) = prefs.edit().putString("neural_voice", value).apply()

    var autoReplyEnabled: Boolean
        get() = prefs.getBoolean("auto_reply", false)
        set(value) = prefs.edit().putBoolean("auto_reply", value).apply()

    var autoReplyText: String
        get() = prefs.getString("auto_reply_text", "Я сейчас занят, перезвоню позже.") ?: ""
        set(value) = prefs.edit().putString("auto_reply_text", value).apply()

    var bubbleRunning: Boolean
        get() = prefs.getBoolean("bubble_running", false)
        set(value) = prefs.edit().putBoolean("bubble_running", value).apply()

    fun getAlarms(): MutableList<AlarmData> {
        val jsonStr = prefs.getString("alarms_json", "[]") ?: "[]"
        val list = mutableListOf<AlarmData>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(AlarmData(obj.getInt("hour"), obj.getInt("minute"), obj.getBoolean("daily"), obj.getInt("id")))
            }
        } catch (e: Exception) {}
        return list
    }

    fun saveAlarm(alarm: AlarmData) {
        val list = getAlarms()
        list.add(alarm)
        val arr = JSONArray()
        for (a in list) { arr.put(JSONObject().apply { put("hour", a.hour); put("minute", a.minute); put("daily", a.daily); put("id", a.id) }) }
        prefs.edit().putString("alarms_json", arr.toString()).apply()
    }

    fun removeAlarm(id: Int) {
        val list = getAlarms()
        list.removeAll { it.id == id }
        val arr = JSONArray()
        for (a in list) { arr.put(JSONObject().apply { put("hour", a.hour); put("minute", a.minute); put("daily", a.daily); put("id", a.id) }) }
        prefs.edit().putString("alarms_json", arr.toString()).apply()
    }
}

data class AlarmData(val hour: Int, val minute: Int, val daily: Boolean, val id: Int = (System.currentTimeMillis() % 10000).toInt())
INNER

echo "=== 2. ВОССТАНАВЛИВАЕМ AlarmService.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/AlarmService.kt
package com.jarvis.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.Calendar

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") { stopAlarm(); return START_NOT_STICKY }

        val hour = intent?.getIntExtra("hour", 7) ?: 7
        val minute = intent?.getIntExtra("minute", 0) ?: 0
        val daily = intent?.getBooleanExtra("daily", false) ?: false
        val alarmId = intent?.getIntExtra("id", 0) ?: 0

        if (daily) scheduleNextDaily(hour, minute, alarmId)

        createNotificationChannel()
        val stopIntent = Intent(this, AlarmService::class.java).apply { action = "STOP_ALARM" }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fullScreenIntent = Intent(this, com.jarvis.app.MainActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 3, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "AlarmChannel")
            .setContentTitle("Jarvis Будильник").setContentText("Проснись! Нажми, чтобы остановить.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPendingIntent).setOngoing(true).build()

        startForeground(3, notification)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis:AlarmWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        playAlarmSound()
        startVibration()

        return START_STICKY
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = true; prepare(); start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        else @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
    }

    private fun stopAlarm() {
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        vibrator?.cancel(); wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun scheduleNextDaily(hour: Int, minute: Int, id: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmService::class.java).apply {
            putExtra("hour", hour); putExtra("minute", minute); putExtra("daily", true); putExtra("id", id)
        }
        val pendingIntent = PendingIntent.getService(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("AlarmChannel", "Jarvis Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() { stopAlarm(); super.onDestroy() }
}
INNER

echo "=== 3. ВОССТАНАВЛИВАЕМ VoiceService.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/VoiceService.kt
package com.jarvis.app.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import com.jarvis.app.core.PrefsManager
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import org.vosk.android.RecognitionListener as VoskListener
import org.vosk.android.SpeechService
import java.io.IOException
import android.content.pm.ServiceInfo

class VoiceService : Service(), VoskListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeWord = "jarvis"
    private var isListeningCommand = false

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
        wakeWord = PrefsManager.assistantName.lowercase()
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "JarvisChannel")
            .setContentTitle("Jarvis активен").setContentText("Слушаю...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setPriority(NotificationCompat.PRIORITY_LOW).build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, notification)
        initVosk()
    }

    private fun initVosk() {
        StorageService.unpack(this, "model-ru", "model",
            { model -> this.model = model; startVoskWakeWord() },
            { exception -> exception.printStackTrace() }
        )
    }

    private fun startVoskWakeWord() {
        if (isListeningCommand) return
        try {
            speechService?.shutdown()
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) { e.printStackTrace() }
    }

    override fun onResult(hypothesis: String) {
        if (hypothesis.contains("\"$wakeWord\"")) { speechService?.stop(); listenForCommand() }
    }

    private fun listenForCommand() {
        if (isListeningCommand) return
        isListeningCommand = true
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotEmpty()) sendBroadcast(Intent("WAKE_WORD_DETECTED").putExtra("command", text))
                    isListeningCommand = false; startVoskWakeWord()
                }
                override fun onError(error: Int) { isListeningCommand = false; startVoskWakeWord() }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { startVoskWakeWord() }
    override fun onTimeout() { startVoskWakeWord() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("JarvisChannel", "Jarvis Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        speechService?.stop(); speechService?.shutdown(); speechRecognizer?.destroy(); super.onDestroy()
    }
}
INNER

echo "✅ Все недостающие файлы восстановлены!"
