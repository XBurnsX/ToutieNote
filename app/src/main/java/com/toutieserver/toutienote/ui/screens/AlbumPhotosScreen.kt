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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.DragIndicator
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun groupPhotosByMonth(photos: List<Photo>): List<Pair<String, List<Photo>>> {
    val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)

    return photos
        .sortedWith(compareByDescending<Photo> { it.favorite }.thenByDescending { it.createdAt })
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
    onDuplicates: () -> Unit = {},
    onImportFromGallery: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsState()
    val loading by vm.loading.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val message by vm.message.collectAsState()
    val error by vm.error.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Photo?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf<Photo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateSetOf<String>() }
    var showMenu by remember { mutableStateOf(false) }

    val pendingDeletions by vm.pendingGalleryDeletions.collectAsState()
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        android.util.Log.d("VaultUI", "deleteRequest result: ${result.resultCode}")
        vm.clearPendingDeletions()
    }

    LaunchedEffect(pendingDeletions) {
        if (pendingDeletions.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.util.Log.d("VaultUI", "createDeleteRequest for ${pendingDeletions.size} URIs: $pendingDeletions")
            try {
                val intent = MediaStore.createDeleteRequest(context.contentResolver, pendingDeletions)
                android.util.Log.d("VaultUI", "createDeleteRequest OK, launching intent")
                deleteLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            } catch (e: Exception) {
                android.util.Log.e("VaultUI", "createDeleteRequest FAILED", e)
                snackbarHostState.showSnackbar("Erreur suppression galerie: ${e.message}")
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

    fun pickPhotos() = onImportFromGallery()

    // Camera capture (never in camera roll — no delete needed)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            vm.uploadPhotosToAlbum(listOf(cameraUri!!), album.id, context.contentResolver, context.cacheDir, deleteFromGallery = false)
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

    // Duplicate detection snackbar
    val dupeCount by vm.duplicateCount.collectAsState()
    LaunchedEffect(dupeCount) {
        if (dupeCount > 0) {
            snackbarHostState.showSnackbar("$dupeCount doublon(s) détecté(s) — utilisez l'outil Doublons pour nettoyer")
            vm.clearDuplicateCount()
        }
    }

    if (showDeleteSelectedDialog) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            containerColor = SurfaceColor,
            icon = { Icon(Icons.Default.Warning, null, tint = DangerColor, modifier = Modifier.size(32.dp)) },
            title = { Text("Supprimer $count photo(s)?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Elles seront définitivement supprimées.", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        photos.filter { it.id in selectedIds }.forEach { vm.deletePhoto(it.filename) }
                        selectedIds.clear()
                        isSelectionMode = false
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Annuler", color = MutedColor) }
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
                OptionItem(
                    if (photo.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (photo.favorite) "Retirer des favoris" else "Ajouter aux favoris",
                    iconTint = if (photo.favorite) Color(0xFFFF4D6A) else MutedColor,
                ) {
                    vm.toggleFavorite(photo, album.id); showOptionsSheet = null
                }
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
                    // Gauche : mode sélection + Tout sélectionner
                    IconButton(
                        onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedIds.clear()
                        }
                    ) {
                        Icon(
                            if (isSelectionMode) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (isSelectionMode) "Quitter la sélection" else "Sélectionner",
                            tint = AccentColor
                        )
                    }
                    if (isSelectionMode) {
                        TextButton(onClick = {
                            if (selectedIds.size == photos.size) selectedIds.clear()
                            else selectedIds.addAll(photos.map { it.id })
                        }) {
                            Text(
                                if (selectedIds.size == photos.size) "Tout désélectionner" else "Tout sélectionner",
                                fontSize = 12.sp,
                                color = AccentColor
                            )
                        }
                    }
                    // Centre : ajout photo
                    IconButton(onClick = ::pickPhotos) {
                        Icon(Icons.Default.AddPhotoAlternate, "Ajouter des photos", tint = AccentColor)
                    }
                    // Droite : menu 3 points
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = AccentColor)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Appareil photo") },
                                onClick = { takePhoto(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, null, tint = AccentColor) }
                            )
                            DropdownMenuItem(
                                text = { Text("Lecture") },
                                onClick = { onSlideshow(photos); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Slideshow, null, tint = AccentColor) }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan de doublons") },
                                onClick = { onDuplicates(); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = AccentColor) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                selectedIds.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = SurfaceColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedIds.size} sélectionnée(s)",
                            fontSize = 14.sp,
                            color = MutedColor
                        )
                        TextButton(onClick = {
                            photos.filter { it.id in selectedIds }.forEach {
                                vm.exportToGallery(it, context.contentResolver)
                            }
                            scope.launch { snackbarHostState.showSnackbar("Export terminé") }
                            selectedIds.clear()
                            isSelectionMode = false
                        }) {
                            Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(20.dp), tint = AccentColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Exporter", color = AccentColor)
                        }
                        TextButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp), tint = DangerColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Supprimer", color = DangerColor)
                        }
                    }
                }
            }
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
                    val hapticFeedback = LocalHapticFeedback.current
                    val lazyGridState = rememberLazyGridState()
                    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
                        val newOrder = photos.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        vm.reorderPhotos(album.id, newOrder)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = lazyGridState,
                        contentPadding = PaddingValues(
                            start = 4.dp,
                            top = 8.dp,
                            end = 4.dp,
                            bottom = if (selectedIds.isNotEmpty()) 72.dp else 8.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            ReorderableItem(reorderableLazyGridState, key = photo.id) { isDragging ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    PhotoCard(
                                        photo = photo,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (photo.id in selectedIds) selectedIds.remove(photo.id)
                                                else selectedIds.add(photo.id)
                                            } else {
                                                onPhotoClick(photo)
                                            }
                                        },
                                        onLongClick = { if (!isSelectionMode) showOptionsSheet = photo },
                                        isDragging = isDragging,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = photo.id in selectedIds
                                    )
                                    if (!isSelectionMode) {
                                        IconButton(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .size(32.dp)
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                            onClick = {}
                                        ) {
                                            Icon(
                                                Icons.Default.DragIndicator,
                                                contentDescription = "Réordonner",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
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
