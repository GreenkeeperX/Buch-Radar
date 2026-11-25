package de.buchradar.app.cover

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.buchradar.app.AppLog
import de.buchradar.app.network.CatalogBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.coroutines.coroutineContext

/**
 * Confirms catalog candidates whose cover shows the same design as the photographed single book:
 * ORB features plus a RANSAC homography, parameters as in the offline measurement. The camera frame
 * stays in memory only; nothing is stored or uploaded. Call only behind coverEvidenceAllowed().
 */
object CoverVerifier {
    private val ready by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    /** Candidate ids with at least [CoverConfirmation.MIN_INLIERS] inliers; empty when unavailable. */
    suspend fun confirm(context: Context, frame: Bitmap, candidates: List<CatalogBook>, budgetMillis: Long = 8_000): Set<String> =
        scores(context, frame, candidates, budgetMillis).filterValues(CoverConfirmation::confirmed).keys

    /** Inlier count per compared candidate id (measurement hook; the app only uses [confirm]). */
    internal suspend fun scores(context: Context, frame: Bitmap, candidates: List<CatalogBook>, budgetMillis: Long = 8_000,
                                maxCandidates: Int = CoverConfirmation.MAX_CANDIDATES): Map<String, Int> =
        withContext(Dispatchers.Default) {
            if (!ready) return@withContext emptyMap()
            val started = SystemClock.elapsedRealtime()
            val loader = context.applicationContext.imageLoader
            val query = features(rectify(frame)) ?: return@withContext emptyMap()
            val scores = linkedMapOf<String, Int>()
            var compared = 0
            try {
                for (book in candidates.take(maxCandidates)) {
                    coroutineContext.ensureActive()
                    val remaining = budgetMillis - (SystemClock.elapsedRealtime() - started)
                    if (remaining <= 0) break
                    val url = book.coverUrl ?: continue
                    val request = ImageRequest.Builder(context.applicationContext).data(url).allowHardware(false).size(600).build()
                    val result = withTimeoutOrNull(minOf(4_000, remaining)) { loader.execute(request) } as? SuccessResult ?: continue
                    val cover = result.drawable.toBitmap()
                    val other = features(toGray(cover)) ?: continue
                    val inliers = try { inliers(query, other) } finally { other.release() }
                    compared++
                    scores[book.candidateId] = inliers
                }
            } finally { query.release() }
            AppLog.add(context, "Cover: $compared Kandidaten verglichen, ${scores.values.count(CoverConfirmation::confirmed)} bestätigt, ${SystemClock.elapsedRealtime() - started} ms")
            scores
        }

    private class Features(val keypoints: MatOfKeyPoint, val descriptors: Mat) {
        fun release() { keypoints.release(); descriptors.release() }
    }

    private fun toGray(bitmap: Bitmap): Mat {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat(); Utils.bitmapToMat(argb, rgba)
        if (argb !== bitmap) argb.recycle()
        val gray = Mat(); Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY); rgba.release()
        return gray
    }

    /** Height 600, 1500 ORB features, as measured. */
    private fun features(gray: Mat): Features? {
        val scaled = Mat()
        val scale = 600.0 / gray.rows()
        Imgproc.resize(gray, scaled, Size(maxOf(1.0, gray.cols() * scale), 600.0)); gray.release()
        val keypoints = MatOfKeyPoint(); val descriptors = Mat()
        ORB.create(1500).detectAndCompute(scaled, Mat(), keypoints, descriptors); scaled.release()
        if (descriptors.empty() || keypoints.rows() < 8) { keypoints.release(); descriptors.release(); return null }
        return Features(keypoints, descriptors)
    }

    private fun inliers(a: Features, b: Features): Int {
        val pairs = mutableListOf<MatOfDMatch>()
        BFMatcher.create(Core.NORM_HAMMING).knnMatch(a.descriptors, b.descriptors, pairs, 2)
        val good = pairs.mapNotNull { pair -> pair.toArray().takeIf { it.size == 2 && it[0].distance < 0.75f * it[1].distance }?.get(0) }
        pairs.forEach(MatOfDMatch::release)
        if (good.size < 8) return good.size
        val ka = a.keypoints.toArray(); val kb = b.keypoints.toArray()
        val src = MatOfPoint2f(*good.map { ka[it.queryIdx].pt }.toTypedArray())
        val dst = MatOfPoint2f(*good.map { kb[it.trainIdx].pt }.toTypedArray())
        val mask = Mat()
        val homography = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0, mask)
        val count = if (homography.empty()) 0 else Core.countNonZero(mask)
        listOf(src, dst, mask, homography).forEach(Mat::release)
        return count
    }

    /** Finds the largest convex quadrilateral (the book) and straightens it; falls back to the full frame. */
    private fun rectify(frame: Bitmap): Mat {
        val gray = toGray(frame)
        val s = 1000.0 / maxOf(gray.rows(), gray.cols())
        val small = Mat(); Imgproc.resize(gray, small, Size(gray.cols() * s, gray.rows() * s))
        Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
        var best: Pair<Double, Array<Point>>? = null
        for ((lo, hi) in listOf(30.0 to 100.0, 50.0 to 150.0, 75.0 to 200.0)) {
            val edges = Mat(); Imgproc.Canny(small, edges, lo, hi)
            Imgproc.dilate(edges, edges, Mat.ones(3, 3, CvType.CV_8U))
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            edges.release()
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < 0.15 * small.rows() * small.cols()) { contour.release(); continue }
                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
                val points = approx.toArray()
                if (points.size == 4 && Imgproc.isContourConvex(MatOfPoint(*points)) && (best == null || area > best.first))
                    best = area to points.map { Point(it.x / s, it.y / s) }.toTypedArray()
                curve.release(); approx.release(); contour.release()
            }
        }
        small.release()
        val corners = best?.second ?: return gray
        val tl = corners.minBy { it.x + it.y }; val br = corners.maxBy { it.x + it.y }
        val tr = corners.minBy { it.y - it.x }; val bl = corners.maxBy { it.y - it.x }
        val transform = Imgproc.getPerspectiveTransform(MatOfPoint2f(tl, tr, br, bl),
            MatOfPoint2f(Point(0.0, 0.0), Point(340.0, 0.0), Point(340.0, 500.0), Point(0.0, 500.0)))
        val warped = Mat(); Imgproc.warpPerspective(gray, warped, transform, Size(340.0, 500.0))
        gray.release(); transform.release()
        return warped
    }
}
