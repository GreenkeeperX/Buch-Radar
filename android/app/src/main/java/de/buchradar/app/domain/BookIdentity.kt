package de.buchradar.app.domain

import net.creativecouple.validation.isbn.ISBN

object BookIdentity {
    fun canonicalIsbn(value: String?): String? = value?.let {
        runCatching { ISBN.valueOf(it.trim()).toCompactString() }.getOrNull()
    }

    // Never join digits across lines, words or separate numeric fields. Ambiguous pages
    // containing several valid ISBNs require the user to choose an edition.
    fun isbnsInText(text: String): List<String> = text.take(4_000).lineSequence().flatMap { line ->
        Regex("(?<![0-9Xx])(?:97[89][ \\t-]?(?:[0-9][ \\t-]?){9}[0-9]|(?:[0-9][ \\t-]?){9}[0-9Xx])(?![0-9Xx])")
            .findAll(line).mapNotNull { canonicalIsbn(it.value) }
    }.distinct().toList()

    fun unambiguousIsbn(text: String): String? = isbnsInText(text).singleOrNull()
}
