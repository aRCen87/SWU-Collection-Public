package fr.swucollection.app

import org.json.JSONObject

internal data class MatchReport(val player: Int = 0, val deckId: String = "", val deckName: String = "",
    val opponentLeader: String = "", val result: String = "draw", val note: String = "") {
    fun json() = JSONObject().put("player",player).put("deckId",deckId).put("deckName",deckName)
        .put("opponentLeader",opponentLeader).put("result",result).put("note",note)
    companion object {
        fun read(j: JSONObject) = MatchReport(toolInt(j,"player",0..1),j.getString("deckId"),j.getString("deckName"),j.getString("opponentLeader"),j.getString("result"),j.getString("note")).also {
            require(it.deckId.length<=128 && it.deckName.length<=120 && it.opponentLeader.length<=160 && it.note.length<=500 && it.result in listOf("win","loss","draw"))
        }
        fun fromScores(s: CounterSession, player: Int=0): MatchReport {
            val score=s.frame.scores[player].compareTo(s.frame.scores[1-player])
            return MatchReport(player=player,result=if(score>0) "win" else if(score<0) "loss" else "draw")
        }
    }
}
internal data class MatchStats(val played: Int, val wins: Int, val losses: Int, val draws: Int) {
    val percent get()=if(played==0) 0 else wins*100/played
}
internal fun matchStats(rows: List<JSONObject>, deckId: String?=null, opponent: String?=null): MatchStats {
    val reports=rows.mapNotNull { it.optJSONObject("report")?.let(MatchReport::read) }.filter {
        (deckId==null || it.deckId==deckId) && (opponent==null || it.opponentLeader==opponent)
    }
    return MatchStats(reports.size,reports.count {it.result=="win"},reports.count {it.result=="loss"},reports.count {it.result=="draw"})
}
