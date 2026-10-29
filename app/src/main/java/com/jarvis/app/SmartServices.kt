package com.jarvis.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

// --- СЕРВИС ГЕОЛОКАЦИИ ---
class GeoService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000).build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                checkGeofences(loc)
            }
        }
        try { fusedLocationClient.requestLocationUpdates(request, locationCallback, null) } catch (e: SecurityException) {}
    }

    private fun checkGeofences(loc: Location) {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val homeLat = prefs.getString("home_lat", "")?.toDoubleOrNull() ?: return
        val homeLon = prefs.getString("home_lon", "")?.toDoubleOrNull() ?: return

        val results = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, homeLat, homeLon, results)
        
        // Если ближе 200 метров — включаем Wi-Fi
        if (results[0] < 200f) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Log.d("JarvisGeo", "Пришел домой! Включил Wi-Fi")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// --- СЕРВИС АВТО-ОТВЕТЧИКА (ЧИТАЕТ УВЕДОМЛЕНИЯ) ---
class SmartNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val autoReplyEnabled = prefs.getBoolean("auto_reply", false)
        
        if (!autoReplyEnabled) return

        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Если пришло сообщение от друга (Telegram, WhatsApp)
        if (pkg == "org.telegram.messenger" || pkg == "com.whatsapp") {
            // Логика отправки ответа через Accessibility
            val access = JarvisAccessibilityService.instance
            if (access != null) {
                // Простой пример: нажимаем на уведомление, вводим текст и отправляем
                // В реальности нужно открыть чат и ввести текст, но для примера покажем уведомление
                Log.d("JarvisAutoReply", "Пришло сообщение от $title: $text")
            }
        }
    }
}
