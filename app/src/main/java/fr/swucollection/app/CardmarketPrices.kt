package fr.swucollection.app

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import org.json.JSONObject
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

internal object CardmarketPrices {
    private const val GUIDE = "https://downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_21.json"
    private const val MAPPING = "https://raw.githubusercontent.com/aRCen87/SWU-Collection-Public/market-data/cardmarket-map.json"
    private const val DAY = 24 * 60 * 60 * 1000L
    private var products: JSONObject? = null
    private var snapshot: JSONObject? = null
    private var rows: Map<Long, JSONObject> = emptyMap()
    private var lastAttempt = 0L
    private var offline = false
    private var mappingDate = 0L
    private var lastMapAttempt = 0L

    private fun download(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", "SWU-Collection-Android")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            check(connection.responseCode == 200)
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                val builder = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(builder.length + count <= 10 * 1024 * 1024)
                    builder.append(buffer, 0, count)
                }
                builder.toString()
            }
            JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun save(context: Context, name: String, root: JSONObject) {
        // A cache write failure must never discard usable in-memory data.
        runCatching {
            val temporary = File(context.filesDir, "$name.tmp")
            temporary.writeText(root.toString(), Charsets.UTF_8)
            check(temporary.renameTo(File(context.filesDir, name)))
        }
    }

    private fun mapping(context: Context, force: Boolean, allowNetwork: Boolean): JSONObject {
        if (products == null) {
            val bundled = context.assets.open("cardmarket-map.json").bufferedReader(Charsets.UTF_8)
                .use { MarketPricing.validateMapping(JSONObject(it.readText())) }
            val cached = runCatching { MarketPricing.validateMapping(JSONObject(
                File(context.filesDir, "cardmarket-map.json").readText(Charsets.UTF_8))) }.getOrNull()
            val newest = listOfNotNull(bundled, cached).maxBy { MarketPricing.timestamp(it) }
            products = newest.getJSONObject("cards")
            mappingDate = MarketPricing.timestamp(newest)
        }
        val now = System.currentTimeMillis()
        if (allowNetwork && (force || lastMapAttempt == 0L || now - lastMapAttempt >= DAY)) {
            lastMapAttempt = now
            runCatching {
                val root = MarketPricing.validateMapping(download(MAPPING))
                require(root.getJSONObject("cards").length() >= products!!.length() * 0.95)
                if (MarketPricing.timestamp(root) >= mappingDate) {
                    products = root.getJSONObject("cards")
                    mappingDate = MarketPricing.timestamp(root)
                    save(context, "cardmarket-map.json", root)
                }
            }
        }
        return products!!
    }

    private fun adopt(root: JSONObject) {
        snapshot = root
        val prices = root.getJSONArray("priceGuides")
        rows = (0 until prices.length()).map { prices.getJSONObject(it) }
            .associateBy { it.getLong("idProduct") }
    }

    @Synchronized
    fun quote(context: Context, card: CatalogCard, variant: String, force: Boolean,
              allowNetwork: Boolean): MarketQuote {
        val map = mapping(context, force, allowNetwork)
        val entry = map.optJSONObject(MarketPricing.key(card.setCode, card.number))
        if (entry == null)
            return MarketPricing.resolve(entry, card.name, emptyMap(), variant, "", false)
        val file = File(context.filesDir, "cardmarket-guide.json")
        if (snapshot == null) {
            val candidates = listOfNotNull(
                runCatching { MarketPricing.validate(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull(),
                runCatching { context.assets.open("cardmarket-guide.json").bufferedReader(Charsets.UTF_8)
                    .use { MarketPricing.validate(JSONObject(it.readText())) } }.getOrNull())
            candidates.maxByOrNull { MarketPricing.timestamp(it) }?.let { adopt(it) }
        }
        val now = System.currentTimeMillis()
        if (allowNetwork && (force || lastAttempt == 0L || now - lastAttempt >= DAY)) {
            lastAttempt = now
            try {
                val downloaded = MarketPricing.validate(download(GUIDE))
                if (snapshot == null || MarketPricing.timestamp(downloaded) >= MarketPricing.timestamp(snapshot!!)) {
                    adopt(downloaded)
                    save(context, "cardmarket-guide.json", downloaded)
                }
                offline = false
            } catch (_: Exception) { offline = true }
        }
        val root = snapshot ?: return MarketQuote(null,
            message = "Guide des prix inaccessible. Vérifie ta connexion puis appuie sur Actualiser.")
        return MarketPricing.resolve(entry, card.name, rows, variant, root.getString("createdAt"),
            offline || lastAttempt == 0L || now - MarketPricing.timestamp(root) >= DAY)
    }
}

@Composable
internal fun CollectionPriceLabel(quote: MarketQuote?, loaded: Boolean, language: String? = null) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
    val label = when {
        quote == null -> if (loaded) "Cote indisponible" else "Cote : …"
        quote.amount == null -> "Cote indisponible"
        else -> "Cote : " + currency.format(quote.amount) +
            if (quote.maxAmount != null && quote.maxAmount != quote.amount)
                " à " + currency.format(quote.maxAmount) else ""
    }
    val price = label.removePrefix("Cote : ").replaceFirst("Cote", "cote")
    val display = if (language == null) label else "${CardLanguages.label(language)} : $price"
    Text(display, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = if (quote?.amount != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CardmarketPricePanel(card: CatalogCard, variant: String) {
    val context = LocalContext.current.applicationContext
    val uriHandler = LocalUriHandler.current
    var request by remember(card.setCode, card.number, variant) { mutableIntStateOf(0) }
    val quote by produceState<MarketQuote?>(null, card.setCode, card.number, variant, request) {
        value = null
        try {
            // Show the bundled/last known quote immediately, then refresh once per app session.
            value = withContext(Dispatchers.IO) {
                CardmarketPrices.quote(context, card, variant, false, allowNetwork = false)
            }
            value = withContext(Dispatchers.IO) {
                CardmarketPrices.quote(context, card, variant, request > 0, allowNetwork = true)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (value?.amount == null) value = MarketQuote(null,
                message = "Impossible de lire la cote de cette carte. Réessaie avec Actualiser.")
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cote Cardmarket · " + variant, fontWeight = FontWeight.SemiBold)
            val result = quote
            if (result == null) {
                Text("Chargement de la cote…", fontSize = 12.sp)
            } else {
                if (result.amount != null) {
                    val currency = NumberFormat.getCurrencyInstance(Locale.FRANCE)
                    val price = currency.format(result.amount) +
                        if (result.maxAmount != null && result.maxAmount != result.amount)
                            " à " + currency.format(result.maxAmount) else ""
                    Text(price,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Tendance du marché · toutes langues · hors livraison", fontSize = 11.sp)
                    Text(result.product, fontSize = 10.sp)
                    if (result.message.isNotBlank()) Text(result.message, fontSize = 11.sp)
                } else Text(result.message, fontSize = 12.sp)
                if (result.date.isNotBlank()) Text("Guide du " + MarketPricing.displayDate(result.date) +
                    if (result.cached) " · dernière cote enregistrée" else "", fontSize = 10.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { request++ }, enabled = quote != null) { Text("Actualiser", fontSize = 12.sp) }
                TextButton(onClick = {
                    val query = android.net.Uri.encode(quote?.search?.takeIf { it.isNotBlank() } ?: card.name)
                    uriHandler.openUri("https://www.cardmarket.com/fr/StarWarsUnlimited/Products/Search?searchString=" + query)
                }) { Text("Voir Cardmarket", fontSize = 12.sp) }
            }
        }
    }
}


