package de.buchradar.app

import de.buchradar.app.scanner.ScannerMode
import de.buchradar.app.scanner.coverEvidenceAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverEvidenceTest {
    @Test fun singleBookAllowsVerificationRegardlessOfDetection() {
        assertTrue(coverEvidenceAllowed(ScannerMode.SINGLE_BOOK, detectedSingleCover = false, shelfCorrection = false))
        assertTrue(coverEvidenceAllowed(ScannerMode.SINGLE_BOOK, detectedSingleCover = true, shelfCorrection = false))
    }

    @Test fun automaticRequiresADetectedSingleCover() {
        assertFalse(coverEvidenceAllowed(ScannerMode.AUTOMATIC, detectedSingleCover = false, shelfCorrection = false))
        assertTrue(coverEvidenceAllowed(ScannerMode.AUTOMATIC, detectedSingleCover = true, shelfCorrection = false))
    }

    @Test fun shelfRejectsVerificationRegardlessOfDetection() {
        assertFalse(coverEvidenceAllowed(ScannerMode.SHELF, detectedSingleCover = false, shelfCorrection = false))
        assertFalse(coverEvidenceAllowed(ScannerMode.SHELF, detectedSingleCover = true, shelfCorrection = false))
    }

    @Test fun shelfCorrectionRejectsVerificationInEveryMode() {
        for (mode in ScannerMode.entries) {
            assertFalse(coverEvidenceAllowed(mode, detectedSingleCover = false, shelfCorrection = true))
            assertFalse(coverEvidenceAllowed(mode, detectedSingleCover = true, shelfCorrection = true))
        }
    }
}
