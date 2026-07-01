package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.di.ZoyaContainer
import com.example.ui.screens.AssistantScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ZoyaViewModel
import com.example.viewmodel.ZoyaViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var container: ZoyaContainer
    private lateinit var viewModel: ZoyaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the manual DI Container
        container = ZoyaContainer(applicationContext)

        // Initialize our unified Zoya ViewModel with the custom repository and voice singletons
        val factory = ZoyaViewModelFactory(container.repository, container.voiceManager)
        viewModel = ViewModelProvider(this, factory)[ZoyaViewModel::class.java]

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AssistantScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure voice speech engine resources are cleanly freed up when MainActivity is destroyed
        if (::container.isInitialized) {
            container.voiceManager.stopSpeaking()
            container.voiceManager.stopListening()
        }
    }
}
