package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CommunityHomeControls(onOpen: (String) -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val service = remember(context) { CommunityService.create(context) }
    if (service == null) {
        CommunityHomeActions(null, 0, "Messages", onOpen, onMenu, modifier)
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
        var trades by remember { mutableStateOf<List<PlayerTrade>>(emptyList()) }
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(uid) { ready = uid != null && service.auth.currentUser?.isEmailVerified == true && runCatching { service.allowed() }.getOrDefault(false) }
        DisposableEffect(ready, uid) {
            val listeners = if (ready) listOf(service.ownProfile({ profile = it }, {}), service.friendships({ friendships = it }, {}),
                service.conversations({ threads = it }, {}), service.receipts({ receipts = it }, {}), service.trades({ trades = it }, {})) else emptyList()
            onDispose { listeners.forEach { it.remove() } }
        }
        val count = if (ready) CommunitySocial.unread(uid.orEmpty(), threads, receipts) +
            friendships.count { it.status == "pending" && it.requester != uid } +
            trades.count { it.status == "pending" && it.requester != uid } else 0
        val destination = when {
            friendships.any { it.status == "pending" && it.requester != uid } -> "Amis"
            trades.any { it.status == "pending" && it.requester != uid } -> "Échanges"
            else -> "Messages"
        }
        CommunityHomeActions(profile?.avatar, count, destination, onOpen, onMenu, modifier)
    }

}

@Composable
private fun CommunityHomeActions(avatar: String?, count: Int, destination: String,
    onOpen: (String) -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    Column(modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onOpen("Mon profil") }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Mon profil" }) {
            CommunityAvatar(avatar ?: "preset:star", Modifier.size(34.dp).border(1.dp, badgeGold, CircleShape).clearAndSetSemantics {})
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Menu, "Ouvrir le menu", tint = badgeGold)
        }
        BadgedBox(modifier = Modifier.size(48.dp), badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else "$count") } }) {
            IconButton(onClick = { onOpen(destination) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MailOutline, "Courrier, $count en attente", tint = badgeGold)
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
                BadgeIllustration(card, false, Modifier.size(64.dp).clip(CircleShape).background(badgeNavy))
                Text(entry.name, fontSize = 11.sp, maxLines = 2)
            } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}
internal suspend fun loadCharacterAvatar(context: Context, card: CatalogCard): String = withContext(Dispatchers.IO) {
    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(OfflineImages.source(context, card.imageUrl)).size(900).allowHardware(false).build())
    require(result is SuccessResult)
    val source = result.drawable.toBitmap()
    val rect = BadgeArtCrop.illustration(DeckConstruction.reference(card), card.type, source.width.toFloat(), source.height.toFloat()).fit(1f)
    val crop = Bitmap.createBitmap(source, rect.left.toInt(), rect.top.toInt(), rect.width.toInt().coerceAtLeast(1), rect.height.toInt().coerceAtLeast(1))
    val scaled = Bitmap.createScaledBitmap(crop, 96, 96, true)
    val bytes = java.io.ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, 78, output); output.toByteArray() }
    // Coil owns the source bitmap: never recycle its cached pixels.
    if (scaled !== source && scaled !== crop) scaled.recycle()
    if (crop !== source) crop.recycle()
    CommunityValidation.avatar("jpeg:" + Base64.encodeToString(bytes, Base64.NO_WRAP))
}

@Composable
internal fun ColumnScope.FriendPanel(service: CommunityService, friendships: List<PlayerFriendship>, profiles: Collection<PlayerProfile>, busy: Boolean,
    onAccept: (PlayerFriendship) -> Unit, onRemove: (PlayerFriendship) -> Unit, onRequest: (String) -> Unit,
    onProfile: (PlayerProfile) -> Unit, onMessage: (PlayerProfile) -> Unit, onTrade: (PlayerProfile) -> Unit) {
    var code by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<PlayerFriendship?>(null) }
    Text("Mes amis", style = MaterialTheme.typography.titleLarge)
    Text("Trouve un joueur par pseudo dans Joueurs, ou utilise son code ami.", fontSize = 12.sp)
    OutlinedTextField(code, { code = it.take(160) }, label = { Text("Code ami") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = { onRequest(code) }, enabled = !busy && code.isNotBlank()) { Text("Envoyer une demande") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(service.uid)) }) { Text("Copier mon code ami") }
    if (friendships.isEmpty()) Text("Tes amis et demandes apparaîtront ici.")
    val ordered = friendships.sortedBy { if (it.status == "pending" && it.requester != service.uid) 0 else if (it.status == "accepted") 1 else 2 }
    ordered.forEach { friend ->
        val player = profiles.firstOrNull { it.id == friend.peer(service.uid) } ?: PlayerProfile(friend.peer(service.uid), if (friend.requester != service.uid) friend.requesterName.ifBlank { "Joueur du test" } else "Joueur du test", avatar = if (friend.requester != service.uid) friend.requesterAvatar else "preset:star")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerHeading(player)
            if (friend.status == "accepted") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onProfile(player) }) { Text("Profil") }
                    TextButton(onClick = { onMessage(player) }) { Text("Message") }
                    TextButton(onClick = { onTrade(player) }) { Text("Échange") }
                }
                TextButton(onClick = { remove = friend }) { Text("Retirer de mes amis") }
            } else {
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
