package de.buchradar.app.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.buchradar.app.AppLog
import de.buchradar.app.domain.BookIdentity
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

enum class ScannerMode { AUTOMATIC, SINGLE_BOOK, SHELF, COVER_TEST }

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@OptIn(ExperimentalCoroutinesApi::class)
fun BookScannerView(
    onBarcode: (String) -> Unit,
    onTextConfirmed: (String, Bitmap, Boolean) -> Unit,
    onShelfConfirmed: (List<String>, Bitmap, String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: ScannerMode = ScannerMode.AUTOMATIC
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val barcodeCallback by rememberUpdatedState(onBarcode)
    val textCallback by rememberUpdatedState(onTextConfirmed)
    val shelfCallback by rememberUpdatedState(onShelfConfirmed)
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var mode by remember { mutableStateOf(initialMode) }
    val currentMode by rememberUpdatedState(mode)
    var menu by remember { mutableStateOf(false) }
    var observed by remember { mutableStateOf("") }
    var spines by remember { mutableStateOf<List<String>>(emptyList()) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var shelf by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Buch ins Bild halten") }
    val delivered = remember { AtomicBoolean(false) }
    val manualRequested = remember { AtomicBoolean(false) }
    var finalizing by remember { mutableStateOf(false) }
    val confirm: () -> Unit = { manualRequested.set(true) }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .75f)), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("Schließen", color = Color.White) }
                Box {
                    TextButton(onClick = { menu = true }, enabled = !finalizing) { Text("${when(mode) { ScannerMode.AUTOMATIC -> "Auto"; ScannerMode.SINGLE_BOOK -> "Einzelbuch"; ScannerMode.SHELF -> "Regal"; ScannerMode.COVER_TEST -> "Cover-Test" }} ▾", color = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ScannerMode.entries.forEach { choice -> DropdownMenuItem(text = { Text(when(choice) { ScannerMode.AUTOMATIC -> "Automatisch"; ScannerMode.SINGLE_BOOK -> "Einzelbuch"; ScannerMode.SHELF -> "Regal"; ScannerMode.COVER_TEST -> "Cover-Test (experimentell)" }) }, onClick = { mode = choice; menu = false }) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .8f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (observed.isNotBlank()) Text(observed, color = Color.White, maxLines = 3, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = confirm, enabled = !finalizing && observed.isNotBlank() && frame != null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (finalizing) "Buchrücken werden gelesen …" else if (shelf) "Buchrücken jetzt lesen" else "Jetzt suchen")
                }
            }
        }
    }

    LaunchedEffect(preview, owner) {
        val executor = Executors.newSingleThreadExecutor()
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val barcodeScanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var provider: ProcessCameraProvider? = null
        var previewUseCase: Preview? = null
        var analysis: ImageAnalysis? = null
        var capture: ImageCapture? = null
        var latestOwned: Bitmap? = null
        var transferredFrame: Bitmap? = null
        var previousSample: IntArray? = null
        var lastAt = 0L
        var lastTextAt = 0L
        var shelfVotes = 0
        var sampleCount = 0
        var priorMode = currentMode
        var publishKey = ""
        var publishCount = 0
        var rotatedPieces = emptyList<OcrPiece>()
        val stability = RecognitionStability()
        try {
            val future = ProcessCameraProvider.getInstance(context)
            val active = suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                future.addListener({
                    if (continuation.isActive) try { continuation.resume(future.get()) } catch (error: Exception) { continuation.resumeWithException(error) }
                }, ContextCompat.getMainExecutor(context))
            }
            provider = active
            val cameraPreview = withContext(Dispatchers.Main.immediate) {
                Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            }
            previewUseCase = cameraPreview
            val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()).build()
            analysis = analyzer
            analyzer.setAnalyzer(executor) { proxy ->
                if (!worker.isActive || delivered.get() || SystemClock.elapsedRealtime() - lastAt < 400) proxy.close()
                else worker.launch(start = CoroutineStart.ATOMIC) {
                    var ownedFrame: Bitmap? = null
                    try {
                        ensureActive()
                        lastAt = SystemClock.elapsedRealtime()
                        val media = proxy.image ?: return@launch
                        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        val barcodes = barcodeScanner.process(input).awaitCompletion()
                        ensureActive()
                        val isbn = barcodes.mapNotNull { BookIdentity.canonicalIsbn(it.rawValue) }.distinct().singleOrNull()
                        val actualMode = currentMode
                        if (actualMode != priorMode) { stability.reset(); shelfVotes = 0; rotatedPieces = emptyList(); manualRequested.set(false); priorMode = actualMode }
                        if (isbn != null && actualMode != ScannerMode.SHELF && actualMode != ScannerMode.COVER_TEST && delivered.compareAndSet(false, true)) {
                            withContext(Dispatchers.Main) { barcodeCallback(isbn) }
                            return@launch
                        }
                        val recognized = recognizer.process(input).awaitCompletion()
                        ensureActive()
                        val raw = proxy.toBitmap()
                        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }, true)
                        if (raw !== rotated) raw.recycle()
                        val scale = minOf(1f, 1920f / maxOf(rotated.width, rotated.height))
                        val image = if (scale < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true) else rotated
                        if (image !== rotated) rotated.recycle()
                        ownedFrame = image
                        val width = if (proxy.imageInfo.rotationDegrees % 180 == 0) proxy.width else proxy.height
                        val height = if (proxy.imageInfo.rotationDegrees % 180 == 0) proxy.height else proxy.width
                        val sample = imageSample(image)
                        val difference = previousSample?.let { old -> old.indices.sumOf { abs(old[it] - sample[it]) }.toDouble() / old.size } ?: 255.0
                        previousSample = sample
                        if (difference >= 8) rotatedPieces = emptyList()
                        if (sampleCount % 3 == 0 && (actualMode == ScannerMode.SHELF || actualMode == ScannerMode.AUTOMATIC || recognized.text.length < 12)) {
                            val extra = mutableListOf<OcrPiece>()
                            for (degrees in listOf(90, 270)) {
                                val result = recognizer.process(InputImage.fromBitmap(image, degrees)).awaitCompletion()
                                ensureActive()
                                extra += result.toPieces().map { piece -> unrotatePiece(piece, degrees, image.width, image.height) }
                            }
                            rotatedPieces = extra
                        }
                        val nativePieces = recognized.toPieces().map { p -> p.copy(left = p.left * image.width / width, right = p.right * image.width / width, top = p.top * image.height / height, bottom = p.bottom * image.height / height) }
                        val pieces = (nativePieces + rotatedPieces).fold(mutableListOf<OcrPiece>()) { acc, piece ->
                            val duplicate = acc.any { old -> foldOcr(old.text) == foldOcr(piece.text) && abs(old.x - piece.x) < minOf(old.width, piece.width) * .5f && abs(old.y - piece.y) < minOf(old.height, piece.height) * .5f }
                            if (!duplicate) acc += piece
                            acc
                        }
                        val groups = groupSpines(pieces, image.width, image.height)
                        val found = groups.map(::spineText).filter { it.isNotBlank() }
                        val looksShelf = groups.size >= 3 && groups.count { group -> group.any { abs(it.angle % 180) in 55f..125f } } >= 2
                        shelfVotes = (shelfVotes + if (looksShelf) 1 else -1).coerceIn(-4, 4)
                        val isShelf = actualMode == ScannerMode.SHELF || actualMode == ScannerMode.AUTOMATIC && shelfVotes >= 3
                        val text = if (isShelf) found.joinToString("\n") else found.joinToString("\n").take(2_000)
                        val edges = sample.indices.filter { it % 64 in 1..62 && it / 64 in 1..62 }
                            .map { abs(4 * sample[it] - sample[it-1] - sample[it+1] - sample[it-64] - sample[it+64]) }.average()
                        val ready = stability.observe(text, lastAt, difference < 8, edges > 2.0)
                        val currentKey = foldOcr(text)
                        if (currentKey == publishKey) publishCount++ else { publishKey = currentKey; publishCount = 1 }
                        val shouldPublish = text.isNotBlank() && publishCount >= 2
                        sampleCount++
                        if (sampleCount % 10 == 0) AppLog.add(context, "Kamera: ${width}×$height, ${groups.size} Textregionen, Bewegung=${difference.toInt()}, Konturen=${edges.toInt()}")
                        withContext(Dispatchers.Main) {
                            if (delivered.get()) return@withContext
                            if (shouldPublish || ready) {
                                latestOwned?.recycle()
                                latestOwned = image
                                ownedFrame = null
                                frame = image
                                shelf = isShelf
                                observed = text; spines = found; lastTextAt = lastAt
                            } else {
                                if (lastAt - lastTextAt > 2_000) { observed = ""; spines = emptyList() }
                            }
                            hint = when {
                                text.isBlank() -> "Näher heran – noch kein lesbarer Text"
                                edges <= 2.0 -> "Text noch unscharf – bitte neu ausrichten"
                                difference >= 8 -> "Noch ruhig halten"
                                isShelf -> "${found.size} Buchrücken – ruhig halten"
                                actualMode == ScannerMode.COVER_TEST -> "Cover-Test: Text liefert Kandidaten"
                                else -> "Text erkannt – Suche startet bei ruhigem Bild"
                            }
                        }
                        if (text.isNotBlank() && (ready || manualRequested.get()) && delivered.compareAndSet(false, true)) {
                            manualRequested.set(false)
                            withContext(Dispatchers.Main) { finalizing = true; if (isShelf) hint = "Buchrücken werden getrennt gelesen – einen Moment …" }
                            val separated = if (isShelf) readShelfWithStill(context, capture, image, pieces, recognizer) else null
                            ensureActive()
                            withContext(Dispatchers.Main) {
                                if (latestOwned !== image) latestOwned?.recycle()
                                latestOwned = image
                                ownedFrame = null
                                transferredFrame = image
                                if (isShelf) {
                                    val selected = separated?.texts?.takeIf { it.isNotEmpty() } ?: found
                                    val warning = when {
                                        separated == null -> "Buchgrenzen waren nicht eindeutig. Bitte die vorgeschlagenen Gruppen prüfen."
                                        separated.exhausted -> "Nicht alle Rücken konnten vollständig gelesen werden. Fehlende Bücher bitte näher scannen."
                                        separated.texts.size < separated.regions -> "${separated.regions - separated.texts.size} Bildbereiche waren nicht lesbar. Fehlende Bücher bitte näher scannen."
                                        else -> null
                                    }
                                    AppLog.add(context, "Regal: ${separated?.regions ?: 0} Bildregionen, ${selected.size} Texte, Zeitlimit=${separated?.exhausted ?: false}")
                                    shelfCallback(selected, image, warning)
                                } else textCallback(text, image, actualMode == ScannerMode.COVER_TEST)
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        AppLog.add(context, "Kameraanalyse fehlgeschlagen (${error.javaClass.simpleName})")
                        delivered.set(false)
                        withContext(Dispatchers.Main) { finalizing = false; hint = "Erkennung unterbrochen – bitte erneut ausrichten" }
                    } finally { ownedFrame?.recycle(); proxy.close() }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                active.unbindAll()
                // FIT_CENTER exposes the full analysis region; share a viewport when it is ready.
                fun group(still: ImageCapture?) = UseCaseGroup.Builder().addUseCase(cameraPreview).addUseCase(analyzer)
                    .apply { still?.let { addUseCase(it) }; preview.viewPort?.let { setViewPort(it) } }.build()
                // A still image gives shelf reading more pixels than the analysis stream.
                val still = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(2592, 1944), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()).build()
                try {
                    active.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group(still))
                    capture = still
                } catch (unsupported: IllegalArgumentException) {
                    AppLog.add(context, "Kamera: kein zusätzliches Foto möglich, Regal nutzt Vorschaubild")
                    active.unbindAll()
                    active.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group(null))
                }
            }
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { hint = "Kamera konnte nicht gestartet werden"; AppLog.add(context, "Kamerastart: ${error.javaClass.simpleName}") }
        finally {
            analysis?.clearAnalyzer()
            worker.cancel()
            withContext(NonCancellable) { worker.coroutineContext[Job]?.join() }
            // An earlier screen may finish cleanup after the next scanner has already bound.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                provider?.unbind(*listOfNotNull(previewUseCase, analysis, capture).toTypedArray())
            }
            barcodeScanner.close(); recognizer.close(); executor.shutdown()
            if (latestOwned !== transferredFrame) latestOwned?.recycle()
        }
    }
}

