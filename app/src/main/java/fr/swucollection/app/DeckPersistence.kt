package fr.swucollection.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

internal fun createDeckTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS decks (
        id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, format TEXT NOT NULL,
        payload TEXT NOT NULL, updated_at INTEGER NOT NULL)""")
    db.execSQL("CREATE TABLE IF NOT EXISTS deck_import_state (id INTEGER PRIMARY KEY)")
}

private fun deckRows(db: SQLiteDatabase): JSONArray = JSONArray().apply {
    db.rawQuery("SELECT id,name,format,payload,updated_at FROM decks ORDER BY updated_at DESC,id", null).use { cursor ->
        while (cursor.moveToNext()) put(JSONObject().put("id", cursor.getString(0))
            .put("name", cursor.getString(1)).put("format", cursor.getString(2))
            .put("deck", JSONObject(cursor.getString(3))).put("updatedAt", cursor.getLong(4)))
    }
}

internal fun validateDeckRows(rows: JSONArray) {
    require(rows.length() <= 10000) { "Trop de decks dans la sauvegarde." }
    val ids = mutableSetOf<String>()
    for (i in 0 until rows.length()) {
        val row = rows.getJSONObject(i)
        require(row.getString("id").length in 1..200 && ids.add(row.getString("id"))) { "Identifiant de deck invalide ou répété." }
        require(row.getString("name").trim().length in 1..120) { "Nom de deck invalide." }
        require(row.getString("format").trim().length in 1..50) { "Format de deck invalide." }
        require(!row.has("updatedAt") || row.getLong("updatedAt") >= 0) { "Date de deck invalide." }
        DeckInterchange.importJson(row.getJSONObject("deck").toString())
    }
}

private fun importDeckRows(db: SQLiteDatabase, rows: JSONArray, replace: Boolean) {
    if (replace) db.delete("decks", null, null)
    for (i in 0 until rows.length()) {
        val row = rows.getJSONObject(i)
        val deck = DeckInterchange.importJson(row.getJSONObject("deck").toString())
        db.insertWithOnConflict("decks", null, ContentValues().apply {
            put("id", row.getString("id")); put("name", row.getString("name").trim())
            put("format", row.getString("format").trim())
            put("payload", DeckInterchange.exportJson(deck, row.getString("name")))
            put("updated_at", row.optLong("updatedAt", 0))
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }
}

internal fun CollectionDatabase.exportDecks(): JSONArray = deckRows(readableDatabase)

/** Called inside the collection restore transaction, after the entire file has been validated. */
internal fun CollectionDatabase.importDecks(rows: JSONArray, replace: Boolean) =
    importDeckRows(writableDatabase, rows, replace)

/** Migrate unpublished development decks once; retain the source file for recovery. */
internal fun importLegacyDecks(context: Context, db: SQLiteDatabase) {
    db.rawQuery("SELECT id FROM deck_import_state WHERE id=1", null).use { if (it.moveToFirst()) return }
    val source = context.getDatabasePath("swu_decks.db")
    val rows = if (source.exists()) SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY)
        .use(::deckRows) else JSONArray()
    validateDeckRows(rows)
    db.beginTransaction()
    try {
        importDeckRows(db, rows, false)
        db.execSQL("INSERT OR IGNORE INTO deck_import_state(id) VALUES(1)")
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
