package com.toutieserver.toutienote.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Couleurs accent disponibles ────────────────────────────────────────────────
data class AccentOption(val key: String, val label: String, val color: Color)

val AccentOptions = listOf(
    AccentOption("blue",   "Bleu",   Color(0xFF5B8DEE)),
    AccentOption("violet", "Violet", Color(0xFF7C6AF5)),
    AccentOption("pink",   "Rose",   Color(0xFFE870AD)),
    AccentOption("red",    "Rouge",  Color(0xFFE05B5B)),
    AccentOption("orange", "Orange", Color(0xFFE8884A)),
    AccentOption("green",  "Vert",   Color(0xFF4ECB8D)),
    AccentOption("cyan",   "Cyan",   Color(0xFF4AB8C8)),
    AccentOption("yellow", "Jaune",  Color(0xFFD4B84A)),
)

val DefaultAccentOption = AccentOptions.first { it.key == "blue" }

// ── CompositionLocal pour couleur accent ───────────────────────────────────────
val LocalAccentColor = compositionLocalOf { DefaultAccentOption.color }

// ── SharedPreferences helper ───────────────────────────────────────────────────
object ThemeManager {
    private const val PREFS_NAME = "toutienote_theme"
    private const val KEY_ACCENT  = "accent_color"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAccentKey(context: Context): String =
        prefs(context).getString(KEY_ACCENT, DefaultAccentOption.key) ?: DefaultAccentOption.key

    fun setAccentKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_ACCENT, key).apply()
    }

    fun getAccentColor(context: Context): Color =
        AccentOptions.firstOrNull { it.key == getAccentKey(context) }?.color
            ?: DefaultAccentOption.color
}
