package fr.swucollection.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class DeckDraft(val name: String, val deck: PortableDeck, val leaderChosen: Boolean, val baseChosen: Boolean,
    val collectionEntries: List<ScanBatchEntry> = emptyList(), val collectionBatchId: String = "")
/** Drafts are independent of saved decks and inventory. Atomic writes keep the previous draft on failure. */
internal class DeckDraftStore(context: Context) {
    private val directory = File(context.filesDir, "deck-drafts")
    private fun file(key: String): AtomicFile {
        check(directory.exists() || directory.mkdirs())
        val id = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$id.json"))
    }
    fun read(key: String, baseline: String): DeckDraft? {
        val f = file(key)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return null
        return runCatching {
            val text = f.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                val result = reader.readText(); require(result.length <= 1_000_000); result
            }
            val root = JSONObject(text)
            if (root.getString("baseline") != baseline) return null
            val name = root.getString("name"); require(name.length <= 120)
            val entries = DeckScan.collectionFromJson(root.optJSONArray("collectionEntries"))
            val batchId = root.optString("collectionBatchId", "")
            require(entries.isEmpty() || batchId.isNotBlank())
            DeckDraft(name, DeckInterchange.importJson(root.getJSONObject("deck").toString()), root.getBoolean("leaderChosen"), root.getBoolean("baseChosen"), entries, batchId)
        }.getOrNull()
    }
    /** New decks have separate keys, so starting another cannot overwrite a draft. */
    fun newDrafts(): List<Pair<String, DeckDraft>> {
        val keys = directory.listFiles().orEmpty().filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            .mapNotNull { source -> runCatching {
                val atomic = AtomicFile(File(source.path.removeSuffix(".bak")))
                val root = atomic.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                root.optString("key").takeIf { it.startsWith("new:") }
            }.getOrNull() }.distinct() + "new" // Keep the legacy draft from earlier versions visible.
        return keys.mapNotNull { key -> runCatching {
            val root = file(key).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            read(key, root.getString("baseline"))?.let { key to it }
        }.getOrNull() }
    }
    fun hasNewDraft(): Boolean = newDrafts().isNotEmpty()
    fun write(key: String, baseline: String, value: DeckDraft) {
        require(value.name.length <= 120)
        val bytes = JSONObject().put("key", key).put("baseline", baseline).put("name", value.name)
            .put("deck", JSONObject(DeckInterchange.exportJson(value.deck, value.name)))
            .put("leaderChosen", value.leaderChosen).put("baseChosen", value.baseChosen)
            .put("collectionEntries", DeckScan.collectionJson(value.collectionEntries))
            .put("collectionBatchId", value.collectionBatchId).toString().toByteArray(Charsets.UTF_8)
        val atomic = file(key); val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch (error: Throwable) { atomic.failWrite(stream); throw error }
        check(read(key, baseline) == value) { "Brouillon non vérifié après écriture" }
    }
    fun clear(key: String) {
        val atomic = file(key)
        atomic.delete()
        check(!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) { "Suppression du brouillon impossible" }
    }
}


