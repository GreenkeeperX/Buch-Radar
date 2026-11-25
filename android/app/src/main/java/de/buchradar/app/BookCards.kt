@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.buchradar.app

import de.buchradar.app.ui.ActionButtonHeight
import de.buchradar.app.ui.ImportantButtonHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.BookListTotals
import de.buchradar.app.network.CatalogBook
import de.buchradar.app.ui.CoverImage
import de.buchradar.app.ui.LocalStatusColors
import de.buchradar.app.ui.MomoxStatusPill
import de.buchradar.app.ui.StatusPill
import de.buchradar.app.ui.formatEuro
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatDateTime(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(millis))

/**
 * Overview above the list: title and counts, then the purchase value with the sale action.
 * Stacked rather than side by side, so large system fonts never squeeze the title into broken words.
 */
@Composable
internal fun ListOverview(title: String, totals: BookListTotals, showValue: Boolean, onSell: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            buildString {
                append(if (totals.count == 1) "1 Buch" else "${totals.count} Bücher")
                if (showValue && totals.uncheckedCount > 0) append(" · ${totals.uncheckedCount} ungeprüft")
                if (showValue && totals.errorCount > 0) append(" · ${totals.errorCount} mit Fehler")
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showValue && totals.availableCount > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val status = LocalStatusColors.current
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(formatEuro(totals.availableValueCents), style = MaterialTheme.typography.titleLarge,
                    color = status.success, fontWeight = FontWeight.Bold)
                Text("Ankauf für ${totals.availableCount} ${if (totals.availableCount == 1) "Buch" else "Bücher"}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onSell != null) FilledTonalButton(onClick = onSell, modifier = Modifier.padding(start = 8.dp).heightIn(min = ActionButtonHeight)) {
                Text("Verkaufen")
            }
        }
    }
}

/** "heute 14:05", "gestern 09:12" or "21.11.2026": short enough for one quiet line. */
internal fun formatCheckedAt(millis: Long, now: Long = System.currentTimeMillis()): String {
    fun midnight(t: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = t; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(millis))
    // Rounded day difference between local midnights, so daylight saving and year ends count right.
    return when (Math.round((midnight(now) - midnight(millis)) / 86_400_000.0)) {
        0L -> "heute $time"
        1L -> "gestern $time"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(millis))
    }
}

