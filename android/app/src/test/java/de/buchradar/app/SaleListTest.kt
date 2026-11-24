package de.buchradar.app

import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.SaleList
import org.junit.Assert.*
import org.junit.Test

class SaleListTest {
    private val now = 1_800_000_000_000L
    private fun book(id: String, cents: Int?, status: String = "AVAILABLE", checked: Long? = now, sold: Long? = null, isbn: String = "9780140328721",
        attempt: String? = null) = BookEntity(isbn, "Titel $id", "Autor", null, momoxStatus = status, currentPriceCents = cents,
            lastCheckedAt = checked, soldAt = sold, lastAttemptStatus = attempt, id = id)

    @Test fun onlyActiveBooksWithAnOfferAreCandidatesHighestFirst() {
        val list = SaleList.candidates(listOf(
            book("a", 120), book("b", 450), book("c", 300, status = "NOT_AVAILABLE"),
            book("d", 200, sold = now), book("e", 0), book("f", 90, isbn = "")))
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(570L, SaleList.totalCents(list))
    }

    @Test fun oldOrFailedOffersNeedARecheck() {
        assertFalse(SaleList.needsRecheck(book("a", 100), now))
        assertTrue(SaleList.needsRecheck(book("b", 100, checked = now - SaleList.STALE_AFTER_MILLIS), now))
        assertTrue(SaleList.needsRecheck(book("c", 100, attempt = "ERROR"), now))
    }

    @Test fun sharedListNamesEveryBookWithIsbnAndSum() {
        val text = SaleList.shareText(listOf(book("a", 450), book("b", 120, isbn = "9780804429573")), now)
        assertTrue(text.startsWith("Bücher für Momox: 2 Bücher, zusammen 5,70"))
        assertTrue(text.lines().any { it.startsWith("• Titel a – Autor – ISBN 978") && it.contains("4,50") })
        assertTrue(text.contains("Maßgeblich ist das Angebot beim Verkauf."))
        assertFalse(text.contains("älter als ein Tag"))
    }

    @Test fun overviewAndSaleListAgreeOnZeroPrices() {
        val books = listOf(book("a", 0), book("b", 250))
        assertEquals(SaleList.candidates(books).size, BookListQuery.totals(books).availableCount)
    }

    @Test fun freshOfferWithLaterFailureIsNotCalledOld() {
        val text = SaleList.shareText(listOf(book("a", 450, attempt = "ERROR")), now)
        assertFalse(text.contains("älter als ein Tag"))
        assertTrue(text.contains("letzte Prüfung fehlgeschlagen"))
    }
}
