package fr.swucollection.app

import android.content.ContentValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class CollectionReviewIssue(val row: CollectionCard, val card: CatalogCard?, val reasons: List<String>)
internal object CollectionReview {
    fun quantityKey(row: CollectionCard) = "quantity:${row.id}:${row.setCode}:${row.number}:${row.variant}:${row.language}:${row.quantity}"
    fun find(rows: List<CollectionCard>, catalog: List<CatalogCard>, accepted: Set<String> = emptySet()): List<CollectionReviewIssue> {
        if (catalog.isEmpty()) return emptyList() // An unloaded catalogue is not an inventory error.
        val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
        return rows.mapNotNull { row ->
            val card = byKey[MarketPricing.key(row.setCode, row.number)]
            val reasons = buildList {
                if (row.language !in CardLanguages.supported) add("Langue à préciser")
                if (card == null) add("Référence absente du catalogue chargé")
                else if (row.variant !in ScanVariants.detect(card).choices) add("Finition incompatible avec cette référence")
                if (row.quantity >= 20 && quantityKey(row) !in accepted) add("Quantité inhabituelle : ${row.quantity} exemplaires à confirmer")
            }
            reasons.takeIf { it.isNotEmpty() }?.let { CollectionReviewIssue(row, card, it) }
        }
    }
}

/** One reversible transaction; a stale review or an outstanding loan cannot lose copies. */
internal fun CollectionDatabase.correctReviewedRow(expected: CollectionCard, card: CatalogCard, language: String, finish: String, quantity: Int) = withHistory("Correction après vérification") {
    require(quantity in 1..9999 && language in CardLanguages.supported)
    require(MarketPricing.key(expected.setCode, expected.number) == MarketPricing.key(card.setCode, card.number))
    require(finish in ScanVariants.detect(card).choices)
    val rows = allCards()
    check(rows.firstOrNull { it.id == expected.id } == expected) { "Cette ligne a changé. Ferme puis actualise la vérification." }
    val lent = loans().reserved()[loanKey(expected)] ?: 0
    val relabel = language != expected.language || finish != expected.variant
    require((!relabel || lent == 0) && quantity >= lent) { "Des exemplaires sont prêtés. Enregistre leur retour avant cette correction." }
    val target = rows.firstOrNull { it.id != expected.id && it.setCode == expected.setCode && it.number == expected.number && it.variant == finish && it.language == language }
    val total = quantity.toLong() + (target?.quantity ?: 0)
    require(total <= 9999) { "Le regroupement dépasserait 9 999 exemplaires." }
    val db = writableDatabase
    val values = ContentValues().apply { put("language", language); put("variant", finish); put("quantity", total.toInt()) }
    if (target == null) db.update("collection_cards", values, "id=?", arrayOf(expected.id.toString()))
    else {
        db.delete("collection_cards", "id=?", arrayOf(expected.id.toString()))
        db.update("collection_cards", values, "id=?", arrayOf(target.id.toString()))
    }
}

@Composable
internal fun CollectionReviewPanel(database: CollectionDatabase, rows: List<CollectionCard>, catalog: List<CatalogCard>, changed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("collection_review", 0) }
    var accepted by remember { mutableStateOf(prefs.getStringSet("quantities", emptySet()).orEmpty().toSet()) }
    val issues = remember(rows, catalog, accepted) { CollectionReview.find(rows, catalog, accepted) }
    var editing by remember { mutableStateOf<CollectionReviewIssue?>(null) }
    Text("À vérifier · ${issues.size} ligne(s)", style = MaterialTheme.typography.titleMedium)
    Text("Ces points sont des invitations à vérifier. Les doublons et grandes quantités peuvent être volontaires ; rien n’est corrigé automatiquement.", style = MaterialTheme.typography.bodySmall)
    if (issues.isEmpty()) Text(if (catalog.isEmpty()) "Chargement du catalogue…" else "Aucun point à vérifier avec le catalogue actuel.")
    if (issues.size > 100) Text("Les 100 premiers points sont affichés. Utilise la recherche pour cibler une carte.")
    issues.take(100).forEach { issue -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(issue.row.name, style = MaterialTheme.typography.titleSmall)
        Text("${issue.row.setCode} ${issue.row.number} · ${issue.row.language} · ${issue.row.variant} · ×${issue.row.quantity}")
        issue.reasons.forEach { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        if (issue.card != null) TextButton(onClick = { editing = issue }) { Text("Vérifier et corriger") }
        else Text("Conserve cette ligne et actualise le catalogue dans Paramètres. Elle n’est pas supprimée.", style = MaterialTheme.typography.bodySmall)
        if (issue.row.quantity >= 20 && CollectionReview.quantityKey(issue.row) !in accepted) TextButton(onClick = {
            accepted = (accepted + CollectionReview.quantityKey(issue.row)).toList().takeLast(2000).toSet()
            prefs.edit().putStringSet("quantities", accepted).apply()
        }) { Text("La quantité est correcte") }
    } } }
    editing?.let { issue ->
        val card = issue.card ?: return@let
        var language by remember(issue) { mutableStateOf(issue.row.language) }
        var finish by remember(issue) { mutableStateOf(issue.row.variant) }
        var quantity by remember(issue) { mutableStateOf(issue.row.quantity.toString()) }
        var error by remember(issue) { mutableStateOf<String?>(null) }
        StableCollectorDialog(onDismissRequest = { editing = null }, title = { Text("Vérifier ${issue.row.name}") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardArtwork(card, Modifier.size(78.dp, 110.dp), language = language, variant = finish)
                CardLanguageChoices(language, { language = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { ScanVariants.detect(card).choices.forEach { choice ->
                    FilterChip(finish == choice, { finish = choice }, label = { Text(choice) })
                } }
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(4) }, label = { Text("Quantité réelle · 1 à 9 999") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Text("Avant : ${issue.row.quantity} × ${issue.row.language} · ${issue.row.variant}. La correction regroupe les lignes identiques et reste annulable dans Historique.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = language in CardLanguages.supported && finish in ScanVariants.detect(card).choices && (quantity.toIntOrNull() ?: 0) in 1..9999,
            onClick = { runCatching { database.correctReviewedRow(issue.row, card, language, finish, quantity.toInt()) }
                .onSuccess { editing = null; changed() }.onFailure { error = it.message } }) { Text("Confirmer la correction") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } })
    }
}
