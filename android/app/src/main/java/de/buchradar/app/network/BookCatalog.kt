package de.buchradar.app.network

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
    val subject: List<String>? = null
)

data class OpenLibraryBookData(
    val title: String? = null,
    @Json(name = "publish_date") val publishDate: String? = null,
    val authors: List<OpenLibraryAuthorData>? = null,
    val cover: OpenLibraryCoverData? = null
)
data class OpenLibraryAuthorData(val name: String? = null)
data class OpenLibraryCoverData(val small: String? = null, val medium: String? = null, val large: String? = null)

data class CatalogBook(
    val title: String,
    val author: String?,
    val publicationYear: Int?,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val visualSimilarity: Int? = null,
    val textSimilarity: Int? = null,
    val shelfQuery: String? = null,
    val sources: Set<String> = emptySet()
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
    @GET("search.json")
    @Headers("User-Agent: BuchRadar/0.3 (personal ISBN lookup)")
    suspend fun findByIsbn(
        @Query("isbn") isbn: String,
        @Query("fields") fields: String = "isbn,title,author_name,first_publish_year,cover_i",
        @Query("limit") limit: Int = 5
    ): CatalogSearchResponse

    @GET("search.json")
    @Headers("User-Agent: BuchRadar/0.3 (personal book lookup)")
    suspend fun findByTitle(
        @Query("title") title: String,
        @Query("fields") fields: String = "isbn,title,author_name,first_publish_year,cover_i",
        @Query("limit") limit: Int = 12
    ): CatalogSearchResponse

    @GET("api/books")
    @Headers("User-Agent: BuchRadar/0.9 (ISBN metadata fallback)")
    suspend fun findBookDataByIsbn(
        @Query("bibkeys") bibkeys: String,
        @Query("jscmd") command: String = "data",
        @Query("format") format: String = "json"
    ): Map<String, OpenLibraryBookData>
}

interface GoogleBooksApi {
    @GET("volumes")
    suspend fun search(@Query("q") query: String, @Query("maxResults") maxResults: Int = 5): GoogleVolumesResponse
}

interface DnbSruApi {
    @GET("sru/dnb")
    suspend fun search(
        @Query("version") version: String = "1.1",
        @Query("operation") operation: String = "searchRetrieve",
        @Query("query") query: String,
        @Query("maximumRecords") maximumRecords: Int = 5,
        @Query("recordSchema") recordSchema: String = "MARC21-xml"
    ): ResponseBody
}

