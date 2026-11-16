package de.buchradar.app.network

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.POST

data class CheckRequest(val isbns: List<String>)
data class BookCheck(val isbn: String, val status: String, @Json(name = "price_cents") val priceCents: Int?, @Json(name = "checked_at") val checkedAt: Long?)
data class CheckResponse(val results: List<BookCheck>)

interface BackendApi {
    @POST("v1/books/check")
    suspend fun checkBooks(@Body request: CheckRequest): CheckResponse
}
