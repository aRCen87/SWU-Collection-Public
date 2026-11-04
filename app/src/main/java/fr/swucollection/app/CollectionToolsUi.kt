package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CollectionValuePanel(cards: List<CollectionCard>,quotes: Map<String,MarketQuote>,sets: List<CatalogSet>) {
    val value=remember(cards,quotes){collectionValue(cards,quotes)}
    val money=remember {NumberFormat.getCurrencyInstance(Locale.FRANCE)}
    var expanded by remember {mutableStateOf(false)}
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Valeur estimée de ma collection",fontWeight=FontWeight.Bold)
        if(quotes.isEmpty()&&cards.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            Text((if(value.unpriced>0)"Sous-total coté : " else "Estimation : ")+money.format(value.low)+if(value.high!=value.low)" à ${money.format(value.high)}" else "",style=MaterialTheme.typography.titleLarge)
            Text("${value.priced} exemplaires cotés · ${value.unpriced} sans cote, non inclus")
            val dates=cards.mapNotNull {quotes[collectionPriceKey(it.setCode,it.number,it.variant)]}.filter {it.amount?.let {p->p.isFinite()&&p>0}==true}.map {it.date.take(10)}.filter {it.isNotBlank()}.distinct().sorted()
            if(dates.isNotEmpty())Text("Dates des cotes : ${MarketPricing.displayDate(dates.first())}"+if(dates.size>1)" au ${MarketPricing.displayDate(dates.last())}" else "",style=MaterialTheme.typography.bodySmall)
            Text("Tendances Cardmarket, toutes langues, hors port. Ce montant n’est pas une offre de rachat.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={expanded=!expanded}) {Text(if(expanded)"Masquer les valeurs par extension" else "Voir par extension")}
            if(expanded) cards.groupBy {it.setCode}.forEach { (code,rows)->val v=collectionValue(rows,quotes)
                Text("${sets.firstOrNull {it.code==code}?.name?:code} : ${money.format(v.low)} · ${v.unpriced} non cotés")
            }
        }
    }}
}
@Composable
internal fun CollectionHistoryPanel(database: CollectionDatabase,onChanged:()->Unit,modifier: Modifier) {
    var events by remember {mutableStateOf(database.history())};var message by remember {mutableStateOf<String?>(null)}
    val current=events.firstOrNull {!it.undone}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Les 100 dernières opérations. Un lot validé compte comme une seule opération.")
        Button(enabled=current?.reversible==true,onClick={runCatching {database.undoLastChange()}.onSuccess {message="Opération annulée : $it";events=database.history();onChanged()}.onFailure {message=it.message}}) {Text("Annuler la dernière modification")}
        message?.let {Text(it)}
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(events.isEmpty())item {Text("Les prochains ajouts et modifications apparaîtront ici.")}
            items(events,key={it.id}) {e->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                Text(e.label+if(e.undone)" · annulée" else "",fontWeight=FontWeight.SemiBold)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(e.time)))
                val changes=org.json.JSONArray(e.changes)
                for(i in 0 until minOf(changes.length(),5)) {val r=changes.getJSONObject(i);val b=r.optJSONObject("before");val a=r.optJSONObject("after");val c=a?:b?:continue
                    Text("${c.getString("name")} · ${c.getString("set")} ${c.getString("number")} · ${c.getString("variant")} : ${b?.optInt("quantity")?:0} → ${a?.optInt("quantity")?:0}",style=MaterialTheme.typography.bodySmall)
                }
                if(changes.length()>5)Text("Et ${changes.length()-5} autres références.")
            }}}
        }
    }
}
@Composable
internal fun RelatedPrintsPanel(card: CatalogCard,catalog: List<CatalogCard>,owned: List<CollectionCard>,details: CardDetails,onOpen:(CatalogCard,List<CatalogCard>)->Unit) {
    var expanded by remember(card.setCode,card.number){mutableStateOf(false)}
    val related=remember(card,catalog,details){details.related(card,catalog)}
    TextButton(onClick={expanded=!expanded}) {Text("Versions et réimpressions (${related.size}) ${if(expanded)"▴" else "▾"}")}
    if(expanded) {
        if(details.get(card)==null)Text("Les liens de cette référence ne sont pas encore identifiés.")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=240.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(related,key={MarketPricing.key(it.setCode,it.number)}) {c->
                val stock=owned.filter {it.setCode==c.setCode&&it.number==c.number}
                OutlinedCard(onClick={onOpen(c,related)},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(8.dp)) {
                    Text("${c.setCode} ${c.number} · ${ScanVariants.detect(c).variant?:"Finitions disponibles"}",fontWeight=FontWeight.SemiBold)
                    Text(if(stock.isEmpty())"Non possédée" else stock.joinToString {"${it.quantity} ${it.variant}"},style=MaterialTheme.typography.bodySmall)
                }}
            }
        }
    }
}
@Composable
internal fun DeckSummary(list: CardList,owned: List<CollectionCard>,catalog: List<CatalogCard>,details: CardDetails,rules: LegalityRules,onFormat:(String)->Unit) {
    var menu by remember {mutableStateOf(false)};var show by remember {mutableStateOf(false)}
    val report=remember(list,owned,catalog,details,rules){DeckAudit.report(list,owned,catalog,details,rules)}
    var formatsVisible by remember(list.id) { mutableStateOf(list.format != "Liste") }
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if (!formatsVisible) TextButton(onClick = { formatsVisible = true }) { Text("Options de vérification") }
        if (formatsVisible) Box {
            TextButton(onClick={menu=true}) {Text("Format : ${list.format}  ▾")}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {listOf("Liste","Premier","Twin Suns","Éternel").forEach {format->DropdownMenuItem(text={Text(format)},onClick={onFormat(format);menu=false})}}
        }
        Text("${report.needs.sumOf {minOf(it.required,it.owned)}} / ${report.needs.sumOf {it.required}} exemplaires disponibles pour cette liste")
        TextButton(onClick={show=true}) {Text("${report.needs.sumOf {it.missing}} manquantes · "+if(list.format=="Liste")"voir les quantités" else if(report.issues.isEmpty())"construction conforme" else "${report.issues.size} point(s) à vérifier")}
    }}
    if(show) AlertDialog(onDismissRequest={show=false},title={Text("Disponibilité et construction")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(list.format!="Liste") {
            Text("${list.format} · ${report.drawCount} cartes dans le deck principal, hors leaders et base.")
            report.issues.forEach {Text("• $it")}
            if(report.issues.isEmpty())Text("Les contrôles de construction sont conformes aux règles chargées. Réserve non gérée dans cette liste.")
            Text("Règles vérifiées : ${rules.checkedAt.take(10)}. Les variantes d’une même carte comptent ensemble.",style=MaterialTheme.typography.bodySmall)
        }
        Text("Cartes à trouver",fontWeight=FontWeight.Bold)
        val missing=report.needs.filter {it.missing>0}
        if(missing.isEmpty())Text("Tu possèdes les quantités nécessaires, toutes versions confondues.")
        missing.forEach {Text("${it.missing} × ${it.card.name} · ${it.owned} possédées sur ${it.required}")}
    }},confirmButton={TextButton(onClick={show=false}){Text("Fermer")}})
}

