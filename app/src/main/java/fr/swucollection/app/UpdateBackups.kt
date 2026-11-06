package fr.swucollection.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

internal object UpdateBackups {
    private fun file(context: Context) = File(context.filesDir, "update-backups/collection-before-update.json")
    fun exists(context: Context) = file(context).exists() || File(file(context).path + ".bak").exists()
    fun read(context: Context): String = AtomicFile(file(context)).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Suppress("DEPRECATION")
    fun create(context: Context, database: CollectionDatabase) {
        val root = JSONObject(database.exportJson())
            .put("savedAt", System.currentTimeMillis())
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
            .put("settings", JSONObject().put("leaderView",
                context.getSharedPreferences("leader_view", Context.MODE_PRIVATE).getString("mode", "flip")))
        if (context.packageName == AppChannel.TEST_PACKAGE) {
            root.put("homeLayout", JSONObject(HomeLayoutStore.json(HomeLayoutStore(context).read())))
            root.put("deckPins", org.json.JSONArray(DeckPins(context).all()))
        }
        val target = file(context)
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Impossible de créer la sauvegarde. Installation annulée." }
        val atomic = AtomicFile(target)
        val text = root.toString(2)
        validateBackup(text)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        check(read(context) == text) { "La sauvegarde n’a pas pu être vérifiée. Installation annulée." }
    }

    fun restore(context: Context, database: CollectionDatabase): Int {
        val json = read(context)
        val root = JSONObject(json)
        val mode = root.optJSONObject("settings")?.optString("leaderView")
        require(mode == null || mode in listOf("flip", "stack")) { "Réglage de sauvegarde invalide." }
        root.optJSONObject("homeLayout")?.let { HomeLayoutStore.parse(it.toString()) }
        val count = database.importJson(json, replace = true)
        if (context.packageName == AppChannel.TEST_PACKAGE) {
            root.optJSONObject("homeLayout")?.let { HomeLayoutStore(context).write(HomeLayoutStore.parse(it.toString())) }
            root.optJSONArray("deckPins")?.let { pins -> DeckPins(context).restore((0 until pins.length()).map { pins.getString(it) }, DeckStore(context).all().map { it.id }.toSet()) }
        }
        if (mode != null) context.getSharedPreferences("leader_view", Context.MODE_PRIVATE)
            .edit().putString("mode", mode).apply()
        return count
    }
}
