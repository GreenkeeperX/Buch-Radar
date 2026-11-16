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

    @Test fun repeatedMergingRetainsOriginalFieldSourceAndConflictingYears() {
        val first = BookCatalog.mergeCandidates(listOf(
            CatalogBook("Titel", null, 1990, "9780804429573", sources = setOf("A")),
            CatalogBook("Titel", "Autor", 2001, "9780804429573", sources = setOf("B")))).single()
        val second = BookCatalog.mergeCandidates(listOf(first,
            CatalogBook("TITEL", "Autor", 1990, "080442957X", sources = setOf("C")))).single()
        assertEquals("9780804429573", second.isbn)
        assertEquals("B, C", second.fieldSources["author"])
        assertEquals(setOf("year"), second.conflictingFields)
        assertEquals(setOf("1990", "2001"), second.fieldValues["year"]!!.map { it.value }.toSet())
        assertEquals(setOf("A", "C"), second.fieldValues["year"]!!.first().sources)
    }

    @Test fun differentTitlesForSameIsbnStayVisibleAsConflict() {
        val match = BookCatalog.mergeCandidates(listOf(
            CatalogBook("Richtiger Titel", "Autor", null, "9780804429573", sources = setOf("A")),
            CatalogBook("Anderer Titel", "Autor", null, "9780804429573", sources = setOf("B")))).single()
        assertEquals("Richtiger Titel", match.title)
        assertTrue("title" in match.conflictingFields)
        assertEquals(2, match.fieldValues["title"]!!.size)
    }
}
