package de.buchradar.app.scanner

enum class ScannerMode { AUTOMATIC, SINGLE_BOOK, SHELF }

/** Gate for any future cover verification. Shelf scans and shelf corrections always use text alone. */
fun coverEvidenceAllowed(mode: ScannerMode, detectedSingleCover: Boolean, shelfCorrection: Boolean): Boolean =
    !shelfCorrection && when (mode) {
        ScannerMode.SINGLE_BOOK -> true
        ScannerMode.AUTOMATIC -> detectedSingleCover
        ScannerMode.SHELF -> false
    }
