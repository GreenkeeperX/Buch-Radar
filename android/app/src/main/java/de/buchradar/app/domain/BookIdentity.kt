package de.buchradar.app.domain

import net.creativecouple.validation.isbn.ISBN

object BookIdentity {
    fun canonicalIsbn(value: String?): String? = value?.let {
        runCatching { ISBN.valueOf(it.trim()).toCompactString() }.getOrNull()
    }

    // Up to two separators between digits: OCR often reads a hyphen as '.' or '–', or adds a
    // space next to it ("3-12.5 17154-7"). The check digit rejects accidental joins.
    private const val SEP = "[ \\t.\\-–]{0,2}"
    private val isbnPattern = Regex(
        "(?<![0-9Xx])(?:97[89]$SEP(?:[0-9]$SEP){9}[0-9]|(?:[0-9]$SEP){9}[0-9Xx])(?![0-9Xx])"
    )

    // Characters that barcode stripes and OCR confusions produce in the printed EAN line.
    private val barcodeRun = Regex("[0-9OoIl|!gSB\"'`]+(?: [0-9OoIl|!gSB\"'`]+)*")

    // Never join digits across lines, words or separate numeric fields. Ambiguous pages
    // containing several valid ISBNs require the user to choose an edition.
    fun isbnsInText(text: String): List<String> = text.take(4_000).lineSequence().flatMap { line ->
        val direct = isbnPattern.findAll(line)
            .mapNotNull { match -> canonicalIsbn(match.value.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()) }
            .toList()
        // A line can hold a printed ISBN and a barcode line of another edition; keep both, so
        // several different ISBNs stay ambiguous instead of silently picking the first.
        (direct + barcodeLineIsbns(line)).asSequence()
    }.distinct().toList()

    /**
     * Recovers an EAN-13 from the human-readable line under a barcode, e.g. `9ll783125171541`
     * (stripes read as `l`) or `g"780863"815805`. Only runs with at least nine real digits are
     * considered, and only 978/979 numbers with a valid check digit are accepted.
     */
    internal fun barcodeLineIsbns(line: String): List<String> = barcodeRun.findAll(line).flatMap { run ->
        val value = run.value
        if (value.count(Char::isDigit) < 9) return@flatMap emptySequence()
        fun mapped(stripesAsOne: Boolean) = buildString {
            for (c in value) when (c) {
                in '0'..'9' -> append(c)
                'O', 'o' -> append('0')
                'g' -> append('9')
                'S' -> append('5')
                'B' -> append('8')
                'I', 'l', '|', '!' -> if (stripesAsOne) append('1')
                else -> Unit
            }
        }
        sequenceOf(mapped(false), mapped(true)).flatMap { digits ->
            Regex("97[89][0-9]{10}").findAll(digits).mapNotNull { canonicalIsbn(it.value) }
        }
    }.distinct().toList()

    fun unambiguousIsbn(text: String): String? = isbnsInText(text).singleOrNull()
}
