package fr.swucollection.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import android.net.Uri
import java.io.OutputStreamWriter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.filled.Home
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var startupError by mutableStateOf<String?>(null)
    private var optionalDetails: CardDetails? = null
    private var recoveryOnly by mutableStateOf(false)
    private val updateNavigationRequest = mutableIntStateOf(0)
    private val foregroundRequest = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UpdateNotifications.ACTION_OPEN_UPDATES) updateNavigationRequest.intValue++
    }

    override fun onResume() {
        super.onResume()
        foregroundRequest.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = CollectionDatabase(this)
        runCatching {
            database.readableDatabase
            ScanPrintCatalogue.initialize(this)
            CatalogSyncWorker.schedulePeriodic(this)
            UpdateCheckWorker.schedule(this)
            LegalitySync.initialize(applicationContext)
            LegalitySyncWorker.schedule(this)
        }.onFailure { startupError = "Le démarrage a rencontré un problème. Tes données restent sur ce téléphone."; recoveryOnly = true }
        optionalDetails = runCatching { CardDetails.load(this) }.getOrElse {
            startupError = "Les détails des cartes n’ont pas pu être chargés. Tu peux sauvegarder ta collection ou continuer avec les fonctions essentielles."
            CardDetails.empty()
        }
        if (intent.action == UpdateNotifications.ACTION_OPEN_UPDATES) updateNavigationRequest.intValue++
        setContent {
            val swuColors = darkColorScheme(
                primary = Color(0xFFFFD47D),
                onPrimary = Color(0xFF172033),
                primaryContainer = Color(0xFF213951),
                onPrimaryContainer = Color(0xFFFFD47D),
                secondary = Color(0xFF75C9D6),
                onSecondary = Color(0xFF10202A),
                secondaryContainer = Color(0xFF112842),
                onSecondaryContainer = Color(0xFF75C9D6),
                background = Color(0xFF050D1A),
                onBackground = Color(0xFFF3F5FA),
                surface = Color(0xFF0B1B2D),
                onSurface = Color(0xFFF3F5FA),
                surfaceVariant = Color(0xFF112842),
                surfaceContainerLowest = Color(0xFF050D1A),
                surfaceContainerLow = Color(0xFF0B1B2D),
                surfaceContainer = Color(0xFF0B1B2D),
                surfaceContainerHigh = Color(0xFF112842),
                surfaceContainerHighest = Color(0xFF17324D),
                onSurfaceVariant = Color(0xFFB4C0D4),
                outline = Color(0xFF9C7941),
                outlineVariant = Color(0xFF66583D)
            )
            MaterialTheme(colorScheme = swuColors) {
                CompositionLocalProvider(LocalContentColor provides swuColors.onBackground) {
                    if (startupError != null) {
                        StartupRecoveryPanel(database, startupError.orEmpty(), canContinue = !recoveryOnly,
                            onContinue = { startupError = null }, onRetry = { recreate() })
                    } else CollectionScreen(
                        details = optionalDetails ?: CardDetails.empty(),
                        database = database,
                        updateNavigationRequest = updateNavigationRequest.intValue,
                        foregroundRequest = foregroundRequest.intValue,
                        onExport = { uri ->
                            if (uri != null) contentResolver.openOutputStream(uri)?.use { output ->
                                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(database.exportCsv()) }
                            }
                        },
                        onSyncCatalog = { onResult ->
                            lifecycleScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    CatalogSyncWorker.syncRemote(applicationContext)
                                }
                                onResult(success)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionScreen(
    details: CardDetails,
    database: CollectionDatabase,
    onExport: (Uri?) -> Unit,
    onSyncCatalog: (onResult: (Boolean) -> Unit) -> Unit,
    updateNavigationRequest: Int = 0,
    foregroundRequest: Int = 0
) {
    val compactHeader = LocalConfiguration.current.screenWidthDp < 400
    var cards by remember { mutableStateOf(database.allCards()) }
    var personalLists by remember { mutableStateOf(database.cardLists()) }
    val deckContext = LocalContext.current
    val deckStore = remember(deckContext) { DeckStore(deckContext) }
    var savedDecks by remember { mutableStateOf(deckStore.all()) }
    val deckPins = remember(deckContext) { DeckPins(deckContext) }
    var pinnedDecks by remember { mutableStateOf(deckPins.all()) }
    var exportingDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparingDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val preparingDeck = savedDecks.firstOrNull { it.id == preparingDeckId }
    var addingToDeck by remember { mutableStateOf<CatalogCard?>(null) }
    val internetAvailable = rememberInternetAvailable()
    LaunchedEffect(savedDecks) {
        pinnedDecks = runCatching { deckPins.retain(savedDecks.map { it.id }.toSet()) }.getOrDefault(pinnedDecks)
    }
    var editingDeck by remember { mutableStateOf<DeckStore.SavedDeck?>(null) }
    var creatingDeck by remember { mutableStateOf(false) }
    var creatingSeed by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingDraftKey by rememberSaveable { mutableStateOf("new") }
    var proxyBuilderOpen by rememberSaveable { mutableStateOf(false) }
    var importingDeck by remember { mutableStateOf(false) }
    var deckLibraryVisible by remember { mutableStateOf(true) }
    var deckMessage by remember { mutableStateOf<String?>(null) }
    var favoriteCard by remember { mutableStateOf<CatalogCard?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = onExport
    )
    var sets by remember { mutableStateOf(database.allCatalogSets()) }
    var name by remember { mutableStateOf("") }
    var set by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("Normale") }
    var search by rememberCollectionFilter("search", "")
    val navigation = remember { AppNavigation() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val selectedSection = navigation.current.section
    val selectedSet = navigation.current.setCode
    val catalogSearch = navigation.current.catalogSearch
    LaunchedEffect(updateNavigationRequest) {
        if (updateNavigationRequest > 0) navigation.navigateTo("Mises à jour")
    }
    var menuOpen by remember { mutableStateOf(false) }
    var collectionFiltersOpen by remember { mutableStateOf(false) }
    var catalogSyncMessage by remember { mutableStateOf<String?>(null) }
    val batchContext = LocalContext.current
    val scanBatch = remember { ScanBatchStore(batchContext) }
    var scanMode by rememberCollectionFilter("scanModeV3", "Une par une")
    var scanReadToken by remember { mutableIntStateOf(0) }
    var scanLastCard by remember { mutableStateOf<ScanBatchEntry?>(null) }
    var batchVariant by rememberCollectionFilter("scanVariantMode", "Automatique")
    var batchMessage by remember { mutableStateOf<String?>(null) }
    var scannerOpen by remember { mutableStateOf(false) }
    var scanDeckPicker by remember { mutableStateOf(false) }
    var startDeckScan by remember { mutableStateOf(false) }
    var scanVariantMenuOpen by remember { mutableStateOf(false) }
    var scanCandidate by remember { mutableStateOf<CatalogCard?>(null) }
    var scanLanguage by remember { mutableStateOf(CardLanguages.UNKNOWN) }
    var addingLanguage by remember { mutableStateOf("FR") }
    var editingOwnedLanguage by remember { mutableStateOf<CollectionCard?>(null) }
    var languageError by remember { mutableStateOf<String?>(null) }
    var scanVariant by remember { mutableStateOf("") }
    var scanVariantError by remember { mutableStateOf<String?>(null) }
    var ownershipFilter by rememberCollectionFilter("ownershipFilter", "Toutes")
    var collectionSetFilter by rememberCollectionFilter("collectionSetFilter", "Toutes")
    var collectionView by rememberCollectionFilter("collectionView", "Visuel")
    var savedCollectionSort by rememberCollectionFilter("sort", CollectionPriceSort.CATALOG.name)
    val collectionSort = CollectionPriceSort.entries.firstOrNull { it.name == savedCollectionSort } ?: CollectionPriceSort.CATALOG
    var groupVersionsValue by rememberCollectionFilter("groupVersions", "false")
    val groupVersions = groupVersionsValue == "true"
    var collectionScope by rememberCollectionFilter("collectionScope", "Possédées")
    var previewSession by remember { mutableStateOf<CardPreviewSession?>(null) }
    var previewPrint by remember { mutableStateOf<CatalogCard?>(null) }
    var previewVariant by remember { mutableStateOf("Normale") }
    var previewLanguage by remember { mutableStateOf("FR") }
    var collectionLanguage by rememberCollectionFilter("language", "Toutes")
    val displayPrefs = LocalContext.current.getSharedPreferences("leader_view", 0)
    var leaderMode by remember { mutableStateOf(displayPrefs.getString("mode", "flip") ?: "flip") }
    var showReverse by remember { mutableStateOf(false) }

    fun openPreview(card: CatalogCard, visible: List<CardPreviewEntry>, variant: String? = null, language: String = card.language) {
        previewPrint = null
        previewSession = CardPreviewSession.open(CardPreviewEntry(card, variant, language), visible)
        previewLanguage = language
        previewVariant = variant ?: ScanVariants.detect(card).let { it.variant ?: it.choices.firstOrNull() ?: "Normale" }
        showReverse = false
    }
    fun movePreview(delta: Int) {
        val previous = previewSession ?: return
        val next = previous.move(delta)
        if (next.index == previous.index) return
        previewSession = next
        previewPrint = null
        previewLanguage = next.current.language
        previewVariant = next.current.variant ?: ScanVariants.detect(next.current.card).let { it.variant ?: it.choices.firstOrNull() ?: "Normale" }
        showReverse = false
    }

    fun beginNewDeck(card: CatalogCard? = null) {
        creatingDraftKey = "new:" + java.util.UUID.randomUUID().toString()
        creatingSeed = card?.let(DeckConstruction::reference)
        editingDeck = null
        creatingDeck = true
    }

    fun openSavedDeck(saved: DeckStore.SavedDeck) {
        if ((creatingDeck || editingDeck != null) && editingDeck?.id != saved.id) {
            android.widget.Toast.makeText(deckContext, "Ferme le constructeur avant d’ouvrir un autre deck.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        previewSession = null
        creatingDeck = false
        editingDeck = saved
        navigation.navigateTo("Mes listes")
    }
    fun openScanner() {
        navigation.navigateTo("Scanner")
        batchMessage = null
        scannerOpen = true
    }
    fun closeScanner() {
        scannerOpen = false
        scanCandidate = null
        scanVariantMenuOpen = false
        if (scanBatch.entries.isEmpty() && navigation.current.section == "Scanner") navigation.back()
    }

    // Dialogs (menu, card preview and camera) dismiss themselves before screen navigation.
    BackHandler(enabled = navigation.canGoBack && !menuOpen && previewSession == null &&
        !scannerOpen && scanCandidate == null && favoriteCard == null) {
        if (selectedSection == "Collection" && collectionFiltersOpen) collectionFiltersOpen = false
        else navigation.back()
    }

    // Keep catalogue queries out of recompositions caused by search typing or quantity edits.
    val catalogBySet = remember(sets) {
        sets.associate { it.code to database.catalogCardsForSet(it.code) }
    }
    val collectionPriceContext = LocalContext.current.applicationContext
    val cardDetails = details
    val allCatalog = remember(catalogBySet) { catalogBySet.values.flatten() }
    var badgeFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val badgeRevision by BadgeChanges.revision.collectAsState()
    var badgeCelebrationSeen by remember { mutableStateOf(emptySet<String>()) }
    val badgeBook by produceState<BadgeCatalogue?>(null) {
        value = withContext(Dispatchers.IO) { BadgeCatalogue.load(deckContext) }
    }
    val badgeProgress by produceState<List<BadgeProgress>>(emptyList(), cards, allCatalog, badgeRevision, badgeBook, foregroundRequest) {
        val book = badgeBook
        if(book != null && allCatalog.isNotEmpty()) value = withContext(Dispatchers.IO) {
            database.resolvePendingBadgeScans(allCatalog,cardDetails)
            database.awardBadges(BadgeEngine.evaluate(book, allCatalog, cards, cardDetails,
                database.badgeEventCounts(), database.badgeAwards()))
        }
    }
    var searchFacets by remember { mutableStateOf(SearchFacets()) }
    val catalogByKey = remember(catalogBySet) {
        catalogBySet.values.flatten().associateBy { MarketPricing.key(it.setCode, it.number) }
    }
    val priceRequests = remember(catalogByKey, cards) {
        (catalogByKey.values.map { it to MarketPricing.CATALOGUE } + cards.mapNotNull { owned ->
            catalogByKey[MarketPricing.key(owned.setCode, owned.number)]?.let { it to owned.variant }
        }).distinctBy { (card, variant) -> collectionPriceKey(card.setCode, card.number, variant) }
    }
    val collectionQuotes by produceState<Map<String, MarketQuote>>(emptyMap(),
        selectedSection, priceRequests, foregroundRequest) {
        if (selectedSection in listOf("Accueil", "Collection", "Compléter mon set", "Statistiques")) {
            suspend fun load(network: Boolean) = withContext(Dispatchers.IO) {
                priceRequests.map { (card, variant) ->
                    collectionPriceKey(card.setCode, card.number, variant) to runCatching {
                        CardmarketPrices.quote(collectionPriceContext, card, variant, false,
                            allowNetwork = network)
                    }.getOrElse { MarketQuote(null) }
                }.toMap()
            }
            value = load(false)
            value = load(true)
        }
    }
    val legalitySnapshot by LegalitySync.state.collectAsState()
    val legalityContext = LocalContext.current.applicationContext
    LaunchedEffect(foregroundRequest) {
        withContext(Dispatchers.IO) { LegalitySync.refresh(legalityContext) }
    }
    val premierLegality = remember(catalogBySet, legalitySnapshot.rules) {
        PremierLegality(catalogBySet.values.flatten(), legalitySnapshot.rules)
    }
    LaunchedEffect(savedDecks, allCatalog, legalitySnapshot.rules) {
        if(allCatalog.isNotEmpty()) withContext(Dispatchers.IO) {
            savedDecks.filter { DeckConstruction.report(it.deck,cards,allCatalog,cardDetails,legalitySnapshot.rules).issues.isEmpty() }
                .forEach { database.recordBadgeEvent("deck",BadgeEngine.deckSignature(it.deck,allCatalog,cardDetails)) }
        }
    }
    val total = cards.sumOf { it.quantity }
    val copiesPerPrint = cards.groupBy { MarketPricing.key(it.setCode,it.number) + ":" + it.variant }.mapValues { (_,rows) -> rows.sumOf { it.quantity } }
    val visibleCards = cards.filter {
        val english = catalogByKey[MarketPricing.key(it.setCode,it.number)]?.let { card -> cardDetails.get(card)?.englishName }.orEmpty()
        DeckBrowser.searchKey(english).contains(DeckBrowser.searchKey(search)) ||
        it.name.contains(search, ignoreCase = true) ||
            it.setCode.contains(search, ignoreCase = true) ||
            it.number.contains(search, ignoreCase = true)
    }
    fun refresh() {
        cards = database.allCards()
        personalLists = database.cardLists()
        savedDecks = deckStore.all()
        sets = database.allCatalogSets()
    }

    val appContext = LocalContext.current
    val reflectionPreferences = remember(appContext) {
        appContext.getSharedPreferences(REFLECTION_PREFERENCES, android.content.Context.MODE_PRIVATE)
    }
    var reflectionsEnabled by remember(appContext) {
        mutableStateOf(reflectionPreferences.getBoolean(REFLECTION_ENABLED, true))
    }
    val borderTilt = rememberBorderTilt(reflectionsEnabled && selectedSection == "Accueil" &&
        !menuOpen && previewSession == null && !scannerOpen && scanCandidate == null && favoriteCard == null)
    val homeArtworkActive = selectedSection == "Accueil" && !menuOpen && !scannerOpen && scanCandidate == null && previewSession == null
    val homeHeaderProgress = rememberHomeHeaderProgress(homeArtworkActive)
    val headerReflection = rememberHeaderReflectionProgress(homeArtworkActive)
    val homeFilmQuote = rememberHomeFilmQuote(homeArtworkActive)
    val updateScope = rememberCoroutineScope()
    val appUpdates = remember(appContext, database) { AppUpdates(appContext, database) }
    UpdateNotificationPermissionRequest()
    val latestForegroundRequest by rememberUpdatedState(foregroundRequest)
    LaunchedEffect(appUpdates) {
        snapshotFlow { latestForegroundRequest }.collect { appUpdates.check() }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Always refresh the catalogue bundled with the installed APK.
            // An older installed database can contain card records without image URLs,
            // and the remote catalogue may be unreachable for a private repository.
            CatalogSyncWorker.importBundled(appContext)
            runCatching { CatalogSyncWorker.syncRemote(appContext) }
        }
        refresh()
    }

    val deckWorkspace = selectedSection == "Mes listes" && (creatingDeck || editingDeck != null)
    Box(Modifier.fillMaxSize().background(Color(0xFF030A17)).safeDrawingPadding().imePadding()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF02090F)))
        Column(
            Modifier.fillMaxSize().padding(bottom = if (deckWorkspace) 0.dp else 82.dp).then(
                if (selectedSection in listOf("Collection", "Extensions", "Mes listes", "Compléter mon set", "Échanges", "Recherche", "Historique", "Hauts faits", "Tournois & Méta"))
                    Modifier else Modifier.verticalScroll(rememberScrollState())
            ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!deckWorkspace && appUpdates.release != null && selectedSection != "Mises à jour") {
                OutlinedButton(onClick = { navigation.navigateTo("Mises à jour") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mise à jour ${appUpdates.release?.version} disponible")
                }
            }
            if (selectedSection == "Accueil") {
            Box(Modifier.fillMaxWidth()) {
                HomeHeaderArtwork(homeHeaderProgress, headerReflection,
                    Modifier.fillMaxWidth().aspectRatio(475f / 185f))
                IconButton(onClick = { menuOpen = true },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) {
                    Icon(Icons.Default.Menu, "Ouvrir le menu", tint = Color.Transparent)
                }
            }
            HomeFilmQuoteCard(homeFilmQuote)
            }
            if (selectedSection != "Accueil") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (backDispatcher != null) backDispatcher.onBackPressed() else navigation.back()
                    }) { Icon(Icons.Default.ArrowBack, "Retour à l’écran précédent") }
                    if (selectedSection !in primaryDestinations) OutlinedButton(
                        onClick = { navigation.navigateTo("Accueil") },
                        modifier = Modifier.heightIn(min = 48.dp).padding(end = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFD47D).copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFD47D).copy(alpha = 0.09f),
                            contentColor = Color(0xFFFFD47D)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = if (compactHeader) "Retour à l’accueil" else null, modifier = Modifier.size(18.dp))
                        if (!compactHeader) {
                            Spacer(Modifier.width(6.dp))
                            Text("Accueil", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(when (selectedSection) {
                        "Collection" -> "Ma collection"
                        "Recherche" -> "Recherche globale"
                        "Ajouter" -> "Ajout manuel"
                        "Sauvegardes" -> "Sauvegardes"
                        "Mes listes" -> if (deckWorkspace) "Construire un deck" else "Mes listes"
                        "Hauts faits" -> "Badges"
                        else -> selectedSection
                    },
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (!deckWorkspace) IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Menu, "Ouvrir le menu", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (menuOpen) {
                CollectionMenu(onDismiss = { menuOpen = false }, onNavigate = { section ->
                    if (section == "Scanner") openScanner() else navigation.navigateTo(section)
                    menuOpen = false
                })
            }
            if (selectedSection == "Accueil") {
                OutlinedCard(onClick = { navigation.navigateTo("Recherche") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Carte, deck, extension…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!internetAvailable) OutlinedButton(onClick = { navigation.navigateTo("Hors connexion") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hors connexion · données disponibles sur ce téléphone ›")
                }
                if (deckContext.packageName == "fr.swucollection.app.test") {
                    OutlinedButton(onClick = { navigation.navigateTo("Communauté") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mon compte · Communauté · Messages")
                    }
                }
                HomeNavigationCard("◈", "SCANNER", "Scanner vos cartes", reflection = borderTilt, onClick = {
                    openScanner()
                })
                HomeNavigationCard("▤", "EXTENSIONS", "Découvrir toutes les extensions", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Extensions")
                })
                HomeNavigationCard("✦", "MA COLLECTION", "Consulter et gérer votre collection", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Collection")
                })
                HomeNavigationCard("★", "MES DECKS", "Decks et cartes favorites", reflection = borderTilt, onClick = {
                    deckLibraryVisible = true
                    navigation.navigateTo("Mes listes")
                })
                HomeNavigationCard("🏆", "TOURNOIS & MÉTA", "Résultats, méta et decks de tournoi", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Tournois & Méta")
                })
                HomeNavigationCard("⇄", "ÉCHANGES", "Comparer et échanger vos cartes", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Échanges")
                })
                HomeNavigationCard("✦", "BADGES", "Hauts faits, histoires et sagas", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Hauts faits")
                })
                HomeNavigationCard("▥", "STATISTIQUES", "Suivre votre collection", reflection = borderTilt, onClick = {
                    navigation.navigateTo("Statistiques")
                })
                ResumeCollectionPanel(database, cards, catalogBySet, sets, cardDetails, collectionQuotes, scanBatch,
                    onScan = { navigation.navigateTo("Scanner"); scannerOpen = false },
                    onSet = { navigation.navigateTo("Compléter mon set", it) })
                PinnedDecksPanel(pinnedDecks.mapNotNull { id -> savedDecks.firstOrNull { it.id == id } }, cards, allCatalog, cardDetails, onOpen = ::openSavedDeck)
                Spacer(Modifier.height(10.dp))
                Text("$total exemplaires • ${cards.map { it.setCode to it.number }.distinct().size} cartes différentes",
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (selectedSection == "Mes listes") {
                if (importingDeck) DeckImportDialog(catalog = allCatalog, details = cardDetails, onDismiss = { importingDeck = false }, onImport = { title, imported ->
                    editingDeck = DeckStore.SavedDeck(java.util.UUID.randomUUID().toString(), title, "Premier", imported, System.currentTimeMillis())
                    importingDeck = false
                })
                if (proxyBuilderOpen) {
                    ProxyBuilderPanel(allCatalog, cards, cardDetails, onBack = { proxyBuilderOpen = false })
                } else if (creatingDeck || editingDeck != null) {
                    DeckBuilderPanel(
                        catalog = allCatalog, owned = cards,
                        initial = editingDeck?.deck ?: PortableDeck("SOR-001", "SOR-024", emptyList()),
                        initialName = editingDeck?.name ?: "Mon deck", newDeck = creatingDeck && editingDeck == null, draftKey = editingDeck?.id ?: creatingDraftKey, details = cardDetails, rules = legalitySnapshot.rules,
                        startScanning = startDeckScan,
                        onScanStarted = { startDeckScan = false },
                        onScanCollection = { openScanner() },
                        seedCard = allCatalog.firstOrNull { DeckConstruction.reference(it) == creatingSeed },
                        onCancel = { editingDeck = null; creatingDeck = false },
                        onSave = { title, deck, batchId, entries ->
                            deckStore.saveWithScans(title, editingDeck?.format ?: "Premier", deck, editingDeck?.id ?: java.util.UUID.randomUUID().toString(), batchId, entries, cardDetails)
                            refresh()
                            savedDecks = deckStore.all()
                            editingDeck = null
                            creatingDeck = false
                            deckMessage = "Deck enregistré"
                        },
                        onPreview = { card, visible -> openPreview(card, visible.map { CardPreviewEntry(it) }) }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(deckLibraryVisible, onClick = { deckLibraryVisible = true }, label = { Text("Mes decks") })
                        FilterChip(!deckLibraryVisible, onClick = { deckLibraryVisible = false }, label = { Text("Sélections") })
                        OutlinedButton(onClick = { proxyBuilderOpen = true }) { Text("Proxies") }
                    }
                    deckMessage?.let { Text(it, color = Color(0xFFFFD47D)) }
                    if (deckLibraryVisible) DeckLibraryPanel(savedDecks, Modifier.weight(1f), catalog = allCatalog, owned = cards, details = cardDetails,
                        onCreate = { beginNewDeck() },
                        onResumeDraft = { key -> creatingSeed = null; creatingDraftKey = key; creatingDeck = true },
                        onImport = { importingDeck = true },
                        onOpen = ::openSavedDeck, pinned = pinnedDecks.toSet(),
                        onPin = { saved ->
                            runCatching { deckPins.toggle(saved.id) }.onSuccess { pinnedDecks = it }
                                .onFailure { deckMessage = it.message ?: "Épinglage impossible." }
                        }, onPrepare = { preparingDeckId = it.id }, onExport = { exportingDeckId = it.id },
                        onDuplicate = { saved ->
                            runCatching {
                                deckStore.save((saved.name.take(110) + " — copie"), saved.format, saved.deck)
                                savedDecks = deckStore.all()
                            }.onFailure { deckMessage = "Impossible de dupliquer ce deck." }
                        },
                        onDelete = { saved ->
                            runCatching {
                                check(deckStore.delete(saved.id))
                                savedDecks = deckStore.all()
                            }.onFailure { deckMessage = "Impossible de supprimer ce deck." }
                        })
                }
                savedDecks.firstOrNull { it.id == exportingDeckId }?.let { saved ->
                    DeckExportDialog(saved.deck, saved.name, allCatalog, cardDetails, owned = cards, onDismiss = { exportingDeckId = null })
                }
                if (!creatingDeck && editingDeck == null && !deckLibraryVisible) {
                CardListsPanel(personalLists, selectedSet, Modifier.weight(1f),
                    onOpen = { if (it == null) navigation.upToLists() else navigation.navigateTo("Mes listes", it) },
                    onCreate = { val id = database.createCardList(it); refresh(); navigation.navigateTo("Mes listes", id) },
                    onRename = { id, title -> database.renameCardList(id, title); refresh() },
                    onDelete = { database.deleteCardList(it); refresh(); navigation.back() },
                    onQuantity = { id, card, quantity -> database.putListCard(id, card, quantity); refresh() },
                    onPreview = { card, visible -> openPreview(card, visible.map { CardPreviewEntry(it) }) },
                    owned = cards, catalog = allCatalog, details = cardDetails, rules = legalitySnapshot.rules,
                    onFormat = { id, format -> database.setListFormat(id, format); refresh() })
                }
            }
            if (selectedSection == "Tournois & Méta") {
                CompetitiveHub(allCatalog, cards, cardDetails, legalitySnapshot.rules, savedDecks, Modifier.weight(1f),
                    onSave = { title, deck ->
                        val id = CompetitiveDecks.saveCopy(deckStore, title, deck, allCatalog)
                        savedDecks = deckStore.all()
                        refresh()
                        id
                    }, onOpen = { id -> savedDecks.firstOrNull { it.id == id }?.let { saved ->
                        deckLibraryVisible = true
                        openSavedDeck(saved)
                    } }, onPreview = { card, visible -> openPreview(card, visible.map { CardPreviewEntry(it) }) })
            }
            if (selectedSection == "À propos") {
                LegalInformationPanel()
            }
            if (selectedSection == "Compléter mon set") {
                CompleteSetPanel(sets, catalogBySet, cards, collectionQuotes, Modifier.weight(1f),
                    onPreview = { card, visible -> openPreview(card, visible.map { CardPreviewEntry(it) }) },
                    initialSetCode = selectedSet, database = database, onGoalChanged = { refresh() }, details = cardDetails)
            }
            if (selectedSection == "Échanges") {
                ExchangePanel(sets, catalogBySet, cards, Modifier.weight(1f), database, cardDetails)
            }
            if (selectedSection == "Historique") {
                CollectionHistoryPanel(database, { refresh() }, Modifier.weight(1f))
            }
            if (selectedSection == "Statistiques") {
                Text("Statistiques de collection", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                CollectionValuePanel(cards, collectionQuotes, sets)
                val uniqueOwned = cards.filter { it.quantity > 0 }
                    .map { it.setCode.uppercase() to it.number }.distinct().size
                val duplicateCount = cards.sumOf { (it.quantity - 1).coerceAtLeast(0) }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$total exemplaire(s)", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("$uniqueOwned référence(s) distincte(s)")
                        Text("$duplicateCount exemplaire(s) supplémentaire(s) par variante")
                        Text("${sets.count { catalogSet -> cards.any { it.setCode.equals(catalogSet.code, true) } }} extension(s) commencée(s)")
                    }
                }
                Text("Cartes différentes possédées par extension", fontWeight = FontWeight.Bold)
                Text("Une référence compte une fois, quelle que soit sa variante. Pour l’objectif classique ×1 ou playset, ouvre Compléter mon set.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sets.forEach { catalogSet ->
                    val owned = cards.filter { it.quantity > 0 &&
                        it.setCode.equals(catalogSet.code, true) }
                        .map { it.number }.distinct().size
                    val count = catalogSet.cardCount
                    val progress = if (count > 0)
                        (owned.toFloat() / count.toFloat()).coerceIn(0f, 1f) else 0f
                    Card(onClick = {
                        navigation.navigateTo("Extensions", catalogSet.code)
                    }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(catalogSet.name, fontWeight = FontWeight.SemiBold)
                            Text("$owned / $count références du catalogue • ${(progress * 100).toInt()} %",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LinearProgressIndicator(progress = { progress },
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (selectedSection == "Recherche") {
                GlobalSearchPanel(catalogSearch, { navigation.updateCatalogSearch(it) }, allCatalog, sets, cards, savedDecks, cardDetails,
                    searchFacets, { searchFacets = it }, Modifier.fillMaxWidth().weight(1f), legality = premierLegality,
                    onCard = { card, visible -> openPreview(card, visible.map { CardPreviewEntry(it) }) },
                    onDeck = ::openSavedDeck, onSet = { navigation.navigateTo("Extensions", it) })
            }
            if (selectedSection == "Communauté" && deckContext.packageName == "fr.swucollection.app.test") {
                CommunityPanel(savedDecks, badgeProgress.filter { it.earned }, badgeBook?.definitions.orEmpty(), allCatalog,
                    onImportDeck = { title, imported ->
                        editingDeck = DeckStore.SavedDeck(java.util.UUID.randomUUID().toString(), title, "Premier", imported, System.currentTimeMillis())
                        creatingDeck = false
                        navigation.navigateTo("Mes listes")
                    })
            }
            if (selectedSection == "Hauts faits") BadgesPanel(badgeProgress, allCatalog,
                initialSelection = badgeFocus, onSelectionConsumed = { badgeFocus = null }, onCard = { card ->
                    openPreview(card, allCatalog.map { CardPreviewEntry(it, null) })
                })
            if (selectedSection == "Hors connexion") OfflineImagesPanel(allCatalog, sets, savedDecks, internetAvailable)
            if (selectedSection == "Paramètres") {
                SettingsOverview(onNavigate = { navigation.navigateTo(it) }, updateAvailable = appUpdates.release?.version)
            }
            if (selectedSection == "Apparence") {
                BorderReflectionSettings(reflectionsEnabled) { enabled ->
                    reflectionsEnabled = enabled
                    reflectionPreferences.edit().putBoolean(REFLECTION_ENABLED, enabled).apply()
                }
            }
            if (selectedSection == "Sauvegardes") {
                PortableBackupPanel(database, onRestored = { refresh() })
                var advancedBackups by remember { mutableStateOf(false) }
                TextButton(onClick = { advancedBackups = !advancedBackups }) {
                    Text(if (advancedBackups) "Masquer les options secondaires" else "Export CSV et récupération locale  ▾")
                }
                if (advancedBackups) {
                    Text("Le CSV sert à consulter tes cartes dans un tableur. Pour changer de téléphone, utilise la sauvegarde complète ci-dessus.", fontSize = 12.sp)
                    OutlinedButton(onClick = { exportLauncher.launch("SWU-Collection.csv") },
                        modifier = Modifier.fillMaxWidth(), enabled = cards.isNotEmpty()) { Text("Exporter pour tableur (CSV)") }
                    UpdateSafetyPanel(database, onRestored = {
                        leaderMode = displayPrefs.getString("mode", "flip") ?: "flip"
                        refresh()
                    })
                }
            }
            if (selectedSection == "Mises à jour") {
                AppUpdatePanel(appUpdates,
                    onCheck = { updateScope.launch { appUpdates.check() } },
                    onInstall = { updateScope.launch {
                        if (appUpdates.readyToInstall) appUpdates.install()
                        else { appUpdates.download(); if (appUpdates.readyToInstall) appUpdates.install() }
                    } })
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Catalogue des cartes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Actualiser les cartes et les extensions disponibles.", fontSize = 12.sp)
                        Button(onClick = {
                            catalogSyncMessage = "Mise à jour du catalogue en cours…"
                            onSyncCatalog { success ->
                                refresh()
                                catalogSyncMessage = if (success) "Catalogue mis à jour." else "Mise à jour impossible. Vérifie ta connexion."
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Actualiser le catalogue") }
                        catalogSyncMessage?.let { Text(it) }
                    }
                }
                LegalitySettingsPanel()
            }
            if (selectedSection == "Scanner" && !scannerOpen) {
                if (scanBatch.entries.isNotEmpty()) {
                    Text("Vérifier le lot scanné", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    ScanBatchPanel(scanBatch, committed = database.isScanBatchCommitted(scanBatch.id), onCommit = {
                        database.commitScanBatch(scanBatch.id, scanBatch.entries, cardDetails)
                        try { scanBatch.clear() } finally { refresh() }
                    })
                } else {
                    Text("Aucun lot en attente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                batchMessage?.let { Text(it) }
                Button(onClick = { openScanner() }, modifier = Modifier.fillMaxWidth()) { Text("Reprendre le scan") }
                OutlinedButton(onClick = { navigation.back() }, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
            }
            if (selectedSection == "Extensions") {
                if (selectedSet == null) {
                    Text("Toutes les extensions du catalogue", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Plus récentes en premier", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sets.isEmpty()) {
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Catalogue français prêt à télécharger", fontWeight = FontWeight.Bold)
                                Text("Télécharge le catalogue depuis les mises à jour pour découvrir les extensions et leurs cartes.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { navigation.navigateTo("Mises à jour") }) { Text("Ouvrir les mises à jour") }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sets, key = { it.code }) { catalogSet ->
                                val owned = cards.filter { it.setCode.equals(catalogSet.code, true) }
                                    .map { it.number }.distinct().size
                                Card(onClick = { navigation.navigateTo("Extensions", catalogSet.code) }) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(catalogSet.name, fontWeight = FontWeight.SemiBold)
                                        Text(CatalogChronology.releaseLabel(catalogSet.releaseDate),
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(catalogSet.code + " • " + owned + " / " + catalogSet.cardCount + " références possédées, variantes confondues",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp)
                                        val progress = if (catalogSet.cardCount > 0)
                                            owned.toFloat() / catalogSet.cardCount.toFloat() else 0f
                                        LinearProgressIndicator(progress = { progress },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val currentSet = sets.firstOrNull { it.code == selectedSet }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { navigation.upToExtensions() }) { Text("← Extensions") }
                        Text(currentSet?.name ?: selectedSet.orEmpty(),
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    }
                    OutlinedButton(onClick = { navigation.navigateTo("Compléter mon set", selectedSet) },
                        modifier = Modifier.fillMaxWidth()) { Text("Compléter cette extension") }
                    Text("Catalogue complet · possession toutes variantes confondues", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val catalogCards = catalogBySet[selectedSet!!].orEmpty()
                    val ownedNumbers = cards.filter { it.setCode.equals(selectedSet, true) }.map { it.number }.toSet()
                    val filteredCatalogCards = catalogCards.filter {
                        (it.name.contains(catalogSearch, true) || it.number.contains(catalogSearch, true)) &&
                        (ownershipFilter == "Toutes" ||
                         (ownershipFilter == "Possédées" && it.number in ownedNumbers) ||
                         (ownershipFilter == "Manquantes" && it.number !in ownedNumbers))
                    }
                    OutlinedTextField(
                        value = catalogSearch,
                        onValueChange = { navigation.updateCatalogSearch(it) },
                        label = { Text("Rechercher une carte par nom ou numéro") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Toutes", "Possédées", "Manquantes").forEach { option ->
                            FilterChip(selected = ownershipFilter == option, onClick = { ownershipFilter = option },
                                label = { Text(option, fontSize = 11.sp) })
                        }
                    }
                    Text("${filteredCatalogCards.size} carte(s) affichée(s) sur ${catalogCards.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp)
                    if (catalogCards.isEmpty()) {
                        Text("Aucune carte importée pour cette extension.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filteredCatalogCards, key = { it.setCode + it.number }) { catalogCard ->
                                val owned = catalogCard.number in ownedNumbers
                                Card(onClick = { openPreview(catalogCard, filteredCatalogCards.map { CardPreviewEntry(it) }) }) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CardArtwork(catalogCard, Modifier.width(60.dp).height(84.dp), grayscale = !owned)
                                        Column(Modifier.weight(1f)) {
                                            Text(catalogCard.name, fontWeight = FontWeight.Medium)
                                            CardLegalityBadge(premierLegality.status(catalogCard))
                                            Text(catalogCard.setCode + " • " + catalogCard.number + " • " + catalogCard.rarity,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                            Text(if (owned) "Possédée" else "Manquante",
                                                color = if (owned) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp)
                                            TextButton(onClick = {
                                                val print=ScanVariants.detect(catalogCard)
                                                if(print.variant!=null) {
                                                    database.addOrIncrement(catalogCard.name,catalogCard.setCode,catalogCard.number,print.variant)
                                                    refresh()
                                                } else openPreview(catalogCard,filteredCatalogCards.map { CardPreviewEntry(it) })
                                            }) { Text("+ Ajouter") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selectedSection == "Collection" || selectedSection == "Ajouter") {
                if (selectedSection != "Collection") {
                if (selectedSection == "Ajouter") Text("GESTION DE COLLECTION", color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ajouter une carte", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(name, { name = it }, label = { Text("Nom de la carte") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(set, { set = it }, label = { Text("Extension (ex. SOR)") },
                                modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                            OutlinedTextField(number, { number = it }, label = { Text("N°") },
                                modifier = Modifier.width(90.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        CardLanguageChoices(addingLanguage, { addingLanguage = it })
                        Text("Version", fontWeight = FontWeight.Medium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ScanVariants.options.forEach { option ->
                                FilterChip(selected = variant == option, onClick = { variant = option },
                                    label = { Text(option, fontSize = 11.sp) })
                            }
                        }
                        Button(onClick = {
                            if (name.isNotBlank() && set.isNotBlank() && number.isNotBlank()) {
                                database.addOrIncrement(name, set, number, variant, addingLanguage)
                                name = ""; number = ""; refresh()
                            }
                        }, enabled = name.isNotBlank() && set.isNotBlank() && number.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Ajouter à ma collection")
                        }
                    }
                }
                }
                if (selectedSection == "Collection") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { openScanner() }, modifier = Modifier.widthIn(min = 112.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(5.dp)); Text("Scanner", maxLines = 1)
                    }
                    OutlinedButton(onClick = { collectionFiltersOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Filtres")
                    }
                    IconButton(onClick = { navigation.navigateTo("Ajouter") }) {
                        Icon(Icons.Default.Add, "Ajouter une carte manuellement")
                    }
                }
                OutlinedTextField(value = search, onValueChange = { search = it },
                    label = { Text("Nom, extension ou numéro") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (collectionFiltersOpen) {
                    AlertDialog(onDismissRequest = { collectionFiltersOpen = false },
                        title = { Text("Filtres et affichage") },
                        text = {
                            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    search = ""; collectionSetFilter = "Toutes"; collectionLanguage = "Toutes"; collectionView = "Visuel"
                    savedCollectionSort = CollectionPriceSort.CATALOG.name; collectionScope = "Possédées"
                    ownershipFilter = "Toutes"
                }) { Text("Réinitialiser les filtres") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Possédées", "Doublons").forEach { option ->
                        FilterChip(selected = if (option == "Doublons") collectionScope == option else collectionScope != "Doublons",
                            onClick = { collectionScope = option },
                            label = { Text(option, fontSize = 12.sp) })
                    }
                }
                Text("Doublons : au moins 2 exemplaires d’une même carte et variante. Ils ne sont pas forcément disponibles à l’échange.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Regrouper les versions", modifier = Modifier.weight(1f))
                    Switch(groupVersions, { groupVersionsValue = it.toString() })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Visuel", "Liste").forEach { option ->
                        FilterChip(selected = collectionView == option,
                            onClick = { collectionView = option },
                            label = { Text(option, fontSize = 12.sp) })
                    }
                }
                var priceSortMenuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { priceSortMenuOpen = true }) {
                        Text("Tri : " + collectionSort.label)
                    }
                    DropdownMenu(expanded = priceSortMenuOpen,
                        onDismissRequest = { priceSortMenuOpen = false }) {
                        CollectionPriceSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                savedCollectionSort = option.name
                                priceSortMenuOpen = false
                            })
                        }
                    }
                }
                Text("Cote à l’unité · Cardmarket · fourchettes triées sur le minimum. Sans cote en fin de liste.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var collectionLanguageMenuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { collectionLanguageMenuOpen = true }) {
                        Text("Langue : " + if (collectionLanguage == "Toutes") "Toutes" else CardLanguages.label(collectionLanguage))
                    }
                    DropdownMenu(expanded = collectionLanguageMenuOpen, onDismissRequest = { collectionLanguageMenuOpen = false }) {
                        (listOf("Toutes") + CardLanguages.supported + CardLanguages.UNKNOWN).forEach { code ->
                            DropdownMenuItem(text = { Text(if (code == "Toutes") "Toutes" else CardLanguages.label(code)) },
                                onClick = { collectionLanguage = code; collectionLanguageMenuOpen = false })
                        }
                    }
                }
                var collectionSetMenuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { collectionSetMenuOpen = true }) {
                        Text("Extension : " + collectionSetFilter)
                    }
                    DropdownMenu(expanded = collectionSetMenuOpen,
                        onDismissRequest = { collectionSetMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Toutes les extensions") },
                            onClick = { collectionSetFilter = "Toutes"; collectionSetMenuOpen = false })
                        sets.forEach { catalogSet ->
                            DropdownMenuItem(text = { Text(catalogSet.code + " — " + catalogSet.name) },
                                onClick = { collectionSetFilter = catalogSet.code; collectionSetMenuOpen = false })
                        }
                    }
                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { collectionFiltersOpen = false }) { Text("Voir les cartes") } })
                }
                if (groupVersions) InputChip(true, { groupVersionsValue = "false" }, label = { Text("Versions regroupées ×") })
                ActiveCollectionFilters(collectionSetFilter, collectionScope, collectionSort, search,
                    onSet = { collectionSetFilter = "Toutes" }, onScope = { collectionScope = "Possédées" },
                    onSort = { savedCollectionSort = CollectionPriceSort.CATALOG.name }, onSearch = { search = "" })
                if (collectionLanguage != "Toutes") InputChip(true, { collectionLanguage = "Toutes" }, label = { Text(CardLanguages.label(collectionLanguage) + " ×") })
                val selectedOwned = sortCollectionByPrice(visibleCards.filter {
                    (collectionSetFilter == "Toutes" || it.setCode == collectionSetFilter) &&
                        (collectionLanguage == "Toutes" || it.language == collectionLanguage) &&
                        (collectionScope != "Doublons" || (copiesPerPrint[MarketPricing.key(it.setCode,it.number) + ":" + it.variant] ?: 0) > 1)
                }, collectionSort) {
                    collectionQuotes[collectionPriceKey(it.setCode, it.number, it.variant)]?.amount
                }
                Text("${selectedOwned.map { MarketPricing.key(it.setCode,it.number) + ":" + it.variant }.distinct().size} références et variantes possédées · ${selectedOwned.sumOf { it.quantity }} exemplaires", fontSize = 12.sp)
                if (collectionScope == "Doublons") Text("Filtre Doublons actif", color = MaterialTheme.colorScheme.primary)
                if (selectedOwned.isEmpty()) {
                    Text(if (cards.isEmpty()) "Ta collection est vide. Scanne ta première carte pour commencer." else "Aucune carte possédée ne correspond aux filtres.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cards.isNotEmpty()) TextButton(onClick = { search = ""; collectionSetFilter = "Toutes"; collectionScope = "Possédées"; collectionLanguage = "Toutes"; savedCollectionSort = CollectionPriceSort.CATALOG.name }) { Text("Effacer les filtres") }
                } else if (groupVersions) {
                    val groups = groupOwnedVersions(selectedOwned, allCatalog, cardDetails)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.first().id }) { rows ->
                            GroupedOwnedCard(rows, allCatalog, collectionQuotes,
                                onPreview = { owned, card -> openPreview(card, rows.mapNotNull { row ->
                                    catalogByKey[MarketPricing.key(row.setCode,row.number)]?.let { CardPreviewEntry(it,row.variant,row.language) }
                                }, owned.variant, owned.language) },
                                onQuantity = { id, delta -> database.changeQuantity(id, delta); refresh() },
                                onLanguage = { editingOwnedLanguage = it; languageError = null })
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedOwned, key = { it.id }) { card ->
                            val catalogMatch = catalogBySet[card.setCode].orEmpty()
                                .firstOrNull { it.number == card.number }
                            Card(onClick = {
                                catalogMatch?.let { matched ->
                                    val visible = selectedOwned.mapNotNull { owned ->
                                        catalogBySet[owned.setCode].orEmpty()
                                            .firstOrNull { it.number == owned.number }
                                            ?.let { CardPreviewEntry(it, owned.variant, owned.language) }
                                    }
                                    openPreview(matched, visible, card.variant, card.language)
                                }
                            }) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (collectionView == "Visuel") {
                                            if (catalogMatch != null) CardArtwork(catalogMatch, Modifier.width(55.dp).height(78.dp), language = card.language, variant = card.variant)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(card.name, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            CardLegalityBadge(premierLegality.status(card))
                                            Text(card.setCode + " • N° " + card.number + " • " + card.variant,
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            CollectionPriceLabel(collectionQuotes[collectionPriceKey(
                                                card.setCode, card.number, card.variant)], collectionQuotes.isNotEmpty())
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { editingOwnedLanguage = card; languageError = null },
                                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(CardLanguages.label(card.language)) }
                                        TextButton(onClick = { database.changeQuantity(card.id, -1); refresh() },
                                            modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
                                        Text(card.quantity.toString(), modifier = Modifier.widthIn(min = 24.dp),
                                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = { database.changeQuantity(card.id, 1); refresh() },
                                            modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        if (!deckWorkspace) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth()) {
            CollectionBottomBar(selectedSection) { section -> navigation.navigateTo(section) }
        }
    }
    editingOwnedLanguage?.let { owned ->
        var chosen by remember(owned.id) { mutableStateOf(owned.language) }
        var reassignedCopies by remember(owned.id) { mutableIntStateOf(owned.quantity) }
        AlertDialog(onDismissRequest = { editingOwnedLanguage = null }, title = { Text("Langue de ces exemplaires") },
            text = { Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${owned.quantity} × ${owned.name} · ${owned.variant}")
                CardLanguageChoices(chosen,{ chosen = it; languageError = null })
                if (owned.quantity > 1) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(enabled = reassignedCopies > 1,onClick = { reassignedCopies-- }) { Text("−") }
                    Text("$reassignedCopies sur ${owned.quantity} exemplaires",Modifier.weight(1f),textAlign=TextAlign.Center)
                    TextButton(enabled = reassignedCopies < owned.quantity,onClick = { reassignedCopies++ }) { Text("+") }
                }
                Text("Les quantités seront regroupées uniquement avec la même langue et la même finition.",style=MaterialTheme.typography.bodySmall)
                languageError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = chosen in CardLanguages.supported && chosen != owned.language, onClick = {
                runCatching { database.changeLanguage(owned.id,chosen,reassignedCopies) }
                    .onSuccess { editingOwnedLanguage = null; refresh() }
                    .onFailure { languageError = it.message }
            }) { Text("Valider") } },
            dismissButton = { TextButton(onClick = { editingOwnedLanguage = null }) { Text("Annuler") } })
    }
    previewSession?.let { session ->
        val card = CardLanguageArtwork.localized(deckContext,previewPrint ?: session.current.card,previewLanguage,previewVariant)
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        val swipeCardModifier = Modifier.pointerInput(session.index, swipeThreshold) {
            var distance = 0f
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onDragCancel = { distance = 0f },
                onDragEnd = {
                    if (distance <= -swipeThreshold) movePreview(1)
                    else if (distance >= swipeThreshold) movePreview(-1)
                    distance = 0f
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    distance += amount
                }
            )
        }
        val artworkHeight = (LocalConfiguration.current.screenHeightDp.dp * .42f).coerceIn(180.dp, 350.dp)
        Dialog(onDismissRequest = { previewSession = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Card(modifier = Modifier.safeDrawingPadding().padding(12.dp).widthIn(max = 720.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        IconButton(onClick = { movePreview(-1) }, enabled = session.hasPrevious) {
                            Icon(Icons.Default.ChevronLeft, "Carte précédente")
                        }
                        Text("${session.index + 1} / ${session.entries.size}",
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp)
                        IconButton(onClick = { movePreview(1) }, enabled = session.hasNext) {
                            Icon(Icons.Default.ChevronRight, "Carte suivante")
                        }
                        IconButton(onClick = { previewSession = null }) {
                            Icon(Icons.Default.Close, "Fermer la carte")
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val hasReverse = card.type.equals("Leader", ignoreCase = true) &&
                            CardLanguageArtwork.displayFace(deckContext,card,previewLanguage,previewVariant,true).first.isNotBlank()
                        if (hasReverse) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = leaderMode == "flip", onClick = {
                                    leaderMode = "flip"
                                    displayPrefs.edit().putString("mode", "flip").apply()
                                }, label = { Text("Retourner") })
                                FilterChip(selected = leaderMode == "stack", onClick = {
                                    leaderMode = "stack"
                                    displayPrefs.edit().putString("mode", "stack").apply()
                                }, label = { Text("Deux faces") })
                            }
                        }
                        if (hasReverse && leaderMode == "stack") {
                            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Recto")
                                CardArtwork(card, Modifier.fillMaxWidth().height(280.dp).then(swipeCardModifier), variant = previewVariant)
                                Text("Verso")
                                CardArtwork(card, Modifier.fillMaxWidth().height(280.dp).then(swipeCardModifier), variant = previewVariant, reverse = true)
                            }
                        } else if (hasReverse) {
                            Crossfade(targetState = showReverse, label = "Retourner le leader") { reverse ->
                                CardArtwork(card, Modifier.fillMaxWidth().height(artworkHeight).then(swipeCardModifier).clickable { showReverse = !showReverse }, variant = previewVariant, reverse = reverse)
                            }
                            Text("Toucher la carte pour la retourner", fontSize = 12.sp)
                        } else {
                            CardArtwork(card, Modifier.fillMaxWidth().height(artworkHeight).then(swipeCardModifier), variant = previewVariant)
                        }
                        Text(card.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val familyStock = remember(card, cards, allCatalog, cardDetails) { DeckCompanion.stock(card, cards, allCatalog, cardDetails) }
                        Text("$familyStock exemplaire(s) possédé(s), versions reconnues comprises", color = ListPalette.Gold)
                        CardDeckUsagePanel(card, savedDecks, allCatalog, cardDetails, enabled = !deckWorkspace,
                            onAdd = { addingToDeck = card }, onOpen = ::openSavedDeck)
                        RelatedPrintsPanel(card, allCatalog, cards, cardDetails, previewVariant, previewLanguage) { related, finish, language ->
                            previewPrint = related
                            previewVariant = finish
                            previewLanguage = language
                            showReverse = false
                        }
                        val inLists = personalLists.count { list -> list.entries.any {
                            it.card.setCode == card.setCode && it.card.number == card.number
                        } }
                        OutlinedButton(onClick = { favoriteCard = card }) {
                            Text(if (inLists > 0) "★ Dans $inLists liste(s) · Ajouter à une liste" else "☆ Ajouter à une liste")
                        }
                        CardLegalityBadge(premierLegality.status(card))
                        Text("${card.setCode} • ${card.number} • ${card.rarity}")
                        CardLanguageChoices(previewLanguage, { previewLanguage = it; showReverse = false })
                        if (previewLanguage == CardLanguages.UNKNOWN) Text("Visuel du catalogue : précise la langue pour afficher ta version.",style=MaterialTheme.typography.bodySmall)
                        if (card.imageUrl.isBlank() && previewLanguage in CardLanguages.supported) Text("Le visuel ${CardLanguages.label(previewLanguage).lowercase(java.util.Locale.FRANCE)} n’est pas publié pour cette référence. Un visuel disponible est affiché avec sa langue.", color = ListPalette.Secondary)
                        Text("Ma collection — choisir une variante", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScanVariants.detect(card).choices.forEach { option ->
                                FilterChip(selected = previewVariant == option,
                                    onClick = { previewVariant = option },
                                    label = { Text(option, fontSize = 11.sp) })
                            }
                        }
                        CardmarketPricePanel(card, previewVariant)
                        val ownedVariant = cards.firstOrNull {
                            it.setCode.equals(card.setCode, true) &&
                            it.number == card.number && it.variant == previewVariant && it.language == previewLanguage
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = {
                                ownedVariant?.let { database.changeQuantity(it.id, -1); refresh() }
                            }, enabled = (ownedVariant?.quantity ?: 0) > 0) { Text("−") }
                            Text("${ownedVariant?.quantity ?: 0} exemplaire(s)",
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                database.addOrIncrement(card.name, card.setCode, card.number, previewVariant, previewLanguage)
                                refresh()
                            }, enabled = previewLanguage in CardLanguages.supported && previewVariant in ScanVariants.detect(card).choices) { Text("+") }
                        }
                        OutlinedButton(onClick = { previewSession = null }) { Text("Fermer") }
                        }
                }
            }
        }
    }
    preparingDeck?.let { saved ->
        DeckPreparationDialog(saved.name, saved.deck, cards, allCatalog, cardDetails, onDismiss = { preparingDeckId = null })
    }
    addingToDeck?.let { card ->
        AddCardToDeckDialog(card, savedDecks, onDismiss = { addingToDeck = null },
            onCreate = { beginNewDeck(card); addingToDeck = null; previewSession = null; navigation.navigateTo("Mes listes") },
            onAdd = { saved, zone ->
                runCatching {
                    val latest = deckStore.all().firstOrNull { it.id == saved.id } ?: error("Ce deck a été supprimé.")
                    val baseline = DeckInterchange.exportJson(latest.deck, latest.name)
                    check(DeckDraftStore(deckContext).read(latest.id, baseline) == null) { "Un brouillon existe pour ce deck. Ouvre-le et enregistre-le avant cet ajout." }
                    val next = DeckCompanion.add(latest.deck, card, allCatalog, cardDetails, zone)
                    deckStore.save(latest.name, latest.format, next, latest.id)
                    savedDecks = deckStore.all()
                    android.widget.Toast.makeText(deckContext, "Deck mis à jour", android.widget.Toast.LENGTH_SHORT).show()
                }.fold({ null }, { it.message ?: "Ajout impossible. Ton deck reste inchangé." })
            })
    }
    if (scannerOpen) {
        CardScannerDialog(
            onDismiss = { closeScanner() },
            paused = scanCandidate != null || scanVariantMenuOpen || scanDeckPicker,
            rescanToken = scanReadToken,
            destinationControls = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(true, onClick = {}, label = { Text("Ajouter à ma collection") })
                        FilterChip(false, onClick = { scanDeckPicker = true }, label = { Text("Choisir un deck…") })
                    }
                    ScannerModeChoices(scanMode, enabled = scanCandidate == null) { scanMode = it }
                }
            },
            inlineControls = {
                val candidate = scanCandidate
                if (candidate != null) {
                    ScannerCardConfirmation(candidate, scanLanguage, scanVariant, "Ajouter à ma collection",
                        scanVariantError, onCancel = { scanCandidate = null; scanReadToken++ },
                        onConfirm = confirm@{ language, finish ->
                            if (scanCandidate != candidate) return@confirm
                            runCatching {
                                database.addScannedCard(candidate.copy(language = language), finish, cardDetails.family(candidate))
                                refresh()
                            }.onSuccess {
                                scanLastCard = ScanBatchEntry(candidate.copy(language = language), finish, 1)
                                scanCandidate = null
                                scanReadToken++
                            }.onFailure { scanVariantError = it.message ?: "Impossible d’enregistrer cet ajout." }
                        })
                } else {
                    scanLastCard?.let { ScannerCardSummary(it.card, it.variant, if (scanMode == "Rafale") "En attente de validation" else "Ajoutée à ma collection") }
                    if (scanMode == "Rafale") TextButton(onClick = { closeScanner() }) {
                        Text("Vérifier la liste (${scanBatch.entries.sumOf { it.quantity }})")
                    }
                }
            },
            batchCount = if (scanMode == "Rafale") scanBatch.entries.sumOf { it.quantity } else null,
            controls = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scanBatch.entries.isNotEmpty()) {
                        TextButton(onClick = { closeScanner() }) { Text("Voir le lot (${scanBatch.entries.sumOf { it.quantity }})") }
                    }
                }
                Text("La variante est identifiée automatiquement à partir du numéro exact et du catalogue.", fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { scanVariantMenuOpen = true }) { Text("Finition indécidable : $batchVariant  ▾") }
                    DropdownMenu(expanded = scanVariantMenuOpen, onDismissRequest = { scanVariantMenuOpen = false }) {
                        (listOf("Automatique") + ScanVariants.options).forEach { option ->
                            DropdownMenuItem(text = { Text(if (option == "Automatique") "Demander seulement en cas de doute" else option) },
                                onClick = { batchVariant = option; scanVariantMenuOpen = false })
                        }
                    }
                }
                Text("Ce réglage sert uniquement aux références dont la finition n’est pas identifiable. Une variante déterminée par son numéro reste prioritaire.", fontSize = 11.sp)
                TextButton(onClick = { closeScanner(); navigation.navigateTo("Ajouter") }) { Text("Ajouter une carte manuellement") }
            },
            onDetected = detected@{ detectedSet, detectedNumber, raw ->
                if (scanCandidate != null || scanVariantMenuOpen) return@detected false
                if (detectedSet.isNotBlank()) set = detectedSet
                if (detectedNumber.isNotBlank()) number = detectedNumber
                val match = if (detectedSet.isNotBlank() && detectedNumber.isNotBlank())
                    (catalogBySet[detectedSet] ?: database.catalogCardsForSet(detectedSet))
                        .firstOrNull { catalogCard ->
                            catalogCard.number.trimStart('0').ifEmpty { "0" } ==
                                detectedNumber.trimStart('0').ifEmpty { "0" }
                        }
                else null
                if (match != null) {
                    name = match.name
                    val detectedLanguage = ScanRecognition.language(raw)
                    scanLanguage = detectedLanguage ?: CardLanguages.UNKNOWN
                    val identified = ScanVariants.detect(match).resolve(batchVariant)
                    if (scanMode == "Rafale") {
                        try {
                            check(!database.isScanBatchCommitted(scanBatch.id)) { "Ce lot est déjà validé. Ferme-le dans le récapitulatif." }
                            scanBatch.add(CardLanguageArtwork.localized(deckContext,match,scanLanguage,identified), identified.orEmpty())
                            scanLastCard = ScanBatchEntry(match.copy(language = scanLanguage), identified.orEmpty(), 1)
                        } catch (error: Exception) {
                            batchMessage = error.message ?: "Impossible d’enregistrer le scan."
                            scannerOpen = false
                            return@detected false
                        }
                    } else {
                        scanVariant = identified.orEmpty()
                        scanVariantError = null
                        scanCandidate = match
                    }
                }
                match != null
            }
        )
    }
    if (scanDeckPicker) AlertDialog(
        onDismissRequest = { scanDeckPicker = false },
        title = { Text("Scanner vers un deck") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Les cartes déjà scannées vers la collection y restent en attente.")
                if (creatingDeck || editingDeck != null) {
                    Button(onClick = {
                        scanDeckPicker = false; closeScanner(); startDeckScan = true
                        navigation.navigateTo("Mes listes")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Reprendre le deck en cours") }
                } else {
                    Button(onClick = {
                        scanDeckPicker = false; closeScanner()
                        beginNewDeck()
                        startDeckScan = true
                        navigation.navigateTo("Mes listes")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Créer un deck") }
                    DeckDraftStore(deckContext).newDrafts().forEach { (key, draft) ->
                        OutlinedButton(onClick = {
                            scanDeckPicker = false; closeScanner()
                            creatingSeed = null; creatingDraftKey = key; creatingDeck = true; startDeckScan = true
                            navigation.navigateTo("Mes listes")
                        }, modifier = Modifier.fillMaxWidth()) { Text("${draft.name} · brouillon") }
                    }
                    savedDecks.forEach { saved ->
                        OutlinedButton(onClick = {
                            scanDeckPicker = false; closeScanner(); startDeckScan = true
                            openSavedDeck(saved)
                        }, modifier = Modifier.fillMaxWidth()) { Text(saved.name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { scanDeckPicker = false }) { Text("Rester sur la collection") } })


    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val pendingBadges = badgeProgress.filter { it.award?.seen == false && it.badge.id !in badgeCelebrationSeen }
    if(selectedSection == "Accueil" && lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED &&
        !menuOpen && !scannerOpen && scanCandidate == null && scanBatch.entries.isEmpty() &&
        previewSession == null && favoriteCard == null && !proxyBuilderOpen && preparingDeck == null && pendingBadges.isNotEmpty()) {
        fun finishBadges() {
            val ids = pendingBadges.map { it.badge.id }
            badgeCelebrationSeen = badgeCelebrationSeen + ids
            updateScope.launch(Dispatchers.IO) { database.markBadgesSeen(ids) }
        }
        BadgeCelebration(pendingBadges,allCatalog,onContinue = { finishBadges() },onView = { id ->
            finishBadges(); badgeFocus = id; navigation.navigateTo("Hauts faits")
        })
    }

    favoriteCard?.let { card ->
        FavoriteListDialog(card, personalLists, onDismiss = { favoriteCard = null },
            onAdd = { database.putListCard(it, card); refresh(); favoriteCard = null },
            onCreate = {
                val id = database.createCardList(it)
                database.putListCard(id, card)
                refresh()
                favoriteCard = null
            })
    }
}

@Composable
private fun ReferenceArtwork(x: Int, y: Int, width: Int, height: Int,
    description: String?, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val sheet = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.collection_design_reference)
    }
    val artwork = remember(sheet, x, y, width, height) {
        Bitmap.createBitmap(sheet, x, y, width, height).asImageBitmap()
    }
    Image(bitmap = artwork, contentDescription = description, modifier = modifier,
        contentScale = ContentScale.Crop)
}

@Composable
private fun CollectionMenu(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
            Surface(Modifier.fillMaxHeight().fillMaxWidth(0.88f),
                color = Color(0xFF030B12), shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)) {
                Box(Modifier.fillMaxSize()) {
                    ReferenceArtwork(1152, 754, 350, 180, null,
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().height(175.dp))
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            ReferenceArtwork(1195, 102, 150, 80, "SWU Collection",
                                Modifier.weight(1f).height(78.dp))
                            IconButton(onClick = { onNavigate("Recherche") }) {
                                Icon(Icons.Default.Search, "Rechercher une carte", tint = Color(0xFFFFD47D))
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Fermer le menu", tint = Color(0xFFFFD47D))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        val communityEntries = if (LocalContext.current.packageName == "fr.swucollection.app.test")
                            listOf(Triple(Icons.Default.Person, "Compte et communauté", "Communauté")) else emptyList()
                        val entries = communityEntries + listOf(
                            Triple(Icons.Default.PhotoCamera, "Scanner", "Scanner"),
                            Triple(Icons.Default.CollectionsBookmark, "Compléter mon set", "Compléter mon set"),
                            Triple(Icons.Default.EmojiEvents, "Tournois & Méta", "Tournois & Méta"),
                            Triple(Icons.Default.WorkspacePremium, "Badges", "Hauts faits"),
                            Triple(Icons.Default.Info, "Statistiques", "Statistiques"),
                            Triple(Icons.Default.History, "Historique et annulation", "Historique"),
                            Triple(Icons.Default.Info, "À propos", "À propos"),
                            Triple(Icons.Default.Download, "Mises à jour", "Mises à jour"),
                            Triple(Icons.Default.Settings, "Paramètres", "Paramètres")
                        )
                        entries.forEach { (icon, label, section) ->
                            Row(Modifier.fillMaxWidth().clickable { onNavigate(section) }.padding(vertical = 17.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Icon(icon, null, tint = Color(0xFFE6C17A), modifier = Modifier.size(27.dp))
                                Text(label, color = Color(0xFFF5F1E8), fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("›", color = Color(0xFFE6C17A), fontSize = 25.sp)
                            }
                            HorizontalDivider(color = Color(0xFF66583D).copy(alpha = 0.4f))
                        }
                        Spacer(Modifier.height(150.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GalaxyBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(brush = Brush.verticalGradient(listOf(
            Color(0xFF020714), Color(0xFF0B2541), Color(0xFF030918)
        )))
        val w = size.width
        val h = size.height
        for (i in 0 until 145) {
            val x = ((i * 173 + 29) % 997) / 997f * w
            val y = ((i * 389 + 61) % 991) / 991f * h
            val r = if (i % 11 == 0) 2.2f else if (i % 3 == 0) 1.3f else 0.7f
            drawCircle(Color(0xFFC6D9F3).copy(alpha = if (i % 4 == 0) 0.75f else 0.32f),
                radius = r, center = Offset(x, y))
        }
        val center = Offset(w * 0.77f, h * 0.29f)
        drawCircle(
            brush = Brush.radialGradient(listOf(
                Color(0xFF366A92).copy(alpha = 0.55f),
                Color(0xFF122C50).copy(alpha = 0.35f),
                Color.Transparent
            ), center = center, radius = w * 0.73f),
            radius = w * 0.73f, center = center
        )
        drawCircle(
            brush = Brush.linearGradient(listOf(
                Color(0xFF47718F), Color(0xFF172E4B), Color(0xFF071322)
            ), start = Offset(w * 0.50f, h * 0.09f),
                end = Offset(w * 1.1f, h * 0.52f)),
            radius = w * 0.46f, center = Offset(w * 0.93f, h * 0.31f),
            alpha = 0.30f
        )
        val horizon = h * 0.29f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFDBA969).copy(alpha = 0.25f), Color(0xFF020815)),
                startY = horizon - h * 0.08f, endY = horizon + h * 0.08f
            ),
            topLeft = Offset(0f, horizon - h * 0.08f),
            size = Size(w, h * 0.16f)
        )
        val buildings = Path().apply {
            moveTo(0f, horizon + h * 0.04f)
            for (i in 0..50) {
                val x = i * w / 50f
                val tower = if (i % 13 == 0) h * 0.09f
                    else if (i % 7 == 0) h * 0.055f
                    else h * (0.012f + (i * 17 % 11) / 650f)
                lineTo(x, horizon - tower)
                lineTo(x + w / 100f, horizon - tower)
                lineTo(x + w / 100f, horizon + h * 0.04f)
            }
            lineTo(w, horizon + h * 0.04f)
            close()
        }
        drawPath(buildings, Color(0xFF030A14))
    }
}

@Composable
internal fun CardArtwork(card: CatalogCard?, modifier: Modifier = Modifier, language: String = card?.language ?: CardLanguages.UNKNOWN,
    variant: String? = null, reverse: Boolean = false, grayscale: Boolean = false) {
    if (card == null) { CardArtwork("",modifier,grayscale); return }
    val context = LocalContext.current
    val face = remember(card,language,variant,reverse) { CardLanguageArtwork.displayFace(context,card,language,variant,reverse) }
    Box(modifier) {
        CardArtwork(face.first, Modifier.fillMaxSize(), grayscale)
        if (face.second != null && face.second != language) {
            Surface(color = Color(0xEE071322), shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                Text("Visuel ${face.second}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal=4.dp,vertical=2.dp))
            }
        }
    }
}

@Composable
internal fun CardArtwork(url: String, modifier: Modifier = Modifier, grayscale: Boolean = false) {
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (url.startsWith("https://")) {
            AsyncImage(model = OfflineImages.source(LocalContext.current, url), contentDescription = "Visuel de la carte",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                colorFilter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
        } else {
            Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFF152C45),
                border = BorderStroke(1.dp, Color(0xFF9C7941)),
                modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("✦", color = Color(0xFFF2C66B), fontSize = 25.sp)
                }
            }
        }
    }
}

/** Display the selected left-hand logo, preserving the supplied artwork exactly. */
@Composable
private fun CollectionLogo(modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val logo = remember(resources) {
        val source = BitmapFactory.decodeResource(resources, R.drawable.collection_logo_options)
        // Original sheet is 1536 x 1024. Isolate only its left-hand emblem.
        val cropped = Bitmap.createBitmap(source, 20, 168, 484, 630)
        source.recycle()
        cropped.asImageBitmap()
    }
    Image(
        bitmap = logo,
        contentDescription = "SWU Collection",
        modifier = modifier.aspectRatio(484f / 630f),
        contentScale = ContentScale.Fit
    )
}



@Composable
private fun AppUpdatePanel(updates: AppUpdates, onCheck: () -> Unit, onInstall: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mise à jour de l’application", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Version installée : ${updates.installedVersion}", fontSize = 13.sp)
            UpdateNotificationControls()
            OutlinedButton(onClick = onCheck, enabled = !updates.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Rechercher une mise à jour")
            }
            updates.message?.let { Text(it, fontSize = 13.sp) }
            if (updates.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            updates.release?.let { release ->
                Text("Nouvelle version : ${release.version}", fontWeight = FontWeight.SemiBold)
                if (release.notes.isNotBlank()) Text(release.notes, fontSize = 13.sp)
                Text("Une sauvegarde locale sera créée avant l’installation. Pour conserver un fichier complet, ouvre Paramètres → Sauvegarder et restaurer.", fontSize = 12.sp)
                Button(onClick = onInstall, enabled = !updates.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (updates.readyToInstall) "Installer la mise à jour" else "Télécharger et installer")
                }
            }
        }
    }
}




