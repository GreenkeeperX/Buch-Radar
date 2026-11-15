package de.buchradar.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE soldAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE soldAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE soldAt IS NOT NULL ORDER BY soldAt DESC")
    fun observeSold(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET isbn = :newIsbn, title = :title, author = :author WHERE isbn = :oldIsbn")
    suspend fun updateBook(oldIsbn: String, newIsbn: String, title: String?, author: String?): Int

    @Query("DELETE FROM books WHERE isbn = :isbn")
    suspend fun deleteByIsbn(isbn: String)

    @Query("UPDATE books SET momoxStatus = :status, currentPriceCents = :price, lastCheckedAt = :checkedAt WHERE isbn = :isbn")
    suspend fun updateCheck(isbn: String, status: String, price: Int?, checkedAt: Long)

    @Query("UPDATE books SET soldAt = :soldAt WHERE isbn IN (:isbns)")
    suspend fun updateSoldStatus(isbns: List<String>, soldAt: Long?): Int
}
