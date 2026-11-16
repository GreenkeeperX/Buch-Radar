package de.buchradar.app

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.buchradar.app.data.AtomicShelfReviewStore
import de.buchradar.app.domain.BookIdentity
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CoverImageMatcher
import de.buchradar.app.data.BookEntity
import de.buchradar.app.scanner.ScannerMode
import java.io.File

/** Search/review state survives activity recreation; bitmaps never enter saved bundles. */
class BookSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    val searchInput = mutableStateOf("")
    val scrollToBookId = mutableStateOf<String?>(null)
    val isSearching = mutableStateOf(false)
    val message = mutableStateOf<String?>(null)
    val detailsBook = mutableStateOf<BookEntity?>(null)
    val manualEntry = mutableStateOf<BookEntity?>(null)
    val editingBook = mutableStateOf<BookEntity?>(null)
    val deletingBook = mutableStateOf<BookEntity?>(null)
    val scannerOpen = mutableStateOf(false)
    val coverFrame = mutableStateOf<Bitmap?>(null)
    val visualCoverTest = mutableStateOf(false)
    val coverCandidates = mutableStateOf<List<de.buchradar.app.network.CatalogBook>>(emptyList())
    val shelfReviewOpen = mutableStateOf(false)
    val shelfReview = ShelfReviewController(viewModelScope,
        AtomicShelfReviewStore(File(application.noBackupFilesDir, "shelf-review.json"))) { text, frame ->
        val isbn = BookIdentity.canonicalIsbn(text) ?: BookIdentity.unambiguousIsbn(text)
        val matches = if (isbn != null) listOfNotNull(BookCatalog.findByIsbn(isbn))
            else BookCatalog.findByRecognizedText(text)
        if (frame != null) CoverImageMatcher.rank(application, frame, matches) else matches
    }
    val shelfCorrectionQuery = mutableStateOf<String?>(null)
    val shelfEditQuery = mutableStateOf<String?>(null)
    val shelfEditText = mutableStateOf("")
    val scannerStartMode = mutableStateOf(ScannerMode.AUTOMATIC)
    val momoxSummary = mutableStateOf<MomoxSummary?>(null)
    val summaryExpanded = mutableStateOf(false)
    val settingsOpen = mutableStateOf(false)
    val clearLogConfirmOpen = mutableStateOf(false)
    val logEntries = mutableStateOf(emptyList<String>())
    val autoCheckEnabled = mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("auto_check", false))
    val autoCheckDays = mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getInt("auto_check_days", 7))
    val notificationsEnabled = mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("notifications", false))
    val notifyOnlyAboveThreshold = mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("notify_threshold", false))
    val notifyThresholdCents = mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getInt("notify_threshold_cents", 1_000))
    val showSoldBooks = mutableStateOf(false)
    val selectionMode = mutableStateOf(false)
    val selectedIsbns = mutableStateOf<Set<String>>(emptySet())
    val statusFilter = mutableStateOf("all")
    val sortMode = mutableStateOf("recent")
    val filterMenuOpen = mutableStateOf(false)
    val sortMenuOpen = mutableStateOf(false)
    val momoxCheckDialogOpen = mutableStateOf(false)
    val momoxCheckMode = mutableStateOf("stale")
    val thresholdMenuOpen = mutableStateOf(false)
    val themeMenuOpen = mutableStateOf(false)
    val autoCheckMenuOpen = mutableStateOf(false)
}
