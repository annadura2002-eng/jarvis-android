package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // Функция для клика по кнопке по тексту (например, "Connect" или "Подключить")
    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    parent = parent.parent
                }
            }
        }
        return false
    }

    // Функция для свайпа (например, листать видео)
    fun swipeUp() {
        val path = Path()
        val displayMetrics = resources.displayMetrics
        val x = displayMetrics.widthPixels / 2f
        val yStart = displayMetrics.heightPixels * 0.8f
        val yEnd = displayMetrics.heightPixels * 0.2f

        path.moveTo(x, yStart)
        path.lineTo(x, yEnd)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
        dispatchGesture(gestureBuilder.build(), null, null)
    }
}
