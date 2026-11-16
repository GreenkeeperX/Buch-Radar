package de.buchradar.app.network

import de.buchradar.app.domain.catchingOperation
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.util.Xml
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Path
import de.buchradar.app.domain.BookIdentity
import java.util.Locale
import net.creativecouple.validation.isbn.ISBN
import java.text.Normalizer
import kotlinx.coroutines.delay
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class CatalogSearchResponse(val docs: List<CatalogDocument> = emptyList())

data class CatalogDocument(
    val title: String?,
    @Json(name = "author_name") val authors: List<String>?,
    @Json(name = "first_publish_year") val firstPublishYear: Int?,
    val isbn: List<String>?,
    @Json(name = "cover_i") val coverId: Long?,
    val subject: List<String>? = null,
    val key: String? = null,
    val editions: CatalogSearchResponse? = null
)

data class OpenLibraryBookData(
    val title: String? = null,
    @Json(name = "publish_date") val publishDate: String? = null,
    val authors: List<OpenLibraryAuthorData>? = null,
    val cover: OpenLibraryCoverData? = null
)
data class OpenLibraryAuthorData(val name: String? = null)
data class OpenLibraryCoverData(val small: String? = null, val medium: String? = null, val large: String? = null)
data class CatalogFieldValue(val value: String, val sources: Set<String> = emptySet())

data class CatalogBook(
    val title: String,
    val author: String?,
    val publicationYear: Int?,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val visualSimilarity: Int? = null,
    val textSimilarity: Int? = null,
    val shelfQuery: String? = null,
    val sources: Set<String> = emptySet(),
    val publisher: String? = null,
    val sourceId: String? = null,
    val fieldSources: Map<String, String> = emptyMap(),
    val fieldValues: Map<String, List<CatalogFieldValue>> = emptyMap()
) {
    val conflictingFields: Set<String> get() = fieldValues.filterValues { it.size > 1 }.keys
    val candidateId: String get() = isbn?.let { "isbn:$it" } ?: sourceId
        ?: "text:$title|$author|$publicationYear|$publisher"
}

data class ShelfQuery(val id: String, val text: String)
data class ShelfMatch(val id: String, val candidates: List<CatalogBook>)
data class OpenLibraryEdition(
    val title: String? = null,
    val isbn_13: List<String>? = null,
    val isbn_10: List<String>? = null,
    val publish_date: String? = null,
    val publishers: List<String>? = null,
    val covers: List<Long>? = null
)

data class GoogleVolumesResponse(val items: List<GoogleVolume>? = null)
data class GoogleVolume(val volumeInfo: GoogleVolumeInfo? = null)
data class GoogleVolumeInfo(
    val title: String? = null,
    val authors: List<String>? = null,
    @Json(name = "publishedDate") val publishedDate: String? = null,
    @Json(name = "industryIdentifiers") val identifiers: List<GoogleIdentifier>? = null,
    @Json(name = "imageLinks") val imageLinks: GoogleImageLinks? = null
)
data class GoogleIdentifier(val type: String? = null, val identifier: String? = null)
data class GoogleImageLinks(val thumbnail: String? = null, @Json(name = "smallThumbnail") val smallThumbnail: String? = null)

interface OpenLibraryApi {
    @GET("books/{edition}.json")
    @Headers("User-Agent: BuecherRadar/0.11")
    suspend fun edition(@Path("edition") edition: String): OpenLibraryEdition

    @GET("search.json")
    @Headers("User-Agent: BuecherRadar/0.11")
    suspend fun findByTitle(
        @Query("q") title: String,
        @Query("fields") fields: String = "key,title,author_name,editions,editions.key,editions.title",
        @Query("limit") limit: Int = 8,
        @Query("lang") lang: String = "de"
    ): CatalogSearchResponse

    @GET("api/books")
    @Headers("User-Agent: BuecherRadar/0.11")
    suspend fun findBookDataByIsbn(@Query("bibkeys") bibkeys: String,
        @Query("jscmd") command: String = "data", @Query("format") format: String = "json"): Map<String, OpenLibraryBookData>
}

interface GoogleBooksApi {
    @GET("volumes")
    suspend fun search(@Query("q") query: String, @Query("maxResults") maxResults: Int = 5): GoogleVolumesResponse
}

interface DnbSruApi {
    @GET("sru/dnb")
    suspend fun search(@Query("version") version: String = "1.1",
        @Query("operation") operation: String = "searchRetrieve", @Query("query") query: String,
        @Query("maximumRecords") maximumRecords: Int = 5, @Query("recordSchema") schema: String = "MARC21-xml"): ResponseBody
}

