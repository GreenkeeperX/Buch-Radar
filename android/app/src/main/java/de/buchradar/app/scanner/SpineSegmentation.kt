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

internal data class SpineEdge(val x: Float, val slope: Float, val strength: Float) {
    fun at(y: Float, height: Int) = x + slope * (y - height * .55f)
}

/** Long, consistently directed colour transitions; short glyph edges should not split books. */
internal fun spineEdges(pixels: IntArray, width: Int, height: Int): List<SpineEdge> {
    if (width < 32 || height < 32 || pixels.size != width * height) return emptyList()
    val candidates = mutableListOf<SpineEdge>()
    val rows = 48
    val delta = (width / 640).coerceAtLeast(1)
    for (x in delta until width - delta) {
        var best = SpineEdge(x.toFloat(), 0f, 0f)
        for (slope in listOf(-.12f, -.06f, 0f, .06f, .12f)) {
            val red = IntArray(rows); val green = IntArray(rows); val blue = IntArray(rows)
            var count = 0
            for (row in 0 until rows) {
                val y = (height * (.23f + .65f * row / (rows - 1))).toInt().coerceIn(0, height - 1)
                val xx = (x + slope * (y - height * .55f)).toInt()
                if (xx - delta < 0 || xx + delta >= width) continue
                val a = pixels[y * width + xx - delta]; val b = pixels[y * width + xx + delta]
                red[count] = (b shr 16 and 255) - (a shr 16 and 255)
                green[count] = (b shr 8 and 255) - (a shr 8 and 255)
                blue[count] = (b and 255) - (a and 255)
                count++
            }
            if (count < rows * .8f) continue
            red.sort(0, count); green.sort(0, count); blue.sort(0, count)
            val strength = (abs(red[count / 2]) + abs(green[count / 2]) + abs(blue[count / 2])) / 3f / (1 + abs(slope) * 5)
            if (strength > best.strength) best = SpineEdge(x.toFloat(), slope, strength)
        }
        if (best.strength >= 22f) candidates += best
    }
    val selected = mutableListOf<SpineEdge>()
    for (edge in candidates.sortedByDescending { it.strength }) {
        if (selected.all { other -> abs(other.x - edge.x) > width * .018f }) selected += edge
        if (selected.size >= 40) break
    }
    return selected.sortedBy { it.x }
}

internal data class ShelfRegionText(val text: String, val left: Float, val right: Float)
internal data class ShelfImageResult(val readings: List<ShelfRegionText>, val regions: Int, val exhausted: Boolean) {
    val texts get() = readings.map { it.text }.filter { it.count(Char::isLetter) >= 4 }
}

