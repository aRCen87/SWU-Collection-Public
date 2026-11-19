package fr.swucollection.app

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CollectionFavoritesTest {
    @Test fun favoritesAreIdempotentAndSurviveBackupWithoutChangingStock() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        val db = CollectionDatabase(context)
        try {
            val card = CatalogCard("SOR", "100", "Favorite", "Unité", "Rare")
            db.addOrIncrement(card.name, card.setCode, card.number, "Foil")
            val stock = db.allCards()
            db.setFavoriteCard(card, true); db.setFavoriteCard(card, true)
            assertEquals(setOf(MarketPricing.key("SOR", "100")), db.favoriteCardKeys())
            assertEquals(stock, db.allCards())
            val backup = db.exportJson()
            db.setFavoriteCard(card, false)
            assertTrue(db.favoriteCardKeys().isEmpty())
            db.importJson(backup, true)
            assertEquals(setOf(MarketPricing.key("SOR", "100")), db.favoriteCardKeys())
            assertEquals(stock.map { it.copy(id = 0) }, db.allCards().map { it.copy(id = 0) })
        } finally { db.close() }
        context.deleteDatabase("swu_collection.db")
    }
}
