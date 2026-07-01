package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Futuristic Cyberpunk / Sassy Voice Assistant Colors
val ZoyaBgDark = Color(0xFF06070D)         // Deep space dark void
val ZoyaSurfaceDark = Color(0xFF0F111E)    // Sleek translucent surface card
val ZoyaSurfaceBorder = Color(0xFF20233B)  // Sleek border line
val ZoyaTextLight = Color(0xFFF1EFF8)      // Brilliant light silver text
val ZoyaTextMuted = Color(0xFF868AAB)      // Muted lavender slate text

// Sassy flirty glowing accent colors
val ZoyaNeonCyan = Color(0xFF00F0FF)       // Electric Cyan for listening/active state
val ZoyaNeonPink = Color(0xFFFF2A85)       // Sassy Hot Pink for speaking/charming state
val ZoyaElectricPurple = Color(0xFF913CFF) // Deep Violet for thinking/glowing orb core
val ZoyaGlowPink = Color(0xFFFF7EBB)

// Theme compatibility fallback mappings
val MinimalistPrimary = ZoyaElectricPurple
val MinimalistOnPrimary = Color.White
val MinimalistPrimaryContainer = ZoyaSurfaceDark
val MinimalistOnPrimaryContainer = ZoyaNeonCyan

val MinimalistSecondary = ZoyaNeonPink
val MinimalistOnSecondary = Color.White

val MinimalistBackground = ZoyaBgDark
val MinimalistSurface = ZoyaSurfaceDark
val MinimalistSurfaceCard = ZoyaSurfaceDark
val MinimalistSurfaceBorder = ZoyaSurfaceBorder
val MinimalistOutline = ZoyaTextMuted

val MinimalistTextDark = ZoyaTextLight
val MinimalistTextMedium = ZoyaTextMuted
val MinimalistTextMuted = ZoyaTextMuted

val MinimalistGlowStart = ZoyaElectricPurple
val MinimalistGlowEnd = ZoyaNeonPink
val MinimalistAmbientGlow = ZoyaNeonCyan
