package com.toutieserver.toutienote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.toutieserver.toutienote.ui.theme.*

enum class PinMode { SETUP, VERIFY }

@Composable
fun PinDialog(
    mode: PinMode,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onVerify: (String, () -> Unit, () -> Unit) -> Unit,
    onSetup: (String, () -> Unit) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var shakeIt by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val shakeOffset by animateFloatAsState(
        targetValue = if (shakeIt) 10f else 0f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 800f),
        finishedListener = { shakeIt = false },
        label = "shake"
    )

    val title = if (mode == PinMode.SETUP) "NOUVEAU PIN" else "VAULT"
    val subtitle = when {
        mode == PinMode.VERIFY -> "Entre ton PIN"
        isConfirming -> "Confirme ton PIN"
        else -> "Crée ton PIN (4 chiffres)"
    }

    fun triggerError(msg: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        error = msg
        shakeIt = true
        pin = ""
        if (isConfirming) { firstPin = ""; isConfirming = false }
    }

    fun handlePin(newPin: String) {
        if (newPin.length < 4) return
        when (mode) {
            PinMode.VERIFY -> onVerify(newPin, onSuccess) { triggerError("PIN incorrect") }
            PinMode.SETUP -> {
                if (!isConfirming) {
                    firstPin = newPin
                    pin = ""
                    isConfirming = true
                } else {
                    if (newPin == firstPin) onSetup(newPin, onSuccess)
                    else triggerError("PINs pas identiques")
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MutedColor, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.sp, color = MutedColor)
                Spacer(Modifier.height(28.dp))

                // Dots with shake
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.graphicsLayer { translationX = shakeOffset }
                ) {
                    repeat(4) { i ->
                        val filled = i < pin.length
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        shakeIt -> DangerColor
                                        filled  -> AccentColor
                                        else    -> Color.Transparent
                                    }
                                )
                                .border(
                                    2.dp,
                                    when {
                                        shakeIt -> DangerColor
                                        filled  -> AccentColor
                                        else    -> BorderColor
                                    },
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            PinKey(
                                label = key,
                                enabled = key.isNotEmpty(),
                                isDelete = key == "⌫",
                                onClick = {
                                    when (key) {
                                        "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        "" -> {}
                                        else -> {
                                            if (pin.length < 4) {
                                                pin += key
                                                handlePin(pin)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = DangerColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, isDelete: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(74.dp, 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Surface2Color else Color.Transparent)
            .border(
                width = if (enabled) 1.dp else 0.dp,
                color = if (enabled) BorderColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            color = if (isDelete) DangerColor else TextColor,
            textAlign = TextAlign.Center,
        )
    }
}
