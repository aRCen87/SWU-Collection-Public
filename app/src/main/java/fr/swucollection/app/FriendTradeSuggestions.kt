package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SuggestedTrade(val peer: PlayerProfile, val offer: List<TradeSelection>, val wanted: List<TradeSelection>)
internal object FriendTradeSuggestions {
    fun selections(matches: List<TradeMatch>): List<TradeSelection> = matches.mapNotNull { match ->
        val item = match.offered
        val finish = when (item.variant.lowercase()) {
            "normale", "classique" -> "Classique"
            "foil" -> "Foil"
            "hyperspace" -> "Hyperspace"
            "hyperspace foil" -> "Hyperspace foil"
            else -> null
        } ?: return@mapNotNull null
        if (item.language !in listOf("FR", "EN", "DE", "ES", "IT")) return@mapNotNull null
        TradeSelection(DeckInterchange.normalizeId(item.set + "-" + item.number), match.quantity.coerceAtMost(9), item.language, finish)
    }.groupBy { Triple(it.cardId,it.language,it.finish) }.values.map { rows -> rows.first().copy(quantity = rows.sumOf { it.quantity }.coerceAtMost(9)) }.take(20)
    fun prepare(peer: PlayerProfile, mine: TradeProfile, theirs: TradeProfile): SuggestedTrade = SuggestedTrade(peer,
        selections(TradeComparison.matching(mine,theirs)), selections(TradeComparison.matching(theirs,mine)))
}
internal suspend fun CommunityService.shareTradeInventory(profile: TradeProfile) {
    val raw = profile.json(); TradeProfile.parse(raw)
    val encoded = PrivatePayload.encode(raw)
    require(encoded.length <= 600000) { "La liste d’échange est trop volumineuse. Réduis tes objectifs d’extension." }
    accountStore().collection("tradeInventories").document(uid).set(mapOf("encoded" to encoded,
        "hash" to PrivatePayload.hash(raw.toByteArray(Charsets.UTF_8)), "updatedAt" to FieldValue.serverTimestamp())).communityAwait()
}
internal suspend fun CommunityService.tradeInventory(peer: String): TradeProfile? {
    val doc = accountStore().collection("tradeInventories").document(peer).get(Source.SERVER).communityAwait()
    if (!doc.exists()) return null
    val encoded = doc.getString("encoded") ?: error("Liste d’échange illisible.")
    require(encoded.length <= 600000)
    val raw = PrivatePayload.decode(encoded, 4 * 1024 * 1024)
    require(PrivatePayload.hash(raw.toByteArray(Charsets.UTF_8)) == doc.getString("hash")) { "Liste d’échange altérée." }
    return TradeProfile.parse(raw)
}
internal suspend fun CommunityService.hideTradeInventory() {
    accountStore().collection("tradeInventories").document(uid).delete().communityAwait()
}
@Composable
internal fun FriendTradeSuggestionPanel(service: CommunityService, database: CollectionDatabase, catalog: List<CatalogCard>, details: CardDetails,
    friends: List<PlayerProfile>, onDraft: (SuggestedTrade) -> Unit) {
    val scope = rememberCoroutineScope(); val owner = service.uid
    var busy by remember { mutableStateOf(false) }; var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<TradeProfile?>(null) }; var matches by remember { mutableStateOf<List<SuggestedTrade>>(emptyList()) }
    fun local() = TradeProfile.build(catalog,database.allCards(),database.goals(),details,variantsBySet = database.goalVariants())
    fun action(block: suspend () -> Unit) { if (!busy) scope.launch {
        busy = true; status = null
        try { check(service.uid == owner); block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { status = if (error is IllegalArgumentException) error.message else communityError(error) }
        finally { busy = false }
    } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suggestions entre amis", style = MaterialTheme.typography.titleMedium)
        Text("Partage uniquement les doublons proposés et les cartes recherchées selon tes objectifs d’extension. Tes amis acceptés pourront les comparer à leurs listes. Ta collection complète reste privée.")
        OutlinedButton(onClick = { preview = local() }, enabled = !busy) { Text("Prévisualiser mes cartes d’échange") }
        OutlinedButton(onClick = { action { service.hideTradeInventory(); matches = emptyList(); status = "Partage des cartes d’échange désactivé." } }, enabled = !busy) { Text("Arrêter le partage avec mes amis") }
        Button(onClick = { action {
            val mine = local(); val found = mutableListOf<SuggestedTrade>(); var unavailable = 0
            for (peer in friends.distinctBy { it.id }) {
                try { service.tradeInventory(peer.id)?.let { found += FriendTradeSuggestions.prepare(peer,mine,it) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { unavailable++ }
            }
            matches = found.filter { it.offer.isNotEmpty() || it.wanted.isNotEmpty() }
            status = if (matches.isEmpty()) "Aucune correspondance pour le moment. Chaque ami doit partager ses cartes d’échange." else "${matches.size} ami(s) avec des cartes correspondantes."
            if (unavailable > 0) status += " $unavailable liste(s) non accessible(s)."
        } }, enabled = !busy && friends.isNotEmpty()) { Text("Chercher les échanges possibles") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        matches.forEach { match -> Column {
            Text(match.peer.name, style = MaterialTheme.typography.titleSmall)
            Text("Je peux proposer ${match.offer.sumOf { it.quantity }} carte(s) · je peux recevoir ${match.wanted.sumOf { it.quantity }} carte(s)")
            TextButton(onClick = { onDraft(match) }, enabled = !busy && match.offer.isNotEmpty() && match.wanted.isNotEmpty()) { Text("Préparer une proposition à vérifier") }
            if (match.offer.isEmpty() || match.wanted.isEmpty()) Text("Une seule direction correspond : actualise les recherches ou compose un échange depuis le profil de cet ami.", style = MaterialTheme.typography.bodySmall)
        } }
    } }
    preview?.let { profile -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("Cartes visibles par mes amis") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${profile.offers.sumOf { it.quantity }} exemplaires proposés · ${profile.needs.sumOf { it.quantity }} recherchés")
            Text("Doublons proposés", style = MaterialTheme.typography.titleSmall)
            profile.offers.forEach { Text("${it.quantity} × ${it.name} · ${it.language} · ${it.variant}", style = MaterialTheme.typography.bodySmall) }
            Text("Cartes recherchées", style = MaterialTheme.typography.titleSmall)
            profile.needs.forEach { Text("${it.quantity} × ${it.name}", style = MaterialTheme.typography.bodySmall) }
            Text("La liste est une copie : actualise-la après des changements dans ta collection. Aucune proposition n’est envoyée automatiquement.")
        }
    }, confirmButton = { TextButton(onClick = { preview = null; action { service.shareTradeInventory(profile); status = "Cartes d’échange partagées avec tes amis acceptés." } }, enabled = !busy) { Text("Partager avec mes amis") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("Annuler") } }) }
}
