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
import android.text.Html
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private var isBubbleRunning = false
    private var skeletonView: View? = null

    private val ADMIN_EMAIL = "russkietop4ik@gmail.com"
    private val ADMIN_SECRET_KEY = "sk-a7KHDbwMldwXfWer96w2LrYd5JWzFvxG"

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cmd = intent?.getStringExtra("command") ?: ""
            if (cmd.isNotEmpty()) runOnUiThread { handleCommand(cmd) }
        }
    }
    
    private val miniChatReceiver = object : BroadcastReceiver() {
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
        window.statusBarColor = Color.parseColor("#0F1115")
        
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

        // Устанавливаем шрифт для заголовка
        toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.manrope_bold)

        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        assistantName = prefs.getString("assistant_name", "Jarvis") ?: "Jarvis"
        apiKey = prefs.getString("api_key", "") ?: ""
        selectedVoice = prefs.getString("neural_voice", "nova") ?: "nova"
        isBubbleRunning = prefs.getBoolean("bubble_running", false)
        
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        val serviceIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(miniChatReceiver, IntentFilter("MINI_CHAT_COMMAND"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, IntentFilter("WAKE_WORD_DETECTED"))
            registerReceiver(miniChatReceiver, IntentFilter("MINI_CHAT_COMMAND"))
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
        btnMenu.setOnClickListener { showBottomSheetMenu() }
    }

    private fun showSkeletonLoader() {
        val skeletonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1E1E28"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8, 200, 8)
            }
        }
        
        for (i in 0..2) {
            val line = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (i == 2) 200 else LinearLayout.LayoutParams.MATCH_PARENT, 
                    20
                ).apply { setMargins(0, 8, 0, 0) }
                setBackgroundColor(Color.parseColor("#2A2A35"))
                startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.pulse_alpha))
            }
            skeletonLayout.addView(line)
        }
        
        logContainer.addView(skeletonLayout)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        skeletonView = skeletonLayout
    }

    private fun hideSkeletonLoader() {
        skeletonView?.let { logContainer.removeView(it) }
        skeletonView = null
    }

    private fun showBottomSheetMenu() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_menu, null)
        bottomSheetDialog.setContentView(view)
        
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }

        val tvBubbleStatus = view.findViewById<TextView>(R.id.tvBubbleStatus)
        if (isBubbleRunning) tvBubbleStatus.text = "🫧 Выключить пузырь" else tvBubbleStatus.text = "🫧 Включить пузырь"

        view.findViewById<LinearLayout>(R.id.btnMenuCommands).setOnClickListener {
            bottomSheetDialog.dismiss(); showCommandsList()
        }
        view.findViewById<LinearLayout>(R.id.btnMenuVoice).setOnClickListener {
            bottomSheetDialog.dismiss(); showVoiceSettingsDialog()
        }
        view.findViewById<LinearLayout>(R.id.btnMenuBubble).setOnClickListener {
            bottomSheetDialog.dismiss(); toggleBubble()
        }
        view.findViewById<LinearLayout>(R.id.btnMenuLogout).setOnClickListener {
            bottomSheetDialog.dismiss(); FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java)); finish()
        }
        bottomSheetDialog.show()
    }

    private fun showCommandsList() {
        val dialog = Dialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_commands_list, null)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleBubble() {
        if (!isBubbleRunning) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, FloatingBubbleService::class.java))
                isBubbleRunning = true
                getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putBoolean("bubble_running", true).apply()
                Toast.makeText(this, "Пузырь включен!", Toast.LENGTH_SHORT).show()
            }
        } else {
            stopService(Intent(this, FloatingBubbleService::class.java))
            isBubbleRunning = false
            getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putBoolean("bubble_running", false).apply()
            Toast.makeText(this, "Пузырь выключен!", Toast.LENGTH_SHORT).show()
        }
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

    private fun saveCustomCommands() {
        val sb = StringBuilder()
        for ((k, v) in customCommands) sb.append("$k::$v;;")
        getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE).edit().putString("custom_commands", sb.toString()).apply()
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

        if (cmd.contains("секрет ")) {
            val secretWord = cmd.replace("секрет ", "").trim()
            if (customCommands.containsKey(secretWord)) {
                val action = customCommands[secretWord]!!
                reply("Выполняю секретную команду: $action"); cmd = action
            } else { reply("Секретная команда '$secretWord' не найдена."); return }
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
        
        if (cmd.contains("позвони ")) {
            val target = cmd.substringAfter("позвони ").trim()
            if (target.replace(" ", "").replace("+","").all { it.isDigit() }) callNumber(target)
            else {
                val num = findContactNumber(target)
                if (num != null) callNumber(num) else reply("Контакт не найден")
            }
            return
        }

        if (cmd.contains("батаре")) {
            val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
            reply("Заряд: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"); return
        }
        
        if (cmd.contains("время")) {
            reply("Сейчас ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())}"); return
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
        val translitMap = mapOf("ютуб" to "youtube", "тг" to "telegram", "вк" to "vk", "вотсап" to "whatsapp", "инста" to "instagram", "тикток" to "tiktok")
        if (translitMap.containsKey(search)) search = translitMap[search]!!
        var pkg = installedAppsMap[search] ?: installedAppsMap.entries.firstOrNull { it.key.contains(search) }?.value
        if (pkg != null) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); return true }
        }
        return false
    }

    private fun callNumber(number: String) {
        try { startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))); reply("Соединяю...") } catch (e: SecurityException) { reply("Нет разрешения") }
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

    private fun askChatGPT(prompt: String) {
        showSkeletonLoader()
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
                        runOnUiThread { 
                            hideSkeletonLoader()
                            animateBotReply(answer) 
                        }
                    } else runOnUiThread { hideSkeletonLoader(); reply("Ошибка ИИ: ${response.code}") }
                }
            } catch (e: Exception) { runOnUiThread { hideSkeletonLoader(); reply("Ошибка сети.") } }
        }
    }

    private fun animateBotReply(fullText: String) {
        val tv = TextView(this)
        tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) // Применяем шрифт
        tv.textSize = 15f; tv.setPadding(32, 24, 32, 24); tv.setTextColor(Color.WHITE)
        
        // Убрали stroke (рамку), оставили чистый градиент
        val drawable = GradientDrawable().apply {
            colors = intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#1E1E28"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            cornerRadius = 24f
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
                    uiHandler.postDelayed(this, 40)
                }
            }
        }
        uiHandler.postDelayed(runnable, 300)
    }

    private fun logUser(text: String) { runOnUiThread { addBubble(text, true) } }
    private fun reply(text: String) { runOnUiThread { addBubble(text, false) }; neuralTTS.speak(text, selectedVoice) }
    
    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this)
        tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) // Применяем шрифт
        tv.setTextColor(Color.WHITE)
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, 0)
        
        if (isUser) {
            tv.setBackgroundColor(Color.parseColor("#2A2A35"))
        } else {
            // Убрали stroke
            val drawable = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#2A2A35"), Color.parseColor("#1E1E28"))
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = 24f
            }
            tv.background = drawable
        }
        
        tv.text = spannable; tv.textSize = 15f; tv.setPadding(32, 24, 32, 24)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(if (isUser) 64 else 0, 8, if (isUser) 0 else 64, 8)
        tv.layoutParams = params; logContainer.addView(tv)
        
        tv.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
        val notGranted = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (notGranted.isNotEmpty()) requestPerms.launch(notGranted.toTypedArray())
    }

    override fun onDestroy() { try { unregisterReceiver(wakeWordReceiver); unregisterReceiver(miniChatReceiver) } catch (e: Exception) {}; super.onDestroy() }
}
