package fr.swucollection.app

import android.content.Context
import org.json.JSONArray
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
@Config(sdk=[28], manifest=Config.NONE)
class CardLanguageStorageTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    private val r2 = CatalogCard("SOR","236","R2-D2 — Ignore le protocole","Unité","Spéciale","https://cdn.starwarsunlimited.com/card_SWH_01fr_236_R2_D2.png")
    private val c3 = r2.copy(number="238",name="C-3PO — Droïde de protocole")
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "SOR:236":{"family":"r2","en":"R2-D2","aspects":[],"traits":[]},
        "SOR:238":{"family":"c3","en":"C-3PO","aspects":[],"traits":[]}}}""")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db"); context.deleteDatabase("swu_decks.db")
        context.getSharedPreferences("scan_batch",0).edit().clear().commit()
        db = CollectionDatabase(context)
    }
    @After fun cleanup() {
        db.close(); context.deleteDatabase("swu_collection.db"); context.deleteDatabase("swu_decks.db")
        context.getSharedPreferences("scan_batch",0).edit().clear().commit()
    }
    private fun counts() = db.allCards().associate { it.language to it.quantity }

    @Test fun scansKeepFrenchAndEnglishApartAndCountTheSameFamilyOnlyOnce() {
        db.addScannedCard(r2,"Normale","r2")
        repeat(2) { db.addScannedCard(r2.copy(language="EN"),"Normale","r2") }
        assertEquals(mapOf("FR" to 1,"EN" to 2),counts())
        assertEquals(3L,db.badgeEventCounts()["scan"])
        assertEquals(1L,db.badgeEventCounts()["scan_unique"])
        db.close();db=CollectionDatabase(context)
        assertEquals(mapOf("FR" to 1,"EN" to 2),counts())
        db.changeQuantity(db.allCards().single { it.language=="EN" }.id,-1)
        assertEquals(mapOf("FR" to 1,"EN" to 1),counts())
    }
    @Test fun allFiveLanguagesSurviveRelaunchAndStillRepresentOneDistinctFamily() {
        CardLanguages.supported.forEachIndexed { index,language ->
            repeat(index+1) { db.addScannedCard(r2.copy(language=language),"Normale","r2") }
        }
        val expected=CardLanguages.supported.mapIndexed { index,language -> language to (index+1) }.toMap()
        assertEquals(expected,counts());assertEquals(15L,db.badgeEventCounts()["scan"])
        assertEquals(1L,db.badgeEventCounts()["scan_unique"])
        db.close();db=CollectionDatabase(context)
        assertEquals(expected,counts())
        db.importJson(db.exportJson(),true)
        assertEquals(expected,counts())
    }
    @Test fun versionTenMigrationPreservesIdsQuantitiesDecksListsAndBadgeEvents() {
        db.replaceCatalog(listOf(CatalogSet("SOR","Étincelle","",1)),listOf(r2))
        db.createCardList("Mes favoris")
        DeckStore(context).use { it.save("Deck existant","Premier",PortableDeck("SOR-005","SOR-024",emptyList(),emptyList())) }
        db.recordBadgeEvent("scan","before-upgrade",7)
        db.writableDatabase.execSQL("DROP TABLE collection_cards")
        db.writableDatabase.execSQL("CREATE TABLE collection_cards(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,set_code TEXT NOT NULL,card_number TEXT NOT NULL,variant TEXT NOT NULL,quantity INTEGER NOT NULL,UNIQUE(set_code,card_number,variant))")
        db.writableDatabase.execSQL("INSERT INTO collection_cards VALUES(42,'R2-D2','SOR','236','Foil',7)")
        db.writableDatabase.version=10
        db.close();db=CollectionDatabase(context)
        val migrated=db.allCards().single()
        assertEquals(42L,migrated.id);assertEquals(7,migrated.quantity)
        assertEquals(CardLanguages.UNKNOWN,migrated.language)
        assertEquals(11,db.readableDatabase.version)
        assertEquals("Mes favoris",db.cardLists().single().name)
        assertEquals(r2,db.catalogCardsForSet("SOR").single())
        assertEquals(7L,db.badgeEventCounts()["scan"])
        DeckStore(context).use { assertEquals("Deck existant",it.all().single().name) }
        db.addOrIncrement(r2.name,"SOR","236","Foil","FR")
        db.addOrIncrement(r2.name,"SOR","236","Foil","EN")
        assertEquals(9,db.allCards().sumOf { it.quantity })
        assertEquals(3,db.allCards().size)
    }
    @Test fun upgradingDirectlyFromTheOriginalSchemaKeepsEveryPrintingAndQuantity() {
        db.close()
        context.deleteDatabase("swu_collection.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath("swu_collection.db"), null).use { legacy ->
            legacy.execSQL("CREATE TABLE collection_cards(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,set_code TEXT NOT NULL,card_number TEXT NOT NULL,variant TEXT NOT NULL,quantity INTEGER NOT NULL,UNIQUE(set_code,card_number,variant))")
            legacy.execSQL("INSERT INTO collection_cards VALUES(12,'Éclairs de Force','SOR','138','Normale',3)")
            legacy.execSQL("INSERT INTO collection_cards VALUES(42,'Éclairs de Force','SOR','138','Foil',7)")
            legacy.version = 1
        }
        db = CollectionDatabase(context)
        val expected = listOf(
            CollectionCard(12,"Éclairs de Force","SOR","138","Normale",3,CardLanguages.UNKNOWN),
            CollectionCard(42,"Éclairs de Force","SOR","138","Foil",7,CardLanguages.UNKNOWN))
        assertEquals(expected.sortedBy { it.id }, db.allCards().sortedBy { it.id })
        val backup = db.exportJson()
        db.importJson(backup, true)
        db.close(); db = CollectionDatabase(context)
        assertEquals(10, db.allCards().sumOf { it.quantity })
        assertEquals(setOf("Normale","Foil"), db.allCards().map { it.variant }.toSet())
        assertTrue(db.allCards().all { it.language == CardLanguages.UNKNOWN })
    }
    @Test fun backupRestoresBothLanguagesAndRejectsInvalidOrMissingLanguageBeforeMutation() {
        db.addOrIncrement(r2.name,"SOR","236","Normale","FR")
        db.addOrIncrement(r2.name,"SOR","236","Normale","EN")
        val backup=db.exportJson()
        assertEquals(7,JSONObject(backup).getInt("version"));assertEquals(2,validateBackup(backup).copies)
        db.importJson(backup,true)
        assertEquals(mapOf("FR" to 1,"EN" to 1),counts())
        for (language in listOf<Any>("XX",42,JSONObject.NULL)) {
            val malformed=JSONObject(backup)
            malformed.getJSONArray("cards").getJSONObject(0).put("language",language)
            assertTrue(runCatching { db.importJson(malformed.toString(),true) }.isFailure)
            assertEquals(backup,db.exportJson())
        }
        val missing=JSONObject(backup)
        missing.getJSONArray("cards").getJSONObject(0).remove("language")
        assertTrue(runCatching { db.importJson(missing.toString(),true) }.isFailure)
        assertEquals(backup,db.exportJson())
        assertTrue(db.exportCsv().contains("Langue"));assertTrue(db.exportCsv().contains("\"EN\""))
    }
    @Test fun oldBackupsRemainUnknownRatherThanInventingFrenchCopies() {
        val old=JSONObject(db.exportJson()).put("version",6).put("cards",JSONArray().put(JSONObject()
            .put("name",r2.name).put("setCode","SOR").put("number","236").put("variant","Normale").put("quantity",2)))
        db.importJson(old.toString(),true)
        assertEquals(mapOf(CardLanguages.UNKNOWN to 2),counts())
    }
    @Test fun correctingLegacyLanguageMergesOnlyTheMatchingLanguageAndUndoRestoresEverything() {
        db.addOrIncrement(r2.name,"SOR","236","Normale",CardLanguages.UNKNOWN)
        db.addOrIncrement(r2.name,"SOR","236","Normale","FR")
        db.addOrIncrement(r2.name,"SOR","236","Normale","EN")
        val before=db.allCards()
        db.changeLanguage(before.single { it.language==CardLanguages.UNKNOWN }.id,"FR")
        assertEquals(mapOf("FR" to 2,"EN" to 1),counts())
        db.undoLastChange()
        assertEquals(before,db.allCards())
    }
    @Test fun changingLanguageWithoutAnExistingTargetAlsoUndoesWithoutAnIdCollision() {
        db.addOrIncrement(r2.name,"SOR","236","Normale",CardLanguages.UNKNOWN)
        val before=db.allCards()
        db.changeLanguage(before.single().id,"IT")
        assertEquals("IT",db.allCards().single().language)
        assertEquals(before.single().id,db.allCards().single().id)
        db.undoLastChange()
        assertEquals(before,db.allCards())
    }
    @Test fun aMixedLegacyLineCanBeSplitIntoFrenchAndEnglishAndBothChangesCanBeUndone() {
        repeat(7) { db.addOrIncrement(r2.name,"SOR","236","Normale",CardLanguages.UNKNOWN) }
        val original=db.allCards()
        db.changeLanguage(original.single().id,"FR",3)
        assertEquals(mapOf(CardLanguages.UNKNOWN to 4,"FR" to 3),counts())
        db.changeLanguage(db.allCards().single { it.language==CardLanguages.UNKNOWN }.id,"EN",4)
        assertEquals(mapOf("FR" to 3,"EN" to 4),counts())
        db.undoLastChange()
        assertEquals(mapOf(CardLanguages.UNKNOWN to 4,"FR" to 3),counts())
        db.undoLastChange()
        assertEquals(original,db.allCards())
    }
    @Test fun batchDraftAndAtomicDeckSaveKeepLanguagesWithoutCreditingAReplayTwice() {
        var batch=ScanBatchStore(context)
        batch.add(r2,"Normale");batch.add(r2.copy(language="EN"),"Normale")
        batch=ScanBatchStore(context)
        assertEquals(setOf("FR","EN"),batch.entries.map { it.card.language }.toSet())
        val entries=DeckScan.collectionFromJson(DeckScan.collectionJson(batch.entries))
        assertEquals(batch.entries,entries)
        val deck=PortableDeck("SOR-005","SOR-024",listOf(DeckEntry("SOR-236",2)),emptyList())
        DeckStore(context).use { store ->
            repeat(2) { store.saveWithScans("Deck bilingue","Premier",deck,"mixed",batch.id,entries,details) }
            assertEquals(deck,store.all().single().deck)
        }
        assertEquals(mapOf("FR" to 1,"EN" to 1),counts())
        assertEquals(2L,db.badgeEventCounts()["scan"])
        assertEquals(1L,db.badgeEventCounts()["scan_unique"])
    }
    @Test fun deckCopyLimitAndCollectionObjectivesPoolLanguagesButExtrasKeepTheirLanguage() {
        val owned=listOf(CollectionCard(1,r2.name,"SOR","236","Normale",2,"FR"),CollectionCard(2,r2.name,"SOR","236","Normale",2,"EN"))
        assertEquals(0,CollectionObjectives.rows(listOf(r2),owned,true,false,details).single().missing)
        val extras=CollectionObjectives.extras(owned,listOf(r2),true,false,details)
        assertEquals(listOf(owned[1].copy(quantity=1)),extras)
        assertEquals(extras,CollectionObjectives.extras(owned,listOf(r2),true,true,details))
        assertTrue(CollectionPlan.shareAvailable(extras).contains("Anglais"))
        var deck=PortableDeck("SOR-005","SOR-024",emptyList(),emptyList())
        repeat(3) { i -> deck=DeckScan.add(deck,r2.copy(language=if(i==0) "FR" else "EN"),DeckEditor.Zone.MAIN,listOf(r2),details) }
        assertEquals(3,deck.deck.sumOf { it.count })
        assertTrue(runCatching { DeckScan.add(deck,r2,DeckEditor.Zone.MAIN,listOf(r2),details) }.isFailure)
    }
    @Test fun twoLanguagesOfR2NeverReplaceC3ForAStoryBadge() {
        val badge=BadgeDefinition("droids","Duo inséparable","Histoires","Trilogie originale","Deux droïdes",0,
            listOf(BadgeRequirement("R2-D2",listOf("SOR-236"),listOf("R2-D2")),BadgeRequirement("C-3PO",listOf("SOR-238"),listOf("C-3PO"))),"",2,emptyList(),listOf("SOR-236"))
        val owned=listOf(CollectionCard(1,r2.name,"SOR","236","Normale",1,"FR"),CollectionCard(2,r2.name,"SOR","236","Normale",1,"EN"))
        fun evaluate(rows: List<CollectionCard>)=BadgeEngine.evaluate(BadgeCatalogue(listOf(badge),emptyMap()),listOf(r2,c3),rows,details,emptyMap(),emptyMap()).single()
        assertEquals(1,evaluate(owned).current)
        assertFalse(evaluate(owned).earned)
        assertEquals(2,evaluate(owned+CollectionCard(3,c3.name,"SOR","238","Normale",1,"EN")).current)
    }
    @Test fun localImageCacheNeverReplacesFrenchArtworkWithEnglishArtwork() {
        val french="https://cdn.starwarsunlimited.com/card_FR_test.png"
        val english="https://cdn.starwarsunlimited.com/card_EN_test.png"
        OfflineImages.clear(context)
        OfflineImages.saveBytes(context,french,byteArrayOf(1))
        assertTrue(OfflineImages.contains(context,french));assertFalse(OfflineImages.contains(context,english))
        OfflineImages.saveBytes(context,english,byteArrayOf(2))
        assertNotEquals(OfflineImages.source(context,french),OfflineImages.source(context,english))
        OfflineImages.clear(context)
    }
}
