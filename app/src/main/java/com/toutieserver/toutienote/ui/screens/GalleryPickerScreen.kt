package com.toutieserver.toutienote.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.toutieserver.toutienote.ui.theme.*

private const val TAG = "GalleryPicker"

data class DeviceMedia(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucketName: String,
    val dateAdded: Long,
    val isVideo: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPickerScreen(
    onConfirm: (List<Uri>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var allMedia by remember { mutableStateOf<List<DeviceMedia>>(emptyList()) }
    var albums by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<Long, Uri>() }
    var loading by remember { mutableStateOf(true) }
    var debugInfo by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionsNeeded = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(permissionsNeeded.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
        permissionDenied = !hasPermission
        if (!hasPermission) loading = false
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            permissionLauncher.launch(permissionsNeeded)
            return@LaunchedEffect
        }
        loading = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val items = mutableListOf<DeviceMedia>()
            val diag = StringBuilder()
            val sdk = Build.VERSION.SDK_INT
            diag.append("SDK=$sdk\n")

            // Dump ALL files in MediaStore to understand what types exist
            try {
                val filesUri = if (sdk >= 29) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    filesUri,
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DATE_ADDED,
                    ),
                    null, null,
                    "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                    val typeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)

                    diag.append("Files total: ${cursor.count}\n")
                    Log.d(TAG, "MediaStore.Files total: ${cursor.count}")

                    val typeCounts = mutableMapOf<Int, Int>()
                    val mimeSamples = mutableMapOf<Int, MutableSet<String>>()
                    var logged = 0

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "null" else "null"
                        val mediaType = if (typeCol >= 0) cursor.getInt(typeCol) else -1
                        val bucket = if (bucketCol >= 0) (try { cursor.getString(bucketCol) } catch (_: Exception) { null }) ?: "" else ""
                        val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                        typeCounts[mediaType] = (typeCounts[mediaType] ?: 0) + 1
                        mimeSamples.getOrPut(mediaType) { mutableSetOf() }.let { if (it.size < 3) it.add(mime) }

                        if (logged < 10) {
                            Log.d(TAG, "  FILE: id=$id type=$mediaType mime=$mime name=$name bucket=$bucket")
                            logged++
                        }

                        val isImage = mime.startsWith("image/") || mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                        val isVideo = mime.startsWith("video/") || mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                        if (isImage || isVideo) {
                            val contentUri = if (isVideo) {
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            } else {
                                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            }
                            items.add(DeviceMedia(
                                id = id,
                                uri = contentUri,
                                displayName = name,
                                bucketName = bucket.ifEmpty { if (isVideo) "Vidéos" else "Photos" },
                                dateAdded = date,
                                isVideo = isVideo,
                            ))
                        }
                    }

                    for ((type, count) in typeCounts.toSortedMap()) {
                        val samples = mimeSamples[type]?.joinToString(", ") ?: ""
                        diag.append("type=$type: $count ($samples)\n")
                        Log.d(TAG, "MEDIA_TYPE=$type count=$count mimes=[$samples]")
                    }
                } ?: diag.append("Files query: NULL\n")
            } catch (e: Exception) {
                Log.e(TAG, "Files dump CRASHED", e)
                diag.append("CRASH: ${e.message}\n")
            }

            val imgCount = items.count { !it.isVideo }
            val vidCount = items.count { it.isVideo }
            diag.append("Result: $imgCount img, $vidCount vid")
            Log.d(TAG, "FINAL: $imgCount images, $vidCount videos, ${items.size} total")

            items.sortByDescending { it.dateAdded }
            allMedia = items
            albums = items.map { it.bucketName }.distinct().sorted()
            debugInfo = diag.toString()
            loading = false
        }
    }

    val filteredMedia = remember(allMedia, selectedAlbum) {
        if (selectedAlbum == null) allMedia
        else allMedia.filter { it.bucketName == selectedAlbum }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Fermer", tint = TextColor, modifier = Modifier.size(20.dp))
                    }
                },
                title = {
                    Column {
                        Text("GALERIE", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MutedColor, letterSpacing = 3.sp)
                        Text(
                            if (selected.isEmpty()) "${filteredMedia.size} éléments"
                            else "${selected.size} sélectionné${if (selected.size > 1) "s" else ""}",
                            fontSize = 11.sp,
                            color = if (selected.isNotEmpty()) AccentColor else MutedColor.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    if (filteredMedia.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selected.size == filteredMedia.size) {
                                    selected.clear()
                                } else {
                                    filteredMedia.forEach { m -> selected[m.id] = m.uri }
                                }
                            }
                        ) {
                            Text(
                                if (selected.size == filteredMedia.size) "Tout désélectionner" else "Tout sélectionner",
                                fontSize = 12.sp,
                                color = AccentColor
                            )
                        }
                    }
                    if (selected.isNotEmpty()) {
                        Button(
                            onClick = { onConfirm(selected.values.toList()) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importer (${selected.size})", fontSize = 13.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (albums.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedAlbum == null) 0 else albums.indexOf(selectedAlbum) + 1,
                    containerColor = SurfaceColor,
                    contentColor = TextColor,
                    edgePadding = 12.dp,
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedAlbum == null,
                        onClick = { selectedAlbum = null },
                        text = {
                            Text(
                                "Tout",
                                fontSize = 13.sp,
                                fontWeight = if (selectedAlbum == null) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedAlbum == null) AccentColor else MutedColor
                            )
                        }
                    )
                    albums.forEach { album ->
                        Tab(
                            selected = selectedAlbum == album,
                            onClick = { selectedAlbum = album },
                            text = {
                                Text(
                                    album,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedAlbum == album) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedAlbum == album) AccentColor else MutedColor
                                )
                            }
                        )
                    }
                }
            }

            if (permissionDenied) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = MutedColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Permission photos requise", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Autorise l'accès pour importer des photos", color = MutedColor, fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permissionsNeeded) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Autoriser")
                        }
                    }
                }
            } else if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentColor)
                }
            } else if (filteredMedia.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = MutedColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Aucun média trouvé", color = MutedColor, fontSize = 16.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMedia, key = { it.id }) { media ->
                        val isSelected = selected.containsKey(media.id)
                        val borderColor by animateColorAsState(
                            if (isSelected) AccentColor else Color.Transparent, label = "border"
                        )

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .border(3.dp, borderColor, RoundedCornerShape(2.dp))
                                .clickable {
                                    if (isSelected) selected.remove(media.id)
                                    else selected[media.id] = media.uri
                                }
                        ) {
                            val imageModel = if (media.isVideo) {
                                ImageRequest.Builder(context)
                                    .data(media.uri)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .size(256)
                                    .build()
                            } else {
                                ImageRequest.Builder(context)
                                    .data(media.uri)
                                    .crossfade(true)
                                    .size(256)
                                    .build()
                            }

                            AsyncImage(
                                model = imageModel,
                                contentDescription = media.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (media.isVideo) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))
                                )
                                Icon(
                                    Icons.Default.PlayCircleFilled, null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) AccentColor else Color.Black.copy(alpha = 0.4f))
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
