package de.buchradar.app

import de.buchradar.app.network.*
import org.junit.Assert.*
import org.junit.Test

class MomoxResponseTest {
    private val isbn = "9780804429573"
    private fun offer(price: String = "5.63", currency: String = "EUR", ean: String = isbn) =
        MomoxOfferResponse("offer", price, currency, MomoxProduct(ean, "Buch", "book"))
    @Test fun exactCents() { assertEquals(563, MomoxCatalog.validateOffer(isbn, offer()).priceCents) }
    @Test fun actualLowercaseCurrencyIsAccepted() { assertEquals(563, MomoxCatalog.validateOffer(isbn, offer(currency = "eur")).priceCents) }
    @Test(expected = MomoxCheckException::class) fun wrongProductTypeRejected() {
        MomoxCatalog.validateOffer(isbn, offer().copy(product = MomoxProduct(isbn, "Medium", "dvd")))
    }
    @Test(expected = MomoxCheckException::class) fun wrongEditionRejected() { MomoxCatalog.validateOffer(isbn, offer(ean = "9780140328721")) }
    @Test(expected = MomoxCheckException::class) fun wrongCurrencyRejected() { MomoxCatalog.validateOffer(isbn, offer(currency = "USD")) }
    @Test(expected = MomoxCheckException::class) fun negativeRejected() { MomoxCatalog.validateOffer(isbn, offer(price = "-1")) }
    @Test(expected = MomoxCheckException::class) fun overflowRejected() { MomoxCatalog.validateOffer(isbn, offer(price = "99999999999999")) }
    @Test(expected = MomoxCheckException::class) fun fractionalCentsRejected() { MomoxCatalog.validateOffer(isbn, offer(price = "1.234")) }
    @Test(expected = MomoxCheckException::class) fun unknownSchemaIsNotNoOffer() { MomoxCatalog.validateOffer(isbn, offer().copy(status = "new-format")) }
    @Test fun unknownStatusIsNamedInMessage() {
        val error = assertThrows(MomoxCheckException::class.java) { MomoxCatalog.validateOffer(isbn, offer().copy(status = "max_reached")) }
        assertTrue(error.message!!.contains("„max_reached“"))
        assertTrue(error.unknownFormat)
        assertFalse(error.pauseQueue)
    }
    @Test fun missingStatusIsReported() {
        val error = assertThrows(MomoxCheckException::class.java) { MomoxCatalog.validateOffer(isbn, MomoxOfferResponse(null, null, null, null)) }
        assertTrue(error.message!!.startsWith("Momox-Antwort ohne Status"))
    }
    @Test fun oddStatusCharactersAreStripped() {
        assertEquals(MomoxCatalog.unknownFormatMessage("<b>x</b>"), MomoxCatalog.unknownFormatMessage("bxb"))
    }
    @Test fun productErrorMeansMomoxDoesNotKnowTheBook() {
        val check = MomoxCatalog.validateOffer(isbn, MomoxOfferResponse("product_error", null, null, null))
        assertEquals("UNKNOWN", check.status)
        assertNull(check.priceCents)
    }
    @Test fun knownNoOffer() { assertEquals("NOT_AVAILABLE", MomoxCatalog.validateOffer(isbn, MomoxOfferResponse("no_offer", null, null, null)).status) }
}
