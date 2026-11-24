package de.buchradar.app

import de.buchradar.app.domain.BookInputFormat
import org.junit.Assert.*
import org.junit.Test

class ScreenIsbnTest {
    @Test fun numberCannotBreak() {
        val shown = BookInputFormat.screenIsbn("9780140328721")
        assertTrue(shown.startsWith("ISBN "))
        assertFalse(shown.contains('-'))
        assertFalse(shown.contains(' '))
        assertEquals(BookInputFormat.displayIsbn("9780140328721"), shown.removePrefix("ISBN ").replace('‑', '-'))
    }
}
