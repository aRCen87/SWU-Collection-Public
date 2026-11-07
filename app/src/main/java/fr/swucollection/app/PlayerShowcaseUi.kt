package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ProfileInterests(profile: PlayerProfile, catalog: List<CatalogCard>) {
    if (profile.city.isNotBlank()) Text(profile.city, color = MaterialTheme.colorScheme.primary)
    if (profile.formats.isNotEmpty()) Text(profile.formats.joinToString(" · "))
    val cards = profile.leaders.mapNotNull { ref -> catalog.firstOrNull { DeckConstruction.reference(it) == ref } }
    if (cards.isNotEmpty()) {
        Text("Leaders favoris", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { card ->
            Column(Modifier.width(100.dp)) { CardArtwork(card, Modifier.size(100.dp, 80.dp)); Text(card.name, style = MaterialTheme.typography.bodySmall) }
        } }
    }
}
@Composable
internal fun PlayerDirectory(service: CommunityService, profiles: List<PlayerProfile>, onSelect: (PlayerProfile) -> Unit) {
    var city by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("") }; var local by remember { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(false) }
    LaunchedEffect(city) {
        local = emptyList(); error = null
        if (PlayerDiscovery.cityKey(city).isNotBlank()) {
            loading = true; delay(600)
            try { local = service.cityPlayers(city) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Recherche indisponible. Vérifie ta connexion puis réessaie." } finally { loading = false }
        } else loading = false
    }
    OutlinedTextField(city, { city = it.take(60) }, label = { Text("Ville · facultatif") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Ville déclarée par les joueurs, sans GPS ni adresse. Une recherche correspond à une ville, pas à une distance.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(name, { name = it.take(32) }, label = { Text("Pseudo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (listOf("") + PlayerDiscovery.formats).forEach { f ->
        FilterChip(format == f, { format = f }, label = { Text(f.ifBlank { "Tous les formats" }) })
    } }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    val source = if (PlayerDiscovery.cityKey(city).isBlank()) profiles else local
    val visible = source.filter { it.name.contains(name, true) && (format.isBlank() || format in it.formats) }
    if (!loading && error == null && visible.isEmpty()) Text("Aucun joueur visible pour ces critères.")
    Text("Jusqu’à 50 profils par recherche. Seuls les profils volontairement visibles apparaissent.", style = MaterialTheme.typography.bodySmall)
    visible.forEach { player -> OutlinedCard(onClick = { onSelect(player) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) { Text(player.name, fontWeight = FontWeight.Bold); Text(listOf(player.city, player.formats.joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" — ")); Text("Voir le profil et la vitrine ›") }
    } }
}
@Composable
internal fun ShowcasePanel(service: CommunityService, profile: PlayerProfile, database: CollectionDatabase, catalog: List<CatalogCard>) {
    val own = profile.id == service.uid; val scope = rememberCoroutineScope()
    var rows by remember(profile.id) { mutableStateOf<List<ShowcaseCard>>(emptyList()) }
    var favorites by remember(profile.id) { mutableStateOf<List<ShowcaseFavorite>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }; var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ShowcaseCard?>(null) }
    LaunchedEffect(profile.id, profile.shareShowcase, revision) {
        rows = emptyList(); error = null
        if (own || profile.shareShowcase) try { rows = service.showcase(profile.id); favorites = service.showcaseFavorites() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Vitrine indisponible ou devenue privée." }
    }
    fun action(block: suspend () -> Unit) { if (!busy) scope.launch { busy = true; error = null
        try { block(); revision++ } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Action impossible." } finally { busy = false }
    } }
    Text("Vitrine personnelle", style = MaterialTheme.typography.titleMedium)
    if (own) {
        Text(if (profile.shareShowcase) "Visible aux joueurs qui peuvent consulter ton profil. Seules les cartes choisies et leurs légendes sont publiées." else "Vitrine masquée. Active son affichage dans Modifier mon profil quand tu souhaites la partager.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { adding = true }, enabled = !busy && rows.size < 12) { Text("Choisir une carte de ma collection") }
    }
    if (!own && !profile.shareShowcase) Text("Ce joueur ne partage pas sa vitrine.")
    if ((own || profile.shareShowcase) && rows.isEmpty() && error == null) Text("Aucune carte exposée.")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    rows.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { row ->
        val card = catalog.firstOrNull { MarketPricing.key(it.setCode, it.number) == MarketPricing.key(row.set, row.number) }
        OutlinedCard(Modifier.weight(1f)) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardArtwork(card, Modifier.fillMaxWidth().height(150.dp), language = row.language, variant = row.variant)
            Text(card?.name ?: "${row.set} ${row.number}", fontWeight = FontWeight.SemiBold)
            Text("${row.variant} · ${CardLanguages.label(row.language)}", style = MaterialTheme.typography.bodySmall)
            if (row.caption.isNotBlank()) Text(row.caption)
            if (own) TextButton(onClick = { removing = row }, enabled = !busy) { Text("Retirer de la vitrine") }
            else { val selected = favorites.any { it.owner == row.owner && it.item == row.id }
                TextButton(onClick = { action { service.favorite(row, !selected) } }, enabled = !busy) { Text(if (selected) "★ Retirer des favoris" else "☆ Ajouter aux favoris") }
            }
        } }
    }; if (pair.size == 1) Spacer(Modifier.weight(1f)) } }
    removing?.let { row -> StableCollectorDialog(onDismissRequest = { removing = null }, title = { Text("Retirer cette carte de la vitrine ?") }, text = { Text("Ta collection et ses quantités sont conservées.") }, confirmButton = { TextButton(onClick = { action { service.removeShowcase(row.id); removing = null } }) { Text("Retirer") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Annuler") } }) }
    if (adding) ShowcasePicker(database.allCards(), { adding = false }) { row, caption -> action { service.publishShowcase(row, caption); adding = false } }
}
@Composable
private fun ShowcasePicker(owned: List<CollectionCard>, close: () -> Unit, publish: (CollectionCard, String) -> Unit) {
    var search by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<CollectionCard?>(null) }; var caption by remember { mutableStateOf("") }
    StableCollectorDialog(onDismissRequest = close, title = { Text("Choisir une carte à exposer") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("La référence, la langue, la finition et ta légende seront visibles si tu actives la vitrine. Aucune quantité ni collection complète n’est partagée.")
        OutlinedTextField(search, { search = it.take(100) }, label = { Text("Rechercher dans ma collection") })
        owned.filter { it.name.contains(search, true) || "${it.setCode} ${it.number}".contains(search, true) }.take(30).forEach { row ->
            FilterChip(selected?.id == row.id, { selected = row }, label = { Text("${row.name} · ${row.setCode} ${row.number} · ${row.variant} · ${row.language}") })
        }
        Text("30 résultats maximum. Précise ta recherche.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(caption, { caption = it.take(160) }, label = { Text("Légende · facultative") })
    } }, confirmButton = { TextButton(onClick = { selected?.let { publish(it, caption) } }, enabled = selected != null) { Text("Ajouter à ma vitrine") } }, dismissButton = { TextButton(onClick = close) { Text("Annuler") } })
}
@Composable
internal fun ShowcaseFavoritesPanel(service: CommunityService, catalog: List<CatalogCard>, select: (PlayerProfile) -> Unit) {
    var rows by remember { mutableStateOf<List<ShowcaseFavorite>>(emptyList()) }; var revision by remember { mutableIntStateOf(0) }
    var showcases by remember { mutableStateOf<Map<String, Pair<PlayerProfile,List<ShowcaseCard>>>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    LaunchedEffect(revision) {
        loading = true; showcases = emptyMap(); status = null
        try {
            rows = service.showcaseFavorites()
            val found = mutableMapOf<String, Pair<PlayerProfile,List<ShowcaseCard>>>()
            rows.map { it.owner }.distinct().forEach { owner ->
                try { val profile = service.profile(owner); if (profile?.shareShowcase == true) found[owner] = profile to service.showcase(owner) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { /* Revoked, blocked or unavailable: no cached card content. */ }
            }
            showcases = found
        } catch (e: CancellationException) { throw e } catch (_: Exception) { status = "Favoris indisponibles." } finally { loading = false }
    }
    Text("Mes favoris de vitrines", style = MaterialTheme.typography.titleMedium)
    Text("Cette liste est privée. Une carte retirée ou une vitrine masquée n’est plus consultable.", style = MaterialTheme.typography.bodySmall)
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    status?.let { Text(it) }; if (!loading && rows.isEmpty()) Text("Ajoute une carte aux favoris depuis la vitrine d’un joueur.")
    val byRef = remember(catalog) { catalog.associateBy { MarketPricing.key(it.setCode,it.number) } }
    rows.forEach { favorite ->
        val showcase = showcases[favorite.owner]; val item = showcase?.second?.firstOrNull { it.id == favorite.item }
        OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item != null) CardArtwork(byRef[MarketPricing.key(item.set,item.number)], Modifier.size(65.dp,90.dp), language = item.language, variant = item.variant)
            Column(Modifier.weight(1f)) {
                Text(if (item != null) byRef[MarketPricing.key(item.set,item.number)]?.name ?: "${item.set} ${item.number}" else if (loading) "Chargement de la vitrine…" else "Carte retirée ou vitrine indisponible")
                if (item != null) { Text(showcase?.first?.name.orEmpty()); Text("${item.variant} · ${item.language}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showcase?.first?.let(select) }) { Text("Ouvrir la vitrine") }
                }
                TextButton(onClick = { scope.launch { try { service.accountStore().collection("showcaseFavorites").document(service.uid).collection("items").document(favorite.id).delete().communityAwait(); revision++ } catch (e: CancellationException) { throw e } catch (_: Exception) { status = "Retrait impossible." } } }) { Text("Retirer des favoris") }
            }
        } }
    }
}

@Composable
internal fun ProfileInterestEditor(value: PlayerProfile, catalog: List<CatalogCard>, change: (PlayerProfile) -> Unit) {
    var leaderSearch by remember { mutableStateOf("") }
    OutlinedTextField(value.city, { change(value.copy(city = it.take(60))) }, label = { Text("Ville · facultative, sans adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("La ville apparaît avec ton profil. La recherche par ville nécessite un profil visible parmi les joueurs.", style = MaterialTheme.typography.bodySmall)
    Text("Mes formats", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PlayerDiscovery.formats.forEach { format ->
        FilterChip(format in value.formats, { change(value.copy(formats = if (format in value.formats) value.formats - format else value.formats + format)) }, label = { Text(format) })
    } }
    Text("Mes leaders favoris · 3 maximum", fontWeight = FontWeight.SemiBold)
    value.leaders.forEach { ref -> TextButton(onClick = { change(value.copy(leaders = value.leaders - ref)) }) { Text((catalog.firstOrNull { DeckConstruction.reference(it) == ref }?.name ?: ref) + " ×") } }
    OutlinedTextField(leaderSearch, { leaderSearch = it.take(60) }, label = { Text("Rechercher un leader") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (leaderSearch.isNotBlank() && value.leaders.size < 3) catalog.filter { it.type.contains("leader", true) && it.name.contains(leaderSearch, true) }.distinctBy { it.name }.take(8).forEach { card ->
        TextButton(onClick = { val ref = DeckConstruction.reference(card); change(value.copy(leaders = (value.leaders + ref).distinct().take(3))); leaderSearch = "" }) { Text("+ ${card.name} · ${card.setCode} ${card.number}") }
    }
    Row { Switch(value.shareShowcase, { change(value.copy(shareShowcase = it)) }); Text("Afficher ma vitrine sur mon profil", modifier = Modifier.padding(8.dp)) }
}
