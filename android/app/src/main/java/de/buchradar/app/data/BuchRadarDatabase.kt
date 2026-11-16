package de.buchradar.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class, PendingCheck::class, CheckHistory::class, CheckRun::class], version = 5, exportSchema = true)
abstract class BuchRadarDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: BuchRadarDatabase? = null
        fun get(context: Context): BuchRadarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BuchRadarDatabase::class.java, "buchradar.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE books_new (isbn TEXT NOT NULL, title TEXT, author TEXT, publicationYear INTEGER, coverUrl TEXT, momoxStatus TEXT NOT NULL, currentPriceCents INTEGER, lastCheckedAt INTEGER, createdAt INTEGER NOT NULL, soldAt INTEGER, lastAttemptAt INTEGER, lastAttemptStatus TEXT, lastError TEXT, metadataSource TEXT, id TEXT NOT NULL PRIMARY KEY)""")
                db.execSQL("""INSERT INTO books_new SELECT isbn,title,author,publicationYear,coverUrl,momoxStatus,currentPriceCents,CASE WHEN momoxStatus = 'ERROR' THEN NULL ELSE lastCheckedAt END,createdAt,soldAt,lastCheckedAt,momoxStatus,NULL,NULL,'legacy:' || isbn FROM books""")
                db.execSQL("DROP TABLE books")
                db.execSQL("ALTER TABLE books_new RENAME TO books")
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_checks (isbn TEXT NOT NULL PRIMARY KEY, bookCreatedAt INTEGER NOT NULL, requestedAt INTEGER NOT NULL, attempts INTEGER NOT NULL, nextAttemptAt INTEGER NOT NULL, requestId TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS check_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, isbn TEXT NOT NULL, checkedAt INTEGER NOT NULL, status TEXT NOT NULL, priceCents INTEGER, error TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS check_run (id INTEGER NOT NULL PRIMARY KEY, total INTEGER NOT NULL, completed INTEGER NOT NULL, available INTEGER NOT NULL, unavailable INTEGER NOT NULL, failed INTEGER NOT NULL, valueCents INTEGER NOT NULL, pausedReason TEXT, startedAt INTEGER NOT NULL, finishedAt INTEGER, notify INTEGER NOT NULL)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN author TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN publicationYear INTEGER")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN soldAt INTEGER")
            }
        }
    }
}
