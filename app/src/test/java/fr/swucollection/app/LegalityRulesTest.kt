package fr.swucollection.app

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LegalityRulesTest {
    private fun feed(): JSONObject = JSONObject()
        .put("format", "SWU_LEGALITY").put("schema", 1)
        .put("checkedAt", "2026-11-01T12:00:00Z")
        .put("activeSets", JSONArray(listOf("ASH", "HMW", "IBH")))
        .put("rotatedSets", JSONArray(listOf("SOR", "SHD", "TWI")))
        .put("suspended", JSONArray())
    private val now = Instant.parse("2026-11-01T13:00:00Z")

    @Test fun emptyOfficialListLiftsSuspensionWithoutApplicationUpdate() {
        val card = CatalogCard("ASH", "011", "Cad Bane — Toujours Plus Rapide Que Toi", "Leader", "Commune")
        val rules = LegalityRules.parse(feed().toString(), now)
        assertNull(PremierLegality(listOf(card), rules).status(card))
    }
    @Test fun downloadedRotationAndSuspensionsReplaceBundledRules() {
        val old = CatalogCard("SOR", "033", "Death Trooper", "Unité", "Commune")
        val reprint = old.copy(setCode = "IBH", number = "030")
        val root = feed().put("suspended", JSONArray().put(JSONObject()
            .put("setCode", "IBH").put("number", "030").put("name", "Death Trooper").put("type", "Unité")))
        val rules = LegalityRules.parse(root.toString(), now)
        assertEquals(PremierStatus.SUSPENDED, PremierLegality(listOf(old, reprint), rules).status(old))
        assertNull(PremierLegality(listOf(old, reprint), LegalityRules.parse(feed().toString(), now)).status(old))
        val nextRotation = feed().put("activeSets", JSONArray(listOf("HMW")))
            .put("rotatedSets", JSONArray(listOf("SOR", "SHD", "TWI", "ASH", "IBH")))
        assertEquals(PremierStatus.OUT_OF_ROTATION,
            PremierLegality(listOf(old, reprint), LegalityRules.parse(nextRotation.toString(), now)).status(reprint))
    }
    @Test fun feedRecognisesReprintsMissingFromPhoneCatalogue() {
        val old = CatalogCard("SOR", "033", "Death Trooper", "Unité", "Commune")
        val root = feed().put("legalCards", JSONArray().put(JSONObject()
            .put("name", "Death Trooper").put("type", "Unité")))
        assertNull(PremierLegality(listOf(old), LegalityRules.parse(root.toString(), now)).status(old))
    }
    @Test fun invalidResponsesCannotBecomeRules() {
        val bad = listOf(
            feed().put("schema", 2), feed().put("activeSets", JSONArray()),
            feed().put("activeSets", JSONArray(listOf("SOR"))),
            feed().put("checkedAt", "bad"), feed().put("checkedAt", "2035-01-01T00:00:00Z"),
            feed().put("suspended", JSONArray().put(JSONObject().put("name", "Unknown")))
        )
        bad.forEach { root -> assertTrue(runCatching { LegalityRules.parse(root.toString(), now) }.isFailure) }
        assertTrue(runCatching { LegalityRules.parse("<html>Error</html>", now) }.isFailure)
    }
}
