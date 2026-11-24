package de.buchradar.app.domain

import de.buchradar.app.data.BookEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Preparing a sale: the books Momox bought at their last check, their sum and a shareable list.
 * Prices are the last checked offers, not a binding quote; the app never sells by itself.
 */
object SaleList {
    /** An offer older than this should be checked again before selling. */
    const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
    /** Minimum purchase value named in the settings; the conditions at sale time decide. */
    const val MINIMUM_CENTS = 1_000

    /** Active books with a valid offer, highest price first. */
    fun candidates(books: List<BookEntity>): List<BookEntity> = books
        .filter { it.soldAt == null && it.isbn.isNotBlank() && BookListQuery.offeredCents(it) != null }
        .sortedWith(compareByDescending<BookEntity> { it.currentPriceCents ?: 0 }.thenBy { it.title.orEmpty().lowercase(Locale.GERMAN) })

    fun totalCents(books: List<BookEntity>): Long = books.sumOf { (BookListQuery.offeredCents(it) ?: 0).toLong() }

    /** The offer is old or the latest attempt failed, so the shown price may no longer hold. */
    fun needsRecheck(book: BookEntity, now: Long): Boolean =
        book.lastCheckedAt == null || now - book.lastCheckedAt >= STALE_AFTER_MILLIS || book.lastAttemptStatus == "ERROR"

    fun shareText(books: List<BookEntity>, now: Long): String {
        val euro = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        val day = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val total = euro.format(totalCents(books) / 100.0)
        val count = if (books.size == 1) "1 Buch" else "${books.size} Bücher"
        val oldest = books.mapNotNull { it.lastCheckedAt }.minOrNull()
        return buildString {
            appendLine("Bücher für Momox: $count, zusammen $total")
            books.forEach { book ->
                append("• ").append(book.title?.takeIf(String::isNotBlank) ?: "Ohne Titel")
                book.author?.takeIf(String::isNotBlank)?.let { append(" – ").append(it) }
                append(" – ISBN ").append(BookInputFormat.displayIsbn(book.isbn))
                append(" – ").appendLine(euro.format((BookListQuery.offeredCents(book) ?: 0) / 100.0))
            }
            append("Preise laut letzter Prüfung")
            oldest?.let { append(" (älteste vom ").append(day.format(Date(it))).append(")") }
            append(". Maßgeblich ist das Angebot beim Verkauf.")
            if (books.any { it.lastCheckedAt != null && now - it.lastCheckedAt >= STALE_AFTER_MILLIS }) append(" Einige Preise sind älter als ein Tag.")
            if (books.any { it.lastCheckedAt == null }) append(" Einige Preise haben kein Prüfdatum.")
            if (books.any { it.lastAttemptStatus == "ERROR" }) append(" Bei einigen ist die letzte Prüfung fehlgeschlagen.")
        }
    }
}
