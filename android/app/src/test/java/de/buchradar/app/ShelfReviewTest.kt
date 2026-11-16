package de.buchradar.app

import de.buchradar.app.network.CatalogBook
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfReviewTest {
    private class MemoryStore(var saved: ShelfReviewState = ShelfReviewState()) : ShelfReviewStore {
        var failWrites = false
        var writeDelay = 0L
        override suspend fun read() = saved
        override suspend fun write(state: ShelfReviewState) {
            delay(writeDelay)
            check(!failWrites)
            saved = state
        }
    }
    private fun book(text: String) = CatalogBook(text, "Autor", null)
    private fun TestScope.work() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    @Test fun pendingSearchSurvivesControllerRecreation() = runTest {
        val store = MemoryStore()
        val firstScope = work()
        val first = ShelfReviewController(firstScope, store) { _, _ -> awaitCancellation() }
        first.append(listOf("Buch A")); runCurrent()
        first.pause(); firstScope.cancel(); runCurrent()
        val secondScope = work()
        try {
            val restored = ShelfReviewController(secondScope, store) { text, _ -> listOf(book(text)) }
            runCurrent()
            assertTrue(restored.state.value.groups.single().pending)
            assertFalse(restored.busy.value)
            restored.resume(); advanceUntilIdle()
            assertEquals("Buch A", restored.state.value.groups.single().candidates.single().title)
            assertFalse(store.saved.groups.single().pending)
        } finally { secondScope.cancel() }
    }

    @Test fun correctionRejectsLateResultAndContinuesOtherBooks() = runTest {
        val store = MemoryStore()
        val scope = work()
        val calls = mutableListOf<String>()
        try {
            val review = ShelfReviewController(scope, store) { text, _ ->
                calls += text
                if (text == "Falsch") withContext(NonCancellable) { delay(1_000) }
                listOf(book(text))
            }
            review.append(listOf("Falsch", "Zweites Buch")); runCurrent()
            val id = review.state.value.groups.first().id
            review.correct(id, "Richtig"); advanceUntilIdle()
            assertEquals(listOf("Falsch", "Richtig", "Zweites Buch"), calls)
            assertEquals("Richtig", store.saved.groups.first { it.id == id }.candidates.single().title)
            assertTrue(store.saved.groups.none { it.pending })
        } finally { scope.cancel() }
    }

    @Test fun postponingKeepsResultsForLater() = runTest {
        val store = MemoryStore()
        val scope = work()
        var calls = 0
        try {
            val review = ShelfReviewController(scope, store) { text, _ -> calls++; listOf(book(text)) }
            review.append(listOf("Buch")); advanceUntilIdle()
            val id = review.state.value.groups.single().id
            review.postpone(id); advanceUntilIdle()
            assertTrue(store.saved.groups.single().postponed)
            review.pause(); review.resume(); advanceUntilIdle()
            assertEquals(1, calls)
            review.restorePostponed(); advanceUntilIdle()
            assertFalse(store.saved.groups.single().postponed)
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun confirmationRemovesOnlyOneOfTwoIdenticalTextFinds() = runTest {
        val store = MemoryStore()
        val scope = work()
        try {
            val review = ShelfReviewController(scope, store) { text, _ -> listOf(book(text)) }
            review.append(listOf("Gleicher Text", "Gleicher Text")); advanceUntilIdle()
            val secondId = review.state.value.groups.last().id
            review.complete(review.state.value.groups.first().id); advanceUntilIdle()
            assertEquals(secondId, store.saved.groups.single().id)
            review.append(listOf("Weiterer Scan")); advanceUntilIdle()
            assertEquals(2, store.saved.groups.size)
            assertEquals(3, store.saved.totalCount)
        } finally { scope.cancel() }
    }

    @Test fun oneNetworkErrorDoesNotStopRemainingSearches() = runTest {
        val store = MemoryStore()
        val scope = work()
        var fail = true
        try {
            val review = ShelfReviewController(scope, store) { text, _ ->
                if (text == "Erstes" && fail) error("Offline")
                listOf(book(text))
            }
            review.append(listOf("Erstes", "Zweites")); advanceUntilIdle()
            assertNotNull(store.saved.groups.first().error)
            assertEquals("Zweites", store.saved.groups.last().candidates.single().title)
            fail = false
            review.retryFailures(); advanceUntilIdle()
            assertNull(store.saved.groups.first().error)
            assertEquals("Erstes", store.saved.groups.first().candidates.single().title)
        } finally { scope.cancel() }
    }

    @Test fun failedCommitKeepsFindInMemoryAndOnDisk() = runTest {
        val store = MemoryStore()
        val scope = work()
        try {
            val review = ShelfReviewController(scope, store) { text, _ -> listOf(book(text)) }
            review.append(listOf("Buch")); advanceUntilIdle()
            val before = store.saved
            store.failWrites = true
            review.complete(before.groups.single().id); advanceUntilIdle()
            assertEquals(before, store.saved)
            assertEquals(before, review.state.value)
            assertNotNull(review.error.value)
        } finally { scope.cancel() }
    }

    @Test fun leavingWhileSavingDoesNotRestartHiddenSearch() = runTest {
        val store = MemoryStore().apply { writeDelay = 100 }
        val scope = work()
        var calls = 0
        try {
            val review = ShelfReviewController(scope, store) { text, _ -> calls++; listOf(book(text)) }
            review.append(listOf("Buch")); runCurrent()
            review.pause(); advanceUntilIdle()
            assertEquals(0, calls)
            assertTrue(store.saved.groups.single().pending)
            review.resume(); advanceUntilIdle()
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun unreadableReviewRequiresExplicitDiscardBeforeReplacement() = runTest {
        val scope = work()
        var broken = true
        var saved = ShelfReviewState()
        val store = object : ShelfReviewStore {
            override suspend fun read(): ShelfReviewState { check(!broken); return saved }
            override suspend fun write(state: ShelfReviewState) { saved = state; broken = false }
        }
        try {
            val review = ShelfReviewController(scope, store) { text, _ -> listOf(book(text)) }
            review.append(listOf("Neuer Scan")); advanceUntilIdle()
            assertTrue(broken)
            assertNotNull(review.error.value)
            review.discardAll(); advanceUntilIdle()
            assertFalse(broken)
            assertNull(review.error.value)
            review.append(listOf("Neuer Scan")); advanceUntilIdle()
            assertEquals("Neuer Scan", saved.groups.single().query)
        } finally { scope.cancel() }
    }
}
