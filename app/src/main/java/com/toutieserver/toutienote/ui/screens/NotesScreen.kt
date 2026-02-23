package com.toutieserver.toutienote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.components.NoteItem
import com.toutieserver.toutienote.ui.components.NoteItemSkeleton
import com.toutieserver.toutienote.ui.components.PinDialog
import com.toutieserver.toutienote.ui.components.PinMode
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun NotesScreen(
    notesVm: NotesViewModel = viewModel(),
    vaultVm: VaultViewModel = viewModel(),
    onNoteClick: (Note?) -> Unit,
    onVaultOpen: () -> Unit,
) {
    val notes by notesVm.notes.collectAsState()
    val loading by notesVm.loading.collectAsState()
    val pinExists by vaultVm.pinExists.collectAsState()
    val vaultError by vaultVm.error.collectAsState()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Note?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else {
            val q = searchQuery.lowercase().trim()
            notes.filter { note ->
                note.title.lowercase().contains(q) || note.content.lowercase().contains(q)
            }
        }
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
            onSetup  = { pin, ok -> vaultVm.setupPin(pin, ok) },
        )
    }

    showDeleteDialog?.let { note ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = SurfaceColor,
            title = { Text("Supprimer cette note?") },
            text  = { Text("Cette action est irréversible.", color = MutedColor, fontSize = 13.sp) },
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
                                onClick = { onSecretTap() }
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text("NOTES", fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp, color = MutedColor, letterSpacing = 3.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNoteClick(null) },
                containerColor = AccentColor,
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
                exit = fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Rechercher...", color = MutedColor, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextColor,
                        unfocusedTextColor = TextColor,
                        focusedBorderColor = AccentColor.copy(alpha = 0.6f),
                        unfocusedBorderColor = BorderColor,
                        cursorColor = AccentColor,
                        focusedContainerColor = Surface2Color.copy(alpha = 0.5f),
                        unfocusedContainerColor = Surface2Color.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading && notes.isEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
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
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (searchQuery.isBlank())
                                "Créez votre première note pour commencer"
                            else
                                "Aucun résultat pour \"$searchQuery\"",
                            color = MutedColor,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { onNoteClick(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        ) {
                            Text("Créer une note", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        val refreshState = rememberPullRefreshState(isRefreshing, { onRefresh() })
                        Box(modifier = Modifier.fillMaxSize().pullRefresh(refreshState)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                            ) {
                                itemsIndexed(filteredNotes, key = { _, note -> note.id }) { index, note ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = BorderColor,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 1.dp
                                        )
                                    }
                                    NoteItem(
                                        note = note,
                                        isSelected = selectedId == note.id,
                                        onClick = { selectedId = note.id; onNoteClick(note) },
                                        onLongClick = { showDeleteDialog = note },
                                    )
                                }
                            }
                            PullRefreshIndicator(
                                refreshing = isRefreshing,
                                state = refreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }

            }
        }
    }
}
