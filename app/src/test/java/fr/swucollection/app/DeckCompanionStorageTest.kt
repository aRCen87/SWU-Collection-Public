package fr.swucollection.app

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DeckCompanionStorageTest {
    private lateinit var context: Context
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); context.getSharedPreferences("deck_home_pins", 0).edit().clear().commit(); OfflineImages.clear(context); context.deleteDatabase("swu_collection.db") }
    @After fun cleanup() { OfflineImages.clear(context); context.deleteDatabase("swu_collection.db") }
    @Test fun threePinsSurviveReopeningAndDeletedDecksReleaseSlots() {
        val pins = DeckPins(context)
        pins.toggle("one"); pins.toggle("two"); pins.toggle("three")
        assertEquals(listOf("one", "two", "three"), DeckPins(context).all())
        assertThrows(IllegalArgumentException::class.java) { pins.toggle("four") }
        assertEquals(listOf("one", "three"), pins.retain(setOf("one", "three")))
        assertEquals(listOf("one", "three", "four"), pins.toggle("four"))
    }
    @Test fun explicitImagesStaySeparateFromCollectionDecksAndOtherFiles() {
        val db = CollectionDatabase(context)
        val store = DeckStore(context)
        try {
            db.addOrIncrement("Éclaireur", "SOR", "095", "Foil")
            store.save("Mon deck", "Premier", PortableDeck("SOR-001", "SOR-024", emptyList()))
            val before = db.exportJson()
            val unrelated = File(context.noBackupFilesDir, "unrelated.txt").apply { writeText("keep") }
            val url = "https://cards.example/recto"
            val back = "https://cards.example/verso"
            OfflineImages.saveBytes(context, url, byteArrayOf(1, 2, 3))
            DeckPins(context).toggle(store.all().single().id)
            assertTrue(OfflineImages.contains(context, url)); assertFalse(OfflineImages.contains(context, back))
            assertArrayEquals(byteArrayOf(1, 2, 3), (OfflineImages.source(context, url) as File).readBytes())
            assertEquals(back, OfflineImages.source(context, back))
            assertEquals(3L, OfflineImages.bytes(context))
            assertThrows(IllegalArgumentException::class.java) { OfflineImages.saveBytes(context, url, byteArrayOf()) }
            assertEquals(3L, OfflineImages.bytes(context))
            OfflineImages.clear(context)
            assertFalse(OfflineImages.contains(context, url)); assertEquals("keep", unrelated.readText())
            assertEquals(before, db.exportJson()); assertEquals(1, store.all().size)
            unrelated.delete()
        } finally { store.close(); db.close() }
    }
}
