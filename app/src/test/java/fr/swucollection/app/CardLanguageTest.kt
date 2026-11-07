package fr.swucollection.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class CardLanguageTest {
    @Test fun languageComesFromTheFooterAndNeverFromTheEnglishOrFrenchTitle() {
        assertEquals("FR",ScanRecognition.language("R2-D2\nSOR • FR • 236/252"))
        assertEquals("EN",ScanRecognition.language("R2-D2\nSOR • EN • 236/252"))
        assertEquals("EN",ScanRecognition.language("L0F EN C 552"))
        assertEquals("EN",ScanRecognition.language("JTLENL1093"))
        assertNull(ScanRecognition.language("ENGLISH TITLE\nSOR 236/252"))
        assertEquals("DE",ScanRecognition.language("SOR DE 236/252"))
        assertEquals("ES",ScanRecognition.language("SOR ES 236/252"))
        assertEquals("IT",ScanRecognition.language("SOR IT 236/252"))
        assertNull(ScanRecognition.language("SOR FR 236/252\nSOR EN 236/252"))
    }
    @Test fun automaticScanRequiresAStableLanguageAndAHeldCardCannotBeAddedAgainByChangingLanguage() {
        val gate=StableScanGate();val ref=ScanReference("SOR","236")
        assertNull(gate.observe(ref,1000,"FR"))
        assertNull(gate.observe(ref,1450,"EN"))
        assertEquals(ref,gate.observe(ref,1900,"EN"))
        assertNull(gate.observe(ref,2350,"FR"));assertNull(gate.observe(ref,2800,"FR"))
        gate.observe(null,3000);gate.observe(null,4000)
        assertNull(gate.observe(ref,4450,"FR"));assertEquals(ref,gate.observe(ref,4900,"FR"))
    }
    @Test fun selectingEnglishAndFrenchUsesExactFrontAndBackUrlsWithoutChangingIdentity() {
        val card=CatalogCard("SOR","005","Luke","Leader","Spéciale","https://cdn.starwarsunlimited.com/fr-front.png","https://cdn.starwarsunlimited.com/fr-back.png")
        val faces=mapOf("EN" to listOf("https://cdn.starwarsunlimited.com/en-front.png","https://cdn.starwarsunlimited.com/en-back.png"), "FR" to listOf(card.imageUrl,card.backImageUrl))
        val english=CardLanguageArtwork.select(card,"EN",faces)
        assertEquals(faces.getValue("EN")[0],english.imageUrl);assertEquals(faces.getValue("EN")[1],english.backImageUrl)
        assertEquals("EN",english.language);assertEquals(DeckConstruction.reference(card),DeckConstruction.reference(english))
        assertEquals(card,CardLanguageArtwork.select(english,"FR",faces))
        assertEquals("",CardLanguageArtwork.select(card,"EN",null).imageUrl)
    }
    @Test fun languageFallbackNeverShowsAnExplicitlyEnglishImageAsFrench() {
        val card=CatalogCard("NEW","001","Carte","Unité","Rare","https://cdn.starwarsunlimited.com/card_10010001_EN_New.png")
        assertEquals("",CardLanguageArtwork.select(card,"FR",null).imageUrl)
        assertEquals(card.imageUrl,CardLanguageArtwork.select(card,"EN",null).imageUrl)
        assertEquals("",CardLanguageArtwork.select(card,"DE",mapOf("DE" to listOf(card.imageUrl,""))).imageUrl)
        assertEquals("FR",CardLanguages.imageLanguage("https://cdn.starwarsunlimited.com/card_SWH_01fr_229_Garde_du_Bloc_de_Detention.png"))
        assertEquals("EN",CardLanguages.imageLanguage("https://cdn.starwarsunlimited.com/card_SWH_01_141_Make_It_Happen.png"))
    }
    @Test fun sharedInventoryPreservesAllFiveLanguagesWithoutMergingTheirKeys() {
        val profile=TradeProfile(CardLanguages.supported.map { TradeItem("SOR","236","Normale","R2-D2","r2",1,language=it) },emptyList())
        val result=TradeProfile.parse(profile.json())
        assertEquals(profile,result)
        assertEquals(5,result!!.offers.map { it.key }.distinct().size)
    }
    @Test fun previewNavigationDoesNotCollapseTheTwoLanguagesOfOnePrinting() {
        val card=CatalogCard("SOR","236","R2-D2","Unité","Spéciale")
        val french=CardPreviewEntry(card,"Normale","FR")
        val english=french.copy(language="EN")
        val session=CardPreviewSession.open(french,listOf(french,english))
        assertEquals(2,session.entries.size);assertEquals("EN",session.move(1).current.language)
    }
    @Test fun localizedArtworkLoadsBothAaptExpandedAndGzipSourceAssets() {
        val text="""{"format":"SWU_CARD_ART_LANGUAGES","version":1,"cards":{"SOR:236":{"EN":["en",""],"FR":["fr",""]}}}"""
        val names=mutableListOf<String>()
        val plain=CardLanguageArtwork.loadAsset { name -> names+=name;check(name=="card-art-languages.json");ByteArrayInputStream(text.toByteArray()) }
        assertEquals("en",plain.getJSONObject("cards").getJSONObject("SOR:236").getJSONArray("EN").getString(0))
        assertEquals(listOf("card-art-languages.json"),names)
        val compressed=ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
        val zipped=CardLanguageArtwork.loadAsset { name ->
            if(name=="card-art-languages.json")throw FileNotFoundException(name)
            ByteArrayInputStream(compressed)
        }
        assertEquals(plain.toString(),zipped.toString())
    }
    @Test fun serialNumberDoesNotOverrideTheCollectorReferenceAndSharedGoldFinishesNeedConfirmation() {
        assertEquals(ScanReference("SEC","1125"),ScanRecognition.extract("000/250\nSEC EN R 1125"))
        assertNull(ScanRecognition.extract("001/250\nSEC EN"))
        assertNull(ScanRecognition.extract("SEC EN 001/250"))
        assertEquals(ScanReference("TS26","008"),ScanRecognition.extract("TS26 FR 008/084"))
        for (code in listOf("34","36","37")) {
            val card=CatalogCard("SEC","1125","Chuchi","Unité","Rare","https://cdn.starwarsunlimited.com/card_06${code}1125_EN_Chuchi_Carb_C_Gold.png")
            val print=ScanVariants.detect(card)
            assertNull(print.variant)
            assertEquals(setOf("Serialized","Sérialisée or rose","Sérialisée or"),print.choices.toSet())
        }
        val standard=CatalogCard("JTL","1087","Banshee","Unité","Rare","https://cdn.starwarsunlimited.com/card_04341087_EN_Banshee.png")
        assertEquals("Serialized",ScanVariants.detect(standard).variant)
    }
    @Test fun printCataloguePreservesTheUsualFinishOrderingAndRejectsUnknownTreatments() {
        val parsed=ScanPrintCatalogue.parse("""{"format":"SWU_PRINT_FINISHES","cards":{"SOR:236":["Foil","Normale"]}}""")
        assertEquals(listOf("Normale","Foil"),parsed["SOR:236"])
        assertTrue(runCatching { ScanPrintCatalogue.parse("""{"format":"SWU_PRINT_FINISHES","cards":{"SOR:236":["invented"]}}""") }.isFailure)
    }
    @Test fun sharedInventoryPreservesBothLanguagesAndNeverOffersEnglishAsExplicitlyFrench() {
        val french=TradeItem("SOR","236","Normale","R2-D2","r2",2,language="FR")
        val english=french.copy(language="EN",quantity=3)
        val giver=TradeProfile(listOf(french,english),emptyList())
        val restored=TradeProfile.parse(giver.json())
        assertEquals(giver,restored)
        val wantsFrench=TradeProfile(emptyList(),listOf(french.copy(quantity=5)))
        assertEquals(2,TradeComparison.matching(giver,wantsFrench).sumOf { it.quantity })
        val wantsAny=TradeProfile(emptyList(),listOf(french.copy(quantity=5,language=CardLanguages.UNKNOWN)))
        val matches=TradeComparison.matching(giver,wantsAny)
        assertEquals(5,matches.sumOf { it.quantity });assertEquals(setOf("FR","EN"),matches.map { it.offered.language }.toSet())
    }
    @Test fun aFreshCatalogFaceCanFillAnOlderEmptyLanguageEntryWithoutReplacingASpecificFinish() {
        val fresh = CatalogCard("SOR","273","Palpatine","Leader","Rare","https://cdn.starwarsunlimited.com/card_SWH_01fr_273_new.png")
        val empty = mapOf("FR" to listOf("",""))
        assertEquals(fresh.imageUrl,CardLanguageArtwork.select(fresh,"FR",empty).imageUrl)
        assertEquals("",CardLanguageArtwork.select(fresh,"FR",empty,allowEmptyFallback=false).imageUrl)
        assertEquals("",CardLanguageArtwork.select(fresh,"EN",empty).imageUrl)
    }
}
