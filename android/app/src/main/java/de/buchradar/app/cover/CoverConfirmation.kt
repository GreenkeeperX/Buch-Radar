package de.buchradar.app.cover

import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.network.CatalogBook

/**
 * Pure rules for cover confirmation. A cover can only confirm a candidate, never demote or hide one.
 * Threshold and limits follow the offline measurement on real cover photos: 100 RANSAC inliers
 * confirmed every matching edition and no other edition or book.
 */
object CoverConfirmation {
    const val MIN_INLIERS = 100
    const val MAX_CANDIDATES = 10

    fun confirmed(inliers: Int?): Boolean = inliers != null && inliers >= MIN_INLIERS

    /**
     * Marks [confirmedIds] and lets a confirmed candidate move ahead only of other editions of the same
     * title; candidates of other titles keep their text order and positions.
     */
    fun apply(candidates: List<CatalogBook>, confirmedIds: Set<String>): List<CatalogBook> {
        if (confirmedIds.isEmpty()) return candidates
        val marked = candidates.map { if (it.candidateId in confirmedIds) it.copy(coverConfirmed = true) else it }
        val result = marked.toMutableList()
        marked.groupBy { titleKey(it) }.values.filter { group -> group.size > 1 && group.any { it.coverConfirmed } }.forEach { group ->
            val slots = group.map { book -> marked.indexOfFirst { it.candidateId == book.candidateId } }.sorted()
            val reordered = group.filter { it.coverConfirmed } + group.filterNot { it.coverConfirmed }
            slots.zip(reordered).forEach { (slot, book) -> result[slot] = book }
        }
        return result
    }

    private fun titleKey(book: CatalogBook): String =
        BookListQuery.fold(book.title).trim()
}
