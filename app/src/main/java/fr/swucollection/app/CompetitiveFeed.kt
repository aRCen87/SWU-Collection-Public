package fr.swucollection.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.time.Instant

internal data class MetaList(val id: String, val name: String, val eventId: String, val placement: Int?, val deck: PortableDeck)
internal data class MetaGroup(val leader: String, val base: String, val count: Int, val deckIds: List<String>)
internal data class MetaEvent(val id: String, val name: String, val date: String, val tier: String, val players: Int?, val deckIds: List<String>)
internal data class CompetitiveFeed(val updatedAt: String, val from: String, val to: String, val listCount: Int,
    val excludedCount: Int, val groups: List<MetaGroup>, val events: List<MetaEvent>, val decks: List<MetaList>) {
    companion object {
        fun parse(raw: String): CompetitiveFeed {
            require(raw.length <= 4_000_000)
            val root = JSONObject(raw)
            require(root.getString("format") == "SWU_COMPETITIVE_FEED" && root.getInt("version") == 1)
            val updated = root.getString("updatedAt"); Instant.parse(updated)
            fun ids(row: JSONObject): List<String> = row.getJSONArray("deckIds").let { a -> (0 until a.length()).map { a.getString(it) } }
            val decks = root.getJSONArray("decks").let { a -> (0 until a.length()).map { i ->
                val d = a.getJSONObject(i)
                MetaList(d.getString("id"), d.getString("name"), d.getString("tournamentId"),
                    if (d.has("placement") && !d.isNull("placement")) d.getInt("placement").also { require(it > 0) } else null,
                    DeckInterchange.importJson(d.getJSONObject("deck").toString()))
            } }
            val events = root.getJSONArray("events").let { a -> (0 until a.length()).map { i ->
                val e = a.getJSONObject(i)
                MetaEvent(e.getString("id"), e.getString("name"), e.getString("date"), e.getString("tier"),
                    if (e.isNull("players")) null else e.getInt("players"), ids(e))
            } }
            val groups = root.getJSONArray("groups").let { a -> (0 until a.length()).map { i ->
                val g = a.getJSONObject(i)
                MetaGroup(DeckInterchange.normalizeId(g.getString("leader")), DeckInterchange.normalizeId(g.getString("base")),
                    g.getInt("count").also { require(it > 0) }, ids(g))
            } }
            require(decks.isNotEmpty() && groups.isNotEmpty())
            require(decks.map { it.id }.distinct().size == decks.size && events.map { it.id }.distinct().size == events.size)
            val byId = decks.associateBy { it.id }; val eventIds = events.map { it.id }.toSet()
            require(decks.all { it.eventId in eventIds })
            require(events.all { e -> e.deckIds.all { byId[it]?.eventId == e.id } })
            require(groups.all { g -> g.deckIds.isNotEmpty() && g.deckIds.all { byId[it]?.deck?.let { d -> d.leader == g.leader && d.base == g.base } == true } })
            val count = root.getInt("listCount"); require(count >= groups.sumOf { it.count })
            return CompetitiveFeed(updated, root.getString("from"), root.getString("to"), count,
                root.getInt("excludedCount").also { require(it >= 0) }, groups, events, decks)
        }
    }
}

/** Public, curated snapshots. No API key or user's inventory is transmitted. */
internal class CompetitiveFeedStore(private val context: Context) {
    private val cache = AtomicFile(File(context.filesDir, "competitive-feed.json"))
    fun local(): CompetitiveFeed? = runCatching { CompetitiveFeed.parse(cache.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
        ?: runCatching { CompetitiveFeed.parse(context.assets.open("competitive-feed.json").bufferedReader().use { it.readText() }) }.getOrNull()

    fun needsRefresh(): Boolean = System.currentTimeMillis() - cache.baseFile.lastModified() !in 0L..21_600_000L

    fun refresh(): CompetitiveFeed {
        val c = URL("https://raw.githubusercontent.com/aRCen87/SWU-Collection-Public/competitive-data/competitive-feed.json").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "SWU-Collection-Android")
        try {
            require(c.responseCode == 200)
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= 4_000_000); out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            require(bytes.size <= 4_000_000)
            val raw = bytes.toString(Charsets.UTF_8)
            val feed = CompetitiveFeed.parse(raw)
            require(local()?.let { Instant.parse(feed.updatedAt) >= Instant.parse(it.updatedAt) } != false)
            val stream = cache.startWrite()
            try { stream.write(bytes); cache.finishWrite(stream) } catch (e: Exception) { cache.failWrite(stream); throw e }
            return feed
        } finally { c.disconnect() }
    }
}
