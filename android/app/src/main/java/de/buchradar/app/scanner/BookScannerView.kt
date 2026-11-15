package de.buchradar.app.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ScannerMode { AUTOMATIC, SINGLE_BOOK, SHELF, COVER_TEST }

@Composable
fun BookScannerView(
    onBarcode: (String) -> Unit,
    onTextConfirmed: (String, Bitmap, Boolean) -> Unit,
    onShelfConfirmed: (List<String>, Bitmap) -> Unit,
    onClose: () -> Unit,
    initialMode: ScannerMode = ScannerMode.AUTOMATIC,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodeCallback by rememberUpdatedState(onBarcode)
    val textConfirmedCallback by rememberUpdatedState(onTextConfirmed)
    val shelfConfirmedCallback by rememberUpdatedState(onShelfConfirmed)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var observedText by remember { mutableStateOf("") }
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var observedSpines by remember { mutableStateOf<List<String>>(emptyList()) }
    var scannerMode by remember { mutableStateOf(initialMode) }
    var automaticShelf by remember { mutableStateOf(false) }
    val scannerModeCallback by rememberUpdatedState(scannerMode)
    val shelfModeCallback by rememberUpdatedState(scannerMode == ScannerMode.SHELF || (scannerMode == ScannerMode.AUTOMATIC && automaticShelf))
    var resultDelivered by remember { mutableStateOf(false) }
    var autoAcceptReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(color = Color.Black.copy(alpha = 0.68f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            autoAcceptReady -> "Bild und Text sind stabil. Die Suche startet gleich automatisch; du kannst auch jetzt tippen."
                            scannerMode == ScannerMode.COVER_TEST -> "Cover-Test: Text liefert Suchkandidaten, danach wird das Kamerabild mit deren Covern verglichen."
                            shelfModeCallback -> "Regalmodus: Barcode-Automatik pausiert. Ruhig halten; Buchrücken werden automatisch übernommen."
                            scannerMode == ScannerMode.AUTOMATIC && automaticShelf -> "Regal erkannt. Ruhig halten; Buchrücken werden automatisch übernommen."
                            else -> "Barcode hat Vorrang. Ruhig halten; erkannter Text wird automatisch gesucht."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = observedText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (observedText.isBlank()) "Text wird stabilisiert …" else "Stabil erkannter Text") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.LightGray,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            }
            cameraError?.let { Text(it, color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .7f)).padding(8.dp)) }
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.68f), MaterialTheme.shapes.medium).padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Schließen", color = Color.White) }
                    TextButton(onClick = { scannerMode = ScannerMode.AUTOMATIC }) { Text(if (scannerMode == ScannerMode.AUTOMATIC) "✓ Auto" else "Auto", color = Color.White) }
                    TextButton(onClick = { scannerMode = ScannerMode.SINGLE_BOOK }) { Text(if (scannerMode == ScannerMode.SINGLE_BOOK) "✓ Einzel" else "Einzel", color = Color.White) }
                    TextButton(onClick = { scannerMode = ScannerMode.SHELF }) { Text(if (scannerMode == ScannerMode.SHELF) "✓ Regal" else "Regal", color = Color.White) }
                    TextButton(onClick = { scannerMode = ScannerMode.COVER_TEST }) { Text(if (scannerMode == ScannerMode.COVER_TEST) "✓ Cover-Test" else "Cover-Test", color = Color.White) }
                }
                Button(
                    onClick = {
                        if (resultDelivered) return@Button
                        resultDelivered = true
                        latestFrame?.let { frame ->
                            if (shelfModeCallback) shelfConfirmedCallback(observedSpines, frame) else textConfirmedCallback(observedText, frame, scannerModeCallback == ScannerMode.COVER_TEST)
                        }
                    },
                    enabled = latestFrame != null && if (shelfModeCallback) observedSpines.isNotEmpty() else observedText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (shelfModeCallback) "Regal jetzt übernehmen" else if (scannerMode == ScannerMode.COVER_TEST) "Cover visuell vergleichen" else "Text jetzt suchen") }
            }
        }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8).build()
        )
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var provider: ProcessCameraProvider? = null
        var barcodeDelivered = false
        var candidateText = ""
        var candidateKey = ""
        var candidateCount = 0
        var displayedKey = ""
        var lastTextPublishAt = 0L
        var lastImageSampleAt = 0L
        var stableImageSince = 0L
        var previousStabilitySample: Bitmap? = null
        var previousShelfMode = shelfModeCallback
        var autoDelivered = false
        try {
            val future = ProcessCameraProvider.getInstance(context)
            val activeProvider = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                future.addListener({
                    try { continuation.resume(future.get()) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }, ContextCompat.getMainExecutor(context))
            }
            provider = activeProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) proxy.close()
                else {
                    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                    barcodeScanner.process(input).addOnSuccessListener { barcodes ->
                        val ean = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                        if (ean != null && !barcodeDelivered && !shelfModeCallback && scannerModeCallback != ScannerMode.COVER_TEST) {
                            barcodeDelivered = true
                            analysis.clearAnalyzer()
                            ContextCompat.getMainExecutor(context).execute { barcodeCallback(ean) }
                            proxy.close()
                        } else {
                            textRecognizer.process(input).addOnSuccessListener { result ->
                                val rawLines = result.text.lines().map(String::trim).filter { it.length >= 2 }
                                val groupedPhrases = groupShelfText(result.textBlocks, proxy.width)
                                    .map { it.replace('\n', ' ').trim() }
                                // Put spatially related title/author lines first for both single-book
                                // matching and shelf lookup, while retaining every raw OCR line as fallback.
                                val text = (groupedPhrases + rawLines)
                                    .distinctBy(::normalizeOcrText)
                                    .joinToString("\n")
                                    .take(2_000)
                                if (text.isNotBlank()) {
                                    val key = normalizeOcrText(text)
                                    if (candidateKey.isNotBlank() && ocrTextSimilarity(candidateKey, key) >= 0.62f) {
                                        candidateCount++
                                        if (text.length > candidateText.length) {
                                            candidateText = text
                                            candidateKey = key
                                        }
                                    } else {
                                        candidateText = text
                                        candidateKey = key
                                        candidateCount = 1
                                    }
                                    val now = SystemClock.elapsedRealtime()
                                    val stable = candidateCount >= 3
                                    val spines = groupShelfText(result.textBlocks, proxy.width)
                                    val verticalLines = result.textBlocks.flatMap { it.lines }.count { kotlin.math.abs(it.angle % 180f) in 55f..125f }
                                    val lineCenters = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                                        line.boundingBox?.let { (it.left + it.right) / 2f }
                                    }
                                    val spread = if (lineCenters.size >= 3) (lineCenters.maxOrNull()!! - lineCenters.minOrNull()!!) / proxy.width.toFloat() else 0f
                                    val sceneLooksLikeShelf = (spines.size >= 4 && spread >= .28f) || (verticalLines >= 3 && spines.size >= 3)
                                    if (previousShelfMode != shelfModeCallback) {
                                previousShelfMode = shelfModeCallback
                                        stableImageSince = 0L
                                    }
                                    if (now - lastImageSampleAt >= 600L) {
                                        lastImageSampleAt = now
                                        val frame = runCatching {
                                            val rawBitmap = proxy.toBitmap()
                                            val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
                                            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                            val scale = minOf(1f, 900f / maxOf(rotated.width, rotated.height))
                                            val output = if (scale < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true) else rotated
                                            if (rawBitmap !== rotated) rawBitmap.recycle()
                                            if (rotated !== output) rotated.recycle()
                                            output
                                        }.getOrNull()
                                        val sample = frame?.let { image ->
                                            val sampleHeight = (32f * image.height / image.width).toInt().coerceAtLeast(16)
                                            Bitmap.createScaledBitmap(image, 32, sampleHeight, true)
                                        }
                                        val difference = if (sample != null && previousStabilitySample != null) imageDifference(previousStabilitySample!!, sample) else Int.MAX_VALUE
                                        previousStabilitySample?.recycle()
                                        previousStabilitySample = sample
                                        val imageStable = difference <= 12
                                        if (stable && imageStable) {
                                            if (stableImageSince == 0L) stableImageSince = now
                                        } else stableImageSince = 0L

                                        val shouldPublishText = stable && (candidateKey != displayedKey || now - lastTextPublishAt >= 1_000L)
                                        val stableText = candidateText
                                        val stableKey = candidateKey
                                        val readyToAutoAccept = stable && stableImageSince != 0L && now - stableImageSince >= 1_500L
                                        if (shouldPublishText || frame != null || readyToAutoAccept || stable) {
                                            ContextCompat.getMainExecutor(context).execute {
                                                if (shouldPublishText) {
                                                    observedText = stableText
                                                    observedSpines = spines
                                                    if (scannerModeCallback == ScannerMode.AUTOMATIC) automaticShelf = sceneLooksLikeShelf
                                                    displayedKey = stableKey
                                                    lastTextPublishAt = now
                                                }
                                                autoAcceptReady = stable && imageStable
                                                if (frame != null) latestFrame = frame
                                                if (readyToAutoAccept && !autoDelivered && !resultDelivered) {
                                                    autoDelivered = true
                                                    resultDelivered = true
                                                    analysis.clearAnalyzer()
                                                    if (shelfModeCallback) shelfConfirmedCallback(spines, frame ?: latestFrame ?: return@execute)
                                                    else textConfirmedCallback(stableText, frame ?: latestFrame ?: return@execute, scannerModeCallback == ScannerMode.COVER_TEST)
                                                }
                                            }
                                        }
                                    }
                                }
                            }.addOnCompleteListener { proxy.close() }
                        }
                    }.addOnFailureListener { proxy.close() }
                }
            }
            activeProvider.unbindAll()
            activeProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            cameraError = "Die Kamera konnte nicht gestartet werden."
        } finally {
            provider?.unbindAll()
            barcodeScanner.close()
            textRecognizer.close()
            executor.shutdown()
        }
    }
}


