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
        val dataPath = prepareModels(context) ?: return@withContext null
        val result = withTimeoutOrNull(budgetMillis) {
            val area = titleArea(dataPath, frame) ?: frame
            try {
                val texts = mutableListOf<String>()
                for (language in listOf("deu_latf", "deu")) {
                    coroutineContext.ensureActive()
                    recognize(dataPath, language, TessBaseAPI.PageSegMode.PSM_AUTO, area)?.let { texts += it }
                }
                texts.joinToString("\n").trim().takeIf(String::isNotBlank)
            } finally { if (area !== frame) area.recycle() }
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
                val text = if (api.init(dataPath, language)) {
                    api.setPageSegMode(mode)
                    api.setImage(image)
                    api.getUTF8Text().also {
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

    /** Copies the bundled models once to no-backup storage; Tesseract needs real files. */
    private fun prepareModels(context: Context): String? = runCatching {
        val root = File(context.noBackupFilesDir, "tess")
        val dir = File(root, "tessdata").apply { mkdirs() }
        for (model in models) {
            val target = File(dir, "$model.traineddata")
            if (target.length() > 0) continue
            val partial = File(dir, "$model.part")
            context.assets.open("tessdata/$model.traineddata").use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(target))
        }
        root.path
    }.getOrNull()

    private inline fun <T> com.googlecode.leptonica.android.Pixa.use(block: (com.googlecode.leptonica.android.Pixa) -> T): T =
        try { block(this) } finally { recycle() }
}
