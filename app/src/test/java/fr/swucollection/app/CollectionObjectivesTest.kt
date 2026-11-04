package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CollectionObjectivesTest {
    private val classic = CatalogCard("JTL", "001", "Éclaireur", "Unité", "Commune", "https://cdn/card_04010001_FR.png")
    private val foil = classic.copy(number = "501", imageUrl = "https://cdn/card_04310501_FR.png")
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{"JTL:001":{"family":"same","en":"Scout","cost":2,"aspects":[],"traits":[]},"JTL:501":{"family":"same","en":"Scout","cost":2,"aspects":[],"traits":[]}}}""")
    private val owned = listOf(CollectionCard(1,"Éclaireur","JTL","001","Normale",2), CollectionCard(2,"Éclaireur","JTL","501","Foil",2))

    @Test fun variantsFillTheSharedObjectiveAndOnlyCopiesBeyondItAreOffered() {
        val catalog = listOf(classic,foil)
        val rows = CollectionObjectives.rows(catalog,owned,true,true,details)
        assertEquals(0, rows.sumOf { it.missing })
        val extras = CollectionObjectives.extras(owned,catalog,true,true,details)
        assertEquals(listOf(owned[1].copy(quantity=1)), extras)
        val profile = TradeProfile.build(catalog,owned,mapOf("JTL" to true),details,variantsBySet=mapOf("JTL" to true))
        assertTrue(profile.needs.isEmpty())
        assertEquals(extras.sumOf { it.quantity },profile.offers.sumOf { it.quantity })
        assertEquals(1,CollectionObjectives.rows(catalog,owned,true,false,details).sumOf { it.missing })
    }

    @Test fun pooledFamiliesDoNotCrossExtensionsOrMergeExactPrintQuantities() {
        val other = classic.copy(setCode="ASH")
        val cards = owned + owned[0].copy(id=3,setCode="ASH",quantity=1)
        val groups = groupOwnedVersions(cards,listOf(classic,foil,other),details)
        assertEquals(2,groups.size)
        assertEquals(3,groups.sumOf { it.size })
        assertEquals(cards.sumOf { it.quantity },groups.flatten().sumOf { it.quantity })
        assertTrue(groups.all { group -> group.map { it.setCode }.distinct().size == 1 })
    }

    @Test fun individualVersionPreferencesSurviveQrAndDoNotLeakToOtherNeeds() {
        val allowed = TradeItem("JTL","001","Normale","Éclaireur","same",1,true)
        val strict = allowed.copy(number="002",acceptVariants=false)
        val receiver = TradeProfile(emptyList(),listOf(allowed,strict))
        val decoded = OfflineTradeQr.frames(receiver).let { frames ->
            val reader = OfflineTradeReceiver()
            frames.mapNotNull { reader.accept(it) }.last()
        }
        assertEquals(receiver,decoded)
        val giver = TradeProfile(listOf(allowed.copy(number="501",variant="Foil",quantity=3,acceptVariants=false)),emptyList())
        val matches = TradeComparison.matching(giver,decoded)
        assertEquals(1,matches.sumOf { it.quantity })
        assertTrue(matches.all { it.wanted.acceptVariants })
    }
    @Test fun paddedReferencesUseOneRetentionTargetWhenVersionsArePooled() {
        val stock = listOf(owned[0].copy(number="1",quantity=3),owned[0].copy(id=2,number="001",quantity=3))
        val profile = TradeProfile.build(listOf(classic),stock,mapOf("JTL" to true),details,variantsBySet=mapOf("JTL" to true))
        assertEquals(3,profile.offers.single().quantity)
        assertEquals("001",profile.offers.single().number)
        assertEquals(profile,TradeProfile.parse(profile.json()))
        assertTrue(profile.needs.isEmpty())
    }

}
