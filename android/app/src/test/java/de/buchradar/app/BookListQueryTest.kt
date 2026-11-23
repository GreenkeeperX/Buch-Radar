package de.buchradar.app

import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.MessageTone
import de.buchradar.app.domain.messageTone
import org.junit.Assert.*
import org.junit.Test

class BookListQueryTest {
    private fun book(id: String, title: String?, status: String = "UNKNOWN", price: Int? = null, checked: Long? = null,
                     created: Long = 0, isbn: String = "", author: String? = null, attempt: String? = null) =
        BookEntity(isbn = isbn, title = title, author = author, momoxStatus = status, currentPriceCents = price,
            lastCheckedAt = checked, createdAt = created, lastAttemptStatus = attempt, id = id)

    @Test fun searchIgnoresCaseAccentsAndSharpS() {
        val books = listOf(book("1", "Die Straße", author = "Thomas Müller"), book("2", "Anderes"))
        assertEquals(listOf("1"), BookListQuery.apply(books, "muller strasse", "all", "recent", false).map { it.id })
        assertEquals(listOf("1"), BookListQuery.apply(books, "STRAßE", "all", "recent", false).map { it.id })
    }

    @Test fun formattedIsbnFindsCompactStoredIsbn() {
        val books = listOf(book("1", "A", isbn = "9780804429573"), book("2", "B", isbn = "9780140328721"))
        assertEquals(listOf("1"), BookListQuery.apply(books, "978-0-8044-2957-3", "all", "recent", false).map { it.id })
    }

    @Test fun statusFiltersUseStoredState() {
        val books = listOf(
            book("offer", "A", "AVAILABLE", 250, checked = 1),
            book("none", "B", "NOT_AVAILABLE", checked = 1),
            book("new", "C"),
            book("failed", "D", "AVAILABLE", 100, checked = 1, attempt = "ERROR")
        )
        fun ids(filter: String) = BookListQuery.apply(books, "", filter, "title_up", false).map { it.id }
        assertEquals(listOf("offer", "failed"), ids("available"))
        assertEquals(listOf("none"), ids("unavailable"))
        assertEquals(listOf("new"), ids("unchecked"))
        assertEquals(listOf("failed"), ids("error"))
        assertEquals(4, ids("all").size)
    }

    @Test fun titleSortUsesGermanCollationAndPutsUntitledLast() {
        val books = listOf(book("z", "Zeit"), book("none", null), book("u", "Über Bücher"), book("a", "abend"))
        assertEquals(listOf("a", "u", "z", "none"), BookListQuery.apply(books, "", "all", "title_up", false).map { it.id })
        assertEquals(listOf("z", "u", "a", "none"), BookListQuery.apply(books, "", "all", "title_down", false).map { it.id })
    }

    @Test fun priceSortKeepsBooksWithoutOfferAtTheEndInBothDirections() {
        val books = listOf(
            book("cheap", "A", "AVAILABLE", 50), book("none", "B", "NOT_AVAILABLE"),
            book("rich", "C", "AVAILABLE", 900), book("stale", "D", "UNKNOWN", 700)
        )
        assertEquals(listOf("rich", "cheap", "none", "stale"), BookListQuery.apply(books, "", "all", "price_down", false).map { it.id })
        assertEquals(listOf("cheap", "rich", "none", "stale"), BookListQuery.apply(books, "", "all", "price_up", false).map { it.id })
    }

    @Test fun recentSortShowsNewestFirst() {
        val books = listOf(book("old", "A", created = 1), book("new", "B", created = 5))
        assertEquals(listOf("new", "old"), BookListQuery.apply(books, "", "all", "recent", false).map { it.id })
    }

    @Test fun totalsCountOnlyCurrentOffers() {
        val totals = BookListQuery.totals(listOf(
            book("1", "A", "AVAILABLE", 250, checked = 1, isbn = "1"),
            book("2", "B", "AVAILABLE", 175, checked = 1, isbn = "2"),
            book("3", "C", "NOT_AVAILABLE", 999, checked = 1, isbn = "3"),
            book("4", "D", isbn = "4"),
            book("5", "E")
        ))
        assertEquals(5, totals.count)
        assertEquals(2, totals.availableCount)
        assertEquals(425L, totals.availableValueCents)
        assertEquals(1, totals.uncheckedCount)
    }

    @Test fun isbnInputIsFormattedButTitlesAreNot() {
        assertEquals("978-0-8044-2957-3", BookInputFormat.formatBookInput("9780804429573"))
        assertEquals("Der Process", BookInputFormat.formatBookInput("Der Process"))
        assertEquals("", BookInputFormat.formatBookInput(""))
        assertNull(BookInputFormat.parseIsbn("9780804429574"))
    }

    @Test fun messagesGetAppropriateTone() {
        assertEquals(MessageTone.ERROR, messageTone("Buch konnte nicht gespeichert werden. Bitte erneut versuchen."))
        assertEquals(MessageTone.ERROR, messageTone("Katalog nicht erreichbar. ISBN gespeichert; Titel und Autor kannst du ergänzen."))
        assertEquals(MessageTone.SUCCESS, messageTone("Hinzugefügt: Der Process"))
        assertEquals(MessageTone.PROGRESS, messageTone("Suche Titel und Buchdaten …"))
        assertEquals(MessageTone.INFO, messageTone("Dieses Buch ist bereits in deiner Liste."))
    }
}
