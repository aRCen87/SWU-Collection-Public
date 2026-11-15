package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class MemberPage(val profiles: List<PlayerProfile>, val cursor: String?, val hasMore: Boolean)

internal fun matchingMembers(profiles: List<PlayerProfile>, uid: String, query: String): List<PlayerProfile> =
    profiles.asSequence().filter { it.id != uid && it.discoverable && it.name.contains(query.trim(), true) }
        .distinctBy { it.id }.sortedWith(compareBy<PlayerProfile> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id }).toList()

/** A blank search browses pages; a pseudo search continues through every remaining page. */
@Composable
internal fun MemberDirectory(uid: String, friendships: List<PlayerFriendship>, busy: Boolean,
    loadPage: suspend (String?) -> MemberPage, onRequest: (String) -> Unit, onProfile: (PlayerProfile) -> Unit) {
    var query by rememberSaveable(uid) { mutableStateOf("") }
    var members by remember(uid) { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var cursor by remember(uid) { mutableStateOf<String?>(null) }
    var started by remember(uid) { mutableStateOf(false) }
    var hasMore by remember(uid) { mutableStateOf(true) }
    var loading by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var request by remember(uid) { mutableIntStateOf(0) }
    var visibleCount by rememberSaveable(uid, query) { mutableIntStateOf(20) }
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
    Text("Membres", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(query, { query = it.take(32) }, label = { Text("Rechercher un pseudo") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Retrouve les membres et invite-les en amis. Seuls les profils visibles apparaissent.", style = MaterialTheme.typography.bodySmall)
    val found = remember(members, uid, query) { matchingMembers(members, uid, query) }
    val relations = remember(friendships, uid) { friendships.associateBy { it.peer(uid) } }
    found.take(visibleCount).forEach { player -> key(player.id) {
        val friend = relations[player.id]
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityAvatar(player.avatar, Modifier.size(42.dp))
                TextButton(onClick = { onProfile(player) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                    Text(player.name, fontWeight = FontWeight.Bold)
                }
                if (friend == null) TextButton(onClick = { onRequest(player.id) }, enabled = !busy) { Text("Ajouter") }
                else Text(when {
                    friend.status == "accepted" -> "Déjà amis"
                    friend.requester == uid -> "Demande envoyée"
                    else -> "Invitation reçue"
                }, modifier = Modifier.widthIn(max = 110.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    } }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!loading && error == null && found.isEmpty()) Text(if (query.isBlank()) "Aucun autre membre visible pour le moment." else "Aucun membre trouvé pour ce pseudo.")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (error != null) TextButton(onClick = { request++ }, enabled = !loading) { Text("Réessayer") }
    if (visibleCount < found.size || hasMore) TextButton(onClick = {
        visibleCount += 20
        if (visibleCount > found.size && hasMore) request++
    }, enabled = !loading) { Text("Afficher plus de membres") }
}
