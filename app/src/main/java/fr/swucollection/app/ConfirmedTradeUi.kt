package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun TradeValuation(offer: List<TradeSelection>, wanted: List<TradeSelection>, catalog: List<CatalogCard>) {
    val context=LocalContext.current.applicationContext
    val byId=remember(catalog) {catalog.associateBy(DeckConstruction::reference)}
    val quotes by produceState<Map<String,MarketQuote>>(emptyMap(),offer,wanted) {
        value=withContext(Dispatchers.IO) { (offer+wanted).distinctBy {it.cardId+":"+it.finish}.associate {item ->
            item.cardId+":"+item.finish to (byId[item.cardId]?.let {runCatching {CardmarketPrices.quote(context,it,tradeFinish(item.finish),false,false)}.getOrNull()} ?: MarketQuote(null))
        } }
    }
    val currency=remember {NumberFormat.getCurrencyInstance(Locale.FRANCE)}
    fun subtotal(items: List<TradeSelection>): Pair<Double,Int> {
        var amount=0.0;var missing=0
        items.forEach {item -> val price=quotes[item.cardId+":"+item.finish]?.amount?.takeIf {it.isFinite() && it>0};if(price==null) missing+=item.quantity else amount+=price*item.quantity }
        return amount to missing
    }
    val a=subtotal(offer);val b=subtotal(wanted)
    Text("Valeurs indicatives Cardmarket · cotes en cache, toutes langues, hors port",style=MaterialTheme.typography.bodySmall)
    Text("Proposition : ${currency.format(a.first)}${if(a.second>0) " · ${a.second} sans cote" else ""}\nRecherche : ${currency.format(b.first)}${if(b.second>0) " · ${b.second} sans cote" else ""}",style=MaterialTheme.typography.bodySmall)
    if(a.second==0 && b.second==0) Text("Écart : ${currency.format(kotlin.math.abs(a.first-b.first))}",style=MaterialTheme.typography.bodySmall)
    else Text("Comparaison partielle : les cotes absentes ne valent pas zéro.",style=MaterialTheme.typography.bodySmall)
}
@Composable
internal fun ConfirmedTradeAction(database: CollectionDatabase, uid: String, trade: PlayerTrade, catalog: List<CatalogCard>, onChanged: () -> Unit) {
    var applied by remember(uid,trade.id) {mutableStateOf(database.tradeApplied(uid,trade.id))}
    var open by remember {mutableStateOf(false)}
    var given by remember {mutableStateOf(false)};var received by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    if(applied) Text("Mise à jour de cet échange déjà enregistrée sur cette collection. Une annulation éventuelle se retrouve dans Historique.",style=MaterialTheme.typography.bodySmall)
    else OutlinedButton(onClick={given=false;received=false;error=null;open=true},modifier=Modifier.fillMaxWidth()) {Text("Cartes remises · mettre ma collection à jour")}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("Confirmer la remise des cartes")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("L’accord est accepté. Confirme maintenant la remise réelle : les cartes données seront retirées et les cartes reçues ajoutées, avec leurs langues et finitions.")
        Row {Checkbox(given,{given=it});Text("J’ai remis toutes les cartes promises.",Modifier.padding(top=10.dp))}
        Row {Checkbox(received,{received=it});Text("J’ai reçu et vérifié toutes les cartes attendues.",Modifier.padding(top=10.dp))}
        Text("Cette action met à jour uniquement ta collection. L’autre joueur confirme la remise et actualise la sienne dans son application. Tu peux annuler les quantités dans Historique.",style=MaterialTheme.typography.bodySmall)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=given && received,onClick={runCatching {database.applyConfirmedTrade(uid,trade,catalog)}.onSuccess {applied=true;open=false;onChanged()}.onFailure {error=it.message ?: "Mise à jour impossible ; aucune carte modifiée."}}) {Text("Confirmer et mettre à jour")}},dismissButton={TextButton(onClick={open=false}) {Text("Annuler")}})
}
