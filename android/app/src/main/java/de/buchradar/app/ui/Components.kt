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
fun InfoBanner(text: String, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
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
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
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
fun StatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, strong: Boolean = false) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center
        )
    }
}

/** Momox state of a book as one readable pill; price is the dominant element. */
@Composable
fun MomoxStatusPill(book: BookEntity, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val label: String
    val container: Color
    val content: Color
    var strong = false
    when {
        book.isbn.isBlank() -> { label = "Ohne ISBN nicht prüfbar"; container = status.neutralContainer; content = status.onNeutralContainer }
        book.momoxStatus == "AVAILABLE" -> {
            label = formatEuro((book.currentPriceCents ?: 0).toLong()); strong = true
            container = status.successContainer; content = status.onSuccessContainer
        }
        book.momoxStatus == "NOT_AVAILABLE" -> { label = "Kein Ankauf"; container = scheme.surfaceContainerHighest; content = scheme.onSurfaceVariant }
        book.momoxStatus == "ERROR" -> { label = "Prüfung fehlgeschlagen"; container = scheme.errorContainer; content = scheme.onErrorContainer }
        book.lastCheckedAt == null -> { label = "Noch nicht geprüft"; container = scheme.secondaryContainer; content = scheme.onSecondaryContainer }
        else -> { label = "Bei Momox unbekannt"; container = status.neutralContainer; content = status.onNeutralContainer }
    }
    StatusPill(label, container, content, modifier.semantics { contentDescription = "Momox: $label" }, strong)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
