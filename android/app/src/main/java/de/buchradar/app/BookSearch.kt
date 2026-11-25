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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** List searches and their navigation state are owned by the session, including across rotation. */
internal class BookSearch(
    private val scope: CoroutineScope,
    private val byIsbn: suspend (String) -> CatalogResult<CatalogBook?> = { isbn ->
        CatalogAccess.report { BookCatalog.findByIsbn(isbn) }
    },
    private val byTitle: suspend (String) -> CatalogResult<List<CatalogBook>> = { title ->
        CatalogAccess.report { BookCatalog.findByRecognizedText(title) }
    }
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

    fun leave() {
        requestId++
        job?.cancel()
        job = null
        if (busy || candidates.isNotEmpty()) message.value = null
        candidates = emptyList()
        busy = false
    }

    fun submit(action: BookInput, active: List<BookEntity>, sold: List<BookEntity>, insert: suspend (BookEntity) -> Long) {
        leave()
        if (action == BookInput.Invalid) {
            message.value = "Bitte eine gültige ISBN oder einen Buchtitel eingeben."
            return
        }
        if (action is BookInput.AddIsbn) {
            val match = active.firstOrNull { it.isbn == action.isbn } ?: sold.firstOrNull { it.isbn == action.isbn }
            if (match != null) {
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
        job = scope.launch {
            try {
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
                if (id == requestId) busy = false
            }
        }
    }
}
