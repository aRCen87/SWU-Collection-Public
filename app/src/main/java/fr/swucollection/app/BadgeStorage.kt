package fr.swucollection.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal object BadgeChanges {
    val revision = MutableStateFlow(0L)
    @Synchronized fun notifyChanged() { revision.value += 1 }
}
internal fun createBadgeTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS badge_awards (id TEXT PRIMARY KEY,unlocked_at INTEGER NOT NULL,seen INTEGER NOT NULL DEFAULT 0,evidence TEXT NOT NULL DEFAULT '[]')")
    db.execSQL("CREATE TABLE IF NOT EXISTS badge_events (kind TEXT NOT NULL,event_key TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),PRIMARY KEY(kind,event_key))")
}
internal val badgeEventKinds = setOf("scan", "scan_unique", "scan_pending", "deck", "export", "proxy", "qr", "trade", "backup", "offline")
internal fun CollectionDatabase.recordBadgeEvent(kind: String, key: String, amount: Int = 1) {
    require(kind in badgeEventKinds && key.isNotBlank() && key.length <= 200 && amount in 1..1000000)
    val inserted = writableDatabase.insertWithOnConflict("badge_events", null, ContentValues().apply {
        put("kind",kind); put("event_key",key); put("amount",amount)
    }, SQLiteDatabase.CONFLICT_IGNORE)
    if (inserted != -1L) BadgeChanges.notifyChanged()
}
/** Unmapped future prints are retained, then credited once their family is verified. */
internal fun CollectionDatabase.recordScannedBadgeFamily(card: CatalogCard, family: String) {
    if(family.startsWith("unknown:")) recordBadgeEvent("scan_pending",DeckConstruction.reference(card))
    else recordBadgeEvent("scan_unique",badgeFingerprint(family))
}
internal fun CollectionDatabase.resolvePendingBadgeScans(catalog: List<CatalogCard>, details: CardDetails) {
    val byId = catalog.associateBy(DeckConstruction::reference)
    val pending = mutableListOf<String>()
    readableDatabase.rawQuery("SELECT event_key FROM badge_events WHERE kind='scan_pending'",null).use { c ->
        while(c.moveToNext()) pending += c.getString(0)
    }
    if(pending.isEmpty()) return
    val db=writableDatabase
    db.beginTransaction()
    try {
        pending.forEach { id -> byId[id]?.let { card -> details.get(card)?.let { detail ->
            recordBadgeEvent("scan_unique",badgeFingerprint(detail.family))
            db.delete("badge_events","kind='scan_pending' AND event_key=?",arrayOf(id))
        } } }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
internal fun recordBadgeAction(context: Context, kind: String, key: String = java.util.UUID.randomUUID().toString()) {
    CollectionDatabase(context.applicationContext).use { it.recordBadgeEvent(kind, key) }
}
internal fun CollectionDatabase.badgeEventCounts(): Map<String, Long> {
    val result = mutableMapOf<String, Long>()
    readableDatabase.rawQuery("SELECT kind,SUM(amount) FROM badge_events GROUP BY kind",null).use { c ->
        while(c.moveToNext()) result[c.getString(0)] = c.getLong(1)
    }
    return result
}
internal fun CollectionDatabase.badgeAwards(): Map<String, BadgeAward> {
    val result = mutableMapOf<String, BadgeAward>()
    readableDatabase.rawQuery("SELECT id,unlocked_at,seen,evidence FROM badge_awards",null).use { c ->
        while(c.moveToNext()) {
            val evidence = JSONArray(c.getString(3)).let { a -> (0 until a.length()).map { a.getString(it) } }
            result[c.getString(0)] = BadgeAward(c.getString(0),c.getLong(1),c.getInt(2)!=0,evidence)
        }
    }
    return result
}
internal fun CollectionDatabase.awardBadges(progress: List<BadgeProgress>): List<BadgeProgress> {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val now = System.currentTimeMillis()
        progress.filter { it.available && it.current >= it.target && !it.earned }.forEach { p ->
            db.insertWithOnConflict("badge_awards", null, ContentValues().apply {
                put("id",p.badge.id);put("unlocked_at",now);put("seen",0);put("evidence",JSONArray(p.evidence).toString())
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        val awards = badgeAwards()
        val result = progress.map { it.copy(award = awards[it.badge.id]) }
        db.setTransactionSuccessful()
        return result
    } finally { db.endTransaction() }
}
internal fun CollectionDatabase.markBadgesSeen(ids: List<String>) {
    val db = writableDatabase
    db.beginTransaction()
    try { ids.forEach { db.execSQL("UPDATE badge_awards SET seen=1 WHERE id=?",arrayOf(it)) };db.setTransactionSuccessful() }
    finally { db.endTransaction() }
}
internal fun CollectionDatabase.exportBadges(): JSONObject {
    val events = JSONArray()
    readableDatabase.rawQuery("SELECT kind,event_key,amount FROM badge_events ORDER BY kind,event_key",null).use { c ->
        while(c.moveToNext()) events.put(JSONObject().put("kind",c.getString(0)).put("key",c.getString(1)).put("amount",c.getLong(2)))
    }
    val awards = JSONArray()
    badgeAwards().values.sortedBy { it.id }.forEach { a -> awards.put(JSONObject().put("id",a.id).put("time",a.time)
        .put("seen",a.seen).put("evidence",JSONArray(a.evidence))) }
    return JSONObject().put("version",1).put("awards",awards).put("events",events)
}
internal fun validateBadgeBackup(root: JSONObject) {
    require(backupInteger(root,"version",1,1..1)==1)
    val awards = root.getJSONArray("awards");val events = root.getJSONArray("events")
    require(awards.length() <= 10000 && events.length() <= 100000)
    val ids = mutableSetOf<String>()
    for(i in 0 until awards.length()) {
        val a = awards.getJSONObject(i)
        require(a.opt("id") is String && a.getString("id").matches(Regex("[a-z0-9_]{1,80}")) && ids.add(a.getString("id")))
        val time = a.get("time");require((time is Long || time is Int) && (time as Number).toLong() > 0)
        require(a.opt("seen") is Boolean)
        val refs = a.badgeStrings("evidence");require(refs.size <= 100 && refs.all { it.matches(Regex("[A-Z0-9]{2,8}-[0-9]{3,4}")) })
    }
    val keys = mutableSetOf<String>()
    for(i in 0 until events.length()) {
        val e=events.getJSONObject(i)
        require(e.opt("kind") is String && e.getString("kind") in badgeEventKinds)
        require(e.opt("key") is String && e.getString("key").isNotBlank() && e.getString("key").length <= 200)
        require(e.getString("kind") != "scan_pending" || e.getString("key").matches(Regex("[A-Z0-9]{2,8}-[0-9]{3,4}")))
        require(keys.add(e.getString("kind")+":"+e.getString("key")))
        backupInteger(e,"amount",1,1..1000000)
    }
}
/** Monotonic merge: restoring a file cannot take away earned badges or replay event counts. */
internal fun CollectionDatabase.importBadges(root: JSONObject) {
    validateBadgeBackup(root)
    val db = writableDatabase
    val awards=root.getJSONArray("awards")
    for(i in 0 until awards.length()) {
        val a=awards.getJSONObject(i)
        db.insertWithOnConflict("badge_awards",null,ContentValues().apply {
            put("id",a.getString("id"));put("unlocked_at",a.getLong("time"));put("seen",if(a.getBoolean("seen"))1 else 0);put("evidence",a.getJSONArray("evidence").toString())
        },SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE badge_awards SET unlocked_at=MIN(unlocked_at,?),seen=MAX(seen,?) WHERE id=?",
            arrayOf(a.getLong("time"),if(a.getBoolean("seen"))1 else 0,a.getString("id")))
    }
    val events=root.getJSONArray("events")
    for(i in 0 until events.length()) {
        val e=events.getJSONObject(i)
        db.insertWithOnConflict("badge_events",null,ContentValues().apply {
            put("kind",e.getString("kind"));put("event_key",e.getString("key"));put("amount",e.getLong("amount"))
        },SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("UPDATE badge_events SET amount=MAX(amount,?) WHERE kind=? AND event_key=?",
            arrayOf(e.getLong("amount"),e.getString("kind"),e.getString("key")))
    }
}
