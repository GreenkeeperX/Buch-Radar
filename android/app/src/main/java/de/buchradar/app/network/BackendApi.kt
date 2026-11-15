package de.buchradar.app.network

import retrofit2.http.Body
import retrofit2.http.POST

data class CheckRequest(val isbns: List<String>)
data class BookCheck(val isbn: String, val status: String, val priceCents: Int?, val checkedAt: Long?)
data class CheckResponse(val results: List<BookCheck>)

interface BackendApi {
    @POST("v1/books/check")
    suspend fun checkBooks(@Body request: CheckRequest): CheckResponse
}
