package fr.swucollection.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MarketPricingTest {
    private val mapping = JSONObject(File("src/main/assets/cardmarket-map.json").readText(Charsets.UTF_8)).getJSONObject("cards")
    private val guide = JSONObject(File("src/main/assets/cardmarket-guide.json").readText(Charsets.UTF_8))
    private val rows = guide.getJSONArray("priceGuides").let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getLong("idProduct") }
    }
    private fun quote(key: String, variant: String = "Normale") = mapping.getJSONObject(key).let {
        MarketPricing.resolve(it, it.getString("nameFr"), rows, variant, guide.getString("createdAt"), false)
    }

    @Test fun krennicAndChewbaccaNeverResolveToVaderOrWaylay() {
        assertEquals(754505L, mapping.getJSONObject("SOR:001").getJSONArray("products").getLong(0))
        assertEquals(754508L, mapping.getJSONObject("SOR:003").getJSONArray("products").getLong(0))
        assertEquals("Directeur Krennic — Aspire Au Pouvoir", quote("SOR:001").product)
        assertNotNull(quote("SOR:001").amount)
        assertNotNull(quote("SOR:003").amount)
    }
    @Test fun all272HomeworldsStandardCardsHaveRealQuotes() {
        for (number in 1..272) {
            val key = "HMW:" + number.toString().padStart(3, '0')
            assertTrue("Missing $key", mapping.has(key))
            assertTrue("Missing price $key", (quote(key).amount ?: 0.0) > 0)
        }
    }
    @Test fun frenchNamesAndAccentsExactlyMatchTheCatalogue() {
        val catalogue = JSONObject(File("src/main/assets/catalog-fr.json").readText(Charsets.UTF_8)).getJSONArray("cards")
        for (i in 0 until catalogue.length()) {
            val card = catalogue.getJSONObject(i)
            val key = MarketPricing.key(card.getString("s"), card.getString("n"))
            if (!mapping.has(key)) continue
            val name = card.getString("name") + card.optString("sub").takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            assertEquals(name, mapping.getJSONObject(key).getString("nameFr"))
        }
        assertTrue(mapping.getJSONObject("SOR:229").getString("nameFr").contains("Détention"))
        assertEquals("SOR:001", MarketPricing.key(" sor ", " 001 / 252 "))
    }
    @Test fun sameCollectorNumberCannotBindADifferentFrenchCard() {
        val entry = mapping.getJSONObject("SOR:001")
        assertNull(MarketPricing.resolve(entry, "Dark Vador", rows, "Normale", "", false).amount)
    }
    @Test fun regularAndFoilUseTheirOwnProductAndField() {
        // This set has separate regular and foil product IDs; the old mapping skipped it entirely.
        val normal = quote("JTL:054")
        val foil = quote("JTL:054", "Foil")
        assertEquals(rows.getValue(806631L).getDouble("trend"), normal.amount!!, 0.00001)
        assertEquals(rows.getValue(815195L).getDouble("trend-foil"), foil.amount!!, 0.00001)
    }
    @Test fun missingFoilAndUnverifiedTreatmentsNeverInheritRegularPrices() {
        assertNotNull(quote("HMW:001").amount)
        val entry = JSONObject("{\"nameFr\":\"Carte accentuée\",\"nameEn\":\"Card\",\"products\":[1]}")
        val onlyNormal = mapOf(1L to JSONObject("{\"idProduct\":1,\"idCategory\":1644,\"trend\":2.0,\"trend-foil\":null}"))
        assertNull(MarketPricing.resolve(entry, "Carte accentuée", onlyNormal, "Foil", "", false).amount)
        assertNull(quote("HMW:001", "Prestige").amount)
        assertNull(MarketPricing.resolve(null, "Une carte", rows, "Normale", "", false).amount)
    }
    @Test fun doubleSidedLeaderUsesCardmarketBothFaceName() {
        assertTrue(mapping.getJSONObject("TWI:017").getString("nameEn").contains("// Darth Sidious"))
        assertNotNull(quote("TWI:017").amount)
    }
    @Test fun differentBaseBacksProduceAnExplicitRange() {
        val result = quote("SOR:024")
        assertNotNull(result.amount)
        assertTrue(result.maxAmount!! >= result.amount!!)
        assertTrue(result.message.contains("verso"))
    }
    @Test fun unsupportedAndMalformedGuidesAreRejected() {
        assertTrue(MarketPricing.timestamp(guide) > 0)
        assertEquals("31/10/2026", MarketPricing.displayDate("2026-10-31T02:43:00+0100"))
        try { MarketPricing.validate(JSONObject("{\"createdAt\":\"bad\",\"priceGuides\":[]}")); fail("Invalid guide accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun noMappedQuoteReturnsANonPositiveOrNonFiniteAmount() {
        for (key in mapping.keys()) for (variant in listOf("Normale", "Foil")) {
            val amount = quote(key, variant).amount
            if (amount != null) assertTrue("Invalid $key $variant", amount.isFinite() && amount > 0)
        }
    }

    @Test fun distinctFoilReferenceUsesFoilTrendWithoutChangingCollectorIdentity() {
        val result = quote("JTL:552", MarketPricing.CATALOGUE)
        assertEquals(quote("JTL:054", "Foil").amount!!, result.amount!!, 0.00001)
        assertNull(quote("JTL:552", "Normale").amount)
    }

    @Test fun verifiedPremiumSelectionCannotFallBackToNormalOrAnotherFinish() {
        val entry = JSONObject("""{"nameFr":"Carte","nameEn":"Card","defaultFinish":"Hyperspace",
            "products":[1],"finishes":{"Hyperspace":{"products":[1],"field":"trend","range":false},
            "Hyperspace Foil":{"products":[1],"field":"trend-foil","range":false}}}""")
        val prices = mapOf(1L to JSONObject("""{"idProduct":1,"idCategory":1644,"trend":4.0,"trend-foil":7.0}"""))
        fun resolve(finish: String) = MarketPricing.resolve(entry, "Carte", prices, finish, "", false)
        assertEquals(4.0, resolve(MarketPricing.CATALOGUE).amount!!, 0.00001)
        assertEquals(7.0, resolve("Hyperspace Foil").amount!!, 0.00001)
        assertNull(resolve("Normale").amount)
        assertNull(resolve("Prestige").amount)
    }

    @Test fun bundledMappingHasValidFinishSelections() {
        MarketPricing.validateMapping(JSONObject(File("src/main/assets/cardmarket-map.json").readText(Charsets.UTF_8)))
    }
}
