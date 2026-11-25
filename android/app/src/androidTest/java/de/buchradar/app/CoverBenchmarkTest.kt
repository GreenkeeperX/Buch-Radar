package de.buchradar.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.cover.CoverVerifier
import de.buchradar.app.network.CatalogBook
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in measurement of the real cover confirmation on local fixtures (never committed):
 * cache/cover-benchmark/manifest.json lists photos and candidate cover files with their truth kind.
 */
class CoverBenchmarkTest {
    @Test fun measureLocalFixtures() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "cover-benchmark")
        val photos = JSONObject(File(directory, "manifest.json").readText()).getJSONArray("photos")
        val report = JSONArray()
        for (index in 0 until photos.length()) {
            val photo = photos.getJSONObject(index)
            val decode = BitmapFactory.Options().apply { inSampleSize = 2 }
            val full = BitmapFactory.decodeFile(File(directory, photo.getString("file")).path, decode)
            val scale = minOf(1f, 1000f / maxOf(full.width, full.height))
            val frame = Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
            if (frame !== full) full.recycle()
            val candidates = photo.getJSONArray("candidates")
            val books = (0 until candidates.length()).map { i ->
                val c = candidates.getJSONObject(i)
                CatalogBook("t$i", null, null, isbn = "c$i", coverUrl = "file://" + File(directory, c.getString("file")).path)
            }
            val started = SystemClock.elapsedRealtime()
            val scores = CoverVerifier.scores(context, frame, books, budgetMillis = 600_000, maxCandidates = books.size)
            val elapsed = SystemClock.elapsedRealtime() - started
            frame.recycle()
            val rows = JSONArray()
            for (i in 0 until candidates.length()) rows.put(JSONObject().put("kind", candidates.getJSONObject(i).getString("kind")).put("inliers", scores["isbn:c$i"] ?: -1))
            report.put(JSONObject().put("cls", photo.getString("cls")).put("ms", elapsed).put("compared", scores.size).put("rows", rows))
        }
        File(directory, "report.json").writeText(report.toString())
    }
}
