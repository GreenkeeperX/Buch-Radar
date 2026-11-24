@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.buchradar.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogAccess
import de.buchradar.app.ui.CoverImage
import de.buchradar.app.ui.InfoBanner
import de.buchradar.app.ui.LocalStatusColors
import de.buchradar.app.ui.MomoxStatusPill
import de.buchradar.app.ui.SectionTitle
import de.buchradar.app.ui.formatEuro
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    original: BookEntity,
    onClose: () -> Unit,
    onEdit: (BookEntity) -> Unit = {},
    onMarkSold: (BookEntity) -> Unit = {},
    onRestore: (BookEntity) -> Unit = {},
    onDelete: (BookEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val dao = remember { BuchRadarDatabase.get(context).bookDao() }
    val stored by dao.observeById(original.id).collectAsState(initial = original)
    val book = stored ?: original
    val history by dao.observeHistory(book.isbn).collectAsState(initial = emptyList())
    val pending by dao.observePendingIsbns().collectAsState(initial = emptyList())
    val queued = book.isbn.isNotBlank() && book.isbn in pending
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(scheme.surface)) {
        Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding(), topBar = {
            TopAppBar(title = { Text("Buchdetails") }, navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            }, actions = {
                // Same actions as the card menu in the list.
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Aktionen") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (book.soldAt == null) DropdownMenuItem(text = { Text("Als verkauft markieren") }, leadingIcon = { Icon(painterResource(R.drawable.ic_sold), null) },
                            onClick = { menuOpen = false; onMarkSold(book) })
                        else DropdownMenuItem(text = { Text("Zurück in Meine Bücher") }, leadingIcon = { Icon(painterResource(R.drawable.ic_books), null) },
                            onClick = { menuOpen = false; onRestore(book) })
                        DropdownMenuItem(text = { Text("Bearbeiten") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; onEdit(book) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Entfernen", color = scheme.error) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = scheme.error) },
                            onClick = { menuOpen = false; onDelete(book) })
                    }
                }
            })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CoverImage(book.coverUrl, book.title, 108.dp, 160.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.headlineSmall)
                            book.author?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant) }
                            if (book.isbn.isBlank()) Text("Keine ISBN – manuell erfasst", style = MaterialTheme.typography.bodyLarge)
                            book.publicationYear?.let { Text("Erscheinungsjahr $it", style = MaterialTheme.typography.bodyMedium) }
                            MomoxStatusPill(book)
                            book.lastCheckedAt?.let { Text("Stand ${formatDateTime(it)}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant) }
                        }
                    }
                }
                if (book.isbn.isNotBlank()) item {
                    Text(BookInputFormat.screenIsbn(book.isbn), modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (book.isbn.isNotBlank() && book.momoxStatus == "UNKNOWN" && book.lastCheckedAt != null && book.lastAttemptStatus != "ERROR") item {
                    InfoBanner("Momox kann diese ISBN keinem Artikel zuordnen. Prüfe, ob ISBN und Ausgabe stimmen; sonst kauft Momox dieses Buch nicht an.")
                }
                if (book.lastAttemptStatus == "ERROR") item {
                    InfoBanner("Letzte Prüfung fehlgeschlagen" + (book.lastError?.let { ": $it" } ?: "") + ". Das ältere Ergebnis bleibt erhalten.")
                }
                item {
                    // From 130 % font the two actions stack at full width; side by side their labels broke inside words.
                    val stacked = LocalConfiguration.current.fontScale >= 1.3f
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (stacked) 1 else 2) {
                        if (book.soldAt == null) FilledTonalButton(
                            enabled = book.isbn.isNotBlank() && !queued,
                            onClick = { scope.launch {
                                message = if (catchingOperation { MomoxCheckCoordinator.enqueue(context, listOf(book)) }.isSuccess) "Prüfung eingereiht. Das Ergebnis erscheint hier." else "Prüfung konnte nicht gestartet werden."
                            } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp))
                            Text(if (queued) "Wird geprüft …" else "Momox prüfen")
                        }
                        OutlinedButton(enabled = !loading && book.isbn.isNotBlank(), modifier = Modifier.weight(1f), onClick = { scope.launch {
                            loading = true
                            val result = catchingOperation {
                                val report = CatalogAccess.report { BookCatalog.findByIsbn(book.isbn) }
                                val metadata = report.value
                                if (metadata != null && metadata.conflictingFields.isEmpty()) dao.fillMissingMetadata(book.id, book.isbn, metadata.title, metadata.author, metadata.publicationYear, metadata.coverUrl, metadata.sources.joinToString())
                                report
                            }
                            loading = false
                            message = result.fold({ report ->
                                val text = when {
                                    report.value == null -> "Keine weiteren Buchdaten gefunden."
                                    report.value.conflictingFields.isNotEmpty() -> "Die Kataloge widersprechen sich. Keine Angaben verändert; bitte das Buch manuell bearbeiten."
                                    else -> "Fehlende Buchdaten ergänzt."
                                }
                                (listOf(text) + report.warnings).joinToString("\n")
                            }, { "Katalog nicht erreichbar. Bitte erneut versuchen." })
                        } }) {
                            Icon(Icons.Filled.Search, null, Modifier.padding(end = 6.dp))
                            Text(if (loading) "Suche …" else "Daten ergänzen")
                        }
                    }
                }
                message?.let { item { InfoBanner(it, onDismiss = { message = null }) } }
                item { HorizontalDivider(); SectionTitle("Prüfverlauf") }
                if (history.isEmpty()) item { Text("Noch kein gespeicherter Verlauf. Neue Prüfungen erscheinen hier.", color = scheme.onSurfaceVariant) }
                items(history, key = { it.id }) { check ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDateTime(check.checkedAt), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                            check.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
                        }
                        val (label, color) = when (check.status) {
                            "AVAILABLE" -> formatEuro((check.priceCents ?: 0).toLong()) to status.success
                            "NOT_AVAILABLE" -> "Kein Ankauf" to scheme.onSurfaceVariant
                            "ERROR" -> "Fehlgeschlagen" to scheme.error
                            else -> "ISBN unbekannt" to scheme.onSurfaceVariant
                        }
                        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
                    }
                }
                book.metadataSource?.let { source -> item {
                    Text("Datenquelle: $source", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } }
            }
        }
    }
}
