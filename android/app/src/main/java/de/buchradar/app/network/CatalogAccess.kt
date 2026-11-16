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

private class SourceTrace : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SourceTrace>
    var successful = 0
    val failed = linkedSetOf<String>()
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
        val trace = SourceTrace()
        return withContext(trace) {
            val result = block()
            if (trace.successful == 0 && trace.failed.isNotEmpty())
                throw CatalogUnavailable("Katalog nicht erreichbar: ${trace.failed.joinToString()}. Bitte erneut versuchen.")
            result
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
        val result = catchingOperation {
            gate.withLock {
                val wait = synchronized(lastRequest) { 1_100 - (System.nanoTime() / 1_000_000 - (lastRequest[source] ?: 0)) }
                if (wait > 0) delay(wait)
                synchronized(lastRequest) { lastRequest[source] = System.nanoTime() / 1_000_000 }
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(12_000) { block() } ?: throw CatalogUnavailable("Zeitüberschreitung")
                }
            }
        }
        return result.getOrNull()?.also {
            trace?.successful = (trace?.successful ?: 0) + 1
            synchronized(cache) { cache[cacheKey] = Cached(System.currentTimeMillis(), it) }
        } ?: run { trace?.failed?.add(source); null }
    }
}
