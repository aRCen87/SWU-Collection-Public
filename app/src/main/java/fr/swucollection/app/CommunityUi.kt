package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal fun communityError(error: Throwable): String = when ((error as? FirebaseAuthException)?.errorCode) {
    "ERROR_INVALID_EMAIL" -> "Cette adresse e-mail n’est pas valide."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "Cette adresse possède déjà un compte. Essaie de te connecter."
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Connexion impossible. Vérifie l’e-mail et le mot de passe."
    "ERROR_OPERATION_NOT_ALLOWED" -> "La connexion par e-mail et mot de passe n’est pas encore activée pour les essais."
    "ERROR_TOO_MANY_REQUESTS" -> "Trop de tentatives. Patiente avant de réessayer."
    else -> if (error is IllegalArgumentException) error.message ?: "Vérifie les informations saisies."
        else "Opération impossible. Vérifie ta connexion, la confirmation de ton e-mail et le statut de ton compte, puis réessaie."
}

@Composable
internal fun ColumnScope.CommunityPanel(decks: List<DeckStore.SavedDeck>, earned: List<BadgeProgress>,
    definitions: List<BadgeDefinition>, catalog: List<CatalogCard>, database: CollectionDatabase, details: CardDetails, initialTab: String = "Mon profil", messaging: Boolean = false,
    initialConversation: Pair<PlayerConversation, String>? = null, onOpenMessenger: ((PlayerConversation, String) -> Unit)? = null,
    onOpenFriends: (() -> Unit)? = null,
    onRestored: () -> Unit, onImportDeck: (String, PortableDeck) -> Unit, onOpenCard: (SharedPlayerCard) -> Unit = {}) {
    val context = LocalContext.current
    val service = remember(context) { CommunityService.create(context) }
    Text(if (messaging) "Tes discussions et tes amis" else "Profils, échanges et decks · essais privés", color = MaterialTheme.colorScheme.primary)
    if (service == null) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Les comptes de test ne sont pas encore activés", fontWeight = FontWeight.Bold)
            Text("Le service privé doit être connecté avant de créer un compte ou d’envoyer des messages. Ta collection et tes decks restent disponibles sur ce téléphone.")
            Text("À l’activation : pseudo, avatar modifiable, profils et badges, messages privés et partage de decks.")
        } }
        return
    }
    var user by remember(service) { mutableStateOf<FirebaseUser?>(service.auth.currentUser) }
    var identityRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(service) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser; identityRevision++ }
        service.auth.addAuthStateListener(listener)
        onDispose { service.auth.removeAuthStateListener(listener) }
    }
    Column(Modifier.fillMaxWidth().then(if(messaging)Modifier.weight(1f) else Modifier).then(if(messaging && (user==null || user?.isEmailVerified!=true))Modifier.verticalScroll(rememberScrollState()) else Modifier),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    key(user?.uid, identityRevision) {
        CommunitySession(service, user, decks, earned, definitions, catalog, database, details, initialTab, messaging, initialConversation, onOpenMessenger, onOpenFriends, { identityRevision++ }, onRestored, onImportDeck, onOpenCard)
    }
    }
}

