package fr.swucollection.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class CardDetailsAssetTest {
    private val json = """{"format":"SWU_CARD_DETAILS","version":1,"cards":{"ASH:031":{"family":"family","en":"Test","cost":2,"aspects":["Vigilance"],"traits":[],"arena":""}}}"""
    private val card = CatalogCard("ASH", "031", "Éclairs", "Évènement", "Rare")

    @Test fun loadsPlainJsonUnderTheNamePackagedByAndroid() {
        val requests = mutableListOf<String>()
        val details = CardDetails.loadAsset { name ->
            requests += name
            check(name == "card-details.json")
            ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        }
        assertEquals(listOf("card-details.json"), requests)
        assertEquals("family", details.family(card))
        assertEquals(2, details.get(card)?.cost)
    }

    @Test fun loadsOriginalGzipFromSourceAssetEnvironments() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val requests = mutableListOf<String>()
        val details = CardDetails.loadAsset { name ->
            requests += name
            if (name == "card-details.json") throw FileNotFoundException(name)
            ByteArrayInputStream(output.toByteArray())
        }
        assertEquals(listOf("card-details.json", "card-details.json.gz"), requests)
        assertEquals("family", details.family(card))
        assertEquals(listOf("Vigilance"), details.get(card)?.aspects)
    }

    @Test fun rejectsInvalidDetailsInsteadOfSilentlyHidingPackagingErrors() {
        try {
            CardDetails.loadAsset { ByteArrayInputStream("{}".toByteArray()) }
            fail("Invalid data accepted")
        } catch (_: org.json.JSONException) { }
    }
}
