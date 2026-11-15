package de.buchradar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.network.MomoxCatalog
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

class MomoxPeriodicWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("buchradar_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_check", false)) return Result.success()
        val database = Room.databaseBuilder(applicationContext, BuchRadarDatabase::class.java, "buchradar.db")
            .addMigrations(BuchRadarDatabase.MIGRATION_1_2, BuchRadarDatabase.MIGRATION_2_3, BuchRadarDatabase.MIGRATION_3_4)
            .build()
        return try {
            val books = database.bookDao().getAll()
            if (books.isEmpty()) {
                AppLog.add(applicationContext, "Automatische Momox-Prüfung: Liste ist leer")
                notifyIfEnabled("Bücher-Radar", "Die Liste enthält keine Bücher.", 0)
                return Result.success()
            }
            var available = 0
            var unavailable = 0
            var errors = 0
            var availableValueCents = 0
            books.forEachIndexed { index, book ->
                if (isStopped) return Result.retry()
                val checkedAt = System.currentTimeMillis()
                val result = runCatching { MomoxCatalog.check(book.isbn) }
                val check = result.getOrNull()
                if (check == null) {
                    errors++
                    database.bookDao().updateCheck(book.isbn, "ERROR", null, checkedAt)
                    AppLog.add(applicationContext, "Automatik ${index + 1}/${books.size}: Fehler – ${result.exceptionOrNull()?.message ?: "unbekannt"}")
                } else {
                    database.bookDao().updateCheck(book.isbn, check.status, check.priceCents, checkedAt)
                    when (check.status) {
                        "AVAILABLE" -> {
                            available++
                            availableValueCents += check.priceCents ?: 0
                        }
                        "NOT_AVAILABLE" -> unavailable++
                        else -> errors++
                    }
                    AppLog.add(applicationContext, "Automatik ${index + 1}/${books.size}: ${check.status}")
                }
                if (index < books.lastIndex) delay(750)
            }
            AppLog.add(applicationContext, "Automatik abgeschlossen: $available Ankauf, $unavailable kein Ankauf, $errors Fehler")
            val total = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(availableValueCents / 100.0)
            notifyIfEnabled(
                "Momox-Prüfung abgeschlossen",
                "$available Ankauf · $unavailable kein Ankauf · $errors Fehler · Gesamtwert $total",
                availableValueCents
            )
            Result.success()
        } catch (error: Exception) {
            AppLog.add(applicationContext, "Automatische Momox-Prüfung fehlgeschlagen", error)
            Result.retry()
        } finally {
            database.close()
        }
    }

    private fun notifyIfEnabled(title: String, text: String, availableValueCents: Int) {
        val prefs = applicationContext.getSharedPreferences("buchradar_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications", false)) return
        val notificationThreshold = prefs.getInt("notify_threshold_cents", 1_000)
        if (prefs.getBoolean("notify_threshold", false) && availableValueCents < notificationThreshold) {
            AppLog.add(applicationContext, "Mitteilung unterdrückt: Ankaufwert liegt unter ${notificationThreshold / 100} €")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("momox_checks", "Momox-Prüfungen", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, "momox_checks")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(4102, notification)
    }
}
