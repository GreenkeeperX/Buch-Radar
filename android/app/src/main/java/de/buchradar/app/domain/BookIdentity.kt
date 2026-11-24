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
    // One character class including the space: a repeated group here recursed per repetition in
    // java.util.regex and overflowed the stack on long stripe noise.
    private val barcodeRun = Regex("[0-9OoIl|!gSB\"'` ]+")

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
     * (stripes read as `l`) or `g"780863"815805`. A candidate is one or more whole, adjacent
     * space-separated groups (`9 780863 815805`) that map to exactly 13 digits; a valid number
     * inside a longer identifier (`197801403287219`) is never cut out. Only runs with at least
     * nine real digits are considered, and only 978/979 numbers with a valid check digit count.
     */
    internal fun barcodeLineIsbns(line: String): List<String> = barcodeRun.findAll(line).flatMap { run ->
        if (run.value.count(Char::isDigit) < 9) return@flatMap emptySequence()
        val groups = run.value.split(' ').filter(String::isNotEmpty)
        fun mapped(group: String, stripesAsOne: Boolean) = buildString {
            for (c in group) when (c) {
                in '0'..'9' -> append(c)
                'O', 'o' -> append('0')
                'g' -> append('9')
                'S' -> append('5')
                'B' -> append('8')
                'I', 'l', '|', '!' -> if (stripesAsOne) append('1')
                else -> Unit
            }
        }
        sequenceOf(false, true).flatMap { stripesAsOne ->
            // Groups without digits (pure stripe noise) are dropped first, so every window step adds
            // at least one digit and a window never grows past 13: linear in the line length.
            val digits = groups.map { mapped(it, stripesAsOne) }.filter(String::isNotEmpty)
            val found = mutableListOf<String>()
            for (start in digits.indices) {
                val window = StringBuilder()
                for (end in start until digits.size) {
                    window.append(digits[end])
                    if (window.length > 13) break
                    if (window.length == 13 && (window.startsWith("978") || window.startsWith("979"))) {
                        canonicalIsbn(window.toString())?.let(found::add)
                        break
                    }
                }
            }
            found.asSequence()
        }
    }.distinct().toList()

    fun unambiguousIsbn(text: String): String? = isbnsInText(text).singleOrNull()
}
