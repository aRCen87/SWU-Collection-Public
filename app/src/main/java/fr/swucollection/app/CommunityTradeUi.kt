package fr.swucollection.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun TradeComposer(peer: PlayerProfile, catalog: List<CatalogCard>, database: CollectionDatabase, busy: Boolean, onClose: () -> Unit,
    onSend: (List<TradeSelection>, List<TradeSelection>) -> Unit, initialOffer: List<TradeSelection> = emptyList(), initialWanted: List<TradeSelection> = emptyList()) {
    var offer by remember(peer.id, initialOffer) { mutableStateOf(initialOffer) }
    var wanted by remember(peer.id, initialWanted) { mutableStateOf(initialWanted) }
    var review by remember { mutableStateOf(false) }
    var offering by remember { mutableStateOf(true) }; var search by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("FR") }; var finish by remember { mutableStateOf("Classique") }
    val available = SimpleTradeStock.available(database.allCards(), database.loans().reserved())
    val offerValid = SimpleTradeStock.valid(offer, available)
    val candidates = remember(search, catalog) { if (search.trim().length < 2) emptyList() else catalog.asSequence()
        .filter { it.name.contains(search.trim(), true) || DeckConstruction.reference(it).contains(search.trim(), true) }
        .distinctBy { DeckConstruction.reference(it) }.take(15).toList() }
    val selected = if (offering) offer else wanted
    fun replace(items: List<TradeSelection>) { if (offering) offer = items else wanted = items }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("Échange avec ${peer.name}") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (review) "Vérifie les cartes avant d’envoyer." else "1 · Je donne   2 · Je reçois   3 · Vérifier", fontSize = 12.sp)
            if (review) {
                TradeCardList("Je donne", offer, catalog)
                TradeCardList("Je reçois", wanted, catalog)
                TradeValuation(offer, wanted, catalog)
                Text("Après l’accord, chacun confirmera la remise des cartes pour actualiser sa collection.", fontSize = 12.sp)
            } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(offering, { offering = true }, label = { Text("Je donne (${offer.sumOf { it.quantity }})") })
                FilterChip(!offering, { offering = false }, label = { Text("Je reçois (${wanted.sumOf { it.quantity }})") })
            }
            selected.forEachIndexed { index, item -> Column {
                TradePreviewRow(catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }, item.cardId, item.quantity, item.language, item.finish)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { replace(selected.toMutableList().apply { if (item.quantity == 1) removeAt(index) else set(index, item.copy(quantity = item.quantity - 1)) }) }, enabled = !busy) { Text("−") }
                    Text("${item.quantity}")
                    TextButton(onClick = { replace(selected.toMutableList().apply { set(index, item.copy(quantity = item.quantity + 1)) }) }, enabled = !busy && item.quantity < (if (offering) SimpleTradeStock.limit(item, available) else 9)) { Text("+") }
                }
            } }
            OutlinedTextField(search, { search = it.take(80) }, label = { Text("Ajouter une carte · nom ou référence") }, modifier = Modifier.fillMaxWidth())
            if (offering) {
                val rows = available.filter { item ->
                    val card = catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }
                    search.isBlank() || item.cardId.contains(search.trim(), true) || card?.name?.contains(search.trim(), true) == true
                }
                Text("Mes exemplaires disponibles · langue et version conservées", fontSize = 12.sp)
                if (rows.isEmpty()) Text("Aucun exemplaire disponible pour cette recherche.", fontSize = 12.sp)
                rows.take(15).forEach { item ->
                    val index = offer.indexOfFirst { it.cardId == item.cardId && it.language == item.language && it.finish == item.finish }
                    val count = offer.getOrNull(index)?.quantity ?: 0
                    val card = catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }
                    OutlinedButton(onClick = {
                        if (index < 0) offer = offer + item.copy(quantity = 1)
                        else offer = offer.toMutableList().apply { set(index, get(index).copy(quantity = count + 1)) }
                    }, enabled = !busy && count < item.quantity && (index >= 0 || offer.size < 20), modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(card?.imageUrl, null, Modifier.size(30.dp, 42.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("+ ${card?.name ?: item.cardId} · ${item.language} · ${item.finish} · ${item.quantity} dispo.", fontSize = 12.sp)
                    }
                }
                if (rows.size > 15) Text("Recherche un nom pour voir les autres cartes.", fontSize = 12.sp)
            } else {
            Text("Langue et version de la carte à ajouter", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("FR", "EN", "DE", "ES", "IT").forEach { lang -> FilterChip(language == lang, { language = lang }, label = { Text(lang) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Classique", "Foil", "Hyperspace", "Hyperspace foil").forEach { kind -> FilterChip(finish == kind, { finish = kind }, label = { Text(kind) }) }
            }
            if (search.trim().length >= 2 && candidates.isEmpty()) Text("Aucune carte correspondante.")
            candidates.forEach { card -> OutlinedButton(onClick = {
                val ref = DeckConstruction.reference(card)
                val index = selected.indexOfFirst { it.cardId == ref && it.language == language && it.finish == finish }
                if (index < 0) replace(selected + TradeSelection(ref, 1, language, finish))
                else if (selected[index].quantity < 9) replace(selected.toMutableList().apply { set(index, get(index).copy(quantity = get(index).quantity + 1)) })
                search = ""
            }, enabled = !busy && selected.size < 20, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(card.imageUrl, null, Modifier.size(30.dp, 42.dp)); Spacer(Modifier.width(8.dp)); Text(card.name, fontSize = 12.sp)
            } }
            }
            if (!offerValid && offer.isNotEmpty()) Text("Certaines cartes à donner ne sont plus disponibles. Ajuste leurs quantités avant l’envoi.", color = MaterialTheme.colorScheme.error)
            Text("Maximum 20 références par côté, 9 exemplaires par référence.", fontSize = 11.sp)
            }
        }
    }, confirmButton = { Button(onClick = { if (review) onSend(offer, wanted) else if (offering) offering = false else review = true },
            enabled = !busy && offerValid && (offering && !review || wanted.isNotEmpty())) {
            Text(if (busy) "Envoi…" else if (review) "Envoyer" else if (offering) "Choisir ce que je reçois" else "Vérifier")
        } },
        dismissButton = { TextButton(onClick = { if (review) review = false else onClose() }, enabled = !busy) { Text(if (review) "Modifier" else "Annuler") } })
}
@Composable
internal fun ColumnScope.TradeRequestsPanel(service: CommunityService, trades: List<PlayerTrade>, profiles: Collection<PlayerProfile>,
    catalog: List<CatalogCard>, database: CollectionDatabase, busy: Boolean, onAnswer: (PlayerTrade, String) -> Unit, onTalk: (String) -> Unit, onChanged: () -> Unit) {
    Text("Demandes d’échange", style = MaterialTheme.typography.titleLarge)

    if (trades.isEmpty()) Text("Aucune proposition pour le moment.")
    var archived by remember { mutableStateOf(false) }
    val active = trades.filter { it.status in listOf("pending", "accepted") && !database.tradeApplied(service.uid, it.id) }
    val history = trades.filterNot { it in active }
    if (history.isNotEmpty()) TextButton(onClick = { archived = !archived }) { Text(if (archived) "Masquer les anciens échanges" else "Anciens échanges (${history.size})") }
    (active.sortedByDescending { it.updatedAt } + if (archived) history.sortedByDescending { it.updatedAt } else emptyList()).forEach { trade ->
        val peer = trade.members.firstOrNull { it != service.uid }.orEmpty()
        val incoming = trade.requester != service.uid
        val offer = remember(trade.offer) { runCatching { CommunitySocial.decodeTrade(trade.offer) }.getOrNull() }
        val wanted = remember(trade.wanted) { runCatching { CommunitySocial.decodeTrade(trade.wanted) }.getOrNull() }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profiles.firstOrNull { it.id == peer }?.name ?: "Ami", fontWeight = FontWeight.Bold)
            Text(when (trade.status) { "pending" -> if (incoming) "Proposition reçue" else "En attente de réponse"; "accepted" -> "Proposition acceptée"; "declined" -> "Proposition refusée"; else -> "Proposition annulée" })
            if (offer == null || wanted == null) Text("Proposition illisible. Tu peux la refuser ou l’annuler.") else {
                TradeCardList("Je donne", if (incoming) wanted else offer, catalog)
                TradeCardList("Je reçois", if (incoming) offer else wanted, catalog)
                TradeValuation(if (incoming) wanted else offer, if (incoming) offer else wanted, catalog)
            }
            if (trade.status == "pending") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (incoming) {
                    Button(onClick = { onAnswer(trade, "accepted") }, enabled = !busy && offer != null && wanted != null) { Text("Accepter") }
                    OutlinedButton(onClick = { onAnswer(trade, "declined") }, enabled = !busy) { Text("Refuser") }
                } else OutlinedButton(onClick = { onAnswer(trade, "cancelled") }, enabled = !busy) { Text("Annuler la proposition") }
            }
            TextButton(onClick = { onTalk(peer) }, enabled = !busy) { Text("En discuter par message") }
            if (trade.status == "accepted" && offer != null && wanted != null) ConfirmedTradeAction(database, service.uid, trade, catalog, onChanged)
        } }
    }
}
@Composable internal fun TradeCardList(title: String, items: List<TradeSelection>, catalog: List<CatalogCard>) {
    Text(title, fontWeight = FontWeight.SemiBold)
    items.forEach { item ->
        val card = catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            card?.let { AsyncImage(it.imageUrl, it.name, Modifier.size(30.dp, 42.dp)) }
            Text("${item.quantity} × ${card?.name ?: item.cardId}\n${item.language} · ${item.finish}", fontSize = 12.sp)
        }
    }
}
