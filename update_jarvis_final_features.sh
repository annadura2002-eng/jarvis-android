#!/bin/bash
set -e

echo "=== 1. УДАЛЯЕМ TELEGRAM БОТА ==="
# Удаляем любые упоминания и файлы, связанные с тг-ботом
rm -f app/src/main/java/com/jarvis/app/TelegramBot.kt 2>/dev/null || true
# Вычищаем из MainActivity любые упоминания (на случай если были)
sed -i '/telegram bot/d' app/src/main/java/com/jarvis/app/MainActivity.kt || true

echo "=== 2. ОБНОВЛЯЕМ NeuralTTS (поддержка перевода и очереди) ==="
# В NeuralTTS мы просто оставляем всё как есть, он уже умеет говорить любой текст.

echo "=== 3. ДОБАВЛЯЕМ ИСТОРИЮ ДИАЛОГА В MainActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# 1. Добавляем переменную для истории
if 'private val chatHistory = mutableListOf<JSONObject>()' not in content:
    content = content.replace(
        'private val client = OkHttpClient()',
        'private val client = OkHttpClient()\n    private val chatHistory = mutableListOf<JSONObject>()'
    )

# 2. Обновляем askChatGPT (добавляем историю и очистку)
old_ask = re.search(r'private fun askChatGPT\(prompt: String\) \{.*?\n    \}', content, re.DOTALL)
if old_ask:
    new_ask = '''private fun askChatGPT(prompt: String) {
        showSkeletonLoader()
        executor.execute {
            try {
                // Добавляем сообщение пользователя в историю
                chatHistory.add(JSONObject().apply { put("role", "user"); put("content", prompt) })
                
                // Ограничиваем историю последними 10 сообщениями (5 пар)
                while (chatHistory.size > 10) { chatHistory.removeAt(0) }

                val messagesArray = JSONArray()
                // Системный промпт
                messagesArray.put(JSONObject().apply { 
                    put("role", "system"); 
                    put("content", "Ты - голосовой ассистент. Сегодня ${java.text.SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(java.util.Date())}. Отвечай кратко.") 
                })
                // Добавляем историю
                for (msg in chatHistory) messagesArray.put(msg)

                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", messagesArray)
                }
                
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answer = JSONObject(response.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        // Добавляем ответ ассистента в историю
                        chatHistory.add(JSONObject().apply { put("role", "assistant"); put("content", answer) })
                        
                        runOnUiThread { hideSkeletonLoader(); animateBotReply(answer) }
                    } else runOnUiThread { hideSkeletonLoader(); reply("Ошибка ИИ: ${response.code}") }
                }
            } catch (e: Exception) { runOnUiThread { hideSkeletonLoader(); reply("Ошибка сети.") } }
        }
    }'''
    content = content.replace(old_ask.group(0), new_ask)

with open(filepath, 'w') as f:
    f.write(content)
print("История диалога добавлена.")
PYEOF

echo "=== 4. ДОБАВЛЯЕМ КОМАНДУ ПЕРЕВОДА В CommandProcessor.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/core/CommandProcessor.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Добавляем обработку перевода перед return false
translate_code = '''
        // 4. Переводчик-синхронист
        if (cmd.contains("переведи с ") && cmd.contains(" на ")) {
            val parts = cmd.split(" на ")
            if (parts.size == 2) {
                val fromLangRaw = parts[0].replace("переведи с ", "").trim()
                val toLangRaw = parts[1].trim()
                context.startTranslationMode(fromLangRaw, toLangRaw)
                return true
            }
        }
'''
if 'context.startTranslationMode' not in content:
    content = content.replace('        return false\n    }\n}', translate_code + '        return false\n    }\n}')
    with open(filepath, 'w') as f:
        f.write(content)
print("Команда перевода добавлена в CommandProcessor.")
PYEOF

