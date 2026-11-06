package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class QuickScanCorrectionTest {
    @Test fun correctingOneCopyPreservesTotalsAndReplacesOnlyItsPrint() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        val db = CollectionDatabase(context)
        val card = CatalogCard("SOR","236","R2-D2","Unité","Rare",language="FR")
        try {
            db.addOrIncrement(card.name,card.setCode,card.number,"Normale","FR")
            db.addOrIncrement(card.name,card.setCode,card.number,"Normale","FR")
            db.correctScannedCopy(ScanBatchEntry(card,"Normale",1),"EN","Foil")
            assertEquals(2,db.allCards().sumOf { it.quantity })
            assertEquals(1,db.allCards().single { it.language=="FR" }.quantity)
            assertEquals("Foil",db.allCards().single { it.language=="EN" }.variant)
            db.correctScannedCopy(ScanBatchEntry(card,"Normale",1),"EN","Foil")
            assertEquals(1,db.allCards().size)
            assertEquals(2,db.allCards().single().quantity)
            validateBackup(db.exportJson())
            val before=db.exportJson()
            assertTrue(runCatching { db.correctScannedCopy(ScanBatchEntry(card,"Normale",1),"DE","Normale") }.isFailure)
            assertEquals(before,db.exportJson())
        } finally { db.close() }
    }
}
