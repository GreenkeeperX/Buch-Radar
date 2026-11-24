package de.buchradar.app.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.tan

// Tuned on public and private shelf photos (see tools/recognition): a step of 18 colour levels in
// at least ~42 % of the sampled rows marks a book edge.
private const val EDGE_STEP = 18f
private const val EDGE_SCALE = 60f
private const val EDGE_MIN_STRENGTH = 25f
private const val EDGE_REFERENCE_Y = .55f // Report all x positions at the same height on the shelf.
// Keep the slope penalty: a 1:4 lean needs a colour step in 94% of sampled rows to qualify.
// Sample steeper leans a pixel apart per 100 rows to keep a thin edge in the sampling window.
private val EDGE_SLOPES = floatArrayOf(-.12f, -.06f, 0f, .06f, .12f) +
    (13..25).flatMap { listOf(-it / 100f, it / 100f) }.toFloatArray()

internal data class SpineEdge(val x: Float, val slope: Float, val strength: Float) {
    fun at(y: Float, height: Int) = x + slope * (y - height * EDGE_REFERENCE_Y)
}

/** Long, consistently directed colour transitions; short glyph edges should not split books. */
internal fun spineEdges(pixels: IntArray, width: Int, height: Int, bandTop: Float = .23f, bandBottom: Float = .88f): List<SpineEdge> {
    if (width < 32 || height < 32 || pixels.size != width * height) return emptyList()
    val candidates = mutableListOf<SpineEdge>()
    val rows = 48
    val sampleY = IntArray(rows) { row ->
        (height * (bandTop + (bandBottom - bandTop) * row / (rows - 1))).toInt().coerceIn(0, height - 1)
    }
    val delta = (width / 640).coerceAtLeast(1)
    // All slope probes reuse the same row samples.
    val steps = Array(rows) { row ->
        val offset = sampleY[row] * width
        BooleanArray(width).also { step ->
            for (x in delta until width - delta) {
                val a = pixels[offset + x - delta]
                val b = pixels[offset + x + delta]
                val change = abs((b shr 16 and 255) - (a shr 16 and 255)) +
                    abs((b shr 8 and 255) - (a shr 8 and 255)) + abs((b and 255) - (a and 255))
                step[x] = change > 3 * EDGE_STEP
            }
        }
    }
    for (x in delta until width - delta) {
        var best = SpineEdge(x.toFloat(), 0f, 0f)
        for (slope in EDGE_SLOPES) {
            var count = 0
            var hits = 0
            for (row in sampleY.indices) {
                val xx = (x + slope * (sampleY[row] - height * EDGE_REFERENCE_Y)).toInt()
                if (xx - delta < 0 || xx + delta >= width) continue
                if (steps[row][xx]) hits++
                count++
            }
            if (count < rows * .8f) continue
            // Share of sampled rows with a clear colour step. A signed median failed on real shelves:
            // neighbouring spines of similar darkness, or one light/dark change along the spine, cancel
            // out, and rows above or below the books dilute it. A book edge is a step in most rows,
            // whatever its direction, while glyph strokes only hit a few rows.
            val strength = hits * EDGE_SCALE / count / (1 + abs(slope) * 5)
            if (strength > best.strength) best = SpineEdge(x.toFloat(), slope, strength)
        }
        if (best.strength >= EDGE_MIN_STRENGTH) candidates += best
    }
    val selected = mutableListOf<SpineEdge>()
    for (edge in candidates.sortedByDescending { it.strength }) {
        if (selected.all { other -> abs(other.x - edge.x) > width * .018f }) selected += edge
        if (selected.size >= 40) break
    }
    return selected.sortedBy { it.x }
}

/**
 * Vertical range to sample for book edges. Edges must run through the whole sampled range, so
 * a fixed 23–88 % of the photo failed whenever the books filled only part of it (shelf board,
 * wall or a second row in view). The range now follows the vertical spine lettering.
 */
