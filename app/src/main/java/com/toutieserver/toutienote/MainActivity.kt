package com.toutieserver.toutienote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.screens.*
import com.toutieserver.toutienote.ui.theme.ToutieNoteTheme
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToutieNoteTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val notesVm: NotesViewModel = viewModel()
    val vaultVm: VaultViewModel = viewModel()

    var screen by remember { mutableStateOf<Screen>(Screen.Notes) }

    when (val s = screen) {
        is Screen.Notes -> NotesScreen(
            notesVm = notesVm,
            vaultVm = vaultVm,
            onNoteClick = { note -> screen = Screen.NoteEdit(note) },
            onVaultOpen = { screen = Screen.Albums },
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
                onAlbumClick = { album -> screen = Screen.AlbumPhotos(album) },
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
                onBack = { screen = Screen.Albums },
            )
        }
        is Screen.PhotoFullscreen -> {
            BackHandler { screen = Screen.AlbumPhotos(s.album) }
            PhotoFullscreenScreen(
                initialIndex = s.initialIndex,
                vm = vaultVm,
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
    }
}
