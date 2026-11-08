package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CommunityHomeControls(onOpen: (String) -> Unit, onMenu: () -> Unit, onCounter: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val service = remember(context) { CommunityService.create(context) }
    if (service == null) {
        CommunityHomeActions(null, 0, "Messages", onOpen, onMenu, onCounter, modifier)
        return
    }
    var uid by remember { mutableStateOf(service.auth.currentUser?.uid) }
    DisposableEffect(service) {
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        service.auth.addAuthStateListener(listener)
        onDispose { service.auth.removeAuthStateListener(listener) }
    }
    key(uid) {
        var profile by remember { mutableStateOf<PlayerProfile?>(null) }
        var friendships by remember { mutableStateOf<List<PlayerFriendship>>(emptyList()) }
        var threads by remember { mutableStateOf<List<PlayerConversation>>(emptyList()) }
        var receipts by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(uid) { ready = uid != null && service.auth.currentUser?.isEmailVerified == true && runCatching { service.allowed() }.getOrDefault(false) }
        DisposableEffect(ready, uid) {
            val listeners = if (ready) listOf(service.ownProfile({ profile = it }, {}), service.friendships({ friendships = it }, {}),
                service.conversations({ threads = it }, {}), service.receipts({ receipts = it }, {})) else emptyList()
            onDispose { listeners.forEach { it.remove() } }
        }
        val count = if (ready) CommunitySocial.unread(uid.orEmpty(), threads, receipts) +
            friendships.count { it.status == "pending" && it.requester != uid } else 0
        CommunityHomeActions(profile?.avatar, count, "Messages", onOpen, onMenu, onCounter, modifier)
    }

}

@Composable
private fun CommunityHomeActions(avatar: String?, count: Int, destination: String,
    onOpen: (String) -> Unit, onMenu: () -> Unit, onCounter: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().padding(8.dp)) {
        Surface(onClick = { onOpen("Mon profil") }, modifier = Modifier.align(Alignment.TopStart).size(64.dp)
            .semantics { contentDescription = "Mon profil" }, shape = CircleShape, color = PremiumNavy,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, PremiumGold)) {
            Box(contentAlignment = Alignment.Center) {
                CommunityAvatar(avatar ?: "preset:star", Modifier.size(58.dp).clearAndSetSemantics {})
            }
        }
        Column(Modifier.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xDB08121F),
                border = androidx.compose.foundation.BorderStroke(1.dp, PremiumGold.copy(alpha = .4f))) {
                IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, "Paramètres", tint = PremiumGold)
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xDB08121F),
                border = androidx.compose.foundation.BorderStroke(1.dp, PremiumGold.copy(alpha = .4f))) {
                IconButton(onClick = { onOpen(destination) }, modifier = Modifier.size(48.dp)) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MailOutline, "Courrier, $count en attente", tint = PremiumGold)
                        if (count > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) {
                            Text(if (count > 99) "99+" else "$count")
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xDB08121F),
                border = androidx.compose.foundation.BorderStroke(1.dp, PremiumGold.copy(alpha = .4f))) {
                IconButton(onClick = onCounter, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MonitorHeart, "Compteur de points de vie", tint = PremiumGold)
                }
            }
        }
    }
}

