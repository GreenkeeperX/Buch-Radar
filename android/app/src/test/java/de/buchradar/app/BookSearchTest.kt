package de.buchradar.app

import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookInput
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.network.CatalogBook
import de.buchradar.app.network.CatalogResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookSearchTest {
    @Test fun typedPastedAndScannedInputsHaveOneAction() {
        for (text in listOf("9780140328721", "978-0-14-032872-1", "ISBN 9780140328721", "Titel\n9780140328721")) {
            assertEquals(BookInput.AddIsbn("9780140328721"), BookInputFormat.classify(text))
        }
        for (text in listOf("Der Process", "ISBN 9780140328721 und 9780804429573")) {
            assertEquals(BookInput.SearchTitle(text), BookInputFormat.classify(text))
        }
        for (text in listOf("", "   ", "9780140\n328721", "9780804429574")) {
            assertEquals(BookInput.Invalid, BookInputFormat.classify(text))
        }
    }

    @Test fun duplicateScanFromEitherTabRevealsTheMatchingRow() = runTest {
        val search = BookSearch(this)
        val active = BookEntity("9780140328721")
        val sold = BookEntity("9780804429573", soldAt = 1L)
        for (startOnSold in listOf(false, true)) for (match in listOf(active, sold)) {
            search.showSold.value = startOnSold
            search.input.value = "ISBN ${match.isbn}"
            search.statusFilter.value = BookListQuery.FILTER_AVAILABLE
            search.submit(BookInputFormat.classify(search.input.value), listOf(active), listOf(sold)) { error("unexpected insert") }
            assertEquals(match.soldAt != null, search.showSold.value)
            assertEquals(match.id, search.scrollToBookId.value)
            assertEquals("", search.input.value)
            assertEquals(BookListQuery.FILTER_ALL, search.statusFilter.value)
        }
    }

    @Test fun replacedSearchCannotPublishOrClearTheNextRequestsBusyState() = runTest {
        val slow = CompletableDeferred<Unit>()
        val next = CompletableDeferred<Unit>()
        val lookup = { title: String -> CatalogBook(title, null, null) }
        val search = BookSearch(this, byTitle = { title ->
            // Model a source that finishes delivering even after its caller cancelled it.
            withContext(NonCancellable) { (if (title == "alt") slow else next).await() }
            CatalogResult(listOf(lookup(title)), listOf(title))
        })
        fun submit(title: String) = search.submit(BookInput.SearchTitle(title), emptyList(), emptyList()) { error("unexpected insert") }
        submit("alt")
        runCurrent()
        submit("neu")
        runCurrent()
        slow.complete(Unit)
        runCurrent()
        assertTrue(search.busy)
        assertTrue(search.candidates.isEmpty())
        assertEquals("Suche nach Titel und Autor …", search.message.value)
        next.complete(Unit)
        runCurrent()
        assertFalse(search.busy)
        assertEquals(listOf(lookup("neu")), search.candidates)
        assertEquals("neu", search.message.value)
        search.leave()
        assertTrue(search.candidates.isEmpty())
        assertNull(search.message.value)
    }

    @Test fun leavingWhileIsbnLookupRunsPreventsLateInsertAndNavigation() = runTest {
        val reply = CompletableDeferred<Unit>()
        val search = BookSearch(this, byIsbn = {
            withContext(NonCancellable) { reply.await() }
            CatalogResult(null, emptyList())
        })
        var inserted = false
        search.submit(BookInput.AddIsbn("9780140328721"), emptyList(), emptyList()) { inserted = true; 1 }
        runCurrent()
        search.leave()
        search.showSold.value = true
        reply.complete(Unit)
        runCurrent()
        assertFalse(inserted)
        assertTrue(search.showSold.value)
        assertNull(search.scrollToBookId.value)
        assertNull(search.message.value)
        assertFalse(search.busy)
    }
}
