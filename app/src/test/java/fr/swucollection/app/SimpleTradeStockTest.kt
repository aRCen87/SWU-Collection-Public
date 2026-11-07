package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class SimpleTradeStockTest {
    private val french = CollectionCard(1, "R2-D2", "SOR", "236", "Normale", 3, "FR")
    @Test fun shortcutsExcludeLoansAndPreserveExactLanguageAndFinish() {
        val english = french.copy(id = 2, language = "EN", variant = "Hyperspace Foil", quantity = 4)
        val available = SimpleTradeStock.available(listOf(french, english), mapOf(loanKey(french) to 2, loanKey(english) to 4))
        assertEquals(listOf(TradeSelection("SOR-236", 1, "FR", "Classique")), available)
        assertFalse(SimpleTradeStock.valid(listOf(TradeSelection("SOR-236", 2)), available))
        assertFalse(SimpleTradeStock.valid(listOf(TradeSelection("SOR-236", 1, "EN")), available))
        assertTrue(SimpleTradeStock.valid(available, available))
    }
    @Test fun duplicateSelectionsCannotExceedAvailableStock() {
        val available = SimpleTradeStock.available(listOf(french), emptyMap())
        val item = available.single().copy(quantity = 2)
        assertFalse(SimpleTradeStock.valid(listOf(item, item), available))
        assertFalse(SimpleTradeStock.valid(listOf(item.copy(quantity = 0)), available))
        assertFalse(SimpleTradeStock.valid(emptyList(), available))
    }
    @Test fun hyperspaceFoilAndOrdinaryCopiesRemainSeparateAndQuantityIsCapped() {
        val rows = SimpleTradeStock.available(listOf(french.copy(quantity = 20), french.copy(id = 2, variant = "Hyperspace Foil")), emptyMap())
        assertEquals(setOf("Classique", "Hyperspace foil"), rows.map { it.finish }.toSet())
        assertEquals(9, rows.first().quantity)
        assertEquals(3, rows.last().quantity)
    }
}
