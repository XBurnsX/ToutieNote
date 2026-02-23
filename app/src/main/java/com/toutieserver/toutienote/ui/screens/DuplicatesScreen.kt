package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
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
    val scanning by vm.scanning.collectAsState()
    val scannedCount by vm.scannedCount.collectAsState()
    val scanTotal by vm.scanTotal.collectAsState()
    val scanPercent by vm.scanPercent.collectAsState()
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Did user trigger a scan at least once
    var hasScanned by remember { mutableStateOf(false) }

    // Photos cochées pour suppression — cocher/décocher n'importe quelle photo, aucune obligatoire par groupe
    val toDeleteIds = remember { mutableStateSetOf<String>() }
    LaunchedEffect(groups) {
        toDeleteIds.clear()
        // Suggestion par défaut: garder la 1re de chaque groupe, marquer le reste pour suppression
        groups.forEach { group ->
            group.drop(1).forEach { photo -> toDeleteIds.add(photo.id) }
        }
    }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    val toDeleteCount = toDeleteIds.size

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
                        if (hasScanned && !scanning) {
                            Text(
                                if (groups.isNotEmpty()) "${groups.size} groupe${if (groups.size > 1) "s" else ""} · $scannedCount photos scannées"
                                else "$scannedCount photos scannées",
                                fontSize = 11.sp, color = MutedColor.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        bottomBar = {
            Surface(color = SurfaceColor, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    if (groups.isNotEmpty() && !scanning) {
                        Button(
                            onClick = {
                                val allPhotos = groups.flatten()
                                val photosToDelete = allPhotos.filter { it.id in toDeleteIds }
                                if (photosToDelete.isNotEmpty()) {
                                    vm.cleanDuplicates(photosToDelete, albumId)
                                    toDeleteIds.clear()
                                    hasScanned = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            enabled = toDeleteCount > 0
                        ) {
                            Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Nettoyer ($toDeleteCount)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (!scanning) {
                        OutlinedButton(
                            onClick = { hasScanned = true; vm.scanDuplicates(albumId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (hasScanned) "Re-scanner" else "Scanner les doublons",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                scanning -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Analyse en cours...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (scanTotal > 0) "$scannedCount / $scanTotal photos" else "Préparation...",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$scanPercent % complété",
                            fontSize = 14.sp, color = MutedColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(24.dp))
                        LinearProgressIndicator(
                            progress = (scanPercent / 100f).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = AccentColor,
                            trackColor = Surface2Color,
                        )
                    }
                }

                !hasScanned -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.FindInPage, null, tint = MutedColor.copy(alpha = 0.4f), modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Scanner vos doublons", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Analyse intelligente qui détecte les photos\nsimilaires, même si elles ont été rognées\nou recadrées.",
                            color = MutedColor, fontSize = 13.sp, textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                groups.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✨", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Aucun doublon", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Toutes vos $scannedCount photos sont uniques",
                            color = MutedColor, fontSize = 14.sp
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.forEachIndexed { groupIndex, group ->
                        item(key = "header_$groupIndex") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (groupIndex > 0) 20.dp else 4.dp, bottom = 10.dp),
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
                            val markedForDelete = photo.id in toDeleteIds

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (markedForDelete) DangerColor.copy(alpha = 0.12f) else AccentColor.copy(alpha = 0.12f))
                                    .clickable {
                                        if (photo.id in toDeleteIds) toDeleteIds.remove(photo.id)
                                        else toDeleteIds.add(photo.id)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ApiService.photoUrl(photo.thumbnailUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceColor)
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        photo.filename,
                                        fontSize = 12.sp,
                                        color = TextColor,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        photo.createdAt.take(10),
                                        fontSize = 11.sp,
                                        color = MutedColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    AnimatedVisibility(visible = !markedForDelete, enter = fadeIn(), exit = fadeOut()) {
                                        Text(
                                            "GARDER",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentColor,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    AnimatedVisibility(visible = markedForDelete, enter = fadeIn(), exit = fadeOut()) {
                                        Text(
                                            "SUPPRIMER",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DangerColor,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                if (markedForDelete) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DangerColor,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            "Supprimer",
                                            tint = BgColor,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentColor.copy(alpha = 0.4f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            "Garder",
                                            tint = BgColor,
                                            modifier = Modifier.padding(8.dp)
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
