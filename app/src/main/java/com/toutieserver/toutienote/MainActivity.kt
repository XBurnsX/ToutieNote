package com.toutieserver.toutienote

import android.os.Bundle
import androidx.activity.ComponentActivity
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Album
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.data.models.Photo
import com.toutieserver.toutienote.ui.screens.*
import com.toutieserver.toutienote.ui.theme.ToutieNoteTheme
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel

sealed class Screen {
    object Notes : Screen()
    data class NoteEdit(val note: Note?) : Screen()
    object Albums : Screen()
    data class AlbumPhotos(val album: Album) : Screen()
    data class PhotoFullscreen(val photo: Photo, val album: Album, val localImageFile: File? = null) : Screen()
    data class PhotoEdit(val photo: Photo, val album: Album) : Screen()
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
            AlbumPhotosScreen(
                album = s.album,
                vm = vaultVm,
                onPhotoClick = { photo -> screen = Screen.PhotoFullscreen(photo, s.album) },
                onBack = { screen = Screen.Albums },
            )
        }
        is Screen.PhotoFullscreen -> {
            BackHandler { screen = Screen.AlbumPhotos(s.album) }
            PhotoFullscreenScreen(
                photo = s.photo,
                localImageFile = s.localImageFile,
                onBack = { screen = Screen.AlbumPhotos(s.album) },
                onEdit = { screen = Screen.PhotoEdit(s.photo, s.album) },
            )
        }
        is Screen.PhotoEdit -> {
            BackHandler { screen = Screen.PhotoFullscreen(s.photo, s.album) }
            PhotoEditScreen(
                photo = s.photo,
                vm = vaultVm,
                onBack = { screen = Screen.PhotoFullscreen(s.photo, s.album) },
                onCropSaved = { newPhoto, localFile, newFilename ->
                    val photoToShow = newPhoto ?: Photo(
                        id = newFilename,
                        filename = newFilename,
                        url = "",
                        size = 0L,
                        createdAt = "",
                        albumId = s.album.id
                    )
                    screen = Screen.PhotoFullscreen(photoToShow, s.album, localImageFile = localFile)
                },
            )
        }
    }
}
