package fr.swucollection.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScanVariantsTest {
    private val catalog by lazy {
        val array = JSONObject(File("src/main/assets/catalog-fr.json").readText(Charsets.UTF_8)).getJSONArray("cards")
        (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            CatalogCard(c.getString("s"), c.getString("n"), c.getString("name"), c.optString("type"),
                c.optString("rarity"), c.optString("imageUrl"))
        }
    }
    private fun card(set: String, number: String) = catalog.single { it.setCode == set && it.number == number }

    @Test fun reconstructedFootersResolveRealLeadersBasesAndExactVariantNumbers() {
        val samples = listOf(
            Triple("SOR", "001", "Normale"), Triple("SOR", "019", "Normale"),
            Triple("SOR", "317", "Hyperspace"), Triple("SOR", "253", "Showcase"),
            Triple("JTL", "788", "Hyperspace Foil"), Triple("JTL", "1093", "Serialized"),
            Triple("ASH", "614", "Hyperspace Foil"))
        for ((set, number, finish) in samples) for (language in listOf("FR", "EN")) {
            val text = ScanFrameRecognition.text("$set $language\nTEXTE\n$number", listOf(
                ScanOcrWord(set, 0, 200, 30, 216), ScanOcrWord(language, 50, 200, 80, 216),
                ScanOcrWord("R", 100, 200, 120, 216), ScanOcrWord(number, 150, 200, 190, 216)))
            val reference = requireNotNull(ScanRecognition.extract(text))
            val resolved = card(reference.set, reference.number)
            assertEquals(number, resolved.number)
            assertTrue("$set $number", finish in ScanVariants.detect(resolved).choices)
            assertEquals(language, ScanRecognition.language(text))
        }
        assertEquals("Leader", card("SOR", "001").type)
        assertEquals("Base", card("SOR", "019").type)
    }

    @Test fun goldLeaderTreatmentsUseTheExactPrintedNumberIncludingFourDigits() {
        // Official JTL Gold Leader variantTypes / serialCode records.
        val expected = mapOf("054" to "Normale", "316" to "Hyperspace", "552" to "Foil",
            "788" to "Hyperspace Foil", "1021" to "Prestige", "1057" to "Prestige Foil", "1093" to "Serialized")
        expected.forEach { (number, variant) ->
            val original = card("JTL", number)
            val detected = ScanVariants.detect(original)
            assertEquals(number, original.number)
            assertEquals("$number", variant, detected.variant)
            assertEquals(variant, detected.resolve("Automatique"))
            assertEquals("A forced default must never override the printed reference", variant, detected.resolve("Foil"))
        }
    }
    @Test fun recentExpansionsDetectHyperspaceFoilAndShowcaseWithoutManualSelection() {
        assertEquals("Hyperspace", ScanVariants.detect(card("ASH", "376")).variant)
        assertEquals("Hyperspace Foil", ScanVariants.detect(card("ASH", "614")).variant)
        assertEquals("Normale", ScanVariants.detect(card("HMW", "001")).variant)
        assertTrue(catalog.filter { it.setCode == "HMW" }.any { ScanVariants.detect(it).variant == "Showcase" })
    }
    @Test fun oldSharedNumbersDoNotMistakeCatalogueArtworkForPhysicalFoilFinish() {
        val normal = ScanVariants.detect(card("SOR", "051"))
        val hyper = ScanVariants.detect(card("SOR", "317"))
        assertNull(normal.variant)
        assertNull(normal.resolve("Automatique"))
        assertEquals(listOf("Normale", "Foil"), normal.choices)
        assertEquals(listOf("Hyperspace", "Hyperspace Foil"), hyper.choices)
        assertNull(hyper.resolve("Normale"))
        assertEquals("Hyperspace Foil", hyper.resolve("Hyperspace Foil"))
        assertEquals("Showcase", ScanVariants.detect(card("SOR", "253")).variant)
    }
    @Test fun unknownOrMismatchedIdentifiersNeverGuessFromCollectorNumberRanges() {
        val original = card("JTL", "788")
        assertNull(ScanVariants.detect(original.copy(imageUrl = "")).variant)
        assertNull(ScanVariants.detect(original.copy(number = "054")).variant)
        assertNull(ScanVariants.detect(original.copy(setCode = "LOF")).variant)
        assertNull(ScanVariants.detect(original.copy(imageUrl = original.imageUrl.replace("0432", "0499"))).variant)
    }
}
