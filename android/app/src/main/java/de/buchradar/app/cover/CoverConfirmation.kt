package de.buchradar.app.cover

import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.network.CatalogBook

/**
 * Pure rules for cover confirmation. A cover can only confirm a candidate, never demote or hide one.
 * Threshold and limits follow the measurement on real cover photos (offline and on the device): 100 RANSAC inliers
 * confirmed every matching edition but once another edition with the same illustration (110); 120 confirmed none.
 */
object CoverConfirmation {
    const val MIN_INLIERS = 120
    const val MAX_CANDIDATES = 10

    fun confirmed(inliers: Int?): Boolean = inliers != null && inliers >= MIN_INLIERS

    /**
     * Marks [confirmedIds] and lets a confirmed candidate move ahead only of other editions of the same
     * work (title and author); all other candidates keep their text order and positions.
     */
    fun apply(candidates: List<CatalogBook>, confirmedIds: Set<String>): List<CatalogBook> {
        if (confirmedIds.isEmpty()) return candidates
        val marked = candidates.map { if (it.candidateId in confirmedIds) it.copy(coverConfirmed = true) else it }
        val result = marked.toMutableList()
        marked.filter { workKey(it) != null }.groupBy { workKey(it) }.values.filter { group -> group.size > 1 && group.any { it.coverConfirmed } }.forEach { group ->
            val slots = group.map { book -> marked.indexOfFirst { it.candidateId == book.candidateId } }.sorted()
            val reordered = group.filter { it.coverConfirmed } + group.filterNot { it.coverConfirmed }
            slots.zip(reordered).forEach { (slot, book) -> result[slot] = book }
        }
        return result
    }

    /**
     * Same work = same folded title and same author surname. Without an author the work is ambiguous
     * (homonymous titles), so such a candidate is only marked, never moved.
     */
    private fun workKey(book: CatalogBook): String? {
        val surname = book.author?.split(',', ';', '&')?.firstOrNull()?.let { BookListQuery.fold(it).trim().split(' ').lastOrNull() }
            ?.takeIf(String::isNotBlank) ?: return null
        return BookListQuery.fold(book.title).trim() + "|" + surname
    }
}
