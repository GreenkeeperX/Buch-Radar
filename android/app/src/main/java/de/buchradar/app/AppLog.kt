package de.buchradar.app

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val PREFS = "buchradar_diagnostics"
    private const val KEY = "entries"
    private const val TAG = "BuchRadar.App"

    fun add(context: Context, message: String, error: Throwable? = null) {
        val line = "${SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date())}  $message"
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = (prefs.getString(KEY, "").orEmpty().lines().filter(String::isNotBlank) + line).takeLast(300)
        prefs.edit().putString(KEY, entries.joinToString("\n")).apply()
        if (error == null) Log.i(TAG, message) else Log.e(TAG, message, error)
    }

    fun read(context: Context): List<String> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, "").orEmpty().lines().filter(String::isNotBlank).asReversed()

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
