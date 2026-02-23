package com.toutieserver.toutienote.viewmodels

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VaultViewModel : ViewModel() {

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading

    private val _pinExists = MutableStateFlow<Boolean?>(null)
    val pinExists: StateFlow<Boolean?> = _pinExists

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _pendingGalleryDeletions = MutableStateFlow<List<Uri>>(emptyList())
    val pendingGalleryDeletions: StateFlow<List<Uri>> = _pendingGalleryDeletions

    private val _duplicateCount = MutableStateFlow(0)
    val duplicateCount: StateFlow<Int> = _duplicateCount

    private val _duplicateGroups = MutableStateFlow<List<List<Photo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<Photo>>> = _duplicateGroups

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount

    private val _scanTotal = MutableStateFlow(0)
    val scanTotal: StateFlow<Int> = _scanTotal

    private val _scanPercent = MutableStateFlow(0)
    val scanPercent: StateFlow<Int> = _scanPercent

    fun clearPendingDeletions() { _pendingGalleryDeletions.value = emptyList() }
    fun clearDuplicateCount() { _duplicateCount.value = 0 }
    fun clearMessage() { _message.value = null }
    fun clearError() { _error.value = null }

    // ── PIN ────────────────────────────────────────────────────
    fun checkPin() {
        viewModelScope.launch(Dispatchers.IO) {
            try { _pinExists.value = ApiService.pinExists() }
            catch (e: Exception) { _error.value = "Erreur serveur: ${e.message}" }
        }
    }

    fun setupPin(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.setupPin(pin)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { _error.value = "Erreur: ${e.message}" }
        }
    }

    fun verifyPin(pin: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.verifyPin(pin)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (ok) onSuccess() else onFail()
            }
        }
    }

    fun changePin(oldPin: String, newPin: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.changePin(oldPin, newPin)
                _message.value = "PIN modifié ✓"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _error.value = "Erreur: ${e.message}"
                kotlinx.coroutines.withContext(Dispatchers.Main) { onFail() }
            }
        }
    }

    // ── Albums ─────────────────────────────────────────────────
    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try { _albums.value = ApiService.getAlbums() }
            catch (e: Exception) { _error.value = "Erreur chargement: ${e.message}" }
            _loading.value = false
        }
    }

    fun createAlbum(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.createAlbum(name)
                _message.value = "Album créé ✓"
                loadAlbums()
            } catch (e: Exception) { _error.value = "Erreur: ${e.message}" }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deleteAlbum(albumId)
                _message.value = "Album supprimé"
                loadAlbums()
            } catch (e: Exception) { _error.value = "Erreur: ${e.message}" }
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.renameAlbum(albumId, name)
                _message.value = "Album renommé ✓"
                loadAlbums()
            } catch (e: Exception) { _error.value = "Erreur: ${e.message}" }
        }
    }

    fun setAlbumCover(albumId: String, photoUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.setAlbumCover(albumId, photoUrl)
                _albums.value = _albums.value.map {
                    if (it.id == albumId) it.copy(coverUrl = photoUrl) else it
                }
                _message.value = "Cover mise à jour ✓"
            } catch (e: Exception) { _error.value = "Erreur cover: ${e.message}" }
        }
    }

    fun lockAlbum(albumId: String, pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.lockAlbum(albumId, pin)
                _albums.value = _albums.value.map {
                    if (it.id == albumId) it.copy(isLocked = true) else it
                }
                _message.value = "Album verrouillé 🔒"
            } catch (e: Exception) { _error.value = "Erreur verrouillage: ${e.message}" }
        }
    }

    fun unlockAlbum(albumId: String, pin: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.unlockAlbum(albumId, pin)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (ok) {
                    _albums.value = _albums.value.map {
                        if (it.id == albumId) it.copy(isLocked = false) else it
                    }
                    _message.value = "Verrou retiré 🔓"
                    onSuccess()
                } else {
                    onFail()
                }
            }
        }
    }

    fun verifyAlbumLock(albumId: String, pin: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.verifyAlbumLock(albumId, pin)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (ok) onSuccess() else onFail()
            }
        }
    }

    fun moveAlbum(albumId: String, direction: Int) {
        val list = _albums.value.toMutableList()
        val idx = list.indexOfFirst { it.id == albumId }
        if (idx < 0) return
        val newIdx = (idx + direction).coerceIn(0, list.lastIndex)
        if (newIdx == idx) return
        val item = list.removeAt(idx)
        list.add(newIdx, item)
        _albums.value = list
        viewModelScope.launch(Dispatchers.IO) {
            try { ApiService.reorderAlbums(list.map { it.id }) }
            catch (_: Exception) { }
        }
    }

    fun reorderPhotos(albumId: String, newOrder: List<Photo>) {
        _photos.value = newOrder
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.reorderPhotos(albumId, newOrder.map { it.id })
                _message.value = "Ordre mis à jour ✓"
            } catch (e: Exception) {
                _error.value = "Erreur réordre: ${e.message}"
                _photos.value = ApiService.getPhotos(albumId)
            }
        }
    }

    // ── Photos ─────────────────────────────────────────────────
    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try { _photos.value = ApiService.getPhotos() }
            catch (e: Exception) { _error.value = "Erreur chargement: ${e.message}" }
            _loading.value = false
        }
    }

    fun loadPhotosForAlbum(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try { _photos.value = ApiService.getPhotos(albumId) }
            catch (e: Exception) { _error.value = "Erreur chargement: ${e.message}" }
            _loading.value = false
        }
    }

    private fun resolveExtension(contentResolver: ContentResolver, uri: Uri): String {
        val mime = contentResolver.getType(uri)
        return when {
            mime?.startsWith("video/") == true -> when (mime) {
                "video/quicktime" -> ".mov"
                "video/x-matroska" -> ".mkv"
                else -> ".mp4"
            }
            mime == "image/png" -> ".png"
            mime == "image/webp" -> ".webp"
            else -> ".jpg"
        }
    }

    fun importFromGallery(mediaStoreUris: List<Uri>, albumId: String, contentResolver: ContentResolver, cacheDir: File) {
        Log.d(TAG, "importFromGallery: ${mediaStoreUris.size} MediaStore URIs")
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            for (uri in mediaStoreUris) {
                try {
                    val ext = resolveExtension(contentResolver, uri)
                    val filename = "vault_${System.currentTimeMillis()}$ext"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    val result = ApiService.uploadPhoto(tempFile, filename, albumId)
                    tempFile.delete()
                    uploaded++
                    Log.d(TAG, "  uploaded: $filename (dupe=${result.duplicateOf})")
                } catch (e: Exception) {
                    Log.e(TAG, "  upload failed: ${e.message}", e)
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded fichier(s) importé(s)"
            loadPhotosForAlbum(albumId)

            if (uploaded > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "Setting pendingGalleryDeletions with ${mediaStoreUris.size} REAL MediaStore URIs")
                _pendingGalleryDeletions.value = mediaStoreUris
            }
        }
    }

    fun uploadPhotos(uris: List<Uri>, contentResolver: ContentResolver, cacheDir: File, deleteFromGallery: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            var dupes = 0
            for (uri in uris) {
                try {
                    val ext = resolveExtension(contentResolver, uri)
                    val filename = "vault_${System.currentTimeMillis()}$ext"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    val result = ApiService.uploadPhoto(tempFile, filename, null)
                    tempFile.delete()
                    uploaded++
                    if (result.duplicateOf != null) dupes++
                } catch (e: Exception) {
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded fichier(s) ajouté(s)"
            if (dupes > 0) _duplicateCount.value = dupes
            loadPhotos()

            if (deleteFromGallery && uploaded > 0) {
                requestGalleryDeletion(uris, contentResolver)
            }
        }
    }

    fun uploadPhotosToAlbum(uris: List<Uri>, albumId: String, contentResolver: ContentResolver, cacheDir: File, deleteFromGallery: Boolean = false) {
        Log.d(TAG, "uploadPhotosToAlbum: ${uris.size} uris, albumId=$albumId, delete=$deleteFromGallery")
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            var dupes = 0
            for (uri in uris) {
                Log.d(TAG, "  uploading: $uri")
                try {
                    val ext = resolveExtension(contentResolver, uri)
                    val filename = "vault_${System.currentTimeMillis()}$ext"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    Log.d(TAG, "  temp file: ${tempFile.length()} bytes")
                    val result = ApiService.uploadPhoto(tempFile, filename, albumId)
                    tempFile.delete()
                    uploaded++
                    Log.d(TAG, "  upload OK, duplicateOf=${result.duplicateOf}")
                    if (result.duplicateOf != null) dupes++
                } catch (e: Exception) {
                    Log.e(TAG, "  upload FAILED: ${e.message}", e)
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded fichier(s) ajouté(s)"
            if (dupes > 0) _duplicateCount.value = dupes
            loadPhotosForAlbum(albumId)

            if (deleteFromGallery && uploaded > 0) {
                Log.d(TAG, "requesting gallery deletion for ${uris.size} uris")
                requestGalleryDeletion(uris, contentResolver)
            } else {
                Log.d(TAG, "skip gallery deletion: delete=$deleteFromGallery uploaded=$uploaded")
            }
        }
    }

    private fun requestGalleryDeletion(sourceUris: List<Uri>, contentResolver: ContentResolver) {
        Log.d(TAG, "=== requestGalleryDeletion START ===")
        Log.d(TAG, "SDK=${Build.VERSION.SDK_INT}, uris=${sourceUris.size}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "SDK < R (30), skipping createDeleteRequest")
            return
        }

        val mediaStoreUris = mutableListOf<Uri>()

        for (uri in sourceUris) {
            Log.d(TAG, "--- Resolving: $uri")
            Log.d(TAG, "    scheme=${uri.scheme} authority=${uri.authority}")
            Log.d(TAG, "    path=${uri.path}")
            Log.d(TAG, "    pathSegments=${uri.pathSegments}")

            var displayName: String? = null
            var fileSize: Long = -1
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        displayName = c.getString(0)
                        fileSize = c.getLong(1)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "    OpenableColumns query FAILED: ${e.message}")
            }
            Log.d(TAG, "    displayName=$displayName, fileSize=$fileSize")

            val collections = listOf(
                "images" to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "video" to MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )

            var found: Uri? = null

            // Method 1: Find by display name + size in MediaStore
            if (displayName != null) {
                for ((label, col) in collections) {
                    if (found != null) break
                    try {
                        val sel = if (fileSize > 0)
                            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
                        else
                            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                        val args = if (fileSize > 0) arrayOf(displayName!!, fileSize.toString())
                                   else arrayOf(displayName!!)
                        Log.d(TAG, "    M1: querying $label where $sel args=${args.toList()}")
                        contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID), sel, args, null)?.use { c ->
                            val count = c.count
                            Log.d(TAG, "    M1: $label returned $count rows")
                            if (c.moveToFirst()) {
                                found = ContentUris.withAppendedId(col, c.getLong(0))
                                Log.d(TAG, "    M1 FOUND: $found")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "    M1 $label ERROR: ${e.message}")
                    }
                }
            }

            // Method 1b: Find by file size only
            if (found == null && fileSize > 0) {
                Log.d(TAG, "    M1b: trying size-only match ($fileSize bytes)")
                for ((label, col) in collections) {
                    if (found != null) break
                    try {
                        contentResolver.query(
                            col,
                            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                            "${MediaStore.MediaColumns.SIZE} = ?",
                            arrayOf(fileSize.toString()),
                            null
                        )?.use { c ->
                            Log.d(TAG, "    M1b: $label returned ${c.count} rows")
                            if (c.count == 1 && c.moveToFirst()) {
                                found = ContentUris.withAppendedId(col, c.getLong(0))
                                Log.d(TAG, "    M1b FOUND (unique size): $found (${c.getString(1)})")
                            } else if (c.count > 1) {
                                Log.d(TAG, "    M1b: ${c.count} files with same size, skipping (ambiguous)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "    M1b $label ERROR: ${e.message}")
                    }
                }
            }

            // Method 1c: Size match + content verification
            if (found == null && fileSize > 0) {
                Log.d(TAG, "    M1c: trying size match + content verification")
                val srcHeader = try {
                    contentResolver.openInputStream(uri)?.use { it.readNBytes(4096) }
                } catch (_: Exception) { null }

                if (srcHeader != null && srcHeader.isNotEmpty()) {
                    for ((label, col) in collections) {
                        if (found != null) break
                        try {
                            contentResolver.query(
                                col,
                                arrayOf(MediaStore.MediaColumns._ID),
                                "${MediaStore.MediaColumns.SIZE} = ?",
                                arrayOf(fileSize.toString()),
                                null
                            )?.use { c ->
                                while (c.moveToNext()) {
                                    val candidateId = c.getLong(0)
                                    val candidateUri = ContentUris.withAppendedId(col, candidateId)
                                    try {
                                        val candidateHeader = contentResolver.openInputStream(candidateUri)?.use { it.readNBytes(4096) }
                                        if (candidateHeader != null && srcHeader.contentEquals(candidateHeader)) {
                                            found = candidateUri
                                            Log.d(TAG, "    M1c FOUND (content match): $found")
                                            break
                                        }
                                    } catch (_: Exception) { }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "    M1c $label ERROR: ${e.message}")
                        }
                    }
                }
            }

            // Method 2: Extract numeric ID from picker URI path
            if (found == null) {
                try {
                    val segments = uri.pathSegments
                    val mediaIdx = segments.lastIndexOf("media")
                    val idStr = if (mediaIdx >= 0 && mediaIdx < segments.lastIndex) segments[mediaIdx + 1]
                                else uri.lastPathSegment
                    val mediaId = idStr?.toLongOrNull()
                    Log.d(TAG, "    M2: extracted mediaId=$mediaId from segments")
                    if (mediaId != null) {
                        for ((label, col) in collections) {
                            if (found != null) break
                            val msUri = ContentUris.withAppendedId(col, mediaId)
                            try {
                                contentResolver.query(msUri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                                    Log.d(TAG, "    M2: query $label/$mediaId returned ${it.count} rows")
                                    if (it.moveToFirst()) {
                                        found = msUri
                                        Log.d(TAG, "    M2 FOUND: $found")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "    M2 $label ERROR: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "    M2 path parse ERROR: ${e.message}")
                }
            }

            if (found != null) {
                Log.d(TAG, "    RESOLVED: $found")
                mediaStoreUris.add(found!!)
            } else {
                Log.e(TAG, "    FAILED TO RESOLVE: $uri")
            }
        }

        Log.d(TAG, "=== RESULT: ${mediaStoreUris.size}/${sourceUris.size} resolved ===")
        if (mediaStoreUris.isNotEmpty()) {
            Log.d(TAG, "Setting pendingGalleryDeletions: $mediaStoreUris")
            _pendingGalleryDeletions.value = mediaStoreUris
        } else {
            Log.e(TAG, "No URIs resolved, cannot delete from gallery")
            _error.value = "Impossible de résoudre les URIs pour suppression"
        }
    }

    // ── Export vers la galerie ──────────────────────────────────
    fun exportToGallery(photo: Photo, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _message.value = "Export en cours..."
                val bytes = ApiService.downloadPhotoBytes(photo.url)

                val ext = photo.filename.substringAfterLast('.', "jpg")
                val mimeType = when (ext.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val displayName = "ToutieNote_${System.currentTimeMillis()}.$ext"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ToutieNote")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val insertUri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: throw Exception("Impossible de créer l'entrée MediaStore")

                    contentResolver.openOutputStream(insertUri)?.use { out ->
                        out.write(bytes)
                    }

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(insertUri, values, null, null)
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES + "/ToutieNote"
                    )
                    dir.mkdirs()
                    val file = File(dir, displayName)
                    FileOutputStream(file).use { it.write(bytes) }

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, file.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    }
                    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }

                _message.value = "Photo exportée dans la galerie ✓"
            } catch (e: Exception) {
                _error.value = "Erreur export: ${e.message}"
            }
        }
    }

    // ── Crop: REMPLACE le fichier, garde la même position ──────
    fun uploadCroppedPhoto(file: File, originalFilename: String, albumId: String? = null, onSuccess: ((Photo?, File, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalPhoto = _photos.value.find { it.filename == originalFilename }

                if (originalPhoto != null) {
                    val newFilename = "cropped_${System.currentTimeMillis()}.jpg"
                    val replacedPhoto = ApiService.replacePhoto(originalPhoto.id, file, newFilename)
                    _message.value = "Photo rognée ✓"

                    _photos.value = _photos.value.map { p ->
                        if (p.id == originalPhoto.id) replacedPhoto else p
                    }
                    if (albumId != null) _albums.value = ApiService.getAlbums()

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onSuccess?.invoke(replacedPhoto, file, replacedPhoto.filename)
                    }
                } else {
                    val newFilename = "cropped_${System.currentTimeMillis()}_$originalFilename"
                    ApiService.uploadPhoto(file, newFilename, albumId)
                    ApiService.deletePhoto(originalFilename)
                    _message.value = "Photo rognée ✓"
                    val updated = if (albumId != null) ApiService.getPhotos(albumId) else ApiService.getPhotos()
                    val newPhoto = updated.find { it.filename == newFilename }
                    _photos.value = updated
                    if (albumId != null) _albums.value = ApiService.getAlbums()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onSuccess?.invoke(newPhoto, file, newFilename)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Erreur crop: ${e.message}"
            }
        }
    }

    // ── Duplicate Scan (ASYNC + POLLING) ───────────────────────
    fun scanDuplicates(albumId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("Doublon", "A: scanDuplicates appelé albumId=$albumId")
            _scanning.value = true
            _duplicateGroups.value = emptyList()
            _scannedCount.value = 0
            _scanTotal.value = 0
            _scanPercent.value = 0
            try {
                // ── Lancer le scan async ────────────────────────────
                Log.d("Doublon", "B: startScanAsync...")
                val (jobId, total) = ApiService.startScanAsync(albumId)
                Log.d("Doublon", "C: job=$jobId total=$total")
                _scanTotal.value = total

                // ── Poll status toutes les secondes ─────────────────
                while (true) {
                    delay(1000L)
                    try {
                        val status = ApiService.getScanStatus(jobId)
                        Log.d("Doublon", "D: poll → scanned=${status.scanned} percent=${status.percent} done=${status.done}")

                        _scannedCount.value = status.scanned
                        if (status.total > 0) _scanTotal.value = status.total
                        _scanPercent.value = status.percent

                        if (status.done) {
                            if (status.error != null) {
                                Log.e("Doublon", "E: scan error: ${status.error}")
                                _error.value = "Erreur scan: ${status.error}"
                            } else {
                                Log.d("Doublon", "E: scan OK groups=${status.groups.size}")
                                _duplicateGroups.value = status.groups
                                _scannedCount.value = status.scanned
                                _scanPercent.value = 100
                            }
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("Doublon", "D: poll failed (retry): ${e.message}")
                    }
                }

                _duplicateGroups.value.forEachIndexed { i, g ->
                    Log.d("Doublon", "F: groupe $i = ${g.size} photos: ${g.map { it.filename }.take(3)}")
                }
                Log.d("Doublon", "Z: scan terminé OK")
            } catch (e: Exception) {
                Log.e("Doublon", "Z: ERREUR", e)
                _error.value = "Erreur scan: ${e.message}"
            }
            _scanning.value = false
        }
    }

    fun cleanDuplicates(toDelete: List<Photo>, albumId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanning.value = true
            var deleted = 0
            for (photo in toDelete) {
                try {
                    ApiService.deletePhoto(photo.filename)
                    deleted++
                } catch (_: Exception) { }
            }
            _message.value = "$deleted doublon(s) supprimé(s)"
            _photos.value = _photos.value.filter { p -> toDelete.none { it.id == p.id } }
            _duplicateGroups.value = emptyList()
            _scannedCount.value = 0
            _scanTotal.value = 0
            _scanPercent.value = 0
            _scanning.value = false
        }
    }

    // ── Resize ─────────────────────────────────────────────────
    fun resizePhoto(filename: String, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.resizePhoto(filename, width, height)
                _message.value = "Resize sauvegardé ✓"
                loadPhotos()
            } catch (e: Exception) { _error.value = "Erreur resize: ${e.message}" }
        }
    }

    // ── Favorite ───────────────────────────────────────────────
    fun toggleFavorite(photo: Photo, albumId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFav = ApiService.toggleFavorite(photo.id)
                _photos.value = _photos.value.map {
                    if (it.id == photo.id) it.copy(favorite = newFav) else it
                }
            } catch (e: Exception) { _error.value = "Erreur favori: ${e.message}" }
        }
    }

    // ── Delete photo ───────────────────────────────────────────
    fun deletePhoto(filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deletePhoto(filename)
                _photos.value = _photos.value.filter { it.filename != filename }
                _message.value = "Photo supprimée"
            } catch (e: Exception) { _error.value = "Erreur: ${e.message}" }
        }
    }

    companion object {
        private const val TAG = "VaultVM"
    }
}
