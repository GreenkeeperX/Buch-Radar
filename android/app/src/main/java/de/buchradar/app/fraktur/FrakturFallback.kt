package de.buchradar.app.fraktur

import android.graphics.Rect
import de.buchradar.app.network.CatalogBook

/** Pure rules for the old-script fallback reader. */
object FrakturFallback {
    /** Below this text similarity no candidate counts as a fitting hit ("Möglicher Treffer" starts at 60). */
    const val FITTING_SIMILARITY = 60

    /**
     * Runs only for a single book photographed from the front (a cover frame exists, i.e. coverEvidenceAllowed),
     * never when the search used an ISBN, and only when no candidate fits the recognised text.
     */
    fun shouldRun(hasSingleBookFrame: Boolean, usedIsbn: Boolean, candidates: List<CatalogBook>): Boolean =
        hasSingleBookFrame && !usedIsbn && candidates.none { (it.textSimilarity ?: 0) >= FITTING_SIMILARITY }

    /** Bounding box of the words at least 60% as tall as the tallest, padded by 0.6 of that height. */
    fun titleRect(words: List<Rect>, width: Int, height: Int): Rect? {
        val usable = words.filter { it.width() > 0 && it.height() > 0 }
        if (usable.isEmpty()) return null
        val tallest = usable.maxOf { it.height() }
        val big = usable.filter { it.height() >= 0.6 * tallest }
        val pad = (tallest * 0.6).toInt()
        val rect = Rect((big.minOf { it.left } - pad).coerceAtLeast(0), (big.minOf { it.top } - pad).coerceAtLeast(0),
            (big.maxOf { it.right } + pad).coerceAtMost(width), (big.maxOf { it.bottom } + pad).coerceAtMost(height))
        return rect.takeIf { it.width() > 8 && it.height() > 8 }
    }
}