object BookCatalog {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()
    private fun retrofit(base: String) = Retrofit.Builder().baseUrl(base).client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
    private val api by lazy { retrofit("https://openlibrary.org/").create(OpenLibraryApi::class.java) }
    private val googleApi by lazy { retrofit("https://www.googleapis.com/books/v1/").create(GoogleBooksApi::class.java) }
    private val dnbApi by lazy { retrofit("https://services.dnb.de/").create(DnbSruApi::class.java) }

    suspend fun findByIsbn(isbn: String): CatalogBook? = CatalogAccess.search {
        val wanted = BookIdentity.canonicalIsbn(isbn) ?: return@search null
        val record = CatalogAccess.fetch("Open Library", "isbn:$wanted") { api.findBookDataByIsbn("ISBN:$wanted") }?.get("ISBN:$wanted")
        val openBook = record?.title?.takeIf(String::isNotBlank)?.let { title ->
            CatalogBook(title, record.authors.orEmpty().mapNotNull { it.name }.joinToString(", ").takeIf(String::isNotBlank),
                yearFrom(record.publishDate), wanted, record.cover?.large ?: record.cover?.medium,
                sources = setOf("Open Library"))
        }
        val google = CatalogAccess.fetch("Google Books", "isbn:$wanted") { googleApi.search("isbn:$wanted") }
            ?.items.orEmpty().firstNotNullOfOrNull { volume ->
                val info = volume.volumeInfo ?: return@firstNotNullOfOrNull null
                if (info.identifiers.orEmpty().none { BookIdentity.canonicalIsbn(it.identifier) == wanted }) return@firstNotNullOfOrNull null
                info.title?.takeIf(String::isNotBlank)?.let { title ->
                    CatalogBook(title, info.authors?.joinToString(), yearFrom(info.publishedDate), wanted,
                        (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)?.replace("http://", "https://"), sources = setOf("Google Books"))
                }
            }
        var combined = mergeCandidates(listOfNotNull(openBook, google))
        if (combined.isEmpty() || combined.first().author == null || combined.first().publicationYear == null) {
            combined = mergeCandidates(combined + dnbSearch("num=$wanted").filter { it.isbn == wanted })
        }
        combined.firstOrNull()
    }

