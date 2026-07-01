package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun ZoyaCore(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zoya_neon_core_anim")

    // Gentle pulsing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Dynamic wave phase for animating central bars
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width / 2.7f
            
            // Apply scale based on current state
            val currentScale = when {
                isListening -> 1.08f + ((rmsLevel + 3f) / 12f).coerceIn(0f, 0.4f)
                isSpeaking -> 1.04f + sin(wavePhase * 2f) * 0.06f
                isThinking -> pulseScale * 1.05f
                else -> pulseScale
            }

            val finalRadius = baseRadius * currentScale

            // Dynamic glow colors depending on current assistant voice state
            val glowColorOuter = when {
                isListening -> ZoyaNeonCyan
                isSpeaking -> ZoyaNeonPink
                isThinking -> ZoyaElectricPurple
                else -> ZoyaNeonPink
            }

            val glowColorInner = when {
                isListening -> ZoyaElectricPurple
                isSpeaking -> ZoyaGlowPink
                isThinking -> ZoyaNeonCyan
                else -> ZoyaElectricPurple
            }

            // 1. Double outer cyberpunk ambient glow layers
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColorOuter.copy(alpha = 0.32f),
                        glowColorOuter.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = finalRadius * 2.0f
                ),
                radius = finalRadius * 2.0f,
                center = center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColorInner.copy(alpha = 0.22f),
                        glowColorInner.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = finalRadius * 1.5f
                ),
                radius = finalRadius * 1.5f,
                center = center
            )

            // 2. Core Sassy Orb Gradient (from ElectricPurple to NeonPink/Cyan)
            val orbColors = if (isListening) {
                listOf(ZoyaElectricPurple, ZoyaNeonCyan)
            } else {
                listOf(ZoyaElectricPurple, ZoyaNeonPink)
            }

            drawCircle(
                brush = Brush.linearGradient(
                    colors = orbColors,
                    start = Offset(center.x - finalRadius, center.y - finalRadius),
                    end = Offset(center.x + finalRadius, center.y + finalRadius)
                ),
                radius = finalRadius,
                center = center
            )

            // 3. Inner fine holographic glass border
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = finalRadius * 0.85f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Soundwave Core Indicator Bars (5 distinct centered white capsules)
            val barCount = 5
            val barWidth = 4.5.dp.toPx()
            val barSpacing = 7.dp.toPx()
            val totalWidth = (barCount * barWidth) + ((barCount - 1) * barSpacing)
            val startX = center.x - (totalWidth / 2f) + (barWidth / 2f)

            // Draw each sound bar with specific height and animation scaling
            for (i in 0 until barCount) {
                val x = startX + i * (barWidth + barSpacing)

                // Unique dynamic heights based on index for asymmetry
                val baseHeightFactor = when (i) {
                    0 -> 0.40f
                    1 -> 0.80f
                    2 -> 0.65f
                    3 -> 0.85f
                    else -> 0.35f
                }

                val activeAnimFactor = when {
                    isListening -> {
                        val rmsFactor = ((rmsLevel + 3f) / 10f).coerceIn(0.1f, 1.1f)
                        val shift = sin(wavePhase + (i * 1.3f)) * 0.4f
                        (rmsFactor + shift).coerceIn(0.2f, 1.2f)
                    }
                    isSpeaking -> {
                        0.75f + sin(wavePhase * 1.8f + (i * 1.0f)) * 0.35f
                    }
                    isThinking -> {
                        0.60f + sin(wavePhase * 3.5f + (i * 1.8f)) * 0.30f
                    }
                    else -> {
                        0.80f + sin(wavePhase * 0.6f + (i * 0.7f)) * 0.15f
                    }
                }

                val maxBarHeight = finalRadius * 0.58f
                val targetBarHeight = maxBarHeight * baseHeightFactor * activeAnimFactor
                val barHeight = targetBarHeight.coerceAtLeast(8.dp.toPx())

                val opacity = when (i) {
                    0 -> 0.70f
                    1 -> 1.00f
                    2 -> 0.85f
                    3 -> 0.95f
                    else -> 0.60f
                }

                drawLine(
                    color = Color.White.copy(alpha = opacity),
                    start = Offset(x, center.y - (barHeight / 2f)),
                    end = Offset(x, center.y + (barHeight / 2f)),
                    strokeWidth = barWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}
