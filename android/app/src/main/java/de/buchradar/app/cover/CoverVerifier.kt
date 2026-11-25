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
            // The budget starts on entry and covers initialisation, rectification, downloads and matching.
            val started = SystemClock.elapsedRealtime()
            fun remaining() = budgetMillis - (SystemClock.elapsedRealtime() - started)
            if (!ready) return@withContext emptyMap()
            coroutineContext.ensureActive()
            val loader = context.applicationContext.imageLoader
            val query = runCatching { features(rectify(frame)) }.getOrNull() ?: return@withContext emptyMap()
            val scores = linkedMapOf<String, Int>()
            try {
                for (book in candidates.take(maxCandidates)) {
                    coroutineContext.ensureActive()
                    if (remaining() <= 0) break
                    val url = book.coverUrl ?: continue
                    val request = ImageRequest.Builder(context.applicationContext).data(url).allowHardware(false).size(600).build()
                    val result = withTimeoutOrNull(minOf(4_000, remaining())) { loader.execute(request) } as? SuccessResult ?: continue
                    coroutineContext.ensureActive()
                    if (remaining() <= 0) break
                    // One unreadable cover must not discard earlier scores.
                    val inliers = runCatching {
                        val other = features(toGray(result.drawable.toBitmap())) ?: return@runCatching null
                        try { inliers(query, other) } finally { other.release() }
                    }.getOrNull() ?: continue
                    // Work that finished after the deadline is not published.
                    if (remaining() < 0) break
                    scores[book.candidateId] = inliers
                }
            } finally { query.release() }
            AppLog.add(context, "Cover: ${scores.size} Kandidaten verglichen, ${scores.values.count(CoverConfirmation::confirmed)} bestätigt, ${SystemClock.elapsedRealtime() - started} ms")
            scores
        }

    private class Features(val keypoints: MatOfKeyPoint, val descriptors: Mat) {
        fun release() { keypoints.release(); descriptors.release() }
    }

    /** Releases every temporary native matrix created in [block], also on exceptions. */
    private inline fun <T> mats(block: (MutableList<Mat>) -> T): T {
        val owned = mutableListOf<Mat>()
        try { return block(owned) } finally { owned.forEach(Mat::release) }
    }

    private fun toGray(bitmap: Bitmap): Mat {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val gray = Mat()
        try {
            mats { owned ->
                val rgba = Mat().also(owned::add)
                Utils.bitmapToMat(argb, rgba)
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            }
        } catch (error: Exception) { gray.release(); throw error }
        finally { if (argb !== bitmap) argb.recycle() }
        return gray
    }

    /** Height 600 (as measured), width bounded; unusual aspect ratios are skipped. 1500 ORB features. */
    private fun features(gray: Mat): Features? = mats { owned ->
        owned += gray
        if (gray.rows() < 8 || gray.cols() < 8) return@mats null
        val aspect = gray.cols().toDouble() / gray.rows()
        if (aspect !in 0.2..2.0) return@mats null
        val scaled = Mat().also(owned::add)
        Imgproc.resize(gray, scaled, Size(600.0 * aspect, 600.0))
        val keypoints = MatOfKeyPoint(); val descriptors = Mat()
        val mask = Mat().also(owned::add)
        try { ORB.create(1500).detectAndCompute(scaled, mask, keypoints, descriptors) }
        catch (error: Exception) { keypoints.release(); descriptors.release(); throw error }
        if (descriptors.empty() || keypoints.rows() < 8) { keypoints.release(); descriptors.release(); return@mats null }
        Features(keypoints, descriptors)
    }

    private fun inliers(a: Features, b: Features): Int = mats { owned ->
        val pairs = mutableListOf<MatOfDMatch>()
        try {
            BFMatcher.create(Core.NORM_HAMMING).knnMatch(a.descriptors, b.descriptors, pairs, 2)
            val good = pairs.mapNotNull { pair -> pair.toArray().takeIf { it.size == 2 && it[0].distance < 0.75f * it[1].distance }?.get(0) }
            if (good.size < 8) return@mats good.size
            val ka = a.keypoints.toArray(); val kb = b.keypoints.toArray()
            val src = MatOfPoint2f(*good.map { ka[it.queryIdx].pt }.toTypedArray()).also(owned::add)
            val dst = MatOfPoint2f(*good.map { kb[it.trainIdx].pt }.toTypedArray()).also(owned::add)
            val mask = Mat().also(owned::add)
            val homography = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0, mask).also(owned::add)
            if (homography.empty()) 0 else Core.countNonZero(mask)
        } finally { pairs.forEach(MatOfDMatch::release) }
    }

    /**
     * Finds the largest convex quadrilateral (the book) and straightens it to 340×500; falls back to the full
     * frame when no valid, non-degenerate quadrilateral is found.
     */
    private fun rectify(frame: Bitmap): Mat {
        val gray = toGray(frame)
        val corners = try { bookCorners(gray) } catch (error: Exception) { null }
        if (corners == null) return gray
        return mats { owned ->
            owned += gray
            val src = MatOfPoint2f(*corners).also(owned::add)
            val dst = MatOfPoint2f(Point(0.0, 0.0), Point(340.0, 0.0), Point(340.0, 500.0), Point(0.0, 500.0)).also(owned::add)
            val transform = Imgproc.getPerspectiveTransform(src, dst).also(owned::add)
            val warped = Mat()
            Imgproc.warpPerspective(gray, warped, transform, Size(340.0, 500.0))
            warped
        }
    }

    /** Corners ordered top-left, top-right, bottom-right, bottom-left, or null. */
    private fun bookCorners(gray: Mat): Array<Point>? = mats { owned ->
        val s = 1000.0 / maxOf(gray.rows(), gray.cols())
        val small = Mat().also(owned::add)
        Imgproc.resize(gray, small, Size(gray.cols() * s, gray.rows() * s))
        Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
        val kernel = Mat.ones(3, 3, CvType.CV_8U).also(owned::add)
        var best: Pair<Double, List<Point>>? = null
        for ((lo, hi) in listOf(30.0 to 100.0, 50.0 to 150.0, 75.0 to 200.0)) {
            val edges = Mat().also(owned::add); val hierarchy = Mat().also(owned::add)
            Imgproc.Canny(small, edges, lo, hi)
            Imgproc.dilate(edges, edges, kernel)
            val contours = mutableListOf<MatOfPoint>()
            try {
                Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                for (contour in contours) {
                    val area = Imgproc.contourArea(contour)
                    if (area < 0.15 * small.rows() * small.cols()) continue
                    val curve = MatOfPoint2f(*contour.toArray()).also(owned::add)
                    val approx = MatOfPoint2f().also(owned::add)
                    Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
                    val points = approx.toArray()
                    if (points.size != 4) continue
                    val polygon = MatOfPoint(*points).also(owned::add)
                    if (Imgproc.isContourConvex(polygon) && (best == null || area > best.first))
                        best = area to points.map { Point(it.x / s, it.y / s) }
                }
            } finally { contours.forEach(MatOfPoint::release) }
        }
        best?.second?.let(::orderCorners)
    }

    /** Orders four convex corners cyclically from the top-left one; null when degenerate. */
    internal fun orderCorners(points: List<Point>): Array<Point>? {
        if (points.size != 4) return null
        val cx = points.sumOf { it.x } / 4; val cy = points.sumOf { it.y } / 4
        val cyclic = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
        val start = cyclic.indices.minBy { cyclic[it].x + cyclic[it].y }
        val ordered = Array(4) { cyclic[(start + it) % 4] }
        val distinct = ordered.indices.all { i -> ordered.indices.all { j -> i == j ||
            kotlin.math.hypot(ordered[i].x - ordered[j].x, ordered[i].y - ordered[j].y) > 8.0 } }
        val area = kotlin.math.abs((0 until 4).sumOf { i -> val p = ordered[i]; val q = ordered[(i + 1) % 4]; p.x * q.y - q.x * p.y }) / 2
        return ordered.takeIf { distinct && area > 64.0 }
    }
}
