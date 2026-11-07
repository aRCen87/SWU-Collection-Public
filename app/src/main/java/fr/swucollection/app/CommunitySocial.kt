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
        CharacterAvatar("SOR-005", "Luke Skywalker"),
        CharacterAvatar("SOR-010", "Dark Vador"),
        CharacterAvatar("SOR-009", "Leia Organa"),
        CharacterAvatar("TWI-004", "Yoda"),
        CharacterAvatar("SOR-017", "Han Solo"),
        CharacterAvatar("SOR-003", "Chewbacca"),
        CharacterAvatar("TWI-011", "Ahsoka Tano"),
        CharacterAvatar("TWI-003", "Obi-Wan Kenobi"),
        CharacterAvatar("SOR-236", "R2-D2"),
        CharacterAvatar("SOR-238", "C-3PO"),
        CharacterAvatar("SOR-015", "Boba Fett"),
        CharacterAvatar("SHD-018", "Le Mandalorien"),
        CharacterAvatar("TWI-009", "Maul"),
        CharacterAvatar("SOR-006", "Empereur Palpatine"),
        CharacterAvatar("SHD-004", "Rey"),
        CharacterAvatar("SOR-014", "Sabine Wren"),
        CharacterAvatar("TWI-012", "Anakin Skywalker"),
        CharacterAvatar("TWI-008", "Padmé Amidala"),
        CharacterAvatar("TWI-007", "Capitaine Rex"),
        CharacterAvatar("TWI-005", "Comte Dooku"),
        CharacterAvatar("TWI-015", "Général Grievous"),
        CharacterAvatar("TWI-013", "Mace Windu"),
        CharacterAvatar("TWI-014", "Asajj Ventress"),
        CharacterAvatar("SHD-011", "Kylo Ren"),
        CharacterAvatar("SHD-017", "Lando Calrissian"),
        CharacterAvatar("SHD-014", "Cad Bane"),
        CharacterAvatar("SOR-016", "Grand Amiral Thrawn"),
        CharacterAvatar("SOR-008", "Hera Syndulla"),
        CharacterAvatar("LOF-015", "Cal Kestis"),
        CharacterAvatar("LOF-016", "Qui-Gon Jinn"),
        CharacterAvatar("LOF-017", "Dark Revan"),
        CharacterAvatar("ASH-018", "Grogu"),
        CharacterAvatar("ASH-013", "Ezra Bridger"),
        CharacterAvatar("ASH-003", "Baylan Skoll"),
        CharacterAvatar("SHD-012", "Bo-Katan Kryze"),
        CharacterAvatar("SOR-002", "Iden Versio"))
    fun friendId(raw: String): String = raw.trim().also {
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