@Composable
private fun ColumnScope.CommunitySession(service: CommunityService, user: FirebaseUser?, decks: List<DeckStore.SavedDeck>,
    earned: List<BadgeProgress>, definitions: List<BadgeDefinition>, catalog: List<CatalogCard>, database: CollectionDatabase, details: CardDetails, initialTab: String, messaging: Boolean,
    initialConversation: Pair<PlayerConversation, String>?, onOpenMessenger: ((PlayerConversation, String) -> Unit)?, onOpenFriends: (() -> Unit)?, refresh: () -> Unit,
    onRestored: () -> Unit, onImportDeck: (String, PortableDeck) -> Unit, onOpenCard: (SharedPlayerCard) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun action(block: suspend () -> Unit) { if (!busy) scope.launch {
        busy = true; status = null
        try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { status = communityError(error) } finally { busy = false }
    } }
    status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (user == null) {
        var create by remember { mutableStateOf(false) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!create, { create = false; password = ""; confirm = "" }, label = { Text("Connexion") })
            FilterChip(create, { create = true; password = ""; confirm = "" }, label = { Text("Créer un compte") })
        }
        OutlinedTextField(email, { email = it.take(254) }, label = { Text("Adresse e-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it.take(128) }, label = { Text("Mot de passe") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        if (create) {
            Text("12 caractères minimum. Confirme ensuite ton e-mail pour accéder automatiquement à la communauté. L’adresse ne sera pas affichée sur ton profil.", fontSize = 12.sp)
            OutlinedTextField(confirm, { confirm = it.take(128) }, label = { Text("Confirmer le mot de passe") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { action {
            if (create) { require(password == confirm) { "Les mots de passe sont différents." }; service.register(email, password) }
            else service.login(email, password)
            password = ""; confirm = ""; refresh()
        } }, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (create) "Créer mon compte de test" else "Me connecter")
        }
        TextButton(onClick = { action { service.resetPassword(email); status = "Si un compte correspond à cette adresse, un e-mail de récupération sera envoyé." } }, enabled = !busy && email.isNotBlank()) { Text("Mot de passe oublié") }
        Text("Le compte est facultatif. Après confirmation de l’e-mail, la sauvegarde privée est automatique et peut être désactivée dans Sauvegarde. La connexion ne remplace jamais les données de ce téléphone sans ton choix.", fontSize = 12.sp)
        return
    }
    if (!user.isEmailVerified) {
        Text("Confirme ton adresse e-mail avec le lien reçu avant de continuer.")
        Button(onClick = { action { service.refreshIdentity(); refresh() } }, enabled = !busy) { Text("J’ai confirmé mon e-mail") }
        OutlinedButton(onClick = { action { service.resendVerification(); status = "E-mail de confirmation envoyé." } }, enabled = !busy) { Text("Renvoyer l’e-mail") }
        TextButton(onClick = { service.signOut() }) { Text("Me déconnecter") }
        return
    }
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(user.uid) { try { allowed = service.allowed() } catch (error: Exception) { status = communityError(error) } }
    if (allowed != true) {
        Text(if (allowed == false) "Ce compte est désactivé. Contacte l’administrateur si tu penses qu’il s’agit d’une erreur." else "Activation de ton compte…")
        OutlinedButton(onClick = { action { allowed = service.allowed() } }, enabled = !busy) { Text("Vérifier mon accès") }
        TextButton(onClick = { service.signOut() }) { Text("Me déconnecter") }
        return
    }
    var profile by remember { mutableStateOf<PlayerProfile?>(null) }
    var profileLoaded by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var conversations by remember { mutableStateOf<List<PlayerConversation>>(emptyList()) }
    var tab by remember { mutableStateOf(if (messaging && initialTab !in listOf("Messages", "Amis")) "Messages" else initialTab) }
    var friendships by remember { mutableStateOf<List<PlayerFriendship>>(emptyList()) }
    var trades by remember { mutableStateOf<List<PlayerTrade>>(emptyList()) }
    var threadStates by remember { mutableStateOf<Map<String,MessengerThreadState>>(emptyMap()) }
    var preferences by remember { mutableStateOf(MessengerPreferences()) }
    var receipts by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var friendsProfiles by remember { mutableStateOf<Map<String, PlayerProfile>>(emptyMap()) }
    var proposingTrade by remember { mutableStateOf<PlayerProfile?>(null) }
    var suggestedTrade by remember { mutableStateOf<SuggestedTrade?>(null) }
    LaunchedEffect(friendships) {
        friendsProfiles = emptyMap()
        friendsProfiles = friendships.mapNotNull { f -> runCatching { service.profile(f.peer(user.uid)) }.getOrNull()?.let { it.id to it } }.toMap()
    }
    var editing by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var conversation by remember { mutableStateOf(initialConversation?.first) }
    var peerName by remember { mutableStateOf(initialConversation?.second.orEmpty()) }
    var blocked by remember { mutableStateOf(false) }
    var findingPlayers by remember { mutableStateOf(false) }
    var choosingRecipient by remember { mutableStateOf(false) }
    BackHandler(enabled = messaging && conversation != null && selectedProfile == null) { conversation = null }
    DisposableEffect(service, user.uid) {
        val subscriptions = listOf(service.ownProfile({ profile = it; profileLoaded = true }, { status = communityError(it) }),
            service.profiles({ profiles = it.filter { p -> p.id != user.uid } }, { status = communityError(it) }),
            service.conversations({ conversations = it }, { status = communityError(it) }),
            service.friendships({ friendships = it }, { status = communityError(it) }),
            service.trades({ trades = it }, { status = communityError(it) }),
            service.receipts({ receipts = it }, { status = communityError(it) }),
            service.threadStates({ threadStates=it },{status=communityError(it)}),
            service.messengerPreferences({preferences=it},{status=communityError(it)}))
        onDispose { subscriptions.forEach { it.remove() } }
    }
    fun talk(peer: PlayerProfile) { action {
        val id = service.openConversation(peer.id)
        val thread = PlayerConversation(id, listOf(user.uid, peer.id), 0L)
        val old=threadStates[id]?:MessengerThreadState()
        if(old.hiddenBefore>0)service.saveThreadState(id,old.copy(hiddenBefore=0))
        if (!messaging && onOpenMessenger != null) onOpenMessenger(thread, peer.name)
        else { conversation = thread; peerName = peer.name; selectedProfile = null; tab = "Messages" }
    } }
    if (!profileLoaded) { Text("Chargement du profil…"); return }
    if (profile == null || editing) {
        ProfileEditor(profile ?: PlayerProfile(user.uid, ""), catalog, earned.map { it.badge.id }, busy, { saved -> action {
            service.saveProfile(saved, earned.map { it.badge.id }); editing = false
        } }, if (profile == null) null else { { editing = false } })
        TextButton(onClick = { service.signOut() }) { Text("Me déconnecter") }
        return
    }
    val unread = CommunitySocial.unread(user.uid, conversations, receipts)
    val requests = friendships.count { it.status == "pending" && it.requester != user.uid }
    val exchangeRequests = trades.count { it.status == "pending" && it.requester != user.uid }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (if (messaging) listOf("Messages", "Amis") else listOf("Mon profil", "Joueurs", "Échanges", "Favoris", "Sauvegarde")).forEach { label ->
            FilterChip(tab == label, { tab = label; conversation = null }, label = { Text((if(messaging && label=="Messages")"Discussions" else label) + when {
                label == "Amis" && requests > 0 -> " · $requests"
                label == "Messages" && unread > 0 -> " · $unread"
                label == "Échanges" && exchangeRequests > 0 -> " · $exchangeRequests"
                else -> ""
            }, fontSize = 12.sp) })
        }
    }
    Column(Modifier.fillMaxWidth().then(if(messaging) Modifier.weight(1f) else Modifier)
        .then(if(messaging && !(tab=="Messages" && conversation!=null)) Modifier.verticalScroll(rememberScrollState()) else Modifier),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    when (tab) {
        "Sauvegarde" -> AccountBackupPanel(database, {}, onRestored)
        "Mon profil" -> {
            PlayerHeading(profile!!)
            ProfileInterests(profile!!, catalog)
            ShowcasePanel(service, profile!!, database, catalog)
            if (profile!!.bio.isNotBlank()) Text(profile!!.bio)
            Text(if (profile!!.discoverable) "Profil visible aux joueurs autorisés du test" else "Profil non listé parmi les joueurs")
            OutlinedButton(onClick = { editing = true }) { Text("Modifier mon profil et mon avatar") }
            CommunityNotificationControls(user.uid)
            Row(verticalAlignment=Alignment.CenterVertically){Text("Afficher mon statut en ligne",Modifier.weight(1f));Switch(preferences.sharePresence,{v->action{service.saveMessengerPreferences(preferences.copy(sharePresence=v))}})}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Partager les confirmations de lecture",Modifier.weight(1f));Switch(preferences.shareRead,{v->action{service.saveMessengerPreferences(preferences.copy(shareRead=v))}})}
            ProfileBadges(profile!!, definitions, catalog)
            SharedProfileDecks(service, profile!!, true, decks, catalog, onImportDeck)
            if (profile!!.shareBadges) OutlinedButton(onClick = { action { service.saveProfile(profile!!, earned.map { it.badge.id }); status = "Badges du profil actualisés." } }, enabled = !busy) { Text("Actualiser mes badges") }
            TextButton(onClick = { service.signOut() }) { Text("Me déconnecter") }
        }
        "Amis" -> {
            if (messaging) {
                OutlinedButton(onClick = { findingPlayers = !findingPlayers }, modifier = Modifier.fillMaxWidth()) { Text(if (findingPlayers) "Masquer la recherche de joueurs" else "Ajouter un ami · Rechercher un joueur") }
                if (findingPlayers) MessengerFriendSearch(profiles) { selectedProfile = it }
            }
            FriendPanel(service, friendships, profiles + friendsProfiles.values, busy,
                { f -> action { service.acceptFriend(f) } }, { f -> action { service.removeFriend(f) } },
                { id -> action { service.requestFriend(id); status = "Demande d’ami envoyée." } },
                { selectedProfile = it }, { talk(it) }, { proposingTrade = it }, messaging = messaging)
        }
        "Échanges" -> {
            val friends = friendships.filter { it.status == "accepted" }.map { f ->
                friendsProfiles[f.peer(user.uid)] ?: profiles.firstOrNull { it.id == f.peer(user.uid) }
                    ?: PlayerProfile(f.peer(user.uid), "Ami")
            }
            var choosingFriend by remember { mutableStateOf(false) }
            var suggestions by remember { mutableStateOf(false) }
            Text("Choisis un ami, prépare les cartes et envoie ta proposition.", fontSize = 12.sp)
            Button(onClick = { choosingFriend = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Nouvel échange") }
            if (choosingFriend) AlertDialog(onDismissRequest = { choosingFriend = false }, title = { Text("Avec qui ?") }, text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (friends.isEmpty()) {
                        Text("Ajoute d’abord un ami en recherchant son pseudo.")
                        TextButton(onClick = { choosingFriend = false; if (onOpenFriends != null) onOpenFriends() else tab = "Amis" }) { Text("Ajouter un ami") }
                    }
                    friends.sortedBy { it.name.lowercase() }.forEach { peer ->
                        OutlinedButton(onClick = { choosingFriend = false; proposingTrade = peer }, modifier = Modifier.fillMaxWidth()) { Text(peer.name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { choosingFriend = false }) { Text("Fermer") } })
            TextButton(onClick = { suggestions = !suggestions }) { Text(if (suggestions) "Masquer l’aide au choix" else "M’aider à trouver des cartes à échanger") }
            if (suggestions) FriendTradeSuggestionPanel(service, database, catalog, details, friends, { suggestedTrade = it })
            TradeRequestsPanel(service, trades, profiles + friendsProfiles.values, catalog, database, busy,
                { trade, answer -> action { service.answerTrade(trade, answer) } },
                { id -> talk((profiles + friendsProfiles.values).firstOrNull { it.id == id } ?: PlayerProfile(id, "Ami")) }, onRestored)
        }
        "Joueurs" -> { PlayerDirectory(service, profiles) { selectedProfile = it } }
        "Favoris" -> { ShowcaseFavoritesPanel(service, catalog) { selectedProfile = it } }
        "Messages" -> {
            val selected = conversation
            if (selected == null) {
                MessengerInbox(user.uid, conversations, receipts, profiles + friendsProfiles.values,
                    states=threadStates,onState={id,state->action{service.saveThreadState(id,state);if(state.hiddenBefore>0)service.markRead(id)}},
                    onNew = { choosingRecipient = true }, onFriends = { tab = "Amis" }, onOpen = { thread, name -> conversation = thread; peerName = name })
            } else {
                OutlinedButton(onClick = { conversation = null }) { Text("‹ Mes conversations") }
                CommunityChat(service, selected, decks, catalog, onImportDeck, peerName, onOpenCard)
            }
        }
    }
    }
    if (choosingRecipient) MessengerRecipientPicker(friendships.filter { it.status == "accepted" }.mapNotNull { friend ->
        (profiles + friendsProfiles.values).firstOrNull { it.id == friend.peer(user.uid) }
    }.distinctBy { it.id }, onClose = { choosingRecipient = false }, onAdd = { choosingRecipient = false; tab = "Amis" },
        onChoose = { choosingRecipient = false; talk(it) })
    selectedProfile?.let { player ->
        var identity by remember(player.id) { mutableStateOf(player.copy(firstName = "", shareFirstName = false)) }
        DisposableEffect(service, player.id) {
            val listener = service.profileIdentity(player) { identity = it }
            onDispose { listener.remove() }
        }
        LaunchedEffect(player.id) { blocked = runCatching { service.isBlocked(player.id) }.getOrDefault(false) }
        AlertDialog(onDismissRequest = { selectedProfile = null }, title = { Text("Profil du joueur") }, text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerHeading(identity); if (player.bio.isNotBlank()) Text(player.bio)
                ProfileInterests(player, catalog)
                ShowcasePanel(service, player, database, catalog)
                ProfileBadges(player, definitions, catalog)
                val friendship = friendships.firstOrNull { player.id in it.members }
                if (friendship == null) OutlinedButton(onClick = { action { service.requestFriend(player.id); status = "Demande d’ami envoyée." } }, enabled = !busy && !blocked) { Text("Ajouter aux amis") }
                else if (friendship.status == "accepted") {
                    Text("Dans mes amis")
                    if (!blocked) FriendProfileTradeSummary(service, player, database, catalog, details) { suggestion ->
                        selectedProfile = null; suggestedTrade = suggestion
                    }
                    if (!messaging) OutlinedButton(onClick = { selectedProfile = null; proposingTrade = player }, enabled = !blocked) { Text("Proposer un échange") }
                } else {
                    Text(if (friendship.requester == user.uid) "Demande envoyée" else "Demande d’ami reçue")
                    TextButton(onClick = { selectedProfile = null; if (messaging) tab = "Amis" else onOpenFriends?.invoke() }) { Text("Voir mes amis et demandes") }
                }
                SharedProfileDecks(service, player, false, decks, catalog, onImportDeck)
                OutlinedButton(onClick = { action { service.block(player.id, !blocked); blocked = !blocked } }, enabled = !busy) { Text(if (blocked) "Débloquer ce joueur" else "Bloquer ce joueur") }
            }
        }, confirmButton = { Button(onClick = { talk(player) }, enabled = !busy && !blocked) { Text("Envoyer un message") } },
            dismissButton = { TextButton(onClick = { selectedProfile = null }) { Text("Fermer") } })
    }
    suggestedTrade?.let { suggestion -> TradeComposer(suggestion.peer, catalog, database, busy, { suggestedTrade = null }, { offer, wanted -> action {
        service.proposeTrade(suggestion.peer.id, offer, wanted); suggestedTrade = null; tab = if (messaging) "Messages" else "Échanges"; status = "Proposition envoyée. Retrouve-la dans Échanges."
    } }, initialOffer = suggestion.offer, initialWanted = suggestion.wanted) }
    proposingTrade?.let { peer -> TradeComposer(peer, catalog, database, busy, { proposingTrade = null }, { offer, wanted -> action {
        service.proposeTrade(peer.id, offer, wanted); proposingTrade = null; tab = "Échanges"; status = "Proposition envoyée."
    } }) }
}

@Composable
private fun ColumnScope.ProfileEditor(initial: PlayerProfile, catalog: List<CatalogCard>, earnedIds: List<String>, busy: Boolean,
    onSave: (PlayerProfile) -> Unit, onCancel: (() -> Unit)?) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var firstName by remember(initial.id) { mutableStateOf(initial.firstName) }
    var shareFirstName by remember(initial.id) { mutableStateOf(initial.shareFirstName) }
    var bio by remember(initial.id) { mutableStateOf(initial.bio) }
    var avatar by remember(initial.id) { mutableStateOf(initial.avatar) }
    var visible by remember(initial.id) { mutableStateOf(initial.discoverable) }
    var badges by remember(initial.id) { mutableStateOf(initial.shareBadges) }
    var policy by remember(initial.id) { mutableStateOf(if(initial.messagePolicy=="nobody")"nobody" else "friends") }
    var interests by remember(initial.id) { mutableStateOf(initial) }
    var shareDecks by remember(initial.id) { mutableStateOf(initial.shareDecks) }
    var avatarLoading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch {
        runCatching { readCommunityAvatar(context, uri) }.onSuccess { avatar = it; photoError = null }.onFailure { photoError = "Cette image ne peut pas être utilisée. Choisis une autre photo." }
    } }
    Text(if (initial.name.isBlank()) "Créer mon profil" else "Modifier mon profil", fontWeight = FontWeight.Bold)
    CommunityAvatar(avatar, Modifier.size(72.dp))
    Text("Personnages des cartes", fontWeight = FontWeight.SemiBold)
    CharacterAvatarGallery(catalog, !busy && !avatarLoading) { card -> scope.launch {
        avatarLoading = true; photoError = null
        try { avatar = loadCharacterAvatar(context, card) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { photoError = "Portrait indisponible. Vérifie ta connexion puis réessaie." }
        finally { avatarLoading = false }
    } }
    if (avatarLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedButton(onClick = { photo.launch("image/*") }, enabled = !busy) { Text("Choisir une photo") }
    photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    OutlinedTextField(name, { name = it.take(32) }, label = { Text("Pseudo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(firstName, { firstName = it.take(40) }, label = { Text("Prénom · facultatif") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(shareFirstName, { shareFirstName = it }); Text("Afficher mon prénom à mes amis", Modifier.padding(start = 8.dp)) }
    OutlinedTextField(bio, { bio = it.take(200) }, label = { Text("À propos de moi · facultatif") }, modifier = Modifier.fillMaxWidth())
    ProfileInterestEditor(interests, catalog) { interests = it }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(visible, { visible = it }); Text("Afficher mon profil parmi les joueurs", Modifier.padding(start = 8.dp)) }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(badges, { badges = it }); Text("Afficher mes badges débloqués", Modifier.padding(start = 8.dp)) }
    Text("Qui peut m’écrire ?", fontWeight = FontWeight.SemiBold)
    listOf("friends" to "Mes amis uniquement", "nobody" to "Personne").forEach { (id, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(policy == id, { policy = id }); Text(label) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(shareDecks, { shareDecks = it }); Text("Afficher mes decks partagés sur mon profil", Modifier.padding(start = 8.dp)) }
    Text("Seuls les joueurs autorisés aux essais pourront consulter le profil. Ni ton e-mail ni ta collection ne sont affichés.", fontSize = 12.sp)
    Button(onClick = { onSave(initial.copy(name = name, firstName = firstName, shareFirstName = shareFirstName, bio = bio, avatar = avatar, discoverable = visible, shareBadges = badges, badgeIds = if (badges) earnedIds else emptyList(), messagePolicy = policy, shareDecks = shareDecks, city = interests.city, formats = interests.formats, leaders = interests.leaders, shareShowcase = interests.shareShowcase)) }, enabled = !busy && !avatarLoading && name.trim().length >= 2) { Text("Enregistrer mon profil") }
    onCancel?.let { TextButton(onClick = it) { Text("Annuler") } }
}

@Composable
internal fun CommunityAvatar(raw: String, modifier: Modifier = Modifier) {
    val photo = remember(raw) { if (raw.startsWith("jpeg:") && raw.length <= 18000) runCatching {
        val bytes = Base64.decode(raw.removePrefix("jpeg:"), Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull() else null }
    Box(modifier.clip(CircleShape).background(badgeNavy), contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo, "Avatar du joueur", Modifier.fillMaxSize())
        else Text(CommunityValidation.avatars[raw.removePrefix("preset:")] ?: "✦", fontSize = 28.sp, color = badgeGold)
    }
}
@Composable internal fun PlayerHeading(profile: PlayerProfile) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CommunityAvatar(profile.avatar, Modifier.size(52.dp))
        Column {
            Text(profile.name, fontWeight = FontWeight.Bold)
            if (profile.firstName.isNotBlank()) Text(profile.firstName, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun ProfileBadges(profile: PlayerProfile, definitions: List<BadgeDefinition>, catalog: List<CatalogCard>) {
    if (!profile.shareBadges) { Text("Les badges de ce profil sont privés."); return }
    val badges = definitions.filter { it.id in profile.badgeIds }
    Text("${badges.size} badges affichés", fontWeight = FontWeight.SemiBold)
    badges.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { badge -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgePin(badge, catalog, Modifier.fillMaxWidth()); Text(badge.title, fontSize = 11.sp)
        } }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    } }
}

private suspend fun readCommunityAvatar(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Image illisible")
    val side = minOf(source.width, source.height)
    val crop = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(crop, 96, 96, true)
    val bytes = java.io.ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, 72, output); output.toByteArray() }
    require(bytes.size <= 13000)
    if (scaled !== crop) scaled.recycle()
    if (crop !== source) crop.recycle()
    source.recycle()
    CommunityValidation.avatar("jpeg:" + Base64.encodeToString(bytes, Base64.NO_WRAP))
}
