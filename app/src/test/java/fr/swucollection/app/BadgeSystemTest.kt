package fr.swucollection.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
@Config(sdk=[28],manifest=Config.NONE)
class BadgeSystemTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    private val r2=CatalogCard("SOR","236","R2-D2 — Ignore le protocole","Unité","Spéciale")
    private val c3=CatalogCard("SOR","238","C-3PO — Droïde de protocole","Unité","Spéciale")
    private val r2alt=r2.copy(number="400")
    private fun details() = CardDetails.parse(JSONObject().put("format","SWU_CARD_DETAILS").put("version",1).put("cards",JSONObject().apply {
        listOf(r2,c3,r2alt).forEach { card -> put(MarketPricing.key(card.setCode,card.number),JSONObject()
            .put("family",if(card==c3)"c3" else "r2").put("en",card.name).put("cost",2)
            .put("aspects",JSONArray()).put("traits",JSONArray()).put("arena","Ground")) }
    }).toString())
    private fun droids()=BadgeDefinition("droids","Duo inséparable","Histoires","Trilogie originale","Deux droïdes",0,
        listOf(BadgeRequirement("R2-D2",listOf("SOR-236"),listOf("R2-D2")),BadgeRequirement("C-3PO",listOf("SOR-238"),listOf("C-3PO"))),"",2,emptyList(),listOf("SOR-236"))
    private fun book()=BadgeCatalogue(listOf(droids()),emptyMap())
    private fun evaluate()=db.awardBadges(BadgeEngine.evaluate(book(),listOf(r2,c3,r2alt),db.allCards(),details(),db.badgeEventCounts(),db.badgeAwards()))
    @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("swu_collection.db");db=CollectionDatabase(context)}
    @After fun cleanup(){db.close();context.deleteDatabase("swu_collection.db")}

    @Test fun variantsNeverSubstituteForTheOtherCharacterAndAwardsPersistAfterTradeAndRestart(){
        db.addOrIncrement(r2.name,"SOR","236","Normale")
        db.addOrIncrement(r2.name,"SOR","400","Hyperspace Foil")
        assertEquals(1,evaluate().single().current)
        assertTrue(db.badgeAwards().isEmpty())
        db.addOrIncrement(c3.name,"SOR","238","Foil")
        val award=evaluate().single().award!!
        assertEquals(2,award.evidence.size)
        db.markBadgesSeen(listOf("droids"))
        db.allCards().forEach{db.changeQuantity(it.id,-it.quantity)}
        db.close();db=CollectionDatabase(context)
        val after=evaluate().single()
        assertTrue(after.earned);assertTrue(after.award!!.seen);assertEquals(award.time,after.award.time)
        assertEquals(2,after.award.evidence.size)
    }
    @Test fun aCorruptSharedFamilyCannotSubstituteAnotherNamedCharacter(){
        val imposter=r2alt.copy(name="Autre personnage — Autre histoire")
        val owned=listOf(CollectionCard(1,imposter.name,"SOR","400","Normale",1),CollectionCard(2,c3.name,"SOR","238","Normale",1))
        val result=BadgeEngine.evaluate(book(),listOf(r2,c3,imposter),owned,details(),emptyMap(),emptyMap())
        assertEquals(1,result.single().current)
    }
    @Test fun scannerCountsOnlyCommittedActionsAndBatchReplayIsIdempotent(){
        assertTrue(db.badgeEventCounts().isEmpty())
        db.addOrIncrement("Ajout manuel","SOR","236","Normale")
        assertTrue(db.badgeEventCounts().isEmpty())
        val entries=listOf(ScanBatchEntry(r2,"Normale",3),ScanBatchEntry(r2alt,"Hyperspace",2),ScanBatchEntry(c3,"Foil",1))
        db.commitScanBatch("batch-one",entries,details());db.commitScanBatch("batch-one",entries,details())
        assertEquals(6L,db.badgeEventCounts()["scan"])
        assertEquals(2L,db.badgeEventCounts()["scan_unique"])
        assertEquals(7,db.allCards().sumOf{it.quantity})
        db.addScannedCard(r2,"Foil","r2")
        assertEquals(7L,db.badgeEventCounts()["scan"]);assertEquals(2L,db.badgeEventCounts()["scan_unique"])
    }
    @Test fun futureVariantsWaitForVerifiedFamiliesAndTheirScansAreCreditedExactlyOnceLater(){
        val future=r2.copy(setCode="NEW",number="001")
        val alternate=future.copy(number="300")
        val catalog=listOf(r2,c3,future,alternate)
        db.addScannedCard(future,"Normale","unknown:NEW:001")
        db.addScannedCard(alternate,"Hyperspace Foil","unknown:NEW:300")
        assertEquals(2L,db.badgeEventCounts()["scan"])
        assertNull(db.badgeEventCounts()["scan_unique"])
        assertEquals(2L,db.badgeEventCounts()["scan_pending"])
        val badge=BadgeDefinition("unique","Diversité","Collection","Collection","Familles vérifiées",1,emptyList(),"families",2,emptyList(),emptyList())
        val evaluated=BadgeEngine.evaluate(BadgeCatalogue(listOf(badge),emptyMap()),catalog,db.allCards(),details(),db.badgeEventCounts(),emptyMap())
        assertEquals(0,evaluated.single().current)
        val beforeMapping=db.exportJson()
        val known=CardDetails.parse(JSONObject().put("format","SWU_CARD_DETAILS").put("version",1).put("cards",JSONObject().apply {
            listOf("NEW:001","NEW:300").forEach { key ->put(key,JSONObject().put("family","same-future-card").put("en","R2-D2")
                .put("cost",2).put("aspects",JSONArray()).put("traits",JSONArray()).put("arena","Ground")) }
        }).toString())
        db.resolvePendingBadgeScans(catalog,known)
        assertEquals(1L,db.badgeEventCounts()["scan_unique"])
        assertNull(db.badgeEventCounts()["scan_pending"])
        db.importJson(beforeMapping,true)
        db.resolvePendingBadgeScans(catalog,known)
        assertEquals(1L,db.badgeEventCounts()["scan_unique"])
        assertEquals(2L,db.badgeEventCounts()["scan"])
    }
    @Test fun globalBackupRestoresBadgesEventsAndInventoryWithoutRecountingAReplay(){
        db.addScannedCard(r2,"Hyperspace Foil","r2");db.addScannedCard(c3,"Foil","c3");evaluate()
        db.recordBadgeEvent("trade","same-comparison");db.markBadgesSeen(listOf("droids"))
        val backup=db.exportJson();validateBackup(backup)
        db.close();context.deleteDatabase("swu_collection.db");db=CollectionDatabase(context)
        db.importJson(backup,true)
        assertEquals(backup,db.exportJson())
        db.importJson(backup,true)
        assertEquals(2L,db.badgeEventCounts()["scan"]);assertEquals(1L,db.badgeEventCounts()["trade"])
        assertTrue(db.badgeAwards()["droids"]!!.seen)
        // Old backups still restore inventory without deleting newer permanent awards.
        val old=JSONObject(backup).put("version",5);old.remove("badges")
        db.importJson(old.toString(),true)
        assertTrue(db.badgeAwards().containsKey("droids"))
    }
    @Test fun malformedBadgeDataIsRejectedBeforeAnyCollectionMutation(){
        db.addOrIncrement(r2.name,"SOR","236","Normale")
        val before=db.exportJson()
        val bad=JSONObject(before)
        bad.getJSONObject("badges").getJSONArray("events").put(JSONObject().put("kind","scan").put("key","bad").put("amount",1.5))
        assertTrue(runCatching{db.importJson(bad.toString(),true)}.isFailure)
        assertEquals(before,db.exportJson())
    }
    @Test fun versionNineUpgradePreservesCollectionAndCreatesBadgeTables(){
        db.addOrIncrement(r2.name,"SOR","236","Foil");db.close()
        val file=context.getDatabasePath("swu_collection.db")
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use{old ->
            old.execSQL("DROP TABLE badge_awards");old.execSQL("DROP TABLE badge_events");old.version=9
        }
        db=CollectionDatabase(context)
        assertEquals("Foil",db.allCards().single().variant)
        assertTrue(db.badgeAwards().isEmpty());db.recordBadgeEvent("backup","verified")
        assertEquals(1L,db.badgeEventCounts()["backup"])
    }
    @Test fun sagaUnlocksFromChaptersEarnedPreviouslyEvenWhenCardsWereTraded(){
        val saga=BadgeDefinition("saga","Saga","Sagas","Sagas","Un parcours",4,emptyList(),"",1,listOf("droids"),listOf("SOR-236"))
        val earned=BadgeAward("droids",1,true,listOf("SOR-236","SOR-238"))
        val result=BadgeEngine.evaluate(BadgeCatalogue(listOf(droids(),saga),emptyMap()),listOf(r2,c3),emptyList(),details(),emptyMap(),mapOf("droids" to earned))
        assertEquals(1,result.last().current);assertTrue(result.last().available)
    }
    @Test fun classicSetTargetsStayFrozenAndRespectLeaderOneAndUnitThree(){
        val leader=r2.copy(number="001",type="Leader")
        val badge=BadgeDefinition("playset","Playset","Collection","Collection","Playset",3,emptyList(),"playsets",1,emptyList(),emptyList())
        val b=BadgeCatalogue(listOf(badge),mapOf("SOR" to listOf("SOR-001","SOR-238")))
        val owned=listOf(CollectionCard(1,leader.name,"SOR","001","Normale",1),CollectionCard(2,c3.name,"SOR","238","Normale",3))
        val result=BadgeEngine.evaluate(b,listOf(leader,c3,r2),owned,details(),emptyMap(),emptyMap())
        assertEquals(1,result.single().current)
        assertEquals(0,BadgeEngine.evaluate(b,listOf(leader,c3,r2),owned.map{it.copy(variant="Foil")},details(),emptyMap(),emptyMap()).single().current)
    }
    @Test fun bundledCatalogueHasSeventyOneDefinitionsAndAllIllustrationsAreRealCards(){
        val b=BadgeCatalogue.load(context)
        val details=CardDetails.load(context)
        val ventress=CatalogCard("HMW","001","Asajj Ventress","Leader","")
        val omega=CatalogCard("HMW","006","Omega","Leader","")
        assertTrue(details.get(ventress)!!.englishName.startsWith("Asajj Ventress"))
        assertNotEquals(details.family(ventress),details.family(omega))
        assertEquals(71,b.definitions.size);assertEquals(36,b.definitions.count{it.category=="Histoires"});assertEquals(7,b.definitions.count{it.category=="Sagas"})
        val catalog=JSONObject(context.assets.open("catalog-fr.json").bufferedReader().use{it.readText()}).getJSONArray("cards")
        val refs=(0 until catalog.length()).map{catalog.getJSONObject(it)}.associateBy{it.getString("s")+"-"+it.getString("n").padStart(3,'0')}
        b.definitions.forEach{d -> assertTrue(d.title,d.art.all{it in refs});d.requirements.forEach{r -> assertTrue(r.label,r.refs.all{it in refs})}}
        assertEquals("Qui-Gon Jinn",refs[b.definitions.first{it.id=="fates"}.requirements.first().refs.first()]!!.getString("name"))
        assertEquals("Ahsoka Tano",refs[b.definitions.first{it.id=="clone_heroes"}.requirements[1].refs.first()]!!.getString("name"))
        val dagobah=b.definitions.first{it.id=="dagobah"}
        assertEquals("Red Cinq",refs[dagobah.requirements.first{it.label.contains("X-wing")}.refs.single()]!!.getString("name"))
    }
}