    suspend fun findByTitle(title: String, expandSources: Boolean = true): List<CatalogBook> = CatalogAccess.search {
        val response = CatalogAccess.fetch("Open Library", "title:${title.take(250)}") { api.findByTitle(title.take(250).trim()) }
        val openLibrary = response?.docs.orEmpty().take(4).mapNotNull { work ->
            val editionKey = work.editions?.docs?.firstOrNull()?.key?.substringAfterLast('/') ?: return@mapNotNull null
            val edition = CatalogAccess.fetch("Open Library", editionKey) { api.edition(editionKey) } ?: return@mapNotNull null
            val cleanTitle = edition.title?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val isbn = (edition.isbn_13.orEmpty() + edition.isbn_10.orEmpty()).firstNotNullOfOrNull(BookIdentity::canonicalIsbn)
            CatalogBook(cleanTitle, work.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank),
                yearFrom(edition.publish_date), isbn, catalogCover(edition.covers?.firstOrNull()?.takeIf { it > 0 }, isbn),
                sources = setOf("Open Library"), publisher = edition.publishers?.joinToString(), sourceId = "ol:$editionKey")
        }
        val primary = openLibrary.distinctBy { it.candidateId }
        if (!expandSources && primary.isNotEmpty()) return@search primary
        val google = CatalogAccess.fetch("Google Books", "title:${title.take(250)}") { googleApi.search(title.take(250).trim(), 10) }?.items.orEmpty().mapNotNull { volume ->
            val info = volume.volumeInfo ?: return@mapNotNull null
            val cleanTitle = info.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val validIsbn = info.identifiers.orEmpty().firstNotNullOfOrNull { BookIdentity.canonicalIsbn(it.identifier) }
            CatalogBook(cleanTitle, info.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank), info.publishedDate?.take(4)?.toIntOrNull(), validIsbn,
                (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)?.replace("http://", "https://"), sources = setOf("Google Books"))
        }
        val combined = mergeCandidates(primary + google)
        val queryTokens = foldText(title).split(" ").filter { it.length >= 3 }.toSet()
        val hasStrongMatch = combined.any { book ->
            val bookTokens = foldText("${book.title} ${book.author.orEmpty()}").split(" ").toSet()
            tokenCoverage(queryTokens, bookTokens) >= 0.8f
        }
        if (!expandSources || (combined.size >= 3 && hasStrongMatch)) combined
        else mergeCandidates(combined + catchingOperation { dnbSearch("woe=${foldText(title)}") }.getOrDefault(emptyList()))
    }

    /** Fetches only a small, on-demand result set; DNB supplies bibliographic metadata, not covers. */
    private suspend fun dnbSearch(query: String): List<CatalogBook> = CatalogAccess.fetch("DNB", query) {
        dnbApi.search(query = query).use { body ->
            val reader = body.charStream()
            val chars = CharArray(2_000_001)
            var length = 0
            while (length < chars.size) {
                val n = reader.read(chars, length, chars.size - length)
                if (n < 0) break
                length += n
            }
            require(length <= 2_000_000) { "Katalogantwort zu groß" }
            val xml = String(chars, 0, length)
            parseDnbRecords(xml)
        }
    }.orEmpty()

    private fun yearFrom(value: String?): Int? = Regex("\\b(1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b").find(value.orEmpty())?.value?.toIntOrNull()

    private fun parseDnbRecords(xml: String): List<CatalogBook> {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "Katalogantwort enthält eine nicht unterstützte Dokumentdeklaration"
        }
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val results = mutableListOf<CatalogBook>()
        var fieldTag: String? = null
        var fields = mutableMapOf<String, MutableList<String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':')
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "recordData" -> fields = mutableMapOf()
                    "datafield" -> fieldTag = parser.getAttributeValue(null, "tag")
                    "subfield" -> {
                        val code = parser.getAttributeValue(null, "code")
                        val value = parser.nextText().trim()
                        if (fieldTag != null && code != null && value.isNotBlank()) fields.getOrPut("$fieldTag:$code") { mutableListOf() }.add(value)
                    }
                }
                XmlPullParser.END_TAG -> when (tag) {
                    "datafield" -> fieldTag = null
                    "recordData" -> {
                        val rawTitle = (fields["245:a"].orEmpty() + fields["245:b"].orEmpty()).joinToString(" ").trim()
                        val cleanTitle = rawTitle.substringBefore(" /").trim().takeIf(String::isNotBlank)
                        if (cleanTitle != null) {
                            val authors = (fields["100:a"].orEmpty() + fields["110:a"].orEmpty() + fields["700:a"].orEmpty())
                                .map { it.trim().trimEnd(',', '.') }.distinct()
                            val isbns = fields["020:a"].orEmpty().flatMap(BookIdentity::isbnsInText).distinct()
                            val year = (fields["264:c"].orEmpty() + fields["260:c"].orEmpty())
                                .firstNotNullOfOrNull { Regex("\\b(1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b").find(it)?.value?.toIntOrNull() }
                            (isbns.map { it as String? }.ifEmpty { listOf(null) }).forEach { isbn ->
                                results += CatalogBook(cleanTitle, authors.joinToString(", ").takeIf(String::isNotBlank), year, isbn,
                                    coverUrl = null, sources = setOf("Deutsche Nationalbibliothek"),
                                    publisher = fields["264:b"]?.firstOrNull() ?: fields["260:b"]?.firstOrNull(),
                                    sourceId = "dnb:$cleanTitle|${authors.joinToString()}|$year|$isbn")
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    /** Edition identity controls merging; keep conflicting field evidence for human review. */
    internal fun mergeCandidates(candidates: List<CatalogBook>): List<CatalogBook> = candidates
        .filter { it.title.isNotBlank() }
        .groupBy { candidate ->
            BookIdentity.canonicalIsbn(candidate.isbn)?.let { "isbn:$it" }
                ?: candidate.candidateId
        }
        .values.map { records ->
            val preferred = records.first()
            fun evidence(field: String, value: (CatalogBook) -> String?): List<CatalogFieldValue> =
                records.flatMap { record ->
                    record.fieldValues[field] ?: listOfNotNull(value(record)?.takeIf(String::isNotBlank)?.let {
                        CatalogFieldValue(it, record.fieldSources[field]?.split(", ")?.toSet() ?: record.sources)
                    })
                }.groupBy { foldText(it.value) }.values.map { values ->
                    CatalogFieldValue(values.first().value, values.flatMap { it.sources }.toSet())
                }
            val values = mapOf(
                "title" to evidence("title") { it.title },
                "author" to evidence("author") { it.author },
                "year" to evidence("year") { it.publicationYear?.toString() },
                "publisher" to evidence("publisher") { it.publisher }
            ).filterValues { it.isNotEmpty() }
            preferred.copy(
                title = records.firstOrNull { it.title.isNotBlank() }?.title ?: preferred.title,
                author = records.firstNotNullOfOrNull { it.author?.takeIf(String::isNotBlank) },
                publicationYear = records.firstNotNullOfOrNull { it.publicationYear },
                isbn = BookIdentity.canonicalIsbn(preferred.isbn) ?: records.firstNotNullOfOrNull { BookIdentity.canonicalIsbn(it.isbn) },
                coverUrl = records.firstNotNullOfOrNull { it.coverUrl?.takeIf(String::isNotBlank) },
                visualSimilarity = records.firstNotNullOfOrNull { it.visualSimilarity },
                textSimilarity = records.mapNotNull { it.textSimilarity }.maxOrNull(),
                shelfQuery = preferred.shelfQuery ?: records.firstNotNullOfOrNull { it.shelfQuery },
                sources = records.flatMap { it.sources }.toSet(),
                publisher = records.firstNotNullOfOrNull { it.publisher },
                fieldSources = values.mapValues { it.value.first().sources.joinToString() } + mapOfNotNull(
                    "cover" to records.firstOrNull { it.coverUrl != null }?.let { it.fieldSources["cover"] ?: it.sources.joinToString() }
                ),
                fieldValues = values
            )
        }

    /** Search OCR as several short title/author fragments, then rank title and author overlap. */
    suspend fun findByRecognizedText(text: String, maxQueries: Int = 4): List<CatalogBook> = CatalogAccess.search {
        val boundedText = text.take(2_000)
        val lines = boundedText.lines().map { it.trim() }
            .filter { it.length >= 3 && it.any(Char::isLetter) }
        val phraseWindows = lines.flatMap { line ->
            val words = line.split(Regex("\\s+")).filter { it.length >= 2 }
            (3..minOf(6, words.size)).sortedDescending().flatMap { size ->
                (words.size - size downTo 0).map { start -> words.subList(start, start + size).joinToString(" ") }
            } + (2..minOf(2, words.size)).flatMap { size ->
                (words.size - size downTo 0).map { start -> words.subList(start, start + size).joinToString(" ") }
            }
        }
        val queries = (listOf(lines.joinToString(" ")) + lines + phraseWindows)
            .map(::foldText)
            .filter { it.length >= 3 }
            .distinct()
            .take(maxQueries.coerceIn(1, 4))
        val candidates = mergeCandidates(queries.flatMapIndexed { index, query ->
            catchingOperation { findByTitle(query, expandSources = index < 2) }.getOrDefault(emptyList())
        })
        val tokens = foldText(boundedText).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        candidates.map { book ->
            val titleTokens = foldText(book.title).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
            val authorTokens = foldText(book.author.orEmpty()).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
            val titleMatch = tokenCoverage(titleTokens, tokens)
            val authorMatch = tokenCoverage(authorTokens, tokens)
            val evidenceCoverage = tokenCoverage(tokens, titleTokens + authorTokens)
            val score = titleMatch * .60f + authorMatch * .15f + evidenceCoverage * .25f
            book.copy(textSimilarity = (score * 100).toInt())
        }.sortedByDescending { it.textSimilarity ?: 0 }
    }

    /** Looks up isolated OCR blocks from several book spines without comparing the whole shelf as one cover. */
    suspend fun findByShelfText(spines: List<ShelfQuery>, onResult: (ShelfMatch) -> Unit): List<ShelfMatch> {
        val results = mutableListOf<ShelfMatch>()
        for (spine in spines) {
            val isbn = BookIdentity.unambiguousIsbn(spine.text)
            val matches = if (isbn != null) listOfNotNull(findByIsbn(isbn))
                else findByRecognizedText(spine.text, maxQueries = 2).take(5)
            val match = ShelfMatch(spine.id, matches)
            results += match
            onResult(match)
        }
        return results
    }

    private fun mapOfNotNull(vararg entries: Pair<String, String?>): Map<String, String> =
        entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

    private fun tokenCoverage(target: Set<String>, recognized: Set<String>): Float {
        if (target.isEmpty() || recognized.isEmpty()) return 0f
        val matched = target.count { word ->
            recognized.any { seen -> seen == word || (minOf(seen.length, word.length) >= 5 && (seen.startsWith(word) || word.startsWith(seen))) ||
                (minOf(seen.length, word.length) >= 6 && editDistanceAtMostOne(seen, word)) }
        }
        return matched.toFloat() / target.size
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val mismatches = a.indices.filter { a[it] != b[it] }
            if (mismatches.size == 2) {
                val first = mismatches[0]
                val second = mismatches[1]
                if (second == first + 1 && a[first] == b[second] && a[second] == b[first]) return true
            }
        }
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++ } else {
                if (++edits > 1) return false
                if (a.length > b.length) i++ else if (b.length > a.length) j++ else { i++; j++ }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }

    private fun foldText(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun catalogCover(coverId: Long?, isbn: String?): String? = when {
        coverId != null -> "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
        isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
        else -> null
    }

}
