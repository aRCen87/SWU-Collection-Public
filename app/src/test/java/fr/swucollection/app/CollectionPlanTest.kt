package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CollectionPlanTest {
    private fun card(n: String, type: String = "Unité") = CatalogCard("ASH", n, "Carte $n", type, "Rare",
        "https://cdn.starwarsunlimited.com/card_0801${n.padStart(4, '0')}_FR_Test.png")
    private fun owned(n: String, qty: Int, variant: String = "Normale") = CollectionCard(n.toLong(), "Carte $n", "ASH", n, variant, qty)
    @Test fun playsetReservesOneBaseAndLeaderAndThreeOtherCards() {
        val catalog = listOf(card("001", "Leader"), card("020", "Base"), card("031"), card("032", "Évènement"))
        val rows = CollectionPlan.rows(catalog, listOf(owned("031", 2), owned("031", 9, "Foil")), true)
        assertEquals(listOf(1, 1, 3, 3), rows.map { it.target })
        assertEquals(listOf(1, 1, 1, 3), rows.map { it.missing })
        assertEquals(0, CollectionPlan.rows(catalog, listOf(owned("031", 2)), false)[2].missing)
    }
    @Test fun alternatePrintsAndUnknownArtworkAreNotGuessedAsClassic() {
        val normal = card("031")
        assertTrue(CollectionPlan.isClassic(normal))
        assertFalse(CollectionPlan.isClassic(normal.copy(imageUrl = normal.imageUrl.replace("card_0801", "card_0802"))))
        assertTrue(CollectionPlan.isClassic(normal.copy(imageUrl = "https://cdn/card_SWH_01fr_031_Inferno.png")))
        assertFalse(CollectionPlan.isClassic(normal.copy(imageUrl = "https://cdn/card_SWH_01fr_302_Gideon_HYP_123.png")))
        assertFalse(CollectionPlan.isClassic(normal.copy(imageUrl = "")))
        // Pré Vizsla is a card name, not an indicator of a prestige print.
        assertTrue(CollectionPlan.isClassic(normal.copy(imageUrl = "https://cdn/card_0201142_FR_Pre_Vizsla.png")))
    }
    @Test fun partialBudgetCountsEveryMissingCopyAndRanges() {
        val rows = CollectionPlan.rows(listOf(card("031"), card("032")), listOf(owned("031", 1)), true)
        val quotes = mapOf(collectionPriceKey("ASH", "031", "Normale") to MarketQuote(1.10, maxAmount = 1.50))
        val budget = CollectionPlan.budget(rows, quotes)
        assertEquals(0, BigDecimal("2.20").compareTo(budget.minimum))
        assertEquals(0, BigDecimal("3.00").compareTo(budget.maximum))
        assertEquals(3, budget.unpricedCopies)
        assertEquals(5, CollectionPlan.budget(rows, quotes.mapValues { MarketQuote(Double.NaN) }).unpricedCopies)
    }
    @Test fun tradeSurplusKeepsPlaysetPerVariantAndUnknownReferencesConservatively() {
        val catalog = listOf(card("001", "Leader"), card("031"))
        val extras = CollectionPlan.exchange(listOf(owned("001", 2), owned("031", 3), owned("031", 5, "Foil"), owned("999", 4)), catalog, true)
        assertEquals(listOf(1, 2, 1), extras.map { it.quantity })
        assertEquals("Foil", extras[1].variant)
        val rows = CollectionPlan.rows(catalog, emptyList(), true)
        assertTrue(CollectionPlan.shareText(rows, extras).contains("RECHERCHÉES"))
        assertTrue(CollectionPlan.csv(rows, extras).contains("\"Normale\";\"3\""))
    }
    @Test fun availableShareAndExportContainOnlySurplusWithoutMissingCards() {
        val catalog = listOf(card("031"), card("032"))
        val extras = CollectionPlan.exchange(listOf(owned("031", 4)), catalog, true)
        assertEquals(1, extras.single().quantity)
        val shared = CollectionPlan.shareAvailable(extras)
        assertTrue(shared.contains("1 × Carte 031"))
        assertFalse(shared.contains("Carte 032"))
        assertFalse(shared.contains("RECHERCHÉES"))
        val csv = CollectionPlan.csv(emptyList(), extras)
        assertTrue(csv.contains("Disponible"))
        assertFalse(csv.contains("Recherchée"))
    }
}

