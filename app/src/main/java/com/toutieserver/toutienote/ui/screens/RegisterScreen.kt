package com.toutieserver.toutienote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toutieserver.toutienote.ui.theme.*

@Composable
fun RegisterScreen(
    vm: com.toutieserver.toutienote.viewmodels.AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val apiError by vm.error.collectAsState()
    val error = localError ?: apiError

    LaunchedEffect(apiError) {
        apiError?.let { vm.clearError() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Créer un compte",
            fontSize = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = TextColor,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Text("Min. 2 caractères, mot de passe 4+", color = MutedColor, fontSize = 12.sp)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Nom d'utilisateur") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = AccentColor,
                cursorColor = AccentColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = MutedColor
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe (min 4)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = AccentColor,
                cursorColor = AccentColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = MutedColor
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmer le mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = BorderColor,
                focusedLabelColor = AccentColor,
                cursorColor = AccentColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
                unfocusedLabelColor = MutedColor
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = DangerColor, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                localError = when {
                    username.trim().length < 2 -> "Nom d'utilisateur trop court"
                    password.length < 4 -> "Mot de passe trop court (min 4)"
                    password != confirmPassword -> "Les mots de passe ne correspondent pas"
                    else -> {
                        localError = null
                        vm.register(username.trim(), password, onRegisterSuccess)
                        null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("S'inscrire")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onLoginClick) {
            Text("Déjà un compte ? Se connecter", color = MutedColor)
        }
    }
}
