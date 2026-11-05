package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ScanBatchEntry(val card: CatalogCard, val variant: String, val quantity: Int) {
    val resolved get() = card.language in CardLanguages.supported && variant in ScanVariants.options
}

/** Resolve copies explicitly; mixed foil/normal copies need not be assigned the same finish. */
internal fun resolveScanEntries(entries: List<ScanBatchEntry>, entry: ScanBatchEntry,
    language: String, variant: String, quantity: Int): List<ScanBatchEntry> {
    require(entry in entries && quantity in 1..entry.quantity)
    require(language in CardLanguages.supported && variant in ScanVariants.detect(entry.card).choices)
    val remaining = entries.toMutableList()
    val index = remaining.indexOf(entry)
    if (quantity == entry.quantity) remaining.removeAt(index)
    else remaining[index] = entry.copy(quantity = entry.quantity - quantity)
    val resolved = entry.copy(card = entry.card.copy(language = language), variant = variant, quantity = quantity)
    val existing = remaining.indexOfFirst { it.card.setCode == resolved.card.setCode && it.card.number == resolved.card.number && it.card.language == language && it.variant == variant }
    if (existing < 0) remaining.add(resolved)
    else {
        val total = remaining[existing].quantity + quantity
        require(total <= 9999)
        remaining[existing] = remaining[existing].copy(quantity = total)
    }
    return remaining
}
internal class ScanBatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("scan_batch", 0)
    var id by mutableStateOf(prefs.getString("id", null) ?: UUID.randomUUID().toString())
        private set
    var entries by mutableStateOf(runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            ScanBatchEntry(cardFromJson(it), it.getString("variant"), it.getInt("quantity").also { n -> require(n in 1..9999) }) }
        }
    }.getOrDefault(emptyList()))
        private set
    private fun save(nextId: String, next: List<ScanBatchEntry>) {
        val array = JSONArray().apply { next.forEach { put(cardToJson(it.card).put("variant", it.variant).put("quantity", it.quantity)) } }
        check(prefs.edit().putString("id", nextId).putString("entries", array.toString()).commit()) { "Impossible d’enregistrer le lot." }
        id = nextId; entries = next
    }
    fun add(card: CatalogCard, variant: String) {
        val index = entries.indexOfFirst { MarketPricing.key(it.card.setCode, it.card.number) == MarketPricing.key(card.setCode, card.number) && it.variant == variant && it.card.language == card.language }
        val next = entries.toMutableList()
        if (index < 0) next += ScanBatchEntry(card, variant, 1)
        else { require(next[index].quantity < 9999); next[index] = next[index].copy(quantity = next[index].quantity + 1) }
        save(id, next)
    }
    fun change(entry: ScanBatchEntry, delta: Int) {
        val next = entries.mapNotNull { if (it != entry) it else (it.quantity + delta).takeIf { n -> n in 1..9999 }?.let { n -> it.copy(quantity = n) } }
        save(id, next)
    }
    fun resolve(entry: ScanBatchEntry, language: String, variant: String, quantity: Int) =
        save(id, resolveScanEntries(entries, entry, language, variant, quantity))
    fun clear() = save(UUID.randomUUID().toString(), emptyList())
}

internal fun CollectionDatabase.isScanBatchCommitted(id: String): Boolean =
    readableDatabase.rawQuery("SELECT id FROM scan_batches WHERE id=?", arrayOf(id)).use { it.moveToFirst() }

