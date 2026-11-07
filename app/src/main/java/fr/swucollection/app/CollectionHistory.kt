package fr.swucollection.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

internal data class CollectionEvent(val id: Long, val label: String, val time: Long,
    val changes: String, val undone: Boolean, val reversible: Boolean)
internal fun createHistoryTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS collection_history (id INTEGER PRIMARY KEY AUTOINCREMENT,label TEXT NOT NULL,created_at INTEGER NOT NULL,changes TEXT NOT NULL,undone INTEGER NOT NULL DEFAULT 0,reversible INTEGER NOT NULL DEFAULT 1)")
    db.execSQL("CREATE TABLE IF NOT EXISTS collection_goals (set_code TEXT PRIMARY KEY,playset INTEGER NOT NULL CHECK(playset IN (0,1)),accept_variants INTEGER NOT NULL DEFAULT 0 CHECK(accept_variants IN (0,1)))")
    db.execSQL("CREATE TABLE IF NOT EXISTS list_preferences (list_id TEXT PRIMARY KEY,format TEXT NOT NULL)")
}
private fun CollectionCard.historyKey() = "$setCode:$number:$variant:$language"
private fun CollectionCard.historyJson() = JSONObject().put("id",id).put("name",name).put("set",setCode)
    .put("number",number).put("variant",variant).put("quantity",quantity).put("language",language)
private fun JSONObject.historyCard() = CollectionCard(getLong("id"),getString("name"),getString("set"),getString("number"),getString("variant"),getInt("quantity"),CardLanguages.read(this))

internal fun <T> CollectionDatabase.withHistory(label: String, action: () -> T): T {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val before = allCards().associateBy { it.historyKey() }
        val result = action()
        val after = allCards().associateBy { it.historyKey() }
        val changes = JSONArray()
        (before.keys + after.keys).forEach { key ->
            if (before[key]?.copy(id=0) != after[key]?.copy(id=0)) changes.put(JSONObject()
                .put("before",before[key]?.historyJson() ?: JSONObject.NULL)
                .put("after",after[key]?.historyJson() ?: JSONObject.NULL))
        }
        if (changes.length() > 0) db.insertOrThrow("collection_history",null,ContentValues().apply {
            put("label",label); put("created_at",System.currentTimeMillis()); put("changes",changes.toString())
        })
        db.execSQL("DELETE FROM collection_history WHERE id NOT IN (SELECT id FROM collection_history ORDER BY id DESC LIMIT 100)")
        db.setTransactionSuccessful()
        return result
    } finally { db.endTransaction() }
}
internal fun CollectionDatabase.historyCheckpoint(label: String) {
    writableDatabase.insertOrThrow("collection_history",null,ContentValues().apply {
        put("label",label); put("created_at",System.currentTimeMillis()); put("changes","[]");put("reversible",0)
    })
}
internal fun CollectionDatabase.history(): List<CollectionEvent> {
    val events = mutableListOf<CollectionEvent>()
    readableDatabase.rawQuery("SELECT id,label,created_at,changes,undone,reversible FROM collection_history ORDER BY id DESC LIMIT 100",null).use { c ->
        while(c.moveToNext()) events += CollectionEvent(c.getLong(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4)!=0,c.getInt(5)!=0)
    }
    return events
}
internal fun CollectionDatabase.undoLastChange(): String {
    val db=writableDatabase; db.beginTransaction()
    try {
        val event=history().firstOrNull { !it.undone } ?: error("Aucune modification à annuler.")
        check(event.reversible) { "Les opérations antérieures à cette restauration ne peuvent plus être annulées." }
        val changes=JSONArray(event.changes)
        val now=allCards().associateBy { it.historyKey() }
        val pairs=(0 until changes.length()).map { i ->
            val row=changes.getJSONObject(i)
            row.optJSONObject("before")?.historyCard() to row.optJSONObject("after")?.historyCard()
        }
        pairs.forEach { (before,after) ->
            val key=(after ?: before ?: error("Historique invalide")).historyKey()
            check(now[key]?.copy(id=0)==after?.copy(id=0)) { "La collection a changé depuis cette opération : annulation refusée." }
        }
        val projected = now.toMutableMap()
        pairs.forEach { (before, after) -> val key = (after ?: before!!).historyKey(); if (before == null) projected.remove(key) else projected[key] = before }
        val reserved = loans().reserved()
        reserved.forEach { (key, quantity) -> check(projected.values.filter { loanKey(it) == key }.sumOf { it.quantity } >= quantity) { "Des exemplaires sont prêtés : enregistre leur retour avant cette annulation." } }
        pairs.forEach { (before,after) ->
            val row=after ?: before!!
            db.delete("collection_cards","set_code=? AND card_number=? AND variant=? AND language=?",arrayOf(row.setCode,row.number,row.variant,row.language))
        }
        // Language changes can retain the same row ID under a different unique key.
        // Remove all affected rows before restoring any IDs.
        pairs.mapNotNull { it.first }.forEach { before ->
            db.insertOrThrow("collection_cards",null,ContentValues().apply {
                put("id",before.id);put("name",before.name);put("set_code",before.setCode);put("card_number",before.number)
                put("variant",before.variant);put("quantity",before.quantity);put("language",before.language)
            })
        }
        db.update("collection_history",ContentValues().apply { put("undone",1) },"id=?",arrayOf(event.id.toString()))
        db.setTransactionSuccessful();return event.label
    } finally {db.endTransaction()}
}

