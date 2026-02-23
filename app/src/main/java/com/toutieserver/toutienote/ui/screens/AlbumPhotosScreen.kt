package com.toutieserver.toutienote.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.components.PhotoCard
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun groupPhotosByMonth(photos: List<Photo>): List<Pair<String, List<Photo>>> {
    val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)

    return photos
        .sortedByDescending { it.createdAt }
        .groupBy { photo ->
            try {
                val date = isoParser.parse(photo.createdAt.take(19))
                if (date != null) monthFormat.format(date).replaceFirstChar { it.uppercase() }
                else "Date inconnue"
            } catch (_: Exception) { "Date inconnue" }
        }
        .toList()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumPhotosScreen(
    album: Album,
    vm: VaultViewModel = viewModel(),
    onPhotoClick: (Photo) -> Unit,
    onSlideshow: (List<Photo>) -> Unit,
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

    val pendingDeletions by vm.pendingGalleryDeletions.collectAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.clearPendingDeletions() }

    LaunchedEffect(pendingDeletions) {
        if (pendingDeletions.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = MediaStore.createDeleteRequest(context.contentResolver, pendingDeletions)
                deleteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            } catch (_: Exception) {
                vm.clearPendingDeletions()
            }
        }
    }

    // Recharger à chaque fois qu'on arrive sur cet écran
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(album.id, refreshKey) { vm.loadPhotosForAlbum(album.id) }
    // Incrémenter au retour (recomposition)
    DisposableEffect(Unit) {
        refreshKey++
        onDispose { }
    }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    // Photo/Video picker (images + videos)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.uploadPhotosToAlbum(uris, album.id, context.contentResolver, context.cacheDir)
        }
    }

    fun pickPhotos() {
        mediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    // Camera capture (never in camera roll)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            vm.uploadPhotosToAlbum(listOf(cameraUri!!), album.id, context.contentResolver, context.cacheDir)
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun takePhoto() {
        cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    // Duplicate detection warning
    val dupeCount by vm.duplicateCount.collectAsState()
    var showDupeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(dupeCount) {
        if (dupeCount > 0) showDupeDialog = true
    }

    if (showDupeDialog && dupeCount > 0) {
        AlertDialog(
            onDismissRequest = { showDupeDialog = false; vm.clearDuplicateCount() },
            containerColor = SurfaceColor,
            icon = { Icon(Icons.Default.ContentCopy, null, tint = AccentColor, modifier = Modifier.size(32.dp)) },
            title = { Text("Doublons détectés", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "$dupeCount fichier(s) similaire(s) déjà dans le vault. Ils ont été importés quand même.",
                    color = MutedColor,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDupeDialog = false; vm.clearDuplicateCount() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) { Text("OK") }
            }
        )
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
                HorizontalDivider(color = BorderColor)
                OptionItem(Icons.Default.Visibility, "Voir en plein écran") {
                    onPhotoClick(photo); showOptionsSheet = null
                }
                OptionItem(Icons.Default.Image, "Définir comme cover") {
                    vm.setAlbumCover(album.id, photo.url)
                    showOptionsSheet = null
                }
                OptionItem(Icons.Default.SaveAlt, "Exporter vers la galerie") {
                    vm.exportToGallery(photo, context.contentResolver)
                    showOptionsSheet = null
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
                        Text(album.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                        Text(
                            "${photos.size} photo${if (photos.size > 1) "s" else ""}",
                            fontSize = 11.sp,
                            color = MutedColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    if (photos.size >= 2) {
                        IconButton(onClick = { onSlideshow(photos) }) {
                            Icon(Icons.Default.Slideshow, "Slideshow", tint = AccentColor)
                        }
                    }
                    IconButton(onClick = ::takePhoto) {
                        Icon(Icons.Default.CameraAlt, "Photo", tint = AccentColor)
                    }
                    IconButton(onClick = ::pickPhotos) {
                        Icon(Icons.Default.AddPhotoAlternate, "Importer", tint = AccentColor)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(uploading, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(
                    color = AccentColor,
                    trackColor = SurfaceColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                loading -> CircularProgressIndicator(
                    color = AccentColor, modifier = Modifier.align(Alignment.Center)
                )
                photos.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("\uD83D\uDDBC\uFE0F", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Album vide", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                    Spacer(Modifier.height(8.dp))
                    Text("Ajoutez des photos pour commencer", color = MutedColor, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = ::takePhoto,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Photo", fontSize = 15.sp)
                        }
                        Button(
                            onClick = ::pickPhotos,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Importer", fontSize = 15.sp)
                        }
                    }
                }
                else -> {
                    val groupedPhotos = remember(photos) { groupPhotosByMonth(photos) }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedPhotos.forEach { (monthLabel, monthPhotos) ->
                            item(
                                key = "header_$monthLabel",
                                span = { GridItemSpan(3) }
                            ) {
                                Text(
                                    text = monthLabel,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BgColor)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextColor
                                )
                            }

                            items(monthPhotos, key = { it.id }) { photo ->
                                PhotoCard(
                                    photo = photo,
                                    onClick = { onPhotoClick(photo) },
                                    onLongClick = { showOptionsSheet = photo }
                                )
                            }
                        }
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
