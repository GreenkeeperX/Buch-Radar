package de.buchradar.app

import de.buchradar.app.scanner.ScannerMode
import de.buchradar.app.scanner.ScannerSelection
import org.junit.Assert.*
import org.junit.Test

class ScannerSelectionTest {
    @Test fun switchingAwayAndBackInvalidatesFramesAndManualRequests() {
        val selection = ScannerSelection(ScannerMode.SINGLE_BOOK, null)
        val previous = requireNotNull(selection.current)
        previous.manualText.set("alter Text")
        assertTrue(selection.select(ScannerMode.SHELF))
        assertTrue(selection.select(ScannerMode.SINGLE_BOOK))
        val next = requireNotNull(selection.current)
        assertNotSame(previous, next)
        assertNull(next.manualText.get())
        previous.delivered.set(true)
        assertFalse(next.delivered.get())
        selection.close()
        assertNull(selection.current)
        assertFalse(selection.select(ScannerMode.AUTOMATIC))
    }

    @Test fun correctionAlwaysUsesSingleBookAndKeepsItsTarget() {
        val selection = ScannerSelection(ScannerMode.SHELF, "regal-3")
        for (choice in ScannerMode.entries) assertFalse(selection.select(choice))
        assertEquals(ScannerMode.SINGLE_BOOK, selection.current?.mode)
        assertEquals("regal-3", selection.correctionGroupId)
    }
}
