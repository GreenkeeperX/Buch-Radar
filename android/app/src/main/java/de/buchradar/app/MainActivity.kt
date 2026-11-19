package de.buchradar.app

import de.buchradar.app.domain.catchingOperation
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import de.buchradar.app.domain.BookIdentity
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogAccess
import de.buchradar.app.network.CoverImageMatcher
import de.buchradar.app.network.MomoxCatalog
import de.buchradar.app.scanner.BookScannerView
import de.buchradar.app.scanner.ScannerMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import net.creativecouple.validation.isbn.ISBN
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeMode by rememberSaveable {
                mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getString("theme", "system") ?: "system")
            }
            val darkMode = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
            val view = LocalView.current
            SideEffect {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                val database = remember(context) { BuchRadarDatabase.get(context) }
                val dao = remember(database) { database.bookDao() }
                val books by dao.observeAll().collectAsState(initial = emptyList())
                val soldBooks by dao.observeSold().collectAsState(initial = emptyList())
                val bookListState = rememberLazyListState()
                val session: BookSessionViewModel = viewModel()
                val scope = session.viewModelScope

                var searchInput by session.searchInput
                var singleSearchBusy by session.isSearching
                val shelfState by session.shelfReview.state.collectAsState()
                val shelfBusy by session.shelfReview.busy.collectAsState()
                val shelfError by session.shelfReview.error.collectAsState()
                val isSearching = singleSearchBusy || shelfBusy
                val checkRun by dao.observeRun().collectAsState(initial = null)
                val isCheckingMomox = checkRun?.let { it.finishedAt == null && it.completed < it.total } == true
                val momoxProgress = checkRun?.completed ?: 0
                val momoxTotal = checkRun?.total ?: 0
                var message by session.message
                var detailsBook by session.detailsBook
                var manualEntry by session.manualEntry
                var editingBook by session.editingBook
                var deletingBook by session.deletingBook
                var scannerOpen by session.scannerOpen
                var coverFrame by session.coverFrame
                var visualCoverTest by session.visualCoverTest
                var coverCandidates by session.coverCandidates
                var shelfReviewOpen by session.shelfReviewOpen
                var discardShelfConfirm by remember { mutableStateOf(false) }
                var shelfActionsOpen by remember { mutableStateOf(false) }
                val shelfResults = if (shelfReviewOpen) shelfState.groups.filterNot { it.postponed } else null
                val shelfTotalCount = shelfState.totalCount
                var shelfCorrectionQuery by session.shelfCorrectionQuery
                var shelfEditQuery by session.shelfEditQuery
                var shelfEditText by session.shelfEditText
                var scannerStartMode by session.scannerStartMode
                var momoxSummary by session.momoxSummary
                var summaryExpanded by session.summaryExpanded
                var settingsOpen by session.settingsOpen
                var clearLogConfirmOpen by session.clearLogConfirmOpen
                var logEntries by session.logEntries
                var autoCheckEnabled by session.autoCheckEnabled
                var autoCheckDays by session.autoCheckDays
                var notificationsEnabled by session.notificationsEnabled
                var notifyOnlyAboveThreshold by session.notifyOnlyAboveThreshold
                var notifyThresholdCents by session.notifyThresholdCents
                var showSoldBooks by session.showSoldBooks
                var selectionMode by session.selectionMode
                var selectedIsbns by session.selectedIsbns
                var statusFilter by session.statusFilter
                var sortMode by session.sortMode
                var filterMenuOpen by session.filterMenuOpen
                var sortMenuOpen by session.sortMenuOpen
                var momoxCheckDialogOpen by session.momoxCheckDialogOpen
                var momoxCheckMode by session.momoxCheckMode
                var thresholdMenuOpen by session.thresholdMenuOpen
                var themeMenuOpen by session.themeMenuOpen
                var autoCheckMenuOpen by session.autoCheckMenuOpen
                SideEffect {
                    val controller = WindowInsetsControllerCompat(window, view)
                    if (scannerOpen) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                }
                val cameraPermissionRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) scannerOpen = true
                    else message = "Für den Barcode-Scanner wird Kamerazugriff benötigt."
                }
                val notificationPermissionRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    notificationsEnabled = granted
                    context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putBoolean("notifications", granted).apply()
                    AppLog.add(context, if (granted) "Benachrichtigungen aktiviert" else "Benachrichtigungen nicht erlaubt")
                }
                val submitInput: (String, Bitmap?) -> Unit = { rawInput, frame ->
                    val isbn = parseIsbn(rawInput) ?: findIsbnInText(rawInput)?.let(::parseIsbn)
                    if (isbn != null) {
                        val compact = isbn.toCompactString()
                        when {
                            books.any { it.isbn == compact } -> message = "Dieses Buch ist bereits in deiner Liste."
                            soldBooks.any { it.isbn == compact } -> {
                                message = "Dieses Buch liegt im Bereich „Verkauft“."
                                showSoldBooks = true
                            }
                            else -> {
                                showSoldBooks = false
                                singleSearchBusy = true
                                message = "Suche Titel und Buchdaten …"
                                scope.launch {
                                    val lookup = catchingOperation { CatalogAccess.report { BookCatalog.findByIsbn(compact) } }
                                    val metadata = lookup.getOrNull()?.value
                                    if (metadata != null && metadata.conflictingFields.any { it == "title" || it == "author" }) {
                                        coverCandidates = listOf(metadata)
                                        singleSearchBusy = false
                                        message = "Die Quellen nennen unterschiedliche Titel oder Autoren zur ISBN. Bitte die Buchdaten vergleichen und den Treffer bestätigen."
                                        return@launch
                                    }
                                    val newBook = BookEntity(isbn = compact, title = metadata?.title, author = metadata?.author, publicationYear = metadata?.publicationYear, coverUrl = metadata?.coverUrl, metadataSource = metadata?.sources?.joinToString())
                                    val saved = catchingOperation { dao.insert(newBook) }
                                    if (saved.isFailure) {
                                        singleSearchBusy = false
                                        message = "Buch konnte nicht gespeichert werden. Bitte erneut versuchen."
                                        return@launch
                                    }
                                    searchInput = ""
                                    statusFilter = "all"
                                    sortMode = "recent"
                                    coverFrame = null
                                    singleSearchBusy = false
                                    message = when {
                                        saved.getOrNull() == -1L -> "Dieses Buch ist bereits gespeichert."
                                        lookup.isFailure -> "Katalog nicht erreichbar. ISBN gespeichert; Titel und Autor kannst du ergänzen."
                                        metadata == null -> "ISBN gespeichert, aber im Katalog nicht gefunden. Titel und Autor kannst du ergänzen."
                                        else -> listOfNotNull("Hinzugefügt: ${metadata.title}",
                                            "Abweichende Katalogangaben – Ausgabe prüfen.".takeIf { metadata.conflictingFields.isNotEmpty() },
                                            lookup.getOrNull()?.warnings?.joinToString(" ")?.takeIf(String::isNotBlank)
                                        ).joinToString("\n")
                                    }
                                    if (saved.getOrNull() != -1L) session.scrollToBookId.value = newBook.id
                                }
                            }
                        }
                    } else if (rawInput.any(Char::isLetter)) {
                        showSoldBooks = false
                        singleSearchBusy = true
                        message = "Suche Titel, Autor und Cover …"
                        scope.launch {
                            val result = catchingOperation {
                                val report = CatalogAccess.report { BookCatalog.findByRecognizedText(rawInput) }
                                val ranked = (frame ?: coverFrame)?.let { CoverImageMatcher.rank(context, it, report.value, visualOnly = visualCoverTest) } ?: report.value
                                report.copy(value = ranked)
                            }
                            coverCandidates = result.getOrNull()?.value.orEmpty()
                            message = when {
                                result.isFailure -> "Buchkatalog nicht erreichbar."
                                result.getOrNull()?.warnings?.isNotEmpty() == true -> result.getOrNull()?.warnings?.joinToString("\n")
                                coverCandidates.isEmpty() -> "Keine passenden Treffer gefunden. Prüfe den Text oder tippe ihn oben ein."
                                else -> null
                            }
                            singleSearchBusy = false
                        }
                    } else {
                        message = "Bitte eine gültige ISBN oder einen Buchtitel eingeben."
                    }
                }
                val runMomoxCheck: (List<BookEntity>) -> Unit = { snapshot ->
                    if (snapshot.isEmpty()) message = "Für diese Auswahl gibt es keine zu prüfenden Bücher."
                    else scope.launch { MomoxCheckCoordinator.enqueue(context, snapshot) }
                }
                LaunchedEffect(checkRun) {
                    checkRun?.let { run ->
                        if (run.finishedAt != null && run.pausedReason == null) {
                            momoxSummary = MomoxSummary(run.available, run.unavailable, run.failed, run.valueCents.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        }
                    }
                }
                val searchShelf: (List<String>) -> Unit = { spines ->
                    scannerOpen = false
                    shelfReviewOpen = true
                    session.shelfReview.append(spines)
                    message = null
                }
                val correctShelfGroup: (String, String, Bitmap?) -> Unit = { groupId, text, frame ->
                    session.shelfReview.correct(groupId, text, frame)
                    shelfCorrectionQuery = null
                    shelfEditQuery = null
                    message = null
                }
                val closeShelfReview: () -> Unit = {
                    session.shelfReview.pause()
                    shelfReviewOpen = false
                    scannerOpen = false
                    shelfEditQuery = null
                    shelfCorrectionQuery = null
                }
                val visibleBooks = remember(books, soldBooks, showSoldBooks, statusFilter, sortMode, searchInput) {
                    val inputKey = foldForListSearch(searchInput)
                    val terms = inputKey.split(Regex("\\s+")).filter(String::isNotBlank)
                    val base = if (showSoldBooks) soldBooks else books
                    val filtered = base.filter { book ->
                        val haystack = foldForListSearch("${book.title.orEmpty()} ${book.author.orEmpty()} ${book.isbn} ${book.publicationYear?.toString().orEmpty()}")
                        val matchesText = terms.all { term -> haystack.contains(term) }
                        val matchesStatus = when (statusFilter) {
                            "available" -> book.momoxStatus == "AVAILABLE"
                            "unavailable" -> book.momoxStatus == "NOT_AVAILABLE"
                            "unchecked" -> book.lastCheckedAt == null
                            "error" -> book.lastAttemptStatus == "ERROR" || book.momoxStatus == "ERROR"
                            else -> true
                        }
                        matchesText && matchesStatus
                    }
                    when (sortMode) {
                        "title_up" -> filtered.sortedBy { it.title?.lowercase(Locale.GERMANY).orEmpty() }
                        "title_down" -> filtered.sortedByDescending { it.title?.lowercase(Locale.GERMANY).orEmpty() }
                        "price_down" -> filtered.sortedByDescending { it.currentPriceCents ?: Int.MIN_VALUE }
                        "price_up" -> filtered.sortedBy { it.currentPriceCents ?: Int.MAX_VALUE }
                        else -> if (showSoldBooks) filtered.sortedByDescending { it.soldAt ?: it.createdAt } else filtered.sortedByDescending { it.createdAt }
                    }
                }
                val visibleIsbns = visibleBooks.map { it.id }.toSet()
                val selectedVisibleIsbns = selectedIsbns.intersect(visibleIsbns)
                BackHandler(enabled = selectionMode && !scannerOpen) { selectionMode = false; selectedIsbns = emptySet() }
                BackHandler(enabled = scannerOpen) { scannerOpen = false; shelfCorrectionQuery = null; if (shelfReviewOpen) session.shelfReview.resume() }
                BackHandler(enabled = !scannerOpen && (shelfResults != null || coverCandidates.isNotEmpty())) { closeShelfReview(); coverCandidates = emptyList() }

                if (settingsOpen) {
                    SettingsScreen(themeMode, { themeMode = it }, { settingsOpen = false })
                    return@MaterialTheme
                }
                detailsBook?.let { original ->
                    val current = (books + soldBooks).firstOrNull { it.id == original.id } ?: original
                    BookDetailsScreen(current, onClose = { detailsBook = null })
                    return@MaterialTheme
                }
                BookListScrollEffect(bookListState, session.scrollToBookId.value, visibleBooks.map { it.id },
                    enabled = !scannerOpen && shelfResults == null && coverCandidates.isEmpty()) {
                    session.scrollToBookId.value = null
                }

                Scaffold(topBar = {
                    if (!scannerOpen) TopAppBar(title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(stringResource(R.string.app_name))
                        }
                    }, actions = {
                        IconButton(modifier = Modifier.semantics { contentDescription = "Einstellungen öffnen" }, onClick = { settingsOpen = true }) { Text("⚙", fontSize = 24.sp) }
                    })
                }, bottomBar = {
                    if (!scannerOpen && shelfResults == null && coverCandidates.isEmpty()) {
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val activeTabColor = if (darkMode) Color(0xFF9BCBFF) else Color(0xFF174A7E)
                                val activeTabText = if (darkMode) Color(0xFF102A43) else Color.White
                                if (!showSoldBooks) {
                                    Button(onClick = { selectionMode = false; selectedIsbns = emptySet() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = true; role = Role.Tab }, colors = ButtonDefaults.buttonColors(containerColor = activeTabColor, contentColor = activeTabText)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Bücher", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedButton(onClick = { showSoldBooks = true; selectionMode = false; selectedIsbns = emptySet(); momoxSummary = null }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = false; role = Role.Tab }, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Verkauft", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                } else {
                                    OutlinedButton(onClick = { showSoldBooks = false; selectionMode = false; selectedIsbns = emptySet(); momoxSummary = null }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = false; role = Role.Tab }, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Bücher", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Button(onClick = { selectionMode = false; selectedIsbns = emptySet() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = true; role = Role.Tab }, colors = ButtonDefaults.buttonColors(containerColor = activeTabColor, contentColor = activeTabText)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Verkauft", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }) { padding ->
                    if (scannerOpen) {
                        BookScannerView(
                            initialMode = scannerStartMode,
                            onBarcode = { rawValue ->
                                AppLog.add(context, "Barcode erkannt (${rawValue.length} Zeichen)")
                                val correctionTarget = shelfCorrectionQuery
                                val parsed = parseIsbn(rawValue)
                                scannerOpen = false
                                if (correctionTarget != null) correctShelfGroup(correctionTarget, parsed?.toCompactString() ?: rawValue, null)
                                else if (parsed == null) message = "Kein gültiger Buch-Barcode erkannt."
                                else {
                                    shelfReviewOpen = false
                                    searchInput = parsed.toString()
                                    submitInput(parsed.toString(), null)
                                }
                            },
                            onTextConfirmed = { text, frame, isVisualTest ->
                                val isbn = findIsbnInText(text)
                                val correctionTarget = shelfCorrectionQuery
                                AppLog.add(context, "OCR-Text übernommen (${text.length} Zeichen, ISBN=${isbn != null})")
                                if (correctionTarget != null) {
                                    scannerOpen = false
                                    correctShelfGroup(correctionTarget, isbn ?: text, frame)
                                } else {
                                shelfReviewOpen = false
                                searchInput = isbn?.let(::autoFormatIsbn) ?: text
                                coverFrame = frame
                                visualCoverTest = isVisualTest
                                scannerOpen = false
                                submitInput(searchInput, frame)
                                }
                            },
                            onShelfConfirmed = { spines, image, warning ->
                                AppLog.add(context, "Regal-OCR übernommen (${spines.size} Buchrücken)")
                                shelfCorrectionQuery = null
                                searchInput = ""
                                coverFrame = null
                                searchShelf(spines)
                                message = warning
                                image.recycle()
                            },
                            onClose = { scannerOpen = false; shelfCorrectionQuery = null; if (shelfReviewOpen) session.shelfReview.resume() }
                        )
                    } else if (shelfResults != null) {
                        val groups = shelfResults.orEmpty()
                        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Bücherregal", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                                if (shelfState.groups.isNotEmpty() || shelfError != null) Box {
                                    IconButton(onClick = { shelfActionsOpen = true }, modifier = Modifier.semantics { contentDescription = "Regalaktionen" }) { Text("⋮", fontSize = 26.sp) }
                                    DropdownMenu(expanded = shelfActionsOpen, onDismissRequest = { shelfActionsOpen = false }) {
                                        DropdownMenuItem(text = { Text("Regal-Funde verwerfen") }, onClick = { shelfActionsOpen = false; discardShelfConfirm = true })
                                    }
                                }
                            }
                            Text(if (shelfBusy) "Buchrücken werden gesucht …" else "Noch ${shelfState.groups.size} von $shelfTotalCount Buchrücken offen.", style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { session.shelfReview.pause(); scannerStartMode = ScannerMode.SHELF; shelfCorrectionQuery = null; scannerOpen = true }, modifier = Modifier.weight(1f)) { Text("Weitere scannen") }
                                TextButton(onClick = closeShelfReview) { Text("Zur Liste") }
                            }
                            if (isSearching) {
                                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            val postponedCount = shelfState.groups.count { it.postponed }
                            if (postponedCount > 0) TextButton(onClick = { session.shelfReview.restorePostponed() }) { Text("Zurückgestellte Funde ($postponedCount) weiterbearbeiten") }
                            if (groups.any { it.error != null }) TextButton(onClick = { session.shelfReview.retryFailures() }) { Text("Fehlgeschlagene Suchen wiederholen") }
                            shelfError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { session.shelfReview.resume() }) { Text("Erneut versuchen") } }
                            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            if (!isSearching && groups.isEmpty()) {
                                Text(when { postponedCount > 0 -> "Die übrigen Funde sind für später zurückgestellt."; shelfTotalCount == 0 -> "Keine offenen Buchrücken. Scanne ein Regal, um Funde zu sammeln."; else -> "Alle Buchrücken sind erledigt." }, style = MaterialTheme.typography.titleMedium)
                            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(groups, key = { "shelf-${it.id}" }) { group ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(group.query, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                            if (group.pending) Text("Suche noch ausstehend …")
                                            else if (group.error != null) Text(group.error, color = MaterialTheme.colorScheme.error)
                                            else if (group.candidates.isEmpty()) Text("Kein passender Katalogtreffer. Korrigiere den Text, nutze die Kamera oder stelle den Fund für später zurück.", style = MaterialTheme.typography.bodyMedium)
                                            group.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                                            group.candidates.forEach { candidate ->
                                                val isActive = books.any { (it.id == "shelf:${group.id}" && it.title == candidate.title && it.author == candidate.author) || candidate.isbn != null && it.isbn == candidate.isbn }
                                                val isSold = candidate.isbn?.let { isbn -> soldBooks.any { it.isbn == isbn } } == true
                                                CandidateCard(candidate, isActive, isSold) {
                                                    val candidateIsbn = candidate.isbn.orEmpty()
                                                    if (isSold) scope.launch {
                                                        val restored = catchingOperation { soldBooks.firstOrNull { it.isbn == candidateIsbn }?.let { dao.updateSoldStatus(listOf(it.id), null) } ?: 0 }
                                                        if (restored.getOrDefault(0) > 0) session.shelfReview.complete(group.id)
                                                        else message = "Zurückholen fehlgeschlagen. Der Fund bleibt erhalten."
                                                    }
                                                    else if (!isActive) {
                                                        scope.launch {
                                                            val saved = catchingOperation { dao.insert(BookEntity(candidateIsbn, candidate.title, candidate.author, candidate.publicationYear, candidate.coverUrl, metadataSource = candidate.sources.joinToString(), id = "shelf:${group.id}")) }
                                                            if (saved.isSuccess) {
                                                                session.shelfReview.complete(group.id)
                                                                message = if (saved.getOrNull() == -1L) "Bereits gespeichert." else "Hinzugefügt: ${candidate.title}"
                                                            } else message = "Speichern fehlgeschlagen. Der Fund bleibt erhalten."
                                                        }
                                                    }
                                                }
                                                if (isActive) TextButton(onClick = { session.shelfReview.complete(group.id) }) { Text("Vorhandenes Buch bestätigen") }
                                            }
                                            if (shelfEditQuery == group.id) {
                                                OutlinedTextField(
                                                    value = shelfEditText,
                                                    onValueChange = { shelfEditText = it },
                                                    label = { Text("Titel oder Autor korrigieren") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { correctShelfGroup(group.id, shelfEditText, null) }, enabled = shelfEditText.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Korrigierte Suche") }
                                                    TextButton(onClick = { shelfEditQuery = null }) { Text("Abbrechen") }
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    session.shelfReview.pause()
                                                    shelfCorrectionQuery = group.id
                                                    scannerStartMode = ScannerMode.SINGLE_BOOK
                                                    scannerOpen = true
                                                }, modifier = Modifier.weight(1f)) { Text("Einzeltitel-Kamera") }
                                                OutlinedButton(onClick = {
                                                    shelfEditQuery = group.id
                                                    shelfEditText = group.query
                                                }, modifier = Modifier.weight(1f)) { Text("Text ändern") }
                                            }
                                            TextButton(onClick = {
                                                session.shelfReview.postpone(group.id)
                                                if (shelfEditQuery == group.id) shelfEditQuery = null
                                            }) { Text("Für später zurückstellen") }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (coverCandidates.isNotEmpty()) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                label = { Text("Suchtext bearbeiten") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Text(if (visualCoverTest) "Cover-Test: Kandidaten werden nur nach dem visuellen Cover-Abgleich sortiert. Der Suchtext liefert weiterhin die Kandidaten." else "ISBN und Ausgabe vergleichen. Coverwerte sind ein zusätzlicher Hinweis, keine sichere Identifikation.", style = MaterialTheme.typography.bodyMedium)
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(coverCandidates, key = { it.candidateId }) { candidate ->
                                    val isActive = candidate.isbn?.let { isbn -> books.any { it.isbn == isbn } } == true
                                    val isSold = candidate.isbn?.let { isbn -> soldBooks.any { it.isbn == isbn } } == true
                                    CandidateCard(candidate, isActive, isSold) {
                                        val candidateIsbn = candidate.isbn.orEmpty()
                                        if (isSold) {
                                            scope.launch { soldBooks.firstOrNull { it.isbn == candidateIsbn }?.let { dao.updateSoldStatus(listOf(it.id), null) } }
                                            showSoldBooks = false
                                            coverCandidates = emptyList()
                                            visualCoverTest = false
                                            message = "Buch zurückgeholt."
                                        } else if (!isActive) scope.launch {
                                            val newBook = BookEntity(candidateIsbn, candidate.title, candidate.author, candidate.publicationYear, candidate.coverUrl, metadataSource = candidate.sources.joinToString())
                                            val saved = catchingOperation { dao.insert(newBook) }
                                            if (saved.isFailure) {
                                                message = "Buch konnte nicht gespeichert werden. Die Treffer bleiben erhalten."
                                                return@launch
                                            }
                                            coverCandidates = emptyList()
                                            visualCoverTest = false
                                            coverFrame = null
                                            searchInput = ""
                                            statusFilter = "all"
                                            sortMode = "recent"
                                            message = if (saved.getOrNull() == -1L) "Dieses Buch ist bereits gespeichert." else "Hinzugefügt: ${candidate.title}"
                                            if (saved.getOrNull() != -1L) session.scrollToBookId.value = newBook.id
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { coverCandidates = emptyList(); submitInput(searchInput, coverFrame) }, enabled = searchInput.isNotBlank() && !isSearching, modifier = Modifier.weight(1f)) { Text("Erneut suchen") }
                                TextButton(onClick = { coverCandidates = emptyList(); coverFrame = null; searchInput = ""; visualCoverTest = false }, modifier = Modifier.weight(1f)) { Text("Zurück zur Eingabe") }
                            }
                        }
                    } else Column(
                        Modifier.fillMaxSize().padding(padding).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = formatBookInput(it); coverFrame = null; visualCoverTest = false },
                            label = { Text("ISBN, EAN oder Buchtitel") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = if (searchInput.isNotEmpty()) {
                                { IconButton(modifier = Modifier.semantics { contentDescription = "Eingabe löschen" }, onClick = { searchInput = ""; coverFrame = null; message = null }) { Text("×", fontSize = 24.sp) } }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !selectionMode && !isSearching,
                            minLines = 1,
                            maxLines = 3
                        )
                        Button(
                            enabled = !isSearching && !selectionMode,
                            onClick = {
                                if (searchInput.isBlank()) {
                                    scannerStartMode = ScannerMode.AUTOMATIC
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scannerOpen = true
                                    else cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                                } else submitInput(searchInput, coverFrame)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when { isSearching -> "Suche …"; searchInput.isBlank() -> "Kamera öffnen"; parseIsbn(searchInput) != null -> "ISBN hinzufügen"; searchInput.any(Char::isLetter) -> "Titel suchen"; else -> "ISBN hinzufügen" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (shelfState.groups.isNotEmpty() || shelfError != null) TextButton(
                            enabled = !singleSearchBusy && !selectionMode,
                            onClick = { shelfReviewOpen = true; message = null; session.shelfReview.resume() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (shelfError != null) "Gespeicherte Regal-Funde laden" else "Regal-Funde fortsetzen (${shelfState.groups.size})") }
                        if (searchInput.any(Char::isLetter) && !isSearching) TextButton(onClick = { manualEntry = BookEntity("", searchInput) }) { Text("Buch manuell erfassen") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(if (showSoldBooks) "Verkaufte Bücher" else "Meine Bücher", style = MaterialTheme.typography.titleLarge)
                            Text("${if (showSoldBooks) soldBooks.size else books.size} Bücher", style = MaterialTheme.typography.bodyLarge)
                        }
                        if (!showSoldBooks) Button(
                            enabled = !isCheckingMomox && books.isNotEmpty(),
                            onClick = { momoxCheckDialogOpen = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text(if (isCheckingMomox) "Momox-Prüfung · $momoxProgress von $momoxTotal" else "Momox-Liste prüfen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (isCheckingMomox) {
                            checkRun?.pausedReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (checkRun?.pausedReason != null) OutlinedButton(onClick = { scope.launch { MomoxCheckCoordinator.resume(context) } }) { Text("Erneut versuchen") }
                                TextButton(onClick = { scope.launch { MomoxCheckCoordinator.cancel(context) } }) { Text("Prüfung beenden") }
                            }
                        }
                        if (!selectionMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box {
                                TextButton(onClick = { filterMenuOpen = true }) { Text("Filter: ${filterLabel(statusFilter)} ▾") }
                                DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                                    listOf("all" to "Alle", "available" to "Ankauf", "unavailable" to "Kein Ankauf", "unchecked" to "Ungeprüft", "error" to "Fehler").forEach { (key, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { statusFilter = key; filterMenuOpen = false })
                                    }
                                }
                            }
                            Box {
                                TextButton(onClick = { sortMenuOpen = true }) { Text("Sortierung: ${sortLabel(sortMode)} ▾") }
                                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                    listOf("recent" to "Neueste", "title_up" to "Titel A–Z", "title_down" to "Titel Z–A", "price_down" to "Preis ↓", "price_up" to "Preis ↑").forEach { (key, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { sortMode = key; sortMenuOpen = false })
                                    }
                                }
                            }
                        }
                        if (selectionMode) Column(Modifier.fillMaxWidth()) {
                            val selectionState = when {
                                selectedVisibleIsbns.isEmpty() -> ToggleableState.Off
                                selectedVisibleIsbns.size == visibleBooks.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            Row(Modifier.fillMaxWidth().triStateToggleable(selectionState, role = Role.Checkbox) {
                                selectedIsbns = if (selectionState == ToggleableState.On) selectedIsbns - visibleIsbns else selectedIsbns + visibleIsbns
                            }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                TriStateCheckbox(state = selectionState, onClick = null)
                                Text("Alle sichtbaren auswählen (${visibleBooks.size})", style = MaterialTheme.typography.bodyLarge)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        dao.updateSoldStatus(selectedVisibleIsbns.toList(), if (showSoldBooks) null else System.currentTimeMillis())
                                        selectedIsbns = emptySet(); selectionMode = false
                                        message = if (showSoldBooks) "Bücher zurück in die Liste gelegt." else "Als verkauft markiert."
                                    }
                                }, enabled = selectedVisibleIsbns.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (showSoldBooks) "Zurück in Meine Bücher" else "Als verkauft markieren (${selectedVisibleIsbns.size})") }
                                TextButton(onClick = { selectionMode = false; selectedIsbns = emptySet() }) { Text("Abbrechen") }
                            }
                        }
                        momoxSummary?.let { MomoxSummaryCard(it, darkMode, summaryExpanded) { summaryExpanded = !summaryExpanded } }
                        if (visibleBooks.isEmpty()) {
                            Text(if (showSoldBooks) if (soldBooks.isEmpty()) "Keine als verkauft markierten Bücher." else "Keine Treffer. Suche oder Filter zurücksetzen." else if (books.isEmpty()) "Noch keine Bücher gespeichert. Gib eine ISBN ein oder scanne ein Regal." else "Keine Bücher entsprechen der Suche oder dem Filter.")
                        } else {
                            LazyColumn(
                                state = bookListState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(visibleBooks, key = { it.id }) { book ->
                                    BookCard(
                                        book = book,
                                        darkMode = darkMode,
                                        selectionMode = selectionMode,
                                        selected = book.id in selectedIsbns,
                                        isSold = showSoldBooks,
                                        onSelect = {
                                            selectedIsbns = if (book.id in selectedIsbns) selectedIsbns - book.id else selectedIsbns + book.id
                                        },
                                        onLongSelect = { selectionMode = true; selectedIsbns = selectedIsbns + book.id },
                                        onRestore = { scope.launch { dao.updateSoldStatus(listOf(book.id), null) } },
                                        onOpen = { detailsBook = book },
                                        onEdit = { editingBook = book },
                                        onDelete = { deletingBook = book },
                                        onMomoxCheck = { runMomoxCheck(listOf(book)) }
                                    )
                                }
                            }
                        }
                        if (momoxCheckDialogOpen) AlertDialog(
                            onDismissRequest = { momoxCheckDialogOpen = false },
                            title = { Text("Momox-Prüfung") },
                            text = { Column {
                                Text("Welche Bücher sollen nacheinander geprüft werden?")
                                listOf("new" to "Nur neue / ungeprüfte", "stale" to "Fällige und fehlgeschlagene", "all" to "Alle Bücher").forEach { (key, label) ->
                                    Row(Modifier.fillMaxWidth().selectable(selected = momoxCheckMode == key, role = Role.RadioButton, onClick = { momoxCheckMode = key }).heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(selected = momoxCheckMode == key, onClick = null)
                                        Text(label, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            } },
                            confirmButton = { TextButton(onClick = {
                                val now = System.currentTimeMillis()
                                val snapshot = when (momoxCheckMode) {
                                    "new" -> books.filter { it.lastCheckedAt == null }
                                    "stale" -> books.filter { it.lastCheckedAt == null || now - it.lastCheckedAt >= 24L * 60 * 60 * 1000 || it.lastAttemptStatus == "ERROR" }
                                    else -> books
                                }
                                momoxCheckDialogOpen = false
                                runMomoxCheck(snapshot)
                            }) { Text("Prüfung starten") } },
                            dismissButton = { TextButton(onClick = { momoxCheckDialogOpen = false }) { Text("Abbrechen") } }
                        )

                    }
                }

                if (discardShelfConfirm) AlertDialog(onDismissRequest = { discardShelfConfirm = false },
                    title = { Text("Offene Regal-Funde verwerfen?") },
                    text = { Text("Die gespeicherten offenen und zurückgestellten Regal-Funde werden entfernt. Bereits gespeicherte Bücher bleiben erhalten.") },
                    confirmButton = { TextButton(onClick = { session.shelfReview.discardAll(); discardShelfConfirm = false }) { Text("Verwerfen") } },
                    dismissButton = { TextButton(onClick = { discardShelfConfirm = false }) { Text("Abbrechen") } })

                manualEntry?.let { draft -> EditBookDialog(draft, { manualEntry = null }) { isbnText, title, author ->
                    val isbn = BookIdentity.canonicalIsbn(isbnText)
                    if (isbnText.isNotBlank() && isbn == null) "Bitte ISBN korrigieren oder leer lassen."
                    else if (title.isBlank()) "Bitte einen Titel eingeben."
                    else {
                        val result = catchingOperation { dao.insert(BookEntity(isbn.orEmpty(), title.trim(), author.trim().takeIf(String::isNotBlank))) }
                        when {
                            result.isFailure -> "Speichern fehlgeschlagen."
                            result.getOrNull() == -1L -> "Diese ISBN ist bereits gespeichert."
                            else -> { searchInput = ""; statusFilter = "all"; sortMode = "recent"; message = "Buch gespeichert."; null }
                        }
                    }
                } }
                editingBook?.let { book ->
                    EditBookDialog(
                        book = book,
                        onDismiss = { editingBook = null },
                        onSave = { isbnText, titleText, authorText ->
                            val parsed = parseIsbn(isbnText)
                            if (parsed == null && isbnText.isNotBlank()) return@EditBookDialog "Bitte eine gültige ISBN eingeben oder das Feld leer lassen."
                            if (titleText.isBlank() && parsed == null) return@EditBookDialog "Bitte einen Titel eingeben."
                            val newIsbn = parsed?.toCompactString().orEmpty()
                            val duplicate = if (newIsbn.isNotBlank()) dao.findByIsbn(newIsbn) else null
                            if (duplicate != null && duplicate.id != book.id && newIsbn != book.isbn) {
                                "Diese ISBN ist bereits einem anderen Eintrag zugeordnet."
                            } else {
                                val changed = dao.updateBook(
                                    id = book.id,
                                    newIsbn = newIsbn,
                                    title = titleText.trim().takeIf(String::isNotEmpty),
                                    author = authorText.trim().takeIf(String::isNotEmpty)
                                )
                                if (changed == 1) {
                                    message = "Eintrag aktualisiert."
                                    null
                                } else {
                                    "Der Eintrag konnte nicht aktualisiert werden."
                                }
                            }
                        }
                    )
                }

                deletingBook?.let { book ->
                    AlertDialog(
                        onDismissRequest = { deletingBook = null },
                        title = { Text("Buch entfernen?") },
                        text = { Text("${book.title ?: "Unbekannter Titel"} wird aus deiner Liste gelöscht.") },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    dao.deleteById(book.id)
                                    deletingBook = null
                                    message = "Buch entfernt."
                                }
                            }) { Text("Entfernen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { deletingBook = null }) { Text("Abbrechen") }
                        }
                    )
                }

            }
        }
    }
}

