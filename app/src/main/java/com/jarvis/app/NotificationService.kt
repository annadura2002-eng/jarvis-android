package com.jarvis.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {
    companion object {
        var latestNotifications: List<String> = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isNotEmpty()) {
            val newNotif = "От $title: $text"
            latestNotifications = (listOf(newNotif) + latestNotifications).take(5)
        }
    }
}
