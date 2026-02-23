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
fun LoginScreen(
    vm: com.toutieserver.toutienote.viewmodels.AuthViewModel,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val error by vm.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "ToutieNote",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = TextColor,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Text("Connexion", color = MutedColor, fontSize = 14.sp)
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
            label = { Text("Mot de passe") },
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
                if (username.isNotBlank() && password.isNotBlank()) {
                    vm.login(username.trim(), password, onLoginSuccess)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Se connecter")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onRegisterClick) {
            Text("Pas de compte ? S'inscrire", color = MutedColor)
        }
    }
}
