package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    vm: VaultViewModel,
    albumId: String? = null,
    onBack: () -> Unit,
) {
    val groups by vm.duplicateGroups.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Track which photo to KEEP per group (index in group)
    val kept = remember(groups) {
        mutableStateMapOf<Int, String>().apply {
            groups.forEachIndexed { i, group ->
                // Default: keep the first (oldest) photo
                put(i, group.first().id)
            }
        }
    }

    LaunchedEffect(Unit) { vm.loadDuplicates(albumId) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    val toDeleteCount = groups.flatMapIndexed { i, group ->
        group.filter { it.id != kept[i] }
    }.size

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
                        Text("DOUBLONS", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MutedColor, letterSpacing = 3.sp)
                        if (groups.isNotEmpty()) {
                            Text(
                                "${groups.size} groupe${if (groups.size > 1) "s" else ""} trouvé${if (groups.size > 1) "s" else ""}",
                                fontSize = 11.sp, color = MutedColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        bottomBar = {
            if (groups.isNotEmpty()) {
                Surface(
                    color = SurfaceColor,
                    tonalElevation = 8.dp,
                ) {
                    Button(
                        onClick = {
                            val photosToDelete = groups.flatMapIndexed { i, group ->
                                group.filter { it.id != kept[i] }
                            }
                            if (photosToDelete.isNotEmpty()) {
                                vm.cleanDuplicates(photosToDelete, albumId)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        enabled = toDeleteCount > 0
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nettoyer ($toDeleteCount)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    color = AccentColor, modifier = Modifier.align(Alignment.Center)
                )
                groups.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✨", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Aucun doublon", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toutes vos photos sont uniques",
                        color = MutedColor, fontSize = 14.sp
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEachIndexed { groupIndex, group ->
                        item(key = "header_$groupIndex") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (groupIndex > 0) 16.dp else 0.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                                Text(
                                    "  Groupe ${groupIndex + 1} · ${group.size} photos  ",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MutedColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                            }
                        }

                        itemsIndexed(group, key = { _, photo -> photo.id }) { _, photo ->
                            val isKept = kept[groupIndex] == photo.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isKept) AccentColor.copy(alpha = 0.1f) else Surface2Color)
                                    .clickable { kept[groupIndex] = photo.id }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                AsyncImage(
                                    model = ApiService.photoUrl(photo.thumbnailUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceColor)
                                )

                                Spacer(Modifier.width(12.dp))

                                // Info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        photo.filename,
                                        fontSize = 13.sp,
                                        color = TextColor,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                    Text(
                                        photo.createdAt.take(10),
                                        fontSize = 11.sp,
                                        color = MutedColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Checkbox
                                if (isKept) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentColor,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            "Garder",
                                            tint = TextColor,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DangerColor.copy(alpha = 0.2f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            "Supprimer",
                                            tint = DangerColor,
                                            modifier = Modifier.padding(6.dp)
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
