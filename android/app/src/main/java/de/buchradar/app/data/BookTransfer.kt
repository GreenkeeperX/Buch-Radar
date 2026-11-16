package de.buchradar.app.data

import android.content.Context
import android.net.Uri
import de.buchradar.app.domain.BookIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object BookTransfer {
    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val rows = BuchRadarDatabase.get(context).bookDao().getIncludingSold()
        val books = JSONArray()
        rows.forEach { book -> books.put(JSONObject().apply {
            put("id", book.id); put("isbn", book.isbn); put("title", book.title); put("author", book.author)
            put("year", book.publicationYear); put("cover", book.coverUrl)
            put("createdAt", book.createdAt); put("soldAt", book.soldAt)
        }) }
        val document = JSONObject().put("format", "buecher-radar").put("version", 1).put("books", books)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(document.toString(2)) }
    }

    suspend fun preview(context: Context, uri: Uri): List<BookEntity> = withContext(Dispatchers.IO) {
        val text = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
            val chars = CharArray(5_000_001)
            var length = 0
            while (length < chars.size) {
                val n = reader.read(chars, length, chars.size - length)
                if (n < 0) break
                length += n
            }
            require(length <= 5_000_000) { "Datei ist zu groß (höchstens 5 MB Text)." }
            String(chars, 0, length)
        }
        val document = JSONObject(text)
        require(document.optString("format") == "buecher-radar" && document.optInt("version") == 1) { "Unbekanntes Sicherungsformat" }
        val books = document.getJSONArray("books")
        require(books.length() <= 10_000) { "Höchstens 10.000 Bücher je Import" }
        (0 until books.length()).map { index ->
            val item = books.getJSONObject(index)
            val rawIsbn = item.getString("isbn")
            val isbn = if (rawIsbn.isBlank()) "" else BookIdentity.canonicalIsbn(rawIsbn) ?: error("Ungültige ISBN in Eintrag ${index + 1}")
            fun optional(key: String) = item.optString(key).takeIf { it.isNotBlank() && it != "null" }?.take(1_000)
            BookEntity(isbn, optional("title"), optional("author"), item.optInt("year").takeIf { it in 1400..2200 },
                optional("cover")?.takeIf { it.startsWith("https://") },
                createdAt = item.optLong("createdAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
                soldAt = item.optLong("soldAt").takeIf { it > 0 }, id = optional("id") ?: java.util.UUID.randomUUID().toString())
        }.distinctBy { it.isbn.takeIf(String::isNotBlank) ?: it.id }
    }
}