internal fun spineBand(pieces: List<OcrPiece>, imageHeight: Int): Pair<Float, Float> {
    val vertical = pieces.filter { it.isVertical }
    if (vertical.size < 2 || imageHeight <= 0) return .23f to .88f
    val top = vertical.minOf { it.top } / imageHeight
    val bottom = vertical.maxOf { it.bottom } / imageHeight
    val pad = .05f
    val band = (top - pad).coerceAtLeast(.02f) to (bottom + pad).coerceAtMost(.98f)
    return if (band.second - band.first >= .2f) band else .23f to .88f
}

// Ignore box margins when vetoing a measured colour edge. ML Kit boxes include blank pixels.
private const val LINE_INSET = .2f
private const val NARROW_OCR_WIDTH = .05f // Short glyphs in the original narrow-box guard can still veto an edge.
// Two characters distinguish a text column from isolated marks and volume numerals.
private const val MIN_COLUMN_CHARS = 2
// Existing atlas crop limits: reject slivers and avoid reading a whole shelf as one spine.
private const val MIN_REGION_WIDTH = .012f
private const val MAX_UNDIVIDED_WIDTH = .25f
private const val MIN_CROP_PIXELS = 8

internal data class SpineRegion(val left: SpineEdge, val right: SpineEdge, val firstPassOnly: Boolean = false) {
    val width get() = right.x - left.x

    fun contains(piece: OcrPiece, height: Int) =
        piece.left >= left.at(piece.y, height) && piece.right <= right.at(piece.y, height)

    fun canCrop(imageWidth: Int, height: Int) = width >= imageWidth * MIN_REGION_WIDTH &&
        right.at(0f, height) - left.at(0f, height) >= MIN_CROP_PIXELS &&
        right.at(height.toFloat(), height) - left.at(height.toFloat(), height) >= MIN_CROP_PIXELS

    fun sourceCorners(height: Int): FloatArray {
        val bottom = height.toFloat()
        return floatArrayOf(left.at(0f, height), 0f, right.at(0f, height), 0f,
            right.at(bottom, height), bottom, left.at(bottom, height), bottom)
    }
}

/** Midline thickness excludes the sideways travel of a leaning line inside its axis-aligned box. */
private fun OcrPiece.coreHalfWidth(): Float {
    val drift = abs(tan(Math.toRadians((90.0 - angle) % 180))).toFloat() * height
    return (width - drift).coerceAtLeast(0f) * (.5f - LINE_INSET)
}

private fun SpineRegion.splitAtColumns(lines: List<OcrPiece>, imageWidth: Int, height: Int): List<SpineRegion> {
    // Work in fractions of the crop. A constant fraction maps to a straight line even when
    // the two image edges lean differently. Project the entire box so a cut cannot clip it.
    fun fraction(x: Float, y: Float): Float {
        val start = left.at(y, height)
        return (x - start) / (right.at(y, height) - start)
    }
    val spans = lines.mapNotNull { line ->
        if (!contains(line, height)) return@mapNotNull null
        val start = minOf(fraction(line.left, line.top), fraction(line.left, line.bottom))
        val end = maxOf(fraction(line.right, line.top), fraction(line.right, line.bottom))
        start.coerceAtLeast(0f)..end.coerceAtMost(1f)
    }.sortedBy { it.start }
    if (spans.size < 2) return listOf(this)
    val typicalWidth = spans.map { it.endInclusive - it.start }.sorted()[spans.size / 2]
    val parts = mutableListOf<SpineRegion>()
    var previous = left
    var end = spans.first().endInclusive
    for (span in spans.drop(1)) {
        // The whole gutter must exceed a typical line width; nearby author/title lines often
        // belong to one book. Overlapping boxes from different OCR rotations occupy one span.
        if (span.start - end > typicalWidth) {
            val middle = (end + span.start) / 2
            val cut = SpineEdge(left.x + width * middle, left.slope + (right.slope - left.slope) * middle, 0f)
            val part = SpineRegion(previous, cut)
            if (part.canCrop(imageWidth, height) && SpineRegion(cut, right).canCrop(imageWidth, height)) {
                parts += part
                previous = cut
            }
        }
        end = maxOf(end, span.endInclusive)
    }
    return parts + SpineRegion(previous, right)
}

