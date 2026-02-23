package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: Note?,
    vm: NotesViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val isNew = note == null
    var title   by remember { mutableStateOf(note?.title   ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var savedId by remember { mutableStateOf(note?.id) }
    var synced  by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val titleFocus = remember { FocusRequester() }
    val syncSuccess by vm.syncSuccess.collectAsState()

    LaunchedEffect(Unit) {
        if (isNew) titleFocus.requestFocus()
    }

    LaunchedEffect(syncSuccess) {
        if (syncSuccess) {
            synced = true
            vm.clearSyncSuccess()
        }
    }

    fun scheduleSync() {
        if (savedId == null) return
        synced = false
        vm.scheduleSync(savedId!!, title, content)
    }

    fun saveNew() {
        val t = title.trim()
        val c = content.trim()
        if (t.isEmpty() && c.isEmpty()) { onBack(); return }
        syncing = true
        vm.createNote(if (t.isEmpty()) "Sans titre" else t, c) { newNote ->
            savedId = newNote.id
            syncing = false
            synced = true
            onBack()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Supprimer cette note?") },
            text  = { Text("Cette action est irréversible.", color = MutedColor, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    savedId?.let { vm.deleteNote(it) }
                    showDeleteDialog = false
                    onBack()
                }) { Text("Supprimer", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Retour",
                            tint = TextColor, modifier = Modifier.size(18.dp))
                    }
                },
                title = {
                    when {
                        syncing -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(color = MutedColor, strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sauvegarde...", color = MutedColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                        isNew -> Text("Nouvelle note", color = MutedColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp, letterSpacing = 1.sp)
                        synced -> Text("Synced", color = GreenColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp, letterSpacing = 1.sp)
                        else -> Text("Modifié", color = MutedColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    if (isNew && savedId == null) {
                        TextButton(
                            onClick = ::saveNew,
                            enabled = !syncing,
                        ) {
                            Text("Enregistrer", color = AccentColor, fontWeight = FontWeight.SemiBold)
                        }
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Title
            TextField(
                value = title,
                onValueChange = { title = it; if (!isNew) scheduleSync() },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextColor),
                placeholder = { Text("Titre...", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = MutedColor) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentColor,
                ),
                singleLine = true,
            )
            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
            // Content
            TextField(
                value = content,
                onValueChange = { content = it; if (!isNew) scheduleSync() },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, color = TextColor),
                placeholder = { Text("Commence à écrire...", color = MutedColor) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentColor,
                ),
            )
        }
    }
}
