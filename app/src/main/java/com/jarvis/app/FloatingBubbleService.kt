package com.jarvis.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var chatView: View? = null
    private var tvAnswer: TextView? = null
    private var isChatOpen = false

    private val answerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val answer = intent?.getStringExtra("answer") ?: ""
            if (answer.isNotEmpty()) {
                tvAnswer?.text = answer
                tvAnswer?.visibility = View.VISIBLE
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

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            if (isChatOpen) closeChat() else showMiniChat()
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

        btnSend.setOnClickListener {
            val question = etInput.text.toString()
            if (question.isNotEmpty()) {
                etInput.setText("")
                tvAnswer?.visibility = View.VISIBLE
                tvAnswer?.text = "Думаю..."
                val intent = Intent("MINI_CHAT_COMMAND")
                intent.putExtra("command", question as String)
                sendBroadcast(intent)
            }
        }

        // ВАЖНЫЕ ФЛАГИ: FLAG_NOT_TOUCH_MODAL (чтобы фон был кликабельным) 
        // и FLAG_ALT_FOCUSABLE_IM (чтобы ввод работал)
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

        etInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeChat() {
        chatView?.let { windowManager.removeView(it) }
        chatView = null
        isChatOpen = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(answerReceiver) } catch (e: Exception) {}
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        closeChat()
    }
}
