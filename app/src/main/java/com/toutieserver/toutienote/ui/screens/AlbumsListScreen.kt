package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsListScreen(
    vm: VaultViewModel = viewModel(),
    onAlbumClick: (Album) -> Unit,
    onBack: () -> Unit,
) {
    val albums by vm.albums.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val error by vm.error.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf<Album?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Album?>(null) }
    var showRenameDialog by remember { mutableStateOf<Album?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadAlbums() }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    // Create dialog
    if (showCreateDialog) {
        var albumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Nouvel album", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text("Nom de l'album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = AccentColor,
                        cursorColor = AccentColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (albumName.isNotBlank()) {
                            vm.createAlbum(albumName)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    // Rename dialog
    showRenameDialog?.let { album ->
        var newName by remember { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            containerColor = SurfaceColor,
            title = { Text("Renommer l'album", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = AccentColor,
                        cursorColor = AccentColor,
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            vm.renameAlbum(album.id, newName)
                            showRenameDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) { Text("Renommer") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    // Delete confirmation
    showDeleteDialog?.let { album ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceColor,
            icon = { Icon(Icons.Default.Warning, null, tint = DangerColor, modifier = Modifier.size(32.dp)) },
            title = { Text("Supprimer ${album.name}?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Toutes les photos (${album.photoCount}) seront définitivement supprimées.",
                    color = MutedColor,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteAlbum(album.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    // Options bottom sheet
    showOptionsSheet?.let { album ->
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = null },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // Album info header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface2Color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (album.coverUrl != null) {
                            AsyncImage(
                                model = ApiService.photoUrl(album.coverUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("📁", fontSize = 32.sp)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            album.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextColor
                        )
                        Text(
                            "${album.photoCount} photo${if (album.photoCount > 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = MutedColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                // Options
                OptionItem(
                    icon = Icons.Default.Edit,
                    text = "Renommer",
                    onClick = {
                        showRenameDialog = album
                        showOptionsSheet = null
                    }
                )
                OptionItem(
                    icon = Icons.Default.Delete,
                    text = "Supprimer",
                    textColor = DangerColor,
                    iconTint = DangerColor,
                    onClick = {
                        showDeleteDialog = album
                        showOptionsSheet = null
                    }
                )
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
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Retour",
                            tint = TextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            "VAULT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MutedColor,
                            letterSpacing = 3.sp
                        )
                        AnimatedVisibility(visible = albums.isNotEmpty()) {
                            Text(
                                "${albums.size} album${if (albums.size > 1) "s" else ""}",
                                fontSize = 11.sp,
                                color = MutedColor.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Nouvel album",
                            tint = AccentColor
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = albums.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = AccentColor,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) {
                    Icon(Icons.Default.Add, "Nouvel album")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    color = AccentColor,
                    modifier = Modifier.align(Alignment.Center)
                )
                albums.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📁", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Aucun album",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Créez votre premier album pour\norganiser vos photos",
                        color = MutedColor,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Créer un album", fontSize = 15.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                            onLongClick = { showOptionsSheet = album }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface2Color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cover photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUrl != null) {
                    AsyncImage(
                        model = ApiService.photoUrl(album.coverUrl),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("📁", fontSize = 56.sp)
                }

                // Photo count badge
                if (album.photoCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "${album.photoCount}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Album name
            Text(
                text = album.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextColor,
                maxLines = 1
            )

            // Metadata
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MutedColor
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${album.photoCount} photo${if (album.photoCount > 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MutedColor,
                    fontFamily = FontFamily.Monospace
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            fontSize = 15.sp,
            color = textColor
        )
    }
}
