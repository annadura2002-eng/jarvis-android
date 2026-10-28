package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var logView: TextView
    private lateinit var inputField: EditText
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var callStartTime: Long = 0
    private var isCalling: Boolean = false

    private val appsMap = mapOf(
        "телеграм" to "org.telegram.messenger",
        "telegram" to "org.telegram.messenger",
        "ютуб" to "com.google.android.youtube",
        "youtube" to "com.google.android.youtube",
        "вк" to "com.vkontakte.android",
        "vk" to "com.vkontakte.android",
        "тикток" to "com.zhiliaoapp.musically",
        "tiktok" to "com.zhiliaoapp.musically",
        "вотсап" to "com.whatsapp",
        "whatsapp" to "com.whatsapp",
        "хром" to "com.android.chrome",
        "chrome" to "com.android.chrome",
        "плей" to "com.android.vending",
        "play" to "com.android.vending",
        "камера" to "com.transsion.camera",
        "camera" to "com.transsion.camera",
        "настройки" to "com.android.settings",
        "settings" to "com.android.settings",
        "контакты" to "com.android.contacts",
        "contacts" to "com.android.contacts",
        "часы" to "com.transsion.deskclock",
        "радио" to "com.transsion.fmradio"
    )

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrEmpty()) handleCommand(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)
        inputField = findViewById(R.id.inputField)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val voiceButton = findViewById<Button>(R.id.voiceButton)

        tts = TextToSpeech(this, this)
        requestAllPermissions()
        setupCallListener()

        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) {
                inputField.text.clear()
                handleCommand(t)
            }
        }

        voiceButton.setOnClickListener { startVoiceRecognition() }
        log("Джарвис запущен")
    }

    private fun setupCallListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!isCalling) {
                            isCalling = true
                            callStartTime = System.currentTimeMillis()
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isCalling) {
                            isCalling = false
                            val duration = System.currentTimeMillis() - callStartTime
                            if (duration < 3000) {
                                log("Звонок отменен или отклонен")
                                tts?.speak("Звонок отменен", TextToSpeech.QUEUE_FLUSH, null, "j")
                            }
                        }
                    }
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPerms.launch(notGranted.toTypedArray())
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            log("STT: ${e.message}")
        }
    }

    private fun handleCommand(raw: String) {
        val cmd = raw.lowercase(Locale.ROOT).trim()
        log("> $cmd")

        if (cmd.startsWith("позвони ") || cmd.startsWith("набери ")) {
            val target = cmd.substringAfter(" ").trim()
            if (target.replace(" ", "").replace("+","").all { it.isDigit() }) {
                callNumber(target)
            } else {
                val num = findContactNumber(target)
                if (num != null) callNumber(num) else reply("Контакт $target не найден")
            }
            return
        }

        if (cmd.startsWith("открой ") || cmd.startsWith("запусти ")) {
            val app = cmd.substringAfter(" ").trim()
            if (openApp(app)) reply("Открываю $app") else reply("Приложение $app не найдено")
            return
        }

        if (cmd.startsWith("погода")) {
            val city = cmd.removePrefix("погода").trim().ifEmpty { "москва" }
            executor.execute {
                val w = getWeather(city)
                runOnUiThread { if (w != null) reply(w) else reply("Не нашёл $city") }
            }
            return
        }

        if (cmd.contains("батаре") || cmd.contains("заряд")) {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            reply("Заряд $level процентов")
            return
        }

        if (cmd.contains("время")) {
            reply("Сейчас ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())}")
            return
        }

        reply("Не понял: $cmd")
    }

    private fun callNumber(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            reply("Звоню $number")
        } catch (e: SecurityException) {
            reply("Нет разрешения")
        }
    }

    private fun findContactNumber(name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        var found: String? = null
        cursor.use {
            while (it.moveToNext()) {
                val dn = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: continue
                if (dn.lowercase().contains(name.lowercase())) {
                    found = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    break
                }
            }
        }
        return found
    }

    private fun openApp(name: String): Boolean {
        // 1) Ищем в нашем жестком списке
        val pkgName = appsMap[name]
        if (pkgName != null) {
            val intent = packageManager.getLaunchIntentForPackage(pkgName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            }
        }

        // 2) Если нет в списке — ищем по названию
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val target = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(name.lowercase())
        } ?: return false
        
        val intent = pm.getLaunchIntentForPackage(target.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    private fun getWeather(city: String): String? {
        return try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1&language=ru"
            val geoJson = JSONObject(URL(geoUrl).readText())
            val results = geoJson.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val r = results.getJSONObject(0)
            val lat = r.getDouble("latitude")
            val lon = r.getDouble("longitude")
            val name = r.optString("name", city)
            val wUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code"
            val wJson = JSONObject(URL(wUrl).readText())
            val cur = wJson.getJSONObject("current")
            val temp = cur.getDouble("temperature_2m")
            val feels = cur.getDouble("apparent_temperature")
            val hum = cur.getInt("relative_humidity_2m")
            val code = cur.getInt("weather_code")
            val desc = weatherDesc(code)
            "В городе $name сейчас ${"%.0f".format(temp)} градусов, $desc. Ощущается как ${"%.0f".format(feels)}. Влажность $hum процентов."
        } catch (e: Exception) { null }
    }

    private fun weatherDesc(code: Int): String = when (code) {
        0 -> "ясно"; 1, 2 -> "переменная облачность"; 3 -> "пасмурно"
        45, 48 -> "туман"; 51, 53, 55 -> "морось"
        61, 63, 65 -> "дождь"; 71, 73, 75 -> "снег"
        80, 81, 82 -> "ливень"; 95, 96, 99 -> "гроза"
        else -> "код $code"
    }

    private fun log(text: String) { runOnUiThread { logView.append("\n$text") } }

    private fun reply(text: String) {
        log(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ru", "RU")
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
