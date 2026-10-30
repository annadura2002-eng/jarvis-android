package com.jarvis.app

import android.Manifest
import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
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
import java.io.File
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

import com.jarvis.app.core.PrefsManager
import com.jarvis.app.core.CommandProcessor
import com.jarvis.app.core.AlarmData
import com.jarvis.app.core.ChatManager
import com.jarvis.app.core.TranslationManager
import com.jarvis.app.services.NeuralTTS
import com.jarvis.app.services.AlarmService
import com.jarvis.app.services.FloatingBubbleService
import com.jarvis.app.services.VoiceService
import com.jarvis.app.services.JarvisAccessibilityService

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
    private var isCallRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var recordFile: File? = null

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
        PrefsManager.init(this)
        setTheme(R.style.Theme_Jarvis)
        setContentView(R.layout.activity_main)
        window.statusBarColor = Color.parseColor("#0B0E14")
        
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

        try { toolbarTitle.typeface = ResourcesCompat.getFont(this, R.font.manrope_bold) } catch(e: Exception){}

        assistantName = PrefsManager.assistantName
        apiKey = PrefsManager.apiKey
        selectedVoice = PrefsManager.selectedVoice
        isBubbleRunning = PrefsManager.bubbleRunning
        
        neuralTTS = NeuralTTS(this, apiKey)
        apiKeyField.setText(apiKey)
        toolbarTitle.text = assistantName
        scanAllApps()
        loadCustomCommands()

        requestAllPermissions()
        setupSpeechRecognizer()
        try { setupCallListener() } catch (e: Exception) {}
        
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
            PrefsManager.apiKey = apiKey
            neuralTTS.updateApiKey(apiKey)
            reply("Ключ сохранен!"); apiBar.visibility = LinearLayout.GONE
        }
        sendButton.setOnClickListener {
            val t = inputField.text.toString().trim()
            if (t.isNotEmpty()) { inputField.text.clear(); handleCommand(t) }
        }
        voiceButton.setOnClickListener { 
            voiceButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
            showBeautifulVoiceInput() 
        }
        btnMenu.setOnClickListener { showBottomSheetMenu() }
    }

    fun setJarvisAlarm(hour: Int, minute: Int, daily: Boolean = false) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmId = (System.currentTimeMillis() % 10000).toInt()
        val intent = Intent(this, AlarmService::class.java).apply {
            putExtra("hour", hour); putExtra("minute", minute); putExtra("daily", daily); putExtra("id", alarmId)
        }
        val pendingIntent = PendingIntent.getService(this, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            
            PrefsManager.saveAlarm(AlarmData(hour, minute, daily, alarmId))
            reply(if (daily) "Ежедневный будильник на ${"%02d".format(hour)}:${"%02d".format(minute)}" else "Будильник на ${"%02d".format(hour)}:${"%02d".format(minute)}")
        } catch (e: Exception) { reply("Ошибка будильника: ${e.message}") }
    }

    // --- МОСТЫ ДЛЯ CommandProcessor ---
    fun createAlarmFromCommand(hour: Int, minute: Int, daily: Boolean) { setJarvisAlarm(hour, minute, daily) }
    fun sendReplyFromCommand(text: String) { reply(text) }
    fun startTranslationMode(fromLang: String, toLang: String) { TranslationManager.start(this, fromLang, toLang) }
    fun showSkeletonLoaderPublic() { showSkeletonLoader() }
    fun hideSkeletonLoaderPublic() { hideSkeletonLoader() }
    fun addBubblePublic(text: String, isUser: Boolean) { addBubble(text, isUser) }
    fun animateBotReplyPublic(text: String) { animateBotReply(text) }

    private fun showSkeletonLoader() {
        val skeletonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1F2B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 200, 8) }
        }
        for (i in 0..2) {
            val line = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(if (i == 2) 200 else LinearLayout.LayoutParams.MATCH_PARENT, 20).apply { setMargins(0, 8, 0, 0) }
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

        view.findViewById<LinearLayout>(R.id.btnMenuCommands).setOnClickListener { bottomSheetDialog.dismiss(); showCommandsList() }
        view.findViewById<LinearLayout>(R.id.btnMenuSecrets).setOnClickListener { bottomSheetDialog.dismiss(); showSecretsDialog() }
        view.findViewById<LinearLayout>(R.id.btnMenuVoice).setOnClickListener { bottomSheetDialog.dismiss(); showVoiceSettingsDialog() }
        view.findViewById<LinearLayout>(R.id.btnMenuSettings).setOnClickListener { bottomSheetDialog.dismiss(); apiBar.visibility = LinearLayout.VISIBLE }
        view.findViewById<LinearLayout>(R.id.btnMenuBubble).setOnClickListener { bottomSheetDialog.dismiss(); toggleBubble() }
        
        view.findViewById<LinearLayout>(R.id.btnMenuTheme)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        view.findViewById<LinearLayout>(R.id.btnMenuLogout).setOnClickListener {
            bottomSheetDialog.dismiss(); FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java)); finish()
        }
        bottomSheetDialog.show()
    }

    private fun showSecretsDialog() {
        val dialog = Dialog(this, R.style.Theme_Jarvis)
        val view = layoutInflater.inflate(R.layout.dialog_secrets, null)
        dialog.setContentView(view)

        val tvList = view.findViewById<TextView>(R.id.tvSecretsList)
        if (customCommands.isEmpty()) {
            tvList.text = "Список пуст. Добавьте первую команду!"
        } else {
            val sb = StringBuilder()
            for ((k, v) in customCommands) sb.append("🔑 Слово: ").append(k).append("\n➡️ Действие: ").append(v).append("\n\n")
            tvList.text = sb.toString()
        }

        view.findViewById<Button>(R.id.btnAddSecret).setOnClickListener { dialog.dismiss(); addSecretCommandDialog() }
        dialog.show()
    }

    private fun addSecretCommandDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        val inputKey = EditText(this).apply { hint = "Секретное слово" }
        val inputAction = EditText(this).apply { hint = "Что сделать" }
        layout.addView(inputKey); layout.addView(inputAction)

        AlertDialog.Builder(this)
            .setTitle("Новая секретная команда")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val k = inputKey.text.toString().lowercase().trim()
                val a = inputAction.text.toString().lowercase().trim()
                if (k.isNotEmpty() && a.isNotEmpty()) {
                    customCommands[k] = a
                    saveCustomCommands()
                    reply("Секретная команда '$k' добавлена!")
                }
            }.setNegativeButton("Отмена", null).show()
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
                PrefsManager.bubbleRunning = true
                Toast.makeText(this, "Пузырь включен!", Toast.LENGTH_SHORT).show()
            }
        } else {
            stopService(Intent(this, FloatingBubbleService::class.java))
            isBubbleRunning = false
            PrefsManager.bubbleRunning = false
            Toast.makeText(this, "Пузырь выключен!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoiceSettingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        val voices = arrayOf(
            "nova (женский, мягкий)", "shimmer (женский, звонкий)", "alloy (нейтральный)", "onyx (мужской, глубокий)", "echo (мужской, спокойный)", "fable (женский, с акцентом)"
        )
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
                PrefsManager.selectedVoice = selectedVoice
                reply("Голос изменен на $selectedVoice")
            }.setNegativeButton("Отмена", null).show()
    }

    private fun loadCustomCommands() {
        val prefs = getSharedPreferences("JarvisPrefs", Context.MODE_PRIVATE)
        val commandsStr = prefs.getString("custom_commands", "") ?: ""
        if (commandsStr.isNotEmpty()) {
            val items = commandsStr.split(";;")
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

    private fun startCallRecording() {
        try {
            recordFile = File(getExternalFilesDir(null), "call_record_${System.currentTimeMillis()}.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordFile?.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopCallRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            if (recordFile?.exists() == true) reply("Звонок записан: ${recordFile?.name}")
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupCallListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            tm.listen(object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK) {
                        if (isCallRecording) startCallRecording()
                    } else if (state == android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                        if (isCallRecording) {
                            stopCallRecording()
                            isCallRecording = false
                        }
                    }
                }
            }, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }
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

    private fun getCurrentLatitude(): Double? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            return loc?.latitude
        } catch (e: SecurityException) { return null }
    }

    private fun getCurrentLongitude(): Double? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            return loc?.longitude
        } catch (e: SecurityException) { return null }
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
            
            "В городе $name сейчас ${"%.0f".format(temp)} градусов. Ощущается как ${"%.0f".format(feels)}. Влажность $hum%."
        } catch (e: Exception) { null }
    }

    private fun askAiForButton(prompt: String): String? {
        try {
            val json = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply { 
                    put(JSONObject().apply { put("role", "system"); put("content", "Ты - голосовой ассистент. Сегодня ${java.text.SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(java.util.Date())}.") })
                    put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                })
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
        var pkg = installedAppsMap[search]
        if (pkg == null) pkg = installedAppsMap.entries.firstOrNull { it.key.contains(search) }?.value
        if (pkg == null) {
            val latinSearch = Translit.toLatin(search)
            pkg = installedAppsMap[latinSearch] ?: installedAppsMap.entries.firstOrNull { it.key.contains(latinSearch) }?.value
        }
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
        ChatManager.ask(this, prompt)
    }

    private fun animateBotReply(fullText: String) {
        val tv = TextView(this)
        try { tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) } catch(e: Exception){}
        tv.textSize = 15f; tv.setPadding(32, 24, 32, 24); tv.setTextColor(Color.WHITE)
        
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

        neuralTTS.speak(fullText, selectedVoice) {
            var index = 0
            val runnable = object : Runnable {
                override fun run() {
                    if (index <= fullText.length) {
                        tv.text = fullText.substring(0, index); index++
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        uiHandler.postDelayed(this, 50)
                    }
                }
            }
            uiHandler.post(runnable)
        }
    }

    private fun logUser(text: String) { runOnUiThread { addBubble(text, true) } }
    private fun reply(text: String) { 
        runOnUiThread { addBubble(text, false) }
        neuralTTS.speak(text, selectedVoice)
        val intent = Intent("BUBBLE_ANSWER")
        intent.putExtra("answer", text as String)
        sendBroadcast(intent)
    }
    
    private fun addBubble(text: String, isUser: Boolean) {
        val tv = TextView(this)
        try { tv.typeface = ResourcesCompat.getFont(this, R.font.manrope_regular) } catch(e: Exception){}
        tv.setTextColor(Color.WHITE)
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, text.length, 0)
        
        if (isUser) {
            tv.setBackgroundColor(Color.parseColor("#2A2A35"))
        } else {
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
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION)
        val notGranted = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (notGranted.isNotEmpty()) requestPerms.launch(notGranted.toTypedArray())
    }

    override fun onDestroy() { 
        try { unregisterReceiver(wakeWordReceiver); unregisterReceiver(miniChatReceiver) } catch (e: Exception) {}; 
        neuralTTS.destroy()
        super.onDestroy() 
    }
}
