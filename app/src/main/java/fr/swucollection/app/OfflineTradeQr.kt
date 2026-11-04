package fr.swucollection.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Self-contained optical transfer. No socket, account, server or catalogue lookup. */
internal object OfflineTradeQr {
    const val PREFIX = "SWUTRADE2:"
    const val CHUNK_LENGTH = 700
    const val MAX_PARTS = 1024
    private const val MAX_COMPRESSED = 512 * 1024
    private const val MAX_EXPANDED = 4 * 1024 * 1024

    fun frames(profile: TradeProfile): List<String> {
        // Reuse the portable format's quantity, duplicate and reference validation.
        TradeProfile.parse(profile.json())
        val dictionary = linkedMapOf<String, Int>()
        fun index(value: String) = dictionary.getOrPut(value) { dictionary.size }
        fun rows(items: List<TradeItem>) = JSONArray().apply {
            items.forEach { item -> put(JSONArray().apply {
                listOf(item.set, item.number, item.variant, item.name, item.family).forEach { put(index(it)) }
                put(item.quantity)
                put(item.acceptVariants)
                put(index(item.language))
            }) }
        }
        val offers = rows(profile.offers)
        val needs = rows(profile.needs)
        val compact = JSONObject().put("v", 3).put("d", JSONArray(dictionary.keys.toList()))
            .put("o", offers).put("n", needs).put("a", profile.acceptVariants)
            .toString().toByteArray(Charsets.UTF_8)
        require(compact.size <= MAX_EXPANDED) { "Profil trop volumineux. Réduis le périmètre des échanges." }
        val packed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(compact) } }.toByteArray()
        require(packed.size <= MAX_COMPRESSED) { "Profil trop volumineux. Réduis le périmètre des échanges." }
        val id = fingerprint(packed)
        val chunks = Base64.getUrlEncoder().withoutPadding().encodeToString(packed).chunked(CHUNK_LENGTH)
        require(chunks.size in 1..MAX_PARTS)
        return chunks.mapIndexed { i, chunk -> "$PREFIX$id:${chunks.size}:$i:$chunk" }
    }

    internal data class Part(val id: String, val total: Int, val index: Int, val data: String)

    internal fun parse(text: String): Part {
        require(!text.startsWith("SWUTRADE1:")) { "Ce code nécessite le Wi-Fi. Mets aussi l’autre appli à jour pour les QR hors ligne." }
        require(text.length <= CHUNK_LENGTH + 100 && text.startsWith(PREFIX)) { "Ce QR code ne contient pas un profil SWU hors ligne." }
        val fields = text.removePrefix(PREFIX).split(':', limit = 4)
        require(fields.size == 4) { "QR code incomplet." }
        val total = fields[1].toIntOrNull() ?: error("Nombre de codes invalide.")
        val index = fields[2].toIntOrNull() ?: error("Numéro de code invalide.")
        require(fields[0].matches(Regex("[a-f0-9]{64}")) && total in 1..MAX_PARTS && index in 0 until total) { "QR code invalide." }
        require(fields[3].length in 1..CHUNK_LENGTH && fields[3].matches(Regex("[A-Za-z0-9_-]+"))) { "Contenu du QR code invalide." }
        require(index == total - 1 || fields[3].length == CHUNK_LENGTH) { "QR code tronqué." }
        return Part(fields[0], total, index, fields[3])
    }

    internal fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    internal fun decode(encoded: String, id: String): TradeProfile {
        require(encoded.length <= (MAX_COMPRESSED * 4 + 2) / 3) { "Profil trop volumineux." }
        val packed = Base64.getUrlDecoder().decode(encoded)
        require(packed.size <= MAX_COMPRESSED && fingerprint(packed) == id) { "Profil endommagé. Recommence le scan." }
        val bytes = GZIPInputStream(ByteArrayInputStream(packed)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                require(output.size() + size <= MAX_EXPANDED) { "Profil décompressé trop volumineux." }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("v") in 1..3) { "Version du profil non prise en charge." }
        val dictionary = root.getJSONArray("d")
        require(dictionary.length() <= 100000)
        fun rows(key: String): JSONArray {
            val source = root.getJSONArray(key)
            require(source.length() <= 20000)
            return JSONArray().apply {
                for (i in 0 until source.length()) {
                    val row = source.getJSONArray(i)
                    require(row.length() == when(root.getInt("v")) { 1 -> 6; 2 -> 7; else -> 8 })
                    val item = JSONObject()
                    listOf("set", "number", "variant", "name", "family").forEachIndexed { column, field ->
                        val index = row.getInt(column)
                        require(index in 0 until dictionary.length())
                        item.put(field, dictionary.getString(index))
                    }
                    if (root.getInt("v") == 3) {
                        val languageIndex = row.getInt(7)
                        require(languageIndex in 0 until dictionary.length())
                        item.put("language",dictionary.getString(languageIndex))
                    } else {
                        // Historical compact profiles never recorded a language.
                        item.put("language",CardLanguages.UNKNOWN)
                    }
                    put(item.put("quantity", row.getInt(5)).put("acceptVariants", if (root.getInt("v") >= 2) row.getBoolean(6) else false))
                }
            }
        }
        return TradeProfile.parse(JSONObject().put("format", "SWU_TRADE_PROFILE").put("version", 3)
            .put("offers", rows("o")).put("needs", rows("n")).put("acceptVariants", root.getBoolean("a")).toString())
    }
}

/** Keeps partial reception local; a different profile can never contaminate it. */
internal class OfflineTradeReceiver {
    private var id: String? = null
    private val parts = mutableMapOf<Int, String>()
    var total: Int = 0
        private set
    val received: Int get() = parts.size
    val missing: List<Int> get() = (0 until total).filterNot { parts.containsKey(it) }.map { it + 1 }
    private var completed: TradeProfile? = null

    fun accept(text: String): TradeProfile? {
        val part = OfflineTradeQr.parse(text)
        require(id == null || (id == part.id && total == part.total)) { "Un autre profil est déjà en cours. Termine-le ou recommence le scan." }
        require(parts[part.index] == null || parts[part.index] == part.data) { "Deux codes contradictoires. Recommence le scan." }
        id = part.id
        total = part.total
        parts[part.index] = part.data
        completed?.let { return it }
        if (parts.size != total) return null
        return OfflineTradeQr.decode((0 until total).joinToString("") { parts.getValue(it) }, part.id).also { completed = it }
    }
}

