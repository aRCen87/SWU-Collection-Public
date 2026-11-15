package fr.swucollection.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class MemberPage(val profiles: List<PlayerProfile>, val cursor: String?, val hasMore: Boolean)

internal fun matchingMembers(profiles: List<PlayerProfile>, uid: String, query: String): List<PlayerProfile> =
    profiles.asSequence().filter { it.id != uid && it.discoverable && it.name.contains(query.trim(), true) }
        .distinctBy { it.id }.sortedWith(compareBy<PlayerProfile> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id }).toList()

/** One list: pages load as it scrolls; a pseudo search checks all remaining pages. */
@Composable
internal fun ColumnScope.MemberDirectory(uid: String, friendships: List<PlayerFriendship>, busy: Boolean,
    loadPage: suspend (String?) -> MemberPage, onRequest: (String) -> Unit, onProfile: (PlayerProfile) -> Unit,
    fillHeight: Boolean = false, knownProfiles: Collection<PlayerProfile> = emptyList(),
    onAccept: (PlayerFriendship) -> Unit = {}, onRemove: (PlayerFriendship) -> Unit = {},
    onMessage: (PlayerProfile) -> Unit = {}) {
    var query by rememberSaveable(uid) { mutableStateOf("") }
    var members by remember(uid) { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var cursor by remember(uid) { mutableStateOf<String?>(null) }
    var started by remember(uid) { mutableStateOf(false) }
    var hasMore by remember(uid) { mutableStateOf(true) }
    var loading by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var request by remember(uid) { mutableIntStateOf(0) }
    var invitations by rememberSaveable(uid) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(uid, query, request) {
        error = null
        if (!hasMore || (started && query.isBlank() && request == 0)) return@LaunchedEffect
        loading = true
        try {
            if (query.isNotBlank()) delay(350)
            do {
                val page = loadPage(cursor)
                members = (members + page.profiles).distinctBy { it.id }
                started = true
                check(!page.hasMore || page.cursor != null && page.cursor != cursor) { "Impossible de poursuivre la liste des membres." }
                cursor = page.cursor
                hasMore = page.hasMore
            } while (query.isNotBlank() && hasMore)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = communityError(e) }
        finally { loading = false }
    }
    val found = remember(members, uid, query) { matchingMembers(members, uid, query) }
    LaunchedEffect(uid, listState, found, hasMore, loading, query, error) {
        snapshotFlow { Triple(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1, found.size, loading) }
            .distinctUntilChanged().collect { (last, size, active) ->
                if (size > 0 && last >= size - 5 && hasMore && !active && error == null && query.isBlank()) request++
            }
    }
    OutlinedTextField(query, { query = it.take(32) }, label = { Text("Rechercher un pseudo") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    val pending = friendships.filter { it.status == "pending" }
    if (pending.isNotEmpty()) TextButton(onClick = { invitations = true }) {
        val received = pending.count { it.requester != uid }
        Text(if (received > 0) "Invitations reçues · $received" else "Demandes envoyées · ${pending.size}")
    }
    val relations = remember(friendships, uid) { friendships.associateBy { it.peer(uid) } }
    LazyColumn(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier.heightIn(max = 400.dp))
        .testTag("member_directory"), state = listState) {
        items(found, key = { it.id }) { player ->
            val friend = relations[player.id]
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clickable { onProfile(player) }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CommunityAvatar(player.avatar, Modifier.size(52.dp))
                    Text(player.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when {
                    friend == null -> TextButton(onClick = { onRequest(player.id) }, enabled = !busy) { Text("Ajouter") }
                    friend.status == "accepted" -> TextButton(onClick = { onMessage(player) }, enabled = !busy) { Text("Message") }
                    friend.requester == uid -> Text("Envoyée", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> TextButton(onClick = { invitations = true }) { Text("Répondre") }
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        if (!loading && error == null && found.isEmpty()) item {
            Text(if (query.isBlank()) "Aucun autre membre visible pour le moment." else "Aucun membre trouvé pour ce pseudo.", modifier = Modifier.padding(vertical = 16.dp))
        }
        error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { request++ }, enabled = !loading) { Text("Réessayer") }
        } }
    }
    if (invitations) AlertDialog(onDismissRequest = { invitations = false }, title = { Text("Invitations") }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            if (pending.isEmpty()) item { Text("Aucune invitation en attente.") }
            items(pending, key = { it.id }) { friendship ->
                val player = (knownProfiles + members).firstOrNull { it.id == friendship.peer(uid) }
                    ?: PlayerProfile(friendship.peer(uid), if (friendship.requester != uid) friendship.requesterName.ifBlank { "Membre" } else "Membre", avatar = friendship.requesterAvatar)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CommunityAvatar(player.avatar, Modifier.size(40.dp)); Text(player.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (friendship.requester != uid) TextButton(onClick = { onAccept(friendship) }, enabled = !busy) { Text("Accepter") }
                        TextButton(onClick = { onRemove(friendship) }, enabled = !busy) { Text(if (friendship.requester == uid) "Annuler la demande" else "Refuser") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { invitations = false }) { Text("Fermer") } })
}
