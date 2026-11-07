package fr.swucollection.app

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun ColumnScope.CardLoansPanel(db: CollectionDatabase,catalog: List<CatalogCard>) {
    var book by remember {mutableStateOf(db.loans())};var previous by remember {mutableStateOf<LoanBook?>(null)}
    var search by remember {mutableStateOf("")};var archive by remember {mutableStateOf(false)};var creating by remember {mutableStateOf(false)}
    var returning by remember {mutableStateOf<CardLoan?>(null)};var error by remember {mutableStateOf<String?>(null)}
    val byRef=remember(catalog) {catalog.associateBy {MarketPricing.key(it.setCode,it.number)}}
    val date=remember {SimpleDateFormat("dd/MM/yyyy",Locale.FRANCE)}
    fun save(next: LoanBook) {runCatching {db.putTool("loans",next.json())}.onSuccess {previous=book;book=next;error=null}.onFailure {error=it.message?:"Prêt non enregistré."}}
    Text("${book.rows.sumOf {it.outstanding}} exemplaire(s) prêté(s). Ils restent dans ta collection et sont exclus de tes cartes disponibles à l’échange.")
    Text("Les anciens partages d’échange sont des copies : actualise ta liste partagée et ton QR après un prêt ou un retour.",style=MaterialTheme.typography.bodySmall)
    Row(verticalAlignment=Alignment.CenterVertically) {Button(onClick={creating=true},modifier=Modifier.weight(1f)) {Text("Enregistrer un prêt")}; TextButton(onClick={previous?.let {old ->runCatching {db.putTool("loans",old.json())}.onSuccess {book=old;previous=null}.onFailure {error="Annulation impossible."}}},enabled=previous!=null) {Text("Annuler")}}
    OutlinedTextField(search,{search=it.take(100)},label={Text("Rechercher un joueur ou une carte")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Row(verticalAlignment=Alignment.CenterVertically) {Text("Afficher aussi les prêts rendus",Modifier.weight(1f));Switch(archive,{archive=it})}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    val rows=book.rows.filter {(archive || it.outstanding>0) && (it.borrower.contains(search,true)||it.name.contains(search,true))}
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(rows.isEmpty()) item {Text("Aucun prêt à afficher.")}
        items(rows,key={it.id}) {row ->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            TradePreviewRow(byRef[MarketPricing.key(row.set,row.number)],row.name,row.outstanding,row.language,row.variant)
            Text("À ${row.borrower} · ${row.returned}/${row.quantity} rendu(s) · prêt du ${date.format(Date(row.date))}")
            if(row.due>0) Text("Retour prévu le ${date.format(Date(row.due))}",color=if(row.outstanding>0 && System.currentTimeMillis()>row.due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if(row.outstanding>0) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={save(book.returned(row.id,1))}) {Text("1 exemplaire rendu")}
                TextButton(onClick={returning=row}) {Text("Tout est rendu")}
            }
        }}}
    }
    returning?.let {row ->AlertDialog(onDismissRequest={returning=null},title={Text("Confirmer le retour ?")},text={Text("${row.borrower} a rendu les ${row.outstanding} exemplaire(s) encore prêtés de ${row.name}.")},confirmButton={TextButton(onClick={save(book.returned(row.id,row.outstanding));returning=null}) {Text("Confirmer")}},dismissButton={TextButton(onClick={returning=null}) {Text("Annuler")} })}
    if(creating) LoanEditor(db.allCards(),book,byRef,{creating=false}) {card,borrower,qty,due ->
        runCatching {db.loans().add(card,borrower,qty,due,System.currentTimeMillis())}.onSuccess {save(it);creating=false}.onFailure {error=it.message}
    }
}
@Composable
private fun LoanEditor(owned: List<CollectionCard>,book: LoanBook,catalog: Map<String,CatalogCard>,onClose: ()->Unit,onSave: (CollectionCard,String,Int,Long)->Unit) {
    var selected by remember {mutableStateOf<CollectionCard?>(null)};var search by remember {mutableStateOf("")};var borrower by remember {mutableStateOf("")};var qty by remember {mutableStateOf("1")};var due by remember {mutableLongStateOf(0)}
    val context=LocalContext.current;val reserved=book.reserved();val date=SimpleDateFormat("dd/MM/yyyy",Locale.FRANCE)
    AlertDialog(onDismissRequest=onClose,title={Text("Prêter des cartes")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val card=selected
        if(card==null) {
            OutlinedTextField(search,{search=it},label={Text("Carte de ma collection")},singleLine=true,modifier=Modifier.fillMaxWidth())
            val candidates=owned.filter {it.quantity>(reserved[loanKey(it)]?:0) && (it.name.contains(search,true) || (it.setCode+" "+it.number).contains(search,true))}.take(40)
            if(candidates.isEmpty()) Text("Aucun exemplaire disponible pour cette recherche.")
            candidates.forEach {row ->OutlinedCard(onClick={selected=row},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(8.dp)) {TradePreviewRow(catalog[MarketPricing.key(row.setCode,row.number)],row.name,row.quantity-(reserved[loanKey(row)]?:0),row.language,row.variant)}}}
            Text("40 résultats maximum : précise la recherche pour retrouver une autre carte.",style=MaterialTheme.typography.bodySmall)
        } else {
            TradePreviewRow(catalog[MarketPricing.key(card.setCode,card.number)],card.name,card.quantity-(reserved[loanKey(card)]?:0),card.language,card.variant)
            TextButton(onClick={selected=null}) {Text("Choisir une autre carte")}
            OutlinedTextField(borrower,{borrower=it.take(80)},label={Text("À qui ?")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(qty,{qty=it.filter(Char::isDigit).take(4)},label={Text("Quantité prêtée")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={val c=Calendar.getInstance();DatePickerDialog(context,{_,y,m,d ->due=Calendar.getInstance().apply {set(y,m,d,23,59,59)}.timeInMillis},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()}) {Text(if(due==0L) "Retour prévu (facultatif)" else "Retour : ${date.format(Date(due))}")}
            if(due>0) TextButton(onClick={due=0}) {Text("Sans date de retour")}
            Text("Ce registre est privé. Enregistrer un prêt n’envoie aucun message.",style=MaterialTheme.typography.bodySmall)
        }
    }},confirmButton={val c=selected;TextButton(onClick={if(c!=null) onSave(c,borrower,qty.toInt(),due)},enabled=c!=null && borrower.isNotBlank() && (qty.toIntOrNull()?:0) in 1..((c?.quantity?:0)-(c?.let {reserved[loanKey(it)]}?:0))) {Text("Enregistrer")}},dismissButton={TextButton(onClick=onClose) {Text("Annuler")} })
}
