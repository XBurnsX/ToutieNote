package com.toutieserver.toutienote.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFullscreenScreen(
    initialIndex: Int,
    vm: VaultViewModel,
    onBack: () -> Unit,
    onEdit: (Photo) -> Unit,
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(true) }
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── LIVE: observe les photos du ViewModel ──
    val photos by vm.photos.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size }
    )

    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    if (photos.isEmpty()) {
        // Plus de photos → retour
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> photos.getOrNull(page)?.url ?: page },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos.getOrNull(page) ?: return@HorizontalPager
            val isVideo = photo.mediaType.startsWith("video")

            key(photo.url) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showOverlay = !showOverlay },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        val videoUrl = ApiService.photoUrl(photo.url)
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse(videoUrl))
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { it.isLooping = true; start() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(ApiService.photoUrl(photo.url))
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = photo.filename,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )

        // Overlay
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBackIosNew,
                                contentDescription = "Retour",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            "${pagerState.currentPage + 1} / ${photos.size}",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Bottom bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { currentPhoto?.let { vm.exportToGallery(it, context.contentResolver) } }
                    ) {
                        Icon(Icons.Default.SaveAlt, "Exporter", tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("Exporter", color = Color.White, fontSize = 10.sp)
                    }

                    // Edit
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { currentPhoto?.let { onEdit(it) } }
                    ) {
                        Icon(Icons.Default.Edit, "Éditer", tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("Éditer", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
