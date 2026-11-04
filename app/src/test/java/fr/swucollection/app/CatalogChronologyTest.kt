package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CatalogChronologyTest {
    private fun set(code: String, date: String = "") = CatalogSet(code, code, date, 1)
    @Test fun blankLegacyDatesAreRecoveredAndUnknownPromosStayLast() {
        val chronology = CatalogChronology(mapOf("SOR" to "2024-03-08", "HMW" to "2026-10-09", "ASH" to "2026-07-17"))
        val original = listOf(set("ASH"), set("P26"), set("SOR"), set("HMW"), set("C24"))
        val sorted = chronology.newestFirst(original)
        assertEquals(listOf("HMW", "ASH", "SOR", "C24", "P26"), sorted.map { it.code })
        assertEquals("2026-10-09", sorted.first().releaseDate)
        assertTrue(original.all { it.releaseDate.isEmpty() })
    }
    @Test fun futureCatalogueDatesAndTiesHaveStableOrder() {
        val chronology = CatalogChronology(mapOf("HMW" to "2026-10-09"))
        val sorted = chronology.newestFirst(listOf(set("B", "2027-01-08"), set("HMW", "2026-10-10"), set("A", "2027-01-08")))
        assertEquals(listOf("A", "B", "HMW"), sorted.map { it.code })
        assertEquals("2026-10-10", sorted.last().releaseDate)
        assertEquals("Sortie : 08/01/2027", CatalogChronology.releaseLabel(sorted.first().releaseDate))
    }
    @Test fun invalidDateFallsBackWithoutInventingAPromoDate() {
        val chronology = CatalogChronology(mapOf("HMW" to "2026-10-09"))
        val sorted = chronology.newestFirst(listOf(set("HMW", "2026-02-30"), set("P26", "not-a-date")))
        assertEquals("2026-10-09", sorted.first().releaseDate)
        assertEquals("", sorted.last().releaseDate)
        assertEquals("Date de sortie non renseignée", CatalogChronology.releaseLabel(""))
    }
}
