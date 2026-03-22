package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.components.PinDialog
import com.toutieserver.toutienote.ui.components.PinMode
import com.toutieserver.toutienote.ui.theme.BgColor
import com.toutieserver.toutienote.ui.theme.DangerColor
import com.toutieserver.toutienote.ui.theme.LocalAccentColor
import com.toutieserver.toutienote.ui.theme.MutedColor
import com.toutieserver.toutienote.ui.theme.Surface2Color
import com.toutieserver.toutienote.ui.theme.SurfaceColor
import com.toutieserver.toutienote.ui.theme.TextColor
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class NoteColorOption(
    val label: String,
    val value: String?,
    val color: Color,
)

private val NoteColorOptions = listOf(
    NoteColorOption("Défaut", null, Color(0xFF1F1F1F)),
    NoteColorOption("Forêt", "forest", Color(0xFF1A3A1F)),
    NoteColorOption("Ambre", "amber", Color(0xFF3A2F1A)),
    NoteColorOption("Océan", "ocean", Color(0xFF1A2F3A)),
    NoteColorOption("Violet", "violet", Color(0xFF2A1A3A)),
    NoteColorOption("Bordeaux", "bordeaux", Color(0xFF3A1A2F)),
    NoteColorOption("Ardoise", "slate", Color(0xFF1E2A2A)),
    NoteColorOption("Rouille", "rust", Color(0xFF3A1F1A)),
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun NotesScreen(
    notesVm: NotesViewModel = viewModel(),
    vaultVm: VaultViewModel = viewModel(),
    onNoteClick: (Note?) -> Unit,
    onVaultOpen: () -> Unit,
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val accentColor = LocalAccentColor.current
    val notes by notesVm.notes.collectAsState()
    val loading by notesVm.loading.collectAsState()
    val pinExists by vaultVm.pinExists.collectAsState()
    val vaultError by vaultVm.error.collectAsState()

    var showPin by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredNotes = remember(notes, searchQuery) {
        val query = searchQuery.trim().lowercase()
        notes
            .filterNot { it.isHidden }
            .filter { note ->
                query.isBlank() ||
                    note.title.lowercase().contains(query) ||
                    note.content.lowercase().contains(query)
            }
    }
    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val otherNotes = remember(filteredNotes) { filteredNotes.filterNot { it.isPinned } }

    LaunchedEffect(vaultError) {
        vaultError?.let {
            snackbarHostState.showSnackbar(it)
            vaultVm.clearError()
            showPin = false
        }
    }

    LaunchedEffect(loading) {
        if (!loading) isRefreshing = false
    }

    fun onSearchSecretTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 1800L) tapCount = 0
        lastTapTime = now
        tapCount += 1
        if (tapCount >= 5) {
            tapCount = 0
            vaultVm.checkPin()
            showPin = true
        }
    }

    if (showPin && pinExists != null) {
        PinDialog(
            mode = if (pinExists == true) PinMode.VERIFY else PinMode.SETUP,
            onSuccess = {
                showPin = false
                onVaultOpen()
            },
            onDismiss = { showPin = false },
            onVerify = { pin, ok, fail -> vaultVm.verifyPin(pin, ok, fail) },
            onSetup = { pin, ok -> vaultVm.setupPin(pin, ok) },
        )
    }

    showDeleteDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceColor,
            title = { Text("Supprimer cette note ?") },
            text = { Text("Cette action est irréversible.", color = MutedColor, fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        notesVm.deleteNote(note.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Supprimer", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuler", color = MutedColor)
                }
            },
        )
    }

    val refreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            notesVm.loadNotes()
        }
    )

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNoteClick(null) },
                modifier = Modifier.navigationBarsPadding(),
                containerColor = accentColor,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nouvelle note",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(padding)
                .pullRefresh(refreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NotesToolbar(
                    query = searchQuery,
                    noteCount = filteredNotes.size,
                    gridMode = gridMode,
                    onQueryChange = { searchQuery = it },
                    onClearQuery = { searchQuery = "" },
                    onToggleLayout = { gridMode = !gridMode },
                    onSearchIconTap = { onSearchSecretTap() },
                    onOpenSettings = onSettings,
                )

                when {
                    loading && notes.isEmpty() -> NotesLoadingState(gridMode = gridMode)
                    filteredNotes.isEmpty() -> EmptyNotesState(
                        hasQuery = searchQuery.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    else -> NotesList(
                        pinnedNotes = pinnedNotes,
                        otherNotes = otherNotes,
                        gridMode = gridMode,
                        onNoteClick = { onNoteClick(it) },
                        onTogglePin = { notesVm.togglePin(it.id) },
                        onDelete = { showDeleteDialog = it },
                        onColorChange = { note, colorTag -> notesVm.updateNoteColor(note.id, colorTag) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = refreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                backgroundColor = SurfaceColor,
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun NotesToolbar(
    query: String,
    noteCount: Int,
    gridMode: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleLayout: () -> Unit,
    onSearchIconTap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Paramètres",
                    tint = TextColor,
                    modifier = Modifier.size(30.dp),
                )
            }

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = {
                    Text(
                        "Rechercher dans les notes",
                        color = MutedColor,
                        fontSize = 14.sp,
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onSearchIconTap) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Recherche",
                            tint = MutedColor,
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Effacer la recherche",
                                tint = MutedColor,
                            )
                        }
                    }
                } else {
                    null
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextColor,
                    unfocusedTextColor = TextColor,
                    focusedContainerColor = Surface2Color.copy(alpha = 0.65f),
                    unfocusedContainerColor = Surface2Color.copy(alpha = 0.65f),
                    disabledContainerColor = Surface2Color.copy(alpha = 0.65f),
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedLeadingIconColor = MutedColor,
                    unfocusedLeadingIconColor = MutedColor,
                    focusedTrailingIconColor = MutedColor,
                    unfocusedTrailingIconColor = MutedColor,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$noteCount note${if (noteCount > 1) "s" else ""}",
                color = MutedColor,
                fontSize = 12.sp,
            )

            IconButton(
                onClick = onToggleLayout,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (gridMode) Icons.Default.ViewAgenda else Icons.Default.ViewModule,
                    contentDescription = "Changer la vue",
                    tint = TextColor,
                )
            }
        }
    }
}