/** Produce the exact crops used for OCR, including boundaries supported only by lettering. */
internal fun spineRegions(edges: List<SpineEdge>, pieces: List<OcrPiece>, width: Int, height: Int): List<SpineRegion> {
    val vertical = pieces.filter { it.isVertical }
    val lines = vertical.filter { it.height > it.width && it.text.count(Char::isLetterOrDigit) >= MIN_COLUMN_CHARS }
    val cores = vertical.filter { it.height > it.width || it.width < width * NARROW_OCR_WIDTH }.map { it to it.coreHalfWidth() }
    val boundaries = edges.filter { edge ->
        cores.none { (line, halfWidth) -> abs(edge.at(line.y, height) - line.x) < halfWidth }
    }
    val borders = listOf(SpineEdge(0f, 0f, 0f)) + boundaries + SpineEdge(width.toFloat(), 0f, 0f)
    val regions = mutableListOf<SpineRegion>()
    var splitByText = false
    for ((left, right) in borders.zipWithNext()) {
        val region = SpineRegion(left, right)
        if (!region.canCrop(width, height)) continue
        val parts = region.splitAtColumns(lines, width, height)
        if (parts.size > 1) splitByText = true
        if (parts.size == 1 && region.width > width * MAX_UNDIVIDED_WIDTH) {
            regions += region.copy(firstPassOnly = true)
        } else {
            regions += parts
        }
    }
    // Keep the original fallback for weak colour-only evidence. Independent columns can
    // identify even two like-coloured books on an otherwise featureless shelf.
    return if (splitByText || edges.size >= 4 && regions.count { !it.firstPassOnly } >= 3) regions else emptyList()
}

internal data class ShelfRegionText(val text: String, val left: Float, val right: Float)
internal data class ShelfImageResult(val readings: List<ShelfRegionText>, val regions: Int, val exhausted: Boolean) {
    val texts get() = readings.map { it.text }.filter(::readableSpine)
}

/** Enough letters for a title search, or a valid ISBN (a spine or label with only the number). */
internal fun readableSpine(text: String): Boolean =
    text.count(Char::isLetter) >= 4 || de.buchradar.app.domain.BookIdentity.isbnsInText(text).isNotEmpty()

// Only repair narrow neighbours: each half of a split spine on the reference shelf was below 4%.
private const val MAX_FRAGMENT_WIDTH = .04f

private fun rejoinLine(leftText: String, rightText: String, witness: String): String? {
    fun compact(text: String) = foldOcr(text).replace(" ", "")
    val whole = compact(witness)
    val leftLines = leftText.lines()
    val rightLines = rightText.lines()
    for ((i, line) in leftLines.withIndex()) {
        val prefix = compact(line)
        if (prefix.length !in MIN_COLUMN_CHARS until whole.length || !whole.startsWith(prefix)) continue
        for ((j, other) in rightLines.withIndex()) {
            val suffix = compact(other)
            if (suffix.length !in MIN_COLUMN_CHARS until whole.length || !whole.endsWith(suffix)) continue
            // Crop padding can repeat letters at the seam ("BORGE" + "ES"). Both sides must
            // contribute different text, and together account for the entire first-pass line.
            if (prefix == suffix || prefix.length + suffix.length < whole.length) continue
            val repaired = leftLines.toMutableList()
            repaired[i] = witness
            repaired += rightLines.filterIndexed { index, _ -> index != j }
            return cleanSpineText(repaired.joinToString("\n"))
        }
    }
    return null
}

