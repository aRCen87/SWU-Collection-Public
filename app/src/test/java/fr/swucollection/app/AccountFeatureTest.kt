package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class AccountFeatureTest {
    @Test fun compressedBackupPreservesAccentsAndRejectsOversizedExpansion() {
        val raw = "Collection — Français : é à ç 中文".repeat(500)
        assertEquals(raw,PrivatePayload.decode(PrivatePayload.encode(raw)))
        assertTrue(runCatching { PrivatePayload.decode(PrivatePayload.encode(raw),100) }.isFailure)
        assertTrue(runCatching { PrivatePayload.decode("invalid payload") }.isFailure)
    }
    @Test fun homeOrderRoundTripAndInvalidPreferencesAreRejected() {
        val layout = HomeLayout(HomeLayoutStore.defaults.reversed(),"SOR")
        assertEquals(layout,HomeLayoutStore.parse(HomeLayoutStore.json(layout)))
        assertTrue(runCatching { HomeLayoutStore.parse("""{"order":["SCANNER","SCANNER"]}""") }.isFailure)
    }
    @Test fun suggestionsKeepLanguageFinishAndDoNotInventAReverseMatch() {
        val offer = TradeItem("SOR","236","Foil","R2-D2","r2",5,language="EN")
        val need = offer.copy(quantity=2)
        val match = FriendTradeSuggestions.prepare(PlayerProfile("peer","Ami"),TradeProfile(listOf(offer),emptyList()),TradeProfile(emptyList(),listOf(need)))
        assertEquals(listOf(TradeSelection("SOR-236",2,"EN","Foil")),match.offer)
        assertTrue(match.wanted.isEmpty())
        val wrongLanguage = need.copy(language="FR")
        assertTrue(FriendTradeSuggestions.prepare(PlayerProfile("peer","Ami"),TradeProfile(listOf(offer),emptyList()),TradeProfile(emptyList(),listOf(wrongLanguage))).offer.isEmpty())
    }
}