object BookCatalog {
    private val api: OpenLibraryApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenLibraryApi::class.java)
    }
    private val googleApi: GoogleBooksApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder().baseUrl("https://www.googleapis.com/books/v1/")
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
            .create(GoogleBooksApi::class.java)
    }
    private val dnbApi: DnbSruApi by lazy {
        Retrofit.Builder().baseUrl("https://services.dnb.de/")
            .build().create(DnbSruApi::class.java)
    }

    suspend fun findByIsbn(isbn: String): CatalogBook? {
        val wanted = isbn.normalizedIsbn()
        val match = runCatching { api.findByIsbn(wanted).docs.firstOrNull { document ->
            document.isbn.orEmpty().any { it.normalizedIsbn() == wanted }
        } }.getOrNull()
        val title = match?.title?.trim()?.takeIf(String::isNotEmpty)
        val openLibraryBook = if (match != null && title != null) CatalogBook(
            title = title,
            author = match.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank),
            publicationYear = match.firstPublishYear,
            isbn = wanted,
            coverUrl = catalogCover(match.coverId, wanted),
            sources = setOf("Open Library")
        ) else null

        // The legacy Books API can return exact edition metadata when search.json has no indexed hit.
        val record = runCatching { api.findBookDataByIsbn("ISBN:$wanted")["ISBN:$wanted"] }.getOrNull()
        val fallbackTitle = record?.title?.trim()?.takeIf(String::isNotEmpty)
        val legacyBook = if (record != null && fallbackTitle != null) {
            val year = Regex("\\b(1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b").find(record.publishDate.orEmpty())?.value?.toIntOrNull()
            CatalogBook(
                title = fallbackTitle,
                author = record.authors.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.joinToString(", ").takeIf(String::isNotBlank),
                publicationYear = year,
                isbn = wanted,
                coverUrl = record.cover?.large ?: record.cover?.medium ?: catalogCover(null, wanted),
                sources = setOf("Open Library")
            )
        } else null
        val googleBook = runCatching { googleApi.search("isbn:$wanted").items.orEmpty().firstNotNullOfOrNull { volume ->
                val info = volume.volumeInfo ?: return@firstNotNullOfOrNull null
                val cleanTitle = info.title?.trim()?.takeIf(String::isNotEmpty) ?: return@firstNotNullOfOrNull null
                val ids = info.identifiers.orEmpty().mapNotNull { it.identifier?.normalizedIsbn() }
                if (ids.isNotEmpty() && wanted !in ids) return@firstNotNullOfOrNull null
                CatalogBook(cleanTitle, info.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank), info.publishedDate?.take(4)?.toIntOrNull(), wanted,
                    (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)?.replace("http://", "https://"),
                    sources = setOf("Google Books"))
            } }.getOrNull()
        var combined = mergeCandidates(listOfNotNull(openLibraryBook, legacyBook, googleBook))
        if (combined.isEmpty() || combined.first().author == null || combined.first().publicationYear == null) {
            val dnb = runCatching { dnbSearch("num=$wanted").firstOrNull { it.isbn?.normalizedIsbn() == wanted } }.getOrNull()
            combined = mergeCandidates(combined + listOfNotNull(dnb))
        }
        return combined.firstOrNull()
    }

    suspend fun findByTitle(title: String): List<CatalogBook> {
        val openLibrary = runCatching { api.findByTitle(title.trim()) }.getOrNull()?.docs.orEmpty().mapNotNull { document ->
            val cleanTitle = document.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val validIsbn = document.isbn.orEmpty().firstNotNullOfOrNull { raw ->
                runCatching { ISBN.valueOf(raw) }.getOrNull()?.let { it.toCompactString() }
            }
            CatalogBook(
                title = cleanTitle,
                author = document.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank),
                publicationYear = document.firstPublishYear,
                isbn = validIsbn,
                coverUrl = catalogCover(document.coverId, validIsbn),
                sources = setOf("Open Library")
            )
        }
        val primary = openLibrary.distinctBy { it.isbn ?: it.title }
        if (primary.size >= 3) return primary
        val google = runCatching { googleApi.search(title.trim(), 10).items.orEmpty().mapNotNull { volume ->
            val info = volume.volumeInfo ?: return@mapNotNull null
            val cleanTitle = info.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val validIsbn = info.identifiers.orEmpty().firstNotNullOfOrNull { id -> id.identifier?.let { raw -> runCatching { ISBN.valueOf(raw) }.getOrNull()?.toCompactString() } }
            CatalogBook(cleanTitle, info.authors.orEmpty().joinToString(", ").takeIf(String::isNotBlank), info.publishedDate?.take(4)?.toIntOrNull(), validIsbn,
                (info.imageLinks?.thumbnail ?: info.imageLinks?.smallThumbnail)?.replace("http://", "https://"), sources = setOf("Google Books"))
        } }.getOrDefault(emptyList())
        val combined = mergeCandidates(primary + google)
        return if (combined.size >= 3) combined else mergeCandidates(combined + runCatching { dnbSearch("tit=${title.trim()}") }.getOrDefault(emptyList()))
    }

    /** Fetches only a small, on-demand result set; DNB supplies bibliographic metadata, not covers. */
    private suspend fun dnbSearch(query: String): List<CatalogBook> = runCatching {
        parseDnbRecords(dnbApi.search(query = query).string())
    }.getOrDefault(emptyList())

    private fun parseDnbRecords(xml: String): List<CatalogBook> {
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
                                .map { it.substringBefore(",").trim() }.distinct()
                            val isbn = fields["020:a"].orEmpty().firstNotNullOfOrNull { raw ->
                                raw.filter { it.isDigit() || it.equals('x', true) }.let { compact ->
                                    runCatching { ISBN.valueOf(compact).toCompactString() }.getOrNull()
                                }
                            }
                            val year = (fields["264:c"].orEmpty() + fields["260:c"].orEmpty())
                                .firstNotNullOfOrNull { Regex("\\b(1[5-9]\\d{2}|20\\d{2}|21\\d{2})\\b").find(it)?.value?.toIntOrNull() }
                            results += CatalogBook(cleanTitle, authors.joinToString(", ").takeIf(String::isNotBlank), year, isbn,
                                coverUrl = null, sources = setOf("Deutsche Nationalbibliothek"))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    /** Same ISBNs represent one edition; records without ISBN merge only when title and author agree. */
    private fun mergeCandidates(candidates: List<CatalogBook>): List<CatalogBook> = candidates
        .filter { it.title.isNotBlank() }
        .groupBy { candidate ->
            candidate.isbn?.let { "isbn:${it.normalizedIsbn()}" }
                ?: "text:${foldText(candidate.title)}|${foldText(candidate.author.orEmpty())}"
        }
        .values.map { records ->
            val preferred = records.first()
            preferred.copy(
                title = records.firstOrNull { it.title.isNotBlank() }?.title ?: preferred.title,
                author = records.firstNotNullOfOrNull { it.author?.takeIf(String::isNotBlank) },
                publicationYear = records.firstNotNullOfOrNull { it.publicationYear },
                isbn = preferred.isbn ?: records.firstNotNullOfOrNull { it.isbn },
                coverUrl = records.firstNotNullOfOrNull { it.coverUrl?.takeIf(String::isNotBlank) },
                visualSimilarity = records.firstNotNullOfOrNull { it.visualSimilarity },
                textSimilarity = records.mapNotNull { it.textSimilarity }.maxOrNull(),
                shelfQuery = preferred.shelfQuery ?: records.firstNotNullOfOrNull { it.shelfQuery },
                sources = records.flatMap { it.sources }.toSet()
            )
        }

    /** Search OCR as several short title/author fragments, then rank title and author overlap. */
    suspend fun findByRecognizedText(text: String, maxQueries: Int = 10): List<CatalogBook> {
        val lines = text.lines().map { it.trim() }
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
            .take(maxQueries.coerceAtLeast(1))
        val candidates = queries.flatMap { query -> runCatching { findByTitle(query) }.getOrDefault(emptyList()) }
            .distinctBy { it.isbn ?: it.title }
        val tokens = foldText(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        return candidates.map { book ->
            val titleTokens = foldText(book.title).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
            val authorTokens = foldText(book.author.orEmpty()).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
            val titleMatch = tokenCoverage(titleTokens, tokens)
            val authorMatch = tokenCoverage(authorTokens, tokens)
            book.copy(textSimilarity = ((titleMatch * .82f + authorMatch * .18f) * 100).toInt())
        }.sortedByDescending { it.textSimilarity ?: 0 }
    }

    /** Looks up isolated OCR blocks from several book spines without comparing the whole shelf as one cover. */
    suspend fun findByShelfText(spines: List<String>): List<CatalogBook> {
        val cleanSpines = spines.map { it.lines().joinToString(" ").trim() }
            .filter { it.length >= 3 && it.any(Char::isLetter) }
            .distinctBy(::foldText)
            .take(24)
        val results = mutableListOf<CatalogBook>()
        cleanSpines.forEachIndexed { index, spine ->
            if (index > 0) delay(1_000)
            val isbn = spine.filter { it.isDigit() || it.equals('x', true) }
            val matches = if (isbn.length in listOf(10, 13)) {
                runCatching { findByIsbn(isbn).let { listOfNotNull(it) } }.getOrDefault(emptyList())
            } else {
                val direct = runCatching { findByTitle(foldText(spine)) }.getOrDefault(emptyList())
                if (direct.isNotEmpty()) direct else runCatching { findByRecognizedText(spine, maxQueries = 3).take(4) }.getOrDefault(emptyList())
            }
            results += matches.take(5).map { it.copy(shelfQuery = spine) }
        }
        return results
    }

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

    private fun foldText(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun catalogCover(coverId: Long?, isbn: String?): String? = when {
        coverId != null -> "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
        isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
        else -> null
    }

    private fun String.normalizedIsbn(): String =
        filter { it.isDigit() || it.equals('x', ignoreCase = true) }.uppercase()
}
