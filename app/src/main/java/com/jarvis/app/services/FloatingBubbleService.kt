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
        
        val btnMic = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#2DD4BF"))
            layoutParams = LinearLayout.LayoutParams(100, 100)
        }
        val parent = etInput?.parent as? LinearLayout
        if (parent != null && parent.findViewWithTag<ImageButton>("btnMic") == null) {
            btnMic.tag = "btnMic"
            parent.addView(btnMic)
        }

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
        
        btnMic.setOnClickListener {
            startVoiceInputForBubble(etInput, tvAnswer)
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
                    sendBroadcast(Intent("MINI_CHAT_COMMAND").putExtra("command", text))
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
