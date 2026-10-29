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

    // Встроенные живые голоса (OpenAI)
    // alloy (нейтральный), echo (мужской), fable (женский), onyx (глубокий мужской), nova (женский), shimmer (женский)
    
    fun speak(text: String, voice: String = "nova") {
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
                            
                            // Воспроизводим скачанный звук
                            android.os.Handler(context.mainLooper).post {
                                val mediaPlayer = MediaPlayer()
                                mediaPlayer.setDataSource(tempFile.absolutePath)
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
