package fr.swucollection.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/** Decks share the collection database so global restores are one SQLite transaction. */
internal class DeckStore(context: Context) : AutoCloseable {
    private val database = CollectionDatabase(context.applicationContext)
    val writableDatabase get() = database.writableDatabase
    private val readableDatabase get() = database.readableDatabase
    data class SavedDeck(val id: String, val name: String, val format: String,
                         val deck: PortableDeck, val updatedAt: Long)
    override fun close() = database.close()

    fun save(name: String, format: String, deck: PortableDeck, id: String = UUID.randomUUID().toString()): String {
        require(name.isNotBlank() && name.length <= 120)
        require(format.isNotBlank() && format.length <= 50)
        val values = ContentValues().apply {
            put("id", id)
            put("name", name.trim())
            put("format", format.trim())
            put("payload", DeckInterchange.exportJson(deck, name))
            put("updated_at", System.currentTimeMillis())
        }
        check(writableDatabase.insertWithOnConflict("decks", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Impossible d’enregistrer le deck"
        }
        return id
    }

    fun all(): List<SavedDeck> {
        val result = mutableListOf<SavedDeck>()
        readableDatabase.rawQuery(
            "SELECT id,name,format,payload,updated_at FROM decks ORDER BY updated_at DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) result += SavedDeck(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                DeckInterchange.importJson(cursor.getString(3)), cursor.getLong(4)
            )
        }
        return result
    }

    fun delete(id: String): Boolean =
        writableDatabase.delete("decks", "id = ?", arrayOf(id)) > 0

    /** Deck and optional inventory additions are one transaction; a retry cannot credit the batch twice. */
    fun saveWithScans(name: String, format: String, deck: PortableDeck, id: String,
        batchId: String, entries: List<ScanBatchEntry>, details: CardDetails): String {
        val db = writableDatabase
        db.beginTransaction()
        val saved: String
        try {
            if (entries.isNotEmpty()) database.commitScanBatch(batchId, entries, details)
            saved = save(name, format, deck, id)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        BadgeChanges.notifyChanged()
        return saved
    }

    /** Standalone export; decks are also included in the global backup. */
    fun exportBackup(): String {
        val array = org.json.JSONArray()
        all().forEach { saved ->
            array.put(JSONObject().put("id", saved.id).put("name", saved.name)
                .put("format", saved.format).put("deck", JSONObject(DeckInterchange.exportJson(saved.deck))))
        }
        return JSONObject().put("format", "SWU_DECKS_BACKUP").put("version", 1)
            .put("decks", array).toString(2)
    }
}



