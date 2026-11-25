package de.buchradar.app

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.buchradar.app.data.AtomicShelfReviewStore
import de.buchradar.app.domain.BookIdentity
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.data.BookEntity
import de.buchradar.app.scanner.ScannerMode
import java.io.File

/** Search and review state survives activity recreation. */
class BookSessionViewModel(application: Application) : AndroidViewModel(application) {
    internal val search = BookSearch(viewModelScope, confirmCovers = { frame, books -> de.buchradar.app.cover.CoverVerifier.confirm(application, frame, books) })
    val detailsBook = mutableStateOf<BookEntity?>(null)
    val manualEntry = mutableStateOf<BookEntity?>(null)
    val editingBook = mutableStateOf<BookEntity?>(null)
    val scannerOpen = mutableStateOf(false)
    val shelfReviewOpen = mutableStateOf(false)
    val shelfReview = ShelfReviewController(viewModelScope,
        AtomicShelfReviewStore(File(application.noBackupFilesDir, "shelf-review.json"))) { text ->
        val isbn = BookIdentity.canonicalIsbn(text) ?: BookIdentity.unambiguousIsbn(text)
        if (isbn != null) listOfNotNull(BookCatalog.findByIsbn(isbn))
        else BookCatalog.findByRecognizedText(text)
    }
    val shelfCorrectionQuery = mutableStateOf<String?>(null)
    val shelfEditQuery = mutableStateOf<String?>(null)
    val shelfEditText = mutableStateOf("")
    val scannerStartMode = mutableStateOf(ScannerMode.AUTOMATIC)
    val deletingBook = mutableStateOf<BookEntity?>(null)
    val pendingUndo = mutableStateOf<PendingUndo?>(null)
    val settingsOpen = mutableStateOf(false)
    val saleOpen = mutableStateOf(false)
    /** Ids chosen on the sale screen; kept here rather than in the saved state, which large lists would overflow. */
    val saleDraft = mutableStateOf<List<String>?>(null)
    val selectionMode = mutableStateOf(false)
    val selectedIsbns = mutableStateOf<Set<String>>(emptySet())
    val sortMenuOpen = mutableStateOf(false)
    val momoxCheckDialogOpen = mutableStateOf(false)
    val momoxCheckMode = mutableStateOf("stale")
}

/**
 * Actions the snackbar can still undo: removed book rows and books marked as sold. [sold] maps
 * each id to the date its sale wrote, so undo only touches rows that still carry that sale.
 */
class PendingUndo(val text: String, val deleted: List<BookEntity> = emptyList(), val sold: Map<String, Long> = emptyMap()) {
    /** A further action while an earlier one can still be undone joins it, so the earlier action keeps its undo. */
    operator fun plus(next: PendingUndo): PendingUndo {
        val rows = (deleted + next.deleted).distinctBy { it.id }
        val sold = sold + next.sold
        val text = listOfNotNull(
            rows.size.takeIf { it > 0 }?.let { if (it == 1) "1 Buch entfernt" else "$it Bücher entfernt" },
            sold.size.takeIf { it > 0 }?.let { if (it == 1) "1 als verkauft markiert" else "$it als verkauft markiert" }
        ).joinToString(", ")
        return PendingUndo(text, rows, sold)
    }
}
