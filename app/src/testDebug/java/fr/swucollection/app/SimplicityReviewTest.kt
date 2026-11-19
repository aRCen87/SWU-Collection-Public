package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Disposable, offline fixtures. No real accounts, collections or remote mutations. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class SimplicityReviewTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val first = CatalogCard("SOR", "100", "Carte possédée", "Unité", "Rare", "https://example.invalid/review-a.png", language = "FR")
    private val second = first.copy(number = "101", name = "Carte manquante", imageUrl = "https://example.invalid/review-b.png")
    private val catalog get() = listOf(first, second)

    @Composable private fun Theme(content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = darkColorScheme(primary = PremiumGold, onPrimary = ListPalette.Background,
            surface = PremiumNavy, surfaceContainer = PremiumNavy, surfaceContainerLow = PremiumNavy,
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF142235),
            secondary = androidx.compose.ui.graphics.Color(0xFF75C9D6),
            secondaryContainer = androidx.compose.ui.graphics.Color(0xFF112842),
            onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF75C9D6),
            outline = PremiumGold.copy(alpha = .38f), outlineVariant = androidx.compose.ui.graphics.Color(0xFF354255),
            background = ListPalette.Background, onSurface = ListPalette.Text,
            onBackground = ListPalette.Text, onSurfaceVariant = ListPalette.Secondary)) {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                Surface(Modifier.fillMaxSize(), color = ListPalette.Background) { content() }
            }
        }
    }
    private fun render(): Bitmap {
        var rendered: Bitmap? = null
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            rendered = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(android.graphics.Canvas(it))
            }
        }
        return rendered!!
    }
    private fun capture(name: String) {
        val bitmap = render()
        File("build/visual-review", "review-$name.png").also { it.parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun seedArtwork() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ui.activity).allowHardware(false).build())
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(224, 96, 32)) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        catalog.forEach { card ->
            OfflineImages.saveBytes(ui.activity, CardLanguageArtwork.displayFace(ui.activity, card, "FR").first, bytes)
        }
        bitmap.recycle()
    }
    private fun saturation(card: CatalogCard): Int {
        val bounds = ui.onNodeWithTag("catalog_artwork_${MarketPricing.key(card.setCode, card.number)}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        val bitmap = render()
        val pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
        bitmap.recycle()
        return listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).let { it.max() - it.min() }
    }

    @Test fun dimmingChangesOnlyMissingArtworkAndDoesNotChangeTheInventory() {
        seedArtwork()
        val owned = listOf(CollectionCard(1, first.name, "SOR", "100", "Normale", 2, "FR"))
        var dim by mutableStateOf(true)
        ui.setContent { Theme { Column(Modifier.fillMaxSize().padding(12.dp)) {
            MissingCardDimmingToggle(dim) { dim = it }
            UnifiedCollectionCatalog(catalog, owned, "Toutes les cartes", "Toutes", "FR", "", "Visuel",
                CardDetails.empty(), Modifier.weight(1f), { _, _ -> }, {}, dimMissing = dim)
        } } }
        try { ui.waitUntil(20_000) { runCatching { saturation(first) > 80 }.getOrDefault(false) } }
        finally { capture("dimming-debug") }
        assertTrue("Owned artwork stays in colour", saturation(first) > 80)
        assertTrue("Missing artwork is grey", saturation(second) < 5)
        capture("missing-grey")
        ui.onNodeWithText("Griser les manquantes").performClick()
        ui.waitForIdle()
        assertTrue(saturation(first) > 80)
        assertTrue("Disabling dimming restores the missing card's colour", saturation(second) > 80)
        assertEquals(2, owned.single().quantity)
        capture("missing-colour")
    }

    @Test fun listEditingKeepsOneAddActionAndDoesNotChangeQuantities() {
        var additions = 0
        val list = CardList("review", "Cartes recherchées", listOf(CardListEntry(first, 2)))
        ui.setContent { Theme {
            CardListsPanel(listOf(list), list.id, Modifier.fillMaxSize(), {}, {}, { _, _ -> }, {},
                { _, _, _ -> additions++ }, { _, _ -> }, emptyList(), catalog, CardDetails.empty(), LegalityRules.DEFAULT, { _, _ -> })
        } }
        ui.onAllNodesWithText("Ajouter des cartes").assertCountEquals(1)
        ui.onNodeWithText("Modifier la liste").performClick()
        ui.onAllNodesWithText("Ajouter des cartes").assertCountEquals(1)
        ui.onNodeWithText("Renommer").assertIsDisplayed()
        ui.onNodeWithText("Terminer les modifications").performClick()
        assertEquals(0, additions)
        assertEquals(2, list.entries.single().quantity)
        capture("list")
    }

    @Test fun everySettingsDestinationRemainsReachable() {
        var selected = ""
        ui.setContent { Theme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            SettingsOverview({ selected = it }, null)
        } } }
        listOf("Mon compte" to "Communauté", "Hors connexion" to "Hors connexion", "Apparence" to "Apparence",
            "Mises à jour" to "Mises à jour", "Sauvegarder et restaurer" to "Sauvegardes").forEach { (label, destination) ->
            ui.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(destination, selected)
        }
        capture("settings")
    }

    @Test fun largeTextKeepsSettingsAndTheMissingCardsSwitchReachable() {
        var destination = ""
        var dimmed by mutableStateOf(true)
        ui.setContent { Theme {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        SettingsOverview({ destination = it }, null)
                        MissingCardDimmingToggle(dimmed) { dimmed = it }
                    }
                    CollectionBottomBar("Collection") { destination = it }
                }
            }
        } }
        ui.onNodeWithText("Sauvegarder et restaurer").performScrollTo().performClick()
        assertEquals("Sauvegardes", destination)
        ui.onNodeWithText("Griser les manquantes").performScrollTo().performClick().assertIsOff()
        assertFalse(dimmed)
        capture("large-text")
    }

    @Test fun visualTourOfOfflineProductionPanels() = visualTour("phone")

    @Test @org.robolectric.annotation.Config(qualifiers = "w840dp-h700dp-320dpi")
    fun visualTourOnFold() = visualTour("fold")

    @Test @org.robolectric.annotation.Config(qualifiers = "w780dp-h360dp-320dpi")
    fun visualTourInLandscape() = visualTour("landscape")

    private fun visualTour(format: String) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ui.activity).allowHardware(false).build())
        assertTrue(CatalogSyncWorker.importBundled(ui.activity))
        val database = CollectionDatabase(ui.activity)
        val sets = database.allCatalogSets()
        val cards = database.catalogCardsForSet("SOR").take(280)
        val details = CardDetails.load(ui.activity)
        val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-100", 2), DeckEntry("SOR-101", 3)))
        val saved = DeckStore.SavedDeck("review", "Mon deck de démonstration", "Premier", deck, 0)
        val rows = listOf(CollectionCard(1, "Carte de démonstration", "SOR", "100", "Normale", 2, "FR"))
        val book = BadgeCatalogue.load(ui.activity)
        val badges = BadgeEngine.evaluate(book, cards, rows, details, emptyMap(), emptyMap())
        cards.forEach { card ->
            val sample = if (DeckConstruction.type(card) in listOf("leader", "chef", "base")) "leader-0" else "portrait"
            javaClass.getResourceAsStream("/scan/$sample.jpg")?.use { input ->
                val bytes = input.readBytes()
                listOf(card.imageUrl, CardLanguageArtwork.displayFace(ui.activity, card, card.language).first).distinct().filter { it.startsWith("https://") }.forEach { url -> OfflineImages.saveBytes(ui.activity, url, bytes) }
            }
        }
        var screen by mutableStateOf("settings")
        ui.setContent { Theme { key(screen) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (screen) {
                    "home" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        val progress = remember { mutableStateOf(1f) }
                        Box(Modifier.fillMaxWidth()) {
                            PremiumHomeBanner(false, progress, progress)
                            CommunityHomeControls({}, {}, {})
                        }
                        PremiumHomeShortcuts(UnifiedCollection.visibleHome(HomeLayoutStore.defaults),
                            remember { mutableStateOf<BorderTilt?>(null) }, progress, false, onOpen = {})
                    }
                    "appearance" -> Column(Modifier.verticalScroll(rememberScrollState())) { ReducedMotionSettings(); BorderReflectionSettings(true) {} }
                    "members" -> MemberDirectory("me", emptyList(), false,
                        loadPage = { MemberPage(listOf(PlayerProfile("one", "Un joueur"), PlayerProfile("two", "Un autre joueur")), null, false) },
                        onRequest = {}, onProfile = {}, fillHeight = true)
                    "settings" -> Column(Modifier.verticalScroll(rememberScrollState())) { SettingsOverview({}, null) }
                    "collection" -> UnifiedCollectionCatalog(cards, rows, "Toutes les cartes", "SOR", "Toutes", "", "Visuel", details, Modifier.weight(1f), { _, _ -> }, {})
                    "decks" -> DeckLibraryPanel(listOf(saved), Modifier.weight(1f), cards, onCreate = {}, onResumeDraft = {}, onImport = {}, onOpen = {}, onDuplicate = {}, onDelete = {}, owned = rows, details = details)
                    "builder" -> DeckBuilderPanel(cards, rows, deck, details = details, rules = LegalityRules.DEFAULT, onCancel = {}, onSave = { _, _, _, _ -> }, onPreview = { _, _ -> })
                    "lists" -> CardListsPanel(listOf(CardList("review", "Cartes recherchées", listOf(CardListEntry(cards.first(), 2)))), null, Modifier.weight(1f), {}, {}, { _, _ -> }, {}, { _, _, _ -> }, { _, _ -> }, rows, cards, details, LegalityRules.DEFAULT, { _, _ -> })
                    "proxies" -> ProxyBuilderPanel(cards, rows, details, onBack = {})
                    "badges" -> BadgesPanel(badges, cards, onCard = {})
                    "counter" -> MatchCounterPanel(database) {}
                    "statistics" -> CollectionValuePanel(emptyList(), emptyMap(), sets)
                    "backups" -> Column(Modifier.verticalScroll(rememberScrollState())) { PortableBackupPanel(database) {} }
                    "history" -> CollectionHistoryPanel(database, {}, Modifier.weight(1f))
                    "offline" -> Column(Modifier.verticalScroll(rememberScrollState())) { OfflineImagesPanel(cards, sets, listOf(saved), false) }
                    "about" -> Column(Modifier.verticalScroll(rememberScrollState())) { LegalInformationPanel() }
                    "scan" -> { ScannerCardSummary(cards.first(), "Normale", "Ajoutée à ma collection", 2); ScannerModeChoices("Une par une") {} }
                    "profile" -> PlayerHeading(PlayerProfile("review", "Joueur de démonstration", firstName = "Prénom"))
                    "chat" -> MessengerChatLayout("me", "Mon ami", listOf(PlayerMessage("1", "peer", "text", "On échange nos cartes ?", "", "", 1)), "", {}, false, true, null, MessengerSignal(), null, cards, {}, {}, {}, {}, {}, {})
                    "trade" -> TradeSides(listOf(TradeSelection("SOR-100", quantity = 2, language = "FR", finish = "Normale")), listOf(TradeSelection("SOR-101", quantity = 1, language = "FR", finish = "Normale")), cards)
                    "complete" -> CompleteSetPanel(sets, mapOf("SOR" to cards), rows, emptyMap(), Modifier.weight(1f), { _, _ -> }, database = database, onGoalChanged = {}, details = details)
                    "search" -> GlobalSearchPanel("", {}, cards, sets, rows, listOf(saved), details, SearchFacets(), {}, Modifier.weight(1f), PremierLegality(cards), { _, _ -> }, {}, {})
                }
            }
        } } }
        listOf("home", "settings", "appearance", "collection", "decks", "builder", "lists", "proxies", "badges", "counter", "statistics", "backups", "history", "offline", "about", "scan", "profile", "members", "chat", "trade", "complete", "search").forEach { destination ->
            ui.runOnIdle { screen = destination }
            ui.waitForIdle()
            capture("tour-$format-$destination")
        }
    }
}
