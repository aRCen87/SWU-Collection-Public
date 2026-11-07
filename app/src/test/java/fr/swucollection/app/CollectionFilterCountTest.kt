package fr.swucollection.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFilterCountTest {
    @Test fun defaultCollectionHasNoHiddenFilters() {
        assertEquals(0, collectionFilterCount("Toutes", "Toutes", "Possédées", CollectionPriceSort.CATALOG))
    }

    @Test fun eachRestrictionAndPriceOrderIsVisibleInTheCount() {
        assertEquals(1, collectionFilterCount("SOR", "Toutes", "Possédées", CollectionPriceSort.CATALOG))
        assertEquals(1, collectionFilterCount("Toutes", "EN", "Possédées", CollectionPriceSort.CATALOG))
        assertEquals(1, collectionFilterCount("Toutes", "Toutes", "Doublons", CollectionPriceSort.CATALOG))
        assertEquals(1, collectionFilterCount("Toutes", "Toutes", "Possédées", CollectionPriceSort.DESCENDING))
        assertEquals(4, collectionFilterCount("SOR", "FR", "Doublons", CollectionPriceSort.ASCENDING))
    }
}