/**
 * Reads shelf regions from a fresh still image when its framing matches the analysed frame;
 * otherwise, or if capturing fails or takes too long, from the analysed frame itself.
 */
private suspend fun readShelfWithStill(context: android.content.Context, capture: ImageCapture?, frame: Bitmap,
    pieces: List<OcrPiece>, recognizer: com.google.mlkit.vision.text.TextRecognizer): ShelfImageResult? {
    val still = capture?.let { captureStill(context, it, maxSide = 2560) }
    if (still == null || still.width <= frame.width ||
        abs(still.width.toFloat() / still.height - frame.width.toFloat() / frame.height) > .02f * frame.width / frame.height) {
        still?.recycle()
        return readShelfRegions(frame, recognizer, protectedPieces = pieces)
    }
    return try {
        val f = still.width.toFloat() / frame.width
        AppLog.add(context, "Regal: Foto ${still.width}×${still.height} statt ${frame.width}×${frame.height}")
        readShelfRegions(still, recognizer, protectedPieces = pieces.map { p ->
            p.copy(left = p.left * f, top = p.top * f, right = p.right * f, bottom = p.bottom * f)
        })
    } finally { still.recycle() }
}

/** Takes one picture, upright and scaled to [maxSide]; null on error or after four seconds. */
private suspend fun captureStill(context: android.content.Context, capture: ImageCapture, maxSide: Int): Bitmap? {
    val proxy = withTimeoutOrNull(4_000) {
        suspendCancellableCoroutine<ImageProxy?> { continuation ->
            capture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // A late picture after timeout or closing must still be released.
                    if (continuation.isActive) continuation.resume(image) else image.close()
                }
                override fun onError(exception: ImageCaptureException) {
                    AppLog.add(context, "Regal-Foto fehlgeschlagen (${exception.imageCaptureError})")
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
    } ?: return null
    return try {
        val raw = proxy.toBitmap()
        val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
        val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()); postScale(scale, scale) }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (upright !== raw) raw.recycle()
        upright
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        AppLog.add(context, "Regal-Foto nicht lesbar (${error.javaClass.simpleName})")
        null
    } finally { proxy.close() }
}

