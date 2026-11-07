package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class MessengerThread(val conversation: PlayerConversation, val peer: PlayerProfile, val unread: Boolean)
internal fun messengerThreads(uid: String, conversations: List<PlayerConversation>, receipts: Map<String, Long>, profiles: Collection<PlayerProfile>, search: String): List<MessengerThread> {
    val players = profiles.associateBy { it.id }
    return conversations.filter { uid in it.members }.sortedByDescending { it.updatedAt }.mapNotNull { thread ->
        val id = thread.members.firstOrNull { it != uid } ?: return@mapNotNull null
        val peer = players[id] ?: PlayerProfile(id, "Conversation privée")
        if (!peer.name.contains(search.trim(), true)) return@mapNotNull null
        MessengerThread(thread, peer, thread.lastSenderId.isNotBlank() && thread.lastSenderId != uid && thread.updatedAt > (receipts[thread.id] ?: 0L))
    }
}

@Composable
internal fun MessengerInbox(uid: String, conversations: List<PlayerConversation>, receipts: Map<String, Long>, profiles: Collection<PlayerProfile>,
    onNew: () -> Unit, onFriends: () -> Unit, onOpen: (PlayerConversation, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    Text("Discussions", style = MaterialTheme.typography.titleLarge)
    Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Nouveau message") }
    OutlinedTextField(search, { search = it.take(80) }, label = { Text("Rechercher une discussion") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val rows = remember(uid, conversations, receipts, profiles, search) { messengerThreads(uid, conversations, receipts, profiles, search) }
    if (rows.isEmpty()) {
        Text(if (conversations.isEmpty()) "Tes discussions apparaîtront ici. Choisis un ami pour lui écrire." else "Aucune discussion pour cette recherche.")
        TextButton(onClick = onFriends) { Text("Voir mes amis · Ajouter un ami") }
    }
    rows.forEach { row -> OutlinedCard(onClick = { onOpen(row.conversation, row.peer.name) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommunityAvatar(row.peer.avatar, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.peer.name, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    row.unread -> "Nouveau message"
                    row.conversation.lastSenderId.isBlank() -> "Commencer la discussion"
                    row.conversation.lastSenderId == uid -> "Ton dernier message"
                    else -> "Discussion privée"
                }, style = MaterialTheme.typography.bodySmall)
                if (row.conversation.updatedAt > 0) Text(SimpleDateFormat("dd/MM · HH:mm", Locale.FRANCE).format(Date(row.conversation.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            if (row.unread) Badge { Text("Nouveau") }
        }
    } }
}

@Composable
internal fun MessengerRecipientPicker(friends: List<PlayerProfile>, onClose: () -> Unit, onAdd: () -> Unit, onChoose: (PlayerProfile) -> Unit) {
    var search by remember { mutableStateOf("") }
    StableCollectorDialog(onDismissRequest = onClose, title = { Text("Écrire à un ami") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search, { search = it.take(80) }, label = { Text("Nom d’un ami") }, singleLine = true)
            val visible = friends.filter { it.name.contains(search.trim(), true) }.sortedBy { it.name.lowercase() }
            if (visible.isEmpty()) Text(if (friends.isEmpty()) "Ajoute un ami ou accepte une demande pour commencer." else "Aucun ami pour cette recherche.")
            visible.forEach { friend -> OutlinedCard(onClick = { onChoose(friend) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CommunityAvatar(friend.avatar, Modifier.size(40.dp)); Text(friend.name)
                }
            } }
        }
    }, confirmButton = { TextButton(onClick = onAdd) { Text("Ajouter un ami") } }, dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } })
}
