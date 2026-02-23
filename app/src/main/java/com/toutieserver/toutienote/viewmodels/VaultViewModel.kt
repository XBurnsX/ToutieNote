package com.toutieserver.toutienote.viewmodels

import android.content.ContentResolver
import android.net.Uri
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
                viewModelScope.launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { _error.value = "Erreur setup PIN: ${e.message}" }
        }
    }

    fun verifyPin(pin: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.verifyPin(pin)
            viewModelScope.launch(Dispatchers.Main) {
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
                ApiService.createAlbum(name)
                loadAlbums()
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

    fun uploadPhotos(uris: List<Uri>, contentResolver: ContentResolver, cacheDir: File, onDeleteFromGallery: ((List<Uri>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            val uploadedUris = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    val filename = "vault_${System.currentTimeMillis()}.jpg"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    ApiService.uploadPhoto(tempFile, filename, null)
                    tempFile.delete()
                    uploadedUris.add(uri)
                    uploaded++
                } catch (e: Exception) {
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded photo(s) ajoutée(s) ✓"
            loadPhotos()
            if (uploadedUris.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.Main) {
                    onDeleteFromGallery?.invoke(uploadedUris)
                }
            }
        }
    }

    fun uploadPhotosToAlbum(uris: List<Uri>, albumId: String, contentResolver: ContentResolver, cacheDir: File, onDeleteFromGallery: ((List<Uri>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploading.value = true
            var uploaded = 0
            val uploadedUris = mutableListOf<Uri>()
            for (uri in uris) {
                try {
                    val filename = "vault_${System.currentTimeMillis()}.jpg"
                    val tempFile = File(cacheDir, filename)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    ApiService.uploadPhoto(tempFile, filename, albumId)
                    tempFile.delete()
                    uploadedUris.add(uri)
                    uploaded++
                } catch (e: Exception) {
                    _error.value = "Erreur upload: ${e.message}"
                }
            }
            _uploading.value = false
            if (uploaded > 0) _message.value = "$uploaded photo(s) ajoutée(s) ✓"
            loadPhotosForAlbum(albumId)
            if (uploadedUris.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.Main) {
                    onDeleteFromGallery?.invoke(uploadedUris)
                }
            }
        }
    }

    /** Upload le crop, met à jour les listes, appelle onSuccess(newPhoto, file, newFilename). Ne supprime pas le file : l’UI l’utilise pour afficher l’image tout de suite. */
    fun uploadCroppedPhoto(file: File, originalFilename: String, albumId: String? = null, onSuccess: ((Photo?, File, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFilename = "cropped_${System.currentTimeMillis()}_$originalFilename"
                ApiService.uploadPhoto(file, newFilename, albumId)
                ApiService.deletePhoto(originalFilename)
                _message.value = "Photo rognée ✓"
                var updated = if (albumId != null) ApiService.getPhotos(albumId) else ApiService.getPhotos()
                var newPhoto = updated.find { it.filename == newFilename }
                if (newPhoto == null) {
                    kotlinx.coroutines.delay(400)
                    updated = if (albumId != null) ApiService.getPhotos(albumId) else ApiService.getPhotos()
                    newPhoto = updated.find { it.filename == newFilename }
                }
                _photos.value = updated
                if (albumId != null) _albums.value = ApiService.getAlbums()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess?.invoke(newPhoto, file, newFilename)
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
