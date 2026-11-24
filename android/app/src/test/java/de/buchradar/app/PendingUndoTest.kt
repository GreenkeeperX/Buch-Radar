package de.buchradar.app

import de.buchradar.app.data.BookEntity
import org.junit.Assert.*
import org.junit.Test

class PendingUndoTest {
    private fun removed(isbn: String) = PendingUndo("„$isbn“ entfernt", deleted = listOf(BookEntity(isbn, title = isbn)))

    @Test fun secondDeletionKeepsTheFirstUndoable() {
        val both = removed("9780804429573") + removed("9780140328721")
        assertEquals(listOf("9780804429573", "9780140328721"), both.deleted.map { it.isbn })
        assertEquals("2 Bücher entfernt", both.text)
    }

    @Test fun deletionAndSaleAreUndoneTogether() {
        val both = PendingUndo("Als verkauft markiert", sold = mapOf("a" to 1L)) + removed("9780804429573") +
            PendingUndo("Als verkauft markiert", sold = mapOf("a" to 2L))
        assertEquals(mapOf("a" to 2L), both.sold)
        assertEquals(1, both.deleted.size)
        assertEquals("1 Buch entfernt, 1 als verkauft markiert", both.text)
    }
}
