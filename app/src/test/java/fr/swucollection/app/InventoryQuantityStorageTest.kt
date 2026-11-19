package fr.swucollection.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class InventoryQuantityStorageTest {
    private lateinit var db: CollectionDatabase
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        db = CollectionDatabase(context)
    }
    @After fun cleanup() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("swu_collection.db") }
    @Test fun multiCopyAdditionIsOneUndoableChangeAndKeepsOtherLanguagesAndPrints() {
        db.addOrIncrement("Carte", "SOR", "095", "Normale", "FR", 3)
        db.addOrIncrement("Carte", "SOR", "095", "Foil", "FR")
        db.addOrIncrement("Carte", "SOR", "095", "Normale", "EN", 2)
        val before = db.allCards()
        val events = db.history().size
        db.addOrIncrement("Carte", "SOR", "095", "Normale", "FR", 4)
        assertEquals(10, db.allCards().sumOf { it.quantity })
        assertEquals(events + 1, db.history().size)
        db.undoLastChange()
        assertEquals(before, db.allCards())
    }
    @Test fun rejectedAdditionLeavesNoPartialQuantityOrHistory() {
        db.addOrIncrement("Carte", "SOR", "095", "Normale", "FR", 9998)
        val before = db.allCards(); val history = db.history()
        assertTrue(runCatching { db.addOrIncrement("Carte", "SOR", "095", "Normale", "FR", 2) }.isFailure)
        assertTrue(runCatching { db.addOrIncrement("Carte", "SOR", "096", "Foil", "EN", 0) }.isFailure)
        assertEquals(before, db.allCards()); assertEquals(history, db.history())
    }
    @Test fun removingLastCopyCanBeUndoneWithoutChangingOtherVariants() {
        db.addOrIncrement("Carte", "SOR", "095", "Foil", "FR")
        db.addOrIncrement("Carte", "SOR", "095", "Normale", "EN", 2)
        val before = db.allCards()
        db.changeQuantity(before.first { it.variant == "Foil" }.id, -1)
        assertEquals(2, db.allCards().sumOf { it.quantity })
        db.undoLastChange()
        assertEquals(before.map { it.copy(id = 0) }.toSet(), db.allCards().map { it.copy(id = 0) }.toSet())
    }
}
