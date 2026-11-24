package de.buchradar.app

import de.buchradar.app.network.CatalogAccess
import de.buchradar.app.network.CatalogUnavailable
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CatalogAccessTest {
    private fun source() = "Test-${UUID.randomUUID()}"

    @Test fun totalBudgetKeepsEarlyMatchesAndSkipsLaterNetworkCalls() = runBlocking {
        var lateCalls = 0
        val result = CatalogAccess.report(budgetMillis = 200) {
            val first = CatalogAccess.fetch(source(), "first") { listOf("Treffer") }.orEmpty()
            CatalogAccess.search {
                CatalogAccess.fetch<String>(source(), "slow") { awaitCancellation() }
                CatalogAccess.fetch(source(), "late") { lateCalls++; "Zu spät" }
            }
            first
        }
        assertEquals(listOf("Treffer"), result.value)
        assertEquals(0, lateCalls)
        assertTrue(result.warnings.any { it.contains("Zeitlimit") })
    }

    @Test fun waitingForSourceGateAlsoConsumesTotalBudget() = runBlocking {
        val provider = source()
        val started = CompletableDeferred<Unit>()
        val held = launch {
            CatalogAccess.report {
                CatalogAccess.fetch<Unit>(provider, "held") { started.complete(Unit); awaitCancellation() }
            }
        }
        started.await()
        try {
            var called = false
            val result = CatalogAccess.report(budgetMillis = 150) {
                CatalogAccess.fetch(source(), "working") { "Treffer" }
                CatalogAccess.fetch(provider, "waiting") { called = true; "Nicht erreichbar" }
            }
            assertFalse(called)
            assertNull(result.value)
            assertTrue(result.warnings.any { it.contains("Zeitlimit") })
        } finally { held.cancelAndJoin() }
    }

    @Test fun partialFailureIsReportedButAllFailedIsAnError() = runBlocking {
        val working = source()
        val broken = source()
        val result = CatalogAccess.report {
            CatalogAccess.fetch(working, "ok") { "Treffer" }
            CatalogAccess.fetch<String>(broken, "broken") { error("offline") }
        }
        assertTrue(result.warnings.any { it.contains(broken) })
        try {
            CatalogAccess.report { CatalogAccess.fetch<String>(source(), "allFailed") { error("offline") } }
            fail("All failed sources must not look like an empty successful search")
        } catch (_: CatalogUnavailable) { }
    }

    @Test fun callerCancellationIsNotConvertedToMissingResults() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var returned = false
        val job = launch {
            CatalogAccess.report {
                CatalogAccess.fetch<Unit>(source(), "cancel") { started.complete(Unit); awaitCancellation() }
            }
            returned = true
        }
        started.await()
        job.cancelAndJoin()
        assertFalse(returned)
    }

    @Test fun resultNotesAreReportedWithoutFailingTheSearch() = runBlocking {
        val result = CatalogAccess.report {
            CatalogAccess.note("Nur der Autor wurde erkannt.")
            CatalogAccess.fetch(source(), "ok") { "Treffer" }
        }
        assertEquals("Treffer", result.value)
        assertEquals(listOf("Nur der Autor wurde erkannt."), result.warnings)
    }
}
