package com.jarvis.app.services

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
import android.app.RemoteInput
import android.util.Log
import com.jarvis.app.core.PrefsManager

// --- СЕРВИС ГЕОЛОКАЦИИ (Энергоэффективный) ---
class GeoService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Балансный режим (жрёт меньше батареи)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300000).build() // Раз в 5 минут
        
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
        
        if (results[0] < 200f) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (!wifiManager.isWifiEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                Log.d("JarvisGeo", "Пришел домой! Включил Wi-Fi")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// --- СЕРВИС АВТО-ОТВЕТЧИКА (Реальный ответ через уведомления) ---
class SmartNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        PrefsManager.init(this)
        if (!PrefsManager.autoReplyEnabled) return

        val pkg = sbn.packageName
        // Поддерживаемые приложения
        if (pkg != "org.telegram.messenger" && pkg != "com.whatsapp") return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "Неизвестный"
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Ищем кнопку "Ответить" (RemoteInput)
        val actions = sbn.notification.actions ?: return
        for (action in actions) {
            if (action.remoteInputs != null) {
                // Приложение поддерживает быстрый ответ
                val remoteInput = action.remoteInputs.firstOrNull { it.allowFreeFormInput }
                if (remoteInput != null) {
                    val intent = Intent()
                    val bundle = android.os.Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, PrefsManager.autoReplyText)
                    RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
                    try {
                        action.actionIntent.send(this, 0, intent)
                        Log.d("JarvisAutoReply", "Ответил на сообщение от $title")
                    } catch (e: Exception) {
                        Log.e("JarvisAutoReply", "Ошибка отправки: ${e.message}")
                    }
                }
            }
        }
    }
}
