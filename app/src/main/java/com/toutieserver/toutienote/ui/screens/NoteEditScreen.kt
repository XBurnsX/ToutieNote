package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.util.formatNoteDate
import com.toutieserver.toutienote.viewmodels.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: Note?,
    vm: NotesViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val isNew = note == null
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var savedId by remember { mutableStateOf(note?.id) }
    var synced by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val titleFocus = remember { FocusRequester() }
    val contentScroll = rememberScrollState()
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
            shape = RoundedCornerShape(16.dp),
            title = { Text("Supprimer cette note ?", fontWeight = FontWeight.Medium) },
            text = { Text("Cette action est irréversible.", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    savedId?.let { vm.deleteNote(it) }
                    showDeleteDialog = false
                    onBack()
                }) { Text("Supprimer", color = DangerColor, fontWeight = FontWeight.SemiBold) }
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
                title = {
                    Column {
                        Text(
                            text = when {
                                syncing -> "Sauvegarde..."
                                isNew -> "Nouvelle note"
                                synced -> "Enregistré"
                                else -> "Modifié"
                            },
                            color = when {
                                syncing -> MutedColor
                                synced -> GreenColor
                                else -> MutedColor
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (!isNew && note != null) {
                            Text(
                                text = formatNoteDate(note.updatedAt),
                                color = MutedColor,
                                fontSize = 11.sp, letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = TextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceColor,
                    titleContentColor = TextColor
                ),
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
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MutedColor)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(contentScroll)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it; if (!isNew) scheduleSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextColor,
                        lineHeight = 32.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (title.isEmpty()) {
                                Text(
                                    "Titre",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MutedColor
                                )
                            }
                            innerTextField()
                        }
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(AccentColor)
                )

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = content,
                    onValueChange = { content = it; if (!isNew) scheduleSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = TextColor
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (content.isEmpty()) {
                                Text(
                                    "Commence à écrire...",
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp,
                                    color = MutedColor
                                )
                            }
                            innerTextField()
                        }
                    },
                    cursorBrush = SolidColor(AccentColor)
                )
            }

            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${content.length + title.length} caractères",
                    color = MutedColor,
                    fontSize = 12.sp
                )
                if (syncing || !synced) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = AccentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (syncing) "Sauvegarde..." else "Synchronisation...",
                            color = MutedColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
