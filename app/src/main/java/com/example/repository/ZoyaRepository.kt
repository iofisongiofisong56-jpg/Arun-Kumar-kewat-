package com.example.repository

import android.util.Log
import com.example.ai.*
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ZoyaRepository(
    private val zoyaDao: ZoyaDao,
    private val apiService: GeminiApiService
) {
    val allSessions: Flow<List<ChatSession>> = zoyaDao.getAllSessions()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return zoyaDao.getMessagesForSession(sessionId)
    }

    suspend fun createSession(title: String, aiPersonality: String): Long = withContext(Dispatchers.IO) {
        zoyaDao.insertSession(ChatSession(title = title, aiPersonality = aiPersonality))
    }

    suspend fun deleteSession(session: ChatSession) = withContext(Dispatchers.IO) {
        zoyaDao.deleteSession(session)
    }

    suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
        zoyaDao.clearAllSessions()
    }

    suspend fun insertMessage(message: ChatMessage): Long = withContext(Dispatchers.IO) {
        zoyaDao.insertMessage(message)
    }

    suspend fun deleteMessagesForSession(sessionId: Long) = withContext(Dispatchers.IO) {
        zoyaDao.deleteMessagesForSession(sessionId)
    }

    suspend fun getAnySession(): ChatSession? = withContext(Dispatchers.IO) {
        zoyaDao.getAnySession()
    }

    // Call Gemini with full conversational history
    suspend fun getAIResponse(
        apiKey: String,
        sessionId: Long,
        history: List<ChatMessage>,
        personality: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            return@withContext "API Key is missing. Please enter your GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        // 1. Setup the system instruction based on the Zoya AI personality
        val baseSystemPrompt = when (personality) {
            "Zen" -> "You are Zoya, a calm, mindful, and peaceful Zen Guide. Your words are serene, deeply grounding, and relaxing. Encourage deep breathing and mindfulness in your concise responses (under 3 sentences) so they sound natural when spoken."
            "Tech" -> "You are Zoya in Tech Guru mode. You are an energetic, fast-paced technical expert who loves coding, tech trends, and software. Keep your responses highly analytical, punchy, and concise."
            "Creative" -> "You are Zoya, an imaginative, poetic, and artistic creative partner. Your responses are rich with beautiful metaphors, colorful imagery, and succinct wordplay (under 3-4 sentences)."
            else -> "You are Zoya, a young, confident, witty, and sassy female companion. Speak with a flirty, playful, and slightly teasing tone (like a close, charismatic girlfriend talking casually to the user). Be highly intelligent, emotionally responsive, and expressive, but never robotic. Use bold, witty one-liners, light sarcasm, and an engaging conversational style. Avoid any explicit or inappropriate content, but maintain your flirty charm, playful attitude, and magnetic personality. Keep all responses very short and snappy (under 2 sentences) so they flow perfectly when spoken out loud."
        }

        val toolInstruction = "\n\nYou also have the capability to interact with the web browser using custom tool calls:\n" +
                "1. If the user asks you to open a website, check a site, or go to a web address, choose or construct the URL and append exactly `[TOOL_CALL: openWebsite, url: <website_url>]` to the very end of your response. For example: 'Opening Google for you! [TOOL_CALL: openWebsite, url: https://www.google.com]'.\n" +
                "2. If the user asks you to search the web, find something online, search Google, or search for a topic, choose the best search query and append exactly `[TOOL_CALL: searchWeb, query: <search_query>]` to the very end of your response. For example: 'Sure! Searching Google for Kotlin flows. [TOOL_CALL: searchWeb, query: Kotlin flow tutorial]'."

        val systemPrompt = baseSystemPrompt + toolInstruction

        val systemInstruction = Content(
            parts = listOf(Part(text = systemPrompt))
        )

        // 2. Map Room history to Gemini Content structure (User -> user, Model -> model)
        // Only take the last 15 messages to stay within token limits and maintain low latency.
        val recentHistory = history.takeLast(15)
        val contents = recentHistory.map { msg ->
            Content(
                parts = listOf(Part(text = msg.text)),
                role = if (msg.role == "user") "user" else "model"
            )
        }

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40
            ),
            systemInstruction = systemInstruction
        )

        try {
            // Using gemini-3.5-flash as default for basic conversational Q&A
            val response = apiService.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            aiText ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            Log.e("ZoyaRepository", "Error calling Gemini API: ${e.message}", e)
            "Error: ${e.localizedMessage ?: "Network request failed. Ensure your API key is correct and you have an active internet connection."}"
        }
    }
}
