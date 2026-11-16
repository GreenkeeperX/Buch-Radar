package de.buchradar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_checks")
data class PendingCheck(
    @PrimaryKey val isbn: String,
    val bookCreatedAt: Long,
    val requestedAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val requestId: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "check_history")
data class CheckHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isbn: String,
    val checkedAt: Long,
    val status: String,
    val priceCents: Int?,
    val error: String? = null
)

@Entity(tableName = "check_run")
data class CheckRun(
    @PrimaryKey val id: Int = 1,
    val total: Int = 0,
    val completed: Int = 0,
    val available: Int = 0,
    val unavailable: Int = 0,
    val failed: Int = 0,
    val valueCents: Long = 0,
    val pausedReason: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    val notify: Boolean = false
)
