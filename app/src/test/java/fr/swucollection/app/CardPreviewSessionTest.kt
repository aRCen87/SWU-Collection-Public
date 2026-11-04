package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CardPreviewSessionTest {
    private fun entry(set: String, number: String, variant: String? = null) =
        CardPreviewEntry(CatalogCard(set, number, "$set $number", "Unité", "Commune"), variant)

    @Test fun browsingFollowsVisibleOrderAndNeverWrapsAtTheEnds() {
        val visible = listOf(entry("HMW", "030"), entry("ASH", "012"), entry("SOR", "033"))
        val session = CardPreviewSession.open(visible[1], visible)
        assertEquals(1, session.index)
        assertEquals(visible[2], session.move(1).current)
        assertEquals(visible[0], session.move(-1).current)
        assertFalse(session.move(-1).hasPrevious)
        assertEquals(visible[0], session.move(-1).move(-1).current)
        assertFalse(session.move(1).hasNext)
        assertEquals(visible[2], session.move(1).move(1).current)
    }
    @Test fun filteredSnapshotDoesNotIncludeHiddenCardsOrChangeAfterOpening() {
        val first = entry("ASH", "001")
        val last = entry("ASH", "005")
        val visible = mutableListOf(first, last)
        val session = CardPreviewSession.open(first, visible)
        visible.clear()
        assertEquals(last, session.move(1).current)
        assertEquals(2, session.entries.size)
    }
    @Test fun ownedFinishesRemainDistinctAndCanonicalDuplicatesAreRemoved() {
        val normal = entry("ASH", "011", "Normale")
        val foil = entry("ASH", "011", "Foil")
        val duplicate = entry("ash", "11", "Normale")
        val session = CardPreviewSession.open(foil, listOf(normal, duplicate, foil))
        assertEquals(2, session.entries.size)
        assertEquals("Foil", session.current.variant)
        assertEquals("Normale", session.move(-1).current.variant)
    }
    @Test fun cardAbsentFromVisibleListOpensAloneWithoutUnrelatedNeighbours() {
        val clicked = entry("HMW", "010")
        val session = CardPreviewSession.open(clicked, listOf(entry("SOR", "001")))
        assertEquals(clicked, session.current)
        assertFalse(session.hasNext)
        assertFalse(session.hasPrevious)
        assertEquals(clicked, CardPreviewSession.open(clicked, emptyList()).current)
    }
}
