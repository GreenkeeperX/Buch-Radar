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
        "X-API-TOKEN: 2231443b8fb511c7b6a0eb25a62577320bac69b6",
        "X-MARKETPLACE-ID: momox_de"
    )
    suspend fun offer(@Query("ean") isbn: String): MomoxOfferResponse
}

object MomoxCatalog {
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
        val startedAt = SystemClock.elapsedRealtime()
        val response = try {
            api.offer(isbn.filter(Char::isDigit))
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            val code = error.code()
            Log.w(TAG, "HTTP $code after ${SystemClock.elapsedRealtime() - startedAt} ms")
            throw MomoxCheckException(httpFailureMessage(code), error)
        } catch (error: IOException) {
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
            throw MomoxCheckException("Antwort konnte nicht verarbeitet werden (${error.javaClass.simpleName})", error)
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        Log.i(TAG, "Response status=${response.status ?: "missing"}, elapsed=${elapsed} ms")
        return when (response.status?.lowercase()) {
            "offer" -> {
                val price = response.price?.toBigDecimalOrNull()
                    ?: return MomoxCheck("ERROR", null, response.product?.title)
                MomoxCheck(
                    status = "AVAILABLE",
                    priceCents = price.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt(),
                    title = response.product?.title
                )
            }
            "no_offer" -> MomoxCheck("NOT_AVAILABLE", null, response.product?.title)
            "unknown" -> MomoxCheck("UNKNOWN", null, response.product?.title)
            else -> {
                Log.w(TAG, "Unrecognized Momox status value")
                MomoxCheck("UNKNOWN", null, response.product?.title)
            }
        }
    }

    private fun httpFailureMessage(code: Int): String = when (code) {
        401, 403 -> "Momox verweigert die Anfrage (HTTP $code); Schnittstellenzugriff/Header prüfen"
        429 -> "Momox begrenzt die Anfragen (HTTP 429); später erneut versuchen"
        in 500..599 -> "Momox-Serverfehler (HTTP $code); später erneut versuchen"
        else -> "Momox antwortet mit HTTP $code"
    }
}

class MomoxCheckException(message: String, cause: Throwable) : Exception(message, cause)
