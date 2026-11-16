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
    @Test(expected = CancellationException::class) fun cancellationIsNotAnErrorResult() {
        catchingOperation { throw CancellationException("closed") }
    }
}
