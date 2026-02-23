package com.toutieserver.toutienote.viewmodels

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import kotlinx.coroutines.Dispatchers
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

    fun clearPendingDeletions() { _pendingGalleryDeletions.value = emptyList() }

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
                _pinExists.value = true
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { _error.value = "Erreur setup PIN: ${e.message}" }
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
                val album = ApiService.createAlbum(name)
                _albums.value = listOf(album) + _albums.value
                _message.value = "Album créé ✓"
            } catch (e: Exception) { _error.value = "Erreur création: ${e.message}" }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deleteAlbum(albumId)
                _albums.value = _albums.value.filter { it.id != albumId }
                _message.value = "Album supprimé ✓"
            } catch (e: Exception) { _error.value = "Erreur suppression: ${e.message}" }
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.renameAlbum(albumId, name)
                _albums.value = _albums.value.map {
                    if (it.id == albumId) it.copy(name = name) else it
                }
                _message.value = "Album renommé ✓"
            } catch (e: Exception) { _error.value = "Erreur renommage: ${e.message}" }
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

    fun uploadPhotos(uris: List<Uri>, contentResolver: ContentResolver, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            val failedDeletions = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    val filename = "vault_${System.currentTimeMillis()}.jpg"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    ApiService.uploadPhoto(tempFile, filename, null)
                    tempFile.delete()
                    uploaded++
                    if (!tryDeleteFromGallery(contentResolver, uri)) {
                        resolveMediaStoreUri(contentResolver, uri)?.let { failedDeletions.add(it) }
                    }
                } catch (e: Exception) {
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded photo(s) ajoutée(s) ✓"
            loadPhotos()
            if (failedDeletions.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                _pendingGalleryDeletions.value = failedDeletions
            }
        }
    }

    fun uploadPhotosToAlbum(uris: List<Uri>, albumId: String, contentResolver: ContentResolver, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            val failedDeletions = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    val filename = "vault_${System.currentTimeMillis()}.jpg"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    ApiService.uploadPhoto(tempFile, filename, albumId)
                    tempFile.delete()
                    uploaded++
                    if (!tryDeleteFromGallery(contentResolver, uri)) {
                        resolveMediaStoreUri(contentResolver, uri)?.let { failedDeletions.add(it) }
                    }
                } catch (e: Exception) {
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded photo(s) ajoutée(s) ✓"
            loadPhotosForAlbum(albumId)
            if (failedDeletions.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                _pendingGalleryDeletions.value = failedDeletions
            }
        }
    }

    private fun tryDeleteFromGallery(contentResolver: ContentResolver, uri: Uri): Boolean {
        try {
            if (contentResolver.delete(uri, null, null) > 0) return true
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) { }

        try {
            val msUri = resolveMediaStoreUri(contentResolver, uri) ?: return false
            if (contentResolver.delete(msUri, null, null) > 0) return true
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) { }

        return false
    }

    private fun resolveMediaStoreUri(contentResolver: ContentResolver, uri: Uri): Uri? {
        var displayName: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) displayName = cursor.getString(0)
            }
        } catch (_: Exception) { }
        if (displayName == null) return null

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(displayName!!),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0)
                    )
                }
            }
        } catch (_: Exception) { }
        return null
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
                // Trouver le photo_id de l'original
                val originalPhoto = _photos.value.find { it.filename == originalFilename }

                if (originalPhoto != null) {
                    // REPLACE: même id, même created_at → même position dans la liste
                    val newFilename = "cropped_${System.currentTimeMillis()}.jpg"
                    val replacedPhoto = ApiService.replacePhoto(originalPhoto.id, file, newFilename)
                    _message.value = "Photo rognée ✓"

                    // Mettre à jour la liste locale en remplaçant l'ancienne par la nouvelle
                    _photos.value = _photos.value.map { p ->
                        if (p.id == originalPhoto.id) replacedPhoto else p
                    }
                    if (albumId != null) _albums.value = ApiService.getAlbums()

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onSuccess?.invoke(replacedPhoto, file, replacedPhoto.filename)
                    }
                } else {
                    // Fallback: photo pas trouvée localement, upload classique
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

    fun resizePhoto(filename: String, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.resizePhoto(filename, width, height)
                _message.value = "Resize sauvegardé ✓"
                loadPhotos()
            } catch (e: Exception) { _error.value = "Erreur resize: ${e.message}" }
        }
    }

    fun deletePhoto(filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deletePhoto(filename)
                _photos.value = _photos.value.filter { it.filename != filename }
            } catch (e: Exception) { _error.value = "Erreur suppression: ${e.message}" }
        }
    }

    fun clearMessage() { _message.value = null }
    fun clearError() { _error.value = null }
}
