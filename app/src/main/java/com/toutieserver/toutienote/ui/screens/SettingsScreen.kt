package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toutieserver.toutienote.data.auth.AuthRepository
import com.toutieserver.toutienote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAccentChanged: (Color) -> Unit,
) {
    val context = LocalContext.current
    val currentAccent = LocalAccentColor.current
    var selectedKey by remember {
        mutableStateOf(ThemeManager.getAccentKey(context))
    }

    val username = remember { AuthRepository.getUsername() ?: "—" }
    val userId   = remember { AuthRepository.getUserId()   ?: "—" }

    var showLogoutDialog    by remember { mutableStateOf(false) }
    var showChangePwDialog  by remember { mutableStateOf(false) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var changePwError by remember { mutableStateOf<String?>(null) }

    // ── Logout dialog ──────────────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor   = SurfaceColor,
            shape            = RoundedCornerShape(16.dp),
            title  = { Text("Déconnexion", fontWeight = FontWeight.Medium) },
            text   = { Text("Confirmer la déconnexion ?", color = MutedColor, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    AuthRepository.clearSession()
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Déconnexion", color = DangerColor, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
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
                    Text(
                        "Paramètres",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 3.sp,
                        color = MutedColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = TextColor)
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

            // ── Section Compte ─────────────────────────────────────────────────
            SettingsSectionTitle("COMPTE")

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar initial
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(currentAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.first().uppercaseChar().toString(),
                            color = currentAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(username, color = TextColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Connecté", color = MutedColor, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsCard {
                SettingsRow(
                    label = "Déconnexion",
                    labelColor = DangerColor,
                    onClick = { showLogoutDialog = true }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Section Apparence ──────────────────────────────────────────────
            SettingsSectionTitle("APPARENCE")

            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Couleur accent",
                        color = TextColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Text(
                        "Appliquée aux boutons, liens et éléments actifs",
                        color = MutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                    )

                    // Grille de couleurs 4x2
                    val chunked = AccentOptions.chunked(4)
                    chunked.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { option ->
                                val isSelected = selectedKey == option.key
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(option.color)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    3.dp, Color.White, CircleShape
                                                ) else Modifier
                                            )
                                            .clickable {
                                                selectedKey = option.key
                                                ThemeManager.setAccentKey(context, option.key)
                                                onAccentChanged(option.color)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        option.label,
                                        color = if (isSelected) option.color else MutedColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                            // Remplir les cases vides si la rangée est incomplète
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Section À propos ───────────────────────────────────────────────
            SettingsSectionTitle("À PROPOS")

            SettingsCard {
                Column {
                    SettingsInfoRow("Version", "1.0.0")
                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsInfoRow("Serveur", "toutieserver.com")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Composants helper ──────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MutedColor,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
    labelColor: androidx.compose.ui.graphics.Color = TextColor,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MutedColor, fontSize = 14.sp)
        Text(value, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
