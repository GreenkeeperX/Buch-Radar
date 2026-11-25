package de.buchradar.app

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.scanner.BookScannerView
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Exercises real camera binding and cleanup without inserting books or saving images. */
class ScannerLifecycleTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun cameraCanAnalyzeCloseAndOpenAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        ui.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val open = mutableStateOf(true)
        val delivered = mutableStateOf(false)
        ui.setContent {
            if (open.value) BookScannerView(onBarcode = { delivered.value = true },
                onTextConfirmed = { delivered.value = true },
                onShelfConfirmed = { _, _ -> delivered.value = true },
                onClose = { open.value = false }) else Text("Testliste")
        }
        repeat(2) {
            ui.waitUntil(15_000) {
                ui.onAllNodesWithContentDescription("Kamera schließen").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            ui.waitUntil(20_000) {
                delivered.value || ui.onAllNodesWithText("Buch ins Bild halten").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            }
            ui.onNodeWithText("Kamera konnte nicht gestartet werden").assertDoesNotExist()
            ui.onNodeWithText("Erkennung unterbrochen – bitte erneut ausrichten").assertDoesNotExist()
            ui.onNodeWithContentDescription("Kamera schließen").performClick()
            ui.onNodeWithText("Testliste").assertIsDisplayed()
            if (it == 0) ui.runOnIdle { delivered.value = false; open.value = true }
        }
    }
}
