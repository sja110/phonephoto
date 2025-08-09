
package com.twobecome.phonephoto.thumb

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    corner: RoundedCornerShape = RoundedCornerShape(10.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "xAnim"
    )
    val widthPx = with(LocalDensity.current) { 180.dp.toPx() }
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset(x * widthPx - widthPx, 0f),
        end = Offset(x * widthPx, 0f)
    )
    Box(
        modifier = modifier
            .clip(corner)
            .background(brush)
            .fillMaxSize()
    )
}
