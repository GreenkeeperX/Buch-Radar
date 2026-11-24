@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.buchradar.app

import androidx.compose.ui.input.key.type

import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.compose.ui.input.key.key

import androidx.compose.ui.input.key.KeyEventType

import androidx.compose.ui.input.key.Key

import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.foundation.verticalScroll

import androidx.compose.ui.platform.LocalConfiguration

import android.content.res.Configuration

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.snapshotFlow
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
import de.buchradar.app.data.ISBN_TAKEN
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
                LaunchedEffect(Unit) { catchingOperation { MomoxCheckCoordinator.resumeIfAccessArrived(context) } }
                val books by dao.observeAll().collectAsState(initial = emptyList())
                val soldBooks by dao.observeSold().collectAsState(initial = emptyList())
                val pendingIsbns by dao.observePendingIsbns().collectAsState(initial = emptyList())
                val pendingSet = remember(pendingIsbns) { pendingIsbns.toSet() }
                val bookListState = rememberLazyListState()
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                // From 130 % system font the list gets the same space-saving layout as landscape.
                val largeFont = LocalConfiguration.current.fontScale >= 1.3f
                // From 170 % the filter chips would fill the screen; one filter menu replaces them.
                val hugeFont = LocalConfiguration.current.fontScale >= 1.7f
                var filterMenuOpen by remember { mutableStateOf(false) }
                // The scan button steps aside while the list scrolls down, so it never covers a card's
                // status for long; it returns on scrolling up, at the top and at the end of the list.
                var fabShown by remember { mutableStateOf(true) }
                LaunchedEffect(bookListState) {
                    var last = 0 to 0
                    snapshotFlow { Triple(bookListState.firstVisibleItemIndex, bookListState.firstVisibleItemScrollOffset, bookListState.canScrollForward) }
                        .collect { (index, offset, more) ->
                            val down = index > last.first || (index == last.first && offset > last.second + 24)
                            val up = index < last.first || (index == last.first && offset < last.second - 24)
                            if (down || up) last = index to offset
                            fabShown = when {
                                index == 0 && offset == 0 || !more -> true
                                down -> false
                                up -> true
                                else -> fabShown
                            }
                        }
                }
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
                var saleOpen by session.saleOpen
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
                    else {
                        message = "Für den Scanner wird Kamerazugriff benötigt. Du kannst ISBN oder Titel auch eintippen."
                        // The shelf review was paused for the scanner; without camera it continues.
                        shelfCorrectionQuery = null
                        if (shelfReviewOpen) session.shelfReview.resume()
                    }
                }
                val openScanner: (ScannerMode) -> Unit = { mode ->
                    keyboard?.hide()
                    scannerStartMode = mode
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scannerOpen = true
                    else cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                }
                // Pending undo lives in the ViewModel, so a rotation keeps both the data and the action.
                // The snackbar host belongs to the list screen: while details or settings cover it, the
                // undo waits instead of timing out unseen, and the 12 s start again once the list is back.
                val pendingUndo = session.pendingUndo.value
                val undoHostVisible = !settingsOpen && !saleOpen && detailsBook == null
                LaunchedEffect(pendingUndo, undoHostVisible) {
                    val undo = pendingUndo ?: return@LaunchedEffect
                    if (!undoHostVisible) return@LaunchedEffect
                    snackbar.currentSnackbarData?.dismiss()
                    // Undo is the only way back for a removed book: keep it 12 s, longer than the
                    // short default some accessibility settings impose on "Long".
                    val result = withTimeoutOrNull(12_000) {
                        snackbar.showSnackbar(undo.text, actionLabel = "Rückgängig", withDismissAction = true, duration = SnackbarDuration.Indefinite)
                    } ?: run { snackbar.currentSnackbarData?.dismiss(); SnackbarResult.Dismissed }
                    if (session.pendingUndo.value === undo) session.pendingUndo.value = null
                    if (result == SnackbarResult.ActionPerformed) scope.launch {
                        val outcome = catchingOperation {
                            val skipped = undo.deleted.count { book -> dao.insert(book) == -1L }
                            if (skipped > 0) message = if (skipped == 1) "Nicht wiederhergestellt: Diese ISBN ist inzwischen wieder in der Liste."
                                else "$skipped Bücher nicht wiederhergestellt: Ihre ISBN ist inzwischen wieder in der Liste."
                            if (undo.sold.isNotEmpty()) dao.undoSales(undo.sold)
                        }
                        if (outcome.isFailure) message = "Rückgängig machen fehlgeschlagen."
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
                // Marks only books that are still active; from the sale screen all or nothing at the shown
                // offers. The undo and the message name exactly the rows that changed.
                // onDone: null = failed, false = offers changed (nothing written), true = written.
                val markSoldChecked: (List<String>, Map<String, Int?>?, (Boolean?) -> Unit) -> Unit = { ids, expected, onDone ->
                    scope.launch {
                        val soldAt = System.currentTimeMillis()
                        val marked = catchingOperation { dao.markSold(ids, soldAt, expected) }
                        val changed = marked.getOrNull()
                        if (marked.isFailure) message = "Markieren fehlgeschlagen. Bitte erneut versuchen."
                        else if (changed != null) {
                            if (changed.isNotEmpty()) {
                                val undo = PendingUndo(if (changed.size == 1) "Als verkauft markiert" else "${changed.size} Bücher als verkauft markiert",
                                    sold = changed.associateWith { soldAt })
                                session.pendingUndo.value = session.pendingUndo.value?.plus(undo) ?: undo
                            }
                            val skipped = ids.distinct().size - changed.size
                            if (skipped > 0) message = if (skipped == 1) "1 Buch war schon als verkauft markiert." else "$skipped Bücher waren schon als verkauft markiert."
                        }
                        onDone(if (marked.isFailure) null else changed != null)
                    }
                }
                val markSold: (List<String>) -> Unit = { ids -> markSoldChecked(ids, null) {} }
                val deleteBook: (BookEntity) -> Unit = { book ->
                    scope.launch {
                        // Undo restores the row as it was deleted, including a check finished meanwhile.
                        val deleted = catchingOperation { dao.deleteBook(book.id) }
                        val row = deleted.getOrNull()
                        when {
                            deleted.isFailure -> message = "Entfernen fehlgeschlagen. Bitte erneut versuchen."
                            row == null -> message = "Dieses Buch war bereits entfernt."
                            // The check history stays keyed by ISBN, so undo restores the full book.
                            else -> {
                                val undo = PendingUndo(row.title?.let { "„$it“ entfernt" } ?: "Buch entfernt", deleted = listOf(row))
                                session.pendingUndo.value = session.pendingUndo.value?.plus(undo) ?: undo
                            }
                        }
                    }
                }
                LaunchedEffect(checkRun) {
                    checkRun?.let { run ->
                        if (run.finishedAt != null && run.pausedReason == null && run.startedAt != dismissedSummaryRun) {
                            momoxSummary = MomoxSummary(run.available, run.unavailable, run.failed, run.valueCents)
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
                if (saleOpen) {
                    val draft = session.saleDraft
                    SaleScreen(books, chosenIds = draft.value, onChosenChange = { draft.value = it },
                        onClose = { saleOpen = false }, onRecheck = runMomoxCheck,
                        onMarkSold = { expected, onResult ->
                            val sent = draft.value
                            markSoldChecked(expected.keys.toList(), expected) { outcome ->
                                onResult(outcome)
                                // Only the draft that was sent closes; a newer one opened meanwhile stays.
                                if (outcome == true && draft.value === sent) saleOpen = false
                            }
                        })
                    return@BuchRadarTheme
                }
                detailsBook?.let { original ->
                    val current = (books + soldBooks).firstOrNull { it.id == original.id } ?: original
                    BookDetailsScreen(current, onClose = { detailsBook = null },
                        onEdit = { detailsBook = null; editingBook = it },
                        onMarkSold = { detailsBook = null; markSold(listOf(it.id)) },
                        onRestore = { detailsBook = null; restoreSold(listOf(it.id)) },
                        onDelete = { detailsBook = null; deletingBook = it })
                    return@BuchRadarTheme
                }
                BookListScrollEffect(bookListState, session.scrollToBookId.value, listOf(LIST_HEADER_KEY) + visibleBooks.map { it.id },
                    enabled = !scannerOpen && shelfResults == null && coverCandidates.isEmpty()) {
                    session.scrollToBookId.value = null
                }
                val onListScreen = !scannerOpen && shelfResults == null && coverCandidates.isEmpty()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    floatingActionButton = {
                        // Collapses to the camera icon once the list scrolls and in landscape, so it covers less.
                        // From 170 % font the scan action sits in the top bar instead, so nothing floats over the cards.
                        if (onListScreen && !showSoldBooks && !selectionMode && searchInput.isBlank() && fabShown && !hugeFont) ExtendedFloatingActionButton(
                            expanded = !landscape && !largeFont && bookListState.firstVisibleItemIndex == 0,
                            onClick = { if (!isSearching) openScanner(ScannerMode.AUTOMATIC) },
                            icon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = "Buch oder Regal scannen") },
                            text = { Text("Buch oder Regal scannen", style = MaterialTheme.typography.titleMedium) }
                        )
                    },
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
                                    if (hugeFont && !showSoldBooks) IconButton(onClick = { if (!isSearching) openScanner(ScannerMode.AUTOMATIC) }) {
                                        Icon(painterResource(R.drawable.ic_camera), contentDescription = "Buch oder Regal scannen")
                                    }
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
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                            Text("Erkannt: ${group.query}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
                                            // A misread spine is not a book: drop it without adding anything. Placed at the
                                            // top because a find can list several long candidates.
                                            TextButton(onClick = {
                                                session.shelfReview.complete(group.id)
                                                if (shelfEditQuery == group.id) shelfEditQuery = null
                                            }) { Text("Verwerfen", color = colorScheme.error) }
                                        }
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
                        // In landscape and with large fonts the fixed area may take at most 40 % of the height and scrolls itself.
                        Column(Modifier.then(if (landscape || largeFont) Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .4f).dp).verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = 16.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    // Enter on a hardware keyboard searches instead of adding a line break;
                                    // the field stays multi-line to show recognised text.
                                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                                        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                            if (event.type == KeyEventType.KeyUp) {
                                                if (showSoldBooks) keyboard?.hide() else if (searchInput.isNotBlank()) submitInput(searchInput, coverFrame)
                                            }
                                            true
                                        } else false
                                    },
                                    enabled = !isSearching,
                                    maxLines = 3,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { if (showSoldBooks) keyboard?.hide() else if (searchInput.isNotBlank()) submitInput(searchInput, coverFrame) })
                                )
                            if (!showSoldBooks && !selectionMode) {
                                val inputIsIsbn = BookInputFormat.parseIsbn(searchInput) != null
                                // With an empty field, scanning is the floating action button in thumb reach.
                                if (searchInput.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    run {
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
                                    color = colorScheme.surfaceContainerHigh, contentColor = colorScheme.onSurface,
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(R.drawable.ic_shelf), contentDescription = null, tint = colorScheme.tertiary)
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
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Overview, Momox action and chips scroll away so long lists keep their space.
                            item(key = LIST_HEADER_KEY) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (books.isNotEmpty() || showSoldBooks) ListOverview(if (showSoldBooks) "Verkaufte Bücher" else "Meine Bücher", totals, showValue = !showSoldBooks,
                                        onSell = if (!showSoldBooks && !selectionMode) ({ keyboard?.hide(); session.saleDraft.value = null; saleOpen = true }) else null)
                                    // Progress and controls follow the run, even if no active book is left.
                                    if (!showSoldBooks && (books.isNotEmpty() || isCheckingMomox || momoxSummary != null)) {
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
                                        } else if (!selectionMode) MomoxActionRow(
                                            summary = momoxSummary,
                                            expanded = summaryExpanded,
                                            canCheck = books.isNotEmpty(),
                                            onToggle = { summaryExpanded = !summaryExpanded },
                                            onDismiss = { momoxSummary = null; dismissedSummaryRun = checkRun?.startedAt },
                                            onCheck = { momoxCheckDialogOpen = true }
                                        )
                                    }
                                    if (!selectionMode && (if (showSoldBooks) soldBooks else books).isNotEmpty()) Row(
                                        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            IconButton(onClick = { sortMenuOpen = true }) {
                                                Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sortierung: ${BookListQuery.sortLabel(sortMode)}",
                                                    tint = if (sortMode != BookListQuery.SORT_RECENT) colorScheme.primary else colorScheme.onSurfaceVariant)
                                            }
                                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                                BookListQuery.sorts.forEach { (key, label) ->
                                                    DropdownMenuItem(text = { Text(label) }, onClick = { sortMode = key; sortMenuOpen = false },
                                                        trailingIcon = if (key == sortMode) { { Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") } } else null)
                                                }
                                            }
                                        }
                                        if (showSoldBooks) Text(BookListQuery.sortLabel(sortMode), style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
                                        else if (hugeFont) Box(Modifier.weight(1f)) {
                                            val current = BookListQuery.filters.firstOrNull { it.first == statusFilter } ?: BookListQuery.filters.first()
                                            OutlinedButton(onClick = { filterMenuOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                                                Text("Filter: ${current.second} ${books.count { BookListQuery.matchesStatus(it, current.first) }}")
                                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                            }
                                            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                                                BookListQuery.filters.forEach { (key, label) ->
                                                    val count = books.count { BookListQuery.matchesStatus(it, key) }
                                                    if (key == BookListQuery.FILTER_ALL || count > 0 || statusFilter == key) DropdownMenuItem(
                                                        text = { Text("$label $count") }, onClick = { statusFilter = key; filterMenuOpen = false },
                                                        trailingIcon = if (key == statusFilter) { { Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") } } else null)
                                                }
                                            }
                                        }
                                        // Chips wrap instead of scrolling sideways, so no filter hides past the edge.
                                        else FlowRow(
                                            Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Counts on the chips; empty filters are hidden unless selected.
                                            BookListQuery.filters.forEach { (key, label) ->
                                                val count = books.count { BookListQuery.matchesStatus(it, key) }
                                                if (key == BookListQuery.FILTER_ALL || count > 0 || statusFilter == key) FilterChip(
                                                    selected = statusFilter == key, onClick = { statusFilter = key },
                                                    label = { Text("$label $count") },
                                                    leadingIcon = if (statusFilter == key) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null)
                                            }
                                        }
                                    }
                                }
                            }
                            if (visibleBooks.isEmpty()) item(key = "static:empty") {
                                val noBooksYet = if (showSoldBooks) soldBooks.isEmpty() else books.isEmpty()
                                EmptyState(
                                    title = when {
                                        showSoldBooks && noBooksYet -> "Noch nichts verkauft"
                                        noBooksYet -> "Noch keine Bücher"
                                        else -> "Keine Treffer"
                                    },
                                    text = when {
                                        showSoldBooks && noBooksYet -> "Halte ein Buch in „Meine Bücher“ gedrückt, um es als verkauft zu markieren."
                                        noBooksYet -> "Scanne den Barcode auf der Rückseite, ein Titelblatt oder ein ganzes Regal. Du kannst ISBN oder Titel auch oben eintippen. Danach fragt „Momox prüfen“ die Ankaufpreise ab."
                                        else -> "Kein Buch entspricht der Suche oder dem Filter."
                                    },
                                    action = when {
                                        !noBooksYet && (statusFilter != BookListQuery.FILTER_ALL || searchInput.isNotBlank()) -> "Suche und Filter zurücksetzen" to { statusFilter = BookListQuery.FILTER_ALL; searchInput = "" }
                                        else -> null
                                    }
                                )
                            } else {
                                items(visibleBooks, key = { "book:${it.id}" }) { book ->
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
                            val changed = dao.updateBook(
                                id = book.id,
                                newIsbn = newIsbn,
                                title = titleText.trim().takeIf(String::isNotEmpty),
                                author = authorText.trim().takeIf(String::isNotEmpty)
                            )
                            when (changed) {
                                1 -> { message = "Eintrag aktualisiert."; null }
                                ISBN_TAKEN -> "Diese ISBN ist bereits einem anderen Eintrag zugeordnet."
                                else -> "Der Eintrag konnte nicht aktualisiert werden."
                            }
                        }
                    )
                }
            }
        }
    }
}

private const val LIST_HEADER_KEY = "static:list-header"

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
