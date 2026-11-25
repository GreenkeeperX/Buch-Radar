package de.buchradar.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.fraktur.FrakturReader
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in: runs the real old-script reader on local fixtures in cache/fraktur-benchmark (never committed). */
class FrakturBenchmarkTest {
    @Test fun measureLocalFixtures() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("frakturBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "fraktur-benchmark")
        val report = JSONArray()
        for (file in directory.listFiles().orEmpty().filter { it.name.endsWith(".jpg") }.sortedBy { it.name }) {
            val full = BitmapFactory.decodeFile(file.path)
            val scale = minOf(1f, 1600f / maxOf(full.width, full.height))
            val frame = Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
            if (frame !== full) full.recycle()
            val started = SystemClock.elapsedRealtime()
            val text = FrakturReader.read(context, frame, budgetMillis = 60_000)
            report.put(JSONObject().put("file", file.name).put("ms", SystemClock.elapsedRealtime() - started).put("text", text ?: ""))
            frame.recycle()
        }
        File(directory, "report.json").writeText(report.toString())
    }
}
