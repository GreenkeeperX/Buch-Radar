package de.buchradar.app

import android.app.UiAutomation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import de.buchradar.app.scanner.ScannerLayout
import de.buchradar.app.scanner.ScannerMode
import de.buchradar.app.ui.BuchRadarTheme
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.data.AtomicShelfReviewStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Opt-in accessibility measurement of the real screens at several system font sizes
 * (`-e a11yAudit true`). Runs only in the separate deviceTest app with three synthetic
 * books, which are removed afterwards (also leftovers of an aborted run).
 *
 * The font size is a system setting. Its value before the run is kept in a settings key of its
 * own, outside the test app, so a crashed or uninstalled test app cannot lose it; the next run
 * or the host script restores it, checks the result and only then drops the backup. Run it with
 * the host-side save and restore from the device test skill as a second guard.
 *
 * Checks per visible node, as Android's accessibility guidance describes them:
 * touch targets of at least 48×48 dp, a spoken label for every actionable element,
 * estimated text contrast from a screenshot (4.5:1, large text 3:1) and elements cut
 * off at the window edge. Results and screenshots stay in the test app's cache.
 * The scanner panel uses fixed text and an empty preview in the app window.
 */
class AccessibilityAuditTest {
    private companion object { const val BACKUP_KEY = "buchradar_a11y_font_scale_backup" }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation: UiAutomation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "de.buchradar.app.devicetest")
    }
    private val out by lazy { File(context.cacheDir, "a11y-audit").apply { mkdirs() } }

    private val seeded = listOf(
        BookEntity("9780140328721", "Testbuch Barrierefreiheit Eins", "Erika Muster", 1999,
            momoxStatus = "AVAILABLE", currentPriceCents = 350, lastCheckedAt = System.currentTimeMillis(), id = "a11y-test-1"),
        BookEntity("9780804429573", "Testbuch mit einem recht langen Titel für Zeilenumbrüche", "Max Beispiel", 2005,
            momoxStatus = "NOT_AVAILABLE", lastCheckedAt = System.currentTimeMillis(), id = "a11y-test-2"),
        BookEntity("9783125171541", "Testbuch Drei", "Anna Probe", 2012,
            lastAttemptStatus = "ERROR", lastAttemptAt = System.currentTimeMillis(), id = "a11y-test-3")
    )

    private val optedIn get() = InstrumentationRegistry.getArguments().getString("a11yAudit") == "true"

    @Before fun repairAbortedRun() { if (optedIn) restoreFontScale() }

    @After fun afterRun() { if (optedIn) restoreFontScale() }

    /** Restores the saved font size ("null" = the setting did not exist) and keeps the backup unless the restore is confirmed. */
    private fun restoreFontScale() {
        val saved = shell("settings get global $BACKUP_KEY").trim()
        if (saved.isEmpty() || saved == "null") return
        val original = saved.removePrefix("v:")
        if (original == "unset") shell("settings delete system font_scale") else shell("settings put system font_scale $original")
        val now = shell("settings get system font_scale").trim()
        check(if (original == "unset") now == "null" || now == "1.0" else now == original) { "Schriftgröße nicht wiederhergestellt" }
        shell("settings delete global $BACKUP_KEY")
    }

    @Test fun auditScreensAtSeveralFontSizes() {
        assumeTrue(optedIn)
        val current = shell("settings get system font_scale").trim()
        val original = when {
            current.matches(Regex("[0-9]+(\\.[0-9]+)?")) -> current
            current == "null" || current.isEmpty() -> "unset"
            else -> error("Unerwartete Schriftgröße, Lauf abgebrochen")
        }
        shell("settings put global $BACKUP_KEY v:$original")
        check(shell("settings get global $BACKUP_KEY").trim() == "v:$original") { "Sicherung der Schriftgröße fehlgeschlagen" }
        val dao = BuchRadarDatabase.get(context).bookDao()
        val report = JSONArray()
        val scales = listOf("1.0", "1.3", "2.0")
        val screens = setOf("liste", "suche-ime", "suche-ime-aktionen", "liste-buch", "details", "verkaufen", "einstellungen", "kamera")
        val shelfStore = AtomicShelfReviewStore(File(context.noBackupFilesDir, "shelf-review.json"))
        val shelfBefore = runBlocking { shelfStore.read() }
        val addShelfFixture = shelfBefore.groups.isEmpty()
        try {
            if (addShelfFixture) runBlocking {
                shelfStore.write(ShelfReviewState(listOf(ShelfLookupGroup("a11y-regal", "Testfund", pending = false)), 1))
            }
            for (scale in scales) {
                val oldCapture = File(out, "kamera-$scale.png")
                check(!oldCapture.exists() || oldCapture.delete())
            }
            // Leftovers of an aborted run go first, so the seeded books are fresh.
            runBlocking { seeded.forEach { dao.deleteBook(it.id) }; seeded.forEach { dao.insert(it) } }
            for (scale in scales) {
                shell("settings put system font_scale $scale")
                SystemClock.sleep(1_500)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { check(it.resources.configuration.fontScale == scale.toFloat()) }
                    showList()
                    waitFor { node { n -> n.text?.toString() == "Fortsetzen" } }
                    report.put(audit("liste", scale, scenario))
                    click { it.isEditable }
                    val title = "Testbuch"
                    val field = requireNotNull(node { it.isEditable })
                    check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title)
                    }))
                    waitFor { node { it.isEditable && it.text?.toString()?.contains(title) == true } }
                    waitFor {
                        var visible = false
                        scenario.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                        Unit.takeIf { visible }
                    }
                    check(node { it.text?.toString() == "Fortsetzen" } == null)
                    waitFor { node { it.text?.toString()?.endsWith(" Treffer") == true } }
                    report.put(audit("suche-ime", scale, scenario))
                    val fieldBounds = Rect().also { bounds -> requireNotNull(node { it.isEditable }).getBoundsInScreen(bounds) }
                    scrollTo(within = fieldBounds) { it.text?.toString() == "Im Katalog suchen" }
                    scrollTo(within = fieldBounds) { it.contentDescription?.toString() == "Buch von Hand eintragen" }
                    scrollTo(within = fieldBounds) { it.contentDescription?.toString() == "Kamera öffnen" }
                    report.put(audit("suche-ime-aktionen", scale, scenario))
                    scrollTo(forward = false, within = fieldBounds) { it.isEditable }
                    pressBack()
                    // Without the keyboard the bounded search area can rest scrolled; bring the clear button back.
                    scrollTo(forward = false) { it.contentDescription?.toString() == "Eingabe löschen" }
                    click { it.contentDescription?.toString() == "Eingabe löschen" }
                    // With large fonts the header may fill the screen; the first book must be reachable by scrolling.
                    scrollTo { n -> n.text?.toString() == seeded[0].title }
                    report.put(audit("liste-buch", scale, scenario))
                    // The list may have moved while the audit ran; find the first book again from wherever it rests.
                    if (node { n -> n.text?.toString() == seeded[0].title } == null)
                        runCatching { scrollTo(forward = false) { n -> n.text?.toString() == seeded[0].title } }
                            .onFailure { runCatching { scrollTo { n -> n.text?.toString() == seeded[0].title } }.onFailure { error ->
                                val texts = mutableListOf<String>()
                                automation.rootInActiveWindow?.let { root -> walk(root) { n -> (n.text ?: n.contentDescription)?.let { t -> texts += "${n.className}|${n.isVisibleToUser}|$t" } } }
                                File(out, "debug.txt").writeText(texts.joinToString("\n")); throw error } }
                    click { n -> n.text?.toString() == seeded[0].title }
                    waitFor { node { n -> n.contentDescription?.toString() == "Weitere Aktionen" } }
                    report.put(audit("details", scale, scenario))
                    pressBack()
                    waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } }
                    // The sale screen, reached from the overview's "Verkaufen" (seeded book 1 has an offer).
                    scrollTo(forward = false) { n -> n.text?.toString() == "Verkaufen" }
                    click { n -> n.text?.toString() == "Verkaufen" }
                    waitFor { node { n -> n.contentDescription?.toString() == "Zurück zur Liste" } }
                    val saleIsbn = BookInputFormat.displayIsbn(seeded[0].isbn)
                    scrollTo { it.text?.contains(saleIsbn) == true }
                    report.put(audit("verkaufen", scale, scenario))
                    pressBack()
                    waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } }
                    click { n -> n.contentDescription?.toString() == "Einstellungen öffnen" }
                    waitFor { node { n -> n.text?.toString() == "Einstellungen" } }
                    report.put(audit("einstellungen", scale, scenario))

                    // Use exactly the production layout. No CameraX, OCR or lookup is attached to its controls.
                    var panel: Rect? = null
                    scenario.onActivity { activity ->
                        activity.setContent {
                            BuchRadarTheme(darkTheme = false) {
                                ScannerLayout(ScannerMode.SINGLE_BOOK, correcting = false, hint = "Text erkannt",
                                    observed = "Testbuch Barrierefreiheit Eins\nErika Muster\nRoman · Verlag", shelf = false, finalizing = false,
                                    onConfirm = {}, onModeChange = {}, onClose = {},
                                    panelModifier = Modifier.onGloballyPositioned {
                                        val r = it.boundsInWindow()
                                        panel = Rect(ceil(r.left).toInt(), ceil(r.top).toInt(), floor(r.right).toInt(), floor(r.bottom).toInt())
                                    }, preview = {})
                            }
                        }
                    }
                    waitFor { node { it.text?.toString() == "Text erkannt" } }
                    scrollTo { it.contentDescription?.toString() == "Kamera schließen" }
                    report.put(audit("kamera", scale, scenario) { panel })
                }
            }
            val measured = (0 until report.length()).map { index ->
                val entry = report.getJSONObject(index)
                entry.getString("screen") to entry.getString("fontScale")
            }
            check(measured.size == screens.size * scales.size && measured.toSet() == scales.flatMap { scale -> screens.map { it to scale } }.toSet())
        } finally {
            restoreFontScale()
            runBlocking { seeded.forEach { dao.deleteBook(it.id) }; if (addShelfFixture) shelfStore.write(shelfBefore) }
            File(out, "report.json").writeText(report.toString(2))
        }
    }

    /**
     * The app restores a screen that was open before (e.g. settings); go back to the list first.
     * Back is only pressed on a recognised inner screen, never on the list itself.
     */
    private fun showList() {
        val inner = setOf("Zurück", "Zurück zur Liste", "Weitere Aktionen")
        repeat(5) {
            waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" || n.contentDescription?.toString() in inner } }
            if (node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } != null) return
            pressBack()
            SystemClock.sleep(800)
        }
        waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } }
    }

    /** Window root of the app under test; any other app in front ends the run instead of being driven or photographed. */
    private fun ownRoot(): AccessibilityNodeInfo {
        val root = requireNotNull(automation.rootInActiveWindow) { "Kein aktives Fenster" }
        check(root.packageName?.toString() == context.packageName) { "Fremdes Fenster im Vordergrund, Lauf abgebrochen" }
        return root
    }

    private fun pressBack() { ownRoot(); instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK) }

    private fun audit(screen: String, scale: String, scenario: ActivityScenario<MainActivity>,
                      panelInWindow: () -> Rect? = { null }): JSONObject {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(800)
        val root = ownRoot()
        val density = context.resources.displayMetrics.density
        val window = Rect().also { root.getBoundsInScreen(it) }
        val safeBounds = Rect()
        val origin = IntArray(2)
        var shot: Bitmap? = null
        val ready = CountDownLatch(1)
        var copyResult = PixelCopy.ERROR_SOURCE_NO_DATA
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
            if (screen.startsWith("suche-ime")) check(insets.isVisible(WindowInsetsCompat.Type.ime()))
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val location = IntArray(2).also { decor.getLocationInWindow(it) }
            val safe = Rect(location[0] + inset.left, location[1] + inset.top,
                location[0] + decor.width - inset.right, location[1] + decor.height - inset.bottom)
            decor.getLocationOnScreen(origin)
            origin[0] -= location[0]
            origin[1] -= location[1]
            safeBounds.set(safe)
            safeBounds.offset(origin[0], origin[1])
            val crop = if (screen == "kamera") panelInWindow()?.takeIf { !it.isEmpty && safe.contains(it) } else safe
            if (crop != null && !crop.isEmpty) {
                val bitmap = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
                shot = bitmap
                origin[0] += crop.left
                origin[1] += crop.top
                PixelCopy.request(activity.window, Rect(crop), bitmap, { result ->
                    copyResult = result
                    ready.countDown()
                }, Handler(Looper.getMainLooper()))
            }
        }
        val image = shot
        if (image != null) check(ready.await(5, TimeUnit.SECONDS) && copyResult == PixelCopy.SUCCESS) { "Fensteraufnahme fehlgeschlagen" }
        val issues = JSONArray()
        fun issue(kind: String, node: AccessibilityNodeInfo, bounds: Rect, detail: String) {
            issues.put(JSONObject().put("kind", kind).put("label", label(node).take(80)).put("detail", detail)
                .put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"))
        }
        walk(root) { node ->
            if (!node.isVisibleToUser) return@walk
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.isEmpty) return@walk
            // A row cut by the edge of its scrolling list is half scrolled out, not too small or faint;
            // it is checked whole once scrolled into view.
            if (cutByScrollEdge(node, bounds)) return@walk
            val actionable = node.isClickable || node.isLongClickable || node.isCheckable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            if (actionable) {
                if (screen == "kamera" && !safeBounds.contains(bounds))
                    issue("systemleisten", node, bounds, "Tippfläche außerhalb des sicheren Fensterbereichs")
                val w = bounds.width() / density; val h = bounds.height() / density
                if (w < 47.5f || h < 47.5f) issue("tippflaeche", node, bounds, "%.0f×%.0f dp".format(w, h))
                if (label(node).isBlank() && !hasLabelledChild(node)) issue("ohne_beschriftung", node, bounds, node.className?.toString().orEmpty())
            }
            if (bounds.right > window.right + 1 || bounds.left < window.left - 1)
                issue("abgeschnitten", node, bounds, "über den Fensterrand")
            // Text that runs past the bottom (or top) of the window and is not inside a scrolling list.
            if ((bounds.bottom > window.bottom + 1 || bounds.top < window.top - 1) && !insideScrollable(node))
                issue("abgeschnitten", node, bounds, "über den oberen oder unteren Fensterrand")
            val text = node.text?.toString().orEmpty()
            if (text.isNotBlank() && image != null) {
                val ratio = contrast(image, Rect(bounds).apply { offset(-origin[0], -origin[1]) })
                val large = isLargeText(node, density)
                if (ratio != null && ratio < (if (large) 3.0 else 4.5)) issue("kontrast", node, bounds, "≈%.1f:1".format(ratio))
            }
        }
        val file = File(out, "$screen-$scale.png")
        if (image == null) {
            check(screen == "kamera")
            check(!file.exists() || file.delete())
        } else try {
            file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
        return JSONObject().put("screen", screen).put("fontScale", scale).put("issues", issues)
            .put("screenshot", if (image == null) "Bedienfeld nicht sicher begrenzt" else file.name)
    }

    /**
     * Large text (3:1 suffices) from the rendered text size where Android reports it: at least
     * 18 pt, i.e. 24 dp after font scaling. Without that information the stricter 4.5:1 applies.
     */
    private fun isLargeText(node: AccessibilityNodeInfo, density: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return false
        return runCatching {
            node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY, android.os.Bundle())
            val px = node.extraRenderingInfo?.textSizeInPx ?: return false
            px / density >= 24f
        }.getOrDefault(false)
    }

    private fun cutByScrollEdge(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        var current = node.parent
        while (current != null) {
            val ancestor: AccessibilityNodeInfo = current
            if (ancestor.isScrollable) {
                val list = Rect().also { ancestor.getBoundsInScreen(it) }
                return kotlin.math.abs(bounds.bottom - list.bottom) <= 1 || kotlin.math.abs(bounds.top - list.top) <= 1
            }
            current = current.parent
        }
        return false
    }

    private fun insideScrollable(node: AccessibilityNodeInfo): Boolean {
        var current = node.parent
        while (current != null) { if (current.isScrollable) return true; current = current.parent }
        return false
    }

    private fun label(node: AccessibilityNodeInfo) =
        listOfNotNull(node.contentDescription, node.text, if (android.os.Build.VERSION.SDK_INT >= 30) node.stateDescription else null).joinToString(" ").trim()

    private fun hasLabelledChild(node: AccessibilityNodeInfo): Boolean = (0 until node.childCount).any { index ->
        node.getChild(index)?.let { child -> label(child).isNotBlank() || hasLabelledChild(child) } == true
    }

    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (index in 0 until node.childCount) node.getChild(index)?.let { walk(it, visit) }
    }

    private fun node(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        automation.rootInActiveWindow?.let { root -> walk(root) { if (found == null && it.isVisibleToUser && match(it)) found = it } }
        return found
    }

    private fun scrollTo(forward: Boolean = true, within: Rect? = null, match: (AccessibilityNodeInfo) -> Boolean) {
        repeat(8) {
            if (node(match) != null) return
            var largest: AccessibilityNodeInfo? = null
            var largestArea = 0
            automation.rootInActiveWindow?.let { root ->
                walk(root) { n ->
                    if (n.isScrollable && n.isVisibleToUser) {
                        val r = Rect().also { n.getBoundsInScreen(it) }
                        if (within != null && !r.contains(within.centerX(), within.centerY())) return@walk
                        if (r.width() * r.height() > largestArea) { largest = n; largestArea = r.width() * r.height() }
                    }
                }
            }
            largest?.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: SystemClock.sleep(250)
            SystemClock.sleep(600)
        }
        waitFor { node(match) }
    }

    private fun click(match: (AccessibilityNodeInfo) -> Boolean) {
        // The tree can be rebuilt while a list settles after scrolling; wait briefly instead of failing.
        val end = SystemClock.elapsedRealtime() + 5_000
        var found = node(match)
        while (found == null && SystemClock.elapsedRealtime() < end) { SystemClock.sleep(200); found = node(match) }
        var target = found ?: error("Element nicht gefunden")
        check(target.packageName?.toString() == context.packageName) { "Element gehört zu einer fremden App" }
        while (!target.isClickable) target = target.parent ?: break
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun waitFor(timeoutMillis: Long = 15_000, condition: () -> Any?) {
        val end = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < end) {
            if (condition() != null) return
            SystemClock.sleep(250)
        }
        error("Zeitüberschreitung beim Warten auf die Ansicht")
    }

    private fun shell(command: String): String {
        check(context.packageName == "de.buchradar.app.devicetest")
        return automation.executeShellCommand(command).let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
    }

    /**
     * Estimated contrast of text against its background: the most frequent colour in the
     * element is taken as background, the 10 % of pixels that differ most from it as text.
     */
    private fun contrast(shot: Bitmap, bounds: Rect): Double? {
        val area = Rect(bounds)
        if (!area.intersect(0, 0, shot.width, shot.height)) return null
        if (area.width() < 4 || area.height() < 4) return null
        val step = max(1, min(area.width(), area.height()) / 40)
        val luminances = mutableListOf<Double>()
        val counts = HashMap<Int, Int>()
        var y = area.top
        while (y < area.bottom) {
            var x = area.left
            while (x < area.right) {
                val color = shot.getPixel(x, y)
                counts.merge(color and 0xF0F0F0, 1, Int::plus)
                luminances += luminance(color)
                x += step
            }
            y += step
        }
        val background = luminance((counts.maxByOrNull { it.value } ?: return null).key or 0xFF080808.toInt())
        val text = luminances.sortedByDescending { kotlin.math.abs(it - background) }
            .take(max(1, luminances.size / 10)).average()
        return (max(text, background) + .05) / (min(text, background) + .05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double = (value / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * channel(Color.red(color)) + .7152 * channel(Color.green(color)) + .0722 * channel(Color.blue(color))
    }
}
