package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchFacetPanel(value: SearchFacets,sets: List<CatalogSet>,catalog: List<CatalogCard>,details: CardDetails,onApply:(SearchFacets)->Unit) {
    var open by remember {mutableStateOf(false)}
    var draft by remember(value) {mutableStateOf(value)}
    var minimum by remember(value) {mutableStateOf(value.minCost?.toString().orEmpty())}
    var maximum by remember(value) {mutableStateOf(value.maxCost?.toString().orEmpty())}
    val count=listOf(value.set!="Toutes",value.rarity!="Toutes",value.type!="Tous",value.aspect!="Toutes",value.minCost!=null||value.maxCost!=null).count {it}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={draft=value;minimum=value.minCost?.toString().orEmpty();maximum=value.maxCost?.toString().orEmpty();open=true}) {Text("Filtres${if(count>0)" ($count)" else ""}")}
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value.set != "Toutes") InputChip(true, { onApply(value.copy(set = "Toutes")) }, label = { Text("${value.set} ×") })
            if (value.rarity != "Toutes") InputChip(true, { onApply(value.copy(rarity = "Toutes")) }, label = { Text("${value.rarity} ×") })
            if (value.type != "Tous") InputChip(true, { onApply(value.copy(type = "Tous")) }, label = { Text("${value.type} ×") })
            if (value.aspect != "Toutes") InputChip(true, { onApply(value.copy(aspect = "Toutes")) }, label = { Text("${aspectNames[value.aspect] ?: value.aspect} ×") })
            if (value.minCost != null || value.maxCost != null) InputChip(true, { onApply(value.copy(minCost = null, maxCost = null)) }, label = { Text("Coût ${value.minCost ?: 0}–${value.maxCost ?: "∞"} ×") })
        }
        if(count>0)TextButton(onClick={onApply(SearchFacets())}) {Text("Réinitialiser")}
    }
    if(open) {
        val low=minimum.toIntOrNull();val high=maximum.toIntOrNull()
        val valid=(minimum.isBlank()||low!=null&&low in 0..99)&&(maximum.isBlank()||high!=null&&high in 0..99)&&(low==null||high==null||low<=high)
        AlertDialog(onDismissRequest={open=false},title={Text("Affiner la recherche")},text={Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FacetChoice("Extension",draft.set,listOf("Toutes" to "Toutes les extensions")+sets.map {it.code to it.name}) {draft=draft.copy(set=it)}
            FacetChoice("Rareté",draft.rarity,listOf("Toutes" to "Toutes")+catalog.map {it.rarity}.filter {it.isNotBlank()}.distinct().sorted().map {it to it}) {draft=draft.copy(rarity=it)}
            FacetChoice("Type",draft.type,listOf("Tous" to "Tous")+catalog.map {it.type}.filter {it.isNotBlank()}.distinct().sorted().map {it to it}) {draft=draft.copy(type=it)}
            FacetChoice("Aspect",draft.aspect,listOf("Toutes" to "Tous les aspects")+aspectNames.toList()) {draft=draft.copy(aspect=it)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minimum,{minimum=it.filter(Char::isDigit).take(2)},label={Text("Coût min.")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(maximum,{maximum=it.filter(Char::isDigit).take(2)},label={Text("Coût max.")},singleLine=true,modifier=Modifier.weight(1f))
            }
            if(!valid)Text("Le coût minimum doit être inférieur au maximum.")
            Text("Les cartes sans coût renseigné sont exclues si un coût est choisi.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=valid,onClick={onApply(draft.copy(minCost=low,maxCost=high));open=false}) {Text("Appliquer")}},dismissButton={TextButton(onClick={open=false}){Text("Annuler")}})
    }
}
@Composable
private fun FacetChoice(label: String,selected: String,options: List<Pair<String,String>>,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text("$label : ${options.firstOrNull {it.first==selected}?.second?:selected}")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {options.forEach {(key,title)->DropdownMenuItem(text={Text(title)},onClick={onSelect(key);expanded=false})}}
    }
}

