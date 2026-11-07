package fr.swucollection.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class CollectionCard(
    val id: Long,
    val name: String,
    val setCode: String,
    val number: String,
    val variant: String,
    val quantity: Int,
    val language: String = "FR"
)

data class CatalogSet(
    val code: String,
    val name: String,
    val releaseDate: String,
    val cardCount: Int
)

data class CatalogCard(
    val setCode: String,
    val number: String,
    val name: String,
    val type: String,
    val rarity: String,
    val imageUrl: String = "",
    val backImageUrl: String = "",
    val language: String = "FR"
)

class CollectionDatabase(context: Context) :
    SQLiteOpenHelper(context, "swu_collection.db", null, 12) {

    private val appContext = context.applicationContext
    private val chronology = runCatching {
        context.applicationContext.assets.open("set-release-dates.json").bufferedReader(Charsets.UTF_8)
            .use { CatalogChronology.fromJson(it.readText()) }
    }.getOrElse { CatalogChronology(emptyMap()) }

    override fun onCreate(db: SQLiteDatabase) {
        createAppTools(db)
        createBadgeTables(db)
        createDeckTables(db)
        createHistoryTables(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_batches (id TEXT PRIMARY KEY)")
        createCardListTables(db)
        createCollectionTable(db)
        createCatalogTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 12) createAppTools(db)
        if (oldVersion < 11) migrateCollectionLanguages(db)
        if (oldVersion < 10) createBadgeTables(db)
        if (oldVersion < 9) createDeckTables(db)
        if (oldVersion < 7) createHistoryTables(db)
        if (oldVersion == 7) db.execSQL("ALTER TABLE collection_goals ADD COLUMN accept_variants INTEGER NOT NULL DEFAULT 0 CHECK(accept_variants IN (0,1))")
        if (oldVersion < 6) db.execSQL("CREATE TABLE IF NOT EXISTS scan_batches (id TEXT PRIMARY KEY)")
        if (oldVersion < 5) createCardListTables(db)
        if (oldVersion < 2) createCatalogTables(db)
        if (oldVersion >= 2 && oldVersion < 3) db.execSQL("ALTER TABLE catalog_cards ADD COLUMN image_url TEXT NOT NULL DEFAULT ''")
        if (oldVersion >= 2 && oldVersion < 4) db.execSQL("ALTER TABLE catalog_cards ADD COLUMN back_image_url TEXT NOT NULL DEFAULT ''")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) runCatching { importLegacyDecks(appContext, db) }
            .onFailure { android.util.Log.w("SWUDecks", "Les anciens decks restent dans leur fichier de récupération.") }
    }

    private fun createCollectionTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS collection_cards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                set_code TEXT NOT NULL,
                card_number TEXT NOT NULL,
                variant TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                language TEXT NOT NULL DEFAULT 'FR' CHECK(language IN ('FR','EN','DE','ES','IT','UND')),
                UNIQUE(set_code, card_number, variant, language)
            )
        """.trimIndent())
    }

    /** Preserve every row ID and quantity; old copies have no reliable recorded language. */
    private fun migrateCollectionLanguages(db: SQLiteDatabase) {
        val columns = db.rawQuery("PRAGMA table_info(collection_cards)", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }
        if ("language" in columns) return
        db.execSQL("ALTER TABLE collection_cards RENAME TO collection_cards_before_language")
        createCollectionTable(db)
        db.execSQL("INSERT INTO collection_cards(id,name,set_code,card_number,variant,quantity,language) SELECT id,name,set_code,card_number,variant,quantity,'UND' FROM collection_cards_before_language")
        db.execSQL("DROP TABLE collection_cards_before_language")
    }

    fun changeLanguage(id: Long, language: String, quantity: Int? = null) = withHistory("Correction de la langue") {
        require(CardLanguages.valid(language))
        val row = allCards().firstOrNull { it.id == id } ?: error("Carte introuvable.")
        val count = quantity ?: row.quantity
        require(count in 1..row.quantity) { "Quantité à répartir invalide." }
        if (row.language != language) {
            val target = allCards().firstOrNull { it.setCode == row.setCode && it.number == row.number && it.variant == row.variant && it.language == language }
            if (target == null && count == row.quantity) {
                writableDatabase.execSQL("UPDATE collection_cards SET language=? WHERE id=?", arrayOf(language,id))
            } else {
                require((target?.quantity ?: 0).toLong() + count <= 9999) { "La fusion dépasserait 9 999 exemplaires." }
                if (target == null) writableDatabase.insertOrThrow("collection_cards",null,ContentValues().apply {
                    put("name",row.name);put("set_code",row.setCode);put("card_number",row.number)
                    put("variant",row.variant);put("quantity",count);put("language",language)
                }) else writableDatabase.execSQL("UPDATE collection_cards SET quantity=quantity+? WHERE id=?", arrayOf(count,target.id))
                if (count == row.quantity) writableDatabase.delete("collection_cards","id=?",arrayOf(row.id.toString()))
                else writableDatabase.execSQL("UPDATE collection_cards SET quantity=quantity-? WHERE id=?",arrayOf(count,row.id))
            }
        }
    }

    private fun createCatalogTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS catalog_sets (
                set_code TEXT PRIMARY KEY,
                set_name TEXT NOT NULL,
                release_date TEXT NOT NULL DEFAULT '',
                card_count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS catalog_cards (
                set_code TEXT NOT NULL,
                card_number TEXT NOT NULL,
                card_name TEXT NOT NULL,
                card_type TEXT NOT NULL DEFAULT '',
                rarity TEXT NOT NULL DEFAULT '',
                image_url TEXT NOT NULL DEFAULT '',
                back_image_url TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(set_code, card_number),
                FOREIGN KEY(set_code) REFERENCES catalog_sets(set_code)
                    ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalog_cards_name ON catalog_cards(card_name)")
    }

    fun allCards(): List<CollectionCard> {
        val result = mutableListOf<CollectionCard>()
        readableDatabase.rawQuery(
            "SELECT id,name,set_code,card_number,variant,quantity,language FROM collection_cards ORDER BY set_code, CAST(card_number AS INTEGER)",
            null
        ).use { c ->
            while (c.moveToNext()) result += CollectionCard(
                c.getLong(0), c.getString(1), c.getString(2),
                c.getString(3), c.getString(4), c.getInt(5), c.getString(6)
            )
        }
        return result
    }

    fun addOrIncrement(name: String, set: String, number: String, variant: String, language: String = "FR"): Unit = withHistory("Ajout d’une carte") {
        val db = writableDatabase
        require(CardLanguages.valid(language))
        val previous = db.rawQuery("SELECT quantity FROM collection_cards WHERE set_code=? AND card_number=? AND variant=? AND language=?",
            arrayOf(set.trim().uppercase(),number.trim(),variant,language)).use { if(it.moveToFirst()) it.getLong(0) else 0L }
        require(previous < 9999) { "Maximum 9 999 exemplaires pour cette langue et cette finition." }
        val values = ContentValues().apply {
            put("language", language)
            put("name", name.trim())
            put("set_code", set.trim().uppercase())
            put("card_number", number.trim())
            put("variant", variant)
            put("quantity", 1)
        }
        val inserted = db.insertWithOnConflict(
            "collection_cards", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        if (inserted == -1L) {
            db.execSQL(
                "UPDATE collection_cards SET quantity = quantity + 1, name = ? WHERE set_code = ? AND card_number = ? AND variant = ? AND language = ?",
                arrayOf(name.trim(), set.trim().uppercase(), number.trim(), variant, language)
            )
        }
    }

    fun addScannedCard(card: CatalogCard, variant: String, family: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            addOrIncrement(card.name, card.setCode, card.number, variant, card.language)
            recordBadgeEvent("scan", java.util.UUID.randomUUID().toString())
            recordScannedBadgeFamily(card, family)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        BadgeChanges.notifyChanged()
    }

    fun allCatalogSets(): List<CatalogSet> {
        val result = mutableListOf<CatalogSet>()
        readableDatabase.rawQuery(
            "SELECT set_code,set_name,release_date,card_count FROM catalog_sets",
            null
        ).use { c ->
            while (c.moveToNext()) result += CatalogSet(
                c.getString(0), c.getString(1), c.getString(2), c.getInt(3)
            )
        }
        return chronology.newestFirst(result)
    }

    fun catalogCardsForSet(setCode: String): List<CatalogCard> {
        val result = mutableListOf<CatalogCard>()
        readableDatabase.rawQuery(
            "SELECT set_code,card_number,card_name,card_type,rarity,image_url,back_image_url FROM catalog_cards WHERE set_code = ? ORDER BY CAST(card_number AS INTEGER),card_number",
            arrayOf(setCode.uppercase())
        ).use { c ->
            while (c.moveToNext()) result += CatalogCard(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6)
            )
        }
        return result
    }

    // Catalogue updates replace catalogue data only; collection_cards is never touched.
    fun replaceCatalog(sets: List<CatalogSet>, cards: List<CatalogCard>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("catalog_cards", null, null)
            db.delete("catalog_sets", null, null)
            sets.forEach { set ->
                val values = ContentValues().apply {
                    put("set_code", set.code.uppercase())
                    put("set_name", set.name)
                    put("release_date", set.releaseDate)
                    put("card_count", set.cardCount)
                }
                db.insertOrThrow("catalog_sets", null, values)
            }
            cards.forEach { card ->
                val values = ContentValues().apply {
                    put("set_code", card.setCode.uppercase())
                    put("card_number", card.number)
                    put("card_name", card.name)
                    put("card_type", card.type)
                    put("rarity", card.rarity)
                    put("image_url", card.imageUrl)
                    put("back_image_url", card.backImageUrl)
                }
                db.insertOrThrow("catalog_cards", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun exportJson(): String {
        val db = readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val root = JSONObject().put("format", "SWU_COLLECTION_BACKUP").put("version", 8)
            val rows = JSONArray()
            allCards().forEach { card ->
                rows.put(JSONObject().put("name", card.name).put("setCode", card.setCode)
                    .put("number", card.number).put("variant", card.variant).put("quantity", card.quantity).put("language",card.language))
            }
            root.put("badges", exportBadges())
            root.put("tools", exportTools())
            root.put("cards", rows)
            root.put("lists", exportCardLists())
            root.put("decks", exportDecks())
            root.put("goals", goalsJson(goals(), goalVariants()))
            val result = root.toString(2)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun importJson(json: String, replace: Boolean = false): Int {
        validateBackup(json)
        val root = JSONObject(json)
        val lists = root.optJSONArray("lists") ?: JSONArray()
        val rows = root.getJSONArray("cards")
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            importTools(root.optJSONObject("tools"), replace)
            root.optJSONObject("badges")?.let { importBadges(it) }
            importCardLists(lists, replace)
            root.optJSONArray("decks")?.let { importDecks(it, replace) }
            root.optJSONArray("goals")?.let { goals ->
                if (replace) db.delete("collection_goals", null, null)
                for (i in 0 until goals.length()) { val g=goals.getJSONObject(i);setGoal(g.getString("set"),g.getBoolean("playset"),g.optBoolean("acceptVariants",false)) }
            }
            if (replace) db.delete("collection_cards", null, null)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val set = row.getString("setCode").trim().uppercase()
                val number = row.getString("number").trim()
                val variant = row.optString("variant", "Normale")
                val language = CardLanguages.read(row)
                val quantity = row.optInt("quantity", 1).coerceAtLeast(1)
                val values = ContentValues().apply {
                    put("name", row.getString("name").trim())
                    put("set_code", set)
                    put("card_number", number)
                    put("variant", variant)
                    put("language", language)
                    put("quantity", quantity)
                }
                val inserted = db.insertWithOnConflict("collection_cards", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (inserted == -1L) {
                    db.rawQuery("SELECT quantity FROM collection_cards WHERE set_code=? AND card_number=? AND variant=? AND language=?",
                        arrayOf(set, number, variant, language)).use { existing ->
                        check(existing.moveToFirst()) { "La carte n’a pas pu être restaurée." }
                        require(existing.getLong(0) + quantity <= 9999) { "La fusion dépasserait 9 999 exemplaires pour $set $number ($variant). Aucune carte n’a été importée." }
                    }
                    db.execSQL(
                    "UPDATE collection_cards SET quantity = quantity + ? WHERE set_code = ? AND card_number = ? AND variant = ? AND language = ?",
                    arrayOf(quantity, set, number, variant, language)
                )
                }
                count++
            }
            historyCheckpoint(if(replace) "Restauration de sauvegarde" else "Import de sauvegarde")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count
    }

    fun exportCsv(): String {
        val rows = allCards()
        val header = "Nom;Extension;Numero;Variante;Quantite;Langue"
        return (listOf(header) + rows.map { card ->
            listOf(card.name, card.setCode, card.number, card.variant, card.quantity.toString(), card.language)
                .joinToString(";") { "\"${it.replace("\"", "\"\"")}\"" }
        }).joinToString("\n")
    }

    fun changeQuantity(id: Long, delta: Int) = withHistory("Modification de quantité") {
        writableDatabase.execSQL(
            "UPDATE collection_cards SET quantity = MAX(0, quantity + ?) WHERE id = ?",
            arrayOf(delta, id)
        )
        writableDatabase.delete("collection_cards", "quantity <= 0", null)
    }
}







