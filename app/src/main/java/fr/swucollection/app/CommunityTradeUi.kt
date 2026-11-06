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
internal fun TradeComposer(peer: PlayerProfile, catalog: List<CatalogCard>, busy: Boolean, onClose: () -> Unit,
    onSend: (List<TradeSelection>, List<TradeSelection>) -> Unit, initialOffer: List<TradeSelection> = emptyList(), initialWanted: List<TradeSelection> = emptyList()) {
    var offer by remember(peer.id, initialOffer) { mutableStateOf(initialOffer) }
    var wanted by remember(peer.id, initialWanted) { mutableStateOf(initialWanted) }
    var offering by remember { mutableStateOf(true) }; var search by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("FR") }; var finish by remember { mutableStateOf("Classique") }
    val candidates = remember(search, catalog) { if (search.trim().length < 2) emptyList() else catalog.asSequence()
        .filter { it.name.contains(search.trim(), true) || DeckConstruction.reference(it).contains(search.trim(), true) }
        .distinctBy { DeckConstruction.reference(it) }.take(15).toList() }
    val selected = if (offering) offer else wanted
    fun replace(items: List<TradeSelection>) { if (offering) offer = items else wanted = items }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("Échange avec ${peer.name}") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compose les deux côtés. L’acceptation confirme l’accord sans modifier les collections.", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(offering, { offering = true }, label = { Text("Je propose (${offer.size})") })
                FilterChip(!offering, { offering = false }, label = { Text("Je recherche (${wanted.size})") })
            }
            selected.forEachIndexed { index, item -> Column {
                TradePreviewRow(catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }, item.cardId, item.quantity, item.language, item.finish)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { replace(selected.toMutableList().apply { if (item.quantity == 1) removeAt(index) else set(index, item.copy(quantity = item.quantity - 1)) }) }, enabled = !busy) { Text("−") }
                    Text("${item.quantity}")
                    TextButton(onClick = { replace(selected.toMutableList().apply { set(index, item.copy(quantity = item.quantity + 1)) }) }, enabled = !busy && item.quantity < 9) { Text("+") }
                }
            } }
            OutlinedTextField(search, { search = it.take(80) }, label = { Text("Ajouter une carte · nom ou référence") }, modifier = Modifier.fillMaxWidth())
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
            Text("Maximum 20 références par côté, 9 exemplaires par référence.", fontSize = 11.sp)
        }
    }, confirmButton = { Button(onClick = { onSend(offer, wanted) }, enabled = !busy && offer.isNotEmpty() && wanted.isNotEmpty()) { Text(if (busy) "Envoi…" else "Envoyer la proposition") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Annuler") } })
}
@Composable
internal fun ColumnScope.TradeRequestsPanel(service: CommunityService, trades: List<PlayerTrade>, profiles: Collection<PlayerProfile>,
    catalog: List<CatalogCard>, busy: Boolean, onAnswer: (PlayerTrade, String) -> Unit, onTalk: (String) -> Unit) {
    Text("Demandes d’échange", style = MaterialTheme.typography.titleLarge)
    Text("Pour proposer un échange, ouvre un ami puis touche Échange.", fontSize = 12.sp)
    if (trades.isEmpty()) Text("Aucune proposition pour le moment.")
    trades.forEach { trade ->
        val peer = trade.members.firstOrNull { it != service.uid }.orEmpty()
        val incoming = trade.requester != service.uid
        val offer = remember(trade.offer) { runCatching { CommunitySocial.decodeTrade(trade.offer) }.getOrNull() }
        val wanted = remember(trade.wanted) { runCatching { CommunitySocial.decodeTrade(trade.wanted) }.getOrNull() }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profiles.firstOrNull { it.id == peer }?.name ?: "Ami", fontWeight = FontWeight.Bold)
            Text(when (trade.status) { "pending" -> if (incoming) "Proposition reçue" else "En attente de réponse"; "accepted" -> "Proposition acceptée"; "declined" -> "Proposition refusée"; else -> "Proposition annulée" })
            if (offer == null || wanted == null) Text("Proposition illisible. Tu peux la refuser ou l’annuler.") else {
                TradeCardList(if (incoming) "Il propose" else "Je propose", offer, catalog)
                TradeCardList(if (incoming) "Il recherche" else "Je recherche", wanted, catalog)
            }
            if (trade.status == "pending") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (incoming) {
                    Button(onClick = { onAnswer(trade, "accepted") }, enabled = !busy && offer != null && wanted != null) { Text("Accepter") }
                    OutlinedButton(onClick = { onAnswer(trade, "declined") }, enabled = !busy) { Text("Refuser") }
                } else OutlinedButton(onClick = { onAnswer(trade, "cancelled") }, enabled = !busy) { Text("Annuler la proposition") }
            }
            TextButton(onClick = { onTalk(peer) }, enabled = !busy) { Text("En discuter par message") }
            if (trade.status == "accepted") Text("Organisez la remise des cartes dans la messagerie. Mets ta collection à jour après l’échange.", fontSize = 12.sp)
        } }
    }
}
@Composable private fun TradeCardList(title: String, items: List<TradeSelection>, catalog: List<CatalogCard>) {
    Text(title, fontWeight = FontWeight.SemiBold)
    items.forEach { item ->
        val card = catalog.firstOrNull { DeckConstruction.reference(it) == item.cardId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            card?.let { AsyncImage(it.imageUrl, it.name, Modifier.size(30.dp, 42.dp)) }
            Text("${item.quantity} × ${card?.name ?: item.cardId}\n${item.language} · ${item.finish}", fontSize = 12.sp)
        }
    }
}
