package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.util.formatNoteDate
import com.toutieserver.toutienote.viewmodels.NotesViewModel

// (label affiché, taille preview, px pour HTML)
data class TextSizeOption(val label: String, val previewSp: androidx.compose.ui.unit.TextUnit, val htmlPx: Int)

private val TEXT_SIZES = listOf(
    TextSizeOption("Très petit",  10.sp, 10),
    TextSizeOption("Petit",       12.sp, 12),
    TextSizeOption("Normal",      16.sp, 16),
    TextSizeOption("Moyen",       20.sp, 20),
    TextSizeOption("Grand",       24.sp, 24),
    TextSizeOption("Très grand",  32.sp, 32),
    TextSizeOption("Titre 3",     28.sp, 28),
    TextSizeOption("Titre 2",     36.sp, 36),
    TextSizeOption("Titre 1",     48.sp, 48),
    TextSizeOption("Énorme",      72.sp, 72),
)

private val CODE_LANGUAGES = listOf(
    "kotlin", "python", "javascript", "typescript", "java", "swift",
    "html", "css", "json", "xml", "bash", "shell", "sql",
    "c", "cpp", "csharp", "go", "rust", "php", "ruby", "yaml", "markdown"
)

private val COLOR_PALETTE = listOf(
    Color.White, Color(0xFFE8E8F0),
    Color(0xFFFF6B6B), Color(0xFFFF8E53), Color(0xFFFFD93D),
    Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFF845EC2),
    Color(0xFFFF9671), Color(0xFFFF6F91), Color(0xFF00C9A7),
    Color(0xFF4ECDC4), Color(0xFFC779D0), Color(0xFF2C73D2),
    Color.Black, Color(0xFF1A1A2E),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: Note?,
    vm: NotesViewModel = viewModel(),
    onBack: () -> Unit,
    onLinkClick: (String) -> Unit = {},
) {
    val isNew       = note == null
    val accentColor = LocalAccentColor.current

    var title      by remember { mutableStateOf(note?.title ?: "") }
    var savedId    by remember { mutableStateOf(note?.id) }
    var synced     by remember { mutableStateOf(true) }
    var syncing    by remember { mutableStateOf(false) }
    var isLocked   by remember { mutableStateOf(note?.isLocked ?: false) }
    var isUnlocked by remember { mutableStateOf(false) }
    var readMode   by remember { mutableStateOf(false) }

    // Rich text state
    val richState = rememberRichTextState()
    LaunchedEffect(Unit) {
        richState.setHtml(note?.content ?: "")
        // Style des liens — couleur accent, pas de soulignement
        richState.config.linkColor = accentColor
        richState.config.linkTextDecoration = androidx.compose.ui.text.style.TextDecoration.None
        // Style des blocs de code
        richState.config.codeSpanColor         = Color(0xFF89DCEB)
        richState.config.codeSpanBackgroundColor = Color(0xFF1E1E2E)
        richState.config.codeSpanStrokeColor   = Color(0xFF444466)
    }

    var showSizePopup    by remember { mutableStateOf(false) }
    var showCodePopup    by remember { mutableStateOf(false) }
    var showColorPopup   by remember { mutableStateOf(false) }
    var showBgColorPopup by remember { mutableStateOf(false) }
    var showLinkPopup    by remember { mutableStateOf(false) }
    var linkInput        by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu         by remember { mutableStateOf(false) }
    var showLockDialog   by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showRemoveLock   by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pinInput         by remember { mutableStateOf("") }
    var pinError         by remember { mutableStateOf(false) }
    var renameInput      by remember { mutableStateOf(title) }

    val contentScroll = rememberScrollState()
    val syncSuccess   by vm.syncSuccess.collectAsState()

    LaunchedEffect(Unit) {
        if (isLocked && !isUnlocked) showUnlockDialog = true
    }
    LaunchedEffect(syncSuccess) {
        if (syncSuccess) { synced = true; vm.clearSyncSuccess() }
    }

    fun scheduleSync() {
        if (savedId == null) return
        synced = false
        vm.scheduleSync(savedId!!, title, richState.toHtml())
    }

    fun saveNew() {
        val t = title.trim()
        val c = richState.toHtml()
        if (t.isEmpty() && richState.annotatedString.text.trim().isEmpty()) { onBack(); return }
        syncing = true
        vm.createNote(if (t.isEmpty()) "Sans titre" else t, c) { newNote ->
            savedId = newNote.id; syncing = false; synced = true; onBack()
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp),
            title = { Text("Supprimer cette note ?", fontWeight = FontWeight.Medium) },
            text  = { Text("Cette action est irréversible.", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    savedId?.let { vm.deleteNote(it) }
                    showDeleteDialog = false; onBack()
                }) { Text("Supprimer", color = DangerColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler", color = MutedColor) } }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp),
            title = { Text("Renommer", fontWeight = FontWeight.Medium) },
            text  = {
                OutlinedTextField(
                    value = renameInput, onValueChange = { renameInput = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextColor, unfocusedTextColor = TextColor, cursorColor = accentColor,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameInput.isNotBlank()) {
                        title = renameInput
                        savedId?.let { vm.renameNote(it, renameInput) }
                    }
                    showRenameDialog = false
                }) { Text("Renommer", color = accentColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Annuler", color = MutedColor) } }
        )
    }

    if (showLinkPopup) {
        AlertDialog(
            onDismissRequest = { showLinkPopup = false; linkInput = "" },
            containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp),
            title = { Text("Lien vers une note", fontWeight = FontWeight.Medium) },
            text  = {
                Column {
                    Text("Nom de la note liée", color = MutedColor, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = linkInput, onValueChange = { linkInput = it }, singleLine = true,
                        placeholder = { Text("Nom de la note...", color = MutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor, unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextColor, unfocusedTextColor = TextColor, cursorColor = accentColor,
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (linkInput.isNotBlank()) {
                        val name = linkInput.trim()
                        val sel = richState.selection
                        if (sel.min != sel.max) {
                            // Texte sélectionné → transformer en lien
                            richState.addLink(name, "toutienote://note/$name")
                        } else {
                            // Pas de sélection → insérer le nom comme lien
                            richState.insertHtml(
                                "<a href=\"toutienote://note/$name\">$name</a>",
                                sel.min
                            )
                        }
                        vm.createNote(name, "", hidden = true) {}
                    }
                    showLinkPopup = false; linkInput = ""
                }) { Text("Créer le lien", color = accentColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showLinkPopup = false; linkInput = "" }) { Text("Annuler", color = MutedColor) } }
        )
    }

    if (showSizePopup) {
        Dialog(onDismissRequest = { showSizePopup = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Taille du texte", fontWeight = FontWeight.Medium, color = TextColor,
                        modifier = Modifier.padding(bottom = 12.dp))
                    Column(modifier = Modifier.height(350.dp).verticalScroll(rememberScrollState())) {
                        TEXT_SIZES.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    // Applique la taille via HTML sur le texte sélectionné
                                    val sel = richState.selection
                                    if (sel.min != sel.max) {
                                        // Texte sélectionné → wrapper avec span
                                        richState.addSpanStyle(
                                            androidx.compose.ui.text.SpanStyle(fontSize = option.previewSp)
                                        )
                                    } else {
                                        // Pas de sélection → insérer exemple avec taille
                                        richState.insertHtml(
                                            "<span style=\"font-size:${option.htmlPx}px\">Texte</span>",
                                            sel.min
                                        )
                                    }
                                    showSizePopup = false
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                            ) {
                                Text(option.label, color = TextColor, fontSize = option.previewSp,
                                    modifier = Modifier.weight(1f))
                                Text("${option.htmlPx}px", color = MutedColor, fontSize = 11.sp)
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }
        }
    }

    if (showCodePopup) {
        Dialog(onDismissRequest = { showCodePopup = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Langage", fontWeight = FontWeight.Medium, color = TextColor,
                        modifier = Modifier.padding(bottom = 12.dp))
                    Column(modifier = Modifier.height(350.dp).verticalScroll(rememberScrollState())) {
                        CODE_LANGUAGES.forEach { lang ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    richState.toggleCodeSpan()
                                    showCodePopup = false
                                }.padding(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Text(lang, color = TextColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                            HorizontalDivider(color = BorderColor)
                        }
                    }
                }
            }
        }
    }

    if (showColorPopup) {
        ColorPickerPopup(
            title = "Couleur du texte", accentColor = accentColor,
            onColorSelected = { color ->
                richState.addSpanStyle(androidx.compose.ui.text.SpanStyle(color = color))
                showColorPopup = false
            },
            onDismiss = { showColorPopup = false }
        )
    }

    if (showBgColorPopup) {
        ColorPickerPopup(
            title = "Couleur de fond", accentColor = accentColor,
            onColorSelected = { color ->
                richState.addSpanStyle(androidx.compose.ui.text.SpanStyle(background = color))
                showBgColorPopup = false
            },
            onDismiss = { showBgColorPopup = false }
        )
    }

    if (showLockDialog) {
        PinInputDialog("Verrouiller la note", "Choisir un PIN à 4 chiffres", pinError, pinInput, accentColor,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = false } },
            onConfirm = {
                if (pinInput.length == 4) savedId?.let { id ->
                    vm.lockNote(id, pinInput,
                        onSuccess = { isLocked = true; showLockDialog = false; pinInput = "" },
                        onError   = { pinError = true })
                } else pinError = true
            },
            onDismiss = { showLockDialog = false; pinInput = ""; pinError = false }
        )
    }

    if (showUnlockDialog) {
        PinInputDialog("Note verrouillée", "Entrer le PIN pour lire", pinError, pinInput, accentColor,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = false } },
            onConfirm = {
                if (pinInput.length == 4) savedId?.let { id ->
                    vm.unlockNote(id, pinInput,
                        onSuccess = { isUnlocked = true; showUnlockDialog = false; pinInput = "" },
                        onError   = { pinError = true })
                } else pinError = true
            },
            onDismiss = { showUnlockDialog = false; pinInput = ""; pinError = false; onBack() }
        )
    }

    if (showRemoveLock) {
        PinInputDialog("Retirer le verrou", "Entrer le PIN actuel", pinError, pinInput, accentColor,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = false } },
            onConfirm = {
                if (pinInput.length == 4) savedId?.let { id ->
                    vm.removeNoteLock(id, pinInput,
                        onSuccess = { isLocked = false; isUnlocked = false; showRemoveLock = false; pinInput = "" },
                        onError   = { pinError = true })
                } else pinError = true
            },
            onDismiss = { showRemoveLock = false; pinInput = ""; pinError = false }
        )
    }

    // ── URI Handler pour les liens internes ───────────────────────────────────
    val uriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) {
                // toutienote://note/NomDeLaNote
                if (uri.startsWith("toutienote://note/")) {
                    val noteName = uri.removePrefix("toutienote://note/")
                    onLinkClick(noteName)
                }
            }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────────
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when { syncing -> "Sauvegarde..."; isNew -> "Nouvelle note"; synced -> "Enregistré"; else -> "Modifié" },
                            color = when { syncing -> MutedColor; synced -> GreenColor; else -> MutedColor },
                            fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                        if (!isNew && note != null) {
                            Text(formatNoteDate(note.updatedAt), color = MutedColor, fontSize = 11.sp, letterSpacing = 0.5.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = TextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor, titleContentColor = TextColor),
                actions = {
                    if (isNew && savedId == null) {
                        TextButton(onClick = ::saveNew, enabled = !syncing) {
                            Text("Enregistrer", color = accentColor, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        IconButton(onClick = { if (isLocked) showRemoveLock = true else showLockDialog = true }) {
                            Icon(
                                if (isLocked) Icons.Outlined.LockOpen else Icons.Default.Lock,
                                "Verrou", tint = if (isLocked) accentColor else MutedColor
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Menu", tint = TextColor)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = SurfaceColor) {
                                DropdownMenuItem(
                                    text = { Text(if (readMode) "✏️  Mode écriture" else "👁  Mode lecture", color = TextColor) },
                                    onClick = { readMode = !readMode; showMenu = false }
                                )
                                HorizontalDivider(color = BorderColor)
                                DropdownMenuItem(
                                    text = { Text("📌  Épingler", color = TextColor) },
                                    onClick = { savedId?.let { vm.togglePin(it) }; showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("✏️  Renommer", color = TextColor) },
                                    onClick = { renameInput = title; showRenameDialog = true; showMenu = false }
                                )
                                HorizontalDivider(color = BorderColor)
                                DropdownMenuItem(
                                    text = { Text("🗑  Supprimer", color = DangerColor) },
                                    onClick = { showDeleteDialog = true; showMenu = false }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val showContent = !isLocked || isUnlocked

            if (showContent) {
                // Titre
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; if (!isNew) scheduleSync() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextColor
                    ),
                    placeholder = { Text("Titre", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MutedColor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor     = TextColor,
                        unfocusedTextColor   = TextColor,
                        cursorColor          = accentColor,
                    )
                )

                // Détecter changements pour sync
                LaunchedEffect(richState.annotatedString) {
                    if (savedId != null) scheduleSync()
                }

                // Éditeur rich text
                RichTextEditor(
                    state = richState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    readOnly = readMode,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 26.sp, color = TextColor),
                    placeholder = { Text("Commence à écrire...", color = MutedColor, fontSize = 16.sp) },
                    colors = RichTextEditorDefaults.richTextEditorColors(
                        containerColor = BgColor,
                        cursorColor    = accentColor,
                    ),
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = MutedColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Note verrouillée", color = MutedColor, fontSize = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showUnlockDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Entrer le PIN") }
                    }
                }
            }

            // Status bar
            HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${richState.annotatedString.text.length + title.length} caractères", color = MutedColor, fontSize = 12.sp)
                when {
                    readMode -> Text("Mode lecture", color = accentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    syncing || !synced -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = accentColor, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (syncing) "Sauvegarde..." else "Synchronisation...", color = MutedColor, fontSize = 12.sp)
                    }
                }
            }

            // Toolbar collée au clavier
            if (!isNew && savedId != null && (!isLocked || isUnlocked) && !readMode) {
                FormatToolbar(
                    accentColor  = accentColor,
                    onUndo       = { /* undo non supporté dans rc13 */ },
                    onRedo       = { /* redo non supporté dans rc13 */ },
                    onLink       = { showLinkPopup = true },
                    onSize       = { showSizePopup = true },
                    onTextColor  = { showColorPopup = true },
                    onBgColor    = { showBgColorPopup = true },
                    onBold       = { richState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) },
                    onItalic     = { richState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) },
                    onStrike     = { richState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) },
                    onCode       = { showCodePopup = true },
                    onBulletList = { richState.toggleUnorderedList() },
                    onNumberList = { richState.toggleOrderedList() },
                    onTab        = { richState.insertHtml("&nbsp;&nbsp;&nbsp;&nbsp;", richState.selection.min) },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        }
    }
    } // CompositionLocalProvider
}

