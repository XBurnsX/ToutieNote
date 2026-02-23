package com.toutieserver.toutienote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.toutieserver.toutienote.ui.theme.MutedColor
import com.toutieserver.toutienote.ui.theme.Surface2Color

@Composable
fun NoteItemSkeleton(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val skeletonColor = MutedColor.copy(alpha = alpha)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2Color.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .height(14.dp)
                    .weight(0.6f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(skeletonColor)
            )
            Box(
                modifier = Modifier
                    .height(11.dp)
                    .width(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(skeletonColor)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(skeletonColor)
        )
    }
}
