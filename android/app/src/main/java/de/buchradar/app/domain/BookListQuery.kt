package de.buchradar.app.domain

import de.buchradar.app.data.BookEntity
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import net.creativecouple.validation.isbn.ISBN

/** Pure list logic, kept out of Compose so it can be tested on the JVM. */
object BookListQuery {
    const val FILTER_ALL = "all"
    const val FILTER_AVAILABLE = "available"
    const val FILTER_UNAVAILABLE = "unavailable"
    const val FILTER_UNCHECKED = "unchecked"
    const val FILTER_ERROR = "error"

    const val SORT_RECENT = "recent"
    const val SORT_TITLE_UP = "title_up"
    const val SORT_TITLE_DOWN = "title_down"
    const val SORT_PRICE_DOWN = "price_down"
    const val SORT_PRICE_UP = "price_up"

    val filters = listOf(
        FILTER_ALL to "Alle", FILTER_AVAILABLE to "Ankauf", FILTER_UNAVAILABLE to "Kein Ankauf",
        FILTER_UNCHECKED to "Ungeprüft", FILTER_ERROR to "Fehler"
    )
    val sorts = listOf(
        SORT_RECENT to "Neueste", SORT_TITLE_UP to "Titel A–Z", SORT_TITLE_DOWN to "Titel Z–A",
        SORT_PRICE_DOWN to "Preis absteigend", SORT_PRICE_UP to "Preis aufsteigend"
    )

    fun filterLabel(key: String) = filters.firstOrNull { it.first == key }?.second ?: "Alle"
    fun sortLabel(key: String) = sorts.firstOrNull { it.first == key }?.second ?: "Neueste"

    /** Case-, accent- and punctuation-insensitive form; "Müller" matches "muller". */
    fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("ß", "ss")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    fun matchesStatus(book: BookEntity, filter: String): Boolean = when (filter) {
        FILTER_AVAILABLE -> book.momoxStatus == "AVAILABLE"
        FILTER_UNAVAILABLE -> book.momoxStatus == "NOT_AVAILABLE"
        FILTER_UNCHECKED -> book.isbn.isNotBlank() && book.lastCheckedAt == null
        FILTER_ERROR -> book.lastAttemptStatus == "ERROR" || book.momoxStatus == "ERROR"
        else -> true
    }

    /** Price that is currently offered; other states never count as a price. */
    fun offeredCents(book: BookEntity): Int? = book.currentPriceCents?.takeIf { book.momoxStatus == "AVAILABLE" }

    fun apply(books: List<BookEntity>, query: String, filter: String, sort: String, soldView: Boolean): List<BookEntity> {
        val terms = fold(query).split(' ').filter(String::isNotBlank)
        // A purely numeric query (e.g. a typed ISBN with hyphens) may match the compact stored ISBN.
        val isbnLikeQuery = query.isNotBlank() && query.all { it.isDigit() || it == 'x' || it == 'X' || it == '-' || it.isWhitespace() }
        val filtered = books.filter { book ->
            val haystack = fold("${book.title.orEmpty()} ${book.author.orEmpty()} ${book.isbn} ${book.publicationYear?.toString().orEmpty()}")
            // Also allow searching a formatted ISBN ("978-3-…") against the compact stored value.
            val compactQuery = query.filter { it.isDigit() || it == 'x' || it == 'X' }
            val isbnHit = isbnLikeQuery && compactQuery.length >= 4 && book.isbn.contains(compactQuery, ignoreCase = true)
            (terms.all(haystack::contains) || isbnHit) && matchesStatus(book, filter)
        }
        val collator = Collator.getInstance(Locale.GERMANY).apply { strength = Collator.SECONDARY }
        val byTitle = Comparator<BookEntity> { a, b -> collator.compare(a.title.orEmpty(), b.title.orEmpty()) }
        val untitledLast = compareBy<BookEntity> { it.title.isNullOrBlank() }
        val unpricedLast = compareBy<BookEntity> { offeredCents(it) == null }
        return when (sort) {
            SORT_TITLE_UP -> filtered.sortedWith(untitledLast.then(byTitle))
            SORT_TITLE_DOWN -> filtered.sortedWith(untitledLast.then(byTitle.reversed()))
            SORT_PRICE_DOWN -> filtered.sortedWith(unpricedLast.thenByDescending { offeredCents(it) ?: 0 }.then(byTitle))
            SORT_PRICE_UP -> filtered.sortedWith(unpricedLast.thenBy { offeredCents(it) ?: 0 }.then(byTitle))
            else -> if (soldView) filtered.sortedByDescending { it.soldAt ?: it.createdAt } else filtered.sortedByDescending { it.createdAt }
        }
    }

