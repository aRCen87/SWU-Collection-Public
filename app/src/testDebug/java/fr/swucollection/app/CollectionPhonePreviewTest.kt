package fr.swucollection.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real application page, isolated local data and offline sample artwork. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPhonePreviewTest {
    @get:Rule(order = 0) val prepare = object : ExternalResource() {
        override fun before() {
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            runCatching { androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build()) }
            check(CatalogSyncWorker.importBundled(context))
            context.getSharedPreferences("collection_filters", 0).edit()
                .putString("collectionScope", "Manquantes")
                .putString("collectionSetFilter", "Toutes")
                .putString("collectionView", "Visuel")
                .putString("collectionCardScale", "1.0")
                .putString("groupVersions", "false")
                .putBoolean("visual_family_default", true)
                .putString("dimMissing", "true").commit()
            coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).allowHardware(false).build())
            CollectionDatabase(context).use { database ->
                (database.catalogCardsForSet("IC27").take(20) + database.catalogCardsForSet("SOR").take(20)).forEach { card ->
                    val sample = if (DeckConstruction.type(card) in listOf("leader", "chef", "base")) "leader-0" else "portrait"
                    javaClass.getResourceAsStream("/scan/$sample.jpg")?.use { stream ->
                        val bytes = stream.readBytes()
                        (listOf(card.imageUrl, CardLanguageArtwork.displayFace(context, card, card.language).first) +
                            CardLanguageArtwork.allFaces(context, card).flatMap { listOf(it.imageUrl, it.backImageUrl) })
                            .distinct().filter { it.startsWith("https://") }.forEach { OfflineImages.saveBytes(context, it, bytes) }
                    }
                }
            }
        }
    }
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        ui.runOnIdle {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/visual-review", "$name.png").also { it.parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun captureStandardPhoneCollectionAndScopeMenu() {
        ui.onAllNodesWithText("Collection").onFirst().performClick()
        ui.onNodeWithText("Afficher : Manquantes ▾").assertIsDisplayed()
        ui.waitForIdle()
        ui.waitUntil(15_000) {
            val bounds = ui.onAllNodesWithContentDescription("Visuel de la carte").onFirst().fetchSemanticsNode().boundsInRoot
            var loaded = false
            ui.runOnIdle {
                val view = ui.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
                loaded = android.graphics.Color.red(pixel) > 90
                bitmap.recycle()
            }
            loaded
        }
        capture("collection-phone-current")
        ui.onNodeWithText("Afficher : Manquantes ▾").performClick()
        ui.onNodeWithText("Possédées").assertIsDisplayed()
        ui.onNodeWithText("Toutes les cartes").assertIsDisplayed()
        ui.onNodeWithText("Manquantes", substring = false).assertIsDisplayed()
        ui.runOnIdle {
            val type = Class.forName("android.view.WindowManagerGlobal")
            val manager = type.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
            val views = type.getDeclaredField("mViews").apply { isAccessible = true }.get(manager) as List<*>
            val popup = views.filterIsInstance<android.view.View>().last()
            val menu = Bitmap.createBitmap(popup.width, popup.height, Bitmap.Config.ARGB_8888)
            popup.draw(android.graphics.Canvas(menu))
            File("build/visual-review/collection-phone-scope-menu.png").outputStream().use {
                menu.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            menu.recycle()
        }
    }

    @Test fun compactCardReaderKeepsNavigationAndConfirmedInventoryChanges() {
        ui.onAllNodesWithText("Collection").onFirst().performClick()
        ui.onNodeWithText("Filtres", substring = false).performClick()
        ui.onNodeWithText("Toutes les extensions").performClick()
        ui.onNodeWithText("SOR · Étincelle de Rébellion", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Voir les cartes").performClick()
        ui.onAllNodesWithContentDescription("Visuel de la carte").onFirst().performTouchInput { click() }
        ui.onNodeWithTag("swu_card_sheet").assertIsDisplayed()
        val inSheet = hasAnyAncestor(hasTestTag("swu_card_sheet"))
        fun artwork() = ui.onAllNodes(hasContentDescription("Visuel de la carte") and inSheet, useUnmergedTree = true).onFirst()
        ui.onNodeWithText("0 possédée").assertIsDisplayed()
        ui.onNodeWithText("Langue de la carte").assertDoesNotExist()
        ui.onNodeWithText("Toucher la carte pour la retourner").assertDoesNotExist()
        ui.onNodeWithText("Dans 0 liste(s)").assertDoesNotExist()
        ui.onNodeWithText("Fermer", substring = false).assertDoesNotExist()
        val recto = artwork().fetchSemanticsNode().boundsInRoot
        assertTrue("Horizontal leader has no portrait-shaped empty frame", recto.height < recto.width)
        ui.waitUntil(15_000) {
            var loaded = false
            ui.runOnIdle {
                val view = ui.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                loaded = android.graphics.Color.red(bitmap.getPixel(recto.center.x.toInt(), recto.center.y.toInt())) > 90
                bitmap.recycle()
            }
            loaded
        }
        capture("card-reader-phone")
        artwork().performTouchInput { click() }
        ui.waitForIdle()
        assertTrue("Leader still turns to its portrait back", artwork().fetchSemanticsNode().boundsInRoot.height > recto.height)
        artwork().performTouchInput { click() }
        ui.onNodeWithText("Deux faces").performClick()
        ui.onNodeWithText("Recto", substring = false).assertExists()
        ui.onNodeWithText("Verso", substring = false).assertExists()
        ui.onNodeWithText("Deux faces").performClick()
        ui.onNodeWithContentDescription("Afficher la carte en plein écran").performClick()
        ui.onNodeWithContentDescription("Fermer le plein écran").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Autres actions de la carte").performScrollTo().performClick()
        ui.onNodeWithText("Comparer", substring = false).assertIsDisplayed()
        ui.onNodeWithText("Partager à des amis").assertIsDisplayed()
        ui.onNodeWithText("Comparer", substring = false).performClick()
        ui.onNodeWithText("Comparer deux cartes").assertIsDisplayed()
        ui.onNodeWithText("Fermer", substring = false).performClick()
        ui.onNodeWithText("Normale ▾").performScrollTo().performClick()
        ui.onAllNodesWithText("Normale", substring = false).onLast().performClick()
        ui.onNodeWithText("Utilisée dans mes decks · 0 ▾").performScrollTo().performClick()
        ui.onNodeWithText("Aucun deck pour le moment.").assertExists()
        ui.onNodeWithText("Utilisée dans mes decks · 0 ▴").performClick()
        ui.onNodeWithText("+", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Confirmer l’ajout").assertIsDisplayed()
        CollectionDatabase(ui.activity).use { assertTrue("Opening confirmation must not change stock", it.allCards().isEmpty()) }
        ui.onNodeWithText("Confirmer l’ajout").performClick()
        ui.onNodeWithText("1 possédée").assertIsDisplayed()
        CollectionDatabase(ui.activity).use { assertEquals(1, it.allCards().sumOf { row -> row.quantity }) }
        ui.onNodeWithContentDescription("Langue de la carte : Français").performScrollTo().performClick()
        ui.onNodeWithText("Anglais", substring = false).performClick()
        ui.onNodeWithText("0 possédée").assertIsDisplayed()
        ui.onNodeWithText("1 au total, autres versions comprises").assertExists()
        ui.onNodeWithContentDescription("Langue de la carte : Anglais").performClick()
        ui.onNodeWithText("Français", substring = false).performClick()
        ui.onNodeWithText("1 possédée").assertIsDisplayed()
        ui.onNodeWithText("−", substring = false).performClick()
        ui.onNodeWithText("0 possédée").assertIsDisplayed()
        CollectionDatabase(ui.activity).use { assertTrue(it.allCards().isEmpty()) }
        ui.onNodeWithContentDescription("Carte suivante").performClick()
        ui.onNodeWithContentDescription("Carte précédente").assertIsEnabled().performClick()
        ui.onNodeWithContentDescription("Fermer la carte").performClick()
        ui.onNodeWithTag("swu_card_sheet").assertDoesNotExist()
    }
}
