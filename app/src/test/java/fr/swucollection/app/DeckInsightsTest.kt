package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeckInsightsTest {
    private val leader = CatalogCard("SOR", "001", "Leader", "Leader", "Commune")
    private val base = CatalogCard("SOR", "024", "Base", "Base", "Commune")
    private val a = CatalogCard("SOR", "095", "Unité", "Unité", "Commune")
    private val reprint = a.copy(setCode = "HMW", number = "100")
    private val event = CatalogCard("SOR", "092", "Évènement", "Évènement", "Rare")
    private val catalog = listOf(leader, base, a, reprint, event)
    private fun details(): CardDetails {
        val rows = JSONObject()
        fun add(card: CatalogCard, family: String, cost: Int?, aspects: List<String>, arena: String = "") {
            rows.put(MarketPricing.key(card.setCode, card.number), JSONObject().put("family", family).put("en", card.name)
                .put("cost", cost ?: JSONObject.NULL).put("aspects", JSONArray(aspects)).put("traits", JSONArray()).put("arena", arena))
        }
        add(leader, "leader", 5, listOf("Command", "Heroism")); add(base, "base", null, listOf("Command"))
        add(a, "unit", 2, listOf("Command", "Command"), "Ground"); add(reprint, "unit", 2, listOf("Command", "Command"), "Terrestre")
        add(event, "event", 9, emptyList())
        return CardDetails.parse(JSONObject().put("format", "SWU_CARD_DETAILS").put("version", 1).put("cards", rows).toString())
    }
    @Test fun analysisWeightsCopiesSeparatesUnknownsAndCountsEachAspectOncePerCard() {
        val analysis = DeckInsights.analyse(listOf(DeckEntry("SOR-095", 3), DeckEntry("SOR-092", 2), DeckEntry("SOR-999", 1)), catalog, details())
        assertEquals(6, analysis.total); assertEquals(3, analysis.costs["2"]); assertEquals(2, analysis.costs["8+"])
        assertEquals(1, analysis.unknownCosts); assertEquals(1, analysis.unknownCards)
        assertEquals(3, analysis.arenas["Terrestres"]); assertEquals(3, analysis.aspects["Commandement"]); assertEquals(2, analysis.aspects["Neutres"])
    }
    @Test fun affinityRequiresRepeatedAspectsAndKeepsNeutralAndUnknownDistinct() {
        val deck = PortableDeck("SOR-001", "SOR-024", emptyList())
        val pool = DeckInsights.affinityPool(deck, catalog, details())
        assertEquals(DeckInsights.Affinity.MATCH, DeckInsights.affinity(a, pool, details()))
        assertEquals(DeckInsights.Affinity.OUTSIDE, DeckInsights.affinity(a, listOf("Command", "Heroism"), details()))
        assertEquals(DeckInsights.Affinity.MATCH, DeckInsights.affinity(event, pool, details()))
        assertEquals(DeckInsights.Affinity.UNKNOWN, DeckInsights.affinity(a, null, details()))
    }
    @Test fun missingProxiesUseSharedReprintStockOnlyOnceAcrossMainAndReserve() {
        val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 2)), listOf(DeckEntry("HMW-100", 1)))
        val owned = listOf(CollectionCard(1, "Unité", "HMW", "100", "Foil", 2), CollectionCard(2, "Leader", "SOR", "001", "Normale", 1))
        val missing = DeckInsights.missingPrints(deck, owned, catalog, details())
        assertEquals(2, missing.sumOf { it.quantity })
        assertEquals(listOf("SOR-024", "HMW-100"), missing.map { DeckConstruction.reference(it.card) })
        assertEquals(2, owned.first().quantity)
    }
    @Test fun budgetPreservesRangesAndUnknownQuotesInsteadOfInventingPrices() {
        val all = listOf(PrintCard(a, 3), PrintCard(event, 2))
        val prices = mapOf("SOR-095" to MarketQuote(2.0, "2026-10-31", true, maxAmount = 3.0), "SOR-092" to MarketQuote(Double.NaN))
        val budget = DeckInsights.budget(all, listOf(PrintCard(a, 1), PrintCard(event, 2)), prices)
        assertEquals(6.0, budget.total.low, .001); assertEquals(9.0, budget.total.high, .001)
        assertEquals(2.0, budget.missing.low, .001); assertEquals(2, budget.total.unknown)
        assertEquals("2026-10-31", budget.date); assertTrue(budget.cached)
    }
}