@Composable
internal fun MomoxActionRow(lastCheckedAt: Long?, errors: Int, canCheck: Boolean, checkDue: Boolean, onShowErrors: () -> Unit, onCheck: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center) {
        lastCheckedAt?.let {
            Text("Geprüft ${formatCheckedAt(it)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (errors > 0) TextButton(onClick = onShowErrors, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("$errors Fehler im Bestand", color = scheme.error)
        }
        val label: @Composable () -> Unit = {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Momox prüfen")
        }
        // Quiet link while the list is fresh; a real button once a check is due.
        if (canCheck && checkDue) FilledTonalButton(onClick = onCheck, modifier = Modifier.heightIn(min = ImportantButtonHeight)) { label() }
        else if (canCheck) TextButton(onClick = onCheck, modifier = Modifier.heightIn(min = 48.dp)) { label() }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BookCard(
    book: BookEntity,
    selectionMode: Boolean,
    selected: Boolean,
    isSold: Boolean,
    checkQueued: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit,
    onRestore: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMomoxCheck: () -> Unit,
    onMarkSold: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClickLabel = if (selectionMode) "Auswahl ändern" else "Details öffnen",
            onClick = { if (selectionMode) onSelect() else onOpen() },
            onLongClickLabel = "Buch auswählen", onLongClick = onLongSelect
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.surfaceContainerHighest else scheme.surfaceContainerLow),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect() }, modifier = Modifier.align(Alignment.CenterVertically))
            CoverImage(book.coverUrl, book.title, 64.dp, 94.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    (if (book.isbn.isBlank()) "ISBN fehlt" else BookInputFormat.screenIsbn(book.isbn)) + (book.publicationYear?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MomoxStatusPill(book)
                    if (checkQueued) Text("wird geprüft …", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    // Direct check only where it is useful: never checked or the last attempt failed.
                    else if (!isSold && !selectionMode && book.isbn.isNotBlank() && (book.lastCheckedAt == null || book.lastAttemptStatus == "ERROR"))
                        TextButton(onClick = onMomoxCheck, contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.semantics { contentDescription = "${book.title ?: "Buch"} bei Momox prüfen" }) { Text("Prüfen") }
                }
                when {
                    book.lastAttemptStatus == "ERROR" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = scheme.error, modifier = Modifier.size(16.dp))
                        Text("Letzter Versuch fehlgeschlagen" + (book.lastCheckedAt?.let { " · Stand ${formatDateTime(it)}" } ?: ""),
                            color = scheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    book.lastCheckedAt != null -> Text("Stand ${formatDateTime(book.lastCheckedAt)}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
                if (isSold) book.soldAt?.let { Text("Verkauft am ${formatDateTime(it)}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant) }
            }
            if (!selectionMode) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Aktionen für ${book.title ?: "Buch"}") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isSold) DropdownMenuItem(text = { Text("Zurück in Meine Bücher") }, leadingIcon = { Icon(painterResource(R.drawable.ic_books), null) },
                            onClick = { menuOpen = false; onRestore() })
                        else DropdownMenuItem(text = { Text("Bei Momox prüfen") }, enabled = book.isbn.isNotBlank() && !checkQueued,
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = { menuOpen = false; onMomoxCheck() })
                        if (!isSold) DropdownMenuItem(text = { Text("Als verkauft markieren") }, leadingIcon = { Icon(painterResource(R.drawable.ic_sold), null) },
                            onClick = { menuOpen = false; onMarkSold() })
                        DropdownMenuItem(text = { Text("Bearbeiten") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; onEdit() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Entfernen", color = scheme.error) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = scheme.error) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CandidateCard(candidate: CatalogBook, alreadyAdded: Boolean, isSold: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverImage(candidate.coverUrl, candidate.title, 80.dp, 118.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                candidate.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                listOfNotNull(candidate.publisher, candidate.publicationYear?.toString()).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Text(candidate.isbn?.let { BookInputFormat.screenIsbn(it) } ?: "Keine ISBN",
                    style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
                candidate.textSimilarity?.let {
                    val (label, container, content) = when {
                        it >= 85 -> Triple("Titel und Autor passen gut", status.successContainer, status.onSuccessContainer)
                        it >= 60 -> Triple("Möglicher Treffer – Ausgabe prüfen", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                        else -> Triple("Unsicher – sorgfältig vergleichen", scheme.errorContainer, scheme.onErrorContainer)
                    }
                    StatusPill(label, container, content)
                }
                // Only ever a positive hint: the photographed cover shows this edition's design.
                if (candidate.coverConfirmed) StatusPill("Cover passt zu deinem Foto", status.successContainer, status.onSuccessContainer, maxLines = 3)
                var showSources by rememberSaveable(candidate.candidateId) { mutableStateOf(false) }
                if (candidate.sources.isNotEmpty()) TextButton(onClick = { showSources = !showSources }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    if (candidate.conflictingFields.isNotEmpty()) Icon(Icons.Filled.Warning, null, tint = scheme.tertiary, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                    Text(if (candidate.conflictingFields.isNotEmpty()) "Abweichende Angaben prüfen" else "Quellen und Buchdaten")
                    Icon(if (showSources) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
                if (showSources) {
                    Text("Quelle: ${candidate.sources.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    candidate.fieldValues.forEach { (field, values) ->
                        val label = when (field) { "title" -> "Titel"; "author" -> "Autor"; "year" -> "Jahr"; "publisher" -> "Verlag"; else -> field }
                        values.forEach { value -> Text("$label: ${value.value} (${value.sources.joinToString()})", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Button(onClick = onClick, enabled = !alreadyAdded, modifier = Modifier.fillMaxWidth().heightIn(min = ImportantButtonHeight)) {
                    Text(when { alreadyAdded -> "Bereits in Liste"; isSold -> "Zurück in meine Bücher"; candidate.isbn == null -> "Ohne ISBN erfassen"; else -> "Buch hinzufügen" })
                }
            }
        }
    }
}

@Composable
internal fun EditBookDialog(
    book: BookEntity,
    title: String = "Buch bearbeiten",
    onDismiss: () -> Unit,
    onSave: suspend (isbn: String, title: String, author: String) -> String?
) {
    var isbn by rememberSaveable(book.id) { mutableStateOf(BookInputFormat.displayIsbn(book.isbn)) }
    var titleText by rememberSaveable(book.id) { mutableStateOf(book.title.orEmpty()) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = isbn, onValueChange = { isbn = it },
                    label = { Text("ISBN (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                OutlinedTextField(value = titleText, onValueChange = { titleText = it }, label = { Text("Titel") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Autor/in") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            FilledTonalButton(modifier = Modifier.heightIn(min = ActionButtonHeight), enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    // A database failure must not crash the dialog; show it instead.
                    val result = de.buchradar.app.domain.catchingOperation { onSave(isbn, titleText, author) }
                    error = result.getOrElse { "Speichern fehlgeschlagen. Bitte erneut versuchen." }
                    saving = false
                    if (error == null) onDismiss()
                }
            }) { Text(if (saving) "Speichert …" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Abbrechen") } }
    )
}
