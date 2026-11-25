package de.buchradar.app

import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.SaleList
import org.junit.Assert.*
import org.junit.Test

class MomoxCheckDueTest {
    private val now = 1_800_000_000_000L
    private val fresh = BookEntity("9780140328721", lastCheckedAt = now)

    @Test fun nextThresholdUsesTheOldestEligibleBook() {
        val oldest = fresh.copy(isbn = "9780804429573", lastCheckedAt = now - SaleList.STALE_AFTER_MILLIS + 1)
        val eligible = SaleList.eligibleForCheck(listOf(fresh, oldest, fresh.copy(isbn = "", lastCheckedAt = null)))
        assertFalse(eligible.any { SaleList.needsRecheck(it, now) })
        assertEquals(now + 1, SaleList.nextRecheckAt(eligible, now))
        assertTrue(eligible.any { SaleList.needsRecheck(it, now + 1) })
    }

    @Test fun uncheckedAndFailedBooksUseTheSaleAndDialogRule() {
        for (due in listOf(fresh.copy(lastCheckedAt = null), fresh.copy(lastAttemptStatus = "ERROR"))) {
            assertTrue(SaleList.eligibleForCheck(listOf(fresh, due)).any { SaleList.needsRecheck(it, now) })
            assertNull(SaleList.nextRecheckAt(listOf(due), now))
        }
    }

    @Test fun soldAndIsbnLessBooksCannotStartACheck() {
        val eligible = SaleList.eligibleForCheck(listOf(fresh.copy(soldAt = now), BookEntity("")))
        assertTrue(eligible.isEmpty())
        assertNull(SaleList.nextRecheckAt(eligible, now))
    }

    @Test fun currentErrorCountMatchesDestinationAfterOtherChecksSalesAndRemoval() {
        val failed = fresh.copy(lastAttemptStatus = "ERROR")
        val legacy = BookEntity("9780804429573", momoxStatus = "ERROR")
        val successful = BookEntity("9783125171541", lastCheckedAt = now)
        for ((active, expected) in listOf(
            listOf(failed, legacy, successful) to 2,
            listOf(legacy, successful) to 1,
            listOf(successful) to 0
        )) {
            val count = BookListQuery.totals(active).errorCount
            assertEquals(expected, count)
            assertEquals(BookListQuery.apply(active, "", BookListQuery.FILTER_ERROR, BookListQuery.SORT_RECENT, false).size, count)
        }
    }
}
