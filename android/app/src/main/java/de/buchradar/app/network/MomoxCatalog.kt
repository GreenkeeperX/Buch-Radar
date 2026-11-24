package de.buchradar.app.network

import android.os.SystemClock
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Header
import de.buchradar.app.BuildConfig
import de.buchradar.app.domain.BookIdentity
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CancellationException
import javax.net.ssl.SSLException

data class MomoxProduct(val ean: String?, val title: String?, val type: String?)
data class MomoxOfferResponse(
    val status: String?,
    val price: String?,
    val currency: String?,
    val product: MomoxProduct?
)
data class MomoxCheck(val status: String, val priceCents: Int?, val title: String?)

private interface MomoxApi {
    @GET("api/v4/media/offer/")
    @Headers(
        "Accept: application/json",
        "User-Agent: momox/11.0 (Android)",
        "X-MARKETPLACE-ID: momox_de"
    )
    suspend fun offer(@Query("ean") isbn: String, @Header("X-API-TOKEN") token: String): MomoxOfferResponse
}

object MomoxCatalog {
    /** Pause reason of a build without access; a later build with access resumes such a run. */
    const val MISSING_ACCESS = "Momox-Zugang ist für diesen Build nicht eingerichtet."
    private const val TAG = "BuchRadar.Momox"
    private val api: MomoxApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl("https://api.momox.de/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MomoxApi::class.java)
    }

    suspend fun check(isbn: String): MomoxCheck {
        if (BuildConfig.MOMOX_API_TOKEN.isBlank()) throw MomoxCheckException(MISSING_ACCESS, null, retryable = false, pauseQueue = true)
        val startedAt = SystemClock.elapsedRealtime()
        val response = try {
            api.offer(BookIdentity.canonicalIsbn(isbn) ?: throw IllegalArgumentException("Ungültige ISBN"), BuildConfig.MOMOX_API_TOKEN)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            val code = error.code()
            Log.w(TAG, "HTTP $code after ${SystemClock.elapsedRealtime() - startedAt} ms")
            throw MomoxCheckException(httpFailureMessage(code), error, retryable = code == 429 || code >= 500, pauseQueue = code in listOf(401, 403, 404), retryAfterSeconds = error.response()?.headers()?.get("Retry-After")?.toLongOrNull())
        } catch (error: com.squareup.moshi.JsonDataException) {
            // Readable JSON with unexpected types: the same "unreadable answer" rule as an unknown status.
            Log.w(TAG, "Unreadable response: ${error.javaClass.simpleName}")
            throw MomoxCheckException("Momox-Antwort nicht lesbar (unerwartete Felder)", error, retryable = false, unknownFormat = true)
        } catch (error: IOException) {
            if (error is com.squareup.moshi.JsonEncodingException) {
                Log.w(TAG, "Unreadable response: ${error.javaClass.simpleName}")
                throw MomoxCheckException("Momox-Antwort nicht lesbar (kein gültiges JSON)", error, retryable = false, unknownFormat = true)
            }
            val category = when (error) {
                is UnknownHostException -> "DNS: Momox-Server nicht erreichbar"
                is SocketTimeoutException -> "Timeout: Momox antwortet nicht rechtzeitig"
                is SSLException -> "TLS: sichere Verbindung zu Momox fehlgeschlagen"
                is ConnectException -> "Verbindung zu Momox abgelehnt"
                else -> "Netzwerkfehler: ${error.javaClass.simpleName}"
            }
            Log.w(TAG, "$category after ${SystemClock.elapsedRealtime() - startedAt} ms")
            throw MomoxCheckException(category, error)
        } catch (error: Exception) {
            Log.e(TAG, "Unexpected response failure: ${error.javaClass.simpleName}")
            throw MomoxCheckException("Antwort konnte nicht verarbeitet werden (${error.javaClass.simpleName})", error, retryable = false, pauseQueue = true)
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "Response status=${response.status ?: "missing"}, elapsed=${elapsed} ms")
        return validateOffer(isbn, response)
    }

    internal fun validateOffer(isbn: String, response: MomoxOfferResponse): MomoxCheck {
        val wanted = BookIdentity.canonicalIsbn(isbn)
            ?: throw MomoxCheckException("Ungültige ISBN", retryable = false)
        val returned = response.product?.ean
        if (returned != null && BookIdentity.canonicalIsbn(returned) != wanted)
            throw MomoxCheckException("Momox-Antwort gehört zu einer anderen Ausgabe.", retryable = false)
        return when (response.status?.lowercase()) {
            "offer" -> {
                if (!response.currency.equals("EUR", ignoreCase = true) || returned == null)
                    throw MomoxCheckException("Momox-Antwort enthält keine bestätigte ISBN oder EUR-Währung.", retryable = false)
                if (!response.product.type.equals("book", ignoreCase = true))
                    throw MomoxCheckException("Momox bestätigt dieses Angebot nicht als Buch.", retryable = false)
                val amount = response.price?.toBigDecimalOrNull()
                    ?: throw MomoxCheckException("Ungültiger Ankaufspreis", retryable = false)
                val cents = try { amount.movePointRight(2).intValueExact() }
                    catch (_: ArithmeticException) { throw MomoxCheckException("Ungültiger Ankaufspreis", retryable = false) }
                if (cents <= 0) throw MomoxCheckException("Ungültiger Ankaufspreis", retryable = false)
                MomoxCheck("AVAILABLE", cents, response.product.title)
            }
            "no_offer" -> MomoxCheck("NOT_AVAILABLE", null, response.product?.title)
            "unknown" -> MomoxCheck("UNKNOWN", null, response.product?.title)
            // Seen on the device (2026-11-24): Momox cannot match this ISBN to an article it buys.
            // A final answer for this book, like "unknown"; no retry, no pause.
            "product_error" -> MomoxCheck("UNKNOWN", null, response.product?.title)
            // One odd answer marks only this book; the coordinator pauses after several in a row.
            else -> throw MomoxCheckException(unknownFormatMessage(response.status), retryable = false, unknownFormat = true)
        }
    }

    /**
     * Momox answered with HTTP 200 but a status the app does not know (or none). Nothing is guessed
     * ("kein Angebot" would be wrong); the received status is named so the cause is visible.
     */
    internal fun unknownFormatMessage(status: String?): String {
        val shown = status?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }?.take(40)?.takeIf(String::isNotBlank)
        return if (shown == null) "Momox-Antwort ohne Status" else "Unbekannter Momox-Status „$shown“"
    }

    private fun httpFailureMessage(code: Int): String = when (code) {
        401, 403 -> "Momox verweigert die Anfrage (HTTP $code); Schnittstellenzugriff/Header prüfen"
        429 -> "Momox begrenzt die Anfragen (HTTP 429); später erneut versuchen"
        in 500..599 -> "Momox-Serverfehler (HTTP $code); später erneut versuchen"
        else -> "Momox antwortet mit HTTP $code"
    }
}

/** [unknownFormat]: an answer the app cannot read; the book gets an error, repeated ones pause the queue. */
class MomoxCheckException(message: String, cause: Throwable? = null, val retryable: Boolean = true, val pauseQueue: Boolean = false,
    val retryAfterSeconds: Long? = null, val unknownFormat: Boolean = false) : Exception(message, cause)
