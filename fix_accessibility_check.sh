#!/bin/bash
set -e

echo "=== 1. ПЕРЕЗАПИСЫВАЕМ PermissionsActivity.kt (правильная проверка Accessibility) ==="
cat << 'INNER' > app/src/main/java/com/jarvis/app/PermissionsActivity.kt
package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var btnMic: Button
    private lateinit var btnContacts: Button
    private lateinit var btnNotifs: Button
    private lateinit var btnAccess: Button
    private lateinit var btnContinue: Button

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        window.statusBarColor = android.graphics.Color.parseColor("#0B0B0E")

        btnMic = findViewById(R.id.btnMic)
        btnContacts = findViewById(R.id.btnContacts)
        btnNotifs = findViewById(R.id.btnNotifs)
        btnAccess = findViewById(R.id.btnAccess)
        btnContinue = findViewById(R.id.btnContinue)

        btnMic.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        btnContacts.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
        btnNotifs.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnContinue.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasNotifs = isNotificationServiceEnabled()
        val hasAccess = isAccessibilityServiceEnabled()

        if (hasMic) btnMic.text = "Выдано ✓" else btnMic.text = "Выдать"
        if (hasContacts) btnContacts.text = "Выдано ✓" else btnContacts.text = "Выдать"
        if (hasNotifs) btnNotifs.text = "Выдано ✓" else btnNotifs.text = "Выдать"
        if (hasAccess) btnAccess.text = "Выдано ✓" else btnAccess.text = "Выдать"

        // Достаточно микрофона, контактов и уведомлений для продолжения
        if (hasMic && hasContacts && hasNotifs) {
            btnContinue.isEnabled = true
            btnContinue.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#5B5BFF"))
        } else {
            btnContinue.isEnabled = false
            btnContinue.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2A2A35"))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // Проверяем, есть ли наш пакет + имя класса в глобальных настройках доступности
        val service = "$packageName/${JarvisAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled != null && enabled.contains(service)
    }
}
INNER

echo "=== 2. ПРОВЕРЯЕМ МАНИФЕСТ (метаданные для Accessibility) ==="
# Убедимся, что служба прописана с meta-data
if grep -q "JarvisAccessibilityService" app/src/main/AndroidManifest.xml; then
    echo "✅ JarvisAccessibilityService найден в манифесте."
else
    echo "❌ JarvisAccessibilityService НЕ НАЙДЕН в манифесте! Добавляем..."
    # Вставляем службу перед </application> (если её нет)
    sed -i 's|</application>|    <service android:name=".services.JarvisAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true">\n        <intent-filter>\n            <action android:name="android.accessibilityservice.AccessibilityService"/>\n        </intent-filter>\n        <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service_config"/>\n    </service>\n</application>|' app/src/main/AndroidManifest.xml
fi

echo "=== 3. ПРОВЕРЯЕМ ФАЙЛ accessibility_service_config.xml ==="
if [ -f "app/src/main/res/xml/accessibility_service_config.xml" ]; then
    echo "✅ Файл accessibility_service_config.xml найден."
else
    echo "❌ Файл не найден! Создаём..."
    mkdir -p app/src/main/res/xml
    cat << 'XML' > app/src/main/res/xml/accessibility_service_config.xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_desc"
    android:notificationTimeout="100" />
XML
    echo "✅ Создан файл accessibility_service_config.xml."
fi

echo "=== 4. ПРОВЕРЯЕМ СТРОКУ accessibility_desc В strings.xml ==="
if grep -q "accessibility_desc" app/src/main/res/values/strings.xml 2>/dev/null; then
    echo "✅ Строка accessibility_desc найдена."
else
    echo "❌ Строки нет! Добавляем..."
    if [ -f "app/src/main/res/values/strings.xml" ]; then
        sed -i 's|</resources>|    <string name="accessibility_desc">Jarvis использует эту службу для управления экраном (нажатия, чтение текста).</string>\n</resources>|' app/src/main/res/values/strings.xml
    fi
fi

echo "✅ Готово! Теперь приложение должно видеть включённую службу доступности."
