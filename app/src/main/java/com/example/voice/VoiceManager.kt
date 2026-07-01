package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.Random
import kotlin.math.sin

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var webView: WebView? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // STT State Flows
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _speechResult = MutableStateFlow("")
    val speechResult: StateFlow<String> = _speechResult

    private val _listeningRms = MutableStateFlow(0f)
    val listeningRms: StateFlow<Float> = _listeningRms

    private val _sttError = MutableStateFlow<String?>(null)
    val sttError: StateFlow<String?> = _sttError

    // IndexedDB-backed Voice Transcripts Flow
    private val _indexedDbTranscripts = MutableStateFlow<List<IndexedDbTranscript>>(emptyList())
    val indexedDbTranscripts: StateFlow<List<IndexedDbTranscript>> = _indexedDbTranscripts

    // TTS State Flows
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    // Speech custom settings (Persisted via SharedPreferences)
    private val prefs = context.getSharedPreferences("zoya_voice_prefs", Context.MODE_PRIVATE)

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1.0f))
    val speechRate: StateFlow<Float> = _speechRate

    private val _speechPitch = MutableStateFlow(prefs.getFloat("speech_pitch", 1.00f))
    val speechPitch: StateFlow<Float> = _speechPitch

    private val _voiceGender = MutableStateFlow(prefs.getString("voice_gender", "Default") ?: "Default")
    val voiceGender: StateFlow<String> = _voiceGender

    private var sttCallback: ((String) -> Unit)? = null

    // RMS simulation loop
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var rmsRunnable: Runnable? = null

    init {
        // Initialize WebView and Text To Speech on the main thread
        handler.post {
            initWebView()
            textToSpeech = TextToSpeech(context, this)
        }
    }

    private fun initWebView() {
        try {
            // Pre-create the WebView cache directories that Chromium checks on startup to suppress "No such file or directory" errors
            try {
                val cacheDir = context.cacheDir
                if (cacheDir != null) {
                    val paths = listOf(
                        "WebView/Default/HTTP Cache/Code Cache/js",
                        "WebView/Default/HTTP Cache/Code Cache/wasm",
                        "app_webview/Default/HTTP Cache/Code Cache/js",
                        "app_webview/Default/HTTP Cache/Code Cache/wasm"
                    )
                    for (path in paths) {
                        val dir = java.io.File(cacheDir, path)
                        if (!dir.exists()) {
                            dir.mkdirs()
                        }
                    }
                    Log.d("VoiceManager", "Successfully pre-created WebView cache directories")
                }
            } catch (ex: Exception) {
                Log.w("VoiceManager", "Non-fatal error pre-creating cache directories", ex)
            }

            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                
                // Allow speech synthesis and mic audio captures in WebView
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources ?: arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    }
                }

                webViewClient = WebViewClient()

                addJavascriptInterface(WebSpeechBridge(), "AndroidBridge")
                loadUrl("file:///android_asset/speech.html")
            }
            Log.d("VoiceManager", "WebView-based Web Speech API Bridge initialized successfully")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error initializing speech bridge WebView", e)
            _sttError.value = "Failed to initialize Web Speech Bridge: ${e.localizedMessage}"
        }
    }

    // JS Bridge class to connect Web Speech API events back to Kotlin StateFlows
    inner class WebSpeechBridge {
        @JavascriptInterface
        fun onStart() {
            handler.post {
                _isListening.value = true
                _sttError.value = null
                startRmsSimulation()
                Log.d("VoiceManager", "WebSpeech: listening started")
            }
        }

        @JavascriptInterface
        fun onResult(text: String, isFinal: Boolean) {
            handler.post {
                _speechResult.value = text
                if (isFinal) {
                    sttCallback?.invoke(text)
                }
                Log.d("VoiceManager", "WebSpeech result: $text (isFinal=$isFinal)")
            }
        }

        @JavascriptInterface
        fun onEnd() {
            handler.post {
                _isListening.value = false
                stopRmsSimulation()
                Log.d("VoiceManager", "WebSpeech: listening stopped")
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            handler.post {
                Log.e("VoiceManager", "WebSpeech error: $error")
                // Suppress normal silent speech/audio timeouts to avoid annoying toasts
                if (error != "no-speech" && error != "aborted" && error != "audio-capture") {
                    _sttError.value = "Speech Error: $error"
                }
                _isListening.value = false
                stopRmsSimulation()
            }
        }

        @JavascriptInterface
        fun onTranscriptsLoaded(jsonStr: String) {
            handler.post {
                try {
                    val list = mutableListOf<IndexedDbTranscript>()
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            IndexedDbTranscript(
                                id = obj.optLong("id", 0L),
                                timestamp = obj.optLong("timestamp", 0L),
                                role = obj.optString("role", ""),
                                text = obj.optString("text", ""),
                                personality = obj.optString("personality", "Standard")
                            )
                        )
                    }
                    _indexedDbTranscripts.value = list
                    Log.d("VoiceManager", "Loaded ${list.size} transcripts from IndexedDB")
                } catch (e: Exception) {
                    Log.e("VoiceManager", "Error parsing IndexedDB transcripts", e)
                }
            }
        }
    }

    private fun startRmsSimulation() {
        stopRmsSimulation()
        rmsRunnable = object : Runnable {
            override fun run() {
                if (_isListening.value) {
                    // Generate natural voice level fluctuations (range: 1.0 to 8.5)
                    val base = 2.0f
                    val variance = random.nextFloat() * 6.5f
                    _listeningRms.value = base + variance
                    handler.postDelayed(this, 120) // Update every 120ms
                }
            }
        }
        rmsRunnable?.let { handler.post(it) }
    }

    private fun stopRmsSimulation() {
        rmsRunnable?.let { handler.removeCallbacks(it) }
        rmsRunnable = null
        _listeningRms.value = 0f
    }

    fun startListening(onResult: (String) -> Unit) {
        handler.post {
            try {
                sttCallback = onResult
                _sttError.value = null
                _speechResult.value = ""
                
                if (webView == null) {
                    initWebView()
                }
                
                webView?.evaluateJavascript("startRecognition();", null)
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to start speech recognition evaluation", e)
                _sttError.value = "Failed to start speech: ${e.localizedMessage}"
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        handler.post {
            try {
                webView?.evaluateJavascript("stopRecognition();", null)
            } catch (e: Exception) {
                Log.e("VoiceManager", "Failed to stop speech recognition evaluation", e)
            }
            _isListening.value = false
            stopRmsSimulation()
        }
    }

    // TTS Control
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isTtsInitialized || textToSpeech == null) {
            Log.e("VoiceManager", "TTS not initialized")
            return
        }

        // Stop any active speech
        stopSpeaking()

        _isSpeaking.value = true
        
        // Setup listener for completion
        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                playEndChime()
                onComplete?.invoke()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ZoyaUtterance")
        }

        // Play the subtle start chime asynchronously
        playStartChime()

        // Wait a micro-delay of 180ms before TTS plays so they don't overlap awkwardly
        handler.postDelayed({
            if (_isSpeaking.value) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ZoyaUtterance")
            }
        }, 180)
    }

    fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        _isSpeaking.value = false
    }

    /**
     * Programmatically synthesizes and plays a pristine dual-tone sinus chime
     * with a smooth fade envelope to avoid any clicking sounds. Run in a background thread.
     */
    private fun playChimeAsync(frequencies: DoubleArray, durationMs: Int) {
        Thread {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / numSamples
                
                // Attack-sustain-decay envelope
                val envelope = when {
                    progress < 0.15 -> progress / 0.15
                    progress > 0.65 -> (1.0 - progress) / 0.35
                    else -> 1.0
                }
                
                var sampleVal = 0.0
                for (freq in frequencies) {
                    sampleVal += sin(2.0 * Math.PI * freq * t)
                }
                // Convert to 16-bit PCM amplitude with a soft volume coefficient (7% for highly subtle background cue)
                sampleVal = (sampleVal / frequencies.size) * 32767.0 * envelope * 0.07
                buffer[i] = sampleVal.toInt().toShort()
            }
            
            try {
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                    
                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                
                // Allow static track to finish playing before freeing native handles
                Thread.sleep(durationMs.toLong() + 50L)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error in chime synth thread", e)
            }
        }.start()
    }

    fun playStartChime() {
        // Pristine major triad bell chime (C5 & E5) for 220ms
        playChimeAsync(doubleArrayOf(523.25, 659.25), 220)
    }

    fun playEndChime() {
        // Soft descending warm resolve chime (G4 & C5) for 260ms
        playChimeAsync(doubleArrayOf(392.00, 523.25), 260)
    }

    fun updateSpeechRate(rate: Float) {
        _speechRate.value = rate
        prefs.edit().putFloat("speech_rate", rate).apply()
        applyVoiceSettings()
    }

    fun updateSpeechPitch(pitch: Float) {
        _speechPitch.value = pitch
        prefs.edit().putFloat("speech_pitch", pitch).apply()
        applyVoiceSettings()
    }

    fun updateVoiceGender(gender: String) {
        _voiceGender.value = gender
        prefs.edit().putString("voice_gender", gender).apply()
        applyVoiceSettings()
    }

    fun applyVoiceSettings() {
        val tts = textToSpeech ?: return
        if (!isTtsInitialized) return

        val currentRate = _speechRate.value
        val currentPitch = _speechPitch.value
        val currentGender = _voiceGender.value

        tts.setSpeechRate(currentRate)
        tts.setPitch(currentPitch)

        try {
            val voices = tts.voices
            if (voices != null && voices.isNotEmpty()) {
                val defaultLocale = Locale.getDefault()
                val localeVoices = voices.filter { it.locale.language == defaultLocale.language }
                
                val selectedVoice = when (currentGender) {
                    "Female" -> {
                        localeVoices.firstOrNull { it.name.lowercase().contains("female") || it.name.lowercase().contains("f") }
                    }
                    "Male" -> {
                        localeVoices.firstOrNull { it.name.lowercase().contains("male") || it.name.lowercase().contains("m") }
                    }
                    else -> null
                }
                
                if (selectedVoice != null) {
                    tts.voice = selectedVoice
                    Log.d("VoiceManager", "Applied voice: ${selectedVoice.name}")
                } else {
                    // Fallback pitch adjustments for engines without multiple voices
                    if (currentGender == "Male") {
                        tts.setPitch(currentPitch * 0.82f)
                    } else if (currentGender == "Female") {
                        tts.setPitch(currentPitch * 1.22f)
                    }
                    Log.d("VoiceManager", "No explicit voice matched for $currentGender, applied pitch offsets")
                }
            } else {
                if (currentGender == "Male") {
                    tts.setPitch(currentPitch * 0.82f)
                } else if (currentGender == "Female") {
                    tts.setPitch(currentPitch * 1.22f)
                }
                Log.d("VoiceManager", "No voices available, applied pitch offsets")
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error applying voice settings", e)
        }
    }

    // TTS Initializer
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceManager", "Language is not supported or missing data")
            } else {
                isTtsInitialized = true
                applyVoiceSettings()
                Log.d("VoiceManager", "TTS initialization successful")
            }
        } else {
            Log.e("VoiceManager", "TTS initialization failed")
        }
    }

    fun release() {
        handler.post {
            try {
                stopRmsSimulation()
                webView?.destroy()
                webView = null
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error destroying speech WebView", e)
            }
        }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // Public API to interact with the WebView's IndexedDB storage layer
    fun loadTranscriptsFromIndexedDb() {
        handler.post {
            webView?.evaluateJavascript("fetchTranscriptsFromDB();", null)
        }
    }

    fun clearTranscriptsFromIndexedDb() {
        handler.post {
            webView?.evaluateJavascript("clearTranscriptsFromDB();", null)
        }
    }

    fun saveTranscriptToIndexedDb(role: String, text: String, personality: String) {
        handler.post {
            val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
            val escapedPersonality = personality.replace("'", "\\'")
            webView?.evaluateJavascript("saveTranscriptToDB('$role', '$escapedText', '$escapedPersonality');", null)
        }
    }

    fun setWebSpeechPersonality(personality: String) {
        handler.post {
            val escapedPersonality = personality.replace("'", "\\'")
            webView?.evaluateJavascript("setPersonality('$escapedPersonality');", null)
        }
    }
}

// Data class mapping IndexedDB transcript records
data class IndexedDbTranscript(
    val id: Long,
    val timestamp: Long,
    val role: String,
    val text: String,
    val personality: String
)
