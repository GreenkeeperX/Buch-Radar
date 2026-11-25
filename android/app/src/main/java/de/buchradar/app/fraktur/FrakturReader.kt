package de.buchradar.app.fraktur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.googlecode.tesseract.android.TessBaseAPI
import de.buchradar.app.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Fallback reader for Fraktur and script titles of a single book (Tesseract, models as measured:
 * deu_latf and deu from tessdata_best, deu fast for locating the title). It reads only the title area
 * (the tallest text lines) of one still frame; the frame stays in memory. Call only behind the
 * fallback rule in [FrakturFallback].
 */
object FrakturReader {
    private val models = listOf("deu_latf", "deu", "deu_fast")

    /** Text of the title area with both models, or null when unavailable, cancelled or out of time. */
    suspend fun read(context: Context, frame: Bitmap, budgetMillis: Long = 8_000): String? = withContext(Dispatchers.Default) {
        val started = SystemClock.elapsedRealtime()
        // The budget includes model preparation; without a located title area nothing is read or searched.
        val result = withTimeoutOrNull(budgetMillis) {
            val dataPath = prepareModels(context) ?: return@withTimeoutOrNull null
            coroutineContext.ensureActive()
            val area = titleArea(dataPath, frame) ?: return@withTimeoutOrNull null
            try {
                val texts = mutableListOf<String>()
                for (language in listOf("deu_latf", "deu")) {
                    coroutineContext.ensureActive()
                    recognize(dataPath, language, TessBaseAPI.PageSegMode.PSM_AUTO, area)?.let { texts += it }
                }
                texts.joinToString("\n").trim().takeIf(String::isNotBlank)
            } finally { area.recycle() }
        }
        AppLog.add(context, "Alte Schrift: ${result?.length ?: 0} Zeichen, ${SystemClock.elapsedRealtime() - started} ms")
        result
    }

    /** Runs one Tesseract pass; cancellation stops the native engine. */
    private suspend fun recognize(dataPath: String, language: String, mode: Int, image: Bitmap, words: MutableList<Rect>? = null): String? {
        val api = TessBaseAPI()
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { api.stop() }
                val text = if (continuation.isActive && api.init(dataPath, language)) {
                    api.setPageSegMode(mode)
                    api.setImage(image)
                    // getHOCRText runs recognition with the cancel monitor, so stop() ends it early;
                    // getUTF8Text then only formats the finished result.
                    api.getHOCRText(0)
                    if (!continuation.isActive) null else api.getUTF8Text().also {
                        if (words != null) api.words.use { pixa -> pixa.boxRects.forEach(words::add) }
                    }
                } else null
                continuation.resume(text)
            }
        } finally { api.recycle() }
    }

    /** Crop around the words whose height is at least 60% of the tallest (as in the offline measurement). */
    private suspend fun titleArea(dataPath: String, frame: Bitmap): Bitmap? {
        val words = mutableListOf<Rect>()
        recognize(dataPath, "deu_fast", TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, frame, words)
        val rect = FrakturFallback.titleRect(words, frame.width, frame.height) ?: return null
        return Bitmap.createBitmap(frame, rect.left, rect.top, rect.width(), rect.height())
    }

    private val prepareLock = Any()

    /**
     * Copies the bundled models to no-backup storage (Tesseract needs real files). Extraction is serialised,
     * written to a unique temporary file and published atomically; a marker with the app's update time and the
     * copied sizes makes an app update or a damaged file trigger a fresh copy.
     */
    private fun prepareModels(context: Context): String? = runCatching {
        synchronized(prepareLock) {
            val root = File(context.noBackupFilesDir, "tess")
            val dir = File(root, "tessdata").apply { mkdirs() }
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            val marker = File(dir, "installed.txt")
            val expected = marker.takeIf { it.exists() }?.readLines().orEmpty()
            val valid = expected.firstOrNull() == stamp && models.all { model ->
                val size = expected.firstOrNull { it.startsWith("$model=") }?.substringAfter('=')?.toLongOrNull()
                size != null && File(dir, "$model.traineddata").length() == size
            }
            if (!valid) {
                marker.delete()
                val sizes = models.map { model ->
                    val temporary = File.createTempFile(model, ".part", dir)
                    try {
                        val copied = context.assets.open("tessdata/$model.traineddata").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                        check(copied > 0 && temporary.length() == copied)
                        check(temporary.renameTo(File(dir, "$model.traineddata")))
                        "$model=$copied"
                    } finally { temporary.delete() }
                }
                marker.writeText((listOf(stamp) + sizes).joinToString("\n"))
            }
            root.path
        }
    }.getOrNull()

    private inline fun <T> com.googlecode.leptonica.android.Pixa.use(block: (com.googlecode.leptonica.android.Pixa) -> T): T =
        try { block(this) } finally { recycle() }
}
