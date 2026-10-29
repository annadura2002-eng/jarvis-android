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

    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) { parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                    parent = parent.parent
                }
            }
        }
        return false
    }

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

    // ЧТЕНИЕ ЭКРАНА
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.text != null) sb.append(node.text).append(" ")
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return sb.toString()
    }
}
