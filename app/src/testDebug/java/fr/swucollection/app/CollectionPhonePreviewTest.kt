package fr.swucollection.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
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
}
