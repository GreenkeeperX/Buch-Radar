package de.buchradar.app.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun CoverTextScannerView(onTextRecognized: (String, Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCallback by rememberUpdatedState(onTextRecognized)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(previewView, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var provider: ProcessCameraProvider? = null
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
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) proxy.close()
                else recognizer.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        if (text.length >= 4) {
                            analysis.clearAnalyzer()
                            val rawBitmap = proxy.toBitmap()
                            val rotation = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
                            val orientedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, rotation, true)
                            val scale = minOf(1f, 640f / maxOf(orientedBitmap.width, orientedBitmap.height))
                            val previewBitmap = if (scale < 1f) Bitmap.createScaledBitmap(orientedBitmap, (orientedBitmap.width * scale).toInt(), (orientedBitmap.height * scale).toInt(), true) else orientedBitmap
                            currentCallback(text, previewBitmap)
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
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
            recognizer.close()
            executor.shutdown()
        }
    }
    cameraError?.let { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(it, color = MaterialTheme.colorScheme.error)
    } }
}
