package com.toutieserver.toutienote.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary        = AccentColor,
    background     = BgColor,
    surface        = SurfaceColor,
    onBackground   = TextColor,
    onSurface      = TextColor,
    error          = DangerColor,
)

val AppTypography = Typography(
    bodyLarge  = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, color = TextColor, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = TextColor),
    bodySmall  = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MutedColor, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextColor),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MutedColor, letterSpacing = 2.sp),
)

@Composable
fun ToutieNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
