package com.toutieserver.toutienote.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    @Test
    fun formatNoteDate_empty_returnsEmpty() {
        assertEquals("", formatNoteDate(""))
    }

    @Test
    fun formatNoteDate_blank_returnsEmpty() {
        assertEquals("", formatNoteDate("   "))
    }

    @Test
    fun formatNoteDate_invalidFormat_returnsFirst10Chars() {
        assertEquals("2024-02-15", formatNoteDate("2024-02-15"))
    }

    @Test
    fun formatNoteDate_validIso_returnsFormatted() {
        val result = formatNoteDate("2024-02-15T10:30:00")
        assert(result.isNotEmpty())
        assert(result.length >= 2)
    }

    @Test
    fun formatNoteDate_oldDate_returnsRelativeOrFormatted() {
        val result = formatNoteDate("2020-01-01T12:00:00")
        assert(result.isNotEmpty())
        // Format français: "1 janv." ou "Il y a X jours" selon l'écart
        assert(result.any { it.isDigit() } || result.contains("janv") || result.contains("Il y a"))
    }
}
