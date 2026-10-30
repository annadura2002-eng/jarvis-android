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
