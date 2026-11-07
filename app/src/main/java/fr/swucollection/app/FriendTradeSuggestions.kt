package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class SuggestedTrade(val peer: PlayerProfile, val offer: List<TradeSelection>, val wanted: List<TradeSelection>,
    val mine: TradeProfile? = null, val theirs: TradeProfile? = null)
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
        selections(TradeComparison.matching(mine,theirs)), selections(TradeComparison.matching(theirs,mine)), mine, theirs)
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
    var inspecting by remember { mutableStateOf<SuggestedTrade?>(null) }
    fun local() = TradeProfile.build(catalog,database.allCards(),database.goals(),details,variantsBySet = database.goalVariants(),reserved = database.loans().reserved())
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
            matches = found
            val matching = found.count { it.offer.isNotEmpty() || it.wanted.isNotEmpty() }
            status = if (matches.isEmpty()) "Aucune liste partagée pour le moment. Chaque ami doit partager ses cartes d’échange." else "${matches.size} liste(s) d’ami(s) consultable(s), dont $matching avec des cartes correspondantes."
            if (unavailable > 0) status += " $unavailable liste(s) non accessible(s)."
        } }, enabled = !busy && friends.isNotEmpty()) { Text("Chercher les échanges possibles") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        matches.forEach { match -> Column {
            Text(match.peer.name, style = MaterialTheme.typography.titleSmall)
            Text("Je peux proposer ${match.offer.sumOf { it.quantity }} carte(s) · je peux recevoir ${match.wanted.sumOf { it.quantity }} carte(s)")
            TextButton(onClick = { inspecting = match }) { Text("Voir nos recherches et les cartes correspondantes") }
            TextButton(onClick = { onDraft(match) }, enabled = !busy) { Text("Préparer une proposition à vérifier") }
            if (match.offer.isEmpty() || match.wanted.isEmpty()) Text("Une seule direction correspond : actualise les recherches ou compose un échange depuis le profil de cet ami.", style = MaterialTheme.typography.bodySmall)
        } }
    } }
    inspecting?.let { match -> TradeMatchDetails(match, catalog, { inspecting = null }, { inspecting = null; onDraft(match) }) }
    preview?.let { profile -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("Cartes visibles par mes amis") }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("${profile.offers.sumOf { it.quantity }} exemplaires proposés · ${profile.needs.sumOf { it.quantity }} recherchés") }
            item { Text("Doublons proposés", style = MaterialTheme.typography.titleSmall) }
            items(profile.offers, key = { "offer:" + it.key }) { Text("${it.quantity} × ${it.name} · ${it.language} · ${it.variant}", style = MaterialTheme.typography.bodySmall) }
            item { Text("Cartes recherchées", style = MaterialTheme.typography.titleSmall) }
            items(profile.needs, key = { "need:" + it.key }) { Text("${it.quantity} × ${it.name}", style = MaterialTheme.typography.bodySmall) }
            item { Text("La liste est une copie : actualise-la après des changements dans ta collection. Aucune proposition n’est envoyée automatiquement.") }
        }
    }, confirmButton = { TextButton(onClick = { preview = null; action { service.shareTradeInventory(profile); status = "Cartes d’échange partagées avec tes amis acceptés." } }, enabled = !busy) { Text("Partager avec mes amis") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("Annuler") } }) }
}

@Composable
private fun TradeMatchDetails(match: SuggestedTrade, catalog: List<CatalogCard>, onClose: () -> Unit, onDraft: () -> Unit) {
    var tab by remember(match.peer.id) { mutableStateOf("Je peux proposer") }
    var search by remember(match.peer.id) { mutableStateOf("") }
    val byRef = remember(catalog) { catalog.associateBy { DeckConstruction.reference(it) } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Échanges avec ${match.peer.name}") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Je peux proposer", "Je peux recevoir", "Mes recherches", "Ses recherches", "Ses doublons").forEach { name ->
                    FilterChip(tab == name, { tab = name }, label = { Text(name) })
                }
            }
            OutlinedTextField(search, { search = it.take(80) }, label = { Text("Filtrer les cartes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == "Je peux proposer" || tab == "Je peux recevoir") {
                    val rows = (if (tab == "Je peux proposer") match.offer else match.wanted).filter {
                        it.cardId.contains(search, true) || byRef[it.cardId]?.name?.contains(search, true) == true
                    }
                    if (rows.isEmpty()) item { Text("Aucune correspondance pour ce côté de l’échange.") }
                    items(rows, key = { it.cardId + ":" + it.language + ":" + it.finish }) { row ->
                        TradePreviewRow(byRef[row.cardId], row.cardId, row.quantity, row.language, row.finish)
                    }
                } else {
                    val all = when (tab) { "Mes recherches" -> match.mine?.needs; "Ses recherches" -> match.theirs?.needs; else -> match.theirs?.offers }.orEmpty()
                    val rows = all.filter { it.name.contains(search, true) || (it.set + "-" + it.number).contains(search, true) }
                    if (rows.isEmpty()) item { Text("Aucune carte dans cette liste.") }
                    items(rows, key = { it.key }) { row ->
                        val ref = DeckInterchange.normalizeId(row.set + "-" + row.number)
                        TradePreviewRow(byRef[ref], row.name, row.quantity, row.language, row.variant)
                    }
                }
            }
            Text("Listes partagées volontairement. Actualise-les après un échange. Le brouillon reste modifiable avant tout envoi.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDraft) { Text("Préparer le brouillon") } }, dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } })
}

@Composable
internal fun TradePreviewRow(card: CatalogCard?, fallback: String, quantity: Int, language: String, finish: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        CardArtwork(card, Modifier.size(48.dp, 68.dp), language = language,
            variant = if (finish == "Classique") "Normale" else finish)
        Column(Modifier.weight(1f)) {
            Text("$quantity × ${card?.name ?: fallback}", style = MaterialTheme.typography.titleSmall)
            Text("${CardLanguages.label(language)} · $finish", style = MaterialTheme.typography.bodySmall)
        }
    }
}
