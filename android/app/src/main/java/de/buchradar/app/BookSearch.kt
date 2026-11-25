package de.buchradar.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookInput
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogAccess
import de.buchradar.app.network.CatalogBook
import de.buchradar.app.network.CatalogResult
import android.graphics.Bitmap
import de.buchradar.app.cover.CoverConfirmation
import de.buchradar.app.fraktur.FrakturFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** List searches and their navigation state are owned by the session, including across rotation. */
internal class BookSearch(
    private val scope: CoroutineScope,
    private val byIsbn: suspend (String) -> CatalogResult<CatalogBook?> = { isbn ->
        CatalogAccess.report { BookCatalog.findByIsbn(isbn) }
    },
    private val byTitle: suspend (String) -> CatalogResult<List<CatalogBook>> = { title ->
        CatalogAccess.report { BookCatalog.findByRecognizedText(title) }
    },
    /** Candidate ids whose cover matches the photographed single book (see CoverVerifier). */
    private val confirmCovers: suspend (Bitmap, List<CatalogBook>) -> Set<String> = { _, _ -> emptySet() },
    /** Title text read with the old-script models (see FrakturReader), or null. */
    private val readOldScript: suspend (Bitmap) -> String? = { null }
) {
    val input = mutableStateOf("")
    val showSold = mutableStateOf(false)
    val scrollToBookId = mutableStateOf<String?>(null)
    val statusFilter = mutableStateOf(BookListQuery.FILTER_ALL)
    val sortMode = mutableStateOf(BookListQuery.SORT_RECENT)
    val message = mutableStateOf<String?>(null)
    var candidates by mutableStateOf(emptyList<CatalogBook>())
        private set
    var busy by mutableStateOf(false)
        private set
    var requestId = 0L
        private set
    private var job: Job? = null
    /** Photo behind the shown candidates, kept in memory for "Alte Schrift lesen" until the search is left. */
    private var shownFrame: Bitmap? = null
    var oldScriptAvailable by mutableStateOf(false)
        private set

    fun leave() {
        requestId++
        job?.cancel()
        job = null
        releaseShownFrame()
        if (busy || candidates.isNotEmpty()) message.value = null
        candidates = emptyList()
        busy = false
    }

    /**
     * [frame] is an in-memory photo of a single book (only from the camera behind coverEvidenceAllowed). This
     * search owns it from the call on and recycles it on every path, including cancellation.
     */
    private fun releaseShownFrame() { shownFrame?.recycle(); shownFrame = null; oldScriptAvailable = false }

    /** The running old-script reading only; cancelling it keeps the search, its first result and message. */
    private var reading: kotlinx.coroutines.Deferred<List<CatalogBook>?>? = null

    /** Stops a running old-script reading; the candidates found so far and their cover check stay. */
    fun cancelOldScript() {
        reading?.cancel()
    }

    /** Manual old-script reading of the photographed single book behind the shown candidates. */
    fun readOldScriptNow() {
        val frame = shownFrame?.let { runCatching { it.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() } ?: return
        oldScriptAvailable = false
        job?.cancel()
        val id = ++requestId
        busy = true
        job = scope.launch(start = kotlinx.coroutines.CoroutineStart.ATOMIC) {
            try {
                if (id != requestId) return@launch
                val shown = candidates
                val better = oldScriptSearch(frame, id)
                if (id != requestId || candidates !== shown) return@launch
                if (better != null) {
                    candidates = better
                    message.value = null
                    val confirmed = catchingOperation { confirmCovers(frame, better) }.getOrDefault(emptySet())
                    if (id == requestId && candidates === better) candidates = CoverConfirmation.apply(better, confirmed)
                } else message.value = "Auch mit alter Schrift kein passender Treffer."
            } finally {
                frame.recycle()
                if (id == requestId) { busy = false; if (message.value == OLD_SCRIPT_PROGRESS) message.value = null }
            }
        }
    }

    /**
     * Reads the title area with the old-script models and searches again as a cancellable child of the current
     * request; null unless a fitting hit appears or when the reading was cancelled.
     */
    private suspend fun oldScriptSearch(frame: Bitmap, id: Long): List<CatalogBook>? = kotlinx.coroutines.coroutineScope {
        val previous = message.value
        message.value = OLD_SCRIPT_PROGRESS
        val child = async {
            val text = catchingOperation { readOldScript(frame) }.getOrNull()
            if (text.isNullOrBlank() || id != requestId) return@async null
            val again = catchingOperation { byTitle(text) }.getOrNull()?.value.orEmpty()
            again.takeIf { list -> list.any { (it.textSimilarity ?: 0) >= FrakturFallback.FITTING_SIMILARITY } }
        }
        reading = child
        try {
            child.join()
            if (child.isCancelled) null else child.await()
        } finally {
            reading = null
            if (message.value == OLD_SCRIPT_PROGRESS) message.value = previous
        }
    }

    fun submit(action: BookInput, active: List<BookEntity>, sold: List<BookEntity>, frame: Bitmap? = null,
               insert: suspend (BookEntity) -> Long) {
        leave()
        val coverFrame = frame.takeIf { action is BookInput.SearchTitle }
        if (coverFrame == null) frame?.recycle()
        if (action == BookInput.Invalid) {
            message.value = "Bitte eine gültige ISBN oder einen Buchtitel eingeben."
            return
        }
        if (action is BookInput.AddIsbn) {
            val match = active.firstOrNull { it.isbn == action.isbn } ?: sold.firstOrNull { it.isbn == action.isbn }
            if (match != null) {
                coverFrame?.recycle()
                showSold.value = match.soldAt != null
                input.value = ""
                statusFilter.value = BookListQuery.FILTER_ALL
                scrollToBookId.value = match.id
                message.value = if (showSold.value) "Dieses Buch liegt im Bereich „Verkauft“." else "Dieses Buch ist bereits in deiner Liste."
                return
            }
        }
        showSold.value = false
        busy = true
        val id = requestId
        // ATOMIC: the body always starts, so its finally releases the frame even if cancelled right away.
        job = scope.launch(start = kotlinx.coroutines.CoroutineStart.ATOMIC) {
            try {
                if (id != requestId) return@launch
                when (action) {
                    is BookInput.SearchTitle -> {
                        message.value = "Suche nach Titel und Autor …"
                        val result = catchingOperation { byTitle(action.text) }
                        if (id != requestId) return@launch
                        candidates = result.getOrNull()?.value.orEmpty()
                        message.value = when {
                            result.isFailure -> "Buchkatalog nicht erreichbar. Bitte Internetverbindung prüfen."
                            result.getOrNull()?.warnings?.isNotEmpty() == true -> result.getOrThrow().warnings.joinToString("\n")
                            candidates.isEmpty() -> "Keine passenden Treffer gefunden. Prüfe den Text oder erfasse das Buch manuell."
                            else -> null
                        }
                        var found = candidates
                        // Old-script fallback: single book in focus, no ISBN, no fitting hit. The first list stays
                        // visible while reading; closing the progress note cancels only the reading.
                        if (coverFrame != null && FrakturFallback.shouldRun(hasSingleBookFrame = true, usedIsbn = false, candidates = found)) {
                            val noHitMessage = message.value
                            val better = oldScriptSearch(coverFrame, id)
                            if (id != requestId || candidates !== found) return@launch
                            if (better != null) { found = better; candidates = better; message.value = null }
                            else message.value = noHitMessage
                        } else if (coverFrame != null) {
                            // Keep the photo for a manual reading when the first list does not contain the book.
                            shownFrame = runCatching { coverFrame.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                            oldScriptAvailable = shownFrame != null
                        }
                        busy = false
                        if (coverFrame != null && found.isNotEmpty()) {
                            val confirmed = catchingOperation { confirmCovers(coverFrame, found) }.getOrDefault(emptySet())
                            // A newer search or leaving replaced the list meanwhile: keep that one untouched.
                            if (id == requestId && candidates === found) candidates = CoverConfirmation.apply(found, confirmed)
                        }
                    }
                    is BookInput.AddIsbn -> {
                        message.value = "Suche Titel und Buchdaten …"
                        val lookup = catchingOperation { byIsbn(action.isbn) }
                        if (id != requestId) return@launch
                        val metadata = lookup.getOrNull()?.value
                        if (metadata != null && metadata.conflictingFields.any { it == "title" || it == "author" }) {
                            candidates = listOf(metadata)
                            message.value = "Die Quellen nennen unterschiedliche Titel oder Autoren zur ISBN. Bitte die Buchdaten vergleichen und den Treffer bestätigen."
                            return@launch
                        }
                        val book = BookEntity(action.isbn, metadata?.title, metadata?.author, metadata?.publicationYear,
                            metadata?.coverUrl, metadataSource = metadata?.sources?.joinToString())
                        val saved = catchingOperation { insert(book) }
                        if (id != requestId) return@launch
                        if (saved.isFailure) {
                            message.value = "Buch konnte nicht gespeichert werden. Bitte erneut versuchen."
                            return@launch
                        }
                        input.value = ""
                        statusFilter.value = BookListQuery.FILTER_ALL
                        sortMode.value = BookListQuery.SORT_RECENT
                        message.value = when {
                            saved.getOrNull() == -1L -> "Dieses Buch ist bereits gespeichert."
                            lookup.isFailure -> "Katalog nicht erreichbar. ISBN gespeichert; Titel und Autor kannst du ergänzen."
                            metadata == null -> "ISBN gespeichert, aber im Katalog nicht gefunden. Titel und Autor kannst du ergänzen."
                            else -> listOfNotNull("Hinzugefügt: ${metadata.title}",
                                "Abweichende Katalogangaben – Ausgabe prüfen.".takeIf { metadata.conflictingFields.isNotEmpty() },
                                lookup.getOrNull()?.warnings?.joinToString(" ")?.takeIf(String::isNotBlank)
                            ).joinToString("\n")
                        }
                        if (saved.getOrNull() != -1L) scrollToBookId.value = book.id
                    }
                    BookInput.Invalid -> Unit
                }
            } finally {
                coverFrame?.recycle()
                if (id == requestId) busy = false
            }
        }
    }
}

internal const val OLD_SCRIPT_PROGRESS = "Alte Schrift wird gelesen … (zum Abbrechen schließen)"
