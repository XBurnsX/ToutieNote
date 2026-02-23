package com.toutieserver.toutienote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.auth.AuthRepository
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.components.PinDialog
import com.toutieserver.toutienote.ui.components.PinMode
import com.toutieserver.toutienote.ui.screens.*
import com.toutieserver.toutienote.ui.theme.ToutieNoteTheme
import com.toutieserver.toutienote.viewmodels.AuthViewModel
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel
import java.io.File

sealed class Screen {
    data class NoteEdit(val note: com.toutieserver.toutienote.data.models.Note?) : Screen()
    object Notes : Screen()
    object Albums : Screen()
    data class AlbumPhotos(val album: Album) : Screen()
    data class PhotoFullscreen(
        val initialIndex: Int,
        val album: Album,
    ) : Screen()
    data class PhotoEdit(
        val photo: Photo,
        val pageIndex: Int,
        val album: Album,
    ) : Screen()
    data class Slideshow(val photos: List<Photo>, val album: Album) : Screen()
    data class Duplicates(val albumId: String?, val album: Album?) : Screen()
    data class GalleryPicker(val album: Album) : Screen()
    object Login : Screen()
    object Register : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AuthRepository.init(applicationContext)
        setContent {
            ToutieNoteTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authVm: AuthViewModel = viewModel()
    val notesVm: NotesViewModel = viewModel()
    val vaultVm: VaultViewModel = viewModel()

    var screen by remember { mutableStateOf<Screen>(
        if (AuthRepository.isLoggedIn()) Screen.Notes else Screen.Login
    ) }
    val unlockedAlbums = remember { mutableStateListOf<String>() }
    var pendingLockedAlbum by remember { mutableStateOf<Album?>(null) }

    // PIN dialog for locked albums
    pendingLockedAlbum?.let { album ->
        PinDialog(
            mode = PinMode.VERIFY,
            onSuccess = {
                unlockedAlbums.add(album.id)
                pendingLockedAlbum = null
                screen = Screen.AlbumPhotos(album)
            },
            onDismiss = { pendingLockedAlbum = null },
            onVerify = { pin, ok, fail ->
                vaultVm.verifyAlbumLock(album.id, pin, ok, fail)
            },
            onSetup = { _, _ -> },
        )
    }

    when (val s = screen) {
        is Screen.Login -> LoginScreen(
            vm = authVm,
            onLoginSuccess = { screen = Screen.Notes },
            onRegisterClick = { screen = Screen.Register },
        )
        is Screen.Register -> RegisterScreen(
            vm = authVm,
            onRegisterSuccess = { screen = Screen.Notes },
            onLoginClick = { screen = Screen.Login },
        )
        is Screen.Notes -> NotesScreen(
            notesVm = notesVm,
            vaultVm = vaultVm,
            onNoteClick = { note -> screen = Screen.NoteEdit(note) },
            onVaultOpen = { screen = Screen.Albums },
            onLogout = { screen = Screen.Login },
        )
        is Screen.NoteEdit -> {
            BackHandler { screen = Screen.Notes }
            NoteEditScreen(
                note = s.note,
                vm = notesVm,
                onBack = { screen = Screen.Notes },
            )
        }
        is Screen.Albums -> {
            BackHandler { screen = Screen.Notes }
            AlbumsListScreen(
                vm = vaultVm,
                onLogout = { screen = Screen.Login },
                onAlbumClick = { album ->
                    if (album.isLocked && album.id !in unlockedAlbums) {
                        pendingLockedAlbum = album
                    } else {
                        screen = Screen.AlbumPhotos(album)
                    }
                },
                onDuplicates = { screen = Screen.Duplicates(null, null) },
                onBack = { screen = Screen.Notes },
            )
        }
        is Screen.AlbumPhotos -> {
            BackHandler { screen = Screen.Albums }

            val photos by vaultVm.photos.collectAsState()

            AlbumPhotosScreen(
                album = s.album,
                vm = vaultVm,
                onPhotoClick = { photo ->
                    val index = photos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
                    screen = Screen.PhotoFullscreen(index, s.album)
                },
                onSlideshow = { slidePhotos -> screen = Screen.Slideshow(slidePhotos, s.album) },
                onDuplicates = { screen = Screen.Duplicates(s.album.id, s.album) },
                onImportFromGallery = { screen = Screen.GalleryPicker(s.album) },
                onBack = { screen = Screen.Albums },
            )
        }
        is Screen.PhotoFullscreen -> {
            BackHandler { screen = Screen.AlbumPhotos(s.album) }
            PhotoFullscreenScreen(
                initialIndex = s.initialIndex,
                vm = vaultVm,
                albumId = s.album.id,
                onBack = { screen = Screen.AlbumPhotos(s.album) },
                onEdit = { photo ->
                    // Passer le pageIndex actuel pour revenir au même endroit après crop
                    val photos = vaultVm.photos.value
                    val idx = photos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
                    screen = Screen.PhotoEdit(photo, idx, s.album)
                },
            )
        }
        is Screen.PhotoEdit -> {
            BackHandler { screen = Screen.PhotoFullscreen(s.pageIndex, s.album) }
            PhotoEditScreen(
                photo = s.photo,
                localImageFile = null,
                vm = vaultVm,
                onBack = {
                    // Annulé → retour au pager à la même position
                    screen = Screen.PhotoFullscreen(s.pageIndex, s.album)
                },
                onCropSaved = { _, localFile, _ ->
                    localFile.delete()
                    // Crop sauvegardé → retour au pager à la même position
                    // vm.photos est déjà mis à jour par uploadCroppedPhoto (replacePhoto)
                    screen = Screen.PhotoFullscreen(s.pageIndex, s.album)
                },
            )
        }
        is Screen.Slideshow -> {
            BackHandler { screen = Screen.AlbumPhotos(s.album) }
            SlideshowScreen(
                photos = s.photos,
                onBack = { screen = Screen.AlbumPhotos(s.album) },
            )
        }
        is Screen.Duplicates -> {
            val backScreen = if (s.album != null) Screen.AlbumPhotos(s.album) else Screen.Albums
            BackHandler { screen = backScreen }
            DuplicatesScreen(
                vm = vaultVm,
                albumId = s.albumId,
                onBack = { screen = backScreen },
            )
        }
        is Screen.GalleryPicker -> {
            BackHandler { screen = Screen.AlbumPhotos(s.album) }
            GalleryPickerScreen(
                onConfirm = { mediaStoreUris ->
                    screen = Screen.AlbumPhotos(s.album)
                    vaultVm.importFromGallery(mediaStoreUris, s.album.id, context.contentResolver, context.cacheDir)
                },
                onCancel = { screen = Screen.AlbumPhotos(s.album) },
            )
        }
    }
}