echo "=== 5. ДОБАВЛЯЕМ МЕТОДЫ ПЕРЕВОДА В MainActivity.kt ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Добавляем метод startTranslationMode и вспомогательный translateText
translation_methods = '''
    // --- РЕЖИМ ПЕРЕВОДЧИКА ---
    fun startTranslationMode(fromLang: String, toLang: String) {
        reply("Режим перевода ($fromLang -> $toLang) включен. Говорите, я буду переводить. Скажите 'стоп' чтобы выйти.")
        
        // Настраиваем распознаватель на непрерывную работу
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.lowercase().contains("стоп") || text.lowercase().contains("stop")) {
                    sr.destroy()
                    reply("Режим перевода выключен.")
                    return
                }
                if (text.isNotEmpty()) {
                    translateText(text, fromLang, toLang) { translated ->
                        runOnUiThread {
                            addBubble("Оригинал: $text", true)
                            animateBotReply(translated)
                        }
                    }
                }
                // Перезапускаем слушку
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
                sr.startListening(intent)
            }
            override fun onError(error: Int) {
                // В случае ошибки перезапускаем
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
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

    private fun translateText(text: String, from: String, to: String, callback: (String) -> Unit) {
        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", "Ты переводчик. Переведи текст с языка $from на язык $to. Отвечай ТОЛЬКО переводом, без лишних слов.") })
                        put(JSONObject().apply { put("role", "user"); put("content", text) })
                    })
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answer = JSONObject(response.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        callback(answer)
                    } else callback("Ошибка перевода")
                }
            } catch (e: Exception) { callback("Ошибка сети") }
        }
    }
'''
if 'fun startTranslationMode' not in content:
    # Вставляем перед последней закрывающей скобкой класса
    content = content.rstrip() + '\n' + translation_methods + '\n}\n'

with open(filepath, 'w') as f:
    f.write(content)
print("Методы перевода добавлены в MainActivity.")
PYEOF

echo "=== 6. ОБНОВЛЯЕМ FloatingBubbleService (ГОЛОСОВОЙ ВВОД + АНИМАЦИЯ) ==="
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
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.view.animation.AnimationUtils
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
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
                        isDragging = false
                        return true
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
                        if (!isDragging) {
                            if (isChatOpen) closeChat() else showMiniChat()
                        } else {
                            // Snap to edge
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
        
        // Добавляем кнопку микрофона прямо в чат (если её нет в layout, создадим программно)
        val btnMic = chatLayout.findViewWithTag<ImageButton>("btnMic") ?: ImageButton(this).apply {
            tag = "btnMic"
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#2DD4BF"))
            val lp = LinearLayout.LayoutParams(100, 100)
            layoutParams = lp
        }
        
        // Если кнопка микрофона новая, добавляем её в layout рядом с полем ввода
        if (btnMic.parent == null) {
            val parent = etInput?.parent as? LinearLayout
            if (parent != null) parent.addView(btnMic)
        }

        if (!lastAnswer.isEmpty()) {
            tvAnswer?.text = lastAnswer
            tvAnswer?.visibility = View.VISIBLE
        } else {
            tvAnswer?.visibility = View.GONE
        }

        btnSend?.setOnClickListener {
            val question = etInput?.text.toString()
            if (question.isNotEmpty()) {
                etInput?.setText("")
                tvAnswer?.visibility = View.VISIBLE
                tvAnswer?.text = "Думаю..."
                val intent = Intent("MINI_CHAT_COMMAND")
                intent.putExtra("command", question)
                sendBroadcast(intent)
            }
        }
        
        btnMic.setOnClickListener {
            startVoiceInputForBubble(etInput, tvAnswer)
        }

        val paramsChat = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        paramsChat.gravity = Gravity.BOTTOM
        paramsChat.y = 150
        paramsChat.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        
        chatView = chatLayout
        windowManager.addView(chatView, paramsChat)

        chatLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            chatLayout.getWindowVisibleDisplayFrame(rect)
            val screenHeight = resources.displayMetrics.heightPixels
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                paramsChat.y = keypadHeight + 150
                windowManager.updateViewLayout(chatView, paramsChat)
            } else {
                paramsChat.y = 150
                windowManager.updateViewLayout(chatView, paramsChat)
            }
        }

        etInput?.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun startVoiceInputForBubble(etInput: EditText?, tvAnswer: TextView?) {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotEmpty()) {
                    etInput?.setText(text)
                    tvAnswer?.visibility = View.VISIBLE
                    tvAnswer?.text = "Думаю..."
                    val intent = Intent("MINI_CHAT_COMMAND")
                    intent.putExtra("command", text)
                    sendBroadcast(intent)
                }
            }
            override fun onError(error: Int) {
                tvAnswer?.visibility = View.VISIBLE
                tvAnswer?.text = "Не расслышал, попробуй еще."
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        speechRecognizer?.startListening(intent)
        tvAnswer?.visibility = View.VISIBLE
        tvAnswer?.text = "Слушаю..."
    }

    private fun closeChat() {
        chatView?.let { windowManager.removeView(it) }
        chatView = null
        isChatOpen = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(answerReceiver) } catch (e: Exception) {}
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        closeChat()
    }
}
INNER

