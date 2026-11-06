package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject

internal data class PlayerFriendship(val id: String, val members: List<String>, val requester: String, val status: String, val requesterName: String = "", val requesterAvatar: String = "preset:star") {
    fun peer(uid: String) = members.firstOrNull { it != uid }.orEmpty()
}
internal data class PlayerTrade(val id: String, val members: List<String>, val requester: String, val status: String,
    val offer: String, val wanted: String, val updatedAt: Long)
internal data class PlayerSharedDeck(val id: String, val name: String, val deckJson: String)
internal data class TradeSelection(val cardId: String, val quantity: Int = 1, val language: String = "FR", val finish: String = "Classique")
internal data class CharacterAvatar(val cardId: String, val name: String)
internal object CommunitySocial {
    val characters = listOf(
        CharacterAvatar("SOR-005", "Luke Skywalker"), CharacterAvatar("SOR-010", "Dark Vador"),
        CharacterAvatar("SOR-189", "Leia Organa"), CharacterAvatar("SOR-045", "Yoda"),
        CharacterAvatar("SOR-198", "Han Solo"), CharacterAvatar("SOR-003", "Chewbacca"),
        CharacterAvatar("TWI-011", "Ahsoka Tano"), CharacterAvatar("SOR-049", "Obi-Wan Kenobi"),
        CharacterAvatar("SOR-236", "R2-D2"), CharacterAvatar("SOR-238", "C-3PO"),
        CharacterAvatar("SOR-015", "Boba Fett"), CharacterAvatar("SHD-018", "Le Mandalorien"),
        CharacterAvatar("TWI-135", "Dark Maul"), CharacterAvatar("SOR-135", "Empereur Palpatine"),
        CharacterAvatar("SHD-266", "Rey"), CharacterAvatar("SOR-014", "Sabine Wren"))
    fun friendQr(uid: String): String { require(Regex("[A-Za-z0-9_-]{1,128}").matches(uid)); return "swu-friend:v1:$uid" }
    fun friendId(raw: String): String = raw.trim().removePrefix("swu-friend:v1:").also {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(it)) { "Ce code ami n’est pas valide." }
    }
    fun unread(uid: String, conversations: List<PlayerConversation>, receipts: Map<String, Long>): Int =
        conversations.count { it.lastSenderId.isNotBlank() && it.lastSenderId != uid && it.updatedAt > (receipts[it.id] ?: 0L) }
    fun encodeTrade(items: List<TradeSelection>): String {
        require(items.size in 1..20) { "Choisis de 1 à 20 références de cartes." }
        val array = JSONArray()
        items.forEach {
            require(Regex("[A-Z0-9]{2,8}-[0-9]{1,5}").matches(it.cardId) && it.quantity in 1..9)
            require(it.language in listOf("FR", "EN", "DE", "ES", "IT") && it.finish in listOf("Classique", "Foil", "Hyperspace", "Hyperspace foil"))
            array.put(JSONObject().put("card", it.cardId).put("quantity", it.quantity).put("language", it.language).put("finish", it.finish))
        }
        return array.toString().also { require(it.length <= 8000) }
    }
    fun decodeTrade(raw: String): List<TradeSelection> {
        require(raw.length <= 8000)
        val array = JSONArray(raw); require(array.length() in 1..20)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            TradeSelection(it.getString("card"), it.getInt("quantity"), it.getString("language"), it.getString("finish"))
        } }.also { encodeTrade(it) }
    }
}
