package com.toutieserver.toutienote.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Formate updatedAt (ISO) en texte relatif : "Il y a 2 min", "Hier", "15 fév.", etc.
 */
fun formatNoteDate(isoDate: String): String {
    if (isoDate.isBlank()) return ""
    return try {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val dt = LocalDateTime.parse(isoDate.take(19), formatter)
        val now = LocalDateTime.now()
        val days = ChronoUnit.DAYS.between(dt.toLocalDate(), now.toLocalDate())
        when {
            days == 0L -> {
                val mins = ChronoUnit.MINUTES.between(dt, now)
                when {
                    mins < 1 -> "À l'instant"
                    mins < 60 -> "Il y a $mins min"
                    else -> "Il y a ${ChronoUnit.HOURS.between(dt, now)} h"
                }
            }
            days == 1L -> "Hier"
            days < 7 -> "Il y a $days jours"
            else -> dt.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
        }
    } catch (e: Exception) {
        isoDate.take(10)
    }
}
