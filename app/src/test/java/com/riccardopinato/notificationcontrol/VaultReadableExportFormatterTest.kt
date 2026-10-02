package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.export.VaultReadableExportFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultReadableExportFormatterTest {
    @Test
    fun csvCellEscapesQuotesCommasAndNewLines() {
        assertEquals(
            "\"hello, \"\"Anna\"\"\nline\"",
            VaultReadableExportFormatter.csvCell("hello, \"Anna\"\nline")
        )
    }

    @Test
    fun spreadsheetFormulaPrefixesAreNeutralized() {
        assertEquals(
            "\"'=HYPERLINK(\"\"https://example.invalid\"\")\"",
            VaultReadableExportFormatter.csvCell(
                "=HYPERLINK(\"https://example.invalid\")"
            )
        )
    }

    @Test
    fun nullCsvCellIsAnEmptyQuotedCell() {
        assertEquals("\"\"", VaultReadableExportFormatter.csvCell(null))
    }
}
