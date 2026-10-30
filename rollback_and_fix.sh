#!/bin/bash

echo "=== 1. Откатываем сломанные файлы ==="
git checkout -- app/src/main/java/com/jarvis/app/MainActivity.kt
git checkout -- app/src/main/java/com/jarvis/app/PermissionsActivity.kt
git checkout -- app/src/main/java/com/jarvis/app/services/SmartServices.kt
git checkout -- app/src/main/java/com/jarvis/app/core/CommandProcessor.kt
git checkout -- app/src/main/java/com/jarvis/app/services/FloatingBubbleService.kt

echo "=== 2. Пересоздаем папки ==="
mkdir -p app/src/main/java/com/jarvis/app/core
mkdir -p app/src/main/java/com/jarvis/app/services

echo "=== 3. Записываем CommandProcessor.kt ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/core/CommandProcessor.kt
package com.jarvis.app.core

import com.jarvis.app.MainActivity

object CommandProcessor {
    fun process(context: MainActivity, raw: String): Boolean {
        val cmd = raw.lowercase().trim()
        
        // 1. Будильники
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

        // 2. Список будильников
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

        // 3. Автоответ
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
        
        // 4. Перевод
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

echo "=== 4. Записываем FloatingBubbleService.kt (фикс Bundle) ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/services/FloatingBubbleService.kt
package com.jarvis.app.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.jarvis.app.R
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var chatView: View? = null
    private var tvAnswer: TextView? = null
    private var isChatOpen = false
    private var lastAnswer = ""
    private var speechRecognizer: SpeechRecognizer? = null

    private val answerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val answer = intent?.getStringExtra("answer") ?: ""
            if (answer.isNotEmpty()) {
                lastAnswer = answer
                if (isChatOpen) {
                    tvAnswer?.text = answer
                    tvAnswer?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(answerReceiver, IntentFilter("BUBBLE_ANSWER"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(answerReceiver, IntentFilter("BUBBLE_ANSWER"))
        }

        val bubble = ImageView(this)
        bubble.setImageResource(android.R.drawable.ic_btn_speak_now)
        bubble.setColorFilter(Color.parseColor("#2DD4BF"))
        bubble.setBackgroundResource(R.drawable.bg_button_primary)
        bubble.setPadding(30, 30, 30, 30)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0; params.y = 200

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        isDragging = false; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - initialTouchX) > 10 || abs(event.rawY - initialTouchY) > 10) {
                            isDragging = true
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) { if (isChatOpen) closeChat() else showMiniChat() } 
                        else {
                            val screenWidth = resources.displayMetrics.widthPixels
                            val bubbleWidth = 150
                            params.x = if (event.rawX > screenWidth / 2) screenWidth - bubbleWidth else 0
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                }
                return false
            }
        })
        floatingView = bubble
        windowManager.addView(floatingView, params)
    }

    private fun showMiniChat() {
        if (isChatOpen) return
        isChatOpen = true
        val chatLayout = LayoutInflater.from(this).inflate(R.layout.dialog_bubble_chat, null)
        tvAnswer = chatLayout.findViewById(R.id.tvBubbleAnswer)
        val etInput = chatLayout.findViewById<EditText>(R.id.etBubbleInput)
        val btnSend = chatLayout.findViewById<Button>(R.id.btnBubbleSend)

        if (lastAnswer.isNotEmpty()) {
            tvAnswer?.text = lastAnswer; tvAnswer?.visibility = View.VISIBLE
        } else { tvAnswer?.visibility = View.GONE }

        btnSend?.setOnClickListener {
            val question = etInput?.text.toString()
            if (question.isNotEmpty()) {
                etInput?.setText("")
                tvAnswer?.visibility = View.VISIBLE; tvAnswer?.text = "Думаю..."
                sendBroadcast(Intent("MINI_CHAT_COMMAND").putExtra("command", question))
            }
        }

        val paramsChat = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, PixelFormat.TRANSLUCENT
        )
        paramsChat.gravity = Gravity.BOTTOM; paramsChat.y = 150
        paramsChat.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        
        chatView = chatLayout
        windowManager.addView(chatView, paramsChat)

        chatLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            chatLayout.getWindowVisibleDisplayFrame(rect)
            val screenHeight = resources.displayMetrics.heightPixels
            val keypadHeight = screenHeight - rect.bottom
            paramsChat.y = if (keypadHeight > screenHeight * 0.15) keypadHeight + 150 else 150
            windowManager.updateViewLayout(chatView, paramsChat)
        }
        etInput?.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeChat() {
        chatView?.let { windowManager.removeView(it) }
        chatView = null; isChatOpen = false
        speechRecognizer?.destroy(); speechRecognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(answerReceiver) } catch (e: Exception) {}
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        closeChat()
    }
}
INNER

echo "=== 5. Патчим MainActivity.kt (через Python без дублей) ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Убираем дубли PrefsManager
content = re.sub(r'\n\s*com\.jarvis\.app\.core\.PrefsManager\.init\(this\)', '', content)
# Вставляем один раз в onCreate
content = content.replace(
    'override fun onCreate(savedInstanceState: Bundle?) {',
    'override fun onCreate(savedInstanceState: Bundle?) {\n        com.jarvis.app.core.PrefsManager.init(this)'
)

# Добавляем мосты в конец класса (до последней скобки)
if 'fun createAlarmFromCommand' not in content:
    bridges = '''
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) { setJarvisAlarm(hour, minute) }
    fun sendReplyFromCommand(text: String) { reply(text) }
    
    fun startTranslationMode(fromLang: String, toLang: String) {
        reply("Режим перевода ($fromLang -> $toLang) включен. Скажите 'стоп' чтобы выйти.")
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.lowercase().contains("стоп") || text.lowercase().contains("stop")) {
                    sr.destroy(); reply("Режим перевода выключен."); return
                }
                if (text.isNotEmpty()) {
                    executor.execute {
                        try {
                            val json = JSONObject().apply {
                                put("model", "gpt-4o-mini")
                                put("messages", JSONArray().apply {
                                    put(JSONObject().apply { put("role", "system"); put("content", "Ты переводчик. Переведи текст с $fromLang на $toLang. Отвечай ТОЛЬКО переводом.") })
                                    put(JSONObject().apply { put("role", "user"); put("content", text) })
                                })
                            }
                            val body = json.toString().toRequestBody("application/json".toMediaType())
                            val req = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                            client.newCall(req).execute().use { res ->
                                if (res.isSuccessful) {
                                    val ans = JSONObject(res.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                                    runOnUiThread { addBubble("Оригинал: $text", true); animateBotReply(ans) }
                                }
                            }
                        } catch (e: Exception) {}
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
'''
    content = content.rstrip() + '\n' + bridges + '\n}\n'

with open(filepath, 'w') as f:
    f.write(content)
print("MainActivity.kt успешно пропатчен.")
PYEOF

echo "✅ Все файлы восстановлены и исправлены. Делай git push."
