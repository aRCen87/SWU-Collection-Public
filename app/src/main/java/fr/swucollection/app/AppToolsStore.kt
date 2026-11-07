package fr.swucollection.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

internal fun toolInt(j: JSONObject, name: String, range: IntRange): Int {
    val value=j.get(name);require(value is Int || value is Long) { "Nombre entier attendu : $name" }
    val n=(value as Number).toLong();require(n>=range.first && n<=range.last);return n.toInt()
}
internal fun createAppTools(db: SQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS app_tools (name TEXT PRIMARY KEY, payload TEXT NOT NULL)") }
internal fun CollectionDatabase.tool(name: String): JSONObject? = readableDatabase.rawQuery("SELECT payload FROM app_tools WHERE name=?",arrayOf(name)).use { if(it.moveToFirst()) JSONObject(it.getString(0)) else null }
internal fun CollectionDatabase.putTool(name: String, value: JSONObject) {
    validateAppTool(name,value)
    check(writableDatabase.insertWithOnConflict("app_tools",null,ContentValues().apply { put("name",name); put("payload",value.toString()) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
}
internal fun validateAppTool(name: String, value: JSONObject) {
    require(value.toString().length<=2_000_000)
    when(name) {
        "printGoals" -> readPrintGoals(value)
        "counter" -> CounterSession.read(value)
        "loans" -> LoanBook.read(value)
        "matches" -> { val a=value.getJSONArray("items"); require(a.length()<=50); for(i in 0 until a.length()) {
            val r=a.getJSONObject(i); require(r.getLong("date")>=0 && r.getLong("duration")>=0)
            val p=r.getJSONArray("players");val s=r.getJSONArray("scores"); require(p.length()==2 && s.length()==2)
            for(n in 0..1) require(p.getString(n).length in 1..40 && s.getInt(n) in 0..99)
        } }
        else -> error("Outil de sauvegarde inconnu.")
    }
}
internal fun validateTools(tools: JSONObject) { require(tools.length()<=4);tools.keys().forEach { validateAppTool(it,tools.getJSONObject(it)) } }
internal fun CollectionDatabase.exportTools() = JSONObject().also { root -> readableDatabase.rawQuery("SELECT name,payload FROM app_tools ORDER BY name",null).use { while(it.moveToNext()) root.put(it.getString(0),JSONObject(it.getString(1))) } }
internal fun CollectionDatabase.importTools(root: JSONObject?, replace: Boolean) {
    root?.let(::validateTools)
    if(root==null) { if(replace) writableDatabase.delete("app_tools",null,null); return }
    if(replace) writableDatabase.delete("app_tools",null,null)
    root.keys().forEach { name ->
        if(replace || tool(name)==null) putTool(name,root.getJSONObject(name))
        // Import without replacement never merges loan quantities or ongoing matches.
    }
}
