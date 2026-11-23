package de.buchradar.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.domain.BookIdentity
import de.buchradar.app.domain.BookInputFormat
import de.buchradar.app.domain.BookListQuery
import de.buchradar.app.domain.catchingOperation
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CatalogAccess
import de.buchradar.app.network.CoverImageMatcher
import de.buchradar.app.scanner.BookScannerView
import de.buchradar.app.scanner.ScannerMode
import de.buchradar.app.ui.BuchRadarTheme
import de.buchradar.app.ui.InfoBanner
import kotlinx.coroutines.launch

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
            BuchRadarTheme(darkTheme = darkMode) {
                val colorScheme = MaterialTheme.colorScheme
                val view = LocalView.current
                SideEffect {
                    window.statusBarColor = colorScheme.surface.toArgb()
                    window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars = !darkMode
                        isAppearanceLightNavigationBars = !darkMode
                    }
                }
                val database = remember(context) { BuchRadarDatabase.get(context) }
                val dao = remember(database) { database.bookDao() }
                val books by dao.observeAll().collectAsState(initial = emptyList())
                val soldBooks by dao.observeSold().collectAsState(initial = emptyList())
                val pendingIsbns by dao.observePendingIsbns().collectAsState(initial = emptyList())
                val pendingSet = remember(pendingIsbns) { pendingIsbns.toSet() }
                val bookListState = rememberLazyListState()
                val session: BookSessionViewModel = viewModel()
                val scope = session.viewModelScope
                val uiScope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                val keyboard = LocalSoftwareKeyboardController.current

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
                var dismissedSummaryRun by session.dismissedSummaryRun
                var settingsOpen by session.settingsOpen
                var showSoldBooks by session.showSoldBooks
                var selectionMode by session.selectionMode
                var selectedIsbns by session.selectedIsbns
                var statusFilter by session.statusFilter
                var sortMode by session.sortMode
                var sortMenuOpen by session.sortMenuOpen
                var momoxCheckDialogOpen by session.momoxCheckDialogOpen
                var momoxCheckMode by session.momoxCheckMode
                SideEffect {
                    val controller = WindowInsetsControllerCompat(window, view)
                    if (scannerOpen) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                }
                val cameraPermissionRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) scannerOpen = true
                    else message = "Für den Scanner wird Kamerazugriff benötigt. Du kannst ISBN oder Titel auch eintippen."
                }
                val openScanner: (ScannerMode) -> Unit = { mode ->
                    keyboard?.hide()
                    scannerStartMode = mode
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scannerOpen = true
                    else cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                }
                val showUndo: (String, suspend () -> Unit) -> Unit = { text, undo ->
                    uiScope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(text, actionLabel = "Rückgängig", withDismissAction = true, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) scope.launch {
                            if (catchingOperation { undo() }.isFailure) message = "Rückgängig machen fehlgeschlagen."
                        }
                    }
                }
                val submitInput: (String, Bitmap?) -> Unit = { rawInput, frame ->
                    keyboard?.hide()
                    val isbn = BookInputFormat.parseIsbn(rawInput) ?: BookIdentity.unambiguousIsbn(rawInput)?.let(BookInputFormat::parseIsbn)
                    if (isbn != null) {
                        val compact = isbn.toCompactString()
                        when {
                            books.any { it.isbn == compact } -> {
                                message = "Dieses Buch ist bereits in deiner Liste."
                                session.scrollToBookId.value = books.first { it.isbn == compact }.id
                                statusFilter = BookListQuery.FILTER_ALL
                                searchInput = ""
                            }
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
                                    statusFilter = BookListQuery.FILTER_ALL
                                    sortMode = BookListQuery.SORT_RECENT
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
                                result.isFailure -> "Buchkatalog nicht erreichbar. Bitte Internetverbindung prüfen."
                                result.getOrNull()?.warnings?.isNotEmpty() == true -> result.getOrNull()?.warnings?.joinToString("\n")
                                coverCandidates.isEmpty() -> "Keine passenden Treffer gefunden. Prüfe den Text oder erfasse das Buch manuell."
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
                    else scope.launch {
                        if (catchingOperation { MomoxCheckCoordinator.enqueue(context, snapshot) }.isFailure)
                            message = "Prüfung konnte nicht gestartet werden. Bitte erneut versuchen."
                    }
                }
                val restoreSold: (List<String>) -> Unit = { ids ->
                    scope.launch {
                        val restored = catchingOperation { dao.updateSoldStatus(ids, null) }
                        message = if (restored.isSuccess) (if (ids.size == 1) "Buch zurück in Meine Bücher gelegt." else "${ids.size} Bücher zurück in Meine Bücher gelegt.")
                            else "Zurückholen fehlgeschlagen. Bitte erneut versuchen."
                    }
                }
                val markSold: (List<String>) -> Unit = { ids ->
                    scope.launch {
                        val marked = catchingOperation { dao.updateSoldStatus(ids, System.currentTimeMillis()) }
                        if (marked.isFailure) message = "Markieren fehlgeschlagen. Bitte erneut versuchen."
                        else showUndo(if (ids.size == 1) "Als verkauft markiert" else "${ids.size} Bücher als verkauft markiert") { dao.updateSoldStatus(ids, null) }
                    }
                }
                val deleteBook: (BookEntity) -> Unit = { book ->
                    scope.launch {
                        if (catchingOperation { dao.deleteBook(book.id) }.isFailure) {
                            message = "Entfernen fehlgeschlagen. Bitte erneut versuchen."
                            return@launch
                        }
                        // The check history stays keyed by ISBN, so undo restores the full book.
                        showUndo(book.title?.let { "„$it“ entfernt" } ?: "Buch entfernt") {
                            if (dao.insert(book) == -1L) message = "Nicht wiederhergestellt: Diese ISBN ist inzwischen wieder in der Liste."
                        }
                    }
                }
                LaunchedEffect(checkRun) {
                    checkRun?.let { run ->
                        if (run.finishedAt != null && run.pausedReason == null && run.startedAt != dismissedSummaryRun) {
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
                val closeCandidates: () -> Unit = {
                    coverCandidates = emptyList(); coverFrame = null; visualCoverTest = false
                }
                val visibleBooks = remember(books, soldBooks, showSoldBooks, statusFilter, sortMode, searchInput) {
                    // Status chips exist only for active books; a filter chosen there must not hide sold books.
                    BookListQuery.apply(if (showSoldBooks) soldBooks else books, searchInput,
                        if (showSoldBooks) BookListQuery.FILTER_ALL else statusFilter, sortMode, showSoldBooks)
                }
                val totals = remember(books, soldBooks, showSoldBooks) { BookListQuery.totals(if (showSoldBooks) soldBooks else books) }
                val visibleIds = visibleBooks.map { it.id }.toSet()
                val selectedVisibleIds = selectedIsbns.intersect(visibleIds)
                val exitSelection: () -> Unit = { selectionMode = false; selectedIsbns = emptySet() }
                BackHandler(enabled = selectionMode && !scannerOpen) { exitSelection() }
                BackHandler(enabled = scannerOpen) { scannerOpen = false; shelfCorrectionQuery = null; if (shelfReviewOpen) session.shelfReview.resume() }
                BackHandler(enabled = !scannerOpen && (shelfResults != null || coverCandidates.isNotEmpty())) { closeShelfReview(); closeCandidates() }

                if (settingsOpen) {
                    SettingsScreen(themeMode, { themeMode = it }, { settingsOpen = false })
                    return@BuchRadarTheme
                }
                detailsBook?.let { original ->
                    val current = (books + soldBooks).firstOrNull { it.id == original.id } ?: original
                    BookDetailsScreen(current, onClose = { detailsBook = null })
                    return@BuchRadarTheme
                }
                BookListScrollEffect(bookListState, session.scrollToBookId.value, listOf(LIST_HEADER_KEY) + visibleBooks.map { it.id },
                    enabled = !scannerOpen && shelfResults == null && coverCandidates.isEmpty()) {
                    session.scrollToBookId.value = null
                }
                val onListScreen = !scannerOpen && shelfResults == null && coverCandidates.isEmpty()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        if (!scannerOpen) TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = if (selectionMode) colorScheme.secondaryContainer else colorScheme.surface),
                            navigationIcon = {
                                when {
                                    selectionMode && onListScreen -> IconButton(onClick = exitSelection) { Icon(Icons.Filled.Close, contentDescription = "Auswahl beenden") }
                                    shelfResults != null || coverCandidates.isNotEmpty() -> IconButton(onClick = { closeShelfReview(); closeCandidates() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zur Liste")
                                    }
                                }
                            },
                            title = {
                                when {
                                    selectionMode && onListScreen -> Text("${selectedVisibleIds.size} ausgewählt")
                                    shelfResults != null -> Text("Regal-Funde")
                                    coverCandidates.isNotEmpty() -> Text("Treffer auswählen")
                                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Same look as the launcher icon; the adaptive foreground has a wide safe zone.
                                        Box(Modifier.size(38.dp).clip(CircleShape).background(androidx.compose.ui.res.colorResource(R.color.ic_launcher_background))) {
                                            Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.45f))
                                        }
                                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            actions = {
                                if (selectionMode && onListScreen) {
                                    val allSelected = visibleBooks.isNotEmpty() && selectedVisibleIds.size == visibleBooks.size
                                    TextButton(onClick = { selectedIsbns = if (allSelected) selectedIsbns - visibleIds else selectedIsbns + visibleIds }) {
                                        Text(if (allSelected) "Keine" else "Alle")
                                    }
                                } else if (shelfResults != null) {
                                    if (shelfState.groups.isNotEmpty() || shelfError != null) Box {
                                        IconButton(onClick = { shelfActionsOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Regalaktionen") }
                                        DropdownMenu(expanded = shelfActionsOpen, onDismissRequest = { shelfActionsOpen = false }) {
                                            DropdownMenuItem(text = { Text("Regal-Funde verwerfen") }, onClick = { shelfActionsOpen = false; discardShelfConfirm = true })
                                        }
                                    }
                                } else if (onListScreen) {
                                    IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen öffnen") }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (onListScreen && selectionMode) Surface(color = colorScheme.surfaceContainer, shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Button(onClick = {
                                    val ids = selectedVisibleIds.toList()
                                    exitSelection()
                                    if (showSoldBooks) restoreSold(ids) else markSold(ids)
                                }, enabled = selectedVisibleIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Icon(painterResource(if (showSoldBooks) R.drawable.ic_books else R.drawable.ic_sold), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text(if (showSoldBooks) "Zurück in Meine Bücher (${selectedVisibleIds.size})" else "Als verkauft markieren (${selectedVisibleIds.size})")
                                }
                            }
                        } else if (onListScreen) NavigationBar(containerColor = colorScheme.surfaceContainer) {
                            NavigationBarItem(
                                selected = !showSoldBooks,
                                onClick = { showSoldBooks = false; exitSelection() },
                                icon = { Icon(painterResource(R.drawable.ic_books), contentDescription = null) },
                                label = { Text("Bücher (${books.size})", style = MaterialTheme.typography.labelLarge) }
                            )
                            NavigationBarItem(
                                selected = showSoldBooks,
                                onClick = { showSoldBooks = true; exitSelection() },
                                icon = { Icon(painterResource(R.drawable.ic_sold), contentDescription = null) },
                                label = { Text("Verkauft (${soldBooks.size})", style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                ) { padding ->
                    if (scannerOpen) {
                        BookScannerView(
                            initialMode = scannerStartMode,
                            onBarcode = { rawValue ->
                                AppLog.add(context, "Barcode erkannt (${rawValue.length} Zeichen)")
                                val correctionTarget = shelfCorrectionQuery
                                val parsed = BookInputFormat.parseIsbn(rawValue)
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
                                val isbn = BookIdentity.unambiguousIsbn(text)
                                val correctionTarget = shelfCorrectionQuery
                                AppLog.add(context, "OCR-Text übernommen (${text.length} Zeichen, ISBN=${isbn != null})")
                                scannerOpen = false
                                if (correctionTarget != null) {
                                    correctShelfGroup(correctionTarget, isbn ?: text, frame)
                                } else {
                                    shelfReviewOpen = false
                                    searchInput = isbn?.let(BookInputFormat::autoFormatIsbn) ?: text
                                    coverFrame = frame
                                    visualCoverTest = isVisualTest
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
                        val groups = shelfResults
                        val postponedCount = shelfState.groups.count { it.postponed }
                        LazyColumn(
                            Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (shelfBusy) "Buchrücken werden gesucht …" else "Noch ${shelfState.groups.size} von $shelfTotalCount Buchrücken offen.",
                                        style = MaterialTheme.typography.bodyLarge)
                                    if (isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { session.shelfReview.pause(); shelfCorrectionQuery = null; openScanner(ScannerMode.SHELF) }, modifier = Modifier.weight(1f)) {
                                            Icon(painterResource(R.drawable.ic_camera), null, Modifier.padding(end = 8.dp)); Text("Weitere scannen")
                                        }
                                        OutlinedButton(onClick = closeShelfReview) { Text("Zur Liste") }
                                    }
                                    if (postponedCount > 0) TextButton(onClick = { session.shelfReview.restorePostponed() }) { Text("Zurückgestellte Funde ($postponedCount) weiterbearbeiten") }
                                    if (groups.any { it.error != null }) TextButton(onClick = { session.shelfReview.retryFailures() }) {
                                        Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 6.dp)); Text("Fehlgeschlagene Suchen wiederholen")
                                    }
                                    shelfError?.let {
                                        InfoBanner(it)
                                        TextButton(onClick = { session.shelfReview.resume() }) { Text("Erneut versuchen") }
                                    }
                                    message?.let { InfoBanner(it, onDismiss = { message = null }) }
                                    if (!isSearching && groups.isEmpty()) Text(when {
                                        postponedCount > 0 -> "Die übrigen Funde sind für später zurückgestellt."
                                        shelfTotalCount == 0 -> "Keine offenen Buchrücken. Scanne ein Regal, um Funde zu sammeln."
                                        else -> "Alle Buchrücken sind erledigt."
                                    }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                                }
                            }
                            items(groups, key = { "shelf-${it.id}" }) { group ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Erkannt: ${group.query}", style = MaterialTheme.typography.titleMedium)
                                        if (group.pending) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LinearProgressIndicator(Modifier.weight(1f)); Text("Suche läuft …", style = MaterialTheme.typography.bodyMedium)
                                        }
                                        else if (group.error != null) Text(group.error, color = colorScheme.error)
                                        else if (group.candidates.isEmpty()) Text("Kein passender Katalogtreffer. Korrigiere den Text, nutze die Kamera oder stelle den Fund für später zurück.", style = MaterialTheme.typography.bodyMedium)
                                        group.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant) }
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
                                            if (isActive) TextButton(onClick = { session.shelfReview.complete(group.id) }) {
                                                Icon(Icons.Filled.Check, null, Modifier.padding(end = 6.dp)); Text("Vorhandenes Buch bestätigen")
                                            }
                                        }
                                        if (shelfEditQuery == group.id) {
                                            OutlinedTextField(
                                                value = shelfEditText,
                                                onValueChange = { shelfEditText = it },
                                                label = { Text("Titel oder Autor korrigieren") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = { if (shelfEditText.isNotBlank()) correctShelfGroup(group.id, shelfEditText, null) })
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = { correctShelfGroup(group.id, shelfEditText, null) }, enabled = shelfEditText.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Korrigiert suchen") }
                                                TextButton(onClick = { shelfEditQuery = null }) { Text("Abbrechen") }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                session.shelfReview.pause()
                                                shelfCorrectionQuery = group.id
                                                openScanner(ScannerMode.SINGLE_BOOK)
                                            }, modifier = Modifier.weight(1f)) { Icon(painterResource(R.drawable.ic_camera), null, Modifier.padding(end = 6.dp)); Text("Einzeln") }
                                            OutlinedButton(onClick = {
                                                shelfEditQuery = group.id
                                                shelfEditText = group.query
                                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null, Modifier.padding(end = 6.dp)); Text("Text ändern") }
                                        }
                                        TextButton(onClick = {
                                            session.shelfReview.postpone(group.id)
                                            if (shelfEditQuery == group.id) shelfEditQuery = null
                                        }) { Text("Für später zurückstellen") }
                                    }
                                }
                            }
                        }
                    } else if (coverCandidates.isNotEmpty()) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                label = { Text("Suchtext bearbeiten") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { if (searchInput.isNotBlank() && !isSearching) { coverCandidates = emptyList(); submitInput(searchInput, coverFrame) } })
                            )
                            message?.let { InfoBanner(it, onDismiss = { message = null }) }
                            Text(if (visualCoverTest) "Cover-Test: Kandidaten werden nur nach dem visuellen Cover-Abgleich sortiert. Der Suchtext liefert weiterhin die Kandidaten." else "ISBN und Ausgabe vergleichen. Coverwerte sind ein zusätzlicher Hinweis, keine sichere Identifikation.",
                                style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(coverCandidates, key = { it.candidateId }) { candidate ->
                                    val isActive = candidate.isbn?.let { isbn -> books.any { it.isbn == isbn } } == true
                                    val isSold = candidate.isbn?.let { isbn -> soldBooks.any { it.isbn == isbn } } == true
                                    CandidateCard(candidate, isActive, isSold) {
                                        val candidateIsbn = candidate.isbn.orEmpty()
                                        if (isSold) {
                                            soldBooks.firstOrNull { it.isbn == candidateIsbn }?.let { restoreSold(listOf(it.id)) }
                                            showSoldBooks = false
                                            closeCandidates()
                                        } else if (!isActive) scope.launch {
                                            val newBook = BookEntity(candidateIsbn, candidate.title, candidate.author, candidate.publicationYear, candidate.coverUrl, metadataSource = candidate.sources.joinToString())
                                            val saved = catchingOperation { dao.insert(newBook) }
                                            if (saved.isFailure) {
                                                message = "Buch konnte nicht gespeichert werden. Die Treffer bleiben erhalten."
                                                return@launch
                                            }
                                            closeCandidates()
                                            searchInput = ""
                                            statusFilter = BookListQuery.FILTER_ALL
                                            sortMode = BookListQuery.SORT_RECENT
                                            message = if (saved.getOrNull() == -1L) "Dieses Buch ist bereits gespeichert." else "Hinzugefügt: ${candidate.title}"
                                            if (saved.getOrNull() != -1L) session.scrollToBookId.value = newBook.id
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { coverCandidates = emptyList(); submitInput(searchInput, coverFrame) }, enabled = searchInput.isNotBlank() && !isSearching, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Search, null, Modifier.padding(end = 6.dp)); Text("Erneut suchen")
                                }
                                TextButton(onClick = { closeCandidates(); searchInput = "" }, modifier = Modifier.weight(1f)) { Text("Zurück zur Eingabe") }
                            }
                        }
                    } else Column(Modifier.fillMaxSize().padding(padding)) {
                        Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!selectionMode) OutlinedTextField(
                                    value = searchInput,
                                    onValueChange = { searchInput = it; coverFrame = null; visualCoverTest = false },
                                    placeholder = { Text(if (showSoldBooks) "Verkaufte durchsuchen" else "ISBN oder Titel", maxLines = 1) },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    trailingIcon = if (searchInput.isNotEmpty()) {
                                        { IconButton(onClick = { searchInput = ""; coverFrame = null; message = null }) { Icon(Icons.Filled.Clear, contentDescription = "Eingabe löschen") } }
                                    } else null,
                                    shape = RoundedCornerShape(28.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSearching,
                                    maxLines = 3,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { if (showSoldBooks) keyboard?.hide() else if (searchInput.isNotBlank()) submitInput(searchInput, coverFrame) })
                                )
                            if (!showSoldBooks && !selectionMode) {
                                val inputIsIsbn = BookInputFormat.parseIsbn(searchInput) != null
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (searchInput.isBlank()) {
                                        Button(onClick = { openScanner(ScannerMode.AUTOMATIC) }, enabled = !isSearching, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                            Icon(painterResource(R.drawable.ic_camera), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                            Text("Buch oder Regal scannen", style = MaterialTheme.typography.titleMedium)
                                        }
                                    } else {
                                        Button(onClick = { submitInput(searchInput, coverFrame) }, enabled = !isSearching, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                            Icon(if (inputIsIsbn) Icons.Filled.Add else Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                            Text(when { isSearching -> "Suche …"; inputIsIsbn -> "ISBN hinzufügen"; searchInput.any(Char::isLetter) -> "Im Katalog suchen"; else -> "ISBN hinzufügen" },
                                                style = MaterialTheme.typography.titleMedium)
                                        }
                                        OutlinedButton(onClick = { openScanner(ScannerMode.AUTOMATIC) }, enabled = !isSearching, modifier = Modifier.heightIn(min = 52.dp)) {
                                            Icon(painterResource(R.drawable.ic_camera), contentDescription = "Kamera öffnen")
                                        }
                                    }
                                }
                                if (searchInput.any(Char::isLetter) && !isSearching) TextButton(onClick = { manualEntry = BookEntity("", searchInput) }) {
                                    Icon(Icons.Filled.Edit, null, Modifier.padding(end = 6.dp)); Text("Buch manuell erfassen")
                                }
                                if (shelfState.groups.isNotEmpty() || shelfError != null) Surface(
                                    color = colorScheme.tertiaryContainer, contentColor = colorScheme.onTertiaryContainer,
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(R.drawable.ic_shelf), contentDescription = null)
                                        Text(if (shelfError != null) "Gespeicherte Regal-Funde" else "${shelfState.groups.size} Regal-Funde offen",
                                            Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge)
                                        TextButton(enabled = !singleSearchBusy, onClick = { shelfReviewOpen = true; message = null; session.shelfReview.resume() }) {
                                            Text(if (shelfError != null) "Laden" else "Fortsetzen", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            message?.let { InfoBanner(it, onDismiss = { message = null }) }
                        }
                        LazyColumn(
                            state = bookListState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Overview, Momox action and chips scroll away so long lists keep their space.
                            item(key = LIST_HEADER_KEY) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (books.isNotEmpty() || showSoldBooks) ListOverview(if (showSoldBooks) "Verkaufte Bücher" else "Meine Bücher", totals, showValue = !showSoldBooks)
                                    if (!showSoldBooks && books.isNotEmpty()) {
                                        if (isCheckingMomox) Card(colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Momox-Prüfung · $momoxProgress von $momoxTotal", style = MaterialTheme.typography.titleMedium)
                                                LinearProgressIndicator(progress = { if (momoxTotal > 0) momoxProgress / momoxTotal.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                                                checkRun?.pausedReason?.let { Text(it, color = colorScheme.error) }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (checkRun?.pausedReason != null) OutlinedButton(onClick = { scope.launch { catchingOperation { MomoxCheckCoordinator.resume(context) } } }) { Text("Erneut versuchen") }
                                                    TextButton(onClick = { scope.launch { catchingOperation { MomoxCheckCoordinator.cancel(context) } } }) { Text("Prüfung beenden") }
                                                }
                                            }
                                        } else if (!selectionMode) FilledTonalButton(onClick = { momoxCheckDialogOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                            Text("Momox-Preise prüfen", style = MaterialTheme.typography.titleMedium)
                                        }
                                        momoxSummary?.let {
                                            if (!isCheckingMomox) MomoxSummaryCard(it, summaryExpanded, { summaryExpanded = !summaryExpanded }) {
                                                momoxSummary = null; dismissedSummaryRun = checkRun?.startedAt
                                            }
                                        }
                                    }
                                    if (!selectionMode && (if (showSoldBooks) soldBooks else books).isNotEmpty()) Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            FilterChip(selected = sortMode != BookListQuery.SORT_RECENT, onClick = { sortMenuOpen = true },
                                                label = { Text(BookListQuery.sortLabel(sortMode)) },
                                                leadingIcon = { Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sortierung", modifier = Modifier.size(18.dp)) })
                                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                                BookListQuery.sorts.forEach { (key, label) ->
                                                    DropdownMenuItem(text = { Text(label) }, onClick = { sortMode = key; sortMenuOpen = false },
                                                        trailingIcon = if (key == sortMode) { { Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") } } else null)
                                                }
                                            }
                                        }
                                        if (!showSoldBooks) BookListQuery.filters.forEach { (key, label) ->
                                            FilterChip(selected = statusFilter == key, onClick = { statusFilter = key }, label = { Text(label) },
                                                leadingIcon = if (statusFilter == key) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null)
                                        }
                                    }
                                }
                            }
                            if (visibleBooks.isEmpty()) item(key = "empty") {
                                val noBooksYet = if (showSoldBooks) soldBooks.isEmpty() else books.isEmpty()
                                EmptyState(
                                    title = when {
                                        showSoldBooks && noBooksYet -> "Noch nichts verkauft"
                                        noBooksYet -> "Noch keine Bücher"
                                        else -> "Keine Treffer"
                                    },
                                    text = when {
                                        showSoldBooks && noBooksYet -> "Halte ein Buch in „Meine Bücher“ gedrückt, um es als verkauft zu markieren."
                                        noBooksYet -> "Scanne den Barcode auf der Rückseite, ein Titelblatt oder ein ganzes Regal. Du kannst ISBN oder Titel auch oben eintippen."
                                        else -> "Kein Buch entspricht der Suche oder dem Filter."
                                    },
                                    action = when {
                                        !noBooksYet && (statusFilter != BookListQuery.FILTER_ALL || searchInput.isNotBlank()) -> "Suche und Filter zurücksetzen" to { statusFilter = BookListQuery.FILTER_ALL; searchInput = "" }
                                        else -> null
                                    }
                                )
                            } else {
                                items(visibleBooks, key = { it.id }) { book ->
                                    BookCard(
                                        book = book,
                                        selectionMode = selectionMode,
                                        selected = book.id in selectedIsbns,
                                        isSold = showSoldBooks,
                                        checkQueued = book.isbn.isNotBlank() && book.isbn in pendingSet,
                                        onSelect = {
                                            selectedIsbns = if (book.id in selectedIsbns) selectedIsbns - book.id else selectedIsbns + book.id
                                        },
                                        onLongSelect = { keyboard?.hide(); selectionMode = true; selectedIsbns = selectedIsbns + book.id },
                                        onRestore = { restoreSold(listOf(book.id)) },
                                        onOpen = { detailsBook = book },
                                        onEdit = { editingBook = book },
                                        onDelete = { deletingBook = book },
                                        onMomoxCheck = { runMomoxCheck(listOf(book)) },
                                        onMarkSold = { markSold(listOf(book.id)) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (momoxCheckDialogOpen) AlertDialog(
                    onDismissRequest = { momoxCheckDialogOpen = false },
                    title = { Text("Momox-Preise prüfen") },
                    text = { Column {
                        Text("Welche Bücher sollen nacheinander geprüft werden?")
                        val now = System.currentTimeMillis()
                        listOf(
                            Triple("new", "Nur neue / ungeprüfte", books.count { it.isbn.isNotBlank() && it.lastCheckedAt == null }),
                            Triple("stale", "Fällige und fehlgeschlagene", books.count { it.isbn.isNotBlank() && (it.lastCheckedAt == null || now - it.lastCheckedAt >= 24L * 60 * 60 * 1000 || it.lastAttemptStatus == "ERROR") }),
                            Triple("all", "Alle Bücher", books.count { it.isbn.isNotBlank() })
                        ).forEach { (key, label, count) ->
                            Row(Modifier.fillMaxWidth().selectable(selected = momoxCheckMode == key, role = Role.RadioButton, onClick = { momoxCheckMode = key }).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = momoxCheckMode == key, onClick = null)
                                Text("$label ($count)", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    } },
                    confirmButton = { FilledTonalButton(onClick = {
                        val now = System.currentTimeMillis()
                        val snapshot = when (momoxCheckMode) {
                            "new" -> books.filter { it.lastCheckedAt == null }
                            "stale" -> books.filter { it.lastCheckedAt == null || now - it.lastCheckedAt >= 24L * 60 * 60 * 1000 || it.lastAttemptStatus == "ERROR" }
                            else -> books
                        }.filter { it.isbn.isNotBlank() }
                        momoxCheckDialogOpen = false
                        runMomoxCheck(snapshot)
                    }) { Text("Prüfung starten") } },
                    dismissButton = { TextButton(onClick = { momoxCheckDialogOpen = false }) { Text("Abbrechen") } }
                )

                deletingBook?.let { book ->
                    AlertDialog(
                        onDismissRequest = { deletingBook = null },
                        title = { Text("Buch entfernen?") },
                        text = { Text("„${book.title ?: "Unbekannter Titel"}“ wird aus deiner Liste gelöscht. Direkt danach kannst du das noch rückgängig machen.") },
                        confirmButton = { TextButton(onClick = { deletingBook = null; deleteBook(book) }) { Text("Entfernen", color = colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { deletingBook = null }) { Text("Abbrechen") } }
                    )
                }

                if (discardShelfConfirm) AlertDialog(onDismissRequest = { discardShelfConfirm = false },
                    title = { Text("Offene Regal-Funde verwerfen?") },
                    text = { Text("Die gespeicherten offenen und zurückgestellten Regal-Funde werden entfernt. Bereits gespeicherte Bücher bleiben erhalten.") },
                    confirmButton = { TextButton(onClick = { session.shelfReview.discardAll(); discardShelfConfirm = false }) { Text("Verwerfen", color = colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { discardShelfConfirm = false }) { Text("Abbrechen") } })

                manualEntry?.let { draft -> EditBookDialog(draft, title = "Buch manuell erfassen", onDismiss = { manualEntry = null }) { isbnText, title, author ->
                    val isbn = BookIdentity.canonicalIsbn(isbnText)
                    if (isbnText.isNotBlank() && isbn == null) "Bitte ISBN korrigieren oder leer lassen."
                    else if (title.isBlank()) "Bitte einen Titel eingeben."
                    else {
                        val newBook = BookEntity(isbn.orEmpty(), title.trim(), author.trim().takeIf(String::isNotBlank))
                        val result = catchingOperation { dao.insert(newBook) }
                        when {
                            result.isFailure -> "Speichern fehlgeschlagen."
                            result.getOrNull() == -1L -> "Diese ISBN ist bereits gespeichert."
                            else -> {
                                searchInput = ""; statusFilter = BookListQuery.FILTER_ALL; sortMode = BookListQuery.SORT_RECENT
                                message = "Buch gespeichert."; session.scrollToBookId.value = newBook.id; null
                            }
                        }
                    }
                } }
                editingBook?.let { book ->
                    EditBookDialog(
                        book = book,
                        onDismiss = { editingBook = null },
                        onSave = { isbnText, titleText, authorText ->
                            val parsed = BookInputFormat.parseIsbn(isbnText)
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
            }
        }
    }
}

private const val LIST_HEADER_KEY = "list-header"

@androidx.compose.runtime.Composable
private fun EmptyState(title: String, text: String, action: Pair<String, () -> Unit>?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_books), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
    }
}
