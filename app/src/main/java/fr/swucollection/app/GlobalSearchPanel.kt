package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GlobalSearchPanel(query: String, onQuery: (String) -> Unit, catalog: List<CatalogCard>, sets: List<CatalogSet>, owned: List<CollectionCard>, decks: List<DeckStore.SavedDeck>, details: CardDetails, facets: SearchFacets, onFacets: (SearchFacets) -> Unit, modifier: Modifier, legality: PremierLegality, onCard: (CatalogCard, List<CatalogCard>) -> Unit, onDeck: (DeckStore.SavedDeck) -> Unit, onSet: (String) -> Unit) {
    val index = remember(catalog, owned, decks, details) { GlobalSearchIndex(catalog, owned, decks, details) }
    val terms = remember(index, query) { index.terms(query) }
    val natural = remember(query) { NaturalCardSearch.parse(query) }
    val results = remember(index, natural, facets, catalog, details, owned) { catalog.filter { facets.matches(it, details.get(it)) && natural.matches(it, details.get(it), owned, index) } }
    val matchingDecks = remember(index, query, decks) { if (query.isBlank()) emptyList() else decks.filter { index.deckMatches(it, terms) } }
    val matchingSets = remember(index, query, sets) { if (query.isBlank()) emptyList() else sets.filter { index.setMatches(it, query) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, onQuery, label = { Text("Rechercher une carte, un deck, une extension") },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Close, "Effacer la recherche")
            } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Ex. : unités rouges à 3 ressources · légendaires manquantes · mes doubles anglais", style = MaterialTheme.typography.bodySmall)
        if (natural.description.isNotBlank()) Text("Compris : ${natural.description}", color = ListPalette.Gold, style = MaterialTheme.typography.bodySmall)
        SearchFacetPanel(facets, sets, catalog, details, onFacets)
        Text("${results.size} carte(s) · ${matchingDecks.size} deck(s) · ${matchingSets.size} extension(s)", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (matchingDecks.isNotEmpty()) item { Text("Mes decks · par nom ou cartes utilisées", color = ListPalette.Gold) }
            items(matchingDecks, key = { "deck:${it.id}" }) { saved ->
                OutlinedCard(onClick = { onDeck(saved) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) { Text(saved.name, color = ListPalette.Gold); Text("Ouvrir le deck ›", style = MaterialTheme.typography.bodySmall) }
                }
            }
            items(matchingSets, key = { "set:${it.code}" }) { set ->
                OutlinedCard(onClick = { onSet(set.code) }, modifier = Modifier.fillMaxWidth()) { Text("${set.name} · ${set.code} ›", Modifier.padding(12.dp)) }
            }
            if (matchingDecks.isNotEmpty() || matchingSets.isNotEmpty()) item { Text("Cartes · filtres appliqués ci-dessus", color = ListPalette.Gold) }
            items(results, key = { "card:${DeckConstruction.reference(it)}" }) { card ->
                Card(onClick = { onCard(card, results) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardArtwork(card, Modifier.width(58.dp).height(82.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.name, color = ListPalette.Gold)
                            CardLegalityBadge(legality.status(card))
                            Text("${card.setCode} · ${card.number}", style = MaterialTheme.typography.bodySmall)
                            Text("${index.stock(card)} possédée(s), versions reconnues comprises", style = MaterialTheme.typography.bodySmall)
                            if (index.deckCount(card) > 0) Text("Utilisée dans ${index.deckCount(card)} deck(s)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (results.isEmpty() && matchingDecks.isEmpty() && matchingSets.isEmpty()) item {
                Text("Aucun résultat.")
                TextButton(onClick = { onFacets(SearchFacets()); onQuery("") }) { Text("Réinitialiser la recherche") }
            }
        }
    }
}
