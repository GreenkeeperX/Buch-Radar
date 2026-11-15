package de.buchradar.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BookSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // TODO: pending ISBNs securely to backend; retry transient network/server errors.
        return Result.success()
    }
}