/** Read separated regions in one padded atlas; OCR lines may not change word ownership. */
internal suspend fun readShelfRegions(bitmap: Bitmap, recognizer: TextRecognizer,
    budgetMillis: Long = 15_000, protectedPieces: List<OcrPiece> = emptyList()): ShelfImageResult? {
    val sampleWidth = minOf(640, bitmap.width)
    val sampleHeight = (bitmap.height.toFloat() * sampleWidth / bitmap.width).toInt().coerceAtLeast(1)
    val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
    val pixels = IntArray(sampleWidth * sampleHeight)
    sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
    if (sample !== bitmap) sample.recycle()
    val scale = bitmap.width.toFloat() / sampleWidth
    val edges = spineEdges(pixels, sampleWidth, sampleHeight).map { it.copy(x = it.x * scale) }.filter { edge ->
        // Vertical lettering is not a binding edge; horizontal lines may already span books.
        protectedPieces.none { p -> abs(p.angle % 180) in 55f..125f && p.width < bitmap.width * .05f && edge.x > p.left && edge.x < p.right }
    }
    if (edges.size < 4) return null
    val boundaries = listOf(SpineEdge(0f, 0f, 0f)) + edges + SpineEdge(bitmap.width.toFloat(), 0f, 0f)
    val regions = boundaries.zipWithNext().filter { (left, right) ->
        val width = right.x - left.x
        width >= bitmap.width * .012f && width <= bitmap.width * .25f &&
            right.at(0f, bitmap.height) - left.at(0f, bitmap.height) >= 8 &&
            right.at(bitmap.height.toFloat(), bitmap.height) - left.at(bitmap.height.toFloat(), bitmap.height) >= 8
    }
    if (regions.size < 3) return null
    val deadline = android.os.SystemClock.elapsedRealtime() + budgetMillis
    val readings = mutableListOf<ShelfRegionText>()
    var exhausted = false
    for (batch in regions.chunked(4)) {
        coroutineContext.ensureActive()
        if (android.os.SystemClock.elapsedRealtime() >= deadline) { exhausted = true; break }
        val result = readSpineBatch(bitmap, recognizer, batch, deadline)
        readings += result.readings
        if (result.exhausted) { exhausted = true; break }
    }
    val supplemented = regions.map { (left, right) ->
        val reading = readings.firstOrNull { abs(it.left - left.x / bitmap.width) < .0001f }
        val existing = protectedPieces.filter { p -> p.left >= left.at(p.y, bitmap.height) && p.right <= right.at(p.y, bitmap.height) }
        val fallback = spineText(existing)
        ShelfRegionText(reading?.text?.let(::cleanSpineText)?.takeIf { it.count(Char::isLetter) >= 4 } ?: fallback, left.x / bitmap.width, right.x / bitmap.width)
    }
    return ShelfImageResult(supplemented, regions.size, exhausted)
}

private suspend fun readSpineBatch(bitmap: Bitmap, recognizer: TextRecognizer,
    regions: List<Pair<SpineEdge, SpineEdge>>, deadline: Long): ShelfImageResult {
    val padding = 32
    val longSide = bitmap.height.coerceAtMost(1280)
    val cellWidth = (regions.maxOf { it.second.x - it.first.x } * longSide / bitmap.height).toInt().coerceAtLeast(32) + padding * 2
    val cellHeight = longSide + padding * 2
    val columns = kotlin.math.ceil(kotlin.math.sqrt(regions.size.toDouble() * cellHeight / cellWidth)).toInt().coerceIn(1, 12)
    val rows = (regions.size + columns - 1) / columns
    val downscale = minOf(1f, kotlin.math.sqrt(4_000_000f / (columns.toFloat() * cellWidth * rows * cellHeight)))
    val atlas = Bitmap.createBitmap((columns * cellWidth * downscale).toInt().coerceAtLeast(32),
        (rows * cellHeight * downscale).toInt().coerceAtLeast(32), Bitmap.Config.ARGB_8888)
    data class Cell(val x: Float, val y: Float, val width: Float, val height: Float)
    val cells = regions.mapIndexed { index, (left, right) ->
        Cell(((index % columns) * cellWidth + padding) * downscale,
            ((index / columns) * cellHeight + padding) * downscale,
            ((right.x - left.x) * longSide / bitmap.height).coerceAtLeast(32f) * downscale, longSide * downscale)
    }
    val bestTexts = MutableList(regions.size) { "" }
    val bestScores = FloatArray(regions.size)
    var exhausted = false
    try {
        val canvas = Canvas(atlas)
        canvas.drawColor(android.graphics.Color.LTGRAY)
        for (index in regions.indices) {
            val (left, right) = regions[index]
            val cell = cells[index]
            val height = bitmap.height
            val source = floatArrayOf(left.at(0f, height), 0f, right.at(0f, height), 0f,
                right.at(height.toFloat(), height), height.toFloat(), left.at(height.toFloat(), height), height.toFloat())
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
    return ShelfImageResult(regions.mapIndexed { index, (left, right) ->
        ShelfRegionText(bestTexts[index], left.x / bitmap.width, right.x / bitmap.width)
    }, regions.size, exhausted)
}