internal fun shelfReadings(regions: List<SpineRegion>, readings: Map<SpineRegion, String>, pieces: List<OcrPiece>,
    width: Int, height: Int): List<ShelfRegionText> {
    val result = mutableListOf<ShelfRegionText>()
    var index = 0
    while (index < regions.size) {
        val region = regions[index]
        val owned = pieces.filter { region.contains(it, height) }
        if (region.firstPassOnly) {
            // A wide area without a supported cut can contain several books. Preserve its
            // separate first-pass groups when the atlas can read the rest of the shelf.
            for (group in groupSpines(owned, width, height)) {
                result += ShelfRegionText(spineText(group), group.minOf { it.left } / width, group.maxOf { it.right } / width)
            }
            index++
            continue
        }
        val text = readings[region].orEmpty()
        val next = regions.getOrNull(index + 1)
        val nextText = readings[next].orEmpty()
        if (next != null && region.right == next.left && region.right.strength > 0f &&
            maxOf(region.width, next.width) <= width * MAX_FRAGMENT_WIDTH && text.isNotBlank() && nextText.isNotBlank()) {
            val joined = SpineRegion(region.left, next.right)
            val repaired = pieces.firstNotNullOfOrNull { p ->
                val seam = region.right.at(p.y, height)
                // A horizontal multi-word OCR line can already span several books.
                if (seam > p.left && seam < p.right && joined.contains(p, height) &&
                    (p.isVertical || p.text.none(Char::isWhitespace))) rejoinLine(text, nextText, p.text) else null
            }
            if (repaired != null) {
                result += ShelfRegionText(repaired, joined.left.x / width, joined.right.x / width)
                index += 2
                continue
            }
        }
        val best = cleanSpineText(text).takeIf(::readableSpine) ?: spineText(owned)
        result += ShelfRegionText(best, region.left.x / width, region.right.x / width)
        index++
    }
    return result
}

/** Read separated regions in one padded atlas; OCR lines may not change word ownership. */
internal suspend fun readShelfRegions(bitmap: Bitmap, recognizer: TextRecognizer,
    budgetMillis: Long = 15_000, protectedPieces: List<OcrPiece> = emptyList()): ShelfImageResult? {
    coroutineContext.ensureActive()
    val sampleWidth = minOf(640, bitmap.width)
    val sampleHeight = (bitmap.height.toFloat() * sampleWidth / bitmap.width).toInt().coerceAtLeast(1)
    val pixels = IntArray(sampleWidth * sampleHeight)
    val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
    try {
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    } finally { if (sample !== bitmap) sample.recycle() }
    val scale = bitmap.width.toFloat() / sampleWidth
    val (bandTop, bandBottom) = spineBand(protectedPieces, bitmap.height)
    val edges = spineEdges(pixels, sampleWidth, sampleHeight, bandTop, bandBottom).map {
        it.copy(x = it.x * scale, slope = it.slope * scale * sampleHeight / bitmap.height)
    }
    val regions = spineRegions(edges, protectedPieces, bitmap.width, bitmap.height)
    coroutineContext.ensureActive()
    if (regions.isEmpty()) return null
    val deadline = android.os.SystemClock.elapsedRealtime() + budgetMillis
    val readings = mutableMapOf<SpineRegion, String>()
    var exhausted = false
    for (batch in regions.filterNot { it.firstPassOnly }.chunked(4)) {
        coroutineContext.ensureActive()
        if (android.os.SystemClock.elapsedRealtime() >= deadline) { exhausted = true; break }
        val result = readSpineBatch(bitmap, recognizer, batch, deadline)
        batch.zip(result.texts).toMap(readings)
        if (result.exhausted) { exhausted = true; break }
    }
    val supplemented = shelfReadings(regions, readings, protectedPieces, bitmap.width, bitmap.height)
    return ShelfImageResult(supplemented, supplemented.size, exhausted)
}

private data class AtlasReadings(val texts: List<String>, val exhausted: Boolean)

