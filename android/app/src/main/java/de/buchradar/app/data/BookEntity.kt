package de.buchradar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val isbn: String,
    val title: String? = null,
    val author: String? = null,
    val publicationYear: Int? = null,
    val coverUrl: String? = null,
    val momoxStatus: String = "UNKNOWN",
    val currentPriceCents: Int? = null,
    val lastCheckedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val soldAt: Long? = null
)
