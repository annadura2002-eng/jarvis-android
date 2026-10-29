package com.jarvis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class EqualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#8B8BFF")
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    
    private var isAnimating = false
    private var amplitudes = FloatArray(5) { 10f }

    fun startAnimation() {
        isAnimating = true
        invalidate()
    }

    fun stopAnimation() {
        isAnimating = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / (amplitudes.size * 2)

        for (i in amplitudes.indices) {
            if (isAnimating) {
                // Симуляция прыгающих полосок (в реальном приложении можно привязать к Visualizer)
                amplitudes[i] = Random.nextFloat() * height * 0.8f + 10f
            } else {
                amplitudes[i] = 10f // Покой
            }
            
            val x = (i * 2 + 0.5f) * barWidth + barWidth / 2
            val startY = height / 2 - amplitudes[i] / 2
            val endY = height / 2 + amplitudes[i] / 2
            
            canvas.drawLine(x, startY, x, endY, paint)
        }

        if (isAnimating) postInvalidateDelayed(100)
    }
}
