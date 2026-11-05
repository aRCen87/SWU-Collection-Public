package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class OwnedPrintGroupingTest {
    private val french = CollectionCard(1, "Lien Bestial", "HMW", "038", "Normale", 2, "FR")
    @Test fun languagesShareAPrintWithoutMergingStocks() {
        val english = french.copy(id = 2, name = "Bestial Bond", language = "EN", quantity = 5)
        val groups = groupOwnedPrints(listOf(french, english))
        assertEquals(listOf(listOf(french, english)), groups)
        assertEquals(listOf(1L, 2L), groups.single().map { it.id })
        assertEquals(7, groups.single().sumOf { it.quantity })
    }
    @Test fun finishesNumbersAndSetsRemainSeparateAndKeepDisplayOrder() {
        val foil = french.copy(id = 3, variant = "Foil")
        val hyperspace = french.copy(id = 4, number = "310", variant = "Hyperspace")
        val reprint = french.copy(id = 5, setCode = "SOR")
        val unknown = french.copy(id = 6, language = "UND")
        val rows = listOf(hyperspace, foil, french, reprint, unknown)
        assertEquals(listOf(listOf(hyperspace), listOf(foil), listOf(french, unknown), listOf(reprint)), groupOwnedPrints(rows))
        assertEquals(rows.sortedBy { it.id }, groupOwnedPrints(rows).flatten().sortedBy { it.id })
        assertTrue(groupOwnedPrints(emptyList()).isEmpty())
    }
}
