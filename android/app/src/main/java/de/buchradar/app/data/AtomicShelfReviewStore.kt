package de.buchradar.app.data

import android.util.AtomicFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.buchradar.app.ShelfReviewState
import de.buchradar.app.ShelfReviewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stored under noBackupFilesDir: recognised book text is private local data. */
class AtomicShelfReviewStore(file: File) : ShelfReviewStore {
    private val baseFile = file
    private val atomic = AtomicFile(file)
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(ShelfReviewState::class.java)

    override suspend fun read(): ShelfReviewState = withContext(Dispatchers.IO) {
        if (!baseFile.exists() && !File(baseFile.path + ".bak").exists()) return@withContext ShelfReviewState()
        val text = atomic.openRead().bufferedReader().use { reader ->
            val buffer = CharArray(5_000_001)
            var size = 0
            while (size < buffer.size) {
                val read = reader.read(buffer, size, buffer.size - size)
                if (read < 0) break
                size += read
            }
            require(size <= 5_000_000)
            String(buffer, 0, size)
        }
        requireNotNull(adapter.fromJson(text)).also {
            require(it.version == 1 && it.groups.size <= 1_000)
            require(it.groups.map { group -> group.id }.distinct().size == it.groups.size)
        }
    }

    override suspend fun write(state: ShelfReviewState) = withContext(Dispatchers.IO) {
        val text = adapter.toJson(state)
        require(text.length <= 5_000_000)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}
