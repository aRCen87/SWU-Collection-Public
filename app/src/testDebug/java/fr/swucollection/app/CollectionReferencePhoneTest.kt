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

/** The real Test page with isolated stock and the publisher's cached card images. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionReferencePhoneTest {
    @get:Rule(order = 0) val prepare = object : ExternalResource() {
        override fun before() {
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            runCatching { androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build()) }
            check(CatalogSyncWorker.importBundled(context))
            context.getSharedPreferences("collection_filters", 0).edit()
                .putBoolean("reference_design_preview", true)
                .putString("collectionScope", "Possédées").putString("collectionSetFilter", "HMW")
                .putString("collectionView", "Gestion").putString("inventoryTest39", "true")
                .putString("groupVersions", "true").putBoolean("visual_family_default", true).commit()
            coil.Coil.setImageLoader(coil.ImageLoader.Builder(context).allowHardware(false).build())
            CollectionDatabase(context).use { database ->
                val leaders = database.catalogCardsForSet("HMW").filter { it.number in listOf("002", "003") }
                check(leaders.size == 2)
                leaders.forEach { card ->
                    val bytes = javaClass.getResourceAsStream("/review/HMW-${card.number}.png")!!.use { it.readBytes() }
                    OfflineImages.saveBytes(context, card.imageUrl, bytes)
                    if (card.number == "002") {
                        database.addOrIncrement(card.name, "HMW", "002", "Normale", "FR", 3)
                        database.addOrIncrement(card.name, "HMW", "002", "Foil", "FR", 1)
                        database.addOrIncrement(card.name, "HMW", "002", "Normale", "EN", 2)
                    } else database.addOrIncrement(card.name, "HMW", "003", "Normale", "FR", 2)
                }
            }
        }
    }
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String, dialog: Boolean = false) {
        ui.waitForIdle()
        ui.runOnIdle {
            val view = if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView else ui.activity.window.decorView
            fun invalidate(node: android.view.View) {
                node.invalidate()
                if (node is android.view.ViewGroup) for (index in 0 until node.childCount) invalidate(node.getChildAt(index))
            }
            invalidate(view)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            if (dialog) {
                ui.activity.window.decorView.draw(canvas)
                val amount = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.attributes.dimAmount
                canvas.drawColor(android.graphics.Color.argb((amount * 255).toInt(), 0, 0, 0))
            }
            view.draw(canvas)
            File("build/visual-review/$name.png").also { it.parentFile.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun collectionMatchesTheReferenceHierarchyAndKeepsExactStockActions() {
        ui.onAllNodesWithText("Collection", substring = false).onFirst().performClick()
        ui.onNodeWithText("6 possédées").assertIsDisplayed()
        capture("test40-real-collection-phone")
        ui.onAllNodesWithText("FR · Normale").onFirst().assertIsDisplayed()
        ui.onNodeWithText("FR · Foil").assertIsDisplayed()
        ui.onNodeWithText("EN · Normale").assertIsDisplayed()
        ui.onAllNodesWithText("Cote", substring = true).assertCountEquals(0)
        ui.onAllNodesWithText("Scanner", substring = false).assertCountEquals(1)
        val search = ui.onNodeWithContentDescription("Rechercher une carte").fetchSemanticsNode().boundsInRoot
        val filter = ui.onNodeWithText("Filtrer", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Search and filter share one row", kotlin.math.abs(search.center.y - filter.center.y) < 5f)
        ui.mainClock.advanceTimeBy(1000); ui.waitForIdle()
        capture("test40-real-collection-phone")
        ui.onAllNodesWithText("＋ Ajouter une variante").onFirst().performClick()
        ui.onNodeWithText("Ajouter à ma collection").performScrollTo().assertIsDisplayed()
        capture("test40-real-variant-sheet", dialog = true)
        CollectionDatabase(ui.activity).use { assertEquals(8, it.allCards().sumOf { row -> row.quantity }) }
    }
}
