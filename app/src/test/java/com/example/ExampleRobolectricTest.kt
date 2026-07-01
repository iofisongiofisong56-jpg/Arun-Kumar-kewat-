package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Zoya AI", appName)
  }

  @Test
  fun `test parsing of indexeddb transcripts in voice manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val voiceManager = com.example.voice.VoiceManager(context)
    val bridge = voiceManager.WebSpeechBridge()
    
    val testJson = """
      [
        {"id": 123, "timestamp": 1680000000000, "role": "user", "text": "Hello Zoya", "personality": "Zen"},
        {"id": 124, "timestamp": 1680000001000, "role": "zoya", "text": "Greetings seeker", "personality": "Zen"}
      ]
    """.trimIndent()
    
    bridge.onTranscriptsLoaded(testJson)
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    
    val loaded = voiceManager.indexedDbTranscripts.value
    assertEquals(2, loaded.size)
    assertEquals(123L, loaded[0].id)
    assertEquals(1680000000000L, loaded[0].timestamp)
    assertEquals("user", loaded[0].role)
    assertEquals("Hello Zoya", loaded[0].text)
    assertEquals("Zen", loaded[0].personality)
  }

  @Test
  fun `test play chimes does not throw exception`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val voiceManager = com.example.voice.VoiceManager(context)
    try {
        voiceManager.playStartChime()
        voiceManager.playEndChime()
    } catch (e: Exception) {
        org.junit.Assert.fail("Chimes playing threw an exception: ${e.message}")
    }
  }

  @Test
  fun `test speech configuration settings flow and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val voiceManager = com.example.voice.VoiceManager(context)

    // Test default values
    assertEquals(1.0f, voiceManager.speechRate.value)
    assertEquals(1.00f, voiceManager.speechPitch.value)
    assertEquals("Default", voiceManager.voiceGender.value)

    // Update settings
    voiceManager.updateSpeechRate(1.25f)
    voiceManager.updateSpeechPitch(1.15f)
    voiceManager.updateVoiceGender("Female")

    // Assert updated state in Flow
    assertEquals(1.25f, voiceManager.speechRate.value)
    assertEquals(1.15f, voiceManager.speechPitch.value)
    assertEquals("Female", voiceManager.voiceGender.value)

    // Create a new instance to verify persistent shared preferences storage
    val anotherVoiceManager = com.example.voice.VoiceManager(context)
    assertEquals(1.25f, anotherVoiceManager.speechRate.value)
    assertEquals(1.15f, anotherVoiceManager.speechPitch.value)
    assertEquals("Female", anotherVoiceManager.voiceGender.value)
  }
}
