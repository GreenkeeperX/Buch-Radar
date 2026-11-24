package de.buchradar.app.scanner

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal fun foldOcr(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Compare with the first accepted sample as well as the previous one to prevent drift. */
internal class RecognitionStability {
    private var anchor = ""
    private var previous = ""
    private var since = 0L
    private var count = 0
    private var lastSeen = 0L
    /** Word overlap needed between frames; shelves with many spines use a lower value. */
    var minSimilarity = .90f
    fun reset() { anchor = ""; previous = ""; since = 0; count = 0 }
    fun observe(text: String, now: Long, imageSteady: Boolean, readable: Boolean): Boolean {
        val key = foldOcr(text)
        if (key.isBlank()) {
            if (now - lastSeen > 1_500) reset()
            return false
        }
        lastSeen = now
        if (!imageSteady || !readable || similarity(anchor, key) < minSimilarity || similarity(previous, key) < minSimilarity) {
            anchor = key; count = 1; since = now
        } else count++
        previous = key
        return readable && imageSteady && count >= 5 && now - since >= 2_200
    }
    private fun similarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        val aa = a.split(' ').toSet(); val bb = b.split(' ').toSet()
        return aa.intersect(bb).size.toFloat() / aa.union(bb).size
    }
}

internal data class OcrPiece(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val angle: Float) {
    val x get() = (left + right) / 2
    val y get() = (top + bottom) / 2
    val width get() = (right - left).coerceAtLeast(1f)
    val height get() = (bottom - top).coerceAtLeast(1f)
}

/** Spatial groups retain duplicate text on different spines. No global text deduplication. */
internal fun groupSpines(pieces: List<OcrPiece>, imageWidth: Int, imageHeight: Int): List<List<OcrPiece>> {
    fun compatible(a: OcrPiece, b: OcrPiece): Boolean {
        val overlap = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) / minOf(a.width, b.width)
        val gapY = maxOf(0f, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
        if (gapY > imageHeight * .35f) return false
        val vertical = abs(a.angle % 180) in 55f..125f || abs(b.angle % 180) in 55f..125f
        val aligned = overlap >= if (vertical) .65f else .55f
        val centered = abs(a.x - b.x) <= minOf(a.width, b.width) * .25f
        // Do not join horizontally adjacent words across unknown book boundaries.
        return aligned || centered && abs(a.x - b.x) < imageWidth * .025f
    }
    val groups = mutableListOf<MutableList<OcrPiece>>()
    for (piece in pieces.sortedWith(compareBy<OcrPiece> { it.x }.thenBy { it.y })) {
        val group = groups.firstOrNull { it.all { other -> compatible(piece, other) } }
        if (group == null) groups += mutableListOf(piece) else group += piece
    }
    return groups.sortedBy { it.map(OcrPiece::x).average() }
}

internal fun spineText(pieces: List<OcrPiece>): String {
    val angle = pieces.map { it.angle }.average()
    val ordered = if (angle < -55) pieces.sortedByDescending { it.y } else pieces.sortedBy { it.y }
    return cleanSpineText(ordered.map { it.text }.distinct().joinToString("\n"))
}

/**
 * Removes OCR stutter on one spine: the same word read twice (0°/90°/270° passes, spaced
 * capitals), near-identical variants ("Transhimalaa Transhimalaja") and lines that repeat
 * text already contained in another line. Only applied within a single spine's text.
 */
internal fun cleanSpineText(text: String): String {
    fun compact(value: String) = foldOcr(value).replace(" ", "")
    fun bigrams(value: String) = value.windowed(2).toSet()
    fun similar(a: String, b: String): Boolean {
        if (a.length < 5 || b.length < 5) return false
        val x = bigrams(a); val y = bigrams(b)
        return 2f * x.intersect(y).size / (x.size + y.size) >= .8f
    }
    val lines = text.lines().map { line ->
        val kept = mutableListOf<String>()
        for (word in line.split(' ').filter(String::isNotBlank)) {
            val previous = kept.lastOrNull()
            if (previous != null) {
                val a = compact(previous); val b = compact(word)
                if (a.isNotEmpty() && a == b) continue
                // Numbers (ISBNs, volumes) differing in one digit are different readings, not stutter.
                if (a.none(Char::isDigit) && b.none(Char::isDigit) && similar(a, b)) { if (b.length > a.length) kept[kept.lastIndex] = word; continue }
            }
            kept += word
        }
        kept.joinToString(" ")
    }.filter { compact(it).isNotEmpty() }
    fun words(value: String) = foldOcr(value).split(' ').filter(String::isNotBlank)
    val result = lines.filterIndexed { index, line ->
        val key = compact(line)
        val lineWords = words(line)
        lines.withIndex().none { (other, candidate) ->
            if (other == index) return@none false
            val otherKey = compact(candidate)
            val otherWords = words(candidate)
            // Repeated whole words ("Hedin" in "Sven Hedin"), not substrings ("IT" in "Illustrated").
            val contained = lineWords.size < otherWords.size && otherWords.windowed(lineWords.size).any { it == lineWords }
            // Lines with digits (volume numbers, years) are never treated as redundant.
            val redundant = key.none(Char::isDigit) && otherKey.length > key.length && (contained || similar(key, otherKey))
            // Identical lines: keep only the first occurrence.
            redundant || otherKey == key && other < index
        }
    }
    return result.joinToString("\n")
}

/** Spine words that name a publisher or place, never a book on their own. */
private val publisherWords = setOf(
    "hanser", "dtv", "suhrkamp", "fischer", "rororo", "rowohlt", "reclam", "diogenes", "piper", "goldmann",
    "heyne", "insel", "ullstein", "kiepenheuer", "witsch", "btb", "aufbau", "brockhaus", "leipzig", "berlin",
    "munchen", "verlag", "taschenbuch", "penguin", "vintage", "oreilly", "beck", "kosmos", "dumont"
)

/**
 * A spine group is worth a catalog search only with at least one word of four or more letters
 * that is not just a publisher or place ("Hanser", "dtv !" alone are not books).
 */
internal fun isUsefulSpineText(text: String): Boolean =
    foldOcr(text).split(' ').any { word -> word.count(Char::isLetter) >= 4 && word !in publisherWords } ||
        de.buchradar.app.domain.BookIdentity.isbnsInText(text).isNotEmpty()