/** Keep ImageProxy alive until ML Kit is finished, even when the screen closes. */
internal suspend fun <T> Task<T>.awaitCompletion(): T = withContext(NonCancellable) {
    suspendCoroutine { continuation -> addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: CancellationException("ML task cancelled"))
    } }
}

internal fun Text.toPieces(): List<OcrPiece> = textBlocks.flatMap { it.lines }.mapNotNull { line ->
    line.boundingBox?.let { box -> OcrPiece(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), line.angle) }
}

private fun imageSample(bitmap: Bitmap): IntArray {
    val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    return IntArray(4096) { index ->
        val color = small.getPixel(index % 64, index / 64)
        (android.graphics.Color.red(color) * 299 + android.graphics.Color.green(color) * 587 + android.graphics.Color.blue(color) * 114) / 1000
    }.also { if (small !== bitmap) small.recycle() }
}

internal fun unrotatePiece(piece: OcrPiece, degrees: Int, width: Int, height: Int): OcrPiece {
    val points = listOf(piece.left to piece.top, piece.right to piece.top, piece.left to piece.bottom, piece.right to piece.bottom)
        .map { (x, y) -> if (degrees == 90) y to (height - x) else (width - y) to x }
    var angle = piece.angle - degrees
    while (angle < -180) angle += 360
    return piece.copy(left = points.minOf { it.first }, right = points.maxOf { it.first }, top = points.minOf { it.second }, bottom = points.maxOf { it.second }, angle = angle)
}
