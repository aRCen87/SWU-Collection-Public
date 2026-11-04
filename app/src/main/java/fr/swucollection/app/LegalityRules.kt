package fr.swucollection.app

import org.json.JSONObject
import java.time.Instant

/** Data only: downloaded rules can never execute code or mutate the collection. */
data class LegalIdentity(val name: String, val type: String)
data class SuspendedCard(val setCode: String, val number: String, val name: String, val type: String)
data class LegalityRules(
    val checkedAt: String,
    val activeSets: Set<String>,
    val rotatedSets: Set<String>,
    val suspended: List<SuspendedCard>,
    val legalCards: List<LegalIdentity> = emptyList(),
    val formatSuspensions: Map<String,List<SuspendedCard>> = emptyMap()
) {
    companion object {
        val DEFAULT = LegalityRules("2026-11-01T00:00:00Z",
            setOf("JTL", "LOF", "IBH", "SEC", "LAW", "ASH", "HMW"),
            setOf("SOR", "SHD", "TWI"),
            listOf(SuspendedCard("ASH", "011", "Cad Bane — Toujours Plus Rapide Que Toi", "Leader")))

        fun parse(text: String, now: Instant = Instant.now()): LegalityRules {
            require(text.length <= 256 * 1024)
            val root = JSONObject(text)
            require(root.getString("format") == "SWU_LEGALITY" && root.getInt("schema") == 1)
            val checked = root.getString("checkedAt")
            val date = Instant.parse(checked)
            require(!date.isBefore(Instant.parse("2026-11-01T00:00:00Z")))
            require(!date.isAfter(now.plusSeconds(86400)))
            fun codes(key: String): Set<String> {
                val rows = root.getJSONArray(key)
                require(rows.length() <= 100)
                val codes = (0 until rows.length()).map { rows.getString(it) }
                require(codes.all { it.matches(Regex("[A-Z0-9]{2,12}")) })
                require(codes.distinct().size == codes.size)
                return codes.toSet()
            }
            val active = codes("activeSets")
            val rotated = codes("rotatedSets")
            require(active.isNotEmpty() && active.intersect(rotated).isEmpty())
            val rows = root.getJSONArray("suspended")
            require(rows.length() <= 100)
            val cards = (0 until rows.length()).map {
                val row = rows.getJSONObject(it)
                SuspendedCard(row.getString("setCode"), row.getString("number"),
                    row.getString("name"), row.getString("type")).also { card ->
                    require(card.setCode.matches(Regex("[A-Z0-9]{2,12}")))
                    require(card.number.matches(Regex("[0-9]{1,5}")))
                    require(card.name.length in 1..300 && card.type.length in 1..40)
                }
            }
            require(cards.map { it.setCode to it.number.toInt() }.distinct().size == cards.size)
            val legalRows = root.optJSONArray("legalCards") ?: org.json.JSONArray()
            require(legalRows.length() <= 5000)
            val legal = (0 until legalRows.length()).map {
                val row = legalRows.getJSONObject(it)
                LegalIdentity(row.getString("name"), row.getString("type")).also { card ->
                    require(card.name.length in 1..300 && card.type.length in 1..40)
                }
            }
            val formats=mutableMapOf<String,List<SuspendedCard>>()
            root.optJSONObject("formatSuspensions")?.let { byFormat ->
                for(format in listOf("Twin Suns","Éternel")) {
                    val a=byFormat.optJSONArray(format) ?: continue
                    require(a.length()<=100)
                    formats[format]=(0 until a.length()).map {i->val r=a.getJSONObject(i)
                        SuspendedCard(r.getString("setCode"),r.getString("number"),r.getString("name"),r.getString("type")).also {
                            require(it.setCode.matches(Regex("[A-Z0-9]{2,12}"))&&it.number.matches(Regex("[0-9]{1,5}"))&&it.name.length in 1..300&&it.type.length in 1..40)
                        }
                    }
                }
            }
            return LegalityRules(checked, active, rotated, cards, legal, formats)
        }
    }
}

