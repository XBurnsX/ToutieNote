package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFullscreenScreen(
    photo: Photo,
    localImageFile: File? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(false) }

    // Supprimer le fichier temporaire (crop) quand on quitte l’écran
    DisposableEffect(localImageFile) {
        onDispose {
            if (localImageFile != null && localImageFile.exists()) localImageFile.delete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showOverlay = !showOverlay }
    ) {
        // Image fullscreen : fichier local après crop, sinon URL
        val model = if (localImageFile != null && localImageFile.exists()) localImageFile else ApiService.photoUrl(photo.url)
        AsyncImage(
            model = model,
            contentDescription = photo.filename,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay avec boutons (apparaît au tap)
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
                    title = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Edit button (bottom right)
                FloatingActionButton(
                    onClick = onEdit,
                    containerColor = AccentColor,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Éditer",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
