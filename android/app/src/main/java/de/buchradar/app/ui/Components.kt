package de.buchradar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.buchradar.app.R
import de.buchradar.app.data.BookEntity
import de.buchradar.app.domain.MessageTone
import de.buchradar.app.domain.messageTone
import java.text.NumberFormat
import java.util.Locale

fun formatEuro(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(cents / 100.0)

/** Status line with tone-dependent colour and icon; announced by TalkBack. */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, compact: Boolean = false, onDismiss: (() -> Unit)? = null) {
    val tone = messageTone(text)
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        MessageTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        MessageTone.SUCCESS -> status.successContainer to status.onSuccessContainer
        MessageTone.PROGRESS, MessageTone.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Surface(
        color = container, contentColor = content, shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        val verticalPadding = if (compact) 0.dp else 8.dp
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = verticalPadding, bottom = verticalPadding), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tone) {
                MessageTone.PROGRESS -> CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.5.dp)
                MessageTone.ERROR -> Icon(Icons.Filled.Warning, contentDescription = null)
                MessageTone.SUCCESS -> Icon(Icons.Filled.CheckCircle, contentDescription = null)
                MessageTone.INFO -> Icon(Icons.Filled.Info, contentDescription = null)
            }
            Text(text, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null && tone != MessageTone.PROGRESS) IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Hinweis schließen")
            } else Box(Modifier.size(8.dp))
        }
    }
}

/** Cover with a calm placeholder for missing or broken images. */
@Composable
fun CoverImage(url: String?, title: String?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Icon(painterResource(R.drawable.ic_book), contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(width / 2.2f))
        } else {
            AsyncImage(
                model = url, contentDescription = "Cover: ${title ?: "Buch"}",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        }
    }
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, strong: Boolean = false, maxLines: Int = 1) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, maxLines = maxLines, textAlign = TextAlign.Center
        )
    }
}

/**
 * Momox state of a book. Only an offered price is a coloured pill, so colour alone carries
 * the "worth selling" signal; other states are a quiet line of text with an icon.
 */
@Composable
fun MomoxStatusPill(book: BookEntity, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val offered = de.buchradar.app.domain.BookListQuery.offeredCents(book)
    if (book.isbn.isNotBlank() && offered != null) {
        val label = formatEuro(offered.toLong())
        StatusPill(label, status.successContainer, status.onSuccessContainer, modifier.semantics { contentDescription = "Momox-Ankauf: $label" }, strong = true)
        return
    }
    val error = book.isbn.isNotBlank() && book.momoxStatus == "ERROR"
    val label = when {
        book.isbn.isBlank() -> "Ohne ISBN nicht prüfbar"
        book.momoxStatus == "NOT_AVAILABLE" -> "Kein Ankauf"
        error -> "Prüfung fehlgeschlagen"
        book.lastCheckedAt == null -> "Noch nicht geprüft"
        else -> "Bei Momox unbekannt"
    }
    val color = if (error) scheme.error else scheme.onSurfaceVariant
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "Momox: $label" }, verticalAlignment = Alignment.CenterVertically) {
        Icon(if (error) Icons.Filled.Warning else Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** One visible minimum height (Material's 40 dp) for all filled, outlined, tonal and segmented buttons; touch targets stay 48 dp via minimumInteractiveComponentSize. */
val ActionButtonHeight = androidx.compose.material3.ButtonDefaults.MinHeight

/** Higher visible height for finding, searching and checking actions (search row, add candidate, Momox check, scan more). */
val ImportantButtonHeight = 48.dp