@Composable
internal fun CharacterAvatarGallery(catalog: List<CatalogCard>, enabled: Boolean, onSelect: (CatalogCard) -> Unit) {
    val choices = remember(catalog) { CommunitySocial.characters.mapNotNull { entry -> catalog.firstOrNull { DeckConstruction.reference(it) == entry.cardId }?.let { entry to it } } }
    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (entry, card) -> Column(Modifier.weight(1f).clickable(enabled = enabled) { onSelect(card) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(androidx.compose.ui.res.painterResource(CharacterPortrait.drawable(entry.cardId)), entry.name, Modifier.size(64.dp).clip(CircleShape))
                Text(entry.name, fontSize = 11.sp, maxLines = 2)
            } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}
internal suspend fun loadCharacterAvatar(context: Context, card: CatalogCard): String = withContext(Dispatchers.IO) {
    CharacterPortrait.encoded(context, DeckConstruction.reference(card))
}

@Composable
internal fun ColumnScope.FriendPanel(service: CommunityService, friendships: List<PlayerFriendship>, profiles: Collection<PlayerProfile>, busy: Boolean,
    onAccept: (PlayerFriendship) -> Unit, onRemove: (PlayerFriendship) -> Unit, onRequest: (String) -> Unit,
    onProfile: (PlayerProfile) -> Unit, onMessage: (PlayerProfile) -> Unit, onTrade: (PlayerProfile) -> Unit, messaging: Boolean = false) {
    var remove by remember { mutableStateOf<PlayerFriendship?>(null) }
    var search by remember { mutableStateOf("") }
    Text("Mes amis", style = MaterialTheme.typography.titleLarge)
    if (friendships.isEmpty()) Text("Tes amis et demandes apparaîtront ici.")
    if (messaging && friendships.isNotEmpty()) OutlinedTextField(search, { search = it.take(80) }, label = { Text("Rechercher parmi mes amis") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val ordered = friendships.sortedBy { if (it.status == "pending" && it.requester != service.uid) 0 else if (it.status == "accepted") 1 else 2 }
    ordered.forEach { friend ->
        val player = profiles.firstOrNull { it.id == friend.peer(service.uid) } ?: PlayerProfile(friend.peer(service.uid), if (friend.requester != service.uid) friend.requesterName.ifBlank { "Joueur du test" } else "Joueur du test", avatar = if (friend.requester != service.uid) friend.requesterAvatar else "preset:star")
        if (!player.name.contains(search.trim(), true)) return@forEach
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CommunityAvatar(player.avatar,Modifier.size(42.dp))
                Column(Modifier.weight(1f)){Text(player.name,fontWeight=FontWeight.Bold);if(friend.status=="accepted")FriendPresence(service,player.id)}
                TextButton(onClick={onProfile(player)},contentPadding=PaddingValues(4.dp)){Text("Profil",fontSize=12.sp)}
                if(friend.status=="accepted")IconButton(onClick={onMessage(player)}){Icon(Icons.Default.MailOutline,"Message à ${player.name}")}
                var menu by remember{mutableStateOf(false)}
                Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Options pour ${player.name}")}
                    DropdownMenu(menu,{menu=false}){
                        if(friend.status=="accepted")DropdownMenuItem(text={Text("Retirer de mes amis")},onClick={menu=false;remove=friend})
                        DropdownMenuItem(text={Text("Profil et blocage")},onClick={menu=false;onProfile(player)})
                        if(!messaging && friend.status=="accepted")DropdownMenuItem(text={Text("Proposer un échange")},onClick={menu=false;onTrade(player)})
                    }
                }
            }
            if (friend.status != "accepted") {
                Text(if (friend.requester == service.uid) "Demande envoyée" else "Souhaite devenir ton ami")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (friend.requester != service.uid) Button(onClick = { onAccept(friend) }, enabled = !busy) { Text("Accepter") }
                    OutlinedButton(onClick = { onRemove(friend) }, enabled = !busy) { Text(if (friend.requester == service.uid) "Annuler" else "Refuser") }
                }
            }
        } }
    }
    remove?.let { friend -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Retirer cet ami ?") }, text = { Text("Il faudra accepter une nouvelle demande pour redevenir amis.") },
        confirmButton = { Button(onClick = { onRemove(friend); remove = null }, enabled = !busy) { Text("Retirer") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Annuler") } }) }
}

@Composable
internal fun ColumnScope.SharedProfileDecks(service: CommunityService, profile: PlayerProfile, own: Boolean, decks: List<DeckStore.SavedDeck>,
    catalog: List<CatalogCard>, onImport: (String, PortableDeck) -> Unit) {
    var shared by remember(profile.id) { mutableStateOf<List<PlayerSharedDeck>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }; var choosing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PlayerSharedDeck?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile.id, profile.shareDecks, revision) {
        shared = emptyList()
        if (own || profile.shareDecks) try { shared = service.sharedDecks(profile.id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = communityError(e) }
    }
    if (!own && !profile.shareDecks) return
    Text("Decks partagés", fontWeight = FontWeight.SemiBold)
    if (own && !profile.shareDecks) Text("L’affichage sur ton profil est désactivé. Active-le dans Modifier mon profil.", fontSize = 12.sp)
    if (shared.isEmpty()) Text("Aucun deck partagé.", fontSize = 12.sp)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    shared.forEach { saved ->
        OutlinedButton(onClick = { preview = saved }, modifier = Modifier.fillMaxWidth()) { Text(saved.name) }
        if (own) TextButton(onClick = { if (!busy) scope.launch {
            busy = true
            try { service.unshareDeck(saved.id); revision++ } catch (e: Exception) { error = communityError(e) } finally { busy = false }
        } }, enabled = !busy) { Text("Ne plus partager") }
    }
    if (own) OutlinedButton(onClick = { choosing = true }, enabled = !busy && shared.size < 20) { Text("Partager un deck sur mon profil") }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Choisir un deck") }, text = {
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
            if (decks.isEmpty()) Text("Crée d’abord un deck dans Mes decks.")
            decks.forEach { deck -> TextButton(onClick = { if (!busy) scope.launch {
                busy = true; error = null
                try { service.shareDeck(deck); choosing = false; revision++ } catch (e: Exception) { error = communityError(e) } finally { busy = false }
            } }, enabled = !busy) { Text(deck.name) } }
        }
    }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Fermer") } })
    preview?.let { saved -> SharedDeckPreview(saved, catalog, { preview = null }) { name, deck -> preview = null; onImport(name, deck) } }
}
@Composable
private fun SharedDeckPreview(saved: PlayerSharedDeck, catalog: List<CatalogCard>, onClose: () -> Unit, onImport: (String, PortableDeck) -> Unit) {
    val parsed = remember(saved.deckJson) { runCatching { CommunityValidation.deck(saved.deckJson) }.getOrNull() }
    AlertDialog(onDismissRequest = onClose, title = { Text(saved.name) }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (parsed == null) Text("Ce deck est invalide.") else {
                Text("Ouvre cette copie dans l’éditeur pour l’enregistrer dans Mes decks.")
                (listOf(DeckEntry(parsed.leader, 1), DeckEntry(parsed.base, 1)) + parsed.deck + parsed.sideboard).forEach { entry ->
                    val card = catalog.firstOrNull { DeckConstruction.reference(it) == entry.cardId }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        card?.let { coil.compose.AsyncImage(it.imageUrl, it.name, Modifier.size(38.dp, 50.dp)) }
                        Text("${entry.count} × ${card?.name ?: entry.cardId}", fontSize = 12.sp)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (parsed != null) onImport(saved.name, parsed) }, enabled = parsed != null) { Text("Enregistrer dans mes decks") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } })
}
