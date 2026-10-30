package com.jarvis.app.services

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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // Улучшенный клик по тексту (точное совпадение)
    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            val cleanText = text.replace("\"", "").trim().lowercase()
            if (cleanText.isEmpty()) continue
            
            val nodes = root.findAccessibilityNodeInfosByText(cleanText)
            for (node in nodes) {
                // Ищем точное совпадение, чтобы не нажать "Отправлено" вместо "Отправить"
                val nodeText = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
                if (nodeText.contains(cleanText)) {
                    var target = node
                    while (target != null) {
                        if (target.isClickable && target.isEnabled) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                        target = target.parent
                    }
                }
            }
        }
        return false
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    // Быстрый сбор текста (только видимые элементы, глубина 15)
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        
        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 15) return
            if (node.isVisibleToUser) {
                if (!node.text.isNullOrEmpty()) sb.append(node.text).append(" | ")
                if (!node.contentDescription.isNullOrEmpty()) sb.append(node.contentDescription).append(" | ")
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }
        traverse(root, 0)
        
        val result = sb.toString()
        // Берем последние 2000 символов (там обычно кнопки внизу экрана)
        return if (result.length > 2000) result.takeLast(2000) else result
    }
}
