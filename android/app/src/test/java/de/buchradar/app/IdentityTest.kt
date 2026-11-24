package de.buchradar.app

import de.buchradar.app.domain.BookIdentity
import de.buchradar.app.domain.catchingOperation
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class IdentityTest {
    @Test fun isbnAliasesShareIdentity() {
        assertEquals("9780804429573", BookIdentity.canonicalIsbn("0-8044-2957-X"))
        assertEquals(BookIdentity.canonicalIsbn("080442957X"), BookIdentity.canonicalIsbn("9780804429573"))
    }
    @Test fun invalidChecksumIsRejected() { assertNull(BookIdentity.canonicalIsbn("9780804429574")) }
    @Test fun detectsLabelledIsbnAnywhereOnLine() {
        assertEquals("9780804429573", BookIdentity.unambiguousIsbn("Autor\nISBN: 0-8044-2957-X (gebunden)\nSeite 12"))
    }
    @Test fun doesNotConcatenateSeparateLines() { assertNull(BookIdentity.unambiguousIsbn("978080\n4429573")) }
    @Test fun doesNotConcatenateWords() { assertNull(BookIdentity.unambiguousIsbn("978080 Preis 4429573")) }
    @Test fun ambiguousEditionsRequireChoice() {
        assertNull(BookIdentity.unambiguousIsbn("ISBN 9780804429573\nISBN 9780140328721"))
    }
    @Test fun toleratesOcrSeparatorsInsideIsbn() {
        assertEquals("9783125171541", BookIdentity.unambiguousIsbn("ISBN 3-12.5 17154-7"))
        assertEquals("9783125171541", BookIdentity.unambiguousIsbn("ISBN 3–12–517154–7"))
    }
    @Test fun recoversEanFromBarcodeLine() {
        assertEquals("9783125171541", BookIdentity.unambiguousIsbn("9ll783125171541"))
        assertEquals("9780863815805", BookIdentity.unambiguousIsbn("g\"780863\"815805"))
        // Wrong digit (5 read as 3): the check digit fails, nothing is guessed.
        assertNull(BookIdentity.unambiguousIsbn("9783123171541"))
        // Ordinary words with a few digits are not treated as barcode lines.
        assertNull(BookIdentity.unambiguousIsbn("Bios 1978 Seite 12"))
    }
    @Test fun printedIsbnAndOtherBarcodeOnOneLineStayAmbiguous() {
        assertEquals(listOf("9780140328721", "9783125171541"), BookIdentity.isbnsInText("ISBN 9780140328721 9ll783125171541"))
        assertNull(BookIdentity.unambiguousIsbn("ISBN 9780140328721 9ll783125171541"))
        // The same edition printed and as barcode line is still one book.
        assertEquals("9783125171541", BookIdentity.unambiguousIsbn("ISBN 3-12-517154-7 9ll783125171541"))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotAnErrorResult() {
        catchingOperation { throw CancellationException("closed") }
    }
}
