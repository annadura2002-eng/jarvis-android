#!/bin/bash
set -e

echo "=== 1. ДОБАВЛЯЕМ REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ==="
# В манифест добавляем разрешение
if ! grep -q "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" app/src/main/AndroidManifest.xml; then
    sed -i 's|<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>|<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>\n    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>|' app/src/main/AndroidManifest.xml
fi

echo "=== 2. ПЕРЕЗАПИСЫВАЕМ VoiceService.kt (с авто-перезапуском) ==="
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
    private var restartCount = 0

    override fun onCreate() {
        super.onCreate()
        PrefsManager.init(this)
        wakeWord = PrefsManager.assistantName.lowercase()
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "JarvisChannel")
            .setContentTitle("Jarvis активен")
            .setContentText("Скажите '$wakeWord' для команды")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        initVosk()
    }

    private fun initVosk() {
        StorageService.unpack(this, "model-ru", "model",
            { model -> 
                this.model = model
                startVoskWakeWord()
            },
            { exception -> 
                exception.printStackTrace()
                // Если не удалось загрузить модель, пытаемся снова через 5 секунд
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initVosk()
                }, 5000)
            }
        )
    }

    private fun startVoskWakeWord() {
        if (isListeningCommand) return
        try {
            speechService?.shutdown()
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            restartCount = 0
        } catch (e: IOException) { 
            e.printStackTrace()
            restartCount++
            if (restartCount < 5) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startVoskWakeWord()
                }, 2000)
            }
        }
    }

    override fun onResult(hypothesis: String) {
        if (hypothesis.contains("\"$wakeWord\"")) { 
            speechService?.stop()
            listenForCommand() 
        }
    }

    private fun listenForCommand() {
        if (isListeningCommand) return
        isListeningCommand = true
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    if (text.isNotEmpty()) {
                        sendBroadcast(Intent("WAKE_WORD_DETECTED").putExtra("command", text))
                    }
                    isListeningCommand = false
                    startVoskWakeWord()
                }
                override fun onError(error: Int) {
                    isListeningCommand = false
                    startVoskWakeWord()
                }
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
    override fun onError(exception: Exception?) { 
        restartCount++
        if (restartCount < 5) {
            startVoskWakeWord()
        }
    }
    override fun onTimeout() { startVoskWakeWord() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("JarvisChannel", "Jarvis Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
INNER

echo "✅ VoiceService обновлён: добавлен авто-перезапуск, проверка батареи, уведомление с именем."

echo "=== 3. ДОБАВЛЯЕМ ЗАПРОС НА ОТКЛЮЧЕНИЕ ЭКОНОМИИ БАТАРЕИ ==="
python3 - << 'PYEOF'
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Проверяем, запрашиваем ли мы разрешение на батарею
if 'ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS' not in content:
    content = content.replace(
        'requestAllPermissions()',
        '''requestAllPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }'''
    )
    with open(filepath, 'w') as f:
        f.write(content)
    print("✅ Запрос на отключение экономии батареи добавлен в onCreate.")
else:
    print("✅ Запрос уже есть.")
PYEOF
