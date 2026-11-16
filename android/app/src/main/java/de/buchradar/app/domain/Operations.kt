package de.buchradar.app.domain

import kotlinx.coroutines.CancellationException

inline fun <T> catchingOperation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
