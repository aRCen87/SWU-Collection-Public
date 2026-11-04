package fr.swucollection.app

import android.content.Context
import android.net.Uri
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
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BackupReliabilityTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        db = CollectionDatabase(context)
        db.addOrIncrement("Éclairs de Force", "SOR", "138", "Foil")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("swu_collection.db") }

    @Test fun exportedFileIsReopenedAndRestoresExactlyAfterTheDatabaseIsReopened() {
        val before = db.exportJson()
        val file = File.createTempFile("verified-backup", ".json", context.cacheDir)
        writeVerifiedPortableBackup(context, Uri.fromFile(file), before)
        val restored = file.inputStream().use(::readPortableBackup)
        assertEquals(before, restored)
        db.addOrIncrement("Autre", "JTL", "788", "Hyperspace Foil")
        db.importJson(restored, true)
        db.close(); db = CollectionDatabase(context)
        assertEquals(before, db.exportJson())
    }

    @Test fun fractionalStringNullAndOverflowQuantitiesNeverChangeTheCollection() {
        val before = db.exportJson()
        listOf<Any>(1.5, "2", JSONObject.NULL, Long.MAX_VALUE).forEach { value ->
            val bad = JSONObject(before)
            bad.getJSONArray("cards").getJSONObject(0).put("quantity", value)
            assertTrue(runCatching { db.importJson(bad.toString(), true) }.isFailure)
            assertEquals(before, db.exportJson())
        }
        listOf<Any>(5.1, "5", JSONObject.NULL).forEach { version ->
            assertTrue(runCatching { db.importJson(JSONObject(before).put("version", version).toString(), true) }.isFailure)
            assertEquals(before, db.exportJson())
        }
    }

    @Test fun mergeOverflowRollsBackNewCardsAndListsAlreadyImportedInTheTransaction() {
        db.writableDatabase.execSQL("UPDATE collection_cards SET quantity=9999")
        val before = db.exportJson()
        val incoming = JSONObject(before)
        val row = incoming.getJSONArray("cards").getJSONObject(0)
        row.put("quantity", 1)
        incoming.put("cards", JSONArray().put(JSONObject().put("name", "Nouvelle")
            .put("setCode", "JTL").put("number", "788").put("quantity", 1)).put(row))
        incoming.put("lists", JSONArray().put(JSONObject().put("id", "new-list")
            .put("name", "À trouver").put("entries", JSONArray())))
        assertTrue(runCatching { db.importJson(incoming.toString(), false) }.isFailure)
        assertEquals(before, db.exportJson())
    }

    @Test fun listQuantitiesAreAlsoValidatedBeforeReplacingAnything() {
        val id = db.createCardList("Favoris été")
        db.putListCard(id, CatalogCard("SOR", "138", "Éclairs de Force", "Évènement", "Rare"), 3)
        val before = db.exportJson()
        listOf<Any>(1.5, "3", JSONObject.NULL).forEach { quantity ->
            val invalid = JSONObject(before)
            invalid.getJSONArray("lists").getJSONObject(0).getJSONArray("entries")
                .getJSONObject(0).put("quantity", quantity)
            assertTrue(runCatching { db.importJson(invalid.toString(), true) }.isFailure)
            assertEquals(before, db.exportJson())
        }
    }

    @Test fun truncatedOrInvalidUtf8FilesCannotBeAcceptedAsVerifiedBackups() {
        assertTrue(runCatching { validateBackup(db.exportJson().dropLast(5)) }.isFailure)
        assertTrue(runCatching { readPortableBackup(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }.isFailure)
        assertTrue(runCatching { readPortableBackup(ByteArrayInputStream(ByteArray(16 * 1024 * 1024 + 1))) }.isFailure)
    }
}