private suspend fun readSpineBatch(bitmap: Bitmap, recognizer: TextRecognizer,
    regions: List<SpineRegion>, deadline: Long): AtlasReadings {
    val padding = 32
    val longSide = bitmap.height.coerceAtMost(1280)
    // Detected edges often sit a few pixels inside the neighbouring spine, so a tight crop cut off
    // the first or last letter of each line ("eon Uris"). Read each spine with a small margin; the
    // reading still belongs to the original region.
    val drawn = regions.map { (left, right) ->
        val margin = ((right.x - left.x) * .12f).coerceAtLeast(bitmap.width * .004f)
        SpineRegion(left.copy(x = left.x - margin), right.copy(x = right.x + margin))
    }
    val cellWidth = (drawn.maxOf { it.width } * longSide / bitmap.height).toInt().coerceAtLeast(32) + padding * 2
    val cellHeight = longSide + padding * 2
    val columns = kotlin.math.ceil(kotlin.math.sqrt(regions.size.toDouble() * cellHeight / cellWidth)).toInt().coerceIn(1, 12)
    val rows = (regions.size + columns - 1) / columns
    val downscale = minOf(1f, kotlin.math.sqrt(4_000_000f / (columns.toFloat() * cellWidth * rows * cellHeight)))
    data class Cell(val x: Float, val y: Float, val width: Float, val height: Float)
    val cells = drawn.mapIndexed { index, (left, right) ->
        Cell(((index % columns) * cellWidth + padding) * downscale,
            ((index / columns) * cellHeight + padding) * downscale,
            ((right.x - left.x) * longSide / bitmap.height).coerceAtLeast(32f) * downscale, longSide * downscale)
    }
    val bestTexts = MutableList(regions.size) { "" }
    val bestScores = FloatArray(regions.size)
    var exhausted = false
    val atlas = Bitmap.createBitmap((columns * cellWidth * downscale).toInt().coerceAtLeast(32),
        (rows * cellHeight * downscale).toInt().coerceAtLeast(32), Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(atlas)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        for (index in regions.indices) {
            val cell = cells[index]
            val source = drawn[index].sourceCorners(bitmap.height)
            val target = floatArrayOf(cell.x, cell.y, cell.x + cell.width, cell.y,
                cell.x + cell.width, cell.y + cell.height, cell.x, cell.y + cell.height)
            val transform = Matrix()
            if (!transform.setPolyToPoly(source, 0, target, 0, 4)) continue
            canvas.save()
            canvas.clipRect(cell.x, cell.y, cell.x + cell.width, cell.y + cell.height)
            canvas.drawBitmap(bitmap, transform, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }
        for (rotation in listOf(0, 90, 270)) {
            coroutineContext.ensureActive()
            if (android.os.SystemClock.elapsedRealtime() >= deadline) { exhausted = true; break }
            val result = recognizer.process(InputImage.fromBitmap(atlas, rotation)).awaitCompletion()
            coroutineContext.ensureActive()
            val lines = MutableList(regions.size) { mutableListOf<String>() }
            val scores = FloatArray(regions.size)
            for (line in result.textBlocks.flatMap { it.lines }) {
                val words = mutableMapOf<Int, MutableList<String>>()
                for (word in line.elements) {
                    val box = word.boundingBox ?: continue
                    val raw = OcrPiece(word.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), line.angle)
                    val piece = if (rotation == 0) raw else unrotatePiece(raw, rotation, atlas.width, atlas.height)
                    val cellIndex = cells.indexOfFirst { c -> piece.x >= c.x && piece.x <= c.x + c.width && piece.y >= c.y && piece.y <= c.y + c.height }
                    if (cellIndex < 0) continue
                    words.getOrPut(cellIndex) { mutableListOf() }.add(word.text)
                    val confidence = word.confidence.takeIf { it > 0f } ?: .5f
                    scores[cellIndex] += word.text.count(Char::isLetter) * confidence * confidence
                }
                for ((index, text) in words) lines[index].add(text.joinToString(" "))
            }
            for (index in regions.indices) if (scores[index] > bestScores[index]) {
                bestScores[index] = scores[index]
                bestTexts[index] = lines[index].joinToString("\n")
            }
        }
    } finally { atlas.recycle() }
    return AtlasReadings(bestTexts, exhausted)
}
