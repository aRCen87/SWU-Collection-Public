package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DeckAlternativesPanel(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails,
    rules: LegalityRules, onReplace: ((PortableDeck) -> Unit)?) {
    var targetId by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf(DeckEditor.Zone.MAIN) }
    var menu by remember { mutableStateOf(false) }
    val entries=if(zone==DeckEditor.Zone.MAIN) deck.deck else deck.sideboard
    val targets=remember(entries,catalog) { val ids=entries.map { it.cardId }.toSet(); catalog.filter { DeckConstruction.reference(it) in ids } }
    val target=targets.firstOrNull { DeckConstruction.reference(it)==targetId }
    var confirm by remember { mutableStateOf<DeckAlternative?>(null) }
    Text("Remplacer une carte",color=ListPalette.Gold)
    Text("Suggestions de ta collection selon coût, type, arène, aspects et traits. Les effets du texte ne sont pas comparés : vérifie la synergie et les règles de ton format.",style=MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { DeckEditor.Zone.entries.forEach { z ->
        FilterChip(zone==z,{zone=z;targetId=""},label={Text(if(z==DeckEditor.Zone.MAIN) "Deck principal" else "Réserve")})
    } }
    Box { OutlinedButton(onClick={menu=true}) {Text(target?.name ?: "Choisir la carte à remplacer")}; DropdownMenu(menu,{menu=false}) {
        targets.forEach { c -> DropdownMenuItem(text={Text(c.name)},onClick={targetId=DeckConstruction.reference(c);menu=false}) }
    } }
    target?.let { c ->
        val choices=remember(c,deck,owned,catalog,details,rules) {DeckAlternatives.suggest(c,deck,owned,catalog,details,rules)}
        if(choices.isEmpty()) Text("Aucune alternative possédée avec des informations suffisantes et des aspects compatibles.")
        choices.forEach { candidate -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {CardArtwork(candidate.card,Modifier.size(35.dp,50.dp));Column {Text(candidate.card.name);Text("${candidate.available} disponible(s) · ${candidate.reason}",style=MaterialTheme.typography.bodySmall)} }
            if(onReplace!=null) TextButton(onClick={confirm=candidate}) {Text("Remplacer 1 exemplaire")}
        } } }
    }
    confirm?.let { choice -> target?.let { from -> AlertDialog(onDismissRequest={confirm=null},title={Text("Remplacer un exemplaire ?")},
        text={Text("${from.name} → ${choice.card.name}. Seul le brouillon du deck change ; tu pourras annuler dans l’éditeur.")},
        confirmButton={TextButton(onClick={onReplace?.invoke(DeckAlternatives.replaceOne(deck,from,choice.card,zone));confirm=null}) {Text("Remplacer")}},
        dismissButton={TextButton(onClick={confirm=null}) {Text("Annuler")}}) } }
}
