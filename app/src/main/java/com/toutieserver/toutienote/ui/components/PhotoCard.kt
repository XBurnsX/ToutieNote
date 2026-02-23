package com.toutieserver.toutienote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: Photo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDragging: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
) {
    val elevation by androidx.compose.animation.core.animateDpAsState(
        if (isDragging) 8.dp else 0.dp,
        label = "drag_elevation"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .shadow(elevation, RoundedCornerShape(8.dp))
            .background(Surface2Color)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        var loading by remember(photo.url) { mutableStateOf(true) }
        val context = LocalContext.current
        val isVideo = photo.mediaType.startsWith("video")

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(ApiService.photoUrl(photo.thumbnailUrl))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(150)
                .build(),
            contentDescription = photo.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onLoading = { loading = true },
            onSuccess = { loading = false },
            onError = { loading = false }
        )

        if (loading) {
            CircularProgressIndicator(
                color = AccentColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }

        if (isVideo) {
            Icon(
                Icons.Default.PlayCircleFilled,
                contentDescription = "Vidéo",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(36.dp)
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    "VID",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (photo.favorite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favori",
                tint = Color(0xFFFF4D6A),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
            )
        }

        if (isSelectionMode) {
            Icon(
                if (isSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Sélectionné" else "Non sélectionné",
                tint = if (isSelected) AccentColor else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
            )
        }
    }
}
