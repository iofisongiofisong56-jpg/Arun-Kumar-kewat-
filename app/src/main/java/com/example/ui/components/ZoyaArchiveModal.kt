package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.voice.IndexedDbTranscript

/**
 * A beautiful, highly modular Jetpack Compose modal sheet to view, filter, and clear
 * conversation transcripts fetched from IndexedDB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoyaArchiveModal(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    transcripts: List<IndexedDbTranscript>,
    onRefresh: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf("ALL") } // ALL, USER, ZOYA

    // Dynamic filtering of transcripts in memory based on search query and role filter chips
    val filteredTranscripts = remember(transcripts, searchQuery, selectedRoleFilter) {
        transcripts.filter { item ->
            val matchesSearch = item.text.contains(searchQuery, ignoreCase = true) || 
                                item.personality.contains(searchQuery, ignoreCase = true)
            val matchesRole = when (selectedRoleFilter) {
                "USER" -> item.role.equals("user", ignoreCase = true)
                "ZOYA" -> item.role.equals("zoya", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesRole
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = ZoyaBgDark,
        contentColor = ZoyaTextLight,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ZoyaSurfaceBorder) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 1. Header Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ZOYA VOICE ARCHIVE",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZoyaNeonCyan,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "IndexedDB Sandbox Storage (Client-side WebView)",
                        fontSize = 11.sp,
                        color = ZoyaTextMuted
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("archive_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh transcripts",
                            tint = ZoyaNeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("archive_clear_all_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear all archives",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transcripts...", color = ZoyaTextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = ZoyaTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = ZoyaTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("archive_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ZoyaTextLight,
                    unfocusedTextColor = ZoyaTextLight,
                    focusedContainerColor = ZoyaSurfaceDark.copy(alpha = 0.5f),
                    unfocusedContainerColor = ZoyaSurfaceDark.copy(alpha = 0.3f),
                    focusedBorderColor = ZoyaNeonCyan.copy(alpha = 0.8f),
                    unfocusedBorderColor = ZoyaSurfaceBorder
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Filter Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf("ALL", "USER", "ZOYA")
                filters.forEach { role ->
                    val isSelected = selectedRoleFilter == role
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedRoleFilter = role },
                        label = {
                            Text(
                                text = role,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = if (role == "USER") ZoyaNeonCyan.copy(alpha = 0.15f) else ZoyaNeonPink.copy(alpha = 0.15f),
                            labelColor = ZoyaTextMuted,
                            selectedLabelColor = if (role == "USER") ZoyaNeonCyan else ZoyaNeonPink
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = ZoyaSurfaceBorder,
                            selectedBorderColor = if (role == "USER") ZoyaNeonCyan else ZoyaNeonPink
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = ZoyaSurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // 4. Scrollable Content List
            if (filteredTranscripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LayersClear,
                            contentDescription = null,
                            tint = ZoyaTextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching records found" else "Archive is completely empty",
                            color = ZoyaTextLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try tweaking your keyword or filter." else "Speak or send message queries to Zoya to generate transcripts.",
                            color = ZoyaTextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("archive_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTranscripts, key = { it.id }) { item ->
                        val isUser = item.role == "user"
                        val cardBg = if (isUser) ZoyaSurfaceDark.copy(alpha = 0.6f) else ZoyaElectricPurple.copy(alpha = 0.08f)
                        val accentBorderColor = if (isUser) ZoyaNeonCyan.copy(alpha = 0.3f) else ZoyaNeonPink.copy(alpha = 0.3f)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, accentBorderColor, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(if (isUser) ZoyaNeonCyan else ZoyaNeonPink, CircleShape)
                                        )
                                        Text(
                                            text = if (isUser) "YOU" else "ZOYA (${item.personality.uppercase()})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isUser) ZoyaNeonCyan else ZoyaNeonPink,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val sdf = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                                        val timeStr = sdf.format(java.util.Date(item.timestamp))
                                        Text(
                                            text = timeStr,
                                            fontSize = 9.sp,
                                            color = ZoyaTextMuted.copy(alpha = 0.8f)
                                        )

                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Zoya Transcript", item.text)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Copied transcript to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy text",
                                                tint = ZoyaTextMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.text,
                                    fontSize = 13.sp,
                                    color = ZoyaTextLight,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Action Footer
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("archive_close_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = ZoyaSurfaceDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close Archive", color = ZoyaTextLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}
