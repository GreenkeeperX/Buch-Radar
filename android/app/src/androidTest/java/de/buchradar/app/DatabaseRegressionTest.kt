package de.buchradar.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.buchradar.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.database.sqlite.SQLiteDatabase

@RunWith(AndroidJUnit4::class)
class DatabaseRegressionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory() = Room.inMemoryDatabaseBuilder(context, BuchRadarDatabase::class.java).build()

    @Test fun isbnChangeClearsEditionDependentData() = runBlocking {
        val db = memory()
        try {
            val original = BookEntity("9780804429573", "Alt", "Autor", 2000, "https://example.org/old.jpg", "AVAILABLE", 563, 100)
            db.bookDao().insert(original)
            db.bookDao().updateBook(original.id, "9780140328721", "Neu", "Autor")
            val changed = db.bookDao().findById(original.id)!!
            assertNull(changed.currentPriceCents); assertNull(changed.coverUrl); assertNull(changed.lastCheckedAt)
            assertNull(changed.publicationYear); assertEquals("UNKNOWN", changed.momoxStatus)
        } finally { db.close() }
    }

    @Test fun networkErrorPreservesLastOffer() = runBlocking {
        val db = memory()
        try {
            val book = BookEntity("9780804429573")
            db.bookDao().insert(book)
            db.bookDao().updateCheck(book.isbn, "AVAILABLE", 563, 100)
            db.bookDao().updateCheck(book.isbn, "ERROR", null, 200)
            val stored = db.bookDao().findByIsbn(book.isbn)!!
            assertEquals(563, stored.currentPriceCents)
            assertEquals(100L, stored.lastCheckedAt)
            assertEquals(200L, stored.lastAttemptAt)
            assertEquals("ERROR", stored.lastAttemptStatus)
        } finally { db.close() }
    }

    @Test fun duplicateIsbnInsertIsReportedAndManualBooksStaySeparate() = runBlocking {
        val db = memory()
        try {
            assertNotEquals(-1L, db.bookDao().insert(BookEntity("9780804429573")))
            assertEquals(-1L, db.bookDao().insert(BookEntity("9780804429573")))
            db.bookDao().insert(BookEntity("", "Altes Buch A"))
            db.bookDao().insert(BookEntity("", "Altes Buch B"))
            assertEquals(3, db.bookDao().getAll().size)
        } finally { db.close() }
    }

    @Test fun migrationFromVersionFourKeepsBooksAndSales() = runBlocking {
        val name = "migration-review-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE books (isbn TEXT NOT NULL PRIMARY KEY, title TEXT, author TEXT, publicationYear INTEGER, coverUrl TEXT, momoxStatus TEXT NOT NULL, currentPriceCents INTEGER, lastCheckedAt INTEGER, createdAt INTEGER NOT NULL, soldAt INTEGER)")
            db.execSQL("INSERT INTO books VALUES ('9780804429573','Test','Autor',2000,NULL,'AVAILABLE',563,100,10,200)")
            db.version = 4
        }
        val db = Room.databaseBuilder(context, BuchRadarDatabase::class.java, name).addMigrations(BuchRadarDatabase.MIGRATION_4_5).build()
        try {
            val stored = db.bookDao().getIncludingSold().single()
            assertEquals("Test", stored.title); assertEquals(200L, stored.soldAt)
            assertEquals(563, stored.currentPriceCents); assertTrue(stored.id.isNotBlank())
            assertEquals("AVAILABLE", stored.lastAttemptStatus)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun queueSurvivesDatabaseReopen() = runBlocking {
        val name = "queue-review-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, BuchRadarDatabase::class.java, name).build()
        val item = PendingCheck("9780804429573", 1, 10)
        db.bookDao().enqueue(item)
        db.close()
        db = Room.databaseBuilder(context, BuchRadarDatabase::class.java, name).build()
        try { assertEquals(item, db.bookDao().nextCheck()) }
        finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun changingQueuedIsbnFinishesRunWithoutCountingAnOffer() = runBlocking {
        val db = memory()
        try {
            val dao = db.bookDao()
            val book = BookEntity("9780804429573")
            dao.insert(book)
            dao.enqueue(PendingCheck(book.isbn, book.createdAt, 1))
            dao.saveRun(CheckRun(total = 1, startedAt = 1, pausedReason = "Zugriff prüfen"))
            dao.updateBook(book.id, "9780140328721", "Neu", null)
            val run = dao.getRun()!!
            assertEquals(0, dao.pendingCount())
            assertEquals(0, run.total); assertEquals(0, run.available)
            assertNotNull(run.finishedAt); assertNull(run.pausedReason)
        } finally { db.close() }
    }

    @Test fun lateMetadataDoesNotOverwriteEditsOrChangedEdition() = runBlocking {
        val db = memory()
        try {
            val dao = db.bookDao()
            val book = BookEntity("9780804429573", "Eigener Titel")
            dao.insert(book)
            dao.fillMissingMetadata(book.id, book.isbn, "Katalogtitel", "Autor", 2000, null, "Testkatalog")
            assertEquals("Eigener Titel", dao.findById(book.id)!!.title)
            assertEquals("Autor", dao.findById(book.id)!!.author)
            dao.updateBook(book.id, "9780140328721", "Neue Ausgabe", null)
            assertEquals(0, dao.fillMissingMetadata(book.id, book.isbn, "Alt", "Falsch", 1900, null, "Testkatalog"))
            assertNull(dao.findById(book.id)!!.author)
        } finally { db.close() }
    }

    @Test fun delayedRetryDoesNotBlockNewBooksOrModifyReplacementTask() = runBlocking {
        val db = memory()
        try {
            val dao = db.bookDao()
            val old = PendingCheck("9780804429573", 1, 1)
            val next = PendingCheck("9780140328721", 2, 2)
            dao.enqueue(old); dao.enqueue(next)
            dao.postpone(old.isbn, old.requestId, 1, 1000)
            assertEquals(next, dao.nextCheck())
            dao.removePending(old.isbn)
            val replacement = PendingCheck(old.isbn, 3, 3)
            dao.enqueue(replacement)
            dao.postpone(old.isbn, old.requestId, 2, 2000)
            assertEquals(replacement, dao.pendingFor(old.isbn))
        } finally { db.close() }
    }
}
