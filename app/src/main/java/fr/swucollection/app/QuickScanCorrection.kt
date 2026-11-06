package fr.swucollection.app

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun CollectionDatabase.correctScannedCopy(entry: ScanBatchEntry, language: String, variant: String) = withHistory("Correction du dernier scan") {
    require(language in CardLanguages.supported && variant in ScanVariants.detect(entry.card).choices)
    val db = writableDatabase; val card = entry.card
    val source = arrayOf(card.setCode.trim().uppercase(), card.number.trim(), entry.variant, card.language)
    db.beginTransaction()
    try {
        val id = db.rawQuery("SELECT id FROM collection_cards WHERE set_code=? AND card_number=? AND variant=? AND language=? AND quantity>0", source).use {
            check(it.moveToFirst()) { "Ce scan n’est plus présent dans la collection. Ouvre sa fiche pour vérifier." }; it.getLong(0)
        }
        if (entry.variant != variant || card.language != language) {
            val target = arrayOf(source[0], source[1], variant, language)
            val count = db.rawQuery("SELECT quantity FROM collection_cards WHERE set_code=? AND card_number=? AND variant=? AND language=?", target).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            require(count < 9999) { "Cette version possède déjà 9 999 exemplaires." }
            db.execSQL("UPDATE collection_cards SET quantity=quantity-1 WHERE id=?", arrayOf(id))
            db.delete("collection_cards", "id=? AND quantity=0", arrayOf(id.toString()))
            val values = ContentValues().apply { put("name",card.name);put("set_code",source[0]);put("card_number",source[1]);put("variant",variant);put("language",language);put("quantity",1) }
            if (db.insertWithOnConflict("collection_cards",null,values,SQLiteDatabase.CONFLICT_IGNORE) == -1L)
                db.execSQL("UPDATE collection_cards SET quantity=quantity+1 WHERE set_code=? AND card_number=? AND variant=? AND language=?",target)
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
@Composable
internal fun QuickScanCorrection(entry: ScanBatchEntry, onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    var language by remember(entry) { mutableStateOf(entry.card.language) }
    var finish by remember(entry) { mutableStateOf(entry.variant) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Corriger le dernier scan") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${entry.card.name} · ${entry.card.setCode} ${entry.card.number}")
            Text("Langue")
            CardLanguageChoices(language, { language = it })
            Text("Finition compatible avec cette référence")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ScanVariants.detect(entry.card).choices.forEach { option -> FilterChip(finish == option, { finish = option }, label = { Text(option) }) }
            }
            Text("La correction s’applique à un exemplaire. Le scan reprend après validation.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = language in CardLanguages.supported && finish in ScanVariants.detect(entry.card).choices,
        onClick = { runCatching { onApply(language, finish) }.onFailure { error = it.message } }) { Text("Corriger et reprendre") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
