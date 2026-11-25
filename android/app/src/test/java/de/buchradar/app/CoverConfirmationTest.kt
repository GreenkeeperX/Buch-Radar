package de.buchradar.app

import de.buchradar.app.cover.CoverConfirmation
import de.buchradar.app.network.CatalogBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverConfirmationTest {
    private fun book(title: String, isbn: String) = CatalogBook(title, "A", 2000, isbn = isbn)

    @Test fun thresholdIs120Inliers() {
        assertFalse(CoverConfirmation.confirmed(null))
        // Another edition with the same illustration reached 110 in the device measurement.
        assertFalse(CoverConfirmation.confirmed(110))
        assertFalse(CoverConfirmation.confirmed(119))
        assertTrue(CoverConfirmation.confirmed(120))
    }

    @Test fun noConfirmationKeepsListUnchanged() {
        val list = listOf(book("Der Vorleser", "1"), book("Der Vorleser", "2"))
        assertEquals(list, CoverConfirmation.apply(list, emptySet()))
    }

    @Test fun confirmedEditionMovesAheadOfSameTitleOnly() {
        val other = book("Ganz anderes Buch", "9")
        val list = listOf(book("Der Vorleser", "1"), other, book("Der  Vorleser!", "2"))
        val result = CoverConfirmation.apply(list, setOf("isbn:2"))
        assertEquals(listOf("isbn:2", "isbn:9", "isbn:1"), result.map { it.candidateId })
        assertTrue(result[0].coverConfirmed)
        assertEquals(other, result[1])
    }

    @Test fun otherTitlesNeverMoveOrDisappear() {
        val list = listOf(book("Erstes", "1"), book("Zweites", "2"), book("Drittes", "3"))
        val result = CoverConfirmation.apply(list, setOf("isbn:3"))
        assertEquals(list.map { it.candidateId }, result.map { it.candidateId })
        assertEquals(3, result.size)
        assertTrue(result[2].coverConfirmed)
        assertFalse(result[0].coverConfirmed || result[1].coverConfirmed)
    }
}
