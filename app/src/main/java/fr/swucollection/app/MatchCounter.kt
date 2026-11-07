package fr.swucollection.app

import android.view.View
import org.json.JSONArray
import org.json.JSONObject

internal enum class CounterStyle(val title: String) {
    GALAXY("Galaxie bleue"), SITH("Citadelle rouge"), JEDI("Temple émeraude"), NEBULA("Nébuleuse violette"), ICE("Cristal polaire"), CONSOLE("Console ambrée")
}
internal data class CounterPlayer(val name: String = "Joueur 1", val maximum: Int = 30, val damage: Int = 0,
    val style: CounterStyle = CounterStyle.GALAXY, val shields: Int = 0, val experience: Int = 0) {
    val life get() = maximum - damage
    fun hit(delta: Int) = copy(damage = (damage.toLong() + delta).coerceIn(0, maximum.toLong()).toInt())
    fun json() = JSONObject().put("name",name).put("maximum",maximum).put("damage",damage).put("style",style.name).put("shields",shields).put("experience",experience)
    companion object {
        fun read(j: JSONObject) = CounterPlayer(j.getString("name"),toolInt(j,"maximum",1..100),toolInt(j,"damage",0..100),CounterStyle.valueOf(j.getString("style")),j.optInt("shields"),j.optInt("experience")).also {
            require(it.name.isNotBlank() && it.name.length <= 40 && it.maximum in 1..100 && it.damage in 0..it.maximum && it.shields in 0..99 && it.experience in 0..99)
        }
    }
}
internal data class CounterFrame(val players: List<CounterPlayer> = listOf(CounterPlayer(),CounterPlayer("Joueur 2",style=CounterStyle.SITH)),
    val scores: List<Int> = listOf(0,0), val round: Int = 1, val initiative: Int = -1) {
    fun player(index: Int, change: (CounterPlayer) -> CounterPlayer) = copy(players = players.mapIndexed { i,p -> if(i==index) change(p) else p })
    fun won(index: Int) = copy(players=players.map { it.copy(damage=0,shields=0,experience=0) }, scores=scores.mapIndexed { i,s -> if(i==index) (s+1).coerceAtMost(99) else s },round=(round+1).coerceAtMost(999),initiative=-1)
    fun json() = JSONObject().put("players",JSONArray().apply { players.forEach { put(it.json()) } }).put("scores",JSONArray(scores)).put("round",round).put("initiative",initiative)
    companion object {
        fun read(j: JSONObject): CounterFrame {
            val p=j.getJSONArray("players"); val s=j.getJSONArray("scores"); require(p.length()==2 && s.length()==2)
            return CounterFrame((0..1).map { CounterPlayer.read(p.getJSONObject(it)) },(0..1).map { s.getInt(it) },j.getInt("round"),j.getInt("initiative")).also { require(it.scores.all { n -> n in 0..99 } && it.round in 1..999 && it.initiative in -1..1) }
        }
    }
}
internal data class CounterSession(val frame: CounterFrame = CounterFrame(), val undo: List<CounterFrame> = emptyList(),
    val events: List<String> = emptyList(), val elapsed: Long = 0, val since: Long = 0,
    val faceToFace: Boolean = true, val damageMode: Boolean = false, val tokens: Boolean = false) {
    fun change(next: CounterFrame, label: String) = if(next==frame) this else copy(frame=next,undo=(undo+frame).takeLast(50),events=(events+label.take(150)).takeLast(50))
    fun undoLast() = if(undo.isEmpty()) this else copy(frame=undo.last(),undo=undo.dropLast(1),events=events.dropLast(1))
    fun duration(now: Long) = (elapsed + if(since>0) (now-since).coerceIn(0,7*24*60*60_000L) else 0).coerceAtMost(365*24*60*60_000L)
    fun toggleTimer(now: Long) = if(since>0) copy(elapsed=duration(now),since=0) else copy(since=now)
    fun json() = JSONObject().put("frame",frame.json()).put("undo",JSONArray().apply { undo.forEach { put(it.json()) } })
        .put("events",JSONArray(events)).put("elapsed",elapsed).put("since",since).put("face",faceToFace).put("damageMode",damageMode).put("tokens",tokens)
    companion object {
        fun read(j: JSONObject): CounterSession {
            val u=j.getJSONArray("undo"); val e=j.getJSONArray("events"); require(u.length()<=50 && e.length()==u.length())
            return CounterSession(CounterFrame.read(j.getJSONObject("frame")),(0 until u.length()).map { CounterFrame.read(u.getJSONObject(it)) },
                (0 until e.length()).map { e.getString(it).also { s -> require(s.length<=150) } },j.getLong("elapsed"),j.getLong("since"),j.getBoolean("face"),j.getBoolean("damageMode"),j.optBoolean("tokens")).also { require(it.elapsed in 0..365*24*60*60_000L && it.since>=0) }
        }
    }
}
/** Scoped to the counter view: Android only honours this flag while that view is visible. */
internal class CounterAwake(private val view: View) : AutoCloseable {
    private val previous = view.keepScreenOn
    private var closed = false
    init { view.keepScreenOn = true }
    override fun close() { if(!closed) { view.keepScreenOn=previous; closed=true } }
}
internal class MatchCounterStore(private val db: CollectionDatabase) {
    fun read() = db.tool("counter")?.let(CounterSession::read) ?: CounterSession()
    fun save(s: CounterSession) = db.putTool("counter",s.json())
    fun history(): List<JSONObject> = db.tool("matches")?.getJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun finish(s: CounterSession, now: Long): CounterSession {
        val item=JSONObject().put("date",now).put("players",JSONArray(s.frame.players.map { it.name })).put("scores",JSONArray(s.frame.scores)).put("duration",s.duration(now))
        val next=CounterSession(frame=CounterFrame(players=s.frame.players.map { it.copy(damage=0,shields=0,experience=0) }),faceToFace=s.faceToFace,damageMode=s.damageMode,tokens=s.tokens)
        db.writableDatabase.beginTransaction()
        try { db.putTool("matches",JSONObject().put("items",JSONArray().apply { (listOf(item)+history()).take(50).forEach { put(it) } })); save(next); db.writableDatabase.setTransactionSuccessful() }
        finally { db.writableDatabase.endTransaction() }
        return next
    }
}
