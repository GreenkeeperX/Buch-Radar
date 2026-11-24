package de.buchradar.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.SaleList
import de.buchradar.app.ui.LocalStatusColors
import de.buchradar.app.ui.formatEuro

/**
 * Sale preparation: choose among the books Momox bought at the last check, see the sum,
 * re-check old prices, share the list and mark the chosen books as sold (with undo in the list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleScreen(
    books: List<BookEntity>,
    onClose: () -> Unit,
    onRecheck: (List<BookEntity>) -> Unit,
    onMarkSold: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val dao = remember { BuchRadarDatabase.get(context).bookDao() }
    val pending by dao.observePendingIsbns().collectAsState(initial = emptyList())
    val candidates = remember(books) { SaleList.candidates(books) }
    // Unticked books stay unticked; everything else, including books that become available later, is chosen.
    var excluded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val chosen = candidates.filter { it.id !in excluded }
    val now = System.currentTimeMillis()
    val stale = chosen.filter { SaleList.needsRecheck(it, now) && it.isbn !in pending }
    val total = SaleList.totalCents(chosen)
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(scheme.surface)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            topBar = {
                TopAppBar(title = { Text("Verkaufen") }, navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zur Liste") }
                })
            },
            bottomBar = {
                if (candidates.isNotEmpty()) Surface(color = scheme.surfaceContainer, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (chosen.size == 1) "1 Buch ausgewählt" else "${chosen.size} Bücher ausgewählt",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(formatEuro(total), style = MaterialTheme.typography.titleLarge, color = status.success, fontWeight = FontWeight.Bold)
                        }
                        if (chosen.isNotEmpty() && total < SaleList.MINIMUM_CENTS) Text(
                            "Noch ${formatEuro(SaleList.MINIMUM_CENTS - total)} bis zum Mindestankaufswert von ${formatEuro(SaleList.MINIMUM_CENTS.toLong())}.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Bücher für Momox")
                                    .putExtra(Intent.EXTRA_TEXT, SaleList.shareText(chosen, System.currentTimeMillis()))
                                context.startActivity(Intent.createChooser(send, "Liste teilen"))
                            }, enabled = chosen.isNotEmpty(), modifier = Modifier.heightIn(min = 52.dp)) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 8.dp)); Text("Teilen")
                            }
                            Button(onClick = { onMarkSold(chosen.map { it.id }) }, enabled = chosen.isNotEmpty(),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                Icon(painterResource(R.drawable.ic_sold), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Als verkauft markieren")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (candidates.isEmpty()) "Momox kauft laut letzter Prüfung keines deiner Bücher an. Prüfe die Liste erneut, um aktuelle Angebote zu sehen."
                            else "Diese Bücher kauft Momox laut letzter Prüfung an. Die Preise sind kein verbindliches Angebot; maßgeblich ist das Angebot beim Verkauf.",
                            style = MaterialTheme.typography.bodyLarge)
                        if (stale.isNotEmpty()) FilledTonalButton(onClick = { onRecheck(stale) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (stale.size == 1) "1 älteren Preis neu prüfen" else "${stale.size} ältere Preise neu prüfen")
                        }
                    }
                }
                items(candidates, key = { it.id }) { book ->
                    val selected = book.id !in excluded
                    val queued = book.isbn in pending
                    Card(colors = CardDefaults.cardColors(containerColor = if (selected) scheme.surfaceContainer else scheme.surface),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { on ->
                                    excluded = if (on) excluded - book.id else excluded + book.id
                                })
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The whole row toggles; the box only shows the state.
                            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.titleMedium)
                                book.author?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                                Text("ISBN ${BookInputFormat.displayIsbn(book.isbn)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                when {
                                    queued -> Text("Wird gerade neu geprüft …", style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
                                    SaleList.needsRecheck(book, now) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Warning, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                                        Text(book.lastCheckedAt?.let { "Preis vom ${formatDateTime(it)}" + if (book.lastAttemptStatus == "ERROR") ", letzte Prüfung fehlgeschlagen" else "" }
                                            ?: "Noch nicht geprüft", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text(formatEuro((BookListQuery.offeredCents(book) ?: 0).toLong()), Modifier.padding(start = 8.dp, end = 4.dp),
                                style = MaterialTheme.typography.titleMedium, color = status.success, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
