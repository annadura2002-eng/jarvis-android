package com.jarvis.app

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import org.vosk.android.RecognitionListener as VoskListener
import org.vosk.android.SpeechService
import java.io.IOException

class VoiceService : Service(), VoskListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeWord = "коля"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "JarvisChannel")
            .setContentTitle("Jarvis активен")
            .setContentText("Скажите кодовое слово...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1, notification)

        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        wakeWord = prefs.getString("assistant_name", "коля")?.lowercase() ?: "коля"

        initVosk()
    }

    private fun initVosk() {
        StorageService.unpack(this, "model-ru", "model",
            { model ->
                this.model = model
                startListening()
            },
            { exception -> exception.printStackTrace() }
        )
    }

    private fun startListening() {
        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) { e.printStackTrace() }
    }

    override fun onResult(hypothesis: String) {
        // hypothesis - это JSON от Vosk. Ищем там слово.
        if (hypothesis.contains(wakeWord)) {
            speechService?.stop()
            listenForCommand()
        }
    }

    private fun listenForCommand() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotEmpty()) {
                        val intent = Intent("WAKE_WORD_DETECTED")
                        intent.putExtra("command", text)
                        sendBroadcast(intent)
                    }
                    restartVosk()
                }
                override fun onError(error: Int) { restartVosk() }
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

    private fun restartVosk() {
        speechRecognizer?.cancel()
        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: IOException) { e.printStackTrace() }
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { restartVosk() }
    override fun onTimeout() { restartVosk() }

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
