package de.buchradar.app

import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogBook
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    @Test fun differentAuthorsWithoutIsbnHaveDifferentKeys() {
        assertNotEquals(CatalogBook("Heimat", "Autor A", 2001).candidateId, CatalogBook("Heimat", "Autor B", 2001).candidateId)
    }
    @Test fun editionsStaySeparate() {
        val matches = BookCatalog.mergeCandidates(listOf(
            CatalogBook("Titel", "Autor", 1990, "9780804429573"),
            CatalogBook("Titel", "Autor", 2000, "9780140328721")))
        assertEquals(2, matches.size)
    }
    @Test fun sameEditionCombinesFieldsAndRetainsProvenance() {
        val match = BookCatalog.mergeCandidates(listOf(
            CatalogBook("Titel", null, null, "9780804429573", sources = setOf("A")),
            CatalogBook("Titel", "Autor", 2001, "9780804429573", sources = setOf("B")))).single()
        assertEquals("Autor", match.author)
        assertEquals("B", match.fieldSources["author"])
        assertEquals(setOf("A", "B"), match.sources)
    }
}
