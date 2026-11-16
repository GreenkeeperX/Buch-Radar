package de.buchradar.app

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BookListScrollTest {
    @get:Rule val ui = createComposeRule()

    @Test fun savedBookScrollsFromBackgroundRequestAfterDatabaseListArrives() {
        val books = mutableStateOf((1..40).map { "Buch $it" })
        val request = mutableStateOf<String?>(null)
        val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            ui.setContent {
                val state = rememberLazyListState()
                BookListScrollEffect(state, request.value, books.value, true) { request.value = null }
                LazyColumn(state = state, modifier = Modifier.testTag("books")) {
                    items(books.value, key = { it }) { Text(it, Modifier.height(100.dp)) }
                }
            }
            // Window attachment can lag behind setContent on a cold Android start.
            ui.waitUntil(10_000) {
                ui.onAllNodesWithTag("books").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            ui.onNodeWithTag("books").performScrollToIndex(30)
            // Like viewModelScope: this coroutine intentionally has no Compose frame clock.
            ui.runOnIdle { work.launch { request.value = "Neues Buch" } }
            ui.runOnIdle {
                assertEquals("Neues Buch", request.value)
                books.value = listOf("Neues Buch") + books.value
            }
            ui.waitForIdle()
            ui.onNodeWithText("Neues Buch").assertIsDisplayed()
            ui.runOnIdle { assertNull(request.value) }
        } finally { work.cancel() }
    }

    @Test fun scanResultWaitsUntilBookListIsVisible() {
        val scanning = mutableStateOf(true)
        val request = mutableStateOf<String?>("Gescanntes Buch")
        ui.setContent {
            val state = rememberLazyListState()
            BookListScrollEffect(state, request.value, listOf("Gescanntes Buch"), !scanning.value) { request.value = null }
            if (scanning.value) Text("Kamera") else LazyColumn(state = state) {
                item { Text("Gescanntes Buch") }
            }
        }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Kamera").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        ui.runOnIdle { assertNotNull(request.value); scanning.value = false }
        ui.waitForIdle()
        ui.onNodeWithText("Gescanntes Buch").assertIsDisplayed()
        ui.runOnIdle { assertNull(request.value) }
    }
}
