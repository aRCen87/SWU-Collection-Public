package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegalityCacheTest {
    @Test fun malformedOrOlderDownloadsPreserveRulesAndCollection() {
        val context: Context = RuntimeEnvironment.getApplication()
        val database = CollectionDatabase(context)
        database.addOrIncrement("Ma carte", "SOR", "033", "Foil")
        val before = database.exportJson()
        val bundled = context.assets.open("legality.json").bufferedReader().use { it.readText() }
        val root = JSONObject(bundled).put("checkedAt", Instant.now().toString())
        val valid = root.toString()
        val accepted = LegalitySync.acceptDownloaded(context, valid)
        val cache = File(context.filesDir, "legality.json")
        assertEquals(valid, cache.readText())
        assertTrue(runCatching { LegalitySync.acceptDownloaded(context, "<html>Error</html>") }.isFailure)
        assertTrue(runCatching {
            LegalitySync.acceptDownloaded(context, root.put("checkedAt", "2026-11-01T00:00:00Z").toString())
        }.isFailure)
        assertEquals(accepted, LegalitySync.state.value.rules)
        assertEquals(valid, cache.readText())
        assertEquals(before, database.exportJson())
        database.close()
    }
}
