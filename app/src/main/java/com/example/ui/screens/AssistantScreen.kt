package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.ui.components.ZoyaCore
import com.example.ui.components.ZoyaStatusIndicator
import com.example.ui.components.ZoyaState
import com.example.ui.components.ZoyaMicTriggerButton
import com.example.ui.components.ZoyaArchiveModal
import com.example.ui.theme.*
import com.example.viewmodel.ZoyaViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AssistantScreen(
    viewModel: ZoyaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Collect Viewmodel States
    val sessions by viewModel.sessions.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val isAutoReadEnabled by viewModel.isVoiceAutoReadEnabled.collectAsState()
    val currentPersonality by viewModel.currentPersonality.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val speechPitch by viewModel.speechPitch.collectAsState()
    val voiceGender by viewModel.voiceGender.collectAsState()

    // Collect Voice States
    val isListening by viewModel.voiceManager.isListening.collectAsState()
    val isSpeaking by viewModel.voiceManager.isSpeaking.collectAsState()
    val rmsLevel by viewModel.voiceManager.listeningRms.collectAsState()
    val speechResult by viewModel.voiceManager.speechResult.collectAsState()
    val sttError by viewModel.voiceManager.sttError.collectAsState()
    val indexedDbTranscripts by viewModel.voiceManager.indexedDbTranscripts.collectAsState()

    // Handle tool event: Launch Website
    val openWebsiteUrl by viewModel.openWebsiteEvent.collectAsState()
    LaunchedEffect(openWebsiteUrl) {
        openWebsiteUrl?.let { url ->
            try {
                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl)).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Executing toolCall: Opening $cleanUrl", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to open site: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                viewModel.clearOpenWebsiteEvent()
            }
        }
    }

    // Settings Modal Open State
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isIndexedDbArchiveOpen by remember { mutableStateOf(false) }

    // Chat text input state (fallback toggle)
    var textInput by remember { mutableStateOf("") }
    var isTextInputMode by remember { mutableStateOf(false) }

    // Request dynamic RECORD_AUDIO permission
    val recordAudioPermission = rememberPermissionState(
        permission = android.Manifest.permission.RECORD_AUDIO
    )

    // Trigger toast alerts for voice engine error
    LaunchedEffect(sttError) {
        sttError?.let {
            Toast.makeText(context, "Voice error: $it", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic sassy, flirty prompts
    val suggestionPrompts = remember(currentPersonality) {
        when (currentPersonality) {
            "Zen" -> listOf(
                "Tell me a Zen proverb",
                "Guide me in a short breathing flow",
                "How do I find calm today?"
            )
            "Tech" -> listOf(
                "Explain Kotlin Coroutines",
                "What is dependency injection?",
                "Open github.com"
            )
            "Creative" -> listOf(
                "Write a cosmic poem about Zoya",
                "Create a sci-fi microstory",
                "Tell me a gorgeous story"
            )
            else -> listOf(
                "Give me a sassy one-liner!",
                "Tease me with light sarcasm",
                "Open wikipedia.org",
                "Do you like me?"
            )
        }
    }

    // Get the very last response from assistant to show as live sassy caption text
    val lastAssistantMessage = remember(messages) {
        messages.lastOrNull { it.role == "model" }?.text ?: "Ready to assist with your day"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ZoyaSurfaceDark,
                drawerContentColor = ZoyaTextLight,
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Title Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(ZoyaBgDark, ZoyaSurfaceDark)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ZoyaElectricPurple.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, ZoyaNeonCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Z",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = ZoyaNeonCyan
                                )
                            }
                            Text(
                                text = "Zoya AI Core",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZoyaTextLight
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conversational Database Logs",
                            fontSize = 12.sp,
                            color = ZoyaTextMuted
                        )
                    }
                }

                Divider(color = ZoyaSurfaceBorder)

                // Sessions List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ZoyaElectricPurple.copy(alpha = 0.15f))
                                .border(1.dp, ZoyaElectricPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.createNewSession(
                                        "New Chat",
                                        currentPersonality
                                    )
                                    scope.launch { drawerState.close() }
                                }
                                .padding(12.dp)
                                .testTag("new_chat_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ZoyaNeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Session", fontWeight = FontWeight.SemiBold, color = ZoyaTextLight)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "HISTORIC INTERACTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZoyaTextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    items(sessions) { session ->
                        val isSelected = currentSession?.id == session.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ZoyaSurfaceDark.copy(alpha = 0.7f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) ZoyaNeonCyan.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.selectSession(session)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (session.aiPersonality) {
                                    "Zen" -> Icons.Rounded.Spa
                                    "Tech" -> Icons.Rounded.Terminal
                                    "Creative" -> Icons.Rounded.Palette
                                    else -> Icons.Rounded.Forum
                                },
                                contentDescription = null,
                                tint = if (isSelected) ZoyaNeonPink else ZoyaTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) ZoyaTextLight else ZoyaTextMuted,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Mode: ${session.aiPersonality}",
                                    fontSize = 11.sp,
                                    color = ZoyaTextMuted.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSession(session) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Session",
                                    tint = Color.Red.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ZoyaSurfaceBorder)

                // Beautiful footer action button to open IndexedDB voice transcripts
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.voiceManager.loadTranscriptsFromIndexedDb()
                            isIndexedDbArchiveOpen = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_indexeddb_archive"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZoyaElectricPurple.copy(alpha = 0.15f),
                            contentColor = ZoyaNeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZoyaNeonCyan.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "View Archive",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Web Transcript Archive (IndexedDB)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = ZoyaTextLight
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(ZoyaElectricPurple.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ZoyaNeonPink, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Z",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ZoyaNeonPink
                                )
                            }
                            Text(
                                text = "Zoya AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp,
                                color = ZoyaTextLight
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Notes,
                                contentDescription = "Drawer menu",
                                tint = ZoyaNeonCyan
                            )
                        }
                    },
                    actions = {
                        // Toggle Text Input fallback mode
                        IconButton(onClick = { isTextInputMode = !isTextInputMode }) {
                            Icon(
                                if (isTextInputMode) Icons.Rounded.Mic else Icons.Rounded.Keyboard,
                                contentDescription = "Toggle Keyboard Mode",
                                tint = ZoyaTextMuted
                            )
                        }
                        IconButton(onClick = { isSettingsOpen = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = ZoyaTextMuted
                            )
                        }
                    }
                )
            },
            containerColor = ZoyaBgDark
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ZoyaBgDark,
                                ZoyaBgDark,
                                ZoyaElectricPurple.copy(alpha = 0.05f)
                            )
                        )
                    )
            ) {
                // If keyboard mode is active, we render an overlay chat bubble list to avoid blocking the voice view
                if (isTextInputMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                        ) {
                            items(messages) { msg ->
                                val isUser = msg.role == "user"
                                ChatBubbleItem(
                                    message = msg,
                                    isUser = isUser,
                                    onSpeak = { viewModel.speakMessage(msg.text) },
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(msg.text))
                                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (isTyping) {
                                item {
                                    ThinkingIndicatorBubble()
                                }
                            }
                        }
                    }
                } else {
                    // Immersive Voice-Only Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 140.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Central Interactive Core Visualizer Orb
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ZoyaCore(
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                isThinking = isTyping,
                                rmsLevel = rmsLevel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("zoya_avatar_core")
                            )
                        }

                        // Sassy live caption box showing assistant responses in elegant stylized text
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // State-based UI status indicator transitioning with smooth physics animations
                            val currentZoyaState = when {
                                isListening -> ZoyaState.LISTENING
                                isSpeaking -> ZoyaState.SPEAKING
                                isTyping -> ZoyaState.THINKING
                                else -> ZoyaState.IDLE
                            }

                            ZoyaStatusIndicator(
                                currentState = currentZoyaState,
                                rmsLevel = rmsLevel,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = ZoyaSurfaceDark.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, ZoyaSurfaceBorder, RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = if (isTyping) "Thinking..." else lastAssistantMessage,
                                    fontSize = 15.sp,
                                    color = ZoyaTextLight,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Light,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                )
                            }
                        }
                    }
                }

                // Horizontal Suggestion Pills overlay
                if (!isListening) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp)
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = 0,
                            divider = {},
                            indicator = {},
                            edgePadding = 16.dp,
                            containerColor = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            suggestionPrompts.forEach { suggestion ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = ZoyaSurfaceDark
                                    ),
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .border(1.dp, ZoyaSurfaceBorder, RoundedCornerShape(14.dp))
                                        .clickable {
                                            viewModel.sendMessage(suggestion)
                                        }
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ZoyaNeonCyan,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live mic transcription overlay card
                if (isListening) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ZoyaSurfaceDark
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp, start = 24.dp, end = 24.dp)
                            .border(1.dp, ZoyaNeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ZoyaNeonCyan.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = ZoyaNeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "YOU ARE SPEAKING",
                                    fontSize = 10.sp,
                                    color = ZoyaNeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (speechResult.isEmpty()) "Zoya is listening..." else speechResult,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ZoyaTextLight,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                // Control bar (Standard or Fallback Keyboard Mode)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (isTextInputMode) {
                        // Keyboard Fallback Input deck
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = { Text("Tease me with a query...", color = ZoyaTextMuted) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .border(1.dp, ZoyaSurfaceBorder, RoundedCornerShape(26.dp))
                                    .testTag("message_input_field"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = ZoyaSurfaceDark,
                                    unfocusedContainerColor = ZoyaSurfaceDark,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = ZoyaTextLight,
                                    unfocusedTextColor = ZoyaTextLight,
                                    cursorColor = ZoyaNeonPink
                                ),
                                shape = RoundedCornerShape(26.dp),
                                singleLine = true,
                                trailingIcon = {
                                    if (textInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.sendMessage(textInput)
                                                textInput = ""
                                            },
                                            modifier = Modifier.testTag("send_button")
                                        ) {
                                            Icon(
                                                Icons.Rounded.Send,
                                                contentDescription = "Send Text",
                                                tint = ZoyaNeonPink
                                            )
                                        }
                                    }
                                }
                            )

                            // Voice switch round button
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(ZoyaSurfaceDark, CircleShape)
                                    .border(1.dp, ZoyaSurfaceBorder, CircleShape)
                                    .clickable { isTextInputMode = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = "Return to mic mode",
                                    tint = ZoyaNeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        // REUSABLE HIGHLY POLISHED MICROPHONE TRIGGER BUTTON COMPONENT
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            ZoyaMicTriggerButton(
                                isListening = isListening,
                                rmsLevel = rmsLevel,
                                onClick = {
                                    if (recordAudioPermission.status.isGranted) {
                                        viewModel.toggleVoiceListening()
                                    } else {
                                        recordAudioPermission.launchPermissionRequest()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet displaying past interactions persisted in IndexedDB
    ZoyaArchiveModal(
        isOpen = isIndexedDbArchiveOpen,
        onDismissRequest = { isIndexedDbArchiveOpen = false },
        transcripts = indexedDbTranscripts,
        onRefresh = { viewModel.voiceManager.loadTranscriptsFromIndexedDb() },
        onClearAll = { viewModel.voiceManager.clearTranscriptsFromIndexedDb() }
    )

    // Config/Settings Dialog Modal
    if (isSettingsOpen) {
        Dialog(onDismissRequest = { isSettingsOpen = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ZoyaSurfaceDark),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, ZoyaSurfaceBorder, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Zoya AI Configuration",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZoyaTextLight,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Voice Auto-Read Speak Options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = ZoyaNeonPink)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Acoustic Speech Out (TTS)", color = ZoyaTextLight, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isAutoReadEnabled,
                            onCheckedChange = { viewModel.toggleVoiceAutoRead() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ZoyaNeonCyan,
                                uncheckedThumbColor = ZoyaTextMuted,
                                uncheckedTrackColor = ZoyaSurfaceBorder
                            )
                        )
                    }

                    // Collapsible Real-Time Voice Customization Parameters
                    AnimatedVisibility(
                        visible = isAutoReadEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Voice Gender Segmented Control
                            Text(
                                "Voice Gender Model",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZoyaTextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Default", "Female", "Male").forEach { gender ->
                                    val isSelected = voiceGender == gender
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) ZoyaElectricPurple.copy(alpha = 0.25f) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (isSelected) ZoyaNeonCyan else ZoyaSurfaceBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setVoiceGender(gender) }
                                            .padding(vertical = 10.dp)
                                            .testTag("voice_gender_${gender.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = gender,
                                            color = if (isSelected) ZoyaNeonCyan else ZoyaTextMuted,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Speech Rate Speed Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Speech Rate (Speed)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZoyaTextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        "${String.format("%.2f", speechRate)}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZoyaNeonCyan
                                    )
                                }
                                Slider(
                                    value = speechRate,
                                    onValueChange = { viewModel.setSpeechRate(it) },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ZoyaNeonCyan,
                                        activeTrackColor = ZoyaNeonCyan,
                                        inactiveTrackColor = ZoyaSurfaceBorder
                                    ),
                                    modifier = Modifier.testTag("speech_rate_slider")
                                )
                            }

                            // Speech Pitch Tone Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Voice Pitch Tone",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZoyaTextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        "${String.format("%.2f", speechPitch)}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZoyaNeonPink
                                    )
                                }
                                Slider(
                                    value = speechPitch,
                                    onValueChange = { viewModel.setSpeechPitch(it) },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ZoyaNeonPink,
                                        activeTrackColor = ZoyaNeonPink,
                                        inactiveTrackColor = ZoyaSurfaceBorder
                                    ),
                                    modifier = Modifier.testTag("speech_pitch_slider")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = ZoyaSurfaceBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Personality Mode Selection Option
                    Text(
                        "Synthetic Persona Selector",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZoyaTextMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Sassy AI (Default)", "Zen", "Tech", "Creative").forEach { mode ->
                            val cleanMode = if (mode.startsWith("Sassy")) "Friendly" else mode // Map "Friendly" backend to Sassy text
                            val isSelected = (currentPersonality == "Friendly" && cleanMode == "Friendly") || currentPersonality == cleanMode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) ZoyaElectricPurple.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) ZoyaNeonCyan else ZoyaSurfaceBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.updatePersonality(cleanMode)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        "Zen" -> Icons.Rounded.Spa
                                        "Tech" -> Icons.Rounded.Terminal
                                        "Creative" -> Icons.Rounded.Palette
                                        else -> Icons.Rounded.Favorite
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) ZoyaNeonPink else ZoyaTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = mode,
                                    color = if (isSelected) ZoyaTextLight else ZoyaTextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Divider(color = ZoyaSurfaceBorder, modifier = Modifier.padding(vertical = 16.dp))

                    // API Key Verification Alert
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ZoyaSurfaceDark.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "SECURITY AUDIT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZoyaTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (viewModel.apiKey.isNotEmpty() && !viewModel.apiKey.startsWith("MY_")) Icons.Rounded.Lock else Icons.Rounded.Warning,
                                    tint = if (viewModel.apiKey.isNotEmpty() && !viewModel.apiKey.startsWith("MY_")) Color(0xFF00E676) else Color(0xFFFF5252),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (viewModel.apiKey.isNotEmpty() && !viewModel.apiKey.startsWith("MY_")) "Gemini Live Key Verified" else "Gemini API Key Missing",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZoyaTextLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { isSettingsOpen = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ZoyaNeonPink),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Configuration", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    isUser: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val containerColor = if (isUser) ZoyaElectricPurple.copy(alpha = 0.25f) else ZoyaSurfaceDark
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_msg_item" else "assistant_msg_item"),
        horizontalAlignment = alignment
    ) {
        Text(
            text = if (isUser) "You" else "Zoya Core",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUser) ZoyaNeonCyan else ZoyaNeonPink,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .border(
                    1.dp,
                    if (isUser) ZoyaNeonCyan.copy(alpha = 0.2f) else ZoyaSurfaceBorder,
                    shape
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = ZoyaTextLight,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isUser && message.isVoiceInput) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = ZoyaNeonCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Acoustic", fontSize = 10.sp, color = ZoyaNeonCyan, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isUser) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = onSpeak,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Speak Response",
                                    tint = ZoyaNeonPink.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    tint = ZoyaTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    val formatter = remember {
                        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    }
                    Text(
                        text = formatter.format(java.util.Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = ZoyaTextMuted,
                        modifier = Modifier.align(Alignment.Bottom)
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 0), RepeatMode.Reverse),
        label = "dot_alpha_1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 150), RepeatMode.Reverse),
        label = "dot_alpha_2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 300), RepeatMode.Reverse),
        label = "dot_alpha_3"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "Zoya Core",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ZoyaNeonPink,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ZoyaSurfaceDark),
            modifier = Modifier
                .width(90.dp)
                .border(1.dp, ZoyaSurfaceBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(ZoyaNeonCyan.copy(alpha = dotAlpha1), CircleShape))
                Box(modifier = Modifier.size(8.dp).background(ZoyaNeonCyan.copy(alpha = dotAlpha2), CircleShape))
                Box(modifier = Modifier.size(8.dp).background(ZoyaNeonCyan.copy(alpha = dotAlpha3), CircleShape))
            }
        }
    }
}
