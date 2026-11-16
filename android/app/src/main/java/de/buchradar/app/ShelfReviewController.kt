package de.buchradar.app

import android.graphics.Bitmap
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.CatalogBook
import de.buchradar.app.network.CatalogAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ShelfLookupGroup(
    val id: String,
    val query: String,
    val candidates: List<CatalogBook> = emptyList(),
    val pending: Boolean = true,
    val postponed: Boolean = false,
    val error: String? = null,
    val revision: Long = 0,
    val warnings: List<String> = emptyList()
)

data class ShelfReviewState(
    val groups: List<ShelfLookupGroup> = emptyList(),
    val totalCount: Int = 0,
    val version: Int = 1
)

interface ShelfReviewStore {
    suspend fun read(): ShelfReviewState
    suspend fun write(state: ShelfReviewState)
}

/** Durable review data, transient network work. No camera image enters the store. */
class ShelfReviewController(
    private val scope: CoroutineScope,
    private val store: ShelfReviewStore,
    private val lookup: suspend (String, Bitmap?) -> List<CatalogBook>
) {
    private val mutableState = MutableStateFlow(ShelfReviewState())
    val state = mutableState.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutations = Mutex()
    private var loaded = false
    private var active = false
    private var generation = 0L
    private var worker: Job? = null
    private val frames = mutableMapOf<String, Bitmap>()

    init { action { } }

    private suspend fun load() = mutations.withLock {
        if (!loaded) { mutableState.value = store.read(); loaded = true }
    }

    private fun action(loadExisting: Boolean = true, block: suspend () -> Unit): Job = scope.launch {
        try {
            if (loadExisting) load()
            mutableError.value = null
            block()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableError.value = "Regal-Funde konnten nicht gespeichert oder geladen werden. Bitte erneut versuchen."
        }
    }

    private suspend fun update(change: (ShelfReviewState) -> ShelfReviewState) {
        // Disk commit and publication must finish together even if a search is cancelled.
        withContext(NonCancellable) {
            mutations.withLock {
                val previous = mutableState.value
                val next = change(previous)
                if (next != previous) { store.write(next); mutableState.value = next }
            }
        }
    }

    private fun activate(block: suspend () -> Unit): Job {
        active = true
        stopWorker()
        return action { block(); startWorker() }
    }

    fun append(queries: List<String>): Job = activate {
        val added = queries.map(String::trim).filter(String::isNotEmpty)
            .map { ShelfLookupGroup(UUID.randomUUID().toString(), it.take(2_000)) }
        update { old ->
            require(old.groups.size + added.size <= 1_000)
            old.copy(groups = old.groups + added, totalCount = (if (old.groups.isEmpty()) 0 else old.totalCount) + added.size)
        }
    }

    fun resume(): Job = activate { }

    fun pause() {
        active = false
        stopWorker()
    }

    fun correct(id: String, query: String, frame: Bitmap? = null): Job = activate {
        require(query.isNotBlank())
        update { old ->
            val found = old.groups.firstOrNull { it.id == id } ?: return@update old
            val corrected = found.copy(query = query.trim().take(2_000), candidates = emptyList(), pending = true,
                postponed = false, error = null, revision = found.revision + 1, warnings = emptyList())
            old.copy(groups = listOf(corrected) + old.groups.filterNot { it.id == id })
        }
        frames.remove(id)
        if (frame != null && mutableState.value.groups.any { it.id == id }) frames[id] = frame
    }

    fun postpone(id: String): Job = action {
        update { old -> old.copy(groups = old.groups.map { if (it.id == id) it.copy(postponed = true) else it }) }
        frames.remove(id)
        startWorker()
    }

    fun restorePostponed(): Job = activate {
        update { old -> old.copy(groups = old.groups.map { it.copy(postponed = false) }) }
    }

    fun retryFailures(): Job = activate {
        update { old -> old.copy(groups = old.groups.map {
            if (it.error != null && !it.postponed) it.copy(pending = true, error = null) else it
        }) }
    }

    fun complete(id: String): Job = action {
        update { old -> old.copy(groups = old.groups.filterNot { it.id == id }) }
        frames.remove(id)
        startWorker()
    }

    fun discardAll(): Job {
        pause()
        return action(loadExisting = false) {
            withContext(NonCancellable) {
                mutations.withLock {
                    val empty = ShelfReviewState()
                    store.write(empty)
                    mutableState.value = empty
                    loaded = true
                }
            }
            frames.clear()
        }
    }

    private fun stopWorker() {
        generation++
        worker?.cancel()
        worker = null
        mutableBusy.value = false
    }

    private fun startWorker() {
        stopWorker()
        if (!active) return
        val ownGeneration = generation
        worker = scope.launch {
            mutableBusy.value = true
            try {
                while (isActive && active) {
                    val item = mutableState.value.groups.firstOrNull { it.pending && !it.postponed } ?: break
                    val frame = frames.remove(item.id)
                    val result = catchingOperation { CatalogAccess.report { lookup(item.query, frame) } }
                    ensureActive()
                    update { old -> old.copy(groups = old.groups.map { current ->
                        if (current.id == item.id && current.revision == item.revision && !current.postponed)
                            current.copy(candidates = result.getOrNull()?.value.orEmpty().take(5), pending = false,
                                warnings = result.getOrNull()?.warnings.orEmpty(),
                                error = if (result.isFailure) "Katalogsuche fehlgeschlagen. Erneut versuchen oder den Text korrigieren." else null)
                        else current
                    }) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = "Regal-Funde konnten nicht gesichert werden. Die Suche wurde angehalten." }
            finally { if (ownGeneration == generation) mutableBusy.value = false }
        }
    }
}
