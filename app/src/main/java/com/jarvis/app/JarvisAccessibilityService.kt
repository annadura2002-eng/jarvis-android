package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

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

    // Клик по тексту (кнопке)
    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            // Очищаем текст от кавычек и лишних пробелов, которые может вернуть ИИ
            val cleanText = text.replace("\"", "").trim()
            if (cleanText.isEmpty()) continue
            
            val nodes = root.findAccessibilityNodeInfosByText(cleanText)
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

    // Ввод текста
    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // Свайп вверх
    fun swipeUp() {
        val path = Path()
        val dm = resources.displayMetrics
        path.moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.8f)
        path.lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.2f)
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build(), null, null)
    }

    // ЧТЕНИЕ ВСЕГО ТЕКСТА С ЭКРАНА
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.text != null) sb.append(node.text).append(" | ")
            if (node.contentDescription != null) sb.append(node.contentDescription).append(" | ")
            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        return sb.toString().take(2000) // Ограничиваем длину, чтобы не превысить лимит токенов
    }
}
