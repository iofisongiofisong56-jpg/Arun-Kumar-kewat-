package com.example.di

import android.content.Context
import com.example.ai.RetrofitClient
import com.example.data.ZoyaDatabase
import com.example.repository.ZoyaRepository
import com.example.voice.VoiceManager

/**
 * ZoyaContainer serves as the manual dependency injection container for managing singletons
 * of the database, API client, repository, and voice/audio helper services.
 */
class ZoyaContainer(private val context: Context) {

    // Lazy initialization of Database and Dao
    private val database: ZoyaDatabase by lazy {
        ZoyaDatabase.getDatabase(context)
    }

    private val zoyaDao by lazy {
        database.zoyaDao()
    }

    // Lazy initialization of Gemini API Service
    private val apiService by lazy {
        RetrofitClient.service
    }

    // Singleton repository instance
    val repository: ZoyaRepository by lazy {
        ZoyaRepository(zoyaDao, apiService)
    }

    // Singleton Voice/Speech coordinator
    val voiceManager: VoiceManager by lazy {
        VoiceManager(context)
    }
}
