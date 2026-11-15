package fr.swucollection.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Read, inspect cards and make explicit inline changes, including incomplete drafts. */
@Composable
internal fun DeckConsultation(draft: DeckDraft, catalog: List<CatalogCard>, onClose: () -> Unit, onEdit: () -> Unit,
    details: CardDetails = CardDetails.empty(), onChange: ((DeckDraft) -> Unit)? = null,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit = { _, _ -> }) {
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<List<DeckDraft>>(emptyList()) }
    fun change(next: DeckDraft) {
        if (onChange == null) return
        runCatching { onChange(next) }.onSuccess { undo = (undo + draft).takeLast(20); error = null }
            .onFailure { error = it.message ?: "Modification non enregistrée. Réessaie." }
    }
    fun preview(card: CatalogCard) {
        val visible = (listOfNotNull(if(draft.leaderChosen)byId[draft.deck.leader] else null,
            if(draft.baseChosen)byId[draft.deck.base] else null) + (draft.deck.deck + draft.deck.sideboard).mapNotNull { byId[it.cardId] }).distinctBy(DeckConstruction::reference)
        onPreview(card, visible)
    }
    fun quantity(entry: DeckEntry, zone: DeckEditor.Zone, delta: Int) {
        runCatching {
            val card = byId[entry.cardId]
            val next = if (delta > 0) {
                require(card != null) { "Cette référence doit être retrouvée dans le catalogue avant d’en ajouter." }
                DeckCompanion.add(draft.deck, card, catalog, details, zone)
            } else DeckEditor.setQuantity(draft.deck, entry.cardId, (entry.count + delta).coerceAtLeast(0), zone)
            change(draft.copy(deck = next))
        }.onFailure { error = it.message ?: "Modification impossible." }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(draft.name.ifBlank { "Mon deck" }) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("${draft.deck.deck.sumOf { it.count }} cartes · ${draft.deck.sideboard.sumOf { it.count }} en réserve")
                if (!draft.leaderChosen || !draft.baseChosen || draft.deck.deck.sumOf { it.count } < 50) Text("Deck en cours · tu peux le compléter plus tard.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onChange != null && undo.isNotEmpty()) TextButton(onClick = {
                    val previous = undo.last()
                    runCatching { onChange(previous) }.onSuccess { undo = undo.dropLast(1); error = null }.onFailure { error = it.message }
                }) { Text("Annuler la dernière modification") }
            }
            item {
                if (draft.leaderChosen) DeckConsultationCard(DeckEntry(draft.deck.leader, 1), byId, ::preview)
                else Text("Leader à choisir")
                if (onChange != null) TextButton(onClick = { adding = "Leader" }) { Text(if(draft.leaderChosen)"Changer le leader" else "Choisir le leader") }
            }
            item {
                if (draft.baseChosen) DeckConsultationCard(DeckEntry(draft.deck.base, 1), byId, ::preview)
                else Text("Base à choisir")
                if (onChange != null) TextButton(onClick = { adding = "Base" }) { Text(if(draft.baseChosen)"Changer la base" else "Choisir la base") }
            }
            item {
                Text("Deck principal", style = MaterialTheme.typography.titleSmall)
                if (onChange != null) TextButton(onClick = { adding = "Deck" }) { Text("Ajouter au deck") }
            }
            if (draft.deck.deck.isEmpty()) item { Text("Aucune carte pour le moment.") }
            items(draft.deck.deck, key = { "main:" + it.cardId }) { entry ->
                DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.MAIN, delta) } })
            }
            if (draft.deck.sideboard.isNotEmpty() || onChange != null) {
                item {
                    Text("Réserve", style = MaterialTheme.typography.titleSmall)
                    if (onChange != null) TextButton(onClick = { adding = "Réserve" }) { Text("Ajouter à la réserve") }
                }
                items(draft.deck.sideboard, key = { "side:" + it.cardId }) { entry ->
                    DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.SIDEBOARD, delta) } })
                }
            }
            if (draft.collectionEntries.isNotEmpty()) item { Text("${draft.collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente de validation.") }
        }
    }, confirmButton = { TextButton(onClick = onEdit) { Text("Modifier") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } })
    adding?.let { target -> InlineCardPicker(catalog, target, { adding = null }) { card ->
        runCatching {
            val next = DeckCompanion.add(draft.deck, card, catalog, details, if(target=="Réserve")DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN)
            change(draft.copy(deck=next, leaderChosen=draft.leaderChosen || target=="Leader", baseChosen=draft.baseChosen || target=="Base"))
            if(target in listOf("Leader","Base"))adding=null
        }.onFailure { error=it.message; adding=null }
    } }
}

@Composable
private fun DeckConsultationCard(entry: DeckEntry, cards: Map<String, CatalogCard>, onPreview: (CatalogCard) -> Unit,
    onQuantity: ((Int) -> Unit)? = null) {
    val card = cards[entry.cardId]
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled=card!=null){card?.let(onPreview)},verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardArtwork(card, Modifier.size(48.dp, 64.dp))
            Text("${entry.count} × ${card?.name ?: entry.cardId}",modifier=Modifier.weight(1f),maxLines=3,overflow=TextOverflow.Ellipsis)
        }
        if(onQuantity!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={onQuantity(-1)}){Icon(Icons.Default.Remove,"Diminuer ${entry.cardId}")}
            Text("${entry.count}")
            IconButton(onClick={onQuantity(1)}){Icon(Icons.Default.Add,"Augmenter ${entry.cardId}")}
            TextButton(onClick={onQuantity(-entry.count)}){Text("Retirer")}
        }
    }
}

/** A bounded, lazy picker keeps the current list/deck open underneath. */
@Composable
internal fun InlineCardPicker(catalog:List<CatalogCard>,target:String,onClose:()->Unit,onChoose:(CatalogCard)->Unit) {
    var query by remember {mutableStateOf("")}
    val visible=remember(catalog,query,target){catalog.filter { card ->
        val type=DeckConstruction.type(card)
        val eligible=when(target){"Leader"->type in listOf("leader","chef");"Base"->type=="base";"Liste"->true;else->type in listOf("unite","unit","evenement","event","amelioration","upgrade")}
        eligible && DeckBrowser.search(card,query)
    }}
    AlertDialog(onDismissRequest=onClose,title={Text("Ajouter une carte · $target")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,{query=it.take(120)},label={Text("Rechercher une carte")},singleLine=true,modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp)) {
                if(visible.isEmpty())item{Text("Aucune carte pour cette recherche.")}
                items(visible,key={DeckConstruction.reference(it)}){card->
                    Row(Modifier.fillMaxWidth().clickable{onChoose(card)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CardArtwork(card,Modifier.size(40.dp,56.dp))
                        Column(Modifier.weight(1f)){Text(card.name,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${card.setCode} · ${card.number}",style=MaterialTheme.typography.labelSmall)}
                        Icon(Icons.Default.Add,"Ajouter ${card.name}")
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onClose){Text("Terminer")}})
}
