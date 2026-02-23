package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowScreen(
    photos: List<Photo>,
    onBack: () -> Unit,
) {
    if (photos.isEmpty()) { onBack(); return }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var intervalSeconds by remember { mutableIntStateOf(3) }

    val currentPhoto = photos[currentIndex]

    // Auto-advance timer
    LaunchedEffect(isPlaying, currentIndex, intervalSeconds) {
        if (isPlaying) {
            delay(intervalSeconds * 1000L)
            currentIndex = (currentIndex + 1) % photos.size
        }
    }

    // Auto-hide controls after 3s
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        // Photo with crossfade
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(600)) togetherWith
                        fadeOut(animationSpec = tween(600))
            },
            modifier = Modifier.fillMaxSize(),
            label = "slideshow"
        ) { index ->
            AsyncImage(
                model = ApiService.photoUrl(photos[index].url),
                contentDescription = "Photo ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Fermer", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                    title = {
                        Text(
                            "${currentIndex + 1} / ${photos.size}",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = 16.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress dots (limité à 20 pour perf)
                    if (photos.size <= 20) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            photos.forEachIndexed { index, _ ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (index == currentIndex) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == currentIndex) AccentColor
                                            else Color.White.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }

                    // Main controls
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
                    ) {
                        // Previous
                        IconButton(
                            onClick = {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else photos.size - 1
                            }
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Précédent", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Play/Pause
                        FilledIconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = AccentColor
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "Pause" else "Lecture",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % photos.size
                            }
                        ) {
                            Icon(Icons.Default.SkipNext, "Suivant", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Speed selector
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(2, 3, 5, 8).forEach { seconds ->
                            val isSelected = intervalSeconds == seconds
                            Surface(
                                onClick = { intervalSeconds = seconds },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AccentColor.copy(alpha = 0.3f) else Color.Transparent,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    "${seconds}s",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentColor else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