// ── Toolbar ────────────────────────────────────────────────────────────────────
@Composable
private fun FormatToolbar(
    accentColor:  Color,
    onUndo:       () -> Unit,
    onRedo:       () -> Unit,
    onLink:       () -> Unit,
    onSize:       () -> Unit,
    onTextColor:  () -> Unit,
    onBgColor:    () -> Unit,
    onBold:       () -> Unit,
    onItalic:     () -> Unit,
    onStrike:     () -> Unit,
    onCode:       () -> Unit,
    onBulletList: () -> Unit,
    onNumberList: () -> Unit,
    onTab:        () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TbIcon(Icons.AutoMirrored.Filled.Undo, "Annuler", onUndo)
        TbIcon(Icons.AutoMirrored.Filled.Redo, "Refaire", onRedo)
        TbSep()
        TbIcon(Icons.Default.Link, "Lien", onLink)
        TbSep()
        TbText("A↕", onSize)
        TbIcon(Icons.Default.FormatColorText, "Couleur texte", onTextColor)
        TbIcon(Icons.Default.FormatColorFill, "Fond texte", onBgColor)
        TbSep()
        TbText("B",   onBold,   bold = true)
        TbText("I",   onItalic, italic = true)
        TbText("S̶",   onStrike)
        TbSep()
        TbText("</>", onCode)
        TbSep()
        TbIcon(Icons.Default.FormatListBulleted, "Liste •",  onBulletList)
        TbIcon(Icons.Default.FormatListNumbered, "Liste 1.", onNumberList)
        TbSep()
        TbText("⇥", onTab)
    }
}

