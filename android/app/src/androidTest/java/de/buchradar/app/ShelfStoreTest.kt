package de.buchradar.app

import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.data.AtomicShelfReviewStore
import de.buchradar.app.network.CatalogBook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ShelfStoreTest {
    @Test fun pendingAndPostponedFindsRoundTripWithEditionMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "shelf-test-${UUID.randomUUID()}.json")
        try {
            val edition = CatalogBook("Über Bücher", "Autor", 1999, "9780804429573", sources = setOf("Testquelle"), fieldSources = mapOf("title" to "Testquelle"))
            val expected = ShelfReviewState(listOf(
                ShelfLookupGroup("a", "Autor\nÜber Bücher", listOf(edition), pending = false, postponed = true),
                ShelfLookupGroup("b", "Weiterer Fund", revision = 2)
            ), totalCount = 3)
            AtomicShelfReviewStore(file).write(expected)
            assertEquals(expected, AtomicShelfReviewStore(file).read())
            assertFalse(file.readText().contains("Bitmap"))
        } finally { file.delete() }
    }

    @Test fun corruptedStateIsNotSilentlyReplacedByEmptyReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "shelf-test-${UUID.randomUUID()}.json")
        try {
            file.writeText("unvollständige Datei")
            assertTrue(runCatching { AtomicShelfReviewStore(file).read() }.isFailure)
            assertEquals("unvollständige Datei", file.readText())
        } finally { file.delete() }
    }
}
