@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Read, inspect cards and make explicit inline changes, including incomplete drafts. */
@Composable
internal fun DeckConsultation(draft: DeckDraft, catalog: List<CatalogCard>, onClose: () -> Unit, onEdit: () -> Unit,
    details: CardDetails = CardDetails.empty(), onChange: ((DeckDraft) -> Unit)? = null,
    onPreview: ((CatalogCard, List<CatalogCard>) -> Unit)? = null) {
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<List<DeckDraft>>(emptyList()) }
    fun change(next: DeckDraft): Boolean {
        if (onChange == null) return false
        return runCatching { onChange(next) }.onSuccess { undo = (undo + draft).takeLast(20); error = null }
            .onFailure { error = it.message ?: "Modification non enregistrée. Réessaie." }.isSuccess
    }
    var grouping by rememberSaveable { mutableStateOf("Cartes") }
    var compareChanges by remember { mutableStateOf(false) }
    val originalDeck = remember { draft.deck }
    val mainEntries = remember(draft.deck.deck, byId, details) { draft.deck.deck.sortedWith(compareBy({ byId[it.cardId]?.let { card -> details.get(card)?.cost } ?: Int.MAX_VALUE }, { it.cardId })) }
    val gallery = remember(draft, byId, mainEntries) { (listOfNotNull(if (draft.leaderChosen) byId[draft.deck.leader] else null,
        if (draft.baseChosen) byId[draft.deck.base] else null) + (mainEntries + draft.deck.sideboard).mapNotNull { byId[it.cardId] }).distinctBy(DeckConstruction::reference) }
    var previewId by remember { mutableStateOf<String?>(null) }
    fun preview(card: CatalogCard) { previewId = DeckConstruction.reference(card) }
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
            Text(draft.name.ifBlank { "Mon deck" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onEdit) { Text("Modifier") }
            TextButton(onClick = onClose) { Text("Fermer") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Cartes", "Coût", "Type").forEach { mode -> FilterChip(grouping == mode, { grouping = mode }, label = { Text(if(mode == "Cartes") "Grille" else "Table · $mode") }) }
            if(onChange != null) TextButton(onClick = { compareChanges = true }) { Text("Avant / après") }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(((LocalConfiguration.current.screenWidthDp - 16) / 112).coerceAtLeast(3)), modifier = Modifier.weight(1f).fillMaxWidth().testTag("deck_overview_grid"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(color = ListPalette.Raised, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${draft.deck.deck.sumOf { it.count }} / 50 cartes", color = ListPalette.Gold, fontWeight = FontWeight.Bold)
                            Text("${draft.deck.sideboard.sumOf { it.count }} / 10 en réserve", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { (draft.deck.deck.sumOf { it.count } / 50f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = ListPalette.Gold)
                        Text(if (!draft.leaderChosen || !draft.baseChosen) "Choisis ton leader et ta base pour commencer." else "Touche une carte pour la lire en grand.", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                        error?.let { Text(it, color = ListPalette.Danger) }
                        if (onChange != null && undo.isNotEmpty()) TextButton(onClick = {
                            val previous = undo.last()
                            runCatching { onChange(previous) }.onSuccess { undo = undo.dropLast(1); error = null }.onFailure { error = it.message }
                        }) { Text("Annuler la dernière modification") }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckIdentitySlot("Leader", if (draft.leaderChosen) byId[draft.deck.leader] else null,
                        Modifier.weight(1f), ::preview, onChange?.let { { adding = "Leader" } })
                    DeckIdentitySlot("Base", if (draft.baseChosen) byId[draft.deck.base] else null,
                        Modifier.weight(1f), ::preview, onChange?.let { { adding = "Base" } })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeckSectionHeading("Deck principal", draft.deck.deck.sumOf { it.count }, onChange?.let { { adding = "Deck" } })
            }
            if (mainEntries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (onChange == null) "Aucune carte. Choisis Modifier pour compléter le deck." else "Ajoute tes premières cartes avec le bouton +.", color = ListPalette.Secondary, style = MaterialTheme.typography.bodySmall)
            }
            val tableGroups = if(grouping == "Cartes") mapOf("" to mainEntries) else mainEntries.groupBy { DesignDeckPlan.group(it, byId, details, grouping) }
            tableGroups.forEach { (heading, entries) ->
            if(heading.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }, key = "table:$heading") { Text("$heading · ${entries.sumOf { it.count }}", style = MaterialTheme.typography.titleSmall, color = ListPalette.Secondary) }
            gridItems(entries, key = { "main:" + it.cardId }) { entry ->
                DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.MAIN, delta) } })
            }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeckSectionHeading("Réserve", draft.deck.sideboard.sumOf { it.count }, onChange?.let { { adding = "Réserve" } })
            }
            if (draft.deck.sideboard.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Cartes de remplacement entre les manches.", color = ListPalette.Secondary, style = MaterialTheme.typography.bodySmall)
            }
            gridItems(draft.deck.sideboard, key = { "side:" + it.cardId }) { entry ->
                DeckConsultationCard(entry, byId, ::preview, onChange?.let { { delta: Int -> quantity(entry, DeckEditor.Zone.SIDEBOARD, delta) } })
            }
            if (draft.collectionEntries.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("${draft.collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente de validation.", style = MaterialTheme.typography.bodySmall) }
        }
       }
      }
     }
    if(compareChanges) DeckChangesDialog(originalDeck, draft.deck, catalog) { compareChanges = false }
    previewId?.let { id -> DeckCardGallery(gallery, id, { previewId = null }, onPreview?.let { callback -> { selected -> previewId = null; callback(selected, gallery) } }) }
    adding?.let { target -> InlineCardPicker(catalog, target, { adding = null }, details = details) { card ->
        val next = DeckCompanion.add(draft.deck, card, catalog, details, if (target == "Réserve") DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN)
        check(change(draft.copy(deck = next, leaderChosen = draft.leaderChosen || target == "Leader", baseChosen = draft.baseChosen || target == "Base"))) { error ?: "Ajout non enregistré. Réessaie." }
        if (target in listOf("Leader", "Base")) adding = null
    } }
}

