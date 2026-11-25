package de.buchradar.app

import de.buchradar.app.fraktur.FrakturFallback
import de.buchradar.app.scanner.ScannerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OldScriptOfferTest {
    @Test fun offeredInBookModeAfterThreeSteadyBlankSeconds() =
        assertTrue(FrakturFallback.offerInCamera(ScannerMode.SINGLE_BOOK, correcting = false, blankSteadyForMillis = 3_000))
    @Test fun notBeforeThreeSeconds() =
        assertFalse(FrakturFallback.offerInCamera(ScannerMode.SINGLE_BOOK, false, 2_999))
    @Test fun notWithoutSteadyBlankImage() =
        assertFalse(FrakturFallback.offerInCamera(ScannerMode.SINGLE_BOOK, false, null))
    @Test fun neverInShelfOrAutomaticMode() {
        assertFalse(FrakturFallback.offerInCamera(ScannerMode.SHELF, false, 10_000))
        assertFalse(FrakturFallback.offerInCamera(ScannerMode.AUTOMATIC, false, 10_000))
    }
    @Test fun neverInShelfCorrection() =
        assertFalse(FrakturFallback.offerInCamera(ScannerMode.SINGLE_BOOK, correcting = true, blankSteadyForMillis = 10_000))
}
