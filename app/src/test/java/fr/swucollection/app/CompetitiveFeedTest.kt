package fr.swucollection.app

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class CompetitiveFeedTest {
    private fun payload(): String = """{"format":"SWU_COMPETITIVE_FEED","version":1,
        "updatedAt":"2026-11-05T12:00:00Z","from":"2026-10-01","to":"2026-11-05","listCount":1,"excludedCount":0,
        "groups":[{"leader":"JTL-001","base":"JTL-024","count":1,"deckIds":["deck"]}],
        "events":[{"id":"event","name":"SQ test","date":"2026-11-01","tier":"SQ","players":42,"deckIds":["deck"]}],
        "decks":[{"id":"deck","name":"Liste test","tournamentId":"event","placement":3,
        "deck":{"leader":"JTL-001","base":"JTL-024","deck":[{"id":"JTL-095","count":3}],"sideboard":[{"id":"JTL-096","count":1}]}}]}"""
    @Test fun feedPreservesIdentityQuantitiesAndReserveForOneClickCopy() {
        val feed = CompetitiveFeed.parse(payload()); val d = feed.decks.single()
        assertEquals(3,d.placement)
        assertEquals(d.deck,DeckInterchange.importJson(DeckInterchange.exportJson(d.deck,d.name)))
        assertEquals(DeckEntry("JTL-096",1),d.deck.sideboard.single())
    }
    @Test fun brokenReferencesOrIncompatibleVersionsAreRejected() {
        for (raw in listOf(payload().replace("\"version\":1","\"version\":2"),payload().replace("\"deckIds\":[\"deck\"]","\"deckIds\":[\"missing\"]"),payload().replace("\"count\":3","\"count\":1.5"),payload().replace("\"base\":\"JTL-024\",\"count\":1","\"base\":\"JTL-025\",\"count\":1"))) {
            try { CompetitiveFeed.parse(raw); fail("Invalid feed accepted") } catch (_: Exception) { }
        }
    }
    @Test fun bundledSelectionContainsOnlyCompleteKnownPortableDecks() {
        val folder = java.io.File("src/main/assets").takeIf { it.isDirectory } ?: java.io.File("app/src/main/assets")
        val feed = CompetitiveFeed.parse(java.io.File(folder,"competitive-feed.json").readText())
        val cards = JSONObject(java.io.File(folder,"catalog-fr.json").readText()).getJSONArray("cards")
        val types = (0 until cards.length()).associate { i -> cards.getJSONObject(i).let {
            (it.getString("s") + "-" + it.getString("n").padStart(3,'0')) to it.getString("type").lowercase()
        } }
        for (list in feed.decks) {
            assertEquals("leader",types[list.deck.leader]); assertEquals("base",types[list.deck.base])
            assertTrue(list.deck.deck.sumOf { it.count } >= 50)
            assertTrue((list.deck.deck + list.deck.sideboard).all { it.cardId in types })
            assertEquals(list.deck,DeckInterchange.importJson(DeckInterchange.exportJson(list.deck,list.name)))
        }
    }
}
