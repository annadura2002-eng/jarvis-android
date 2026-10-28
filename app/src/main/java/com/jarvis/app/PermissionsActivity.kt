package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var btnMic: Button
    private lateinit var btnContacts: Button
    private lateinit var btnPhoneSms: Button
    private lateinit var btnNotifs: Button
    private lateinit var btnAccess: Button
    private lateinit var btnContinue: Button

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checkStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        btnMic = findViewById(R.id.btnMic)
        btnContacts = findViewById(R.id.btnContacts)
        btnPhoneSms = findViewById(R.id.btnPhoneSms)
        btnNotifs = findViewById(R.id.btnNotifs)
        btnAccess = findViewById(R.id.btnAccess)
        btnContinue = findViewById(R.id.btnContinue)

        btnMic.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        btnContacts.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
        btnPhoneSms.setOnClickListener {
            requestPerms.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE))
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
        val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        val hasNotifs = isNotificationServiceEnabled()
        val hasAccess = isAccessibilityServiceEnabled()

        // Меняем текст кнопок на "Выдано"
        if (hasMic) btnMic.text = "Выдано ✓" else btnMic.text = "Выдать"
        if (hasContacts) btnContacts.text = "Выдано ✓" else btnContacts.text = "Выдать"
        if (hasPhone && hasSms) btnPhoneSms.text = "Выдано ✓" else btnPhoneSms.text = "Выдать"
        if (hasNotifs) btnNotifs.text = "Выдано ✓" else btnNotifs.text = "Выдать"
        if (hasAccess) btnAccess.text = "Выдано ✓" else btnAccess.text = "Выдать"

        // Кнопка продолжить активна, если выдано всё (кроме Accessibility - он опционален)
        if (hasMic && hasContacts && hasPhone && hasSms && hasNotifs) {
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
        val service = "$packageName/${JarvisAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled != null && enabled.contains(service)
    }
}
