package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZoyaDarkColorScheme = darkColorScheme(
    primary = ZoyaElectricPurple,
    onPrimary = Color.White,
    primaryContainer = ZoyaSurfaceDark,
    onPrimaryContainer = ZoyaNeonCyan,
    secondary = ZoyaNeonPink,
    onSecondary = Color.White,
    background = ZoyaBgDark,
    surface = ZoyaSurfaceDark,
    onBackground = ZoyaTextLight,
    onSurface = ZoyaTextLight,
    onSurfaceVariant = ZoyaTextMuted,
    outline = ZoyaTextMuted
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force the breathtakingly beautiful Dark Cyberpunk/Sassy voice theme!
    content: @Composable () -> Unit
) {
    val colorScheme = ZoyaDarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
