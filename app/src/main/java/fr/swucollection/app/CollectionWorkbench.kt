package fr.swucollection.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal fun CollectionDatabase.bulkCorrect(ids: Set<Long>, catalog: List<CatalogCard>, language: String?, finish: String?) = withHistory("Modification par lot · ${ids.size} lignes") {
    require(ids.isNotEmpty() && ids.size <= 500)
    require(language == null || language in CardLanguages.supported)
    require(finish == null || finish in ScanVariants.options)
    val current = allCards(); val selected = current.filter { it.id in ids }
    require(selected.size == ids.size) { "La sélection a changé. Actualise la collection." }
    val reserved = loans().reserved()
    require(selected.none { (reserved[loanKey(it)] ?: 0) > 0 }) { "Une carte sélectionnée est prêtée. Enregistre son retour avant de changer sa langue ou sa finition." }
    val next = selected.map { row ->
        val card = catalog.firstOrNull { MarketPricing.key(it.setCode, it.number) == MarketPricing.key(row.setCode, row.number) }
            ?: error("Référence absente du catalogue : ${row.name}")
        if (finish != null) {
            val choices = ScanVariants.detect(card).choices
            require(choices.size < ScanVariants.options.size) { "Finitions inconnues pour ${row.name} : corrige cette carte individuellement." }
            require(finish in choices) { "La finition choisie ne correspond pas au numéro de ${row.name}." }
        }
        row.copy(language = language ?: row.language, variant = finish ?: row.variant)
    }
    val merged = (current.filter { it.id !in ids } + next).groupBy { loanKey(it) }
    require(merged.values.all { group -> group.sumOf { it.quantity.toLong() } <= 9999 }) { "Le regroupement dépasserait 9 999 exemplaires." }
    val db = writableDatabase
    selected.forEach { db.delete("collection_cards", "id=?", arrayOf(it.id.toString())) }
    next.forEach { row ->
        val values = ContentValues().apply { put("name", row.name); put("set_code", row.setCode); put("card_number", row.number); put("variant", row.variant); put("language", row.language); put("quantity", row.quantity) }
        if (db.insertWithOnConflict("collection_cards", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1L)
            db.execSQL("UPDATE collection_cards SET quantity=quantity+? WHERE set_code=? AND card_number=? AND variant=? AND language=?", arrayOf(row.quantity, row.setCode, row.number, row.variant, row.language))
    }
}
internal fun CollectionDatabase.bulkList(ids: Set<Long>, list: String, catalog: List<CatalogCard>) {
    val db = writableDatabase; db.beginTransaction()
    try {
        val selected = allCards().filter { it.id in ids }; require(selected.size == ids.size && ids.isNotEmpty())
        val references = selected.map { row -> catalog.firstOrNull { MarketPricing.key(it.setCode, it.number) == MarketPricing.key(row.setCode, row.number) } ?: error("Carte absente du catalogue") }.distinctBy { MarketPricing.key(it.setCode, it.number) }
        val target = cardLists().firstOrNull { it.id == list } ?: error("Liste introuvable")
        // Add one reference, preserving any larger existing quantity and the inventory.
        references.forEach { card -> putListCard(list, card, maxOf(1, target.entries.firstOrNull { it.card.setCode == card.setCode && it.card.number == card.number }?.quantity ?: 0)) }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
@Composable
internal fun CollectionWorkbench(database: CollectionDatabase, catalog: List<CatalogCard>, decks: List<DeckStore.SavedDeck>, details: CardDetails, close: () -> Unit, changed: () -> Unit) {
    var rows by remember { mutableStateOf(database.allCards()) }; var tab by remember { mutableStateOf("Sélection") }
    var search by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var language by remember { mutableStateOf<String?>(null) }; var finish by remember { mutableStateOf<String?>(null) }
    var finishMenu by remember { mutableStateOf(false) }; var confirmation by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }; var status by remember { mutableStateOf<String?>(null) }
    val reserved = database.loans().reserved()
    val byRef = remember(catalog) { catalog.associateBy { MarketPricing.key(it.setCode, it.number) } }
    val demand = remember(decks, catalog, details) {
        val byDeckRef = catalog.associateBy { DeckConstruction.reference(it) }
        val result = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        decks.forEach { saved ->
            val entries = listOfNotNull(saved.deck.leader.takeIf { it.isNotBlank() }?.let { DeckEntry(it, 1) }, saved.deck.base.takeIf { it.isNotBlank() }?.let { DeckEntry(it, 1) }) + saved.deck.deck + saved.deck.sideboard
            entries.mapNotNull { entry -> byDeckRef[entry.cardId]?.let { details.family(it) to entry.count } }.groupBy { it.first }.forEach { (family, group) ->
                result.getOrPut(family) { mutableListOf() }.add(saved.name to group.sumOf { it.second })
            }
        }
        result
    }
    val visible = rows.filter { it.name.contains(search, true) || "${it.setCode} ${it.number}".contains(search, true) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.safeDrawingPadding().padding(12.dp).widthIn(max = 850.dp).fillMaxWidth().fillMaxHeight(.94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row { Text("Gérer ma collection", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = close) { Text("Fermer") } }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Sélection", "Disponibilité").forEach { title -> FilterChip(tab == title, { tab = title }, label = { Text(title) }) } }
                OutlinedTextField(search, { search = it.take(100) }, label = { Text("Nom, extension ou numéro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                status?.let { Text(it) }
                if (tab == "Sélection") {
                    Text("${selected.size} lignes sélectionnées · 500 maximum. La modification s’applique à tous leurs exemplaires.", style = MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick = { selected = visible.take(500).map { it.id }.toSet() }) { Text("Sélectionner les résultats") }; TextButton(onClick = { selected = emptySet() }) { Text("Tout désélectionner") } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (listOf<String?>(null) + CardLanguages.supported).forEach { lang -> FilterChip(language == lang, { language = lang }, label = { Text(lang ?: "Langue inchangée") }) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box { OutlinedButton(onClick = { finishMenu = true }) { Text(finish ?: "Finition inchangée") }; DropdownMenu(finishMenu, { finishMenu = false }) {
                            (listOf<String?>(null) + ScanVariants.options).forEach { value -> DropdownMenuItem(text = { Text(value ?: "Inchangée") }, onClick = { finish = value; finishMenu = false }) }
                        } }
                        Button(onClick = { confirmation = true }, enabled = selected.isNotEmpty() && (language != null || finish != null)) { Text("Appliquer au lot") }
                        Box { OutlinedButton(onClick = { listMenu = true }, enabled = selected.isNotEmpty()) { Text("Ajouter à une liste") }; DropdownMenu(listMenu, { listMenu = false }) {
                            val lists = database.cardLists(); if (lists.isEmpty()) DropdownMenuItem(text = { Text("Crée d’abord une liste dans Mes listes") }, onClick = { listMenu = false })
                            lists.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = { listMenu = false; runCatching { database.bulkList(selected, list.id, catalog) }.onSuccess { status = "Références ajoutées à ${list.name}. Quantités existantes conservées."; changed() }.onFailure { status = it.message } }) }
                        } }
                    }
                    Text("Les numéros sont conservés. Une finition incompatible bloque tout le lot. Les cartes prêtées doivent d’abord être rendues. Annulation disponible dans Historique.", style = MaterialTheme.typography.bodySmall)
                } else Text("En main = possédés moins prêts. Les decks indiquent leurs besoins et réutilisent tes exemplaires : ils ne les réservent pas automatiquement.", style = MaterialTheme.typography.bodySmall)
                }
                    if (tab == "Sélection") items(visible, key = { it.id }) { row -> OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(8.dp)) {
                        Checkbox(row.id in selected, { check -> selected = if (check) (selected + row.id).take(500).toSet() else selected - row.id })
                        Column(Modifier.weight(1f)) { Text(row.name); Text("${row.setCode} ${row.number} · ${row.variant} · ${row.language} · ×${row.quantity}", style = MaterialTheme.typography.bodySmall) }
                    } } }
                    else items(visible.groupBy { row -> byRef[MarketPricing.key(row.setCode, row.number)]?.let { details.family(it) } ?: MarketPricing.key(row.setCode, row.number) }.values.toList(), key = { it.first().id }) { group ->
                        val card = byRef[MarketPricing.key(group.first().setCode, group.first().number)]
                        val family = card?.let { details.family(it) }
                        val uses = demand[family].orEmpty()
                        val owned = group.sumOf { it.quantity }; val lent = group.sumOf { reserved[loanKey(it)] ?: 0 }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(group.first().name); Text("Possédés $owned · Prêtés $lent · En main ${(owned - lent).coerceAtLeast(0)}")
                            Text(if (uses.isEmpty()) "Aucun deck enregistré" else "Dans ${uses.size} deck(s) · besoin maximum ${(uses.maxOf { it.second })} exemplaire(s), réserve incluse")
                            uses.take(5).forEach { Text("${it.first} · ×${it.second}", style = MaterialTheme.typography.bodySmall) }
                        } }
                    }
                }
            }
        }
    }
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("Modifier ${selected.size} lignes ?") }, text = { Text("Langue : ${language ?: "inchangée"}. Finition : ${finish ?: "inchangée"}. Le nombre total d’exemplaires est conservé, les lignes identiques sont regroupées.") }, confirmButton = { TextButton(onClick = { runCatching { database.bulkCorrect(selected, catalog, language, finish) }.onSuccess { rows = database.allCards(); selected = emptySet(); status = "Lot modifié. Tu peux annuler dans Historique."; changed() }.onFailure { status = it.message }; confirmation = false }) { Text("Confirmer") } }, dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Annuler") } })
}
