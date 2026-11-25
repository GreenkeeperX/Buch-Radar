package de.buchradar.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomoxCheckDueTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test fun freshCheckStaysQuiet() = assertFalse(momoxCheckDue(now - day + 1, hasUncheckedIsbn = false, now = now))
    @Test fun exactlyOneDayIsDue() = assertTrue(momoxCheckDue(now - day, hasUncheckedIsbn = false, now = now))
    @Test fun uncheckedBookWithIsbnIsDue() = assertTrue(momoxCheckDue(now - 60_000, hasUncheckedIsbn = true, now = now))
    @Test fun neverCheckedIsDue() = assertTrue(momoxCheckDue(null, hasUncheckedIsbn = false, now = now))
}
