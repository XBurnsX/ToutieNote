package com.toutieserver.toutienote.ui.screens

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.theme.*
import io.moyuru.cropify.Cropify
import io.moyuru.cropify.CropifyOption
import io.moyuru.cropify.CropifySize
import io.moyuru.cropify.rememberCropifyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.toutieserver.toutienote.viewmodels.VaultViewModel
import coil.compose.AsyncImagePainter
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditScreen(
    photo: Photo,
    localImageFile: File? = null,
    vm: VaultViewModel = viewModel(),
    onBack: () -> Unit,
    onCropSaved: (Photo?, java.io.File, String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val message by vm.message.collectAsState()
    val error   by vm.error.collectAsState()

    var saving by remember { mutableStateOf(false) }
    var imageModel: Any by remember(photo.id, localImageFile) {
        mutableStateOf(
            if (localImageFile != null && localImageFile.exists()) localImageFile as Any
            else ApiService.photoUrl(photo.url) + "?t=${System.currentTimeMillis()}" as Any
        )
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCropSheet by remember { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropRatioX by remember { mutableFloatStateOf(0f) }
    var cropRatioY by remember { mutableFloatStateOf(0f) }

    var wText by remember { mutableStateOf("") }
    var hText by remember { mutableStateOf("") }
    var keepRatio by remember { mutableStateOf(true) }
    var ratio by remember { mutableDoubleStateOf(1.0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val cropifyState = rememberCropifyState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            if (localImageFile == null) imageModel = ApiService.photoUrl(photo.url) + "?t=${System.currentTimeMillis()}"
            saving = false
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(error) {
        error?.let { saving = false; snackbarHostState.showSnackbar(it); vm.clearError() }
    }

    fun launchCrop(ratioX: Float = 0f, ratioY: Float = 0f) {
        saving = true
        Thread {
            try {
                val bitmap = if (localImageFile != null && localImageFile.exists()) {
                    BitmapFactory.decodeFile(localImageFile.absolutePath)
                        ?: throw java.io.IOException("Image invalide")
                } else {
                    val url = imageModel as? String ?: ApiService.photoUrl(photo.url)
                    val client = OkHttpClient()
                    val req = Request.Builder().url(url).build()
                    val response = client.newCall(req).execute()
                    if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                    val bytes = response.body?.bytes() ?: throw java.io.IOException("Réponse vide")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw java.io.IOException("Image invalide")
                }
                Handler(Looper.getMainLooper()).post {
                    cropBitmap = bitmap
                    cropRatioX = ratioX
                    cropRatioY = ratioY
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { saving = false }
            }
        }.start()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Supprimer cette photo?") },
            text  = { Text("Elle sera supprimée du vault.", color = MutedColor, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deletePhoto(photo.filename); showDeleteDialog = false; onBack() }) {
                    Text("Supprimer", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler", color = MutedColor) }
            }
        )
    }

    if (showCropSheet) {
        CropOptionsSheet(
            onSelect = { ratioX, ratioY -> showCropSheet = false; launchCrop(ratioX, ratioY) },
            onDismiss = { showCropSheet = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
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
                    Text(photo.filename, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MutedColor)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    if (saving) {
                        CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).padding(end = 16.dp))
                    } else {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer",
                                tint = DangerColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF0A0A0C)),
                contentAlignment = Alignment.Center
            ) {
                var loading by remember { mutableStateOf(true) }
                AsyncImage(
                    model = imageModel,
                    contentDescription = photo.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = { loading = true },
                    onError = { loading = false },
                    onSuccess = { state: AsyncImagePainter.State.Success ->
                        loading = false
                        val w = state.painter.intrinsicSize.width
                        val h = state.painter.intrinsicSize.height
                        if (h > 0f) {
                            ratio = (w / h).toDouble()
                            if (wText.isEmpty()) wText = w.toInt().toString()
                            if (hText.isEmpty()) hText = h.toInt().toString()
                        }
                    }
                )
                if (loading) {
                    CircularProgressIndicator(
                        color = AccentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceColor).padding(16.dp),
            ) {
                Button(
                    onClick = { showCropSheet = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("✂️  Rogner") }

                Spacer(Modifier.height(14.dp))
                Text("RESIZE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    color = MutedColor, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("50%" to 0.5, "75%" to 0.75, "150%" to 1.5, "200%" to 2.0).forEach { (label, factor) ->
                        PresetChip(label) {
                            val w = wText.toDoubleOrNull() ?: 0.0
                            val h = hText.toDoubleOrNull() ?: 0.0
                            if (w > 0 && h > 0) {
                                wText = (w * factor).toInt().toString()
                                hText = (h * factor).toInt().toString()
                            }
                        }
                    }
                    PresetChip("4K") {
                        if (keepRatio && ratio > 0) {
                            hText = "2160"; wText = (2160 * ratio).toInt().toString()
                        } else { wText = "3840"; hText = "2160" }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = wText,
                        onValueChange = { wText = it
                            if (keepRatio && ratio > 0) {
                                it.toDoubleOrNull()?.let { w -> hText = (w / ratio).toInt().toString() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Largeur px", fontSize = 11.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextColor),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentColor, unfocusedBorderColor = BorderColor,
                            focusedLabelColor = AccentColor, cursorColor = AccentColor,
                        )
                    )

                    IconButton(
                        onClick = { keepRatio = !keepRatio },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (keepRatio) AccentColor.copy(alpha = 0.2f) else Surface2Color)
                            .border(1.dp, if (keepRatio) AccentColor else BorderColor, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            if (keepRatio) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = "Garder ratio",
                            tint = if (keepRatio) AccentColor else MutedColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedTextField(
                        value = hText,
                        onValueChange = { hText = it
                            if (keepRatio && ratio > 0) {
                                it.toDoubleOrNull()?.let { h -> wText = (h * ratio).toInt().toString() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Hauteur px", fontSize = 11.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextColor),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentColor, unfocusedBorderColor = BorderColor,
                            focusedLabelColor = AccentColor, cursorColor = AccentColor,
                        )
                    )

                    Button(
                        onClick = {
                            val w = wText.toIntOrNull()
                            val h = hText.toIntOrNull()
                            if (w != null && h != null && w > 0 && h > 0) {
                                saving = true; vm.resizePhoto(photo.filename, w, h)
                            }
                        },
                        enabled = !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Appliquer", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    cropBitmap?.let { bitmap ->
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            val cropOption = remember(cropRatioX, cropRatioY) {
                CropifyOption(
                    frameSize = if (cropRatioX > 0f && cropRatioY > 0f)
                        CropifySize.FixedAspectRatio(cropRatioY / cropRatioX)
                    else null,
                    frameColor = AccentColor,
                    maskColor = androidx.compose.ui.graphics.Color.Black,
                    maskAlpha = 0.6f,
                    backgroundColor = androidx.compose.ui.graphics.Color.Black
                )
            }
            Cropify(
                bitmap = bitmap.asImageBitmap(),
                state = cropifyState,
                onImageCropped = { imageBitmap ->
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            val f = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                            java.io.FileOutputStream(f).use { out ->
                                imageBitmap.asAndroidBitmap()
                                    .compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            f
                        }
                        vm.uploadCroppedPhoto(file, photo.filename, photo.albumId) { newPhoto, localFile, newFilename ->
                            if (!bitmap.isRecycled) bitmap.recycle()
                            cropBitmap = null
                            saving = false
                            onCropSaved(newPhoto, localFile, newFilename)
                        }
                    }
                },
                option = cropOption,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(SurfaceColor.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        if (!bitmap.isRecycled) bitmap.recycle()
                        cropBitmap = null
                        saving = false
                    }) {
                        Text("Annuler", color = MutedColor)
                    }
                    Button(
                        onClick = { cropifyState.crop() },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Valider", Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Valider")
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2Color)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CropOptionsSheet(
    onSelect: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    var customX by remember { mutableStateOf("") }
    var customY by remember { mutableStateOf("") }

    val presets = listOf(
        Triple("⬜ Libre",  0f,  0f),
        Triple("◻️ Carré",  1f,  1f),
        Triple("📷 4:3",   4f,  3f),
        Triple("🖼️ 3:2",  3f,  2f),
        Triple("🖥️ 16:9", 16f, 9f),
        Triple("📱 9:16",  9f, 16f),
        Triple("📱 9:20",  9f, 20f),
        Triple("📲 20:9", 20f,  9f),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("FORMAT DE ROGNAGE", fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, color = MutedColor, letterSpacing = 2.sp)
            Spacer(Modifier.height(20.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                presets.forEach { (label, x, y) ->
                    CropPresetChip(label) { onSelect(x, y) }
                }
                CropPresetChip("✏️ Perso") { showCustom = !showCustom }
            }

            if (showCustom) {
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customX, onValueChange = { customX = it },
                        modifier = Modifier.weight(1f), label = { Text("Ratio X") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextColor),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentColor,
                            unfocusedBorderColor = BorderColor, focusedLabelColor = AccentColor, cursorColor = AccentColor)
                    )
                    Text(":", color = MutedColor, fontSize = 20.sp)
                    OutlinedTextField(
                        value = customY, onValueChange = { customY = it },
                        modifier = Modifier.weight(1f), label = { Text("Ratio Y") },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextColor),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentColor,
                            unfocusedBorderColor = BorderColor, focusedLabelColor = AccentColor, cursorColor = AccentColor)
                    )
                    Button(
                        onClick = {
                            val x = customX.toFloatOrNull()
                            val y = customY.toFloatOrNull()
                            if (x != null && y != null) onSelect(x, y)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(14.dp),
                    ) { Icon(Icons.Default.Check, contentDescription = "OK", modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CropPresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2Color)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextColor)
    }
}