    fun totals(books: List<BookEntity>): BookListTotals = BookListTotals(
        count = books.size,
        availableCount = books.count { offeredCents(it) != null },
        availableValueCents = books.sumOf { (offeredCents(it) ?: 0).toLong() },
        uncheckedCount = books.count { it.isbn.isNotBlank() && it.lastCheckedAt == null },
        errorCount = books.count { matchesStatus(it, FILTER_ERROR) }
    )
}

data class BookListTotals(
    val count: Int,
    val availableCount: Int,
    val availableValueCents: Long,
    val uncheckedCount: Int,
    val errorCount: Int
)

/** Formatting of typed or scanned ISBN input. */
object BookInputFormat {
    /** Accepts ISBN-10/13 with or without hyphens and spaces. */
    fun parseIsbn(value: String): ISBN? {
        // Multi-line text (OCR) goes through BookIdentity.isbnsInText, which never joins lines.
        if (value.trim().any { it == '\n' || it == '\r' }) return null
        val compact = value.trim().filterNot { it == '-' || it.isWhitespace() }
        if (compact.isEmpty()) return null
        return catchingOperation { ISBN.valueOf(compact) }.getOrNull()
    }

    /** Hyphenated display form of a complete ISBN; other input is returned unchanged. */
    fun autoFormatIsbn(value: String): String = parseIsbn(value)?.toString() ?: value

    // Typed input is deliberately never reformatted while editing: a 10-digit prefix of an
    // ISBN-13 can itself be a valid ISBN-10, and rewriting it moved the cursor and mangled
    // the remaining digits. Input is normalised only when it is submitted.

    fun displayIsbn(value: String): String = parseIsbn(value)?.toString() ?: value

    /** On screen: "ISBN" plus the number joined by non-breaking hyphens, so large fonts never split the number. */
    fun screenIsbn(value: String): String = "ISBN\u00A0" + displayIsbn(value).replace('-', '\u2011')
}

enum class MessageTone { INFO, SUCCESS, ERROR, PROGRESS }

/** Visual tone for status messages, so errors stand out from confirmations. */
fun messageTone(text: String): MessageTone {
    val lower = text.lowercase(Locale.GERMANY)
    return when {
        // Fixed prefixes first: they are followed by catalog text (titles) that must not decide the tone.
        text.startsWith("Hinzugefügt:") -> if (text.contains('\n')) MessageTone.INFO else MessageTone.SUCCESS
        text.trimEnd().endsWith("…") -> MessageTone.PROGRESS
        listOf("fehlgeschlagen", "nicht erreichbar", "nicht gespeichert", "konnte nicht", "fehler", "ungültig", "kein gültig", "benötigt").any(lower::contains) -> MessageTone.ERROR
        listOf("nicht gefunden", "bereits", "bitte", "prüfen").any(lower::contains) -> MessageTone.INFO
        listOf("hinzugefügt", "gespeichert", "aktualisiert", "entfernt", "zurückgeholt", "zurück in", "markiert", "ergänzt", "gesichert").any(lower::contains) -> MessageTone.SUCCESS
        else -> MessageTone.INFO
    }
}