internal fun deckCardLabel(raw: String): String = raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

@Composable
private fun DeckSectionHeading(title: String, count: Int, onAdd: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ListPalette.Gold)
        Text("$count", style = MaterialTheme.typography.labelLarge)
        if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Ajouter à $title") }
    }
}

@Composable
private fun DeckIdentitySlot(label: String, card: CatalogCard?, modifier: Modifier, onPreview: (CatalogCard) -> Unit, onChoose: (() -> Unit)?) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = ListPalette.Raised, border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = .35f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = ListPalette.Gold)
            if (card != null) CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(1.4f).clickable { onPreview(card) })
            else Box(Modifier.fillMaxWidth().aspectRatio(1.4f).then(if (onChoose != null) Modifier.clickable(onClick = onChoose) else Modifier), contentAlignment = Alignment.Center) {
                if (onChoose != null) Icon(Icons.Default.Add, "Choisir $label", tint = ListPalette.Gold) else Text("Non choisi", style = MaterialTheme.typography.labelSmall, color = ListPalette.Secondary)
            }
            if (card != null) Text(deckCardLabel(card.name).substringBefore(" — "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            if (onChoose != null) TextButton(onClick = onChoose, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text(if (card == null) "Choisir" else "Changer", maxLines = 1)
            }
        }
    }
}

@Composable
private fun DeckConsultationCard(entry: DeckEntry, cards: Map<String, CatalogCard>, onPreview: (CatalogCard) -> Unit,
    onQuantity: ((Int) -> Unit)? = null) {
    val card = cards[entry.cardId]
    var managing by remember(entry.cardId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().testTag("deck_overview_card_${entry.cardId}").clip(RoundedCornerShape(9.dp)).combinedClickable(enabled = card != null,
            onClick = { card?.let(onPreview) }, onLongClickLabel = "Modifier la quantité", onLongClick = { if (onQuantity != null) managing = true })) {
            CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(5f / 7f))
            Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), color = ListPalette.Background, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, ListPalette.Gold)) {
                Text("×${entry.count}", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = ListPalette.Gold, fontWeight = FontWeight.Bold)
            }
            if (onQuantity != null) IconButton(onClick = { managing = true }, modifier = Modifier.align(Alignment.TopEnd).background(ListPalette.Background.copy(alpha = .9f), RoundedCornerShape(8.dp)).size(36.dp)) {
                Icon(Icons.Default.MoreVert, "Modifier la quantité de ${deckCardLabel(card?.name ?: entry.cardId)}", tint = ListPalette.Gold)
            }
        }
        Text(deckCardLabel(card?.name ?: entry.cardId).substringBefore(" — "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        if (managing && onQuantity != null) CardQuantityDialog(deckCardLabel(card?.name ?: entry.cardId), entry.count,
            { managing = false }, { target -> onQuantity(target - entry.count) }, maximum = 3)
    }
}

