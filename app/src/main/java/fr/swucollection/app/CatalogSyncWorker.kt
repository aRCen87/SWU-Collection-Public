package fr.swucollection.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class CatalogSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result =
        try {
            if (syncRemote(applicationContext)) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }

    companion object {
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/aRCen87/SWU-Collection-Public/main/app/src/main/assets/catalog-fr.json"
        private const val UNIQUE_WORK = "swu-french-catalogue-periodic"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun syncRemote(context: Context): Boolean {
            val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "SWU-Collection-Android")
            }
            return try {
                if (connection.responseCode !in 200..299) return false
                val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                importCatalog(context, json)
            } finally {
                connection.disconnect()
            }
        }

        fun importBundled(context: Context): Boolean =
            try {
                val json = context.assets.open("catalog-fr.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                importCatalog(context, json)
            } catch (_: Exception) {
                false
            }

        private fun importCatalog(context: Context, json: String): Boolean {
            val root = JSONObject(json)
            require(root.optString("format") == "SWU_CATALOG_FR") { "Catalogue français invalide." }
            val setsJson = root.getJSONArray("sets")
            val cardsJson = root.getJSONArray("cards")
            val sets = (0 until setsJson.length()).map { index ->
                val item = setsJson.getJSONObject(index)
                CatalogSet(
                    code = item.getString("code"),
                    name = item.getString("name"),
                    releaseDate = item.optString("releaseDate", ""),
                    cardCount = item.optInt("cardCount", 0)
                )
            }
            val cards = (0 until cardsJson.length()).map { index ->
                val item = cardsJson.getJSONObject(index)
                CatalogCard(
                    setCode = item.getString("s"),
                    number = item.getString("n"),
                    name = item.getString("name") +
                        item.optString("sub").takeIf { it.isNotBlank() }?.let { " — " + it }.orEmpty(),
                    type = item.optString("type", ""),
                    rarity = item.optString("rarity", ""),
                    imageUrl = item.optString("imageUrl", "").takeIf {
                        it.startsWith("https://")
                    }.orEmpty(),
                    backImageUrl = item.optString("backImageUrl", "").takeIf {
                        it.startsWith("https://")
                    }.orEmpty()
                )
            }
            require(sets.isNotEmpty() && cards.isNotEmpty()) { "Catalogue vide : conservation de la version actuelle." }
            require(cards.all { card -> sets.any { it.code.equals(card.setCode, true) } }) {
                "Catalogue incohérent : extension manquante pour certaines cartes."
            }
            require(cards.map { it.setCode.uppercase() + ":" + it.number }.distinct().size == cards.size) {
                "Catalogue incohérent : numéros de cartes en double."
            }
            CollectionDatabase(context).replaceCatalog(sets, cards)
            context.getSharedPreferences("catalog_sync", Context.MODE_PRIVATE)
                .edit().putString("last_updated", root.optString("updatedAt")).apply()
            return true
        }
    }
}
