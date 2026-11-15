package de.buchradar.app.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object DailySyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BookSyncWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "buchradar-daily-sync", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
