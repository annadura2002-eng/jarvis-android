package com.jarvis.app

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var toolbarTitle: TextView
    private lateinit var apiBar: LinearLayout
    private lateinit var apiKeyField: EditText
    private var tts: TextToSpeech? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    
    private var isCalling: Boolean = false
    private var callStartTime: Long = 0
    private var assistantName = "Jarvis"
    private var isFlashlightOn = false
    private var apiKey = ""

    private val wakeWordReceiver = object : BroadcastReceiver() {
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
        "happ" to "com.happvpn.app", "впн" to "com.happvpn.app"
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
        window.statusBarColor = Color.parseColor("#0B0B0E")

        logContainer = findViewById(R.id.logContainer)
        scrollView = findViewById(R.id.scrollView)
        inputField = findViewById(R.id.inputField)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        apiBar = findViewById(R.id.apiBar)
        apiKeyField = findViewById(R.id.apiKeyField)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)
        val btnClear = findViewById<ImageButton>(R.id.btnClear)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)

        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        assistantName = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) apiKey = BuildConfig.DEFAULT_API_KEY
        apiKeyField.setText(apiKey)
        toolbarTitle.text = assistantName

        tts = TextToSpeech(this, this)
        requestAllPermissions()
        try { setupCallListener() } catch (e: Exception) { }

        val serviceIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"))
        }

        btnSaveKey.setOnClickListener {
            apiKey = apiKeyField.text.toString().trim()
            prefs.edit().putString("api_key", apiKey).apply()
            reply("Ключ сохранен! Теперь ИИ работает.")
            apiBar.visibility = LinearLayout.GONE
        }

        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) { inputField.text.clear(); handleCommand(t) }
        }
        voiceButton.setOnClickListener { startVoiceRecognition() }
        btnClear.setOnClickListener { logContainer.removeAllViews() }
        btnMenu.setOnClickListener { showMenuDialog() }
    }

    private fun showMenuDialog() {
        val maskedKey = if (apiKey.length > 8) apiKey.take(5) + "****************" + apiKey.takeLast(4) else "Не установлен"
        
        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val tvKey = TextView(this).apply {
            text = "Ваш ключ ИИ:\n$maskedKey\n(Нажмите, чтобы скопировать)"
            setTextColor(Color.parseColor("#8B8BFF"))
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }
        tvKey.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("API Key", apiKey))
            Toast.makeText(this, "Ключ скопирован!", Toast.LENGTH_SHORT).show()
        }

        val btnChangeKey = Button(this).apply {
            text = "Изменить ключ ИИ"
            setBackgroundColor(Color.parseColor("#2A2A35"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                apiBar.visibility = LinearLayout.VISIBLE
                // Закрываем диалог
                (parent as? android.app.Dialog)?.dismiss()
            }
        }
        
        val btnLogout = Button(this).apply {
            text = "Выйти из аккаунта"
            setBackgroundColor(Color.parseColor("#FF5252"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }

        menuLayout.addView(tvKey)
        menuLayout.addView(btnChangeKey)
        menuLayout.addView(btnLogout)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setTitle("Настройки профиля")
            .setView(menuLayout)
            .setPositiveButton("Закрыть", null)
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
        if (cmd.contains("включи впн") || cmd.contains("подключи впн")) {
            val pkg = appsMap["впн"]
            if (pkg != null) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    reply("Открываю VPN...")
                    executor.execute {
                        Thread.sleep(2500)
                        runOnUiThread {
                            val success = JarvisAccessibilityService.instance?.clickByText(listOf("Connect", "Подключить", "Включить", "On", "Start"))
                            if (success == true) reply("VPN подключен.") else reply("Кнопка не найдена.")
                        }
                    }
                }
            } else reply("VPN не найдено.")
            return
        }
        if (cmd.contains("следующее видео") || cmd.contains("следующее") || cmd.contains("листни")) {
            val success = JarvisAccessibilityService.instance?.swipeUp()
            if (success != null) reply("Листаю...") else reply("Служба Accessibility не включена.")
            return
        }

        if (apiKey.isNotEmpty()) {
            reply("Думаю...")
            askChatGPT(raw)
        } else {
            reply("Для ИИ нужен ключ! Нажмите на три полоски вверху -> Изменить ключ ИИ.")
        }
    }

    private fun askChatGPT(prompt: String) {
        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
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
                        runOnUiThread { reply("Ошибка ИИ: ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { reply("Ошибка сети.") }
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
        try { unregisterReceiver(wakeWordReceiver) } catch (e: Exception) {}
        tts?.shutdown(); super.onDestroy()
    }
}
