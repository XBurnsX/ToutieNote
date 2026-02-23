package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.toutieserver.toutienote.ui.components.PinDialog
import com.toutieserver.toutienote.ui.components.PinMode
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.viewmodels.NotesViewModel
import com.toutieserver.toutienote.viewmodels.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    // Afficher les erreurs du vault (ex: serveur injoignable)
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
                    Text("NOTES", fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp, color = MutedColor, letterSpacing = 3.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                actions = {
                    IconButton(onClick = { onNoteClick(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Nouvelle note", tint = AccentColor)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    color = AccentColor, modifier = Modifier.align(Alignment.Center))
                notes.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📝", fontSize = 52.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("Aucune note", color = MutedColor,
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onNoteClick(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    ) { Text("Créer une note") }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteItem(
                            note = note,
                            isSelected = selectedId == note.id,
                            onClick = { selectedId = note.id; onNoteClick(note) },
                            onLongClick = { showDeleteDialog = note },
                        )
                    }
                }
            }

            // Secret tap zone
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.BottomEnd)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSecretTap() }
                    )
            )
        }
    }
}
