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
