package com.jarvis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class AuroraBackground @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply { isAntiAlias = true }
    private var time = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Темный фон
        canvas.drawColor(Color.parseColor("#0F1115"))

        // Плавное движение пятен
        time += 0.005f
        val offsetX = sin(time) * 150f
        val offsetY = sin(time * 0.8f) * 100f

        // Мятное пятно (сверху)
        paint.shader = RadialGradient(
            w * 0.3f + offsetX, h * 0.2f + offsetY, w * 0.7f,
            Color.parseColor("#402DD4BF"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Темно-зеленое пятно (снизу)
        paint.shader = RadialGradient(
            w * 0.8f - offsetX, h * 0.8f - offsetY, w * 0.6f,
            Color.parseColor("#300F766E"), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Перерисовка для плавной анимации (60 fps)
        postInvalidateOnAnimation()
    }
}