private fun imageDifference(first: Bitmap, second: Bitmap): Int {
    if (first.width != second.width || first.height != second.height) return Int.MAX_VALUE
    var total = 0L
    val count = first.width * first.height
    for (y in 0 until first.height) for (x in 0 until first.width) {
        val a = first.getPixel(x, y)
        val b = second.getPixel(x, y)
        val lumaA = (android.graphics.Color.red(a) * 299 + android.graphics.Color.green(a) * 587 + android.graphics.Color.blue(a) * 114) / 1000
        val lumaB = (android.graphics.Color.red(b) * 299 + android.graphics.Color.green(b) * 587 + android.graphics.Color.blue(b) * 114) / 1000
        total += kotlin.math.abs(lumaA - lumaB)
    }
    return (total / count).toInt()
}

private fun normalizeOcrText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

private data class ShelfTextPiece(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val angle: Float
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val width get() = (right - left).coerceAtLeast(1)
    val height get() = (bottom - top).coerceAtLeast(1)
}

/** Merge OCR lines that occupy the same narrow spine column; line angle handles rotated spine text. */
private fun groupShelfText(blocks: List<Text.TextBlock>, imageWidth: Int): List<String> {
    val pieces = blocks.flatMap { block -> block.lines }.mapNotNull { line ->
        val text = line.text.trim()
        val box = line.boundingBox ?: return@mapNotNull null
        if (text.length < 2 || !text.any { it.isLetterOrDigit() }) return@mapNotNull null
        ShelfTextPiece(text, box.left, box.top, box.right, box.bottom, line.angle)
    }
    if (pieces.isEmpty()) return blocks.map { it.text.trim() }

    fun sameSpine(a: ShelfTextPiece, b: ShelfTextPiece): Boolean {
        val overlapX = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0)
        val overlapRatio = overlapX.toFloat() / minOf(a.width, b.width)
        val xGap = maxOf(0, maxOf(a.left, b.left) - minOf(a.right, b.right))
        val centerGap = kotlin.math.abs(a.centerX - b.centerX)
        val aVertical = kotlin.math.abs(a.angle % 180f) in 55f..125f
        val bVertical = kotlin.math.abs(b.angle % 180f) in 55f..125f
        val sameColumn = if (aVertical || bVertical) {
            overlapRatio >= 0.45f || centerGap <= maxOf(7f, minOf(a.width, b.width) * 0.65f)
        } else {
            overlapRatio >= 0.38f || centerGap <= maxOf(7f, imageWidth * 0.010f)
        }

        // ML Kit can split one horizontal title line into neighboring blocks; join only when
        // their boxes share the same row and almost touch, avoiding broad row-wise shelf merges.
        val overlapY = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)
        val rowOverlap = overlapY.toFloat() / minOf(a.height, b.height)
        val sameRowFragment = xGap <= maxOf(4, (imageWidth * 0.006f).toInt()) && rowOverlap >= 0.72f
        return sameColumn || sameRowFragment
    }
    // Complete-link grouping prevents a chain of nearby words from merging neighboring spines.
    val clusters = mutableListOf<MutableList<Int>>()
    for (index in pieces.indices) {
        val cluster = clusters.firstOrNull { candidate -> candidate.all { sameSpine(pieces[index], pieces[it]) } }
        if (cluster == null) clusters += mutableListOf(index) else cluster += index
    }

    return clusters
        .map { group ->
            val groupPieces = group.map { pieces[it] }
            val text = groupPieces
                .sortedWith(compareBy<ShelfTextPiece> { it.centerY }.thenBy { it.left })
                .map { it.text }
                .distinctBy(::normalizeOcrText)
                .joinToString("\n")
            groupPieces.map { it.centerX }.average() to text
        }
        .filter { (_, text) -> text.length >= 3 && text.any(Char::isLetter) }
        .sortedBy { it.first }
        .map { it.second }
        .distinctBy(::normalizeOcrText)
}

private fun ocrTextSimilarity(first: String, second: String): Float {
    val a = first.split(Regex("\\s+")).filter(String::isNotBlank).toSet()
    val b = second.split(Regex("\\s+")).filter(String::isNotBlank).toSet()
    if (a.isEmpty() || b.isEmpty()) return 0f
    return a.intersect(b).size.toFloat() / minOf(a.size, b.size)
}
