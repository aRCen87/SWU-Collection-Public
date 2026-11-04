package fr.swucollection.app

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One chronology for the extension page and every extension selector. */
class CatalogChronology(private val knownDates: Map<String, String>) {
    fun newestFirst(sets: List<CatalogSet>): List<CatalogSet> = sets.map { set ->
        val date = validDate(set.releaseDate)
            ?: validDate(knownDates[set.code.uppercase(Locale.ROOT)].orEmpty())
        set.copy(releaseDate = date?.toString().orEmpty())
    }.sortedWith(compareByDescending<CatalogSet> { it.releaseDate }.thenBy { it.code })

    companion object {
        fun validDate(value: String): LocalDate? =
            if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                runCatching { LocalDate.parse(value) }.getOrNull() else null

        fun fromJson(text: String): CatalogChronology {
            val root = JSONObject(text)
            require(root.getString("format") == "SWU_SET_RELEASE_DATES" && root.getInt("version") == 1)
            val sets = root.getJSONObject("sets")
            val dates = sets.keys().asSequence().associateWith { code ->
                sets.getJSONObject(code).getString("date").also { require(validDate(it) != null) }
            }
            return CatalogChronology(dates)
        }

        fun releaseLabel(date: String): String = validDate(date)?.let {
            "Sortie : " + it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE))
        } ?: "Date de sortie non renseignée"
    }
}
