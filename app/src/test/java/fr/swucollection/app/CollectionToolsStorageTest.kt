package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
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
class CollectionToolsStorageTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    private val card = CatalogCard("ASH", "031", "Éclairs — essai", "Évènement", "Rare")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        context.getSharedPreferences("scan_batch", 0).edit().clear().commit()
        db = CollectionDatabase(context)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("swu_collection.db") }
    @Test fun freshInstallAndCatalogueSyncNeverPopulateOwnedCards() {
        assertTrue(db.allCards().isEmpty())
        assertTrue(db.cardLists().isEmpty())
        DeckStore(context).use { assertTrue(it.all().isEmpty()) }
        assertTrue(db.badgeAwards().isEmpty())
        assertTrue(db.badgeEventCounts().isEmpty())
        assertEquals(0, JSONObject(db.exportJson()).getJSONArray("cards").length())
        val sets = listOf(CatalogSet("ASH", "Cendres", "", 1))
        repeat(2) {
            db.replaceCatalog(sets, listOf(card))
            assertTrue(db.allCards().isEmpty())
        }
        db.close()
        db = CollectionDatabase(context)
        assertEquals(card, db.catalogCardsForSet("ASH").single())
        assertTrue(db.allCards().isEmpty())
        assertEquals(0, JSONObject(db.exportJson()).getJSONArray("cards").length())
    }
    @Test fun batchSurvivesRelaunchCanBeEditedAndCommitsExactlyOnce() {
        var batch = ScanBatchStore(context)
        batch.add(card, "Normale"); batch.add(card, "Normale"); batch.add(card, "Foil")
        batch = ScanBatchStore(context)
        assertEquals(3, batch.entries.sumOf { it.quantity })
        batch.change(batch.entries.first(), -1)
        assertTrue(db.allCards().isEmpty())
        db.commitScanBatch(batch.id, batch.entries)
        db.commitScanBatch(batch.id, batch.entries)
        assertEquals(2, db.allCards().sumOf { it.quantity })
        assertEquals(setOf("Normale", "Foil"), db.allCards().map { it.variant }.toSet())
        batch.clear()
        assertTrue(ScanBatchStore(context).entries.isEmpty())
    }
    @Test fun failedBatchRollsBackEveryCardAndCanBeRetried() {
        val other = card.copy(number = "032")
        db.writableDatabase.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON collection_cards WHEN NEW.card_number='032' BEGIN SELECT RAISE(ABORT, 'test'); END")
        val entries = listOf(ScanBatchEntry(card, "Normale", 2), ScanBatchEntry(other, "Foil", 1))
        try { db.commitScanBatch("retry", entries); fail("Expected transaction failure") } catch (_: android.database.SQLException) { }
        assertTrue(db.allCards().isEmpty())
        assertFalse(db.isScanBatchCommitted("retry"))
        db.writableDatabase.execSQL("DROP TRIGGER fail_batch")
        db.commitScanBatch("retry", entries)
        assertEquals(3, db.allCards().sumOf { it.quantity })
    }
    @Test fun migrationFromFivePreservesCollectionListsCatalogueAndCreatesBatchLedger() {
        db.addOrIncrement(card.name, card.setCode, card.number, "Foil")
        val list = db.createCardList("Deck préféré"); db.putListCard(list, card, 3)
        db.replaceCatalog(listOf(CatalogSet("ASH", "Cendres", "", 1)), listOf(card))
        db.writableDatabase.execSQL("DROP TABLE scan_batches")
        db.writableDatabase.version = 5
        db.close(); db = CollectionDatabase(context)
        assertEquals("Foil", db.allCards().single().variant)
        assertEquals(3, db.cardLists().single().entries.single().quantity)
        assertEquals(card, db.catalogCardsForSet("ASH").single())
        db.commitScanBatch("migration", listOf(ScanBatchEntry(card, "Normale", 2)))
        assertEquals(3, db.allCards().sumOf { it.quantity })
    }
    @Test fun directUpgradeFromSixPreservesDataAndAddsHistory() {
        db.addOrIncrement(card.name, card.setCode, card.number, "Hyperspace Foil")
        val list = db.createCardList("Favoris été"); db.putListCard(list, card, 3)
        db.replaceCatalog(listOf(CatalogSet("ASH", "Cendres", "", 1)), listOf(card))
        val expectedCards = db.allCards()
        val expectedLists = db.cardLists()
        for (table in listOf("collection_history", "collection_goals", "list_preferences")) {
            db.writableDatabase.execSQL("DROP TABLE " + table)
        }
        db.writableDatabase.version = 6
        db.close(); db = CollectionDatabase(context)
        assertEquals(expectedCards, db.allCards())
        assertEquals(expectedLists, db.cardLists())
        assertEquals(card, db.catalogCardsForSet("ASH").single())
        assertTrue(db.history().isEmpty())
        db.addOrIncrement(card.name, card.setCode, card.number, "Hyperspace Foil")
        db.undoLastChange()
        assertEquals(expectedCards, db.allCards())
        val backup = db.exportJson()
        db.importJson(backup, true)
        assertEquals(expectedCards.map { it.copy(id = 0) }, db.allCards().map { it.copy(id = 0) })
        assertEquals(expectedLists, db.cardLists())
    }

    @Test fun portableRestorePreservesAccentsVariantsAndListsAndIsRepeatable() {
        db.addOrIncrement(card.name, card.setCode, card.number, "Hyperspace Foil")
        val list = db.createCardList("Favoris été"); db.putListCard(list, card, 3)
        val beforeCards = db.allCards().map { it.copy(id = 0) }; val beforeLists = db.cardLists()
        val backup = db.exportJson()
        assertEquals(BackupSummary(1, 1, 1), validateBackup(backup))
        db.addOrIncrement(card.name, card.setCode, card.number, "Normale")
        db.importJson(backup, true); db.importJson(backup, true)
        assertEquals(beforeCards, db.allCards().map { it.copy(id = 0) })
        assertEquals(beforeLists, db.cardLists())
        db.importJson(backup, false)
        assertEquals(2, db.allCards().single().quantity)
        assertEquals(beforeLists, db.cardLists())
    }
    @Test fun badBackupNeverMutatesData() {
        db.addOrIncrement(card.name, card.setCode, card.number, "Normale")
        val original = db.exportJson()
        for (bad in listOf(JSONObject(original).put("version", 99), JSONObject(original).apply {
            val rows = getJSONArray("cards"); rows.put(rows.getJSONObject(0))
        }, JSONObject(original).apply { getJSONArray("cards").getJSONObject(0).put("quantity", -1) })) {
            try { db.importJson(bad.toString(), true); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(original, db.exportJson())
        }
    }
    @Test fun automaticallyDetectedPrintsSurviveBatchCommitAndBackupWithTheirOwnNumbers() {
        val prints = listOf(
            CatalogCard("JTL", "788", "Gold Leader", "Unité", "Commune",
                "https://cdn.starwarsunlimited.com/card_04320788_FR_Gold_Leader.png"),
            CatalogCard("JTL", "552", "Gold Leader", "Unité", "Commune",
                "https://cdn.starwarsunlimited.com/card_04310552_FR_Gold_Leader.png"),
            CatalogCard("JTL", "1093", "Gold Leader", "Unité", "Légendaire",
                "https://cdn.starwarsunlimited.com/card_04341093_FR_Gold_Leader.png"),
            CatalogCard("JTL", "1057", "Gold Leader", "Unité", "Légendaire",
                "https://cdn.starwarsunlimited.com/card_04331057_FR_Gold_Leader.png"))
        db.addOrIncrement("Gold Leader", "JTL", "054", "Normale")
        var batch = ScanBatchStore(context)
        prints.forEach { print -> batch.add(print, ScanVariants.detect(print).resolve("Automatique")!!) }
        batch = ScanBatchStore(context)
        db.commitScanBatch(batch.id, batch.entries)
        db.commitScanBatch(batch.id, batch.entries)
        val expected = mapOf("054" to "Normale", "788" to "Hyperspace Foil",
            "552" to "Foil", "1093" to "Serialized", "1057" to "Prestige Foil")
        assertEquals(expected, db.allCards().associate { it.number to it.variant })
        val backup = db.exportJson()
        db.importJson(backup, true)
        db.importJson(backup, true)
        assertEquals(expected, db.allCards().associate { it.number to it.variant })
        assertEquals(5, db.allCards().sumOf { it.quantity })
    }
}

