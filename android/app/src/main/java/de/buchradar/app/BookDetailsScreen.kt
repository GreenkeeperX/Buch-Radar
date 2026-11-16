package de.buchradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogAccess
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(original: BookEntity, onClose: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { BuchRadarDatabase.get(context).bookDao() }
    val stored by dao.observeById(original.id).collectAsState(initial = original)
    val book = stored ?: original
    val history by dao.observeHistory(book.isbn).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding(), topBar = { TopAppBar(title = { Text("Buchdetails") }, navigationIcon = { TextButton(onClick = onClose) { Text("Zurück") } }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.headlineSmall) }
                item { book.author?.let { Text(it, style = MaterialTheme.typography.titleLarge) }; Text(if (book.isbn.isBlank()) "Keine ISBN – manuell erfasst" else "ISBN ${book.isbn}", style = MaterialTheme.typography.bodyLarge) }
                item { book.metadataSource?.let { Text("Datenquelle: $it") } }
                item { OutlinedButton(enabled = !loading && book.isbn.isNotBlank(), onClick = { scope.launch {
                    loading = true
                    val result = catchingOperation {
                        val report = CatalogAccess.report { BookCatalog.findByIsbn(book.isbn) }
                        val metadata = report.value
                        if (metadata != null && metadata.conflictingFields.isEmpty()) dao.fillMissingMetadata(book.id, book.isbn, metadata.title, metadata.author, metadata.publicationYear, metadata.coverUrl, metadata.sources.joinToString())
                        report
                    }
                    loading = false
                    message = result.fold({ report ->
                        val status = when {
                            report.value == null -> "Keine weiteren Buchdaten gefunden."
                            report.value.conflictingFields.isNotEmpty() -> "Die Kataloge widersprechen sich. Keine Angaben verändert; bitte das Buch manuell bearbeiten."
                            else -> "Fehlende Buchdaten ergänzt."
                        }
                        (listOf(status) + report.warnings).joinToString("\n")
                    }, { "Katalog nicht erreichbar. Bitte erneut versuchen." })
                } }) { Text(if(loading) "Suche Buchdaten …" else "Fehlende Buchdaten ergänzen") } }
                item { message?.let { Text(it) }; HorizontalDivider(); Text("Prüfverlauf", style = MaterialTheme.typography.titleLarge) }
                if (history.isEmpty()) item { Text("Noch kein gespeicherter Verlauf. Neue Prüfungen erscheinen hier.") }
                items(history, key = { it.id }) { check ->
                    Column {
                        Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(check.checkedAt)))
                        Text(when(check.status) {
                            "AVAILABLE" -> "Ankauf: ${NumberFormat.getCurrencyInstance(Locale.GERMANY).format((check.priceCents ?: 0) / 100.0)}"
                            "NOT_AVAILABLE" -> "Kein Ankauf"
                            "ERROR" -> "Prüfung fehlgeschlagen"
                            else -> "ISBN bei Momox unbekannt"
                        }, style = MaterialTheme.typography.titleMedium)
                        check.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