internal fun CollectionDatabase.commitScanBatch(id: String, entries: List<ScanBatchEntry>, details: CardDetails = CardDetails.empty()) = withHistory("Ajout d’un lot scanné") {
    require(id.isNotBlank() && entries.isNotEmpty())
    require(entries.all { it.quantity in 1..9999 && it.resolved && it.card.name.isNotBlank() })
    val db = writableDatabase
    db.beginTransaction()
    try {
        val done = db.rawQuery("SELECT id FROM scan_batches WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
        if (!done) {
            entries.forEach { entry ->
                val previous = db.rawQuery("SELECT quantity FROM collection_cards WHERE set_code=? AND card_number=? AND variant=? AND language=?",
                    arrayOf(entry.card.setCode.trim().uppercase(),entry.card.number.trim(),entry.variant,entry.card.language)).use { if(it.moveToFirst()) it.getLong(0) else 0L }
                require(previous + entry.quantity <= 9999) { "Maximum 9 999 exemplaires pour cette langue et cette finition." }
                val values = android.content.ContentValues().apply {
                    put("name", entry.card.name); put("set_code", entry.card.setCode.trim().uppercase())
                    put("card_number", entry.card.number.trim()); put("variant", entry.variant); put("quantity", entry.quantity); put("language",entry.card.language)
                }
                if (db.insertWithOnConflict("collection_cards", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) == -1L) {
                    db.execSQL("UPDATE collection_cards SET quantity=quantity+? WHERE set_code=? AND card_number=? AND variant=? AND language=?",
                        arrayOf(entry.quantity, entry.card.setCode.trim().uppercase(), entry.card.number.trim(), entry.variant, entry.card.language))
                }
            }
            recordBadgeEvent("scan", "batch:" + id, entries.sumOf { it.quantity })
            entries.forEach { entry -> recordScannedBadgeFamily(entry.card, details.family(entry.card)) }
            db.execSQL("INSERT INTO scan_batches(id) VALUES(?)", arrayOf(id))
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

@Composable
internal fun ScanBatchPanel(batch: ScanBatchStore, committed: Boolean, onCommit: () -> Unit) {
    var discard by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    if (batch.entries.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lot à valider · ${batch.entries.sumOf { it.quantity }} carte(s)")
            Text(if (committed) "Ce lot a déjà été ajouté. Termine sa fermeture avant de scanner de nouvelles cartes." else "Vérifie les références, variantes et quantités. Ce lot est conservé si tu fermes l’appli.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(batch.entries, key = { it.card.setCode + ":" + it.card.number + ":" + it.variant + ":" + it.card.language }) { entry ->
                    Column {
                        ScannerCardSummary(entry.card, entry.variant)
                        if (!entry.resolved && !committed) ScanEntryReview(entry) { language, variant, quantity ->
                            runCatching { batch.resolve(entry, language, variant, quantity) }.onFailure { message = it.message }
                        }
                        Row {
                            TextButton(enabled = !committed, onClick = { runCatching { batch.change(entry, -1) }.onFailure { message = it.message } }) { Text("−") }
                            Text("${entry.quantity}", modifier = Modifier.padding(top = 12.dp))
                            TextButton(enabled = !committed, onClick = { runCatching { batch.change(entry, 1) }.onFailure { message = it.message } }) { Text("+") }
                        }
                    }
                }
            }
            if (batch.entries.any { !it.resolved }) Text("Précise les langues et finitions en attente avant de valider.")
            Button(enabled = committed || batch.entries.all { it.resolved }, onClick = { runCatching(onCommit).onSuccess { message = "Lot ajouté à la collection." }.onFailure { message = it.message } }, modifier = Modifier.fillMaxWidth()) { Text(if (committed) "Fermer le lot déjà ajouté" else "Valider · Ajouter à ma collection") }
            TextButton(enabled = !committed, onClick = { discard = true }) { Text("Abandonner ce lot") }
            message?.let { Text(it) }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Abandonner le lot ?") },
        text = { Text("Les cartes en attente seront retirées du lot. Ta collection reste conservée.") },
        confirmButton = { TextButton(onClick = { runCatching { batch.clear() }.onFailure { message = it.message }; discard = false }) { Text("Abandonner") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Continuer") } })
}



@Composable
internal fun ScanEntryReview(entry: ScanBatchEntry, onResolve: (String, String, Int) -> Unit) {
    var language by remember(entry) { mutableStateOf(entry.card.language) }
    var variant by remember(entry) { mutableStateOf(entry.variant) }
    var variantMenu by remember(entry) { mutableStateOf(false) }
    if (entry.card.language !in CardLanguages.supported) CardLanguageChoices(language, { language = it })
    if (entry.variant !in ScanVariants.options) Box {
        OutlinedButton(onClick = { variantMenu = true }) { Text(variant.ifBlank { "Choisir la finition" }) }
        DropdownMenu(expanded = variantMenu, onDismissRequest = { variantMenu = false }) {
            ScanVariants.detect(entry.card).choices.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { variant = option; variantMenu = false })
            }
        }
    }
    val ready = language in CardLanguages.supported && variant in ScanVariants.detect(entry.card).choices
    TextButton(enabled = ready, onClick = { onResolve(language, variant, 1) }) { Text("Appliquer à 1 exemplaire") }
    if (entry.quantity > 1) TextButton(enabled = ready, onClick = { onResolve(language, variant, entry.quantity) }) {
        Text("Appliquer aux ${entry.quantity} exemplaires identiques")
    }
}
