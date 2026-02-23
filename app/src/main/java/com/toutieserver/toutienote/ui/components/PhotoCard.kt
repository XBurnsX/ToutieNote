package com.toutieserver.toutienote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCard(
    photo: Photo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2Color)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        var loading by remember(photo.id) { mutableStateOf(true) }

        AsyncImage(
            model = ApiService.photoUrl(photo.url),
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
    }
}
