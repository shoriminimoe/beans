package com.example.beans.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for issue #19: after an autocomplete suggestion is applied
 * the caret must sit at the end of the inserted text, so the next keystroke
 * appends instead of landing mid-text.
 */
class AutocompleteCursorTest {
    @Test
    fun `applied suggestion places caret at end of text`() {
        val result = textFieldValueAtEnd("Expenses:Food")
        assertEquals("Expenses:Food", result.text)
        assertEquals(TextRange("Expenses:Food".length), result.selection)
    }

    @Test
    fun `caret is collapsed, not a selection range`() {
        val result = textFieldValueAtEnd("Assets:Cash")
        assertTrue(result.selection.collapsed)
        assertEquals(result.text.length, result.selection.start)
    }

    @Test
    fun `empty text places caret at zero`() {
        val result = textFieldValueAtEnd("")
        assertEquals(TextRange(0), result.selection)
    }

    @Test
    fun `incremental account segment keeps caret after trailing colon`() {
        val result = textFieldValueAtEnd("Expenses:")
        assertEquals(TextRange("Expenses:".length), result.selection)
    }
}
