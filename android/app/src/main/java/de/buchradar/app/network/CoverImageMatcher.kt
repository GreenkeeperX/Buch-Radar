package de.buchradar.app.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ranks a short catalog result list against the current camera frame; it stores neither image. */
object CoverImageMatcher {
    suspend fun rank(context: Context, cameraFrame: Bitmap, books: List<CatalogBook>, visualOnly: Boolean = false): List<CatalogBook> =
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(context)
            val queryFeatures = features(cameraFrame)
            books.map { book ->
                val url = book.coverUrl ?: book.isbn?.let { "https://covers.openlibrary.org/b/isbn/$it-M.jpg?default=false" }
                val score = url?.let {
                    runCatching {
                        val request = ImageRequest.Builder(context)
                            .data(it)
                            .allowHardware(false)
                            .size(320)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) similarity(queryFeatures, features(result.drawable.toBitmap())) else null
                    }.getOrNull()
                }
                book.copy(visualSimilarity = score)
            }.sortedByDescending { book ->
                val imageScore = book.visualSimilarity
                val textScore = book.textSimilarity
                if (visualOnly) return@sortedByDescending (imageScore ?: -1).toDouble()
                when {
                    imageScore != null && textScore != null -> textScore * 0.62 + imageScore * 0.38
                    textScore != null -> textScore.toDouble()
                    imageScore != null -> imageScore.toDouble()
                    else -> 0.0
                }
            }
        }

    private data class Features(val hash: Long, val histogram: IntArray)

    private fun features(bitmap: Bitmap): Features {
        val cover = centerCrop(bitmap, 0.68f)
        val small = Bitmap.createScaledBitmap(cover, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val left = luminance(small.getPixel(x, y))
            val right = luminance(small.getPixel(x + 1, y))
            if (left > right) hash = hash or (1L shl bit)
            bit++
        }
        val histogram = IntArray(24)
        val sample = Bitmap.createScaledBitmap(cover, 32, 48, true)
        for (y in 0 until sample.height) for (x in 0 until sample.width) {
            val color = sample.getPixel(x, y)
            histogram[Color.red(color) * 8 / 256]++
            histogram[8 + Color.green(color) * 8 / 256]++
            histogram[16 + Color.blue(color) * 8 / 256]++
        }
        return Features(hash, histogram)
    }

    private fun similarity(first: Features, second: Features): Int {
        val hashMatches = 64 - java.lang.Long.bitCount(first.hash xor second.hash)
        val firstTotal = first.histogram.sum().toFloat()
        val secondTotal = second.histogram.sum().toFloat()
        var histogramIntersection = 0f
        for (i in first.histogram.indices) {
            histogramIntersection += minOf(first.histogram[i] / firstTotal, second.histogram[i] / secondTotal)
        }
        val score = 0.75f * (hashMatches / 64f) + 0.25f * (histogramIntersection / 3f)
        return (score * 100).toInt()
    }

    private fun centerCrop(bitmap: Bitmap, aspectRatio: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val current = width.toFloat() / height
        val crop = if (current > aspectRatio) {
            val cropWidth = (height * aspectRatio).toInt().coerceAtLeast(1)
            Rect((width - cropWidth) / 2, 0, (width + cropWidth) / 2, height)
        } else {
            val cropHeight = (width / aspectRatio).toInt().coerceAtLeast(1)
            Rect(0, (height - cropHeight) / 2, width, (height + cropHeight) / 2)
        }
        val output = Bitmap.createBitmap(68, 100, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, crop, Rect(0, 0, output.width, output.height), null)
        return output
    }

    private fun luminance(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}
