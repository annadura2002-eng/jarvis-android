package com.jarvis.app.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PrefsManager {
    private const val PREFS_NAME = "JarvisPrefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var assistantName: String
        get() = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        set(value) = prefs.edit().putString("assistant_name", value).apply()

    var selectedVoice: String
        get() = prefs.getString("neural_voice", "nova") ?: "nova"
        set(value) = prefs.edit().putString("neural_voice", value).apply()

    var autoReplyEnabled: Boolean
        get() = prefs.getBoolean("auto_reply", false)
        set(value) = prefs.edit().putBoolean("auto_reply", value).apply()

    var autoReplyText: String
        get() = prefs.getString("auto_reply_text", "Я сейчас занят, перезвоню позже.") ?: ""
        set(value) = prefs.edit().putString("auto_reply_text", value).apply()

    var bubbleRunning: Boolean
        get() = prefs.getBoolean("bubble_running", false)
        set(value) = prefs.edit().putBoolean("bubble_running", value).apply()

    fun getAlarms(): MutableList<AlarmData> {
        val jsonStr = prefs.getString("alarms_json", "[]") ?: "[]"
        val list = mutableListOf<AlarmData>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(AlarmData(obj.getInt("hour"), obj.getInt("minute"), obj.getBoolean("daily"), obj.getInt("id")))
            }
        } catch (e: Exception) {}
        return list
    }

    fun saveAlarm(alarm: AlarmData) {
        val list = getAlarms()
        list.add(alarm)
        val arr = JSONArray()
        for (a in list) { arr.put(JSONObject().apply { put("hour", a.hour); put("minute", a.minute); put("daily", a.daily); put("id", a.id) }) }
        prefs.edit().putString("alarms_json", arr.toString()).apply()
    }

    fun removeAlarm(id: Int) {
        val list = getAlarms()
        list.removeAll { it.id == id }
        val arr = JSONArray()
        for (a in list) { arr.put(JSONObject().apply { put("hour", a.hour); put("minute", a.minute); put("daily", a.daily); put("id", a.id) }) }
        prefs.edit().putString("alarms_json", arr.toString()).apply()
    }
}

data class AlarmData(val hour: Int, val minute: Int, val daily: Boolean, val id: Int = (System.currentTimeMillis() % 10000).toInt())
