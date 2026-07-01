package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.repository.ZoyaRepository
import com.example.voice.VoiceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ZoyaViewModel(
    private val repository: ZoyaRepository,
    val voiceManager: VoiceManager
) : ViewModel() {

    val apiKey: String = BuildConfig.GEMINI_API_KEY

    // UI state for sessions
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active session state
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession

    // AI active typing animation indicator
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    // Voice Auto-Read (TTS toggle)
    private val _isVoiceAutoReadEnabled = MutableStateFlow(true)
    val isVoiceAutoReadEnabled: StateFlow<Boolean> = _isVoiceAutoReadEnabled

    // Selected AI personality mode
    private val _currentPersonality = MutableStateFlow("Friendly")
    val currentPersonality: StateFlow<String> = _currentPersonality

    // TTS speech settings flows from VoiceManager
    val speechRate: StateFlow<Float> = voiceManager.speechRate
    val speechPitch: StateFlow<Float> = voiceManager.speechPitch
    val voiceGender: StateFlow<String> = voiceManager.voiceGender

    fun setSpeechRate(rate: Float) {
        voiceManager.updateSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        voiceManager.updateSpeechPitch(pitch)
    }

    fun setVoiceGender(gender: String) {
        voiceManager.updateVoiceGender(gender)
    }

    // Trigger event to open a URL in browser via custom function calling
    private val _openWebsiteEvent = MutableStateFlow<String?>(null)
    val openWebsiteEvent: StateFlow<String?> = _openWebsiteEvent

    fun clearOpenWebsiteEvent() {
        _openWebsiteEvent.value = null
    }

    // Reactive messages list for current session
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSession
        .flatMapLatest { session ->
            if (session != null) {
                repository.getMessagesForSession(session.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatically fetch or create a session on launch
        viewModelScope.launch {
            repository.allSessions.collectFirst { list ->
                if (list.isNotEmpty()) {
                    selectSession(list.first())
                } else {
                    createNewSession("New Chat", "Friendly")
                }
            }
        }
    }

    // Helper to get first item from flow
    private suspend fun <T> Flow<T>.collectFirst(action: suspend (T) -> Unit) {
        take(1).collect(action)
    }

    fun selectSession(session: ChatSession) {
        _currentSession.value = session
        _currentPersonality.value = session.aiPersonality
        voiceManager.stopSpeaking()
    }

    fun createNewSession(title: String, personality: String) {
        viewModelScope.launch {
            voiceManager.stopSpeaking()
            val id = repository.createSession(title, personality)
            val newSession = ChatSession(id = id, title = title, aiPersonality = personality)
            _currentSession.value = newSession
            _currentPersonality.value = personality
        }
    }

    fun updatePersonality(personality: String) {
        _currentPersonality.value = personality
        val current = _currentSession.value
        if (current != null) {
            viewModelScope.launch {
                val updatedSession = current.copy(aiPersonality = personality)
                repository.createSession(updatedSession.title, updatedSession.aiPersonality) // Replace existing
                _currentSession.value = updatedSession
            }
        }
    }

    fun toggleVoiceAutoRead() {
        _isVoiceAutoReadEnabled.value = !_isVoiceAutoReadEnabled.value
        if (!_isVoiceAutoReadEnabled.value) {
            voiceManager.stopSpeaking()
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            voiceManager.stopSpeaking()
            repository.deleteSession(session)
            if (_currentSession.value?.id == session.id) {
                _currentSession.value = null
                // Pick the next available session or create one
                repository.allSessions.collectFirst { list ->
                    val filtered = list.filter { it.id != session.id }
                    if (filtered.isNotEmpty()) {
                        selectSession(filtered.first())
                    } else {
                        createNewSession("General Assistant", "Friendly")
                    }
                }
            }
        }
    }

    fun sendMessage(text: String, isVoice: Boolean = false) {
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            // Ensure we have an active session
            var session = _currentSession.value
            if (session == null) {
                val sessionId = repository.createSession("Chat Summary", _currentPersonality.value)
                session = ChatSession(id = sessionId, title = "Chat Summary", aiPersonality = _currentPersonality.value)
                _currentSession.value = session
            }

            // 1. Save User Message to database
            val userMsg = ChatMessage(
                sessionId = session.id,
                role = "user",
                text = text,
                isVoiceInput = isVoice
            )
            repository.insertMessage(userMsg)

            // Save text inputs to the IndexedDB voice transcript store too (voice inputs are saved by WebView automatically)
            if (!isVoice) {
                voiceManager.saveTranscriptToIndexedDb("user", text, _currentPersonality.value)
            }

            // 2. Set thinking/typing state
            _isTyping.value = true

            // Fetch history from DB for context awareness
            val msgHistory = messages.value + userMsg

            // 3. Request Gemini API Response
            val aiResponseText = repository.getAIResponse(
                apiKey = apiKey,
                sessionId = session.id,
                history = msgHistory,
                personality = _currentPersonality.value
            )

            _isTyping.value = false

            // Extract custom tool call if present (e.g. [TOOL_CALL: openWebsite, url: https://...]) or [TOOL_CALL: searchWeb, query: ...]
            val openWebsiteRegex = Regex("\\[TOOL_CALL:\\s*openWebsite,\\s*url:\\s*([^]]+)\\]")
            val searchWebRegex = Regex("\\[TOOL_CALL:\\s*searchWeb,\\s*query:\\s*([^]]+)\\]")
            var parsedText = aiResponseText

            val openMatch = openWebsiteRegex.find(aiResponseText)
            val searchMatch = searchWebRegex.find(aiResponseText)

            if (openMatch != null) {
                val url = openMatch.groupValues[1].trim()
                _openWebsiteEvent.value = url
                parsedText = aiResponseText.replace(openMatch.value, "").trim()
            } else if (searchMatch != null) {
                val query = searchMatch.groupValues[1].trim()
                try {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    _openWebsiteEvent.value = "https://www.google.com/search?q=$encodedQuery"
                } catch (e: Exception) {
                    _openWebsiteEvent.value = "https://www.google.com/search?q=$query"
                }
                parsedText = aiResponseText.replace(searchMatch.value, "").trim()
            }

            // 4. Save AI Response to database
            val assistantMsg = ChatMessage(
                sessionId = session.id,
                role = "model",
                text = parsedText
            )
            repository.insertMessage(assistantMsg)

            // Save Zoya's assistant response to the IndexedDB transcript archive
            voiceManager.saveTranscriptToIndexedDb("zoya", parsedText, _currentPersonality.value)

            // Auto-rename chat title dynamically if it is still a default name
            if (session.title == "New Chat" || session.title == "General Assistant" || session.title == "Chat Summary") {
                val summaryPrompt = "Condense this query into a highly engaging, super-short title of 2-3 words: '$text'"
                val condensedTitle = repository.getAIResponse(apiKey, session.id, listOf(ChatMessage(sessionId=session.id, role="user", text=summaryPrompt)), _currentPersonality.value)
                    .replace("\"", "").trim()
                val cleanTitle = if (condensedTitle.length > 25) condensedTitle.take(22) + "..." else condensedTitle
                if (cleanTitle.isNotEmpty() && !cleanTitle.startsWith("Error")) {
                    val renamedSession = session.copy(title = cleanTitle)
                    repository.createSession(renamedSession.title, renamedSession.aiPersonality) // Overwrites in Room
                    _currentSession.value = renamedSession
                }
            }

            // 5. Trigger Text To Speech if enabled
            if (_isVoiceAutoReadEnabled.value && !parsedText.startsWith("Error:")) {
                // Strip markdown backticks, asterisks, etc. for a clean voice-out
                val cleanVoiceText = parsedText
                    .replace(Regex("[\\*\\_\\`\\#\\-\\>]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                voiceManager.speak(cleanVoiceText)
            }
        }
    }

    // Trigger mic/audio voice flow
    fun toggleVoiceListening() {
        if (voiceManager.isSpeaking.value) {
            voiceManager.stopSpeaking()
        }

        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening { text ->
                if (text.isNotEmpty()) {
                    sendMessage(text, isVoice = true)
                }
            }
        }
    }

    fun speakMessage(text: String) {
        val cleanText = text.replace(Regex("[\\*\\_\\`\\#\\-\\>]"), "").trim()
        voiceManager.speak(cleanText)
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}

class ZoyaViewModelFactory(
    private val repository: ZoyaRepository,
    private val voiceManager: VoiceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZoyaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ZoyaViewModel(repository, voiceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
