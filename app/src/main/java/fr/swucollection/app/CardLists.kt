package fr.swucollection.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.util.UUID

data class CardList(val id: String, val name: String, val entries: List<CardListEntry>, val format: String = "Liste")
data class CardListEntry(val card: CatalogCard, val quantity: Int)

internal fun createCardListTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS card_lists (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
    // No catalogue foreign key: a catalogue refresh must never remove a personal selection.
    db.execSQL("""CREATE TABLE IF NOT EXISTS card_list_entries (
        list_id TEXT NOT NULL, set_code TEXT NOT NULL, card_number TEXT NOT NULL,
        card_json TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99),
        PRIMARY KEY(list_id, set_code, card_number))""")
}

fun CollectionDatabase.cardLists(): List<CardList> {
    val result = mutableListOf<CardList>()
    readableDatabase.rawQuery("SELECT l.id,l.name,COALESCE(p.format,'Liste') FROM card_lists l LEFT JOIN list_preferences p ON p.list_id=l.id ORDER BY l.name COLLATE NOCASE,l.id", null).use { lists ->
        while (lists.moveToNext()) {
            val id = lists.getString(0)
            val entries = mutableListOf<CardListEntry>()
            readableDatabase.rawQuery("SELECT card_json,quantity FROM card_list_entries WHERE list_id=? ORDER BY set_code,CAST(card_number AS INTEGER),card_number", arrayOf(id)).use { rows ->
                while (rows.moveToNext()) entries += CardListEntry(cardFromJson(org.json.JSONObject(rows.getString(0))), rows.getInt(1))
            }
            result += CardList(id, lists.getString(1), entries, lists.getString(2))
        }
    }
    return result
}

fun CollectionDatabase.createCardList(name: String): String {
    require(name.trim().isNotEmpty() && name.trim().length <= 80)
    val id = UUID.randomUUID().toString()
    writableDatabase.insertOrThrow("card_lists", null, ContentValues().apply { put("id", id); put("name", name.trim()) })
    return id
}

fun CollectionDatabase.renameCardList(id: String, name: String) {
    require(name.trim().isNotEmpty() && name.trim().length <= 80)
    writableDatabase.update("card_lists", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id))
}

fun CollectionDatabase.deleteCardList(id: String) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("card_list_entries", "list_id=?", arrayOf(id))
        db.delete("card_lists", "id=?", arrayOf(id))
        db.delete("list_preferences", "list_id=?", arrayOf(id))
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

fun CollectionDatabase.putListCard(id: String, card: CatalogCard, quantity: Int = 1) {
    require(quantity in 0..99)
    val db = writableDatabase
    db.rawQuery("SELECT id FROM card_lists WHERE id=?", arrayOf(id)).use { require(it.moveToFirst()) }
    if (quantity == 0) {
        db.delete("card_list_entries", "list_id=? AND set_code=? AND card_number=?", arrayOf(id, card.setCode, card.number))
    } else db.insertWithOnConflict("card_list_entries", null, ContentValues().apply {
        put("list_id", id); put("set_code", card.setCode); put("card_number", card.number)
        put("card_json", cardToJson(card).toString()); put("quantity", quantity)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

internal fun cardToJson(card: CatalogCard) = org.json.JSONObject()
    .put("setCode", card.setCode).put("number", card.number).put("name", card.name)
    .put("type", card.type).put("rarity", card.rarity).put("imageUrl", card.imageUrl).put("backImageUrl", card.backImageUrl).put("language",card.language)

internal fun cardFromJson(row: org.json.JSONObject) = CatalogCard(row.getString("setCode"), row.getString("number"),
    row.getString("name"), row.optString("type"), row.optString("rarity"), row.optString("imageUrl"), row.optString("backImageUrl"), CardLanguages.read(row))

internal fun CollectionDatabase.exportCardLists(): JSONArray = JSONArray().apply {
    cardLists().forEach { list -> put(org.json.JSONObject().put("id", list.id).put("name", list.name).put("format",list.format)
        .put("entries", JSONArray().apply { list.entries.forEach { put(cardToJson(it.card).put("quantity", it.quantity)) } })) }
}

internal fun validateCardLists(lists: JSONArray) {
    require(lists.length() <= 10000) { "Trop de listes dans la sauvegarde." }
    val ids = mutableSetOf<String>()
    for (i in 0 until lists.length()) {
        val list = lists.getJSONObject(i)
        require(list.getString("id").isNotBlank() && ids.add(list.getString("id")))
        require(list.getString("name").trim().length in 1..80)
        require(list.optString("format","Liste") in listOf("Liste","Premier","Twin Suns","Éternel"))
        val entries = list.getJSONArray("entries")
        require(entries.length() <= 100000)
        val keys = mutableSetOf<Pair<String, String>>()
        for (j in 0 until entries.length()) {
            val entry = entries.getJSONObject(j)
            val card = cardFromJson(entry)
            require(card.name.isNotBlank() && card.setCode.isNotBlank() && card.number.isNotBlank())
            backupInteger(entry, "quantity", 0, 1..99)
            require(keys.add(card.setCode to card.number))
        }
    }
}

internal fun CollectionDatabase.importCardLists(lists: JSONArray, replace: Boolean) {
    val db = writableDatabase
    if (replace) { db.delete("card_list_entries", null, null); db.delete("card_lists", null, null);db.delete("list_preferences",null,null) }
    for (i in 0 until lists.length()) {
        val list = lists.getJSONObject(i)
        val id = list.getString("id")
        val inserted = db.insertWithOnConflict("card_lists", null, ContentValues().apply {
            put("id", id); put("name", list.getString("name").trim())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        // Re-importing a backup must not duplicate lists or overwrite a more recent selection.
        if (inserted == -1L) continue
        setListFormat(id,list.optString("format","Liste"))
        val entries = list.getJSONArray("entries")
        for (j in 0 until entries.length()) {
            val entry = entries.getJSONObject(j)
            putListCard(id, cardFromJson(entry), entry.getInt("quantity"))
        }
    }
}


internal fun CollectionDatabase.setListFormat(id: String, format: String) {
    require(format in listOf("Liste","Premier","Twin Suns","Éternel"))
    writableDatabase.insertWithOnConflict("list_preferences",null,ContentValues().apply { put("list_id",id);put("format",format) },SQLiteDatabase.CONFLICT_REPLACE)
}
