package com.example.bjjtimerpro

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var webViewOverlay: View
    private lateinit var timerContainer: View
    private lateinit var timerDisplay: TextView
    private lateinit var roundInfo: TextView
    private lateinit var txtFinishTime: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnReset: Button
    private lateinit var btnShowSlides: ImageButton
    private lateinit var quickSettings: View
    private lateinit var btnSettings: ImageButton
    private lateinit var sideQuickSets: View
    private lateinit var btnToggleWarning: ImageButton

    private lateinit var txtRoundLength: TextView
    private lateinit var txtRestTime: TextView
    private lateinit var txtNumRounds: TextView

    private var roundLengthSec: Int = 300 // 5 minutes
    private var restTimeSec: Int = 60
    private var totalRounds: Int = 0
    
    private var currentRound: Int = 1
    private var isResting: Boolean = false
    private var isTimerRunning: Boolean = false
    private var isInitialCountdown: Boolean = false
    private var isWarningEnabled: Boolean = true
    private var timeLeftMs: Long = roundLengthSec * 1000L

    private var countDownTimer: CountDownTimer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var warningPlayer: MediaPlayer? = null

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        if (!isTimerRunning) {
            showSlides()
        }
    }
    private val INACTIVITY_DELAY = 15 * 60 * 1000L // 15 minutes

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (webView.visibility == View.VISIBLE) {
                webView.reload()
                refreshHandler.postDelayed(this, 15 * 60 * 1000L)
            }
        }
    }

    private var slidesUrl = "https://docs.google.com/presentation/d/e/2PACX-1vRBqlDI8ffCQM2eoT1bnOyN_HSvbZK_qvtZ3bIeqFTxvMa4k7RTJ7OPQfWnxc3QeA/pub?start=false&loop=false&delayms=5000"
    
    private val THEME_COLORS = intArrayOf(
        Color.parseColor("#2196F3"), // Classic Blue
        Color.parseColor("#F44336"), // Fire Red
        Color.parseColor("#4CAF50"), // Forest Green
        Color.parseColor("#9C27B0")  // Royal Purple
    )
    private var currentThemeIndex = 0

    private var httpServer: AndroidHttpServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadPreferences()

        webView = findViewById(R.id.webView)
        webViewOverlay = findViewById(R.id.webViewOverlay)
        timerContainer = findViewById(R.id.timerContainer)
        timerDisplay = findViewById(R.id.timerDisplay)
        roundInfo = findViewById(R.id.roundInfo)
        txtFinishTime = findViewById(R.id.txtFinishTime)
        btnStartPause = findViewById(R.id.btnStartPause)
        btnReset = findViewById(R.id.btnReset)
        btnShowSlides = findViewById(R.id.btnShowSlides)
        quickSettings = findViewById(R.id.quickSettings)
        btnSettings = findViewById(R.id.btnSettings)
        sideQuickSets = findViewById(R.id.sideQuickSets)
        btnToggleWarning = findViewById(R.id.btnToggleWarning)

        txtRoundLength = findViewById(R.id.txtRoundLength)
        txtRestTime = findViewById(R.id.txtRestTime)
        txtNumRounds = findViewById(R.id.txtNumRounds)

        setupWebView()
        setupButtons()
        applyTheme()
        updateQuickSettingsUI()
        updateTimerText()
        updateRoundInfo()
        updateUIState()
        updateWarningIcon()

        resetInactivityTimer()
        startHttpServer()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("BJJTimerPrefs", Context.MODE_PRIVATE)
        slidesUrl = prefs.getString("slidesUrl", slidesUrl) ?: slidesUrl
        currentThemeIndex = prefs.getInt("themeIndex", 0)
        isWarningEnabled = prefs.getBoolean("warningEnabled", true)
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("BJJTimerPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("slidesUrl", slidesUrl)
            putInt("themeIndex", currentThemeIndex)
            putBoolean("warningEnabled", isWarningEnabled)
            apply()
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(slidesUrl)
        
        webViewOverlay.setOnClickListener {
            hideSlides()
        }
    }

    private fun setupButtons() {
        btnStartPause.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                if (!isResting && currentRound == 1 && timeLeftMs == roundLengthSec * 1000L) {
                    startInitialCountdown()
                } else {
                    startTimer()
                }
            }
            resetInactivityTimer()
        }

        btnReset.setOnClickListener {
            resetTimer()
            resetInactivityTimer()
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnShowSlides.setOnClickListener {
            showSlides()
        }

        btnToggleWarning.setOnClickListener {
            isWarningEnabled = !isWarningEnabled
            savePreferences()
            updateWarningIcon()
            val status = if (isWarningEnabled) "Enabled" else "Disabled"
            Toast.makeText(this, "Warnings $status", Toast.LENGTH_SHORT).show()
        }

        // Side Quick Set Buttons
        findViewById<Button>(R.id.btnQuick1m).setOnClickListener { setRoundLength(60) }
        findViewById<Button>(R.id.btnQuick3m).setOnClickListener { setRoundLength(180) }
        findViewById<Button>(R.id.btnQuick5m).setOnClickListener { setRoundLength(300) }
        findViewById<Button>(R.id.btnQuick8m).setOnClickListener { setRoundLength(480) }
        findViewById<Button>(R.id.btnQuick10m).setOnClickListener { setRoundLength(600) }

        // Round Length +/- (10s increments)
        findViewById<Button>(R.id.btnRoundMinus).setOnClickListener {
            if (roundLengthSec > 10) {
                roundLengthSec -= 10
                updateQuickSettingsUI()
                if (!isTimerRunning) resetTimer()
            }
            resetInactivityTimer()
        }
        findViewById<Button>(R.id.btnRoundPlus).setOnClickListener {
            roundLengthSec += 10
            updateQuickSettingsUI()
            if (!isTimerRunning) resetTimer()
            resetInactivityTimer()
        }

        // Rest Time +/-
        findViewById<Button>(R.id.btnRestMinus).setOnClickListener {
            if (restTimeSec >= 5) {
                restTimeSec -= 5
                updateQuickSettingsUI()
            }
            resetInactivityTimer()
        }
        findViewById<Button>(R.id.btnRestPlus).setOnClickListener {
            restTimeSec += 5
            updateQuickSettingsUI()
            resetInactivityTimer()
        }

        // Number of Rounds +/-
        findViewById<Button>(R.id.btnRoundsMinus).setOnClickListener {
            if (totalRounds > 0) {
                totalRounds--
                updateQuickSettingsUI()
                if (!isTimerRunning) updateRoundInfo()
            }
            resetInactivityTimer()
        }
        findViewById<Button>(R.id.btnRoundsPlus).setOnClickListener {
            totalRounds++
            updateQuickSettingsUI()
            if (!isTimerRunning) updateRoundInfo()
            resetInactivityTimer()
        }
    }

    private fun setRoundLength(seconds: Int) {
        if (!isTimerRunning) {
            roundLengthSec = seconds
            updateQuickSettingsUI()
            resetTimer()
            resetInactivityTimer()
        }
    }

    private fun updateWarningIcon() {
        val iconRes = if (isWarningEnabled) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode
        btnToggleWarning.setImageResource(iconRes)
    }

    private fun getDarkerColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.6f // Reduce brightness by 40%
        return Color.HSVToColor(hsv)
    }

    private fun applyTheme() {
        val color = THEME_COLORS[currentThemeIndex]
        val darkerColor = getDarkerColor(color)
        
        // High-contrast state list for focused elements
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf()
        )
        
        // Use darker theme color for focus
        val colors = intArrayOf(
            darkerColor, // Focused
            Color.LTGRAY, // Pressed
            color         // Default: Theme color
        )
        val stateList = ColorStateList(states, colors)
        
        btnStartPause.backgroundTintList = stateList
        btnReset.backgroundTintList = stateList
        
        // Apply to side preset buttons
        findViewById<Button>(R.id.btnQuick1m).backgroundTintList = stateList
        findViewById<Button>(R.id.btnQuick3m).backgroundTintList = stateList
        findViewById<Button>(R.id.btnQuick5m).backgroundTintList = stateList
        findViewById<Button>(R.id.btnQuick8m).backgroundTintList = stateList
        findViewById<Button>(R.id.btnQuick10m).backgroundTintList = stateList
        
        // High-contrast state list for adjust buttons (+/-)
        val adjustColors = intArrayOf(
            darkerColor, // Focused
            Color.LTGRAY, // Pressed
            Color.parseColor("#44888888") // Default: Semi-transparent grey
        )
        val adjustStateList = ColorStateList(states, adjustColors)
        
        findViewById<Button>(R.id.btnRoundMinus).backgroundTintList = adjustStateList
        findViewById<Button>(R.id.btnRoundPlus).backgroundTintList = adjustStateList
        findViewById<Button>(R.id.btnRestMinus).backgroundTintList = adjustStateList
        findViewById<Button>(R.id.btnRestPlus).backgroundTintList = adjustStateList
        findViewById<Button>(R.id.btnRoundsMinus).backgroundTintList = adjustStateList
        findViewById<Button>(R.id.btnRoundsPlus).backgroundTintList = adjustStateList

        // Use darker theme color for image buttons (icons) when focused
        val iconColors = intArrayOf(
            darkerColor,      // Focused
            Color.LTGRAY,     // Pressed
            color             // Default
        )
        val iconStateList = ColorStateList(states, iconColors)
        btnSettings.imageTintList = iconStateList
        btnShowSlides.imageTintList = iconStateList
        btnToggleWarning.imageTintList = iconStateList
        
        updateTimerText()
        updateRoundInfo()
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Settings")
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val editUrl = view.findViewById<EditText>(R.id.editSlidesUrl)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupThemes)
        val btnQrSync = view.findViewById<Button>(R.id.btnQrSync)

        editUrl.setText(slidesUrl)
        
        btnQrSync.setOnClickListener {
            showQrDialog()
        }

        // Select current theme radio button
        when (currentThemeIndex) {
            0 -> radioGroup.check(R.id.radioTheme1)
            1 -> radioGroup.check(R.id.radioTheme2)
            2 -> radioGroup.check(R.id.radioTheme3)
            3 -> radioGroup.check(R.id.radioTheme4)
        }

        builder.setView(view)
        builder.setPositiveButton("Save") { _, _ ->
            val newUrl = editUrl.text.toString()
            val newThemeIndex = when (radioGroup.checkedRadioButtonId) {
                R.id.radioTheme1 -> 0
                R.id.radioTheme2 -> 1
                R.id.radioTheme3 -> 2
                R.id.radioTheme4 -> 3
                else -> 0
            }
            
            if (newUrl.isNotEmpty() && newUrl != slidesUrl) {
                slidesUrl = newUrl
                webView.loadUrl(slidesUrl)
            }
            
            currentThemeIndex = newThemeIndex
            savePreferences()
            applyTheme()
        }
        builder.setNegativeButton("Cancel", null)
        
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.show()
    }

    private fun showQrDialog() {
        val ipAddress = getIpAddress()
        if (ipAddress == null) {
            AlertDialog.Builder(this)
                .setMessage("Could not find local IP address. Ensure this device is connected to the gym Wi-Fi.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val serverUrl = "http://$ipAddress:8080/"
        val qrBitmap = generateQrCode(serverUrl)

        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_settings, null)
        val imgQrCode = dialogView.findViewById<ImageView>(R.id.imgQrCode)
        val txtServerAddress = dialogView.findViewById<TextView>(R.id.txtServerAddress)

        if (qrBitmap != null) {
            imgQrCode.setImageBitmap(qrBitmap)
        }
        
        txtServerAddress.text = "1. Connect phone to the SAME Wi-Fi\n2. Scan code above\n3. Paste URL on phone\n\nDirect Link: $serverUrl"

        AlertDialog.Builder(this)
            .setTitle("QR Sync")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun getIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val list = mutableListOf<NetworkInterface>()
            while (interfaces.hasMoreElements()) list.add(interfaces.nextElement())
            
            list.sortBy { ni -> 
                if (ni.name.startsWith("wlan") || ni.name.startsWith("eth")) 0 else 1 
            }

            for (ni in list) {
                if (ni.name.startsWith("tun") || ni.name.startsWith("ppp") || 
                    ni.name.startsWith("p2p") || ni.name.startsWith("dummy")) continue
                
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BJJTimer", "Error getting IP address", e)
        }
        return null
    }

    private fun generateQrCode(text: String): Bitmap? {
        val writer = QRCodeWriter()
        return try {
            val hints = mutableMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 1
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e("BJJTimer", "Error generating QR code", e)
            null
        }
    }

    private fun updateQuickSettingsUI() {
        txtRoundLength.text = formatTime(roundLengthSec * 1000L)
        txtRestTime.text = formatTime(restTimeSec * 1000L)
        txtNumRounds.text = if (totalRounds == 0) "∞" else totalRounds.toString()
    }

    private fun updateTimerText() {
        if (isInitialCountdown) return 

        timerDisplay.text = formatTime(timeLeftMs)
        
        // Change color based on rest vs work
        // Force Red for rest period, regardless of theme
        var color = if (isResting) Color.RED else THEME_COLORS[currentThemeIndex]
        
        // Warning color for last 10 seconds of round (using yellow for visibility if rest is red)
        if (!isResting && timeLeftMs < 10000 && timeLeftMs > 0) {
            color = Color.YELLOW
        }

        timerDisplay.setTextColor(color)
        
        updateFinishTime()
    }

    private fun updateRoundInfo() {
        if (isInitialCountdown) {
            roundInfo.text = "GET READY"
            return
        }
        val roundsText = if (totalRounds == 0) "Round $currentRound" else "Round $currentRound / $totalRounds"
        roundInfo.text = if (isResting) "RESTING" else roundsText
    }

    private fun updateFinishTime() {
        if (!isTimerRunning) {
            txtFinishTime.visibility = View.GONE
            return
        }

        var remainingMs = timeLeftMs
        
        if (totalRounds > 0 && currentRound <= totalRounds) {
            val roundsLeft = totalRounds - currentRound
            if (isResting) {
                // remaining rest + round 2 work + rest 2 + ... + round N work
                remainingMs = timeLeftMs + roundsLeft * roundLengthSec * 1000L + (roundsLeft - 1).coerceAtLeast(0) * restTimeSec * 1000L
            } else {
                // remaining work + rest 1 + round 2 work + ... + round N work
                remainingMs = timeLeftMs + roundsLeft * (roundLengthSec + restTimeSec) * 1000L
            }
        } else if (totalRounds == 0) {
            txtFinishTime.visibility = View.GONE
            return
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MILLISECOND, remainingMs.toInt())
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        txtFinishTime.text = "Finish at ${sdf.format(calendar.time)}"
        txtFinishTime.visibility = View.VISIBLE
    }

    private fun updateUIState() {
        if (isTimerRunning) {
            timerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 200f)
            quickSettings.visibility = View.GONE
            btnShowSlides.visibility = View.GONE
            btnSettings.visibility = View.GONE
            sideQuickSets.visibility = View.GONE
        } else {
            timerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
            quickSettings.visibility = View.VISIBLE
            btnShowSlides.visibility = View.VISIBLE
            btnSettings.visibility = View.VISIBLE
            sideQuickSets.visibility = View.VISIBLE
        }
        updateFinishTime()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun startInitialCountdown() {
        isTimerRunning = true
        isInitialCountdown = true
        btnStartPause.text = "PAUSE"
        
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                timerDisplay.text = seconds.toString()
                timerDisplay.setTextColor(Color.WHITE)
                roundInfo.text = "GET READY"
            }

            override fun onFinish() {
                isInitialCountdown = false
                playAlarm()
                startTimer()
            }
        }.start()
        
        hideSlides()
        updateUIState()
    }

    private fun startTimer() {
        isTimerRunning = true
        btnStartPause.text = "PAUSE"
        
        updateRoundInfo()
        updateTimerText()
        
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeLeftMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                
                // Check for warning beeps
                if (isWarningEnabled) {
                    val secondsLeft = (timeLeftMs / 1000).toInt()
                    if (isResting && secondsLeft == 10) {
                        playWarning()
                    } else if (!isResting && secondsLeft == 30) {
                        playWarning()
                    }
                }
                
                updateTimerText()
            }

            override fun onFinish() {
                playAlarm()
                if (isResting) {
                    isResting = false
                    currentRound++
                    timeLeftMs = roundLengthSec * 1000L
                } else {
                    if (totalRounds > 0 && currentRound >= totalRounds) {
                        resetTimer()
                        return
                    }
                    isResting = true
                    timeLeftMs = restTimeSec * 1000L
                }
                updateRoundInfo()
                startTimer()
            }
        }.start()
        
        hideSlides()
        updateUIState()
    }

    private fun pauseTimer() {
        isTimerRunning = false
        isInitialCountdown = false
        btnStartPause.text = "START"
        countDownTimer?.cancel()
        updateUIState()
        updateTimerText()
        updateRoundInfo()
    }

    private fun resetTimer() {
        pauseTimer()
        isResting = false
        isInitialCountdown = false
        currentRound = 1
        timeLeftMs = roundLengthSec * 1000L
        updateTimerText()
        updateRoundInfo()
        updateUIState()
    }

    private fun playAlarm() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, R.raw.bell)
        mediaPlayer?.start()
    }

    private fun playWarning() {
        warningPlayer?.release()
        warningPlayer = MediaPlayer.create(this, R.raw.beep)
        warningPlayer?.start()
    }

    private fun showSlides() {
        webView.visibility = View.VISIBLE
        webViewOverlay.visibility = View.VISIBLE
        timerContainer.visibility = View.GONE
        quickSettings.visibility = View.GONE
        btnShowSlides.visibility = View.GONE
        
        refreshHandler.postDelayed(refreshRunnable, 15 * 60 * 1000L)
        
        webViewOverlay.requestFocus()
    }

    private fun hideSlides() {
        webView.visibility = View.GONE
        webViewOverlay.visibility = View.GONE
        timerContainer.visibility = View.VISIBLE
        
        updateUIState()
        
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_DELAY)
    }

    private fun startHttpServer() {
        try {
            httpServer = AndroidHttpServer(8080)
            httpServer?.start()
        } catch (e: Exception) {
            Log.e("BJJTimer", "Could not start server", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        mediaPlayer?.release()
        warningPlayer?.release()
        httpServer?.stop()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (webView.visibility == View.VISIBLE) {
            // Intercept any key except volume buttons to return to timer mode
            if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    hideSlides()
                }
                return true // Consume both DOWN and UP actions
            }
        }
        return super.dispatchKeyEvent(event)
    }

    inner class AndroidHttpServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val params = session.parameters
            val newUrl = params["url"]?.get(0)
            
            if (newUrl != null) {
                runOnUiThread {
                    slidesUrl = newUrl
                    webView.loadUrl(slidesUrl)
                    savePreferences()
                    Toast.makeText(this@MainActivity, "URL Updated", Toast.LENGTH_SHORT).show()
                }
                return newFixedLengthResponse("URL Updated Successfully!")
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>BJJ Timer Sync</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                        body { font-family: sans-serif; padding: 20px; text-align: center; }
                        input { width: 100%; padding: 10px; margin: 10px 0; box-sizing: border-box; }
                        button { padding: 10px 20px; background: #2196F3; color: white; border: none; border-radius: 4px; }
                    </style>
                </head>
                <body>
                    <h2>Update Slides URL</h2>
                    <form action="/" method="get">
                        <input type="text" name="url" placeholder="Paste Google Slides URL here" required>
                        <br>
                        <button type="submit">Update Timer</button>
                    </form>
                </body>
                </html>
            """.trimIndent()
            
            return newFixedLengthResponse(html)
        }
    }
}
