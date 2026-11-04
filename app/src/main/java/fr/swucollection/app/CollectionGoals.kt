package fr.swucollection.app

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

internal fun CollectionDatabase.goals(): Map<String,Boolean> {
    val result=mutableMapOf<String,Boolean>()
    readableDatabase.rawQuery("SELECT set_code,playset FROM collection_goals",null).use { c ->while(c.moveToNext()) result[c.getString(0)]=c.getInt(1)!=0 }
    return result
}
internal fun CollectionDatabase.setGoal(code: String, playset: Boolean, acceptVariants: Boolean? = null) {
    require(code.matches(Regex("[A-Z0-9]{2,12}")))
    writableDatabase.insertWithOnConflict("collection_goals",null,ContentValues().apply {
        put("set_code",code);put("playset",if(playset)1 else 0)
        put("accept_variants", if (acceptVariants ?: (goalVariants()[code] ?: false)) 1 else 0)
    },android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
}
internal fun goalsJson(goals: Map<String,Boolean>, variants: Map<String,Boolean> = emptyMap())=JSONArray().apply { goals.forEach { (code,playset)->put(JSONObject().put("set",code).put("playset",playset).put("acceptVariants",variants[code] ?: false)) } }
internal fun validateGoals(rows: JSONArray) {
    require(rows.length()<=100)
    val seen=mutableSetOf<String>()
    for(i in 0 until rows.length()) { val row=rows.getJSONObject(i);val code=row.getString("set")
        require(code.matches(Regex("[A-Z0-9]{2,12}"))&&seen.add(code)&&row.get("playset") is Boolean)
        require(!row.has("acceptVariants") || row.get("acceptVariants") is Boolean)
    }
}


internal fun CollectionDatabase.goalVariants(): Map<String, Boolean> {
    val result = mutableMapOf<String, Boolean>()
    readableDatabase.rawQuery("SELECT set_code,accept_variants FROM collection_goals", null).use { c ->
        while (c.moveToNext()) result[c.getString(0)] = c.getInt(1) != 0
    }
    return result
}
