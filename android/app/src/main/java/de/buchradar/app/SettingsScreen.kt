package de.buchradar.app

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import de.buchradar.app.data.*
import de.buchradar.app.domain.catchingOperation
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(theme: String, onTheme: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("buchradar_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("settings") }
    var automatic by remember { mutableStateOf(prefs.getBoolean("auto_check", false)) }
    var days by remember { mutableStateOf(prefs.getInt("auto_check_days", 7)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", false)) }
    var every by remember { mutableStateOf(prefs.getBoolean("notify_every_result", false)) }
    var threshold by remember { mutableStateOf(if (prefs.getBoolean("notify_threshold", false)) prefs.getInt("notify_threshold_cents", 1000) else 0) }
    var log by remember { mutableStateOf(AppLog.read(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf<List<BookEntity>?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var clearLog by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        notifications = allowed; prefs.edit().putBoolean("notifications", allowed).apply()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = catchingOperation { BookTransfer.export(context, uri); "Bücherliste gesichert." }.getOrElse { "Sicherung fehlgeschlagen." }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = catchingOperation { BookTransfer.preview(context, uri) }
            importing = result.getOrNull()
            if (result.isFailure) message = result.exceptionOrNull()?.message ?: "Sicherung nicht lesbar."
        }
    }
    val exportLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            message = catchingOperation {
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write("Bücher-Radar ${BuildConfig.VERSION_NAME}\n" + AppLog.read(context).joinToString("\n")) } }
                "Protokoll gespeichert."
            }.getOrElse { "Protokoll konnte nicht gespeichert werden." }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackHandler { if (page == "settings") onClose() else page = "settings" }
        Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding(), topBar = { TopAppBar(title = { Text(when(page) { "log" -> "Diagnose"; "privacy" -> "Datenschutz"; else -> "Einstellungen" }) }, navigationIcon = {
            IconButton(onClick = { if (page == "settings") onClose() else page = "settings" }) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
        }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { message?.let { de.buchradar.app.ui.InfoBanner(it, onDismiss = { message = null }) } }
                when(page) {
                    "settings" -> {
                        item { de.buchradar.app.ui.SectionTitle("Darstellung") }
                        item { ChoiceMenu(when(theme) { "dark" -> "Dunkel"; "light" -> "Hell"; else -> "Geräteeinstellung" }, listOf("system" to "Geräteeinstellung", "light" to "Hell", "dark" to "Dunkel")) { onTheme(it); prefs.edit().putString("theme", it).apply() } }
                        item { HorizontalDivider(); de.buchradar.app.ui.SectionTitle("Automatische Prüfung") }
                        item { SwitchRow("Regelmäßig Momox prüfen", automatic) { automatic = it; prefs.edit().putBoolean("auto_check", it).apply(); MomoxWorkScheduler.configure(context, it, days) } }
                        if (automatic) {
                            item { ChoiceMenu(if(days == 1) "Täglich" else "Wöchentlich", listOf("1" to "Täglich", "7" to "Wöchentlich")) { days = it.toInt(); prefs.edit().putInt("auto_check_days", days).apply(); MomoxWorkScheduler.configure(context, true, days) } }
                            item { Text("Android startet die Prüfung bei verfügbarer Verbindung. Der genaue Zeitpunkt kann variieren.", style = MaterialTheme.typography.bodyMedium) }
                        }
                        item { HorizontalDivider(); de.buchradar.app.ui.SectionTitle("Benachrichtigungen") }
                        item { SwitchRow("Ergebnisse automatisch melden", notifications) { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { notifications = enabled; prefs.edit().putBoolean("notifications", enabled).apply() }
                        } }
                        if (notifications) {
                            item { ChoiceMenu(if(every) "Jeden Abschluss melden" else "Nur geänderte Ergebnisse", listOf("changes" to "Nur geänderte Ergebnisse", "every" to "Jeden Abschluss melden")) { every = it == "every"; prefs.edit().putBoolean("notify_every_result", every).apply() } }
                            item { ChoiceMenu(if(threshold == 0) "Ohne Mindestwert" else "Ab ${threshold / 100} €", listOf("0" to "Ohne Mindestwert", "1000" to "Ab 10 €", "1500" to "Ab 15 €")) { threshold = it.toInt(); prefs.edit().putBoolean("notify_threshold", threshold > 0).putInt("notify_threshold_cents", threshold).apply() } }
                            item { Text("10 €: Mindestankaufswert. Die Momox-Bücherseite nennt 15 € für Abholung. Maßgeblich sind die Bedingungen beim Verkauf. Prüfprobleme werden unabhängig vom Mindestwert gemeldet.", style = MaterialTheme.typography.bodyMedium) }
                            if (!automatic) item { Text("Für automatische Meldungen auch die regelmäßige Prüfung einschalten.") }
                            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) item { Text("Android blockiert Benachrichtigungen für diese App.", color = MaterialTheme.colorScheme.error) }
                        }
                        item { HorizontalDivider(); de.buchradar.app.ui.SectionTitle("Datensicherung") }
                        item { Text("Die Sicherung enthält deine Bücher und den Verkaufsstatus. Angebote werden nach dem Import neu geprüft.") }
                        item { OutlinedButton(onClick = { export.launch("buecher-radar.json") }, modifier = Modifier.fillMaxWidth()) { Text("Bücherliste sichern") } }
                        item { OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("Sicherung importieren") } }
                        item { HorizontalDivider(); NavRow("Diagnose und Protokoll") { log = AppLog.read(context); page = "log" } }
                        item { NavRow("Datenschutz und Datenquellen") { page = "privacy" } }
                        item { Text("Bücher-Radar ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyLarge) }
                    }
                    "log" -> {
                        item { Text("Begrenztes lokales Protokoll ohne Kameraaufnahmen. Vor dem Weitergeben prüfen.") }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { log = AppLog.read(context) }) { Text("Aktualisieren") }
                            TextButton(onClick = { clearLog = true }) { Text("Leeren") }
                        } }
                        item { OutlinedButton(onClick = { exportLog.launch("buecher-radar-diagnose.txt") }) { Text("Protokoll speichern") } }
                        items(log.withIndex().toList(), key = { it.index }) { Text(it.value, style = MaterialTheme.typography.bodyMedium) }
                    }
                    "privacy" -> {
                        item { de.buchradar.app.ui.SectionTitle("Kamera"); Text("Barcode und Text werden auf dem Gerät erkannt. Kamerabilder werden nicht hochgeladen. Für den Cover-Test lädt die App Katalogbilder und vergleicht sie lokal.") }
                        item { de.buchradar.app.ui.SectionTitle("Online-Suche"); Text("Suchtext oder ISBN werden an Open Library, Google Books und bei Bedarf die Deutsche Nationalbibliothek gesendet. Die Dienste erhalten dabei auch die Netzwerkadresse. Momox erhält die zu prüfende ISBN. Suche und Ankaufspreise benötigen Internet.") }
                        item { de.buchradar.app.ui.SectionTitle("Deine Daten"); Text("Die Bücherliste bleibt lokal. Offene Regal-Funde werden mit erkanntem Text und Katalogtreffern lokal gespeichert, ohne Kameraaufnahmen. Sie sind nicht Teil des Bücherlisten-Exports oder der Systemsicherung. Android kann Einstellungen gemäß deinen Geräteoptionen sichern. Bücherliste, Diagnose und offene Prüfaufträge sind von der Systemsicherung ausgeschlossen. Für einen Gerätewechsel bitte die Bücherliste exportieren und auf dem neuen Gerät importieren. Exportierte Bücherlisten enthalten deinen privaten Bestand.") }
                        item { de.buchradar.app.ui.SectionTitle("Ankauf"); Text("Bücher-Radar verkauft keine Bücher automatisch. Ein angezeigtes Angebot ersetzt nicht die Prüfung von Ausgabe und Zustand beim Verkaufsabschluss bei Momox.") }
                    }
                }
            }
        }
        importing?.let { rows -> AlertDialog(onDismissRequest = { importing = null }, title = { Text("${rows.size} Bücher importieren?") }, text = { Text("Vorhandene ISBNs bleiben unverändert. Neue Bücher werden ergänzt; Ankaufspreise anschließend neu prüfen.") }, confirmButton = {
            TextButton(enabled = !importBusy, onClick = { importBusy = true; scope.launch {
                val db = BuchRadarDatabase.get(context)
                val result = catchingOperation { db.withTransaction { rows.count { db.bookDao().insert(it) != -1L } } }
                message = result.fold({ "$it Bücher ergänzt." }, { "Import fehlgeschlagen; es wurden keine Bücher verändert." }); importing = null; importBusy = false
            } }) { Text("Importieren") }
        }, dismissButton = { TextButton(onClick = { importing = null }) { Text("Abbrechen") } }) }
        if (clearLog) AlertDialog(onDismissRequest = { clearLog = false }, title = { Text("Protokoll leeren?") }, confirmButton = { TextButton(onClick = { AppLog.clear(context); log = emptyList(); clearLog = false }) { Text("Leeren") } }, dismissButton = { TextButton(onClick = { clearLog = false }) { Text("Abbrechen") } })
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceMenu(label: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onChange(value); open = false },
                    trailingIcon = if (title == label) { { Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = "Ausgewählt") } } else null)
            }
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    // Aligned with section titles and body text (no extra ListItem inset).
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
