#!/bin/bash
set -e

echo "=== ПЕРЕЗАПИСЫВАЕМ TranslationManager.kt (с циклом) ==="
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
                    sr.destroy()
                    context.sendReplyFromCommand("Режим перевода выключен.")
                    return
                }
                
                if (text.isNotEmpty()) {
                    translate(context, text, fromLang, toLang) { translated ->
                        context.runOnUiThread {
                            context.addBubblePublic("Оригинал: $text", true)
                            context.animateBotReplyPublic(translated)
                        }
                    }
                }
                
                // Перезапускаем слушку для следующей фразы
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
                sr.startListening(intent)
            }
            
            override fun onError(error: Int) {
                // При ошибке тоже перезапускаем
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
                sr.startListening(intent)
            }
            
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        sr.startListening(intent)
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
                val req = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey").post(body).build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val ans = JSONObject(res.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        callback(ans)
                    } else {
                        callback("Ошибка перевода: ${res.code}")
                    }
                }
            } catch (e: Exception) { 
                callback("Ошибка сети") 
            }
        }
    }
}
INNER

echo "✅ TranslationManager обновлён: теперь цикл (распознал -> перевёл -> озвучил -> слушает снова)."
