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
    fun reset() { anchor = ""; previous = ""; since = 0; count = 0 }
    fun observe(text: String, now: Long, imageSteady: Boolean, readable: Boolean): Boolean {
        val key = foldOcr(text)
        if (key.isBlank()) {
            if (now - lastSeen > 1_500) reset()
            return false
        }
        lastSeen = now
        if (!imageSteady || !readable || similarity(anchor, key) < .90f || similarity(previous, key) < .90f) {
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
    return ordered.map { it.text }.distinct().joinToString("\n")
}
