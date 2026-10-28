package com.jarvis.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.text.Html
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var toolbarTitle: TextView
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    
    private var callStartTime: Long = 0
    private var isCalling: Boolean = false
    private var assistantName = "Jarvis"

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd.isNotEmpty()) {
                runOnUiThread { handleCommand(cmd) }
            }
        }
    }

    private val appsMap = mapOf(
        "телеграм" to "org.telegram.messenger", "ютуб" to "com.google.android.youtube",
        "вк" to "com.vkontakte.android", "тикток" to "com.zhiliaoapp.musically",
        "вотсап" to "com.whatsapp", "хром" to "com.android.chrome",
        "камера" to "com.transsion.camera", "настройки" to "com.android.settings"
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
        scrollView = findViewById(R.id.scrollView)
        inputField = findViewById(R.id.inputField)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)
        val btnClear = findViewById<ImageButton>(R.id.btnClear)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)

        // Читаем имя из памяти
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        assistantName = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        toolbarTitle.text = assistantName

        tts = TextToSpeech(this, this)
        requestAllPermissions()
        try { setupCallListener() } catch (e: Exception) { }

        // Запускаем фоновый сервис
        val serviceIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)

        // Регистрируем слушатель фоновых команд
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"))
        }

        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) { inputField.text.clear(); handleCommand(t) }
        }

        voiceButton.setOnClickListener { startVoiceRecognition() }

        btnClear.setOnClickListener {
            logView.text = ""
            log("Чат очищен. Имя '$assistantName' в безопасности.")
        }

        btnMenu.setOnClickListener { showCommandsMenu() }

        log("<i>Система инициализирована. Я слушаю вас.</i>")
    }

    private fun showCommandsMenu() {
        val commands = arrayOf(
            "<b>Управление:</b><br>открой (приложение)<br>позвони (контакт)<br>батарея<br>время<br>погода (город)<br>смени имя на (имя)"
        )
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setTitle("Доступные команды")
            .setMessage(Html.fromHtml(commands[0], Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun setupCallListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            tm.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> { if (!isCalling) { isCalling = true; callStartTime = System.currentTimeMillis() } }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            if (isCalling) {
                                isCalling = false
                                if (System.currentTimeMillis() - callStartTime < 3000) {
                                    log("<font color='#FF5252'>Звонок отменен</font>")
                                    tts?.speak("Звонок отменен", TextToSpeech.QUEUE_FLUSH, null, "j")
                                }
                            }
                        }
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS
        )
        val notGranted = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (notGranted.isNotEmpty()) requestPerms.launch(notGranted.toTypedArray())
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        try { speechLauncher.launch(intent) } catch (e: Exception) { log("STT не работает") }
    }

    private fun handleCommand(raw: String) {
        val cmd = raw.lowercase(Locale.ROOT).trim()
        log("<b><font color='#7B7BFF'>Вы:</font></b> <i>$cmd</i>")

        if (cmd.startsWith("смени имя на ")) {
            val newName = cmd.replace("смени имя на ", "").trim()
            assistantName = newName.replaceFirstChar { it.uppercase() }
            getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putString("assistant_name", assistantName).apply()
            reply("Теперь меня зовут $assistantName. Перезапустите приложение, чтобы имя закрепилось в шапке.")
            return
        }

        if (cmd.startsWith("позвони ") || cmd.startsWith("набери ")) {
            val target = cmd.substringAfter(" ").trim()
            if (target.replace(" ", "").replace("+","").all { it.isDigit() }) callNumber(target)
            else {
                val num = findContactNumber(target)
                if (num != null) callNumber(num) else reply("Контакт не найден")
            }
            return
        }

        if (cmd.startsWith("открой ")) {
            val app = cmd.substringAfter(" ").trim()
            if (openApp(app)) reply("Открываю $app") else reply("Не удалось найти приложение")
            return
        }

        if (cmd.startsWith("погода")) {
            val city = cmd.removePrefix("погода").trim().ifEmpty { "москва" }
            executor.execute {
                val w = getWeather(city)
                runOnUiThread { if (w != null) reply(w) else reply("Город не найден") }
            }
            return
        }

        if (cmd.contains("батаре")) {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            reply("Заряд батареи: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%")
            return
        }

        if (cmd.contains("время")) {
            reply("Сейчас ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}")
            return
        }
        reply("Я не знаю такой команды.")
    }

    private fun callNumber(number: String) {
        try { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); reply("Соединяю...") } 
        catch (e: SecurityException) { reply("Нет разрешения на звонки") }
    }

    private fun findContactNumber(name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return null
        var found: String? = null
        cursor.use {
            while (it.moveToNext()) {
                val dn = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: continue
                if (dn.lowercase().contains(name.lowercase())) {
                    found = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)); break
                }
            }
        }
        return found
    }

    private fun openApp(name: String): Boolean {
        val pkgName = appsMap[name]
        if (pkgName != null) {
            val intent = packageManager.getLaunchIntentForPackage(pkgName)
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); return true }
        }
        val pm = packageManager
        val target = pm.getInstalledApplications(0).firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(name.lowercase()) } ?: return false
        val intent = pm.getLaunchIntentForPackage(target.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); return true
    }

    private fun getWeather(city: String): String? {
        return try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1&language=ru"
            val geoJson = JSONObject(URL(geoUrl).readText())
            val results = geoJson.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val r = results.getJSONObject(0)
            val wUrl = "https://api.open-meteo.com/v1/forecast?latitude=${r.getDouble("latitude")}&longitude=${r.getDouble("longitude")}&current=temperature_2m"
            val wJson = JSONObject(URL(wUrl).readText())
            "Сейчас в ${r.optString("name")} ${"%.0f".format(wJson.getJSONObject("current").getDouble("temperature_2m"))} градусов."
        } catch (e: Exception) { null }
    }

    private fun log(text: String) {
        runOnUiThread {
            logView.append(Html.fromHtml("$text<br><br>", Html.FROM_HTML_MODE_LEGACY))
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun reply(text: String) {
        log("<b><font color='#4CAF50'>$assistantName:</font></b> $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ru", "RU")
    }

    override fun onDestroy() { 
        try { unregisterReceiver(wakeWordReceiver) } catch (e: Exception) {}
        tts?.shutdown()
        super.onDestroy() 
    }
}
