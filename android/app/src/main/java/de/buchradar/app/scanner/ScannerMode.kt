package de.buchradar.app.scanner

enum class ScannerMode { AUTOMATIC, SINGLE_BOOK, SHELF }

/** Gate for any future cover verification. Shelf scans and shelf corrections always use text alone. */
fun coverEvidenceAllowed(mode: ScannerMode, detectedSingleCover: Boolean, shelfCorrection: Boolean): Boolean =
    !shelfCorrection && when (mode) {
        ScannerMode.SINGLE_BOOK -> true
        ScannerMode.AUTOMATIC -> detectedSingleCover
        ScannerMode.SHELF -> false
    }

/** Each mode selection owns its manual tap and delivery flag, even after switching back to the same mode. */
internal class ScanGeneration(val mode: ScannerMode) {
    val manualText = java.util.concurrent.atomic.AtomicReference<String?>(null)
    val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
}

internal class ScannerSelection(initial: ScannerMode, val correctionGroupId: String?) {
    @Volatile var current: ScanGeneration? = ScanGeneration(if (correctionGroupId == null) initial else ScannerMode.SINGLE_BOOK)
        private set

    // Selected and closed only on the UI thread. The analyzer snapshots current before processing a frame.
    fun select(mode: ScannerMode): Boolean {
        if (current == null || correctionGroupId != null || current?.mode == mode) return false
        current = ScanGeneration(mode)
        return true
    }

    fun close() { current = null }
}
