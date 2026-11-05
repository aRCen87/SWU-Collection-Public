package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ScanPendingReviewTest {
    private val card = CatalogCard("SOR", "236", "R2-D2", "Unité", "Rare", language = CardLanguages.UNKNOWN)
    @Test fun unresolvedScansSurviveReopeningAndCannotEnterTheCollection() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("scan_batch",0).edit().clear().commit()
        val batch = ScanBatchStore(context)
        batch.add(card, ""); batch.add(card, "")
        val restored = ScanBatchStore(context)
        assertEquals(batch.entries, restored.entries)
        assertFalse(restored.entries.single().resolved)
        val db = CollectionDatabase(context)
        try {
            val before = db.exportJson()
            assertTrue(runCatching { db.commitScanBatch(batch.id, restored.entries) }.isFailure)
            assertEquals(before, db.exportJson())
        } finally { db.close() }
        restored.clear()
    }
    @Test fun mixedCopiesCanBeReviewedSeparatelyAndMergedWithoutLosingQuantities() {
        val pending = ScanBatchEntry(card,"",3)
        var entries = resolveScanEntries(listOf(pending),pending,"FR","Normale",1)
        assertEquals(3, entries.sumOf { it.quantity })
        entries = resolveScanEntries(entries,entries.first { !it.resolved },"EN","Foil",1)
        entries = resolveScanEntries(entries,entries.first { !it.resolved },"FR","Normale",1)
        assertTrue(entries.all { it.resolved })
        assertEquals(2,entries.first { it.variant=="Normale" && it.card.language=="FR" }.quantity)
        assertEquals(1,entries.first { it.variant=="Foil" && it.card.language=="EN" }.quantity)
        val restored = DeckScan.collectionFromJson(DeckScan.collectionJson(listOf(pending)))
        assertEquals(listOf(pending),restored)
    }
}
