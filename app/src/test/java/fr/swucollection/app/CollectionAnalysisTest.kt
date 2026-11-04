package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

class CollectionAnalysisTest {
    private val card=CatalogCard("JTL","054","Gold Leader","Unité","Commune","https://cdn.starwarsunlimited.com/card_04010054_FR.png")
    private val details=CardDetails(mapOf("JTL:054" to CardDetail("gold","Gold Leader",3,listOf("Command"),emptyList(),"Space"),
        "JTL:788" to CardDetail("gold","Gold Leader",3,listOf("Command"),emptyList(),"Space")))
    @Test fun filtersCombineAndUnknownCostsNeverPassCostBounds() {
        assertTrue(SearchFacets(set="JTL",rarity="Commune",type="Unité",aspect="Command",minCost=3,maxCost=3).matches(card,details.get(card)))
        assertFalse(SearchFacets(aspect="Vigilance").matches(card,details.get(card)))
        assertFalse(SearchFacets(minCost=0).matches(card,null))
        assertTrue(SearchFacets().matches(card,null))
        assertFalse(SearchFacets(maxCost=2).matches(card,details.get(card)))
    }
    @Test fun valuesIncludeOnlyFiniteVerifiedQuotesAndRespectVariantQuantities() {
        val owned=listOf(CollectionCard(1,card.name,"JTL","054","Normale",3),CollectionCard(2,card.name,"JTL","788","Hyperspace Foil",2),CollectionCard(3,"Unknown","JTL","100","Normale",1))
        val quotes=mapOf(collectionPriceKey("JTL","054","Normale") to MarketQuote(2.5,maxAmount=3.0),collectionPriceKey("JTL","100","Normale") to MarketQuote(Double.NaN))
        val v=collectionValue(owned,quotes)
        assertEquals(0,v.low.compareTo("7.5".toBigDecimal()));assertEquals(0,v.high.compareTo("9".toBigDecimal()))
        assertEquals(3,v.priced);assertEquals(3,v.unpriced)
    }
    @Test fun decksAggregatePrintsAndFindMissingWithoutCountingOneStockTwice() {
        val hyper=card.copy(number="788",imageUrl="https://cdn.starwarsunlimited.com/card_04320788_FR.png")
        val list=CardList("id","Deck",listOf(CardListEntry(card,2),CardListEntry(hyper,2)),"Premier")
        val report=DeckAudit.report(list,listOf(CollectionCard(1,card.name,"JTL","788","Hyperspace Foil",3)),listOf(card,hyper),details,LegalityRules.DEFAULT)
        assertEquals(1,report.needs.size);assertEquals(1,report.needs.single().missing)
        assertTrue(report.issues.any {it.contains("maximum 3")});assertTrue(report.issues.any {it.contains("50 cartes")})
        val twin=DeckAudit.report(list.copy(format="Twin Suns"),emptyList(),listOf(card,hyper),details,LegalityRules.DEFAULT)
        assertTrue(twin.issues.any {it.contains("80 cartes")});assertTrue(twin.issues.any {it.contains("maximum 1")})
    }
    @Test fun reprintedGameIdentityOverridesOldSetRotationAndCarriesSuspensions() {
        val old=card.copy(setCode="SOR",name="Ancienne traduction")
        val identities=CardDetails(mapOf("JTL:054" to details.get(card)!!,"SOR:054" to details.get(card)!!))
        val list=CardList("id","Deck",listOf(CardListEntry(old,1)),"Premier")
        val rules=LegalityRules.DEFAULT.copy(suspended=emptyList())
        val legal=DeckAudit.report(list,emptyList(),listOf(card,old),identities,rules)
        assertFalse(legal.issues.any {it.contains("hors rotation")||it.contains("légalité Premier")})
        val suspended=rules.copy(suspended=listOf(SuspendedCard("JTL","054",card.name,card.type)))
        assertTrue(DeckAudit.report(list,emptyList(),listOf(card,old),identities,suspended).issues.any {it.contains("suspendue en Premier")})
    }
    @Test fun tradeProfilesRespectEachExtensionGoalAndKeepSpecialPrintNumbers() {
        val hyper=card.copy(number="788",imageUrl="https://cdn.starwarsunlimited.com/card_04320788_FR.png")
        val stock=listOf(CollectionCard(1,card.name,"JTL","054","Normale",4),CollectionCard(2,card.name,"JTL","788","Hyperspace Foil",4))
        val profile=TradeProfile.build(listOf(card,hyper),stock,mapOf("JTL" to true),details)
        assertEquals(2,profile.offers.sumOf {it.quantity});assertTrue(profile.needs.isEmpty())
        assertEquals("Hyperspace Foil",profile.offers.single {it.number=="788"}.variant)
        val x1=TradeProfile.build(listOf(card,hyper),stock,mapOf("JTL" to false),details)
        assertEquals(6,x1.offers.sumOf {it.quantity})
    }
    @Test fun paddedManualReferencesShareOneRetentionTargetAndOneTradeRow() {
        val stock=listOf(CollectionCard(1,card.name,"JTL","054","Normale",2),CollectionCard(2,card.name,"JTL","54","Normale",3))
        val profile=TradeProfile.build(listOf(card),stock,mapOf("JTL" to true),details)
        assertEquals(2,profile.offers.single().quantity)
        assertEquals(2,CollectionPlan.exchange(stock,listOf(card),true).single().quantity)
        assertEquals("054",profile.offers.single().number)
        assertEquals(profile,TradeProfile.parse(profile.json()))
    }
    @Test fun bundledMetadataCoversCatalogueAndLinksGoldLeaderWithoutChangingNumbers() {
        val root=JSONObject(File("src/main/assets/catalog-fr.json").readText())
        val loaded=GZIPInputStream(File("src/main/assets/card-details.json.gz").inputStream()).bufferedReader().use {CardDetails.parse(it.readText())}
        val a=root.getJSONArray("cards")
        val catalog=(0 until a.length()).map {i->val c=a.getJSONObject(i);CatalogCard(c.getString("s"),c.getString("n"),c.getString("name"),c.optString("type"),c.optString("rarity"))}
        assertEquals(catalog.size,catalog.count {loaded.get(it)!=null})
        val base=catalog.single {it.setCode=="JTL"&&it.number=="054"}
        val related=loaded.related(base,catalog)
        assertTrue(related.any {it.setCode=="JTL"&&it.number=="788"})
        assertEquals(loaded.family(base),loaded.family(catalog.single {it.setCode=="JTL"&&it.number=="1093"}))
        assertEquals(6,loaded.get(base)?.cost)
    }
}
