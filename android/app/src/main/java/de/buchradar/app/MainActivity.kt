package de.buchradar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.room.Room
import de.buchradar.app.data.BookEntity
import de.buchradar.app.data.BuchRadarDatabase
import de.buchradar.app.network.BookCatalog
import de.buchradar.app.network.CoverImageMatcher
import de.buchradar.app.network.MomoxCatalog
import de.buchradar.app.scanner.BookScannerView
import de.buchradar.app.scanner.ScannerMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
            var themeMode by remember {
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
                val database = remember(context) {
                    Room.databaseBuilder(
                        context.applicationContext,
                        BuchRadarDatabase::class.java,
                        "buchradar.db"
                    ).addMigrations(BuchRadarDatabase.MIGRATION_1_2, BuchRadarDatabase.MIGRATION_2_3, BuchRadarDatabase.MIGRATION_3_4).build()
                }
                val dao = remember(database) { database.bookDao() }
                val books by dao.observeAll().collectAsState(initial = emptyList())
                val soldBooks by dao.observeSold().collectAsState(initial = emptyList())
                val bookListState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                var searchInput by remember { mutableStateOf("") }
                var isSearching by remember { mutableStateOf(false) }
                var isCheckingMomox by remember { mutableStateOf(false) }
                var momoxProgress by remember { mutableStateOf(0) }
                var momoxTotal by remember { mutableStateOf(0) }
                var message by remember { mutableStateOf<String?>(null) }
                var editingBook by remember { mutableStateOf<BookEntity?>(null) }
                var deletingBook by remember { mutableStateOf<BookEntity?>(null) }
                var scannerOpen by remember { mutableStateOf(false) }
                var coverFrame by remember { mutableStateOf<Bitmap?>(null) }
                var visualCoverTest by remember { mutableStateOf(false) }
                var coverCandidates by remember { mutableStateOf<List<de.buchradar.app.network.CatalogBook>>(emptyList()) }
                var shelfResults by remember { mutableStateOf<List<ShelfLookupGroup>?>(null) }
                var shelfTotalCount by remember { mutableStateOf(0) }
                var shelfWorkGeneration by remember { mutableStateOf(0) }
                var shelfCorrectionQuery by remember { mutableStateOf<String?>(null) }
                var shelfEditQuery by remember { mutableStateOf<String?>(null) }
                var shelfEditText by remember { mutableStateOf("") }
                var scannerStartMode by remember { mutableStateOf(ScannerMode.AUTOMATIC) }
                var shelfSearchJob by remember { mutableStateOf<Job?>(null) }
                var momoxSummary by remember { mutableStateOf<MomoxSummary?>(null) }
                var summaryExpanded by remember { mutableStateOf(false) }
                var settingsOpen by remember { mutableStateOf(false) }
                var clearLogConfirmOpen by remember { mutableStateOf(false) }
                var logEntries by remember { mutableStateOf(emptyList<String>()) }
                var autoCheckEnabled by remember { mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("auto_check", false)) }
                var autoCheckDays by remember { mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getInt("auto_check_days", 7)) }
                var notificationsEnabled by remember { mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("notifications", false)) }
                var notifyOnlyAboveThreshold by remember { mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getBoolean("notify_threshold", false)) }
                var notifyThresholdCents by remember { mutableStateOf(context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).getInt("notify_threshold_cents", 1_000)) }
                var showSoldBooks by remember { mutableStateOf(false) }
                var selectionMode by remember { mutableStateOf(false) }
                var selectedIsbns by remember { mutableStateOf<Set<String>>(emptySet()) }
                var statusFilter by remember { mutableStateOf("all") }
                var sortMode by remember { mutableStateOf("recent") }
                var filterMenuOpen by remember { mutableStateOf(false) }
                var sortMenuOpen by remember { mutableStateOf(false) }
                var momoxCheckDialogOpen by remember { mutableStateOf(false) }
                var momoxCheckMode by remember { mutableStateOf("stale") }
                var thresholdMenuOpen by remember { mutableStateOf(false) }
                var themeMenuOpen by remember { mutableStateOf(false) }
                var autoCheckMenuOpen by remember { mutableStateOf(false) }
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
                                isSearching = true
                                message = "Suche Titel und Buchdaten …"
                                scope.launch {
                                    val lookup = runCatching { BookCatalog.findByIsbn(compact) }
                                    val metadata = lookup.getOrNull()
                                    dao.insert(BookEntity(isbn = compact, title = metadata?.title, author = metadata?.author, publicationYear = metadata?.publicationYear, coverUrl = metadata?.coverUrl))
                                    searchInput = ""
                                    coverFrame = null
                                    isSearching = false
                                    message = when {
                                        lookup.isFailure -> "Katalog nicht erreichbar. ISBN gespeichert; Titel und Autor kannst du ergänzen."
                                        metadata == null -> "ISBN gespeichert, aber im Katalog nicht gefunden. Titel und Autor kannst du ergänzen."
                                        else -> "Hinzugefügt: ${metadata.title}"
                                    }
                                    delay(100)
                                    if (bookListState.layoutInfo.totalItemsCount > 0) bookListState.animateScrollToItem(0)
                                }
                            }
                        }
                    } else if (rawInput.any(Char::isLetter)) {
                        showSoldBooks = false
                        isSearching = true
                        message = "Suche Titel, Autor und Cover …"
                        scope.launch {
                            val result = runCatching {
                                val matches = BookCatalog.findByRecognizedText(rawInput)
                                (frame ?: coverFrame)?.let { CoverImageMatcher.rank(context, it, matches, visualOnly = visualCoverTest) } ?: matches
                            }
                            coverCandidates = result.getOrDefault(emptyList())
                            message = when {
                                result.isFailure -> "Buchkatalog nicht erreichbar."
                                coverCandidates.isEmpty() -> "Keine passenden Treffer gefunden. Prüfe den Text oder tippe ihn oben ein."
                                else -> null
                            }
                            isSearching = false
                        }
                    } else {
                        message = "Bitte eine gültige ISBN oder einen Buchtitel eingeben."
                    }
                }
                val runMomoxCheck: (List<BookEntity>) -> Unit = { snapshot ->
                    if (snapshot.isEmpty()) message = "Für diese Auswahl gibt es keine zu prüfenden Bücher."
                    else {
                        isCheckingMomox = true
                        momoxProgress = 0
                        momoxTotal = snapshot.size
                        momoxSummary = null
                        scope.launch {
                            var available = 0
                            var unavailable = 0
                            var failed = 0
                            var totalValueCents = 0
                            snapshot.forEachIndexed { index, book ->
                                message = "Prüfe Momox ${index + 1}/${snapshot.size}: ${book.title ?: displayIsbn(book.isbn)}"
                                val checkedAt = System.currentTimeMillis()
                                AppLog.add(context, "Momox ${index + 1}/${snapshot.size}: Anfrage gestartet")
                                val result = runCatching { MomoxCatalog.check(book.isbn) }
                                val check = result.getOrNull()
                                if (check == null) {
                                    failed++
                                    dao.updateCheck(book.isbn, "ERROR", null, checkedAt)
                                    AppLog.add(context, "Momox ${index + 1}/${snapshot.size}: Fehler – ${result.exceptionOrNull()?.message ?: "unbekannt"}")
                                } else {
                                    dao.updateCheck(book.isbn, check.status, check.priceCents, checkedAt)
                                    AppLog.add(context, "Momox ${index + 1}/${snapshot.size}: ${check.status}${check.priceCents?.let { ", ${NumberFormat.getCurrencyInstance(Locale.GERMANY).format(it / 100.0)}" } ?: ""}")
                                    when (check.status) {
                                        "AVAILABLE" -> { available++; totalValueCents += check.priceCents ?: 0 }
                                        "NOT_AVAILABLE" -> unavailable++
                                        else -> failed++
                                    }
                                }
                                momoxProgress = index + 1
                                if (index < snapshot.lastIndex) delay(750)
                            }
                            isCheckingMomox = false
                            momoxSummary = MomoxSummary(available, unavailable, failed, totalValueCents)
                            summaryExpanded = false
                            message = null
                        }
                    }
                }
                val searchShelf: (List<String>) -> Unit = { spines ->
                    shelfSearchJob?.cancel()
                    shelfWorkGeneration += 1
                    val requestId = shelfWorkGeneration
                    scannerOpen = false
                    isSearching = true
                    shelfResults = emptyList()
                    shelfTotalCount = spines.distinctBy(::foldForListSearch).size
                    message = "Suche ${spines.size} erkannte Buchrücken …"
                    shelfSearchJob = scope.launch {
                        val result = runCatching { BookCatalog.findByShelfText(spines) }
                        if (requestId != shelfWorkGeneration) return@launch
                        val matches = result.getOrDefault(emptyList()).groupBy { it.shelfQuery.orEmpty() }
                        shelfResults = spines.distinctBy(::foldForListSearch).mapIndexed { index, spine ->
                            ShelfLookupGroup(id = index.toString(), query = spine, candidates = matches[spine].orEmpty())
                        }
                        isSearching = false
                        shelfSearchJob = null
                        message = if (result.isFailure) "Der Katalog ist nicht erreichbar. Regaltext bleibt zur manuellen Suche verfügbar." else null
                    }
                }
                val correctShelfGroup: (String, String, Bitmap?) -> Unit = { groupId, recognizedText, frame ->
                    shelfSearchJob?.cancel()
                    shelfWorkGeneration += 1
                    val requestId = shelfWorkGeneration
                    isSearching = true
                    shelfSearchJob = scope.launch {
                        val result = runCatching {
                            val isbn = parseIsbn(recognizedText)
                            val matches = if (isbn != null) listOfNotNull(BookCatalog.findByIsbn(isbn.toCompactString())) else BookCatalog.findByRecognizedText(recognizedText)
                            if (frame != null) CoverImageMatcher.rank(context, frame, matches) else matches
                        }
                        if (requestId != shelfWorkGeneration) return@launch
                        shelfResults = shelfResults?.map { group ->
                            if (group.id == groupId) group.copy(candidates = result.getOrDefault(emptyList()).map { it.copy(shelfQuery = group.query) }) else group
                        }
                        shelfCorrectionQuery = null
                        shelfEditQuery = null
                        isSearching = false
                        shelfSearchJob = null
                        message = if (result.isFailure) "Suche fehlgeschlagen. Du kannst den Titel überspringen oder noch einmal korrigieren." else null
                    }
                }
                val closeShelfReview: () -> Unit = {
                    shelfWorkGeneration += 1
                    shelfSearchJob?.cancel()
                    shelfSearchJob = null
                    isSearching = false
                    scannerOpen = false
                    shelfResults = null
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
                            "unchecked" -> book.lastCheckedAt == null || book.momoxStatus == "UNKNOWN"
                            "error" -> book.momoxStatus == "ERROR"
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
                val visibleIsbns = visibleBooks.map { it.isbn }.toSet()
                val selectedVisibleIsbns = selectedIsbns.intersect(visibleIsbns)
                BackHandler(enabled = scannerOpen) { scannerOpen = false; shelfCorrectionQuery = null }
                BackHandler(enabled = !scannerOpen && (shelfResults != null || coverCandidates.isNotEmpty())) { closeShelfReview(); coverCandidates = emptyList() }

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
                        IconButton(onClick = { logEntries = AppLog.read(context); settingsOpen = true }) { Text("⚙", fontSize = 24.sp) }
                    })
                }, bottomBar = {
                    if (!scannerOpen && shelfResults == null && coverCandidates.isEmpty()) {
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val activeTabColor = if (darkMode) Color(0xFF9BCBFF) else Color(0xFF174A7E)
                                val activeTabText = if (darkMode) Color(0xFF102A43) else Color.White
                                if (!showSoldBooks) {
                                    Button(onClick = { selectionMode = false; selectedIsbns = emptySet() }, modifier = Modifier.weight(1f).heightIn(min = 68.dp), colors = ButtonDefaults.buttonColors(containerColor = activeTabColor, contentColor = activeTabText)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Meine Bücher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${books.size} · AKTIV", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    OutlinedButton(onClick = { showSoldBooks = true; selectionMode = false; selectedIsbns = emptySet(); momoxSummary = null }, modifier = Modifier.weight(1f).heightIn(min = 68.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Verkauft", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${soldBooks.size} Bücher", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                } else {
                                    OutlinedButton(onClick = { showSoldBooks = false; selectionMode = false; selectedIsbns = emptySet(); momoxSummary = null }, modifier = Modifier.weight(1f).heightIn(min = 68.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Meine Bücher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text("${books.size} Bücher", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                    Button(onClick = { selectionMode = false; selectedIsbns = emptySet() }, modifier = Modifier.weight(1f).heightIn(min = 68.dp), colors = ButtonDefaults.buttonColors(containerColor = activeTabColor, contentColor = activeTabText)) {
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("Verkauft", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${soldBooks.size} · AKTIV", style = MaterialTheme.typography.labelLarge)
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
                                searchInput = isbn?.let(::autoFormatIsbn) ?: text
                                coverFrame = frame
                                visualCoverTest = isVisualTest
                                scannerOpen = false
                                submitInput(searchInput, frame)
                                }
                            },
                            onShelfConfirmed = { spines, _ ->
                                AppLog.add(context, "Regal-OCR übernommen (${spines.size} Buchrücken)")
                                shelfCorrectionQuery = null
                                searchInput = ""
                                coverFrame = null
                                searchShelf(spines)
                            },
                            onClose = { scannerOpen = false; shelfCorrectionQuery = null }
                        )
                    } else if (shelfResults != null) {
                        val groups = shelfResults.orEmpty()
                        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Bücherregal", style = MaterialTheme.typography.headlineSmall)
                            Text(if (isSearching) "Buchrücken werden gesucht …" else "Noch ${groups.size} von $shelfTotalCount Buchrücken offen.", style = MaterialTheme.typography.bodyLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { shelfWorkGeneration += 1; isSearching = false; scannerStartMode = ScannerMode.SHELF; shelfCorrectionQuery = null; scannerOpen = true }, enabled = !isSearching, modifier = Modifier.weight(1f)) { Text("Regal neu scannen") }
                                TextButton(onClick = closeShelfReview) { Text("Zur Liste") }
                            }
                            if (isSearching) {
                                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else if (groups.isEmpty()) {
                                Text(if (shelfTotalCount == 0) "Keine Buchrücken erkannt. Bitte halte die Kamera ruhiger und richte sie direkt auf die Titel." else "Alle Buchrücken sind erledigt.", style = MaterialTheme.typography.titleMedium)
                            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(groups, key = { "shelf-${it.id}" }) { group ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(group.query, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                            if (group.candidates.isEmpty()) Text("Kein passender Katalogtreffer. Du kannst den Text korrigieren, die Kamera verwenden oder dieses Buch überspringen.", style = MaterialTheme.typography.bodyMedium)
                                            group.candidates.forEach { candidate ->
                                                val isActive = candidate.isbn?.let { isbn -> books.any { it.isbn == isbn } } == true
                                                val isSold = candidate.isbn?.let { isbn -> soldBooks.any { it.isbn == isbn } } == true
                                                CandidateCard(candidate, isActive, isSold) {
                                                    val candidateIsbn = candidate.isbn ?: return@CandidateCard
                                                    if (isSold) message = "Dieser Titel liegt im Bereich „Verkauft“."
                                                    else if (!isActive) {
                                                        // Resolve this OCR group immediately so its alternative editions disappear at once.
                                                        shelfResults = shelfResults?.mapNotNull { otherGroup ->
                                                            if (otherGroup.id == group.id || foldForListSearch(otherGroup.query) == foldForListSearch(group.query)) null
                                                            else {
                                                                val remaining = otherGroup.candidates.filterNot { it.isbn == candidateIsbn }
                                                                if (otherGroup.candidates.isNotEmpty() && remaining.isEmpty()) null
                                                                else otherGroup.copy(candidates = remaining)
                                                            }
                                                        }
                                                        if (shelfEditQuery == group.id) shelfEditQuery = null
                                                        scope.launch {
                                                            dao.insert(BookEntity(candidateIsbn, candidate.title, candidate.author, candidate.publicationYear, candidate.coverUrl))
                                                        message = "Hinzugefügt: ${candidate.title}"
                                                        }
                                                    }
                                                }
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
                                                    Button(onClick = { correctShelfGroup(group.id, shelfEditText, null) }, enabled = shelfEditText.any(Char::isLetter), modifier = Modifier.weight(1f)) { Text("Korrigierte Suche") }
                                                    TextButton(onClick = { shelfEditQuery = null }) { Text("Abbrechen") }
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
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
                                                shelfResults = shelfResults?.filterNot { it.id == group.id }
                                                if (shelfEditQuery == group.id) shelfEditQuery = null
                                            }) { Text("Diesen Buchrücken überspringen") }
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
                            Text(if (visualCoverTest) "Cover-Test: Kandidaten werden nur nach dem visuellen Cover-Abgleich sortiert. Der Suchtext liefert weiterhin die Kandidaten." else "ISBN und Ausgabe vergleichen. Coverwerte sind ein zusätzlicher Hinweis, keine sichere Identifikation.", style = MaterialTheme.typography.bodyMedium)
                            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(coverCandidates, key = { it.isbn ?: it.title }) { candidate ->
                                    val isActive = candidate.isbn?.let { isbn -> books.any { it.isbn == isbn } } == true
                                    val isSold = candidate.isbn?.let { isbn -> soldBooks.any { it.isbn == isbn } } == true
                                    CandidateCard(candidate, isActive, isSold) {
                                        val candidateIsbn = candidate.isbn ?: return@CandidateCard
                                        if (isSold) {
                                            showSoldBooks = true
                                            coverCandidates = emptyList()
                                            visualCoverTest = false
                                            message = "Dieser Treffer liegt im Bereich „Verkauft“."
                                        } else if (!isActive) scope.launch {
                                            dao.insert(BookEntity(candidateIsbn, candidate.title, candidate.author, candidate.publicationYear, candidate.coverUrl))
                                            coverCandidates = emptyList()
                                            visualCoverTest = false
                                            coverFrame = null
                                            searchInput = ""
                                            statusFilter = "all"
                                            sortMode = "recent"
                                            message = "Hinzugefügt: ${candidate.title}"
                                            delay(120)
                                            if (bookListState.layoutInfo.totalItemsCount > 0) bookListState.animateScrollToItem(0)
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
                                { IconButton(onClick = { searchInput = ""; coverFrame = null; message = null }) { Text("×", fontSize = 24.sp) } }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !selectionMode,
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
                        if (!showSoldBooks && !selectionMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                val allVisibleSelected = visibleBooks.isNotEmpty() && visibleBooks.all { it.isbn in selectedVisibleIsbns }
                                Checkbox(checked = allVisibleSelected, onCheckedChange = { checked ->
                                    selectedIsbns = if (checked) selectedIsbns + visibleIsbns else selectedIsbns - visibleIsbns
                                })
                                Text("Alle auswählen (${visibleBooks.size})", style = MaterialTheme.typography.bodyLarge)
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
                            Text(if (showSoldBooks) "Keine als verkauft markierten Bücher." else if (books.isEmpty()) "Noch keine Bücher gespeichert. Gib eine ISBN ein oder scanne ein Regal." else "Keine Bücher entsprechen der Suche oder dem Filter.")
                        } else {
                            LazyColumn(
                                state = bookListState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(visibleBooks, key = { it.isbn }) { book ->
                                    BookCard(
                                        book = book,
                                        darkMode = darkMode,
                                        selectionMode = selectionMode,
                                        selected = book.isbn in selectedIsbns,
                                        isSold = showSoldBooks,
                                        onSelect = {
                                            selectedIsbns = if (book.isbn in selectedIsbns) selectedIsbns - book.isbn else selectedIsbns + book.isbn
                                        },
                                        onLongSelect = { selectionMode = true; selectedIsbns = selectedIsbns + book.isbn },
                                        onRestore = { scope.launch { dao.updateSoldStatus(listOf(book.isbn), null) } },
                                        onEdit = { editingBook = book },
                                        onDelete = { deletingBook = book },
                                        onMomoxCheck = {
                                            scope.launch {
                                                val checkedAt = System.currentTimeMillis()
                                                AppLog.add(context, "Momox Einzelprüfung gestartet")
                                                val result = runCatching { MomoxCatalog.check(book.isbn) }
                                                val check = result.getOrNull()
                                                if (check == null) {
                                                    dao.updateCheck(book.isbn, "ERROR", null, checkedAt)
                                                    AppLog.add(context, "Momox Einzelprüfung fehlgeschlagen: ${result.exceptionOrNull()?.message ?: "unbekannt"}")
                                                    message = result.exceptionOrNull()?.message ?: "Momox-Abfrage fehlgeschlagen."
                                                } else {
                                                    dao.updateCheck(book.isbn, check.status, check.priceCents, checkedAt)
                                                    AppLog.add(context, "Momox Einzelprüfung: ${check.status}${check.priceCents?.let { ", ${NumberFormat.getCurrencyInstance(Locale.GERMANY).format(it / 100.0)}" } ?: ""}")
                                                    message = "Momox-Einzelprüfung abgeschlossen."
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (momoxCheckDialogOpen) AlertDialog(
                            onDismissRequest = { momoxCheckDialogOpen = false },
                            title = { Text("Momox-Prüfung") },
                            text = { Column {
                                Text("Welche Bücher sollen nacheinander geprüft werden?")
                                listOf("new" to "Nur neue / ungeprüfte", "stale" to "Älter als 24 Stunden", "all" to "Alle Bücher").forEach { (key, label) ->
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(selected = momoxCheckMode == key, onClick = { momoxCheckMode = key })
                                        Text(label, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            } },
                            confirmButton = { TextButton(onClick = {
                                val now = System.currentTimeMillis()
                                val snapshot = when (momoxCheckMode) {
                                    "new" -> books.filter { it.lastCheckedAt == null || it.momoxStatus == "UNKNOWN" }
                                    "stale" -> books.filter { it.lastCheckedAt == null || now - it.lastCheckedAt >= 24L * 60 * 60 * 1000 }
                                    else -> books
                                }
                                momoxCheckDialogOpen = false
                                runMomoxCheck(snapshot)
                            }) { Text("Prüfung starten") } },
                            dismissButton = { TextButton(onClick = { momoxCheckDialogOpen = false }) { Text("Abbrechen") } }
                        )

                    }
                }

                editingBook?.let { book ->
                    EditBookDialog(
                        book = book,
                        onDismiss = { editingBook = null },
                        onSave = { isbnText, titleText, authorText ->
                            val parsed = parseIsbn(isbnText)
                                ?: return@EditBookDialog "Bitte eine gültige ISBN mit korrekter Prüfziffer eingeben."
                            val newIsbn = parsed.toCompactString()
                            val duplicate = dao.findByIsbn(newIsbn)
                            if (duplicate != null && duplicate.isbn != book.isbn) {
                                "Diese ISBN ist bereits einem anderen Eintrag zugeordnet."
                            } else {
                                val changed = dao.updateBook(
                                    oldIsbn = book.isbn,
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
                                    dao.deleteByIsbn(book.isbn)
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
                if (settingsOpen) {
                    AlertDialog(
                        onDismissRequest = { settingsOpen = false },
                        title = { Text("Einstellungen") },
                        text = {
                            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Darstellung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Box {
                                    TextButton(onClick = { themeMenuOpen = true }) { Text("${when (themeMode) { "light" -> "Hell"; "dark" -> "Dunkel"; else -> "Geräteeinstellung" }} ▾", style = MaterialTheme.typography.bodyLarge) }
                                    DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                                        listOf("system" to "Geräteeinstellung", "light" to "Hell", "dark" to "Dunkel").forEach { (key, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                themeMode = key
                                                context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putString("theme", key).apply()
                                                themeMenuOpen = false
                                            })
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(top = 4.dp))
                                Text("Automatische Prüfung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Regelmäßig Momox prüfen", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Switch(checked = autoCheckEnabled, onCheckedChange = { enabled ->
                                        autoCheckEnabled = enabled
                                        context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putBoolean("auto_check", enabled).apply()
                                        MomoxWorkScheduler.configure(context, enabled, autoCheckDays)
                                        AppLog.add(context, if (enabled) "Automatische Prüfung aktiviert (${if (autoCheckDays == 1) "täglich" else "wöchentlich"})" else "Automatische Prüfung ausgeschaltet")
                                    })
                                }
                                if (autoCheckEnabled) {
                                    Box {
                                        TextButton(onClick = { autoCheckMenuOpen = true }) { Text("${if (autoCheckDays == 1) "Täglich" else "Wöchentlich"} ▾", style = MaterialTheme.typography.bodyLarge) }
                                        DropdownMenu(expanded = autoCheckMenuOpen, onDismissRequest = { autoCheckMenuOpen = false }) {
                                            listOf(1 to "Täglich", 7 to "Wöchentlich").forEach { (days, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                                    autoCheckDays = days
                                                    context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putInt("auto_check_days", days).apply()
                                                    MomoxWorkScheduler.configure(context, true, days)
                                                    autoCheckMenuOpen = false
                                                })
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(top = 4.dp))
                                Text("Benachrichtigungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Automatisches Prüfergebnis anzeigen", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Switch(checked = notificationsEnabled, onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            notificationsEnabled = false
                                            context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putBoolean("notifications", false).apply()
                                        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            notificationsEnabled = true
                                            context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit().putBoolean("notifications", true).apply()
                                        }
                                    })
                                }
                                if (notificationsEnabled) {
                                    Text("Benachrichtigen", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Box {
                                        TextButton(onClick = { thresholdMenuOpen = true }) {
                                            Text(when {
                                                !notifyOnlyAboveThreshold -> "Bei jedem Prüfergebnis ▾"
                                                notifyThresholdCents >= 1_500 -> "Ab 15 € · Abholung möglich ▾"
                                                else -> "Ab 10 € · Versandkostenfrei ▾"
                                            }, style = MaterialTheme.typography.bodyLarge)
                                        }
                                        DropdownMenu(expanded = thresholdMenuOpen, onDismissRequest = { thresholdMenuOpen = false }) {
                                            listOf(
                                                Triple(false, 1_000, "Bei jedem Prüfergebnis"),
                                                Triple(true, 1_000, "Ab 10 € · Versandkostenfrei"),
                                                Triple(true, 1_500, "Ab 15 € · Abholung möglich")
                                            ).forEach { (enabled, amount, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                                    notifyOnlyAboveThreshold = enabled
                                                    notifyThresholdCents = amount
                                                    context.getSharedPreferences("buchradar_settings", MODE_PRIVATE).edit()
                                                        .putBoolean("notify_threshold", enabled)
                                                        .putInt("notify_threshold_cents", amount)
                                                        .apply()
                                                    thresholdMenuOpen = false
                                                })
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(top = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Protokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { clearLogConfirmOpen = true }, enabled = logEntries.isNotEmpty()) { Text("Leeren") }
                                }
                                if (logEntries.isEmpty()) Text("Noch keine Einträge.", style = MaterialTheme.typography.bodyLarge)
                                else Text(logEntries.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Schließen") } },
                    )
                }
                if (clearLogConfirmOpen) AlertDialog(
                    onDismissRequest = { clearLogConfirmOpen = false },
                    title = { Text("Protokoll leeren?") },
                    text = { Text("Alle gespeicherten Diagnoseeinträge werden gelöscht.") },
                    confirmButton = { TextButton(onClick = { AppLog.clear(context); logEntries = emptyList(); clearLogConfirmOpen = false }) { Text("Leeren") } },
                    dismissButton = { TextButton(onClick = { clearLogConfirmOpen = false }) { Text("Abbrechen") } }
                )
            }
        }
    }
}

private data class ShelfLookupGroup(val id: String, val query: String, val candidates: List<de.buchradar.app.network.CatalogBook>)
private data class MomoxSummary(val available: Int, val unavailable: Int, val errors: Int, val totalValueCents: Int = 0)

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
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(candidate.title, style = MaterialTheme.typography.titleLarge)
                candidate.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                candidate.publicationYear?.let { Text("Erscheinungsjahr: $it", style = MaterialTheme.typography.bodyMedium) }
                Text(candidate.isbn?.let { "ISBN ${displayIsbn(it)}" } ?: "Keine ISBN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                candidate.textSimilarity?.let { Text("Textähnlichkeit: $it%", style = MaterialTheme.typography.labelMedium) }
                candidate.visualSimilarity?.let { Text("Cover-Abgleich: $it%", style = MaterialTheme.typography.labelMedium) }
                if (candidate.visualSimilarity == null) Text(if (coverFailed) "Kein Katalog-Cover für Bildabgleich" else "Kein Bildabgleich für diesen Treffer", style = MaterialTheme.typography.labelMedium)
                Button(onClick = onClick, enabled = !alreadyAdded && !isSold && candidate.isbn != null, modifier = Modifier.fillMaxWidth()) {
                    Text(when { alreadyAdded -> "Bereits in Liste"; isSold -> "Als verkauft markiert"; candidate.isbn == null -> "Keine ISBN zum Hinzufügen"; else -> "Buch hinzufügen" })
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMomoxCheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (selectionMode) onSelect() }, onLongClickLabel = "Buch auswählen", onLongClick = onLongSelect),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect() })
                book.coverUrl?.let { AsyncImage(model = it, contentDescription = "Cover: ${book.title ?: "Buch"}", modifier = Modifier.size(width = 68.dp, height = 98.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(book.title ?: "Unbekannter Titel", style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    book.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 2) }
                    Text("ISBN ${displayIsbn(book.isbn)}${book.publicationYear?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    when (book.momoxStatus) {
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
                        else -> Text("Momox: noch nicht geprüft", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    book.lastCheckedAt?.let { checkedAt ->
                        Text("Stand: ${SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(checkedAt))}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (!selectionMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSold) OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Zurückholen") }
                else OutlinedButton(onClick = onMomoxCheck, modifier = Modifier.weight(1f)) { Text("Prüfen") }
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
    runCatching { ISBN.valueOf(value.trim()) }.getOrNull()

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

private fun findIsbnInText(text: String): String? {
    val normalized = text.uppercase().replace(Regex("[^0-9X]"), "")
    for (length in listOf(13, 10)) {
        for (start in 0..normalized.length - length) {
            val candidate = normalized.substring(start, start + length)
            if (parseIsbn(candidate) != null) return candidate
        }
    }
    return null
}
