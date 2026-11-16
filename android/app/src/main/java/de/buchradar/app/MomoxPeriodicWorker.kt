package de.buchradar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.buchradar.app.data.BuchRadarDatabase
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import java.util.Locale
import java.security.MessageDigest

/** Periodic work only produces tasks; the same queue is used by manual checks. */
class MomoxPeriodicWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSharedPreferences("buchradar_settings", Context.MODE_PRIVATE).getBoolean("auto_check", false)) return Result.success()
        val books = BuchRadarDatabase.get(applicationContext).bookDao().getAll()
        val now = System.currentTimeMillis()
        val due = books.filter { it.isbn.isNotBlank() && (it.lastCheckedAt == null || now - it.lastCheckedAt >= 86_400_000 || it.lastAttemptStatus == "ERROR") }
        if (due.isNotEmpty()) MomoxCheckCoordinator.enqueue(applicationContext, due, notify = true)
        return Result.success()
    }
}

class MomoxQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (MomoxCheckCoordinator.drain(applicationContext)) Result.retry()
        else {
            notifyResult()
            Result.success()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AppLog.add(applicationContext, "Prüflauf unterbrochen (${error.javaClass.simpleName}); Fortschritt bleibt erhalten")
        Result.retry()
    }

    private suspend fun notifyResult() {
        val prefs = applicationContext.getSharedPreferences("buchradar_settings", Context.MODE_PRIVATE)
        val dao = BuchRadarDatabase.get(applicationContext).bookDao()
        val run = dao.getRun() ?: return
        if (!run.notify || !prefs.getBoolean("notifications", false)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        val now = System.currentTimeMillis()
        val available = dao.getAll().filter { it.momoxStatus == "AVAILABLE" && it.lastAttemptStatus != "ERROR" && now - (it.lastCheckedAt ?: 0) < 86_400_000 }
        val value = available.sumOf { (it.currentPriceCents ?: 0).toLong() }
        val hasError = run.pausedReason != null || run.failed > 0
        if (!hasError && prefs.getBoolean("notify_threshold", false) && value < prefs.getInt("notify_threshold_cents", 1_000)) return
        val signatureInput = available.sortedBy { it.isbn }.joinToString { "${it.isbn}:${it.currentPriceCents}" } + "|${run.failed}|${run.pausedReason}"
        val signature = MessageDigest.getInstance("SHA-256").digest(signatureInput.toByteArray()).joinToString("") { "%02x".format(it) }
        if (!prefs.getBoolean("notify_every_result", false) && signature == prefs.getString("last_notification_signature", null)) return
        val system = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        system.createNotificationChannel(NotificationChannel("momox_checks", "Momox-Prüfungen", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val valueText = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value / 100.0)
        val text = run.pausedReason ?: "${available.size} ankaufbar · $valueText aktuell bestätigt${if (run.failed > 0) " · ${run.failed} Fehler" else ""}"
        manager.notify(4102, NotificationCompat.Builder(applicationContext, "momox_checks")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(if (hasError) "Prüfung braucht Aufmerksamkeit" else "Bücher-Radar: Ankaufsergebnis")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).build())
        prefs.edit().putString("last_notification_signature", signature).apply()
    }
}