@Composable
private fun NotesList(
    pinnedNotes: List<Note>,
    otherNotes: List<Note>,
    gridMode: Boolean,
    onNoteClick: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onColorChange: (Note, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pinnedNotes.isNotEmpty()) {
            item { SectionHeader(label = "Épinglées", showPin = true) }
            noteRows(
                notes = pinnedNotes,
                gridMode = gridMode,
                onNoteClick = onNoteClick,
                onTogglePin = onTogglePin,
                onDelete = onDelete,
                onColorChange = onColorChange,
            )
        }

        if (otherNotes.isNotEmpty()) {
            if (pinnedNotes.isNotEmpty()) {
                item { SectionHeader(label = "Autres") }
            }
            noteRows(
                notes = otherNotes,
                gridMode = gridMode,
                onNoteClick = onNoteClick,
                onTogglePin = onTogglePin,
                onDelete = onDelete,
                onColorChange = onColorChange,
            )
        }
    }
}

private fun LazyListScope.noteRows(
    notes: List<Note>,
    gridMode: Boolean,
    onNoteClick: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onColorChange: (Note, String?) -> Unit,
) {
    if (gridMode) {
        val rows = notes.chunked(2)
        items(rows.size) { index ->
            val row = rows[index]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { note ->
                    NoteCard(
                        note = note,
                        gridMode = true,
                        onClick = { onNoteClick(note) },
                        onTogglePin = { onTogglePin(note) },
                        onDelete = { onDelete(note) },
                        onColorChange = { onColorChange(note, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                gridMode = false,
                onClick = { onNoteClick(note) },
                onTogglePin = { onTogglePin(note) },
                onDelete = { onDelete(note) },
                onColorChange = { onColorChange(note, it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    showPin: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPin) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = MutedColor,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = label.uppercase(Locale.FRENCH),
            color = MutedColor,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    gridMode: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onColorChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    val accentColor = LocalAccentColor.current
    val previewText = remember(note.content, note.isLocked) {
        if (note.isLocked) {
            "Contenu verrouillé"
        } else {
            note.content
                .replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .trim()
        }
    }
    val selectedColorValue = note.colorTag?.trim()?.lowercase()

    Surface(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onDelete),
        color = noteBackgroundColor(note.colorTag),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        modifier = Modifier.weight(1f),
                        color = accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (gridMode) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = Color(0xFFD0D0D0),
                        modifier = Modifier
                            .padding(start = 8.dp, top = 2.dp)
                            .size(14.dp),
                    )
                } else if (note.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFD0D0D0),
                        modifier = Modifier
                            .padding(start = 8.dp, top = 2.dp)
                            .size(14.dp),
                    )
                }
            }

            if (previewText.isNotBlank()) {
                Text(
                    text = previewText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = accentColor,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = if (gridMode) 5 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatNotesTimestamp(note.updatedAt),
                    color = Color(0xFF8A8A8A),
                    fontSize = 11.sp,
                )

                Box {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                showMenu = !showMenu
                                if (!showMenu) showColors = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "...",
                            color = Color(0xFFD0D0D0),
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                            showColors = false
                        },
                        containerColor = Color(0xFF2C2C2C),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (note.isPinned) "Désépingler" else "Épingler", color = TextColor) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = TextColor,
                                )
                            },
                            onClick = {
                                onTogglePin()
                                showMenu = false
                                showColors = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Couleur", color = TextColor) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = TextColor,
                                )
                            },
                            onClick = { showColors = !showColors },
                        )

                        if (showColors) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NoteColorOptions.chunked(4).forEach { colorRow ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        colorRow.forEach { option ->
                                            val isSelected = (option.value ?: "") == (selectedColorValue ?: "")
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(option.color)
                                                    .border(
                                                        width = 2.dp,
                                                        color = if (isSelected) Color.White else Color.Transparent,
                                                        shape = CircleShape,
                                                    )
                                                    .clickable {
                                                        onColorChange(option.value)
                                                        showMenu = false
                                                        showColors = false
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        DropdownMenuItem(
                            text = { Text("Supprimer", color = DangerColor) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = DangerColor,
                                )
                            },
                            onClick = {
                                onDelete()
                                showMenu = false
                                showColors = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesLoadingState(
    gridMode: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader(label = "Chargement") }
        if (gridMode) {
            items(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NoteCardPlaceholder(modifier = Modifier.weight(1f))
                    NoteCardPlaceholder(modifier = Modifier.weight(1f))
                }
            }
        } else {
            items(6) {
                NoteCardPlaceholder(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NoteCardPlaceholder(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1F1F1F),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(12.dp)
        ) {
            PlaceholderLine(width = 0.62f, height = 14.dp)
            Spacer(modifier = Modifier.height(10.dp))
            PlaceholderLine(width = 1f, height = 12.dp)
            Spacer(modifier = Modifier.height(6.dp))
            PlaceholderLine(width = 0.78f, height = 12.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceholderLine(width = 0.22f, height = 10.dp)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }
    }
}

@Composable
private fun PlaceholderLine(
    width: Float,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(width)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun EmptyNotesState(
    hasQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MutedColor.copy(alpha = 0.45f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasQuery) "Aucune note trouvée" else "Aucune note",
            color = TextColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun noteBackgroundColor(colorTag: String?): Color {
    return when (colorTag?.trim()?.lowercase()) {
        null, "", "default" -> Color(0xFF1F1F1F)
        "forest", "foret" -> Color(0xFF1A3A1F)
        "amber", "ambre" -> Color(0xFF3A2F1A)
        "ocean" -> Color(0xFF1A2F3A)
        "violet" -> Color(0xFF2A1A3A)
        "bordeaux" -> Color(0xFF3A1A2F)
        "slate", "ardoise" -> Color(0xFF1E2A2A)
        "rust", "rouille" -> Color(0xFF3A1F1A)
        else -> Color(0xFF1F1F1F)
    }
}

private fun formatNotesTimestamp(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return runCatching {
        val dateTime = LocalDateTime.parse(isoDate.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val today = LocalDate.now()
        when (dateTime.toLocalDate()) {
            today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "Hier"
            else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        }
    }.getOrElse {
        isoDate.take(10)
    }
}
