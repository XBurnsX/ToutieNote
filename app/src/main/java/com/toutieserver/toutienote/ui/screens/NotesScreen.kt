package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.components.NoteItem
import com.toutieserver.toutienote.ui.components.NoteItemSkeleton
import com.toutieserver.toutienote.ui.components.PinDialog
import com.toutieserver.toutienote.ui.components.PinMode
import com.toutieserver.toutienote.ui.theme.BgColor
import com.toutieserver.toutienote.ui.theme.BorderColor
import com.toutieserver.toutienote.ui.theme.DangerColor
import com.toutieserver.toutienote.ui.theme.LocalAccentColor
import com.toutieserver.toutienote.ui.theme.MutedColor
import com.toutieserver.toutienote.ui.theme.Surface2Color
import com.toutieserver.toutienote.ui.theme.SurfaceColor
import com.toutieserver.toutienote.ui.theme.TextColor
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel

private enum class NoteSortMode(val label: String) {
    UPDATED("Dernière modif"),
    CREATED("Date création"),
    TITLE("Titre A-Z"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun NotesScreen(
    notesVm: NotesViewModel = viewModel(),
    vaultVm: VaultViewModel = viewModel(),
    onNoteClick: (Note?) -> Unit,
    onVaultOpen: () -> Unit,
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val notes by notesVm.notes.collectAsState()
    val tags by notesVm.tags.collectAsState()
    val loading by notesVm.loading.collectAsState()
    val pinExists by vaultVm.pinExists.collectAsState()
    val vaultError by vaultVm.error.collectAsState()
    val accentColor = LocalAccentColor.current

    var selectedId by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Note?>(null) }
    var lockTarget by remember { mutableStateOf<Note?>(null) }
    var lockPin by remember { mutableStateOf("") }
    var lockError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(NoteSortMode.UPDATED) }
    var gridMode by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredNotes = remember(notes, searchQuery, selectedTag, sortMode) {
        val query = searchQuery.trim().lowercase()
        notes
            .filter { note ->
                val matchesSearch = query.isBlank() ||
                    note.title.lowercase().contains(query) ||
                    note.content.lowercase().contains(query)
                val matchesTag = selectedTag == null || note.tags.any { it.equals(selectedTag, ignoreCase = true) }
                matchesSearch && matchesTag
            }
            .sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenByDescending { it.isFavorite }
                    .then(
                        when (sortMode) {
                            NoteSortMode.UPDATED -> compareByDescending { it.updatedAt }
                            NoteSortMode.CREATED -> compareByDescending { it.createdAt }
                            NoteSortMode.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { "zzzzzz" } }
                        }
                    )
            )
    }

    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(vaultError) {
        vaultError?.let {
            snackbarHostState.showSnackbar(it)
            vaultVm.clearError()
            showPin = false
        }
    }

    fun onSecretTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 1800L) tapCount = 0
        lastTapTime = now
        tapCount++
        if (tapCount >= 5) {
            tapCount = 0
            vaultVm.checkPin()
            showPin = true
        }
    }

    fun onRefresh() {
        isRefreshing = true
        notesVm.loadNotes()
    }

    LaunchedEffect(loading) {
        if (!loading) isRefreshing = false
    }

    if (showPin && pinExists != null) {
        PinDialog(
            mode = if (pinExists == true) PinMode.VERIFY else PinMode.SETUP,
            onSuccess = { showPin = false; onVaultOpen() },
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
                TextButton(onClick = {
                    notesVm.deleteNote(note.id)
                    showDeleteDialog = null
                }) { Text("Supprimer", color = DangerColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    lockTarget?.let { note ->
        val removingLock = note.isLocked
        AlertDialog(
            onDismissRequest = { lockTarget = null; lockPin = ""; lockError = null },
            containerColor = SurfaceColor,
            title = { Text(if (removingLock) "Déverrouiller la note" else "Verrouiller la note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (removingLock) "Entre le PIN actuel (4 chiffres)." else "Choisis un PIN unique à 4 chiffres.",
                        color = MutedColor,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = lockPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all(Char::isDigit)) {
                                lockPin = it
                                lockError = null
                            }
                        },
                        singleLine = true,
                        isError = lockError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        placeholder = { Text("_ _ _ _", color = MutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            cursorColor = accentColor,
                        )
                    )
                    lockError?.let { Text(it, color = DangerColor, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (lockPin.length != 4) {
                        lockError = "PIN invalide"
                        return@TextButton
                    }
                    if (removingLock) {
                        notesVm.removeNoteLock(
                            note.id,
                            lockPin,
                            onSuccess = {
                                lockTarget = null
                                lockPin = ""
                                lockError = null
                            },
                            onError = { lockError = "PIN incorrect" },
                        )
                    } else {
                        notesVm.lockNote(
                            note.id,
                            lockPin,
                            onSuccess = {
                                lockTarget = null
                                lockPin = ""
                                lockError = null
                            },
                            onError = { lockError = "Impossible de verrouiller la note" },
                        )
                    }
                }) {
                    Text(if (removingLock) "Déverrouiller" else "Verrouiller", color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { lockTarget = null; lockPin = ""; lockError = null }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    Scaffold(
        containerColor = BgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSecretTap() },
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            "NOTES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MutedColor,
                            letterSpacing = 3.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Tri", tint = MutedColor)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = SurfaceColor,
                        ) {
                            NoteSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        sortMode = mode
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            if (gridMode) Icons.Default.ViewAgenda else Icons.Default.ViewModule,
                            contentDescription = "Changer la vue",
                            tint = MutedColor,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Paramètres", tint = MutedColor)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNoteClick(null) },
                containerColor = accentColor,
                contentColor = BgColor,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle note")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                visible = notes.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut(),
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Rechercher dans le titre et le contenu...", color = MutedColor, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextColor,
                            unfocusedTextColor = TextColor,
                            focusedBorderColor = accentColor.copy(alpha = 0.6f),
                            unfocusedBorderColor = BorderColor,
                            cursorColor = accentColor,
                            focusedContainerColor = Surface2Color.copy(alpha = 0.5f),
                            unfocusedContainerColor = Surface2Color.copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    if (tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = { selectedTag = null },
                                label = { Text("Tous") },
                                colors = noteFilterChipColors(selectedTag == null, accentColor),
                            )
                            tags.forEach { tag ->
                                AssistChip(
                                    onClick = {
                                        selectedTag = if (selectedTag == tag.name) null else tag.name
                                    },
                                    label = { Text("#${tag.name} (${tag.noteCount})") },
                                    colors = noteFilterChipColors(selectedTag == tag.name, accentColor),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${filteredNotes.size} note(s) • ${sortMode.label}",
                            color = MutedColor,
                            fontSize = 12.sp,
                        )
                        if (selectedTag != null) {
                            Text(
                                text = "Filtre: #$selectedTag",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            val refreshState = rememberPullRefreshState(isRefreshing, { onRefresh() })
            Box(modifier = Modifier.fillMaxSize().pullRefresh(refreshState)) {
                when {
                    loading && notes.isEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(5) { NoteItemSkeleton() }
                    }

                    filteredNotes.isEmpty() -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📝", fontSize = 64.sp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Aucune note",
                            color = TextColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                selectedTag != null -> "Aucune note pour #$selectedTag"
                                searchQuery.isBlank() -> "Crée ta première note pour commencer"
                                else -> "Aucun résultat pour \"$searchQuery\""
                            },
                            color = MutedColor,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { onNoteClick(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Créer une note", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    gridMode -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 96.dp),
                        ) {
                            gridItems(filteredNotes, key = { it.id }) { note ->
                                NoteItem(
                                    note = note,
                                    isSelected = selectedId == note.id,
                                    onClick = { selectedId = note.id; onNoteClick(note) },
                                    onLongClick = { showDeleteDialog = note },
                                    onToggleFavorite = { notesVm.toggleFavorite(note.id) },
                                    onTogglePin = { notesVm.togglePin(note.id) },
                                    onToggleLock = {
                                        lockTarget = note
                                        lockPin = ""
                                        lockError = null
                                    },
                                    onDelete = { showDeleteDialog = note },
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        ) {
                            items(filteredNotes, key = { it.id }) { note ->
                                NoteItem(
                                    note = note,
                                    isSelected = selectedId == note.id,
                                    onClick = { selectedId = note.id; onNoteClick(note) },
                                    onLongClick = { showDeleteDialog = note },
                                    onToggleFavorite = { notesVm.toggleFavorite(note.id) },
                                    onTogglePin = { notesVm.togglePin(note.id) },
                                    onToggleLock = {
                                        lockTarget = note
                                        lockPin = ""
                                        lockError = null
                                    },
                                    onDelete = { showDeleteDialog = note },
                                )
                            }
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = refreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = SurfaceColor,
                    contentColor = accentColor,
                )
            }
        }
    }
}

@Composable
private fun noteFilterChipColors(selected: Boolean, accentColor: androidx.compose.ui.graphics.Color) =
    AssistChipDefaults.assistChipColors(
        containerColor = if (selected) accentColor.copy(alpha = 0.18f) else SurfaceColor,
        labelColor = if (selected) accentColor else MutedColor,
        leadingIconContentColor = if (selected) accentColor else MutedColor,
    )
