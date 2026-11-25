package de.buchradar.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CheckedAtFormatTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun todayYesterdayAndOlderDates() {
        val now = at(2026, 11, 24, 21, 0)
        assertEquals("heute 14:05", formatCheckedAt(at(2026, 11, 24, 14, 5), now))
        assertEquals("gestern 09:12", formatCheckedAt(at(2026, 11, 23, 9, 12), now))
        assertEquals("21.11.2026", formatCheckedAt(at(2026, 11, 21, 8, 0), now))
        // Across a year boundary "yesterday" still works.
        assertEquals("gestern 23:59", formatCheckedAt(at(2025, 12, 31, 23, 59), at(2026, 1, 1, 0, 5)))
    }
}
