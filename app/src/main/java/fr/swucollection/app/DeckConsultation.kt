@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      Surface(Modifier.fillMaxSize().testTag("swu_deck_consultation_fullscreen"), color = ListPalette.Background, contentColor = ListPalette.Text) {
       Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(draft.name.ifBlank { "Mon deck" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onEdit) { Text("Modifier") }
            TextButton(onClick = onClose) { Text("Fermer") }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(135.dp), modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${draft.deck.deck.sumOf { it.count }} cartes · ${draft.deck.sideboard.sumOf { it.count }} en réserve")
                if (!draft.leaderChosen || !draft.baseChosen || draft.deck.deck.sumOf { it.count } < 50) Text("Deck en cours · tu peux le compléter plus tard.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onChange != null && undo.isNotEmpty()) TextButton(onClick = {
                    val previous = undo.last()
                    runCatching { onChange(previous) }.onSuccess { undo = undo.dropLast(1); error = null }.onFailure { error = it.message }
                }) { Text("Annuler la dernière modification") }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (draft.leaderChosen) DeckConsultationCard(DeckEntry(draft.deck.leader, 1), byId, ::preview)
                        else Text("Leader à choisir")
                        if (onChange != null) TextButton(onClick = { adding = "Leader" }) { Text(if(draft.leaderChosen)"Changer le leader" else "Choisir le leader") }
                    }
                    Column(Modifier.weight(1f)) {
                        if (draft.baseChosen) DeckConsultationCard(DeckEntry(draft.deck.base, 1), byId, ::preview)
                        else Text("Base à choisir")
                        if (onChange != null) TextButton(onClick = { adding = "Base" }) { Text(if(draft.baseChosen)"Changer la base" else "Choisir la base") }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Deck principal", style = MaterialTheme.typography.titleSmall)
                    if (onChange != null) TextButton(onClick = { adding = "Deck" }) { Text("Ajouter au deck") }
                }
            }
            if (draft.deck.deck.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Aucune carte pour le moment.") }
            gridItems(draft.deck.deck.sortedWith(compareBy({ byId[it.cardId]?.let { card -> details.get(card)?.cost } ?: Int.MAX_VALUE }, { it.cardId })), key = { "main:" + it.cardId }) { entry ->
                DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.MAIN, delta) } })
            }
            if (draft.deck.sideboard.isNotEmpty() || onChange != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text("Réserve", style = MaterialTheme.typography.titleSmall)
                        if (onChange != null) TextButton(onClick = { adding = "Réserve" }) { Text("Ajouter à la réserve") }
                    }
                }
                gridItems(draft.deck.sideboard, key = { "side:" + it.cardId }) { entry ->
                    DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.SIDEBOARD, delta) } })
                }
            }
            if (draft.collectionEntries.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("${draft.collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente de validation.") }
        }
       }
      }
     }
    adding?.let { target -> InlineCardPicker(catalog, target, { adding = null }, details = details) { card ->
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
    var managing by remember(entry.cardId) { mutableStateOf(false) }
    Column {
        Column(Modifier.fillMaxWidth().combinedClickable(enabled=card!=null,
            onClick={card?.let(onPreview)}, onLongClickLabel="Modifier la quantité",
            onLongClick={if(onQuantity!=null)managing=true}), horizontalAlignment = Alignment.CenterHorizontally) {
            CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(if (card?.let(DeckConstruction::type) in listOf("leader", "chef", "base")) 1.4f else 5f / 7f))
            Text("${entry.count} × ${card?.name ?: entry.cardId}", Modifier.fillMaxWidth(), maxLines=3, overflow=TextOverflow.Ellipsis)
        }
        if (onQuantity != null) TextButton(onClick = { managing = true }) { Text("Gérer · ×${entry.count}") }
        if (managing && onQuantity != null) CardQuantityDialog(card?.name ?: entry.cardId, entry.count,
            { managing = false }, { target -> onQuantity(target - entry.count) }, maximum = 3)
    }
}

/** A bounded, lazy picker keeps the current list/deck open underneath. */
@Composable
internal fun InlineCardPicker(catalog:List<CatalogCard>,target:String,onClose:()->Unit,details: CardDetails = CardDetails.empty(), onChoose:(CatalogCard)->Unit) {
    var query by remember {mutableStateOf("")}
    var filters by remember { mutableStateOf(DeckBrowserFilters()) }
    var filtering by remember { mutableStateOf(false) }
    val visible=remember(catalog,query,target,filters,details){catalog.filter { card ->
        val type=DeckConstruction.type(card)
        val eligible=when(target){"Leader"->type in listOf("leader","chef");"Base"->type=="base";"Liste"->true;else->type in listOf("unite","unit","evenement","event","amelioration","upgrade")}
        eligible && DeckBrowser.search(card,query) && filters.matches(card, details.get(card), 0, target in listOf("Leader", "Base"))
    }}
    AlertDialog(onDismissRequest=onClose,title={Text("Ajouter une carte · $target")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,{query=it.take(120)},label={Text("Rechercher une carte")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick = { filtering = true }) { Text("Filtres (${filters.activeCount})") }
            Text("${visible.size} carte(s)", style = MaterialTheme.typography.labelSmall)
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
    if (filtering) DeckFilterDialog(filters, catalog, details, { filters = it }, { filtering = false }, identity = target in listOf("Leader", "Base"))
}
