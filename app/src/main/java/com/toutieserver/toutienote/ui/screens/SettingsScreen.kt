package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.auth.AuthRepository
import com.toutieserver.toutienote.ui.theme.AccentOptions
import com.toutieserver.toutienote.ui.theme.BgColor
import com.toutieserver.toutienote.ui.theme.BorderColor
import com.toutieserver.toutienote.ui.theme.DangerColor
import com.toutieserver.toutienote.ui.theme.LocalAccentColor
import com.toutieserver.toutienote.ui.theme.MutedColor
import com.toutieserver.toutienote.ui.theme.SurfaceColor
import com.toutieserver.toutienote.ui.theme.TextColor
import com.toutieserver.toutienote.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class SettingsAccount(
    val userId: String,
    val username: String,
    val createdAt: String,
)

private fun formatAccountDate(isoDate: String): String {
    if (isoDate.isBlank()) return "Inconnue"
    return try {
        val date = LocalDateTime.parse(isoDate.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
    } catch (_: Exception) {
        isoDate.take(10)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAccentChanged: (Color) -> Unit,
) {
    val context = LocalContext.current
    val currentAccent = LocalAccentColor.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedKey by remember { mutableStateOf(ThemeManager.getAccentKey(context)) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangePwDialog by remember { mutableStateOf(false) }

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var changePwError by remember { mutableStateOf<String?>(null) }
    var account by remember {
        mutableStateOf(
            SettingsAccount(
                userId = AuthRepository.getUserId().orEmpty(),
                username = AuthRepository.getUsername().orEmpty().ifBlank { "-" },
                createdAt = AuthRepository.getCreatedAt().orEmpty(),
            )
        )
    }

    LaunchedEffect(Unit) {
        runCatching { ApiService.getAccountInfo() }
            .onSuccess { info ->
                account = SettingsAccount(
                    userId = info.userId,
                    username = info.username,
                    createdAt = info.createdAt,
                )
                AuthRepository.saveSession(
                    token = AuthRepository.getToken().orEmpty(),
                    username = info.username,
                    userId = info.userId,
                    createdAt = info.createdAt,
                )
            }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Déconnexion", fontWeight = FontWeight.Medium) },
            text = { Text("Confirmer la déconnexion ?", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    AuthRepository.clearSession()
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Déconnexion", color = DangerColor, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler", color = MutedColor)
                }
            }
        )
    }

    if (showChangePwDialog) {
        AlertDialog(
            onDismissRequest = { showChangePwDialog = false },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Changer le mot de passe", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = {
                            oldPassword = it
                            changePwError = null
                        },
                        label = { Text("Mot de passe actuel") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = settingsFieldColors(currentAccent),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            changePwError = null
                        },
                        label = { Text("Nouveau mot de passe") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = settingsFieldColors(currentAccent),
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            changePwError = null
                        },
                        label = { Text("Confirmer") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = changePwError != null,
                        supportingText = changePwError?.let { { Text(it, color = DangerColor) } },
                        colors = settingsFieldColors(currentAccent),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank() -> {
                            changePwError = "Complète les 3 champs"
                        }
                        newPassword.length < 4 -> {
                            changePwError = "Minimum 4 caractères"
                        }
                        newPassword != confirmPassword -> {
                            changePwError = "Les mots de passe ne correspondent pas"
                        }
                        else -> {
                            scope.launch(Dispatchers.IO) {
                                runCatching { ApiService.changePassword(oldPassword, newPassword) }
                                    .onSuccess {
                                        oldPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                        changePwError = null
                                        showChangePwDialog = false
                                        snackbarHostState.showSnackbar("Mot de passe mis à jour")
                                    }
                                    .onFailure {
                                        changePwError = it.message ?: "Impossible de changer le mot de passe"
                                    }
                            }
                        }
                    }
                }) {
                    Text("Valider", color = currentAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePwDialog = false }) {
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
                    Text(
                        "PARAMÈTRES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 3.sp,
                        color = MutedColor,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = TextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionTitle("COMPTE")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(currentAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = account.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = currentAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                    Spacer(Modifier.padding(start = 14.dp))
                    Column {
                        Text(account.username.ifBlank { "-" }, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Compte connecté", color = MutedColor, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsInfoRow("Identifiant", account.userId.ifBlank { "-" })
                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsInfoRow("Créé le", formatAccountDate(account.createdAt))
            }

            Spacer(Modifier.height(24.dp))

            SettingsSectionTitle("APPARENCE")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Couleur accent", color = TextColor, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(
                        "Appliquée aux boutons, liens, tags, wikilinks et éléments actifs.",
                        color = MutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
                    )
                    AccentOptions.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { option ->
                                val isSelected = selectedKey == option.key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(option.color)
                                            .then(
                                                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
                                            )
                                            .clickable {
                                                selectedKey = option.key
                                                ThemeManager.setAccentKey(context, option.key)
                                                onAccentChanged(option.color)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        option.label,
                                        color = if (isSelected) option.color else MutedColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SettingsSectionTitle("SÉCURITÉ")
            SettingsCard {
                SettingsRow(label = "Changer le mot de passe", onClick = { showChangePwDialog = true })
            }

            Spacer(Modifier.height(24.dp))

            SettingsSectionTitle("SESSION")
            SettingsCard {
                SettingsRow(label = "Déconnexion", labelColor = DangerColor, onClick = { showLogoutDialog = true })
            }

            Spacer(Modifier.height(24.dp))

            SettingsSectionTitle("À PROPOS")
            SettingsCard {
                Column {
                    SettingsInfoRow("Version", "1.0.0")
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsInfoRow("Serveur", "toutieserver.com")
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsInfoRow("Fond", "#0f0f0f")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MutedColor,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    labelColor: Color = TextColor,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = labelColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MutedColor, fontSize = 14.sp)
        Text(value, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun settingsFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = BorderColor,
    focusedTextColor = TextColor,
    unfocusedTextColor = TextColor,
    cursorColor = accent,
)
