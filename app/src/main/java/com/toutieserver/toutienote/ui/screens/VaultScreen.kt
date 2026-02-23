package com.toutieserver.toutienote.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.components.PhotoCard
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    vm: VaultViewModel = viewModel(),
    onPhotoClick: (Photo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val photos   by vm.photos.collectAsState()
    val loading  by vm.loading.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val message  by vm.message.collectAsState()
    val error    by vm.error.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Photo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadPhotos() }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.uploadPhotos(uris, context.contentResolver, context.cacheDir)
        }
    }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) photoPickerLauncher.launch("image/*")
        else { /* show error */ }
    }

    fun pickPhotos() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        permLauncher.launch(perm)
    }

    // Delete dialog
    showDeleteDialog?.let { photo ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceColor,
            title = { Text("Supprimer cette photo?") },
            text  = { Text("Elle sera supprimée du vault.", color = MutedColor, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePhoto(photo.filename)
                    showDeleteDialog = null
                }) { Text("Supprimer", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Retour",
                            tint = TextColor, modifier = Modifier.size(18.dp))
                    }
                },
                title = {
                    Text("VAULT", fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, color = MutedColor, letterSpacing = 3.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    if (uploading) {
                        CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).padding(end = 16.dp))
                    } else {
                        IconButton(onClick = ::pickPhotos) {
                            Icon(Icons.Default.AddPhotoAlternate,
                                contentDescription = "Ajouter photos", tint = AccentColor)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    color = AccentColor, modifier = Modifier.align(Alignment.Center))
                photos.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🖼️", fontSize = 52.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Vault vide", color = MutedColor,
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = ::pickPhotos,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    ) { Text("Ajouter des photos") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoCard(
                            photo = photo,
                            onClick = { onPhotoClick(photo) },
                            onLongClick = { showDeleteDialog = photo },
                        )
                    }
                }
            }
        }
    }
}
