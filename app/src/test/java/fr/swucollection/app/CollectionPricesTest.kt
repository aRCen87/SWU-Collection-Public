package fr.swucollection.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionPricesTest {
    private data class Item(val name: String, val amount: Double?)
    private val cards = listOf(Item("absente", null), Item("chère", 12.5),
        Item("petite", 0.02), Item("égalité", 12.5), Item("zéro", 0.0),
        Item("invalide", Double.NaN), Item("infinie", Double.POSITIVE_INFINITY))

    @Test fun ascendingUsesNumericValuesAndKeepsMissingLast() {
        assertEquals(listOf("petite", "chère", "égalité", "absente", "zéro", "invalide", "infinie"),
            sortCollectionByPrice(cards, CollectionPriceSort.ASCENDING) { it.amount }.map { it.name })
    }

    @Test fun descendingKeepsMissingLastAndTiesStable() {
        assertEquals(listOf("chère", "égalité", "petite", "absente", "zéro", "invalide", "infinie"),
            sortCollectionByPrice(cards, CollectionPriceSort.DESCENDING) { it.amount }.map { it.name })
    }

    @Test fun catalogOrderIsPreserved() {
        assertEquals(cards, sortCollectionByPrice(cards, CollectionPriceSort.CATALOG) { it.amount })
    }

    @Test fun variantQuotesAreIndependentAndReferencesNormalized() {
        val normal = collectionPriceKey("ash", "1/264", "Normale")
        val foil = collectionPriceKey("ASH", "001", "Foil")
        val quotes = mapOf(normal to 1.0, foil to 15.0)
        assertEquals(listOf(normal, foil), sortCollectionByPrice(listOf(foil, normal),
            CollectionPriceSort.ASCENDING) { quotes[it] })
        assertEquals("ASH:001:Normale", normal)
    }
}
