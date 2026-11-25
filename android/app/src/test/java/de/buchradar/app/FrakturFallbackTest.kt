package de.buchradar.app

import android.graphics.Rect
import de.buchradar.app.fraktur.FrakturFallback
import de.buchradar.app.network.CatalogBook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrakturFallbackTest {
    private fun hit(similarity: Int?) = CatalogBook("T", "A", 2000, isbn = "1", textSimilarity = similarity)

    @Test fun runsOnlyForSingleBookWithoutFittingHit() {
        assertTrue(FrakturFallback.shouldRun(true, false, emptyList()))
        assertTrue(FrakturFallback.shouldRun(true, false, listOf(hit(59), hit(null))))
    }
    @Test fun fittingHitStopsIt() = assertFalse(FrakturFallback.shouldRun(true, false, listOf(hit(20), hit(60))))
    @Test fun neverWithoutSingleBookFrame() = assertFalse(FrakturFallback.shouldRun(false, false, emptyList()))
    @Test fun neverAfterIsbn() = assertFalse(FrakturFallback.shouldRun(true, true, emptyList()))
    @Test fun noWordsNoTitleArea() = assertTrue(FrakturFallback.titleRect(emptyList(), 100, 100) == null)
}
