package com.toutieserver.toutienote.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Base colors
val BgColor      = Color(0xFF0A0A0C)
val SurfaceColor = Color(0xFF16161A)
val Surface2Color= Color(0xFF1E1E24)
val BorderColor  = Color(0xFF2A2A32)
val AccentColor  = Color(0xFF5B8DEE)
val AccentLight  = Color(0xFF7BA4F5)
val AccentDark   = Color(0xFF4A7BD8)
val DangerColor  = Color(0xFFE05B5B)
val GreenColor   = Color(0xFF4ECB8D)
val TextColor    = Color(0xFFE8E8F0)
val MutedColor   = Color(0xFF6A6A7E)

// Gradients
val AccentGradient = Brush.horizontalGradient(
    colors = listOf(AccentDark, AccentColor, AccentLight)
)

val DangerGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFD84848), DangerColor, Color(0xFFE87878))
)

val SurfaceGradient = Brush.verticalGradient(
    colors = listOf(Surface2Color, SurfaceColor)
)
