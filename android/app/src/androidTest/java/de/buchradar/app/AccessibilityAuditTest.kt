package de.buchradar.app

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
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
 * books, which are removed afterwards. The system font size is restored in any case.
 *
 * Checks per visible node, as Android's accessibility guidance describes them:
 * touch targets of at least 48×48 dp, a spoken label for every actionable element,
 * estimated text contrast from a screenshot (4.5:1, large text 3:1) and elements cut
 * off at the window edge. Results and screenshots stay in the test app's cache.
 */
class AccessibilityAuditTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation: UiAutomation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val out = File(context.cacheDir, "a11y-audit").apply { mkdirs() }

    private val seeded = listOf(
        BookEntity("9780140328721", "Testbuch Barrierefreiheit Eins", "Erika Muster", 1999,
            momoxStatus = "AVAILABLE", currentPriceCents = 350, lastCheckedAt = System.currentTimeMillis(), id = "a11y-test-1"),
        BookEntity("9780804429573", "Testbuch mit einem recht langen Titel für Zeilenumbrüche", "Max Beispiel", 2005,
            momoxStatus = "NOT_AVAILABLE", lastCheckedAt = System.currentTimeMillis(), id = "a11y-test-2"),
        BookEntity("9783125171541", "Testbuch Drei", "Anna Probe", 2012,
            lastAttemptStatus = "ERROR", lastAttemptAt = System.currentTimeMillis(), id = "a11y-test-3")
    )

    /** The font size before the run, kept on disk so an aborted run is repaired by the next one or by @After. */
    private val savedScale = File(out, "font-scale-before.txt")

    @Before fun repairAbortedRun() { restoreFontScale() }

    @After fun restoreFontScale() {
        if (!savedScale.exists()) return
        shell("settings put system font_scale ${savedScale.readText().trim()}")
        savedScale.delete()
    }

    @Test fun auditScreensAtSeveralFontSizes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("a11yAudit") == "true")
        val originalScale = shell("settings get system font_scale").trim().takeIf { it.matches(Regex("[0-9.]+")) } ?: "1.0"
        savedScale.writeText(originalScale)
        val dao = BuchRadarDatabase.get(context).bookDao()
        val inserted = runBlocking { seeded.filter { dao.insert(it) != -1L }.map { it.id } }
        val report = JSONArray()
        try {
            for (scale in listOf("1.0", "1.3", "2.0")) {
                shell("settings put system font_scale $scale")
                SystemClock.sleep(1_500)
                ActivityScenario.launch(MainActivity::class.java).use {
                    showList()
                    waitFor { node { n -> n.text?.toString() == seeded[0].title } }
                    report.put(audit("liste", scale))
                    click { n -> n.text?.toString() == seeded[0].title }
                    waitFor { node { n -> n.contentDescription?.toString() == "Weitere Aktionen" } }
                    report.put(audit("details", scale))
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } }
                    click { n -> n.contentDescription?.toString() == "Einstellungen öffnen" }
                    waitFor { node { n -> n.text?.toString() == "Einstellungen" } }
                    report.put(audit("einstellungen", scale))
                }
            }
        } finally {
            restoreFontScale()
            runBlocking { inserted.forEach { dao.deleteBook(it) } }
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
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            SystemClock.sleep(800)
        }
        waitFor { node { n -> n.contentDescription?.toString() == "Einstellungen öffnen" } }
    }

    private fun audit(screen: String, scale: String): JSONObject {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(800)
        val shot = automation.takeScreenshot()
        val root = requireNotNull(automation.rootInActiveWindow) { "Kein aktives Fenster" }
        val density = context.resources.displayMetrics.density
        val window = Rect().also { root.getBoundsInScreen(it) }
        val issues = JSONArray()
        fun issue(kind: String, node: AccessibilityNodeInfo, bounds: Rect, detail: String) {
            issues.put(JSONObject().put("kind", kind).put("label", label(node).take(80)).put("detail", detail)
                .put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"))
        }
        walk(root) { node ->
            if (!node.isVisibleToUser) return@walk
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.isEmpty) return@walk
            val actionable = node.isClickable || node.isLongClickable || node.isCheckable ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            if (actionable) {
                val w = bounds.width() / density; val h = bounds.height() / density
                if (w < 47.5f || h < 47.5f) issue("tippflaeche", node, bounds, "%.0f×%.0f dp".format(w, h))
                if (label(node).isBlank() && !hasLabelledChild(node)) issue("ohne_beschriftung", node, bounds, node.className?.toString().orEmpty())
            }
            if (bounds.right > window.right + 1 || bounds.left < window.left - 1)
                issue("abgeschnitten", node, bounds, "über den Fensterrand")
            val text = node.text?.toString().orEmpty()
            if (text.isNotBlank() && shot != null) {
                val ratio = contrast(shot, bounds)
                val large = bounds.height() / density >= 32f
                if (ratio != null && ratio < (if (large) 3.0 else 4.5)) issue("kontrast", node, bounds, "≈%.1f:1".format(ratio))
            }
        }
        shot?.let { image ->
            File(out, "$screen-$scale.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        return JSONObject().put("screen", screen).put("fontScale", scale).put("issues", issues)
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

    private fun click(match: (AccessibilityNodeInfo) -> Boolean) {
        var target = node(match) ?: error("Element nicht gefunden")
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

    private fun shell(command: String): String =
        automation.executeShellCommand(command).let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }

    /**
     * Estimated contrast of text against its background: the most frequent colour in the
     * element is taken as background, the 10 % of pixels that differ most from it as text.
     */
    private fun contrast(shot: Bitmap, bounds: Rect): Double? {
        val area = Rect(bounds).apply { intersect(0, 0, shot.width, shot.height) }
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
