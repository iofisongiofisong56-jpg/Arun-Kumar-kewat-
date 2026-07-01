package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaElectricPurple
import com.example.ui.theme.ZoyaBgDark

/**
 * A beautiful, highly-polished microphone trigger button designed following
 * Material Design 3 guidelines. It features real-time RMS-responsive voice ripples,
 * glowing concentric ring animations, and accessibility support.
 *
 * @param isListening True when Zoya is actively listening to user voice.
 * @param rmsLevel The real-time volume level from the voice manager (STT).
 * @param onClick Click handler triggered when the button is tapped.
 * @param modifier Optional modifier to configure layouts.
 */
@Composable
fun ZoyaMicTriggerButton(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Infinite pulse animations for background glowing halo
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    
    val haloPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_pulse"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_alpha"
    )

    // Animated colors and properties based on listening states
    val baseColor = if (isListening) ZoyaNeonCyan else ZoyaNeonPink
    val glowColor = if (isListening) ZoyaNeonCyan.copy(alpha = 0.25f) else ZoyaNeonPink.copy(alpha = 0.15f)
    
    // Dynamic scaling factor influenced directly by the real-time RMS voice inputs
    val rmsScaleMultiplier = if (isListening) {
        // Normalize RMS level (approx range 0 to 8.5) to a subtle scale factor
        1.0f + (rmsLevel.coerceIn(0f, 10f) / 10f) * 0.18f
    } else {
        1.0f
    }

    // Smooth physics-based scaling transition for micro-interactions
    val buttonScale by animateFloatAsState(
        targetValue = if (isListening) 1.05f * rmsScaleMultiplier else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    Box(
        modifier = modifier
            .size(90.dp)
            .testTag("voice_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Real-time dynamic voice ripple/wave animation based on WebView's RMS levels
        if (isListening && rmsLevel > 0f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(buttonScale * 1.35f)
            ) {
                // Outer organic sounding audio wave border
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ZoyaNeonCyan.copy(alpha = (0.25f * (rmsLevel / 10f)).coerceIn(0f, 0.4f)),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2f
                )
            }
        }

        // 2. Continuous ambient pulse halo
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(haloPulseScale)
        ) {
            val strokeWidth = 1.5.dp.toPx()
            drawCircle(
                color = baseColor.copy(alpha = haloAlpha * if (isListening) 0.6f else 0.3f),
                radius = (size.minDimension / 2f) - strokeWidth,
                style = Stroke(width = strokeWidth)
            )
        }

        // 3. Central glowing sphere button satisfying minimum touch target of 48dp (designed at 68dp)
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(buttonScale)
                .shadow(
                    elevation = if (isListening) 12.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = baseColor,
                    spotColor = baseColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            baseColor,
                            baseColor.copy(alpha = 0.85f),
                            ZoyaBgDark.copy(alpha = 0.1f)
                        )
                    )
                )
                .clickable {
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = if (isListening) "Stop listening and process" else "Speak with Zoya AI",
                tint = if (isListening) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
