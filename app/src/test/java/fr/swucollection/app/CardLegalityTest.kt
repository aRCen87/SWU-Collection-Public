package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CardLegalityTest {
    private fun card(set: String, number: String, name: String, type: String = "Unité") =
        CatalogCard(set, number, name, type, "Commune")

    @Test fun rotatedCardsAndReprints() {
        val old = card("SOR", "033", "Death Trooper")
        val reprint = card("SEC", "030", "Death Trooper")
        assertEquals(PremierStatus.OUT_OF_ROTATION, PremierLegality(listOf(old)).status(old))
        assertNull(PremierLegality(listOf(old, reprint)).status(old))
        val promo = card("P26", "001", "Death Trooper")
        assertEquals(PremierStatus.OUT_OF_ROTATION, PremierLegality(listOf(old, promo)).status(promo))
        assertNull(PremierLegality(listOf(old, reprint, promo)).status(promo))
    }

    @Test fun fullTitleAndTypeMustMatch() {
        val old = card("SHD", "014", "Cad Bane — Qui Ne Prend Aucun Risque", "Leader")
        val other = card("ASH", "011", "Cad Bane — Toujours Plus Rapide Que Toi", "Leader")
        val unit = card("HMW", "001", old.name)
        assertEquals(PremierStatus.OUT_OF_ROTATION, PremierLegality(listOf(old, other, unit)).status(old))
    }

    @Test fun suspensionCoversVariantsAndPromosButNotOtherCadBanes() {
        val banned = listOf("011", "275", "777").map {
            card("ASH", it, "Cad Bane — Toujours Plus Rapide Que Toi", "Leader")
        }
        val promo = card("P26", "099", banned.first().name, "Leader")
        val other = card("SHD", "014", "Cad Bane — Qui Ne Prend Aucun Risque", "Leader")
        val policy = PremierLegality(banned + promo + other)
        (banned + promo).forEach { assertEquals(PremierStatus.SUSPENDED, policy.status(it)) }
        assertEquals(PremierStatus.OUT_OF_ROTATION, policy.status(other))
        assertEquals(PremierStatus.SUSPENDED,
            policy.status(CollectionCard(1, "Cad Bane", "ash", "11", "Foil", 3)))
    }

    @Test fun activeAndUnknownCardsAreNotMarkedIllegal() {
        val active = card("HMW", "123", "Carte actuelle")
        val unknown = card("UNKNOWN", "1", "Carte inconnue")
        val policy = PremierLegality(listOf(active, unknown))
        assertNull(policy.status(active))
        assertNull(policy.status(unknown))
        assertNull(policy.status(CollectionCard(1, "Manuelle", "SOR", "9999", "Normale", 1)))
    }
}