/** Shared reader above both full-screen dialogs; navigation never edits quantities. */
@Composable
internal fun DeckCardGallery(cards: List<CatalogCard>, initialId: String, onClose: () -> Unit, onDetails: ((CatalogCard) -> Unit)? = null) {
    if (cards.isEmpty()) return
    var index by remember(initialId) { mutableStateOf(cards.indexOfFirst { DeckConstruction.reference(it) == initialId }.coerceAtLeast(0)) }
    val card = cards[index.coerceIn(0, cards.lastIndex)]
    var zoom by remember(card) { mutableStateOf(1f) }
    var offset by remember(card) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var reverse by remember(card) { mutableStateOf(false) }
    val transform = rememberTransformableState { scale, pan, _ ->
        zoom = (zoom * scale).coerceIn(1f, 4f)
        val moved = offset + pan
        offset = if (zoom > 1f) Offset(moved.x.coerceIn(-frame.width * (zoom - 1f) / 2f, frame.width * (zoom - 1f) / 2f),
            moved.y.coerceIn(-frame.height * (zoom - 1f) / 2f, frame.height * (zoom - 1f) / 2f)) else Offset.Zero
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("deck_card_gallery"), color = ListPalette.Background, contentColor = ListPalette.Text) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(deckCardLabel(card.name), Modifier.weight(1f).testTag("deck_gallery_title"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (onDetails != null) IconButton(onClick = { onDetails(card) }) { Icon(Icons.Default.Info, "Ouvrir la fiche complète") }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer la carte") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("deck_gallery_artwork").onSizeChanged { frame = it }
                    .cardSwipeNavigation(card, zoom == 1f, { if (index > 0) index-- }, { if (index < cards.lastIndex) index++ }).transformable(transform)
                    .pointerInput(card) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f; offset = Offset.Zero }) }, contentAlignment = Alignment.Center) {
                    CardArtwork(card, Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y), reverse = reverse)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { zoom = (zoom - .5f).coerceAtLeast(1f); offset = Offset.Zero }, enabled = zoom > 1f) { Icon(Icons.Default.Remove, "Zoom arrière") }
                    Text("${(zoom * 100).toInt()} %", Modifier.padding(top = 14.dp).testTag("deck_gallery_zoom"), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { zoom = (zoom + .5f).coerceAtMost(4f) }, enabled = zoom < 4f, modifier = Modifier.testTag("deck_gallery_zoom_in")) { Icon(Icons.Default.Add, "Zoom avant") }
                    TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Recentrer") }
                    if (DeckConstruction.type(card) in listOf("leader", "chef")) TextButton(onClick = { reverse = !reverse; zoom = 1f; offset = Offset.Zero }) { Text("Retourner") }
                }
                Text("Écarte les doigts ou touche deux fois pour zoomer.", style = MaterialTheme.typography.labelSmall, color = ListPalette.Secondary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.testTag("deck_gallery_previous")) { Icon(Icons.Default.ChevronLeft, "Carte précédente") }
                    Text("${index + 1} / ${cards.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { index++ }, enabled = index < cards.lastIndex, modifier = Modifier.testTag("deck_gallery_next")) { Icon(Icons.Default.ChevronRight, "Carte suivante") }
                }
            }
        }
    }
}

/** Full-screen illustrated catalogue: inspect a card first, add only with its button. */
@Composable
internal fun InlineCardPicker(catalog: List<CatalogCard>, target: String, onClose: () -> Unit, details: CardDetails = CardDetails.empty(), onChoose: (CatalogCard) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(DeckBrowserFilters()) }
    var filtering by remember { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var additions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var addError by remember { mutableStateOf<String?>(null) }
    val visible = remember(catalog, query, target, filters, details) { catalog.filter { card ->
        val type = DeckConstruction.type(card)
        val eligible = when (target) { "Leader" -> type in listOf("leader", "chef"); "Base" -> type == "base"; "Liste" -> true
            else -> type in listOf("unite", "unit", "evenement", "event", "amelioration", "upgrade") }
        eligible && DeckBrowser.search(card, query) && filters.matches(card, details.get(card), 0, target in listOf("Leader", "Base"))
    } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("deck_card_picker"), color = ListPalette.Background, contentColor = ListPalette.Text) {
            val keyboard = LocalSoftwareKeyboardController.current
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (target) { "Leader" -> "Choisir le leader"; "Base" -> "Choisir la base"; "Réserve" -> "Ajouter à la réserve"; "Liste" -> "Ajouter à la liste"; else -> "Ajouter au deck" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Terminer") }
                }
                OutlinedTextField(query, { query = it.take(120) }, label = { Text("Rechercher une carte") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { filtering = true }) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(6.dp)); Text("Filtres (${filters.activeCount})") }
                    Text("${visible.size} cartes", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                }
                addError?.let { Text(it, color = ListPalette.Danger, style = MaterialTheme.typography.bodySmall) }
                LazyVerticalGrid(columns = GridCells.Adaptive(144.dp), modifier = Modifier.weight(1f).fillMaxWidth().testTag("deck_picker_grid"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Aucune carte pour ces filtres.") }
                    gridItems(visible, key = DeckConstruction::reference) { card ->
                        val id = DeckConstruction.reference(card)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(if (DeckConstruction.type(card) in listOf("leader", "chef", "base")) 1.4f else 5f / 7f).clip(RoundedCornerShape(10.dp)).clickable { previewId = id }.testTag("deck_picker_card_$id"))
                            Text(deckCardLabel(card.name), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            Button(onClick = { runCatching { onChoose(card) }.onSuccess { additions = additions + (id to ((additions[id] ?: 0) + 1)); addError = null }.onFailure { addError = it.message ?: "Ajout impossible. Réessaie." } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("deck_picker_add_$id"), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                                Text(if (target in listOf("Leader", "Base")) "Choisir" else "Ajouter", maxLines = 1)
                            }
                            additions[id]?.let { Text("+$it ajouté(s)", style = MaterialTheme.typography.labelSmall, color = ListPalette.Gold) }
                        }
                    }
                }
            }
        }
    }
    previewId?.let { DeckCardGallery(visible, it, { previewId = null }) }
    if (filtering) DeckFilterDialog(filters, catalog, details, { filters = it }, { filtering = false }, identity = target in listOf("Leader", "Base"))
}
