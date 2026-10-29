package com.jarvis.app

import android.content.Context
import android.media.MediaPlayer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeuralTTS(private val context: Context, private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private var mediaPlayer: MediaPlayer? = null

    fun speak(text: String, voice: String = "nova", onStart: () -> Unit = {}) {
        stop()
        Thread {
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
                    if (response.isSuccessful) {
                        val audioBytes = response.body?.bytes()
                        if (audioBytes != null) {
                            val tempFile = File(context.cacheDir, "tts_voice.mp3")
                            FileOutputStream(tempFile).use { it.write(audioBytes) }
                            
                            android.os.Handler(context.mainLooper).post {
                                mediaPlayer = MediaPlayer()
                                mediaPlayer?.setDataSource(tempFile.absolutePath)
                                mediaPlayer?.prepare()
                                mediaPlayer?.start()
                                onStart() // Запускаем печать текста, когда пошел звук
                            }
                        }
                    } else {
                        android.os.Handler(context.mainLooper).post { onStart() }
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).post { onStart() }
            }
        }.start()
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }
}
