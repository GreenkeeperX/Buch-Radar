package de.buchradar.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.buchradar.app.scanner.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Opt-in local still-image measurement. No network or private books are accessed. */
class RecognitionBenchmarkTest {
    @Test fun measurePublicFixtures() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "recognition-benchmark")
        val manifest = JSONObject(File(directory, "manifest.json").readText()).getJSONArray("images")
        val report = JSONArray()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for (index in 0 until manifest.length()) {
                val fixture = manifest.getJSONObject(index)
                val filename = fixture.getString("file")
                require(filename.matches(Regex("[a-z0-9-]+\\.jpg")))
                val path = File(directory, filename).path
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val decode = BitmapFactory.Options()
                while (maxOf(bounds.outWidth, bounds.outHeight) / (decode.inSampleSize.coerceAtLeast(1) * 2) >= 2560) {
                    decode.inSampleSize = decode.inSampleSize.coerceAtLeast(1) * 2
                }
                val original = requireNotNull(BitmapFactory.decodeFile(path, decode))
                try {
                    val shelf = fixture.getString("mode") == "shelf"
                    val sizes = if (shelf) listOf(1280, 1920, 2560) else listOf(1280)
                    val rotations = if (shelf) listOf(0) else listOf(0, 90, 270)
                    for (maxSize in sizes) for (rotation in rotations) {
                        val factor = minOf(1f, maxSize.toFloat() / maxOf(original.width, original.height))
                        val scaled = Bitmap.createScaledBitmap(original, (original.width * factor).toInt(), (original.height * factor).toInt(), true)
                        val image = if (rotation == 0) scaled else Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height,
                            Matrix().apply { postRotate(rotation.toFloat()) }, true)
                        if (image !== scaled && scaled !== original) scaled.recycle()
                        try {
                            val started = SystemClock.elapsedRealtime()
                            val pieces = mutableListOf<OcrPiece>()
                            for (degrees in listOf(0, 90, 270)) {
                                val result = recognizer.process(InputImage.fromBitmap(image, degrees)).awaitCompletion()
                                val found = result.toPieces().map { if(degrees == 0) it else unrotatePiece(it, degrees, image.width, image.height) }
                                for (piece in found) {
                                    if (pieces.none { old -> foldOcr(old.text) == foldOcr(piece.text) && abs(old.x - piece.x) < minOf(old.width, piece.width) * .5f && abs(old.y - piece.y) < minOf(old.height, piece.height) * .5f }) pieces += piece
                                }
                            }
                            val groups = groupSpines(pieces, image.width, image.height)
                            val observed = foldOcr(pieces.joinToString(" ") { it.text }).split(' ').toSet()
                            val expected = fixture.getJSONArray("expectedTokens").strings()
                            val row = JSONObject().put("file", filename).put("rotation", rotation).put("maxSize", maxSize)
                                .put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                                .put("groups", groups.size).put("pieces", pieces.size)
                                .put("expectedTokens", expected.size).put("recognizedTokens", expected.count { it in observed })
                            if (shelf) {
                                val regions = fixture.getJSONArray("regions")
                                fun regionOf(piece: OcrPiece): Int? = (0 until regions.length()).firstOrNull { n ->
                                    val region = regions.getJSONObject(n)
                                    piece.x / image.width >= region.getDouble("left") && piece.x / image.width <= region.getDouble("right") &&
                                        piece.y / image.height in .1f.. .92f
                                }
                                val membership = groups.map { group -> group.mapNotNull(::regionOf).toSet() }
                                val merged = membership.count { it.size > 1 }
                                val split = (0 until regions.length()).count { n -> membership.count { n in it } > 1 }
                                val complete = (0 until regions.length()).count { n ->
                                    val matches = membership.indices.filter { n in membership[it] }
                                    if (matches.size != 1 || membership[matches.single()].size != 1) false else {
                                        val words = foldOcr(spineText(groups[matches.single()])).split(' ').toSet()
                                        val target = regions.getJSONObject(n).getJSONArray("tokens").strings()
                                        target.count { it in words }.toFloat() / target.size >= .6f
                                    }
                                }
                                row.put("expectedRegions", regions.length()).put("cleanReadableRegions", complete)
                                    .put("mergedGroups", merged).put("splitRegions", split)
                                val regionStarted = SystemClock.elapsedRealtime()
                                val cropped = readShelfRegions(image, recognizer, protectedPieces = pieces)
                                if (cropped != null) {
                                    val mapped = cropped.readings.map { reading -> (0 until regions.length()).filter { n ->
                                        val r = regions.getJSONObject(n)
                                        val overlap = minOf(reading.right.toDouble(), r.getDouble("right")) - maxOf(reading.left.toDouble(), r.getDouble("left"))
                                        overlap / (r.getDouble("right") - r.getDouble("left")) > .4
                                    }.toSet() }
                                    val clean = (0 until regions.length()).count { n ->
                                        val ids = mapped.indices.filter { n in mapped[it] }
                                        if (ids.size != 1 || mapped[ids.single()].size != 1) false else {
                                            val words = foldOcr(cropped.readings[ids.single()].text).split(' ').toSet()
                                            val target = regions.getJSONObject(n).getJSONArray("tokens").strings()
                                            target.count { it in words }.toFloat() / target.size >= .6f
                                        }
                                    }
                                    row.put("regionPassMillis", SystemClock.elapsedRealtime() - regionStarted)
                                        .put("imageRegions", cropped.regions).put("regionPassExhausted", cropped.exhausted)
                                        .put("regionPassCleanReadable", clean).put("regionPassMerged", mapped.count { it.size > 1 })
                                        .put("regionPassSplit", (0 until regions.length()).count { n -> mapped.count { n in it } > 1 })
                                    row.put("regionReadings", JSONArray(cropped.readings.map { r -> JSONObject().put("text", r.text).put("left", r.left).put("right", r.right) }))
                                }
                            }
                            println("Recognition benchmark: $row")
                            row.put("ocr", JSONArray(groups.map { group -> JSONObject().put("text", spineText(group))
                                .put("left", group.minOf { it.left } / image.width).put("right", group.maxOf { it.right } / image.width) }))
                            report.put(row)
                        } finally { if (image !== original) image.recycle() }
                    }
                } finally { original.recycle() }
            }
            File(directory, "report.json").writeText(JSONObject().put("version", BuildConfig.VERSION_NAME)
                .put("scope", "Small public still-image set, not representative camera accuracy").put("cases", report).toString(2))
        } finally { recognizer.close() }
    }

    private fun JSONArray.strings() = (0 until length()).map(::getString)
}