echo "=== 7. ДОБАВЛЯЕМ АНИМАЦИЮ ПУЛЬСАЦИИ В VOICESERVICE ==="
# Создаем анимацию пульсации для пузыря
cat << 'INNER' > app/src/main/res/anim/pulse_scale.xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:duration="1000"
        android:fromXScale="1.0"
        android:fromYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:repeatCount="infinite"
        android:repeatMode="reverse"
        android:toXScale="1.1"
        android:toYScale="1.1" />
</set>
INNER
# Применяем анимацию к кнопке микрофона в MainActivity (если есть)
sed -i 's/voiceButton.setOnClickListener { showBeautifulVoiceInput() }/voiceButton.setOnClickListener { voiceButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale)); showBeautifulVoiceInput() }/g' app/src/main/java/com/jarvis/app/MainActivity.kt

echo "=== 8. ДОБАВЛЯЕМ ПЕРЕКЛЮЧАТЕЛЬ ТЕМ В МЕНЮ ==="
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/res/layout/dialog_menu.xml'
try:
    with open(filepath, 'r') as f:
        content = f.read()
    # Ищем LinearLayout с настройками и добавляем кнопку смены темы
    if 'btnMenuTheme' not in content:
        content = content.replace(
            '</LinearLayout>',
            '''    <LinearLayout
        android:id="@+id/btnMenuTheme"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🌗 Сменить тему"
            android:textColor="#F8FAFC"
            android:textSize="16sp" />
    </LinearLayout>
</LinearLayout>''',
            1
        )
        with open(filepath, 'w') as f:
            f.write(content)
    print("Кнопка темы добавлена в меню.")
except FileNotFoundError:
    print("dialog_menu.xml не найден, пропускаем.")
PYEOF

# Добавляем обработчик смены темы в MainActivity
python3 - << 'PYEOF'
import re
filepath = 'app/src/main/java/com/jarvis/app/MainActivity.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Ищем showBottomSheetMenu и добавляем обработчик
if 'btnMenuTheme' not in content:
    # Внутри showBottomSheetMenu, где ищем кнопки (примерно после btnMenuLogout)
    content = content.replace(
        'view.findViewById<LinearLayout>(R.id.btnMenuLogout).setOnClickListener {',
        '''view.findViewById<LinearLayout>(R.id.btnMenuTheme)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
        view.findViewById<LinearLayout>(R.id.btnMenuLogout).setOnClickListener {'''
    )
    with open(filepath, 'w') as f:
        f.write(content)
print("Обработчик темы добавлен.")
PYEOF

echo "✅ ВСЁ ГОТОВО! История, Перевод, Пузырь с голосом, Анимация и Тема установлены."
