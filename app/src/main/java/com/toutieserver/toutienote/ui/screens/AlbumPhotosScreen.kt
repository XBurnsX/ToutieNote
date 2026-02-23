package com.toutieserver.toutienote.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.components.PhotoCard
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPhotosScreen(
    album: Album,
    vm: VaultViewModel = viewModel(),
    onPhotoClick: (Photo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsState()
    val loading by vm.loading.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val message by vm.message.collectAsState()
    val error by vm.error.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Photo?>(null) }
    var showOptionsSheet by remember { mutableStateOf<Photo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(album.id) { vm.loadPhotosForAlbum(album.id) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* Résultat ignoré, les photos seront supprimées si l'user confirme */ }

    fun deleteFromGallery(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (_: Exception) {
                uris.forEach { try { context.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            }
        } else {
            uris.forEach { try { context.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.uploadPhotosToAlbum(uris, album.id, context.contentResolver, context.cacheDir) { uploadedUris ->
                deleteFromGallery(uploadedUris)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) photoPickerLauncher.launch("image/*")
    }

    fun pickPhotos() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        permLauncher.launch(perm)
    }

    showDeleteDialog?.let { photo ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceColor,
            icon = { Icon(Icons.Default.Warning, null, tint = DangerColor, modifier = Modifier.size(32.dp)) },
            title = { Text("Supprimer cette photo?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Elle sera définitivement supprimée.", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { vm.deletePhoto(photo.filename); showDeleteDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Annuler", color = MutedColor) }
            }
        )
    }

    showOptionsSheet?.let { photo ->
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = null },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Options",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextColor
                )
                Divider(color = BorderColor)
                OptionItem(Icons.Default.Visibility, "Voir en plein écran") {
                    onPhotoClick(photo); showOptionsSheet = null
                }
                OptionItem(Icons.Default.Delete, "Supprimer", DangerColor, DangerColor) {
                    showDeleteDialog = photo; showOptionsSheet = null
                }
            }
        }
    }

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, "Retour", tint = TextColor, modifier = Modifier.size(18.dp))
                    }
                },
                title = {
                    Column {
                        Text(album.name.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            color = MutedColor, letterSpacing = 2.sp, maxLines = 1)
                        AnimatedVisibility(visible = photos.isNotEmpty()) {
                            Text("${photos.size} photo${if (photos.size > 1) "s" else ""}",
                                fontSize = 11.sp, color = MutedColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    AnimatedVisibility(visible = uploading) {
                        CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).padding(end = 16.dp))
                    }
                    AnimatedVisibility(visible = !uploading) {
                        IconButton(onClick = ::pickPhotos) {
                            Icon(Icons.Default.AddPhotoAlternate, "Ajouter photos", tint = AccentColor)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = photos.isNotEmpty() && !uploading, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(
                    onClick = ::pickPhotos,
                    containerColor = AccentColor,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) { Icon(Icons.Default.AddPhotoAlternate, "Ajouter photos") }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(color = AccentColor, modifier = Modifier.align(Alignment.Center))
                photos.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🖼️", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Album vide", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                    Spacer(Modifier.height(8.dp))
                    Text("Ajoutez vos premières photos\nà cet album", color = MutedColor, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = ::pickPhotos,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter des photos", fontSize = 15.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoCard(photo, { onPhotoClick(photo) }, { showOptionsSheet = photo })
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    textColor: androidx.compose.ui.graphics.Color = TextColor,
    iconTint: androidx.compose.ui.graphics.Color = TextColor,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 15.sp, color = textColor)
    }
}
