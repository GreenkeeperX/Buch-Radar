package de.buchradar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE soldAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE soldAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    suspend fun getIncludingSold(): List<BookEntity>

    @Query("SELECT * FROM books WHERE soldAt IS NOT NULL ORDER BY soldAt DESC")
    fun observeSold(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRaw(book: BookEntity): Long

    @Transaction
    suspend fun insert(book: BookEntity): Long {
        if (book.isbn.isNotBlank() && findByIsbn(book.isbn) != null) return -1
        return insertRaw(book)
    }

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>


    @Query("""UPDATE books SET
        momoxStatus = CASE WHEN isbn = :newIsbn THEN momoxStatus ELSE 'UNKNOWN' END,
        currentPriceCents = CASE WHEN isbn = :newIsbn THEN currentPriceCents ELSE NULL END,
        lastCheckedAt = CASE WHEN isbn = :newIsbn THEN lastCheckedAt ELSE NULL END,
        lastAttemptAt = CASE WHEN isbn = :newIsbn THEN lastAttemptAt ELSE NULL END,
        lastAttemptStatus = CASE WHEN isbn = :newIsbn THEN lastAttemptStatus ELSE NULL END,
        lastError = CASE WHEN isbn = :newIsbn THEN lastError ELSE NULL END,
        coverUrl = CASE WHEN isbn = :newIsbn THEN coverUrl ELSE NULL END,
        publicationYear = CASE WHEN isbn = :newIsbn THEN publicationYear ELSE NULL END,
        metadataSource = CASE WHEN isbn = :newIsbn THEN metadataSource ELSE NULL END,
        isbn = :newIsbn, title = :title, author = :author WHERE id = :id""")
    suspend fun updateBookRow(id: String, newIsbn: String, title: String?, author: String?): Int

    @Transaction
    suspend fun updateBook(id: String, newIsbn: String, title: String?, author: String?): Int {
        val original = findById(id) ?: return 0
        if (original.isbn != newIsbn && pendingFor(original.isbn) != null) {
            removePending(original.isbn)
            getRun()?.takeIf { it.finishedAt == null }?.let { run ->
                val remaining = pendingCount()
                saveRun(run.copy(
                    total = (run.total - 1).coerceAtLeast(run.completed),
                    pausedReason = if (remaining == 0) null else run.pausedReason,
                    finishedAt = if (remaining == 0) System.currentTimeMillis() else null
                ))
            }
        }
        return updateBookRow(id, newIsbn, title, author)
    }

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""UPDATE books SET
        momoxStatus = CASE WHEN :status = 'ERROR' THEN momoxStatus ELSE :status END,
        currentPriceCents = CASE WHEN :status = 'ERROR' THEN currentPriceCents ELSE :price END,
        lastCheckedAt = CASE WHEN :status = 'ERROR' THEN lastCheckedAt ELSE :checkedAt END,
        lastAttemptAt = :checkedAt, lastAttemptStatus = :status
        WHERE isbn = :isbn AND (lastAttemptAt IS NULL OR lastAttemptAt <= :checkedAt)""")
    suspend fun updateCheck(isbn: String, status: String, price: Int?, checkedAt: Long)

    @Query("UPDATE books SET soldAt = :soldAt WHERE id IN (:ids)")
    suspend fun updateSoldStatus(ids: List<String>, soldAt: Long?): Int

    @Query("UPDATE books SET lastError = :message WHERE isbn = :isbn")
    suspend fun setCheckError(isbn: String, message: String?)

    @Query("UPDATE books SET title = :title, author = :author, publicationYear = :year, coverUrl = :cover, metadataSource = :source WHERE isbn = :isbn")
    suspend fun updateMetadata(isbn: String, title: String?, author: String?, year: Int?, cover: String?, source: String?)

    @Query("""UPDATE books SET title = COALESCE(NULLIF(title, ''), :title),
        author = COALESCE(NULLIF(author, ''), :author),
        publicationYear = COALESCE(publicationYear, :year),
        coverUrl = COALESCE(NULLIF(coverUrl, ''), :cover),
        metadataSource = COALESCE(metadataSource, :source)
        WHERE id = :id AND isbn = :expectedIsbn""")
    suspend fun fillMissingMetadata(id: String, expectedIsbn: String, title: String?, author: String?, year: Int?, cover: String?, source: String?): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(check: PendingCheck): Long
    @Query("SELECT * FROM pending_checks ORDER BY nextAttemptAt, requestedAt, isbn LIMIT 1")
    suspend fun nextCheck(): PendingCheck?
    @Query("SELECT * FROM pending_checks WHERE isbn = :isbn")
    suspend fun pendingFor(isbn: String): PendingCheck?
    @Query("SELECT isbn FROM pending_checks")
    fun observePendingIsbns(): Flow<List<String>>
    @Query("SELECT COUNT(*) FROM pending_checks")
    suspend fun pendingCount(): Int
    @Query("DELETE FROM pending_checks WHERE isbn = :isbn")
    suspend fun removePending(isbn: String)
    @Query("DELETE FROM pending_checks")
    suspend fun clearPending()
    @Query("UPDATE pending_checks SET attempts = :attempts, nextAttemptAt = :nextAttemptAt WHERE isbn = :isbn AND requestId = :requestId")
    suspend fun postpone(isbn: String, requestId: String, attempts: Int, nextAttemptAt: Long)
    @Query("UPDATE pending_checks SET attempts = 0, nextAttemptAt = 0")
    suspend fun resetRetries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRun(run: CheckRun)
    @Query("SELECT * FROM check_run WHERE id = 1")
    suspend fun getRun(): CheckRun?
    @Query("SELECT * FROM check_run WHERE id = 1")
    fun observeRun(): Flow<CheckRun?>
    @Insert
    suspend fun insertHistory(check: CheckHistory)
    @Query("SELECT * FROM check_history WHERE isbn = :isbn ORDER BY checkedAt DESC LIMIT 30")
    fun observeHistory(isbn: String): Flow<List<CheckHistory>>
    @Query("DELETE FROM check_history WHERE id NOT IN (SELECT id FROM check_history ORDER BY checkedAt DESC LIMIT 3000)")
    suspend fun pruneHistory()
}
