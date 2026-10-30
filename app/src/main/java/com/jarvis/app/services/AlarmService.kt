package com.jarvis.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.Calendar

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") { stopAlarm(); return START_NOT_STICKY }

        val hour = intent?.getIntExtra("hour", 7) ?: 7
        val minute = intent?.getIntExtra("minute", 0) ?: 0
        val daily = intent?.getBooleanExtra("daily", false) ?: false
        val alarmId = intent?.getIntExtra("id", 0) ?: 0

        if (daily) scheduleNextDaily(hour, minute, alarmId)

        createNotificationChannel()
        val stopIntent = Intent(this, AlarmService::class.java).apply { action = "STOP_ALARM" }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fullScreenIntent = Intent(this, com.jarvis.app.MainActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 3, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "AlarmChannel")
            .setContentTitle("Jarvis Будильник").setContentText("Проснись! Нажми, чтобы остановить.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPendingIntent).setOngoing(true).build()

        startForeground(3, notification)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis:AlarmWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        playAlarmSound()
        startVibration()

        return START_STICKY
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = true; prepare(); start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        else @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0)
    }

    private fun stopAlarm() {
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        vibrator?.cancel(); wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun scheduleNextDaily(hour: Int, minute: Int, id: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmService::class.java).apply {
            putExtra("hour", hour); putExtra("minute", minute); putExtra("daily", true); putExtra("id", id)
        }
        val pendingIntent = PendingIntent.getService(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("AlarmChannel", "Jarvis Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() { stopAlarm(); super.onDestroy() }
}
