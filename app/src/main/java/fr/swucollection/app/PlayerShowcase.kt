package fr.swucollection.app

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import java.text.Normalizer
import java.util.Locale

internal object PlayerDiscovery {
    val formats = listOf("Premier", "Éternel", "Twin Suns", "Draft", "Scellé")
    fun cityKey(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun validate(profile: PlayerProfile) {
        require(profile.city.length <= 60 && profile.city.none(Char::isISOControl)) { "Ville : 60 caractères maximum." }
        require(profile.formats.size <= 5 && profile.formats.distinct().size == profile.formats.size && profile.formats.all { it in formats })
        require(profile.leaders.size <= 3 && profile.leaders.distinct().size == profile.leaders.size)
        profile.leaders.forEach { DeckInterchange.normalizeId(it) }
    }
}
internal data class ShowcaseCard(val id: String, val owner: String, val set: String, val number: String,
    val variant: String, val language: String, val caption: String = "") {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,120}")) && owner.isNotBlank())
        require(set.matches(Regex("[A-Z0-9]{2,12}")) && number.matches(Regex("[0-9]{1,5}")))
        require(variant in ScanVariants.options && CardLanguages.valid(language) && caption.length <= 160)
    }
}
internal data class ShowcaseFavorite(val id: String, val owner: String, val item: String)
internal suspend fun CommunityService.cityPlayers(city: String): List<PlayerProfile> {
    val key = PlayerDiscovery.cityKey(city)
    require(key.isNotBlank()) { "Indique une ville." }
    return accountStore().collection("profiles").whereEqualTo("discoverable", true).whereEqualTo("cityKey", key)
        .limit(50).get(Source.SERVER).communityAwait().documents.map { it.toPlayerProfile() }.filter { it.id != uid }
}
internal suspend fun CommunityService.showcase(owner: String): List<ShowcaseCard> =
    accountStore().collection("profiles").document(owner).collection("showcase").limit(12).get(Source.SERVER).communityAwait().documents.map { row ->
        ShowcaseCard(row.id, owner, row.getString("set").orEmpty(), row.getString("number").orEmpty(), row.getString("variant").orEmpty(), row.getString("language").orEmpty(), row.getString("caption").orEmpty())
    }
internal suspend fun CommunityService.publishShowcase(row: CollectionCard, caption: String) {
    require(caption.length <= 160)
    require(row.language in CardLanguages.supported) { "Précise la langue de la carte avant de l’exposer." }
    val id = "${row.setCode}_${row.number}_${row.variant.hashCode().toString().replace('-', 'n')}_${row.language}"
    val item = ShowcaseCard(id, uid, row.setCode, row.number, row.variant, row.language, caption.trim()).also { it.validate() }
    val ref = accountStore().collection("profiles").document(uid).collection("showcase").document(id)
    val count = showcase(uid)
    require(count.size < 12 || count.any { it.id == id }) { "Ta vitrine contient déjà 12 cartes. Retire une carte avant d’en ajouter une." }
    ref.set(mapOf("set" to item.set, "number" to item.number, "variant" to item.variant,
        "language" to item.language, "caption" to item.caption, "updatedAt" to FieldValue.serverTimestamp())).communityAwait()
}
internal suspend fun CommunityService.removeShowcase(id: String) {
    accountStore().collection("profiles").document(uid).collection("showcase").document(id).delete().communityAwait()
}
internal suspend fun CommunityService.showcaseFavorites(): List<ShowcaseFavorite> = accountStore().collection("showcaseFavorites").document(uid)
    .collection("items").limit(100).get(Source.SERVER).communityAwait().documents.map {
        ShowcaseFavorite(it.id, it.getString("owner").orEmpty(), it.getString("item").orEmpty())
    }
internal suspend fun CommunityService.favorite(item: ShowcaseCard, selected: Boolean) {
    item.validate(); require(item.owner != uid)
    val ref = accountStore().collection("showcaseFavorites").document(uid).collection("items").document(item.owner + "__" + item.id)
    if (!selected) ref.delete().communityAwait() else {
        require(showcaseFavorites().size < 100 || ref.get().communityAwait().exists()) { "Maximum 100 favoris. Retire un favori avant d’en ajouter un." }
        ref.set(mapOf("owner" to item.owner, "item" to item.id, "createdAt" to FieldValue.serverTimestamp())).communityAwait()
    }
}
