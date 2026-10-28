package com.jarvis.app

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.text.Html
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var toolbarTitle: TextView
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    
    private var isCalling: Boolean = false
    private var callStartTime: Long = 0
    private var assistantName = "Jarvis"
    private var isFlashlightOn = false
    private var apiKey = ""
    private var telegramToken = ""

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd.isNotEmpty()) runOnUiThread { handleCommand(cmd) }
        }
    }
    
    private val telegramReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd.isNotEmpty()) runOnUiThread { handleCommand(cmd) }
        }
    }

    private val appsMap = mapOf(
        "телеграм" to "org.telegram.messenger", "ютуб" to "com.google.android.youtube",
        "вк" to "com.vkontakte.android", "тикток" to "com.zhiliaoapp.musically",
        "вотсап" to "com.whatsapp", "хром" to "com.android.chrome",
        "камера" to "com.transsion.camera", "настройки" to "com.android.settings",
        "happ" to "com.happvpn.app", "впн" to "com.happvpn.app" // Пример пакета VPN
    )

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrEmpty()) handleCommand(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logContainer = findViewById(R.id.logContainer)
        scrollView = findViewById(R.id.scrollView)
        inputField = findViewById(R.id.inputField)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)
        val btnClear = findViewById<ImageButton>(R.id.btnClear)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        assistantName = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        telegramToken = prefs.getString("telegram_token", "") ?: ""
        
        apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) apiKey = BuildConfig.DEFAULT_API_KEY

        toolbarTitle.text = assistantName

        tts = TextToSpeech(this, this)
        requestAllPermissions()
        try { setupCallListener() } catch (e: Exception) { }

        val serviceIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        
        if (telegramToken.isNotEmpty()) {
            startService(Intent(this, TelegramService::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(telegramReceiver, IntentFilter("TELEGRAM_COMMAND"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"))
            registerReceiver(telegramReceiver, IntentFilter("TELEGRAM_COMMAND"))
        }

        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) { inputField.text.clear(); handleCommand(t) }
        }
        voiceButton.setOnClickListener { startVoiceRecognition() }
        btnClear.setOnClickListener { logContainer.removeAllViews() }
        btnMenu.setOnClickListener { showCommandsMenu() }
        btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val inputApi = EditText(this).apply { setText(apiKey); hint = "API Ключ ProxyAPI" }
        val inputTg = EditText(this).apply { setText(telegramToken); hint = "Токен Telegram Бота" }
        layout.addView(inputApi)
        layout.addView(inputTg)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setTitle("Настройки")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                apiKey = inputApi.text.toString().trim()
                telegramToken = inputTg.text.toString().trim()
                getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().apply {
                    putString("api_key", apiKey)
                    putString("telegram_token", telegramToken)
                }.apply()
                reply("Настройки сохранены.")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun handleCommand(raw: String) {
        val cmd = raw.lowercase(Locale.ROOT).trim()
        logUser(raw)

        if (cmd.startsWith("смени имя на ")) {
            assistantName = cmd.replace("смени имя на ", "").trim().replaceFirstChar { it.uppercase() }
            getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putString("assistant_name", assistantName).apply()
            reply("Теперь меня зовут $assistantName. Перезапустите приложение.")
            return
        }
        if (cmd.startsWith("позвони ")) {
            val target = cmd.substringAfter(" ").trim()
            val num = findContactNumber(target)
            if (num != null) {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))); reply("Соединяю...")
            } else reply("Контакт не найден")
            return
        }
        if (cmd.startsWith("открой ")) {
            val app = cmd.substringAfter(" ").trim()
            if (openApp(app)) reply("Открываю") else reply("Не найдено")
            return
        }
        if (cmd.contains("прочитай уведомления")) {
             val notifs = NotificationService.latestNotifications
             if (notifs.isEmpty()) reply("Новых уведомлений нет.") else reply(notifs.joinToString(". "))
             return
        }
        if (cmd.contains("не беспокоить")) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                reply("Режим включен.")
            } else startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        if (cmd.contains("батаре")) {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            reply("Заряд: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"); return
        }
        if (cmd.contains("время")) {
            reply("Сейчас ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"); return
        }

        // --- ACCESSIBILITY (VPN и свайпы) ---
        if (cmd.contains("включи впн") || cmd.contains("подключи впн")) {
            val pkg = appsMap["впн"]
            if (pkg != null) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    reply("Открываю VPN, сейчас нажму подключить...")
                    executor.execute {
                        Thread.sleep(2500) // Ждем загрузки VPN
                        runOnUiThread {
                            val success = JarvisAccessibilityService.instance?.clickByText(listOf("Connect", "Подключить", "Включить", "On", "Start"))
                            if (success == true) reply("VPN подключен.") else reply("Не нашел кнопку подключения на экране.")
                        }
                    }
                } else reply("Приложение VPN не найдено.")
            } else reply("Пакет VPN не настроен в appsMap.")
            return
        }

        if (cmd.contains("следующее видео") || cmd.contains("следующее") || cmd.contains("листни")) {
            val success = JarvisAccessibilityService.instance?.swipeUp()
            if (success != null) reply("Листаю...") else reply("Служба управления (Accessibility) не включена.")
            return
        }

        // --- CHATGPT ---
        if (apiKey.isNotEmpty()) {
            reply("Думаю...")
            askChatGPT(raw)
        } else {
            reply("Я не знаю такой команды, а API-ключ не установлен.")
        }
    }

    private fun askChatGPT(prompt: String) {
        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "gpt-3.5-turbo")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user"); put("content", prompt)
                    }))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.proxyapi.ru/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val resBody = response.body?.string()
                    if (response.isSuccessful && resBody != null) {
                        val answer = JSONObject(resBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        runOnUiThread { reply(answer) }
                    } else {
                        runOnUiThread { reply("Ошибка ИИ. Проверьте баланс или ключ.") }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { reply("Ошибка сети: ${e.message}") }
            }
        }
    }

    private fun logUser(text: String) { runOnUiThread { addBubble(text, true) } }
    private fun reply(text: String) {
        runOnUiThread { addBubble(text, false) }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this)
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, 0)
        
        if (isUser) {
            tv.setBackgroundColor(Color.parseColor("#2A2A35"))
        } else {
            val drawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C26")); cornerRadius = 24f
                setStroke(2, Color.parseColor("#5B5BFF"))
            }
            tv.background = drawable
        }
        tv.text = spannable
        tv.textSize = 15f
        tv.setPadding(32, 24, 32, 24)
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(if (isUser) 64 else 0, 8, if (isUser) 0 else 64, 8)
        tv.layoutParams = params
        logContainer.addView(tv)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun showCommandsMenu() {
        val commands = "<b>Команды:</b><br>• открой (приложение)<br>• позвони (контакт)<br>• включи впн<br>• следующее видео / листни<br>• прочитай уведомления<br>• не беспокоить<br>• батарея / время<br><br>Или задайте любой вопрос ИИ!"
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setTitle("Помощь").setMessage(Html.fromHtml(commands, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("ОК", null).show()
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
        try { speechLauncher.launch(intent) } catch (e: Exception) { logUser("STT не работает") }
    }

    private fun setupCallListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            tm.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) { isCalling = true; callStartTime = System.currentTimeMillis() }
                    else if (state == TelephonyManager.CALL_STATE_IDLE && isCalling) {
                        isCalling = false
                        if (System.currentTimeMillis() - callStartTime < 3000) reply("Звонок отменен")
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
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
                    found = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)); break
                }
            }
        }
        return found
    }

    private fun openApp(name: String): Boolean {
        val pkg = appsMap[name] ?: return false
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); return true }
        return false
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ru", "RU") }
    override fun onDestroy() {
        try { unregisterReceiver(wakeWordReceiver); unregisterReceiver(telegramReceiver) } catch (e: Exception) {}
        tts?.shutdown(); super.onDestroy()
    }
}
