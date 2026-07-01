package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class ZoyaState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun ZoyaStatusIndicator(
    currentState: ZoyaState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    // Determine target colors & sizes based on state
    val targetBgColor = when (currentState) {
        ZoyaState.LISTENING -> ZoyaNeonCyan.copy(alpha = 0.12f)
        ZoyaState.SPEAKING -> ZoyaNeonPink.copy(alpha = 0.12f)
        ZoyaState.THINKING -> ZoyaElectricPurple.copy(alpha = 0.12f)
        ZoyaState.IDLE -> ZoyaSurfaceDark.copy(alpha = 0.8f)
    }

    val targetBorderColor = when (currentState) {
        ZoyaState.LISTENING -> ZoyaNeonCyan.copy(alpha = 0.8f)
        ZoyaState.SPEAKING -> ZoyaNeonPink.copy(alpha = 0.8f)
        ZoyaState.THINKING -> ZoyaElectricPurple.copy(alpha = 0.8f)
        ZoyaState.IDLE -> ZoyaSurfaceBorder.copy(alpha = 0.5f)
    }

    val targetStateLabel = when (currentState) {
        ZoyaState.LISTENING -> "LISTENING"
        ZoyaState.SPEAKING -> "ZOYA SPEAKING"
        ZoyaState.THINKING -> "THINKING"
        ZoyaState.IDLE -> "ONLINE"
    }

    val targetLabelColor = when (currentState) {
        ZoyaState.LISTENING -> ZoyaNeonCyan
        ZoyaState.SPEAKING -> ZoyaNeonPink
        ZoyaState.THINKING -> ZoyaElectricPurple
        ZoyaState.IDLE -> ZoyaTextMuted
    }

    // Spring animations mirroring Framer Motion physics-based transitions
    val springSpecFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val springSpecDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bg_color"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "border_color"
    )

    val animatedLabelColor by animateColorAsState(
        targetValue = targetLabelColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "label_color"
    )

    // Interactive scale animation to simulate physics-based reactive feedback
    val animatedScale by animateFloatAsState(
        targetValue = when (currentState) {
            ZoyaState.LISTENING -> 1.05f + (rmsLevel / 15f).coerceIn(0f, 0.1f)
            ZoyaState.SPEAKING -> 1.03f
            ZoyaState.THINKING -> 0.98f
            ZoyaState.IDLE -> 1.0f
        },
        animationSpec = springSpecFloat,
        label = "pill_scale"
    )

    // Animated horizontal width expanding/collapsing smoothly
    val animatedWidth by animateDpAsState(
        targetValue = when (currentState) {
            ZoyaState.LISTENING -> 150.dp
            ZoyaState.SPEAKING -> 165.dp
            ZoyaState.THINKING -> 140.dp
            ZoyaState.IDLE -> 115.dp
        },
        animationSpec = springSpecDp,
        label = "pill_width"
    )

    // Pulse transitions for subtle visual breathe animations
    val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_spin"
    )

    Row(
        modifier = modifier
            .width(animatedWidth)
            .height(38.dp)
            .scale(animatedScale)
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBgColor)
            .border(1.dp, animatedBorderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Status Dot or Graphic element
        Box(
            modifier = Modifier
                .size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (currentState) {
                ZoyaState.LISTENING -> {
                    // 3 animated bouncing mic-capture lines
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 3) {
                            val barHeight by infiniteTransition.animateFloat(
                                initialValue = 3f,
                                targetValue = 12f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 350 + (i * 100),
                                        easing = EaseInOutSine
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "listening_bar_$i"
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(ZoyaNeonCyan)
                            )
                        }
                    }
                }
                ZoyaState.SPEAKING -> {
                    // Smooth radar circular pulse wave
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .border(1.5.dp, ZoyaNeonPink.copy(alpha = pulseAlpha), RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ZoyaNeonPink)
                        )
                    }
                }
                ZoyaState.THINKING -> {
                    // Rotating planetary system spinner
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.width / 2f
                            val angleRad = Math.toRadians(rotationAngle.toDouble())
                            val dot1X = r + (r * Math.cos(angleRad)).toFloat()
                            val dot1Y = r + (r * Math.sin(angleRad)).toFloat()
                            
                            drawCircle(
                                color = ZoyaElectricPurple,
                                radius = 2.dp.toPx(),
                                center = Offset(dot1X, dot1Y)
                            )
                            drawCircle(
                                color = ZoyaNeonCyan.copy(alpha = 0.5f),
                                radius = 1.5.dp.toPx(),
                                center = Offset(r - (r * Math.cos(angleRad)).toFloat(), r - (r * Math.sin(angleRad)).toFloat())
                            )
                        }
                    }
                }
                ZoyaState.IDLE -> {
                    // Sleek green/pink breathing status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(pulseScale)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZoyaNeonPink.copy(alpha = pulseAlpha))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // State Text label
        Text(
            text = targetStateLabel,
            color = animatedLabelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}