data class MomoxSummary(val available: Int, val unavailable: Int, val errors: Int, val totalValueCents: Int = 0)

@Composable
private fun MomoxSummaryCard(summary: MomoxSummary, darkMode: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val success = if (darkMode) Color(0xFF7CFF4A) else Color(0xFF176B35)
    val failure = if (darkMode) Color(0xFFFF6B6B) else Color(0xFFB3261E)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = if (expanded) 10.dp else 4.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryValue("Ankauf", summary.available, if (summary.available > 0) success else failure, expanded)
            SummaryValue("Kein Ankauf", summary.unavailable, failure, expanded)
            SummaryValue("Fehler", summary.errors, if (summary.errors > 0) failure else success, expanded)
        }
        if (expanded) Text("Ankaufwert gesamt: ${NumberFormat.getCurrencyInstance(Locale.GERMANY).format(summary.totalValueCents / 100.0)}", modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryValue(label: String, value: Int, color: Color, expanded: Boolean) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value.toString(), color = color, fontSize = if (expanded) 32.sp else 23.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color, style = if (expanded) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CandidateCard(candidate: de.buchradar.app.network.CatalogBook, alreadyAdded: Boolean, isSold: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            var coverFailed by remember(candidate.coverUrl) { mutableStateOf(candidate.coverUrl == null) }
            if (!coverFailed) AsyncImage(model = candidate.coverUrl, contentDescription = "Cover: ${candidate.title}", modifier = Modifier.size(width = 86.dp, height = 124.dp), onError = { coverFailed = true })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(candidate.title, style = MaterialTheme.typography.titleLarge)
                candidate.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                candidate.publisher?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                candidate.publicationYear?.let { Text("Erscheinungsjahr: $it", style = MaterialTheme.typography.bodyMedium) }
                Text(candidate.isbn?.let { "ISBN ${displayIsbn(it)}" } ?: "Keine ISBN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                candidate.textSimilarity?.let { Text(when { it >= 85 -> "Titel und Autor passen gut"; it >= 60 -> "Möglicher Treffer – Ausgabe prüfen"; else -> "Unsicherer Treffer – sorgfältig vergleichen" }, style = MaterialTheme.typography.bodyMedium) }
                candidate.visualSimilarity?.let { Text("Coverähnlichkeit: $it/100 · experimentell", style = MaterialTheme.typography.labelMedium) }
                var showSources by remember(candidate.candidateId) { mutableStateOf(false) }
                if (candidate.sources.isNotEmpty()) TextButton(onClick = { showSources = !showSources }) {
                    Text(if (candidate.conflictingFields.isNotEmpty()) "Abweichende Angaben prüfen ▾" else "Quellen und Buchdaten ▾")
                }
                if (showSources) {
                    Text("Quelle: ${candidate.sources.joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    candidate.fieldValues.forEach { (field, values) ->
                        val label = when(field) { "title" -> "Titel"; "author" -> "Autor"; "year" -> "Jahr"; "publisher" -> "Verlag"; else -> field }
                        values.forEach { value -> Text("$label: ${value.value} (${value.sources.joinToString()})", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Button(onClick = onClick, enabled = !alreadyAdded, modifier = Modifier.fillMaxWidth()) {
                    Text(when { alreadyAdded -> "Bereits in Liste"; isSold -> "Zurück in meine Bücher"; candidate.isbn == null -> "Ohne ISBN erfassen"; else -> "Buch hinzufügen" })
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookCard(
    book: BookEntity,
    darkMode: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    isSold: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit,
    onRestore: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMomoxCheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (selectionMode) onSelect() else onOpen() }, onLongClickLabel = "Buch auswählen", onLongClick = onLongSelect),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect() })
                book.coverUrl?.let { AsyncImage(model = it, contentDescription = "Cover: ${book.title ?: "Buch"}", modifier = Modifier.size(width = 68.dp, height = 98.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    book.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 2) }
                    Text((if (book.isbn.isBlank()) "ISBN fehlt" else "ISBN ${displayIsbn(book.isbn)}") + (book.publicationYear?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (book.lastAttemptStatus == "ERROR") Text("Letzter Versuch fehlgeschlagen – älteres Ergebnis", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    if (book.isbn.isBlank()) Text("Ohne ISBN nicht bei Momox prüfbar", style = MaterialTheme.typography.bodyLarge)
                    else when (book.momoxStatus) {
                        "AVAILABLE" -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Momox: ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                NumberFormat.getCurrencyInstance(Locale.GERMANY).format((book.currentPriceCents ?: 0) / 100.0),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (darkMode) Color(0xFF7CFF4A) else Color(0xFF176B35),
                                maxLines = 1
                            )
                        }
                        "NOT_AVAILABLE" -> Text("Momox: derzeit kein Ankauf", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        "ERROR" -> Text("Momox: letzter Check fehlgeschlagen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        else -> Text(if (book.lastCheckedAt == null) "Momox: noch nicht geprüft" else "Momox: ISBN nicht bekannt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    book.lastCheckedAt?.let { checkedAt ->
                        Text("Stand: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(checkedAt))}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (!selectionMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSold) OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Zurückholen") }
                else OutlinedButton(onClick = onMomoxCheck, enabled = book.isbn.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Prüfen") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Bearbeiten") }
                TextButton(onClick = onDelete, modifier = Modifier.weight(.8f)) { Text("Entfernen") }
            }
        }
    }
}

@Composable
private fun EditBookDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSave: suspend (isbn: String, title: String, author: String) -> String?
) {
    var isbn by remember(book.isbn) { mutableStateOf(displayIsbn(book.isbn)) }
    var title by remember(book.title) { mutableStateOf(book.title.orEmpty()) }
    var author by remember(book.author) { mutableStateOf(book.author.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buch bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = isbn,
                    onValueChange = { isbn = autoFormatIsbn(it) },
                    label = { Text("ISBN") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Autor/in") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    error = onSave(isbn, title, author)
                    saving = false
                    if (error == null) onDismiss()
                }
            }) { Text(if (saving) "Speichert …" else "Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun parseIsbn(value: String): ISBN? =
    catchingOperation { ISBN.valueOf(value.trim()) }.getOrNull()

private fun autoFormatIsbn(value: String): String {
    val compact = value.filter { it.isDigit() || it.equals('x', ignoreCase = true) }
    return parseIsbn(compact)?.toString() ?: compact.uppercase()
}

private fun formatBookInput(value: String): String =
    if (value.all { it.isDigit() || it.equals('x', true) || it == '-' || it.isWhitespace() }) autoFormatIsbn(value)
    else value

private fun displayIsbn(value: String): String = parseIsbn(value)?.toString() ?: value

private fun foldForListSearch(value: String): String = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

private fun filterLabel(key: String) = when (key) { "available" -> "Ankauf"; "unavailable" -> "Kein Ankauf"; "unchecked" -> "Ungeprüft"; "error" -> "Fehler"; else -> "Alle" }
private fun sortLabel(key: String) = when (key) { "title_up" -> "Titel A–Z"; "title_down" -> "Titel Z–A"; "price_down" -> "Preis ↓"; "price_up" -> "Preis ↑"; else -> "Neueste" }

private fun findIsbnInText(text: String): String? = BookIdentity.unambiguousIsbn(text)
