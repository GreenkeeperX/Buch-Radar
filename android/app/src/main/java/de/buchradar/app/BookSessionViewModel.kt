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
    val searchInput = mutableStateOf("")
    val scrollToBookId = mutableStateOf<String?>(null)
    val isSearching = mutableStateOf(false)
    val message = mutableStateOf<String?>(null)
    val detailsBook = mutableStateOf<BookEntity?>(null)
    val manualEntry = mutableStateOf<BookEntity?>(null)
    val editingBook = mutableStateOf<BookEntity?>(null)
    val scannerOpen = mutableStateOf(false)
    val coverCandidates = mutableStateOf<List<de.buchradar.app.network.CatalogBook>>(emptyList())
    /** Camera frame of a single book for the next catalog search only; memory only, never saved. */
    private var coverFrame: android.graphics.Bitmap? = null
    fun offerCoverFrame(frame: android.graphics.Bitmap?) { coverFrame?.takeIf { it !== frame }?.recycle(); coverFrame = frame }
    /** Hands the frame to the caller, who then owns and recycles it. */
    fun takeCoverFrame(): android.graphics.Bitmap? = coverFrame.also { coverFrame = null }
    var coverJob: kotlinx.coroutines.Job? = null
    override fun onCleared() { coverJob?.cancel(); offerCoverFrame(null); super.onCleared() }
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
    val momoxSummary = mutableStateOf<MomoxSummary?>(null)
    val summaryExpanded = mutableStateOf(false)
    val dismissedSummaryRun = mutableStateOf<Long?>(null)
    val deletingBook = mutableStateOf<BookEntity?>(null)
    val pendingUndo = mutableStateOf<PendingUndo?>(null)
    val settingsOpen = mutableStateOf(false)
    val saleOpen = mutableStateOf(false)
    /** Ids chosen on the sale screen; kept here rather than in the saved state, which large lists would overflow. */
    val saleDraft = mutableStateOf<List<String>?>(null)
    val showSoldBooks = mutableStateOf(false)
    val selectionMode = mutableStateOf(false)
    val selectedIsbns = mutableStateOf<Set<String>>(emptySet())
    val statusFilter = mutableStateOf("all")
    val sortMode = mutableStateOf("recent")
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
