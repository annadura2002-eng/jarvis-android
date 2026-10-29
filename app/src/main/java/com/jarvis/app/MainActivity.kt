package com.jarvis.app

import android.Manifest
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var toolbarTitle: TextView
    private lateinit var apiBar: LinearLayout
    private lateinit var apiKeyField: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var neuralTTS: NeuralTTS
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private val uiHandler = Handler(Looper.getMainLooper())
    
    private var assistantName = "Jarvis"
    private var apiKey = ""
    private var installedAppsMap = mutableMapOf<String, String>()
    private var customCommands = mutableMapOf<String, String>()
    private lateinit var speechRecognizer: SpeechRecognizer
    private var voiceDialog: Dialog? = null
    private var equalizerView: EqualizerView? = null
    private var selectedVoice = "nova"

    private val ADMIN_EMAIL = "russkietop4ik@gmail.com"
    private val ADMIN_SECRET_KEY = "sk-a7KHDbwMldwXfWer96w2LrYd5JWzFvxG"

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd.isNotEmpty()) runOnUiThread { handleCommand(cmd) }
        }
    }

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Jarvis)
        setContentView(R.layout.activity_main)
        
        logContainer = findViewById(R.id.logContainer)
        scrollView = findViewById(R.id.scrollView)
        inputField = findViewById(R.id.inputField)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        apiBar = findViewById(R.id.apiBar)
        apiKeyField = findViewById(R.id.apiKeyField)
        voiceButton = findViewById(R.id.voiceButton)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)

        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        assistantName = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        apiKey = prefs.getString("api_key", "") ?: ""
        selectedVoice = prefs.getString("neural_voice", "nova") ?: "nova"
        
        val currentUser = FirebaseAuth.getInstance().currentUser?.email ?: ""
        if (currentUser == ADMIN_EMAIL && apiKey.isEmpty()) {
            apiKey = ADMIN_SECRET_KEY; prefs.edit().putString("api_key", apiKey).apply()
        }
        
        neuralTTS = NeuralTTS(this, apiKey)
        apiKeyField.setText(apiKey)
        toolbarTitle.text = assistantName
        scanAllApps()
        loadCustomCommands()

        requestAllPermissions()
        setupSpeechRecognizer()
        
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
            neuralTTS = NeuralTTS(this, apiKey)
            reply("Ключ сохранен!"); apiBar.visibility = LinearLayout.GONE
        }
        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) { inputField.text.clear(); handleCommand(t) }
        }
        voiceButton.setOnClickListener { showBeautifulVoiceInput() }
        btnMenu.setOnClickListener { showMenuDialog() }
    }

    // ФУНКЦИЯ ВКЛЮЧЕНИЯ ПУЗЫРЯ
    private fun toggleFloatingBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разрешите отображение поверх других окон", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startService(Intent(this, FloatingBubbleService::class.java))
            Toast.makeText(this, "Пузырь включен! Вы можете перемещать его.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenuDialog() {
        val menuLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        
        val tvCommands = TextView(this).apply {
            text = "Команды:\n• открой (приложение)\n• нажми (кнопку)\n• добавь секретную команду\n\nНастройки:"
            setTextColor(Color.WHITE); textSize = 15f; setLineSpacing(0f, 1.2f)
        }

        val btnBubble = Button(this).apply {
            text = "Включить плавающий пузырь"
            setBackgroundColor(Color.parseColor("#6C63FF")); setTextColor(Color.WHITE)
            setOnClickListener { 
                toggleFloatingBubble()
                (parent as? android.app.Dialog)?.dismiss() 
            }
        }

        val btnVoiceSettings = Button(this).apply {
            text = "Выбрать нейроголос"
            setBackgroundColor(Color.parseColor("#2A2A35")); setTextColor(Color.WHITE)
            setOnClickListener { (parent as? android.app.Dialog)?.dismiss(); showVoiceSettingsDialog() }
        }

        val btnClear = Button(this).apply {
            text = "Очистить чат"
            setBackgroundColor(Color.parseColor("#2A2A35")); setTextColor(Color.WHITE)
            setOnClickListener { logContainer.removeAllViews(); (parent as? android.app.Dialog)?.dismiss() }
        }

        val btnLogout = Button(this).apply {
            text = "Выйти"; setBackgroundColor(Color.parseColor("#FF5252")); setTextColor(Color.WHITE)
            setOnClickListener { FirebaseAuth.getInstance().signOut(); startActivity(Intent(this@MainActivity, LoginActivity::class.java)); finish() }
        }
        menuLayout.addView(tvCommands); menuLayout.addView(btnBubble); menuLayout.addView(btnVoiceSettings); menuLayout.addView(btnClear); menuLayout.addView(btnLogout)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_Material3_Dark_Dialog_Alert)
            .setTitle("Меню").setView(menuLayout).setPositiveButton("Закрыть", null).show()
    }

    private fun showVoiceSettingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        val voices = arrayOf("nova (женский)", "shimmer (женский)", "alloy (нейтральный)", "onyx (мужской)", "echo (мужской)", "fable (женский)")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
        val currentIndex = voices.indexOfFirst { it.startsWith(selectedVoice) }
        if (currentIndex != -1) spinner.setSelection(currentIndex)
        layout.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle("Выбор живого голоса (ИИ)")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                selectedVoice = spinner.selectedItem.toString().split(" ")[0]
                getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putString("neural_voice", selectedVoice).apply()
                reply("Голос изменен на $selectedVoice")
            }.setNegativeButton("Отмена", null).show()
    }

    private fun loadCustomCommands() {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("custom_commands", "") ?: ""
        if (saved.isNotEmpty()) {
            val items = saved.split(";;")
            for (item in items) {
                val parts = item.split("::")
                if (parts.size == 2) customCommands[parts[0]] = parts[1]
            }
        }
    }

    private fun showBeautifulVoiceInput() {
        voiceDialog = Dialog(this)
        voiceDialog?.setContentView(R.layout.dialog_voice)
        equalizerView = voiceDialog?.findViewById(R.id.equalizerView)
        val statusView = voiceDialog?.findViewById<TextView>(R.id.voiceStatus)
        equalizerView?.startAnimation()
        statusView?.text = "Слушаю..."
        speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        })
        voiceDialog?.show()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                equalizerView?.stopAnimation(); voiceDialog?.dismiss()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrEmpty()) handleCommand(text)
            }
            override fun onError(error: Int) { equalizerView?.stopAnimation(); voiceDialog?.dismiss() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun handleCommand(raw: String) {
        var cmd = raw.lowercase(Locale.ROOT).trim()
        logUser(raw)
        val access = JarvisAccessibilityService.instance

        for ((key, action) in customCommands) {
            if (cmd.contains(key)) { reply("Выполняю секретную команду: $action"); cmd = action; break }
        }

        if (cmd.contains("нажми ") || cmd.contains("сделай ")) {
            if (apiKey.isEmpty()) { reply("Для этого нужен ИИ-ключ!"); return }
            reply("Анализирую экран...")
            executor.execute {
                val screenText = access?.getScreenText() ?: ""
                if (screenText.isEmpty()) { runOnUiThread { reply("Служба Accessibility выключена.") }; return@execute }
                val prompt = "На экране телефона есть текст: [$screenText]. Пользователь просит: '$cmd'. Найди кнопку, выведи ТОЛЬКО ТЕКСТ кнопки."
                val buttonName = askAiForButton(prompt)
                runOnUiThread {
                    if (buttonName != null && buttonName != "NO") {
                        val success = access?.clickByText(listOf(buttonName))
                        if (success == true) reply("Нажал на '$buttonName'") else reply("Не нашел кнопку.")
                    } else reply("Не понял, куда нажимать.")
                }
            }
            return
        }

        if (cmd.contains("открой ")) {
            val app = cmd.substringAfter("открой ").trim().replace("приложение", "").trim()
            if (openAppUniversal(app)) reply("Открываю $app") else reply("Не найдено")
            return
        }
        if (cmd.contains("листни") || cmd.contains("следующее") || cmd.contains("вниз")) { access?.swipeUp(); reply("Листаю..."); return }
        if (cmd.contains("батаре")) {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            reply("Заряд: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"); return
        }

        if (apiKey.isNotEmpty()) askChatGPT(raw) else reply("Для ИИ нужен ключ!")
    }

    private fun askAiForButton(prompt: String): String? {
        try {
            val json = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
                put("max_tokens", 20)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return JSONObject(response.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            }
        } catch (e: Exception) {}
        return null
    }

    private fun scanAllApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (pm.getLaunchIntentForPackage(app.packageName) != null) installedAppsMap[label] = app.packageName
        }
    }

    private fun openAppUniversal(name: String): Boolean {
        var search = name.lowercase(Locale.ROOT).trim()
        val synonyms = mapOf("ютуб" to "youtube", "тг" to "telegram", "вк" to "vk")
        if (synonyms.containsKey(search)) search = synonyms[search]!!
        var pkg = installedAppsMap.entries.firstOrNull { it.key.contains(search) }?.value
        if (pkg != null) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); return true }
        }
        return false
    }

    private fun askChatGPT(prompt: String) {
        executor.execute {
            try {
                val json = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("https://api.proxyapi.ru/openai/v1/chat/completions").addHeader("Authorization", "Bearer $apiKey").post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answer = JSONObject(response.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        runOnUiThread { animateBotReply(answer) }
                    } else runOnUiThread { reply("Ошибка ИИ: ${response.code}") }
                }
            } catch (e: Exception) { runOnUiThread { reply("Ошибка сети.") } }
        }
    }

    private fun animateBotReply(fullText: String) {
        val tv = TextView(this)
        tv.textSize = 15f; tv.setPadding(32, 24, 32, 24)
        val drawable = GradientDrawable().apply {
            colors = intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#1E1E28"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            cornerRadius = 24f
            setStroke(1, Color.parseColor("#33FFFFFF"))
        }
        tv.background = drawable
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 8, 64, 8); tv.layoutParams = params
        logContainer.addView(tv); scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        neuralTTS.speak(fullText, selectedVoice)
        var index = 0
        val runnable = object : Runnable {
            override fun run() {
                if (index <= fullText.length) {
                    tv.text = fullText.substring(0, index); index++; scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    uiHandler.postDelayed(this, 30)
                }
            }
        }
        uiHandler.post(runnable)
    }

    private fun logUser(text: String) { runOnUiThread { addBubble(text, true) } }
    private fun reply(text: String) { runOnUiThread { addBubble(text, false) }; neuralTTS.speak(text, selectedVoice) }
    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this)
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, 0)
        if (isUser) tv.setBackgroundColor(Color.parseColor("#2A2A35"))
        else {
            val drawable = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#1E1E28"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = 24f
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            tv.background = drawable
        }
        tv.text = spannable; tv.textSize = 15f; tv.setPadding(32, 24, 32, 24)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(if (isUser) 64 else 0, 8, if (isUser) 0 else 64, 8)
        tv.layoutParams = params; logContainer.addView(tv)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
        val notGranted = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (notGranted.isNotEmpty()) requestPerms.launch(notGranted.toTypedArray())
    }

    override fun onDestroy() { try { unregisterReceiver(wakeWordReceiver) } catch (e: Exception) {}; super.onDestroy() }
}
