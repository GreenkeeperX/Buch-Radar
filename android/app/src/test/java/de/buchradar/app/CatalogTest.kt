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

    private val hailey = listOf(
        CatalogBook("Hotel", "Arthur Hailey", 1965),
        CatalogBook("Airport", "Arthur Hailey", 1968),
        CatalogBook("Arthur und die Minimoys", "Luc Besson", 2004))

    @Test fun authorAloneIsRecognizedAsAuthorOnly() {
        assertEquals("Arthur Hailey", BookCatalog.authorOnlyName("ARTHUR HAILEY", hailey))
        assertEquals("Arthur Hailey", BookCatalog.authorOnlyName("Arthur Hailey\nRoman", hailey))
    }

    @Test fun recognizedTitleIsNotAuthorOnly() {
        assertNull(BookCatalog.authorOnlyName("Arthur Hailey Hotel", hailey))
        assertNull(BookCatalog.authorOnlyName("Tom Reiss Der Orientalist", listOf(CatalogBook("Der Orientalist", "Tom Reiss", 2008))))
        // A title word that happens to be a first name is not an author.
        assertNull(BookCatalog.authorOnlyName("Arthur Minimoys", hailey))
    }

    @Test fun catalogNameInInvertedOrderStillMatches() {
        assertEquals("Hailey, Arthur", BookCatalog.authorOnlyName("Arthur Hailey", listOf(CatalogBook("Hotel", "Hailey, Arthur", 1966))))
    }

    @Test fun authorChoicesShowDifferentTitlesBeforeFurtherEditions() {
        val first = CatalogBook("Hotel", "Arthur Hailey", 1965, "9780804429573")
        val second = CatalogBook("Hotel", "Arthur Hailey", 1990, "9780140328721")
        val other = CatalogBook("Airport", "Arthur Hailey", 1968)
        assertEquals(listOf(first, other, second), BookCatalog.authorChoiceOrder(listOf(first, second, other)))
    }
}
