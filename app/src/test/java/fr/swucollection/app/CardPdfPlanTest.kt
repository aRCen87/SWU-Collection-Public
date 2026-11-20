package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CardPdfPlanTest {
    private val card = CatalogCard("SOR", "001", "Krennic", "Leader", "Commune", "https://example.test/front.png", "https://example.test/back.png")
    @Test fun proxiesRepeatCopiesAtPhysicalSizeWithNinePerSheet() {
        val pages = CardPdfPlan.pages(listOf(PrintCard(card.copy(type = "Unité", backImageUrl = ""), 10)), true, false)
        assertEquals(listOf(9, 1), pages.map { it.size })
        pages.flatten().forEach {
            assertEquals(63f, it.width * 25.4f / 72f, .001f)
            assertEquals(88f, it.height * 25.4f / 72f, .001f)
            assertTrue(it.x > 0 && it.y > 0)
            assertTrue(it.x + it.width < CardPdfPlan.PAGE_WIDTH)
            assertTrue(it.y + it.height < 825)
            assertEquals(1, it.item.quantity)
        }
    }
    @Test fun alternateSizeAndOptionalBackFacesKeepQuantities() {
        val pages = CardPdfPlan.pages(listOf(PrintCard(card, 3)), true, true, true)
        assertEquals(6, pages.flatten().size)
        assertEquals(3, pages.flatten().count { it.item.back })
        assertEquals(63.5f, pages[0][0].width * 25.4f / 72f, .001f)
        assertEquals(88.9f, pages[0][0].height * 25.4f / 72f, .001f)
    }
    @Test fun leaderBacksAreAutomaticAndKeepTheSelectedQuantity() {
        for (type in listOf("Leader", "Chef")) {
            for (backs in listOf(false, true)) {
                val faces = CardPdfPlan.pages(listOf(PrintCard(card.copy(type = type), 3)), true, backs).flatten()
                assertEquals(6, faces.size)
                assertEquals(3, faces.count { it.item.back })
                assertEquals(3, faces.count { !it.item.back })
            }
        }
    }
    @Test fun otherCardsKeepOptionalBacksAndMissingLeaderBackDoesNotDuplicateFront() {
        val unit = card.copy(type = "Unité")
        assertEquals(1, CardPdfPlan.pages(listOf(PrintCard(unit, 1)), true, false).flatten().size)
        assertEquals(2, CardPdfPlan.pages(listOf(PrintCard(unit, 1)), true, true).flatten().size)
        assertEquals(1, CardPdfPlan.pages(listOf(PrintCard(card.copy(backImageUrl = ""), 1)), true, false).flatten().size)
    }
    @Test fun automaticBacksCountTowardsTheFaceLimit() {
        try {
            CardPdfPlan.pages(List(2) { PrintCard(card, 99) }, true, false)
            fail("Accepted more than 360 automatic faces")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun illustratedDeckKeepsQuantitiesAndAllFourZonesWithoutRepeatingCards() {
        val base = card.copy(number = "024", type = "Base")
        val unit = card.copy(number = "095", type = "Unité")
        val reserve = card.copy(number = "092", type = "Évènement")
        val input = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 3)), listOf(DeckEntry("SOR-092", 2)))
        val rows = CardPdfPlan.fromDeck(input, listOf(card, base, unit, reserve))
        assertEquals(listOf("Leader", "Base", "Deck", "Réserve"), rows.map { it.section })
        val placements = CardPdfPlan.pages(rows, false, false).flatten()
        assertEquals(4, placements.size)
        assertEquals(listOf(1, 1, 3, 2), placements.map { it.item.quantity })
    }
    @Test fun rejectsOversizedOrEmptyRequestsBeforeAllocatingCopies() {
        listOf(emptyList(), List(5) { PrintCard(card, 99) }).forEach { items ->
            try { CardPdfPlan.pages(items, true, true); fail("Accepted invalid PDF request") } catch (_: IllegalArgumentException) { }
        }
    }
}