@Composable
private fun TbIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, desc, tint = TextColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TbText(label: String, onClick: () -> Unit, bold: Boolean = false, italic: Boolean = false) {
    Box(modifier = Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = TextColor, fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle  = if (italic) FontStyle.Italic else FontStyle.Normal)
    }
}

@Composable
private fun TbSep() {
    Box(modifier = Modifier.width(1.dp).height(22.dp).background(BorderColor))
}

@Composable
private fun ColorPickerPopup(title: String, accentColor: Color, onColorSelected: (Color) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceColor), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Medium, color = TextColor, modifier = Modifier.padding(bottom = 16.dp))
                COLOR_PALETTE.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color)
                                .border(1.dp, BorderColor, CircleShape).clickable { onColorSelected(color) })
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Annuler", color = MutedColor)
                }
            }
        }
    }
}

@Composable
private fun PinInputDialog(
    title: String, subtitle: String, error: Boolean, value: String,
    accentColor: Color, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor, shape = RoundedCornerShape(16.dp),
        title = { Text(title, fontWeight = FontWeight.Medium) },
        text  = {
            Column {
                Text(subtitle, color = MutedColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    placeholder = { Text("_ _ _ _", color = MutedColor) },
                    isError = error,
                    supportingText = if (error) ({ Text("PIN incorrect", color = DangerColor) }) else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextColor, unfocusedTextColor = TextColor, cursorColor = accentColor,
                    )
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirmer", color = accentColor, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = MutedColor) } }
    )
}
