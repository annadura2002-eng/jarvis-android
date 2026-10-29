package com.jarvis.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private var chatView: View? = null
    private var isChatOpen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
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
                            if (isChatOpen) {
                                chatView?.let { windowManager.removeView(it) }
                                chatView = null
                                isChatOpen = false
                            } else {
                                showMiniChat()
                            }
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

        val chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F21C1C1C"))
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "Jarvis AI"
            setTextColor(Color.parseColor("#2DD4BF"))
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        
        val input = EditText(this).apply {
            hint = "Задайте вопрос..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#262626"))
            setPadding(24, 24, 24, 24)
        }
        
        val btnSend = Button(this).apply {
            text = "Отправить"
            setBackgroundColor(Color.parseColor("#2DD4BF"))
            setOnClickListener {
                val question = input.text.toString()
                if (question.isNotEmpty()) {
                    input.setText("")
                    val intent = Intent("MINI_CHAT_COMMAND")
                    intent.putExtra("command", question)
                    sendBroadcast(intent)
                }
            }
        }

        chatLayout.addView(title)
        chatLayout.addView(input)
        chatLayout.addView(btnSend)
        
        val paramsChat = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        paramsChat.gravity = Gravity.BOTTOM
        paramsChat.y = 150
        
        chatView = chatLayout
        windowManager.addView(chatView, paramsChat)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        chatView?.let { windowManager.removeView(it) }
    }
}
