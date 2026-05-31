package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit
) {
    // Generate a beautiful, stable, repeating waveform pattern
    val waveHeights = remember {
        val random = Random(42) // Stable seed so wave doesn't jump
        FloatArray(65) {
            val base = if (it in 10..55) 0.5f else 0.15f
            val noise = random.nextFloat() * 0.45f
            (base + noise).coerceIn(0.1f, 0.95f)
        }
    }

    // Pulse animation when audio is active
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_visualizer")
    val pulseScale by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val activeSecondary = MaterialTheme.colorScheme.tertiary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Custom visual feedback via bar selection
            ) {
                // Seek logic on click
                // Simply handled in modern canvas touch or basic seek clicks
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barCount = waveHeights.size
        val spacing = 7.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        if (canvasWidth <= totalSpacing) return@Canvas
        val barWidth = (canvasWidth - totalSpacing) / barCount
        if (barWidth <= 0f) return@Canvas

        for (i in waveHeights.indices) {
            val barFraction = i.toFloat() / barCount.toFloat()
            val isPassed = barFraction <= progress

            val baseHeight = waveHeights[i] * canvasHeight
            val animatedHeight = if (isPlaying && !isPassed) {
                baseHeight * pulseScale
            } else {
                baseHeight
            }.coerceIn(5.dp.toPx(), canvasHeight)

            val x = i * (barWidth + spacing)
            val y = (canvasHeight - animatedHeight) / 2f

            val brush = if (isPassed) {
                Brush.verticalGradient(
                    colors = listOf(activeColor, activeSecondary)
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(inactiveColor, inactiveColor.copy(alpha = 0.5f))
                )
            }

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, animatedHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}
