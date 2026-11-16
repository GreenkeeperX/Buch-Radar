package de.buchradar.app.network

import de.buchradar.app.domain.catchingOperation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class CatalogUnavailable(message: String) : Exception(message)

internal data class CatalogResult<T>(val value: T, val warnings: List<String>)

private class SourceTrace(budgetMillis: Long) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SourceTrace>
    val deadline = System.nanoTime() / 1_000_000 + budgetMillis
    var successful = 0
    val failed = linkedSetOf<String>()
    var budgetExhausted = false
    fun remaining() = (deadline - System.nanoTime() / 1_000_000).coerceAtLeast(0)
    fun warnings() = buildList {
        if (failed.isNotEmpty()) add("Nicht alle Quellen erreichbar: ${failed.joinToString()}. Treffer können fehlen.")
        if (budgetExhausted) add("Zeitlimit erreicht. Bisherige Treffer bleiben erhalten; bei Bedarf erneut suchen.")
    }
}

/** Bounded in-memory cache. No book queries are written to diagnostic logs. */
internal object CatalogAccess {
    private data class Cached(val at: Long, val value: Any)
    private val cache = object : LinkedHashMap<String, Cached>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 128
    }
    private val gates = mutableMapOf<String, Mutex>()
    private val lastRequest = mutableMapOf<String, Long>()

    suspend fun <T> search(block: suspend () -> T): T {
        if (coroutineContext[SourceTrace] != null) return block()
        return report(block = block).value
    }

    /** Nested ISBN/title/OCR lookups share one budget, including rate-limit waits. */
    suspend fun <T> report(budgetMillis: Long = 30_000, block: suspend () -> T): CatalogResult<T> {
        val existing = coroutineContext[SourceTrace]
        if (existing != null) return CatalogResult(block(), existing.warnings())
        require(budgetMillis > 0)
        val trace = SourceTrace(budgetMillis)
        return withContext(trace) {
            val result = block()
            if (trace.successful == 0 && (trace.failed.isNotEmpty() || trace.budgetExhausted))
                throw CatalogUnavailable("Katalog nicht erreichbar: ${trace.failed.joinToString()}. Bitte erneut versuchen.")
            CatalogResult(result, trace.warnings())
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> fetch(source: String, key: String, block: suspend () -> T): T? {
        val trace = coroutineContext[SourceTrace]
        val cacheKey = "$source:$key"
        synchronized(cache) { cache[cacheKey] }?.takeIf { System.currentTimeMillis() - it.at < 3_600_000 }?.let {
            trace?.successful = (trace?.successful ?: 0) + 1
            return it.value as T
        }
        val gate = synchronized(gates) { gates.getOrPut(source) { Mutex() } }
        val remaining = trace?.remaining() ?: 12_000
        if (remaining <= 0) { trace?.budgetExhausted = true; return null }
        val result = catchingOperation {
            withTimeoutOrNull(minOf(12_000, remaining)) {
                gate.withLock {
                    val wait = synchronized(lastRequest) { 1_100 - (System.nanoTime() / 1_000_000 - (lastRequest[source] ?: 0)) }
                    if (wait > 0) delay(wait)
                    synchronized(lastRequest) { lastRequest[source] = System.nanoTime() / 1_000_000 }
                    withContext(Dispatchers.IO) {
                        block()
                    }
                }
            } ?: run {
                if (trace != null && trace.remaining() <= 0) trace.budgetExhausted = true
                throw CatalogUnavailable("Zeitüberschreitung")
            }
        }
        return result.getOrNull()?.also {
            trace?.successful = (trace?.successful ?: 0) + 1
            synchronized(cache) { cache[cacheKey] = Cached(System.currentTimeMillis(), it) }
        } ?: run { trace?.failed?.add(source); null }
    }
}
