package de.buchradar.app

import android.content.Context
import androidx.room.withTransaction
import de.buchradar.app.data.*
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.MomoxCatalog
import de.buchradar.app.network.MomoxCheckException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** All producers share one durable queue. Only the worker consumes it. */
object MomoxCheckCoordinator {
    /** Unreadable Momox answers in a row before the whole queue pauses. */
    const val UNKNOWN_FORMAT_PAUSE = 3
    private val consumer = Mutex()

    suspend fun enqueue(context: Context, books: List<BookEntity>, notify: Boolean = false) {
        val db = BuchRadarDatabase.get(context)
        db.withTransaction {
            val dao = db.bookDao()
            val old = dao.getRun()
            var run = if (dao.pendingCount() == 0) CheckRun(startedAt = System.currentTimeMillis(), notify = notify)
                else old ?: CheckRun(startedAt = System.currentTimeMillis())
            var added = 0
            books.filter { it.soldAt == null && it.isbn.isNotBlank() }.forEach { book ->
                if (dao.enqueue(PendingCheck(book.isbn, book.createdAt, System.currentTimeMillis())) != -1L) added++
            }
            run = run.copy(total = run.total + added, notify = run.notify || notify, finishedAt = null)
            if (run.total == run.completed) run = run.copy(finishedAt = System.currentTimeMillis())
            dao.saveRun(run)
        }
        MomoxWorkScheduler.drain(context)
    }

    /** A run paused only because an earlier build had no access continues once this build has one. */
    suspend fun resumeIfAccessArrived(context: Context) {
        if (BuildConfig.MOMOX_API_TOKEN.isBlank()) return
        if (BuchRadarDatabase.get(context).bookDao().getRun()?.pausedReason == MomoxCatalog.MISSING_ACCESS) resume(context)
    }

    suspend fun resume(context: Context) {
        val db = BuchRadarDatabase.get(context)
        db.withTransaction {
            db.bookDao().resetRetries()
            db.bookDao().getRun()?.let { db.bookDao().saveRun(it.copy(pausedReason = null)) }
        }
        MomoxWorkScheduler.drain(context)
    }

    suspend fun cancel(context: Context) {
        val db = BuchRadarDatabase.get(context)
        db.withTransaction {
            db.bookDao().clearPending()
            db.bookDao().getRun()?.let { db.bookDao().saveRun(it.copy(pausedReason = "Prüfung beendet", finishedAt = System.currentTimeMillis())) }
        }
    }

    /** Returns true if pending work should be retried; runs well below worker limits. */
    suspend fun drain(context: Context): Boolean = consumer.withLock {
        val db = BuchRadarDatabase.get(context)
        val dao = db.bookDao()
        val deadline = android.os.SystemClock.elapsedRealtime() + 4 * 60_000L
        // Unreadable answers in a row: one marks only its book, several mean Momox changed and pause the queue.
        var unknownInRow = 0
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (dao.getRun()?.pausedReason != null) return@withLock false
            val item = dao.nextCheck() ?: return@withLock false
            val book = dao.findByIsbn(item.isbn)
            if (book == null || book.createdAt != item.bookCreatedAt || book.soldAt != null) {
                finish(db, item, null, null, null)
                continue
            }
            val now = System.currentTimeMillis()
            if (item.nextAttemptAt > now) return@withLock true
            val result = catchingOperation { MomoxCatalog.check(item.isbn) }
            val check = result.getOrNull()
            val error = result.exceptionOrNull()
            val failure = error as? MomoxCheckException
            unknownInRow = if (failure?.unknownFormat == true) unknownInRow + 1 else 0
            val pause = failure?.pauseQueue == true || unknownInRow >= UNKNOWN_FORMAT_PAUSE
            if (error != null && pause) {
                val current = db.withTransaction {
                    // A book sold or replaced during the request no longer decides about the queue;
                    // the next loop pass finishes its task and the following book reports the error itself.
                    val stillQueued = dao.pendingFor(item.isbn)?.requestId == item.requestId &&
                        dao.findByIsbn(item.isbn)?.let { it.createdAt == item.bookCreatedAt && it.soldAt == null } == true
                    if (stillQueued) {
                        dao.getRun()?.takeIf { it.finishedAt == null }?.let {
                            dao.saveRun(it.copy(pausedReason = if (failure?.unknownFormat == true)
                                "$UNKNOWN_FORMAT_PAUSE Momox-Antworten in Folge nicht lesbar (zuletzt: ${error.message}); Prüfung angehalten."
                                else error.message ?: "Zugriff prüfen"))
                        }
                    }
                    stillQueued
                }
                // A late answer for a removed or replaced task must not stall the remaining queue.
                if (!current) continue
                AppLog.add(context, "Momox-Prüfung pausiert: ${error.message}")
                return@withLock false
            }
            if (error != null && failure?.retryable != false && item.attempts < 3) {
                val seconds = failure?.retryAfterSeconds?.coerceIn(30, 86_400) ?: (30L shl item.attempts)
                dao.postpone(item.isbn, item.requestId, item.attempts + 1, now + seconds * 1_000)
                AppLog.add(context, "Momox: vorübergehender Fehler, Wiederholung vorgemerkt")
                delay(1_000)
                continue
            }
            finish(db, item, check?.status ?: "ERROR", check?.priceCents, error?.message)
            AppLog.add(context, "Momox: ${check?.status ?: "ERROR"}" + if (failure?.unknownFormat == true) " (${error.message})" else "")
            delay(1_000)
        }
        dao.pendingCount() > 0
    }

    private suspend fun finish(db: BuchRadarDatabase, item: PendingCheck, status: String?, price: Int?, error: String?) {
        db.withTransaction {
            val dao = db.bookDao()
            // Cancellation or an edition change may have removed/replaced this task meanwhile.
            val book = dao.findByIsbn(item.isbn)
            if (dao.pendingFor(item.isbn)?.requestId != item.requestId) return@withTransaction
            val run = dao.getRun() ?: return@withTransaction
            if (run.finishedAt != null) return@withTransaction
            val valid = book != null && book.createdAt == item.bookCreatedAt && book.soldAt == null && status != null
            if (valid) {
                val at = System.currentTimeMillis()
                dao.updateCheck(item.isbn, status!!, price, at)
                dao.setCheckError(item.isbn, error)
                dao.insertHistory(CheckHistory(isbn = item.isbn, checkedAt = at, status = status, priceCents = price, error = error))
                dao.pruneHistory()
            }
            dao.removePending(item.isbn)
            dao.saveRun(run.copy(
                completed = run.completed + 1,
                available = run.available + if (valid && status == "AVAILABLE") 1 else 0,
                unavailable = run.unavailable + if (valid && status == "NOT_AVAILABLE") 1 else 0,
                failed = run.failed + if (valid && status !in listOf("AVAILABLE", "NOT_AVAILABLE")) 1 else 0,
                valueCents = run.valueCents + if (valid && status == "AVAILABLE") (price ?: 0).toLong() else 0,
                finishedAt = if (dao.pendingCount() == 0) System.currentTimeMillis() else null
            ))
        }
    }
}
