package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    states: Map<String, MessengerThreadState> = emptyMap(), onState: (String,MessengerThreadState) -> Unit = {_,_->},
    onNew: () -> Unit, onFriends: () -> Unit, onOpen: (PlayerConversation, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PlayerConversation?>(null) }
    Text("Discussions", style = MaterialTheme.typography.titleLarge)
    Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Nouveau message") }
    OutlinedTextField(search, { search = it.take(80) }, label = { Text("Rechercher une discussion") },
        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
            Icon(Icons.Default.Close, "Effacer la recherche")
        } }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val rows = remember(uid, conversations, receipts, profiles, search) { messengerThreads(uid, conversations, receipts, profiles, search) }.filter { messengerVisible(it.conversation,states[it.conversation.id]?:MessengerThreadState()) }.sortedByDescending { states[it.conversation.id]?.pinned==true }
    if (rows.isEmpty()) {
        Text(if (conversations.isEmpty()) "Tes discussions apparaîtront ici. Choisis un ami pour lui écrire." else "Aucune discussion pour cette recherche.")
        TextButton(onClick = onFriends) { Text("Voir mes amis · Ajouter un ami") }
    }
    rows.forEach { row -> OutlinedCard(onClick = { onOpen(row.conversation, row.peer.name) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommunityAvatar(row.peer.avatar, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.peer.name, style = MaterialTheme.typography.titleMedium)
                val preferences=states[row.conversation.id]?:MessengerThreadState()
                Text((if(preferences.pinned)"📌 " else "")+(if(preferences.mutedUntil>System.currentTimeMillis())"Silencieux · " else "")+row.conversation.lastText.ifBlank { if(row.unread)"Nouveau message" else "Discussion privée" },maxLines=2,style=MaterialTheme.typography.bodySmall)
                if (row.conversation.updatedAt > 0) Text(SimpleDateFormat("dd/MM · HH:mm", Locale.FRANCE).format(Date(row.conversation.updatedAt)), style = MaterialTheme.typography.labelSmall)
            }
            if (row.unread) Badge { Text("Nouveau") }
            var menu by remember(row.conversation.id){mutableStateOf(false)}
            Box {
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Options de la conversation")}
                DropdownMenu(menu,{menu=false}) {
                    val state=states[row.conversation.id]?:MessengerThreadState()
                    DropdownMenuItem(text={Text(if(state.pinned)"Désépingler" else "Épingler")},onClick={menu=false;onState(row.conversation.id,state.copy(pinned=!state.pinned))})
                    listOf("1 heure" to 3_600_000L,"8 heures" to 28_800_000L,"Toujours" to Long.MAX_VALUE).forEach{(label,duration)->
                        DropdownMenuItem(text={Text("Silencieux · $label")},onClick={menu=false;onState(row.conversation.id,state.copy(mutedUntil=if(duration==Long.MAX_VALUE)duration else System.currentTimeMillis()+duration))})
                    }
                    if(state.mutedUntil>0)DropdownMenuItem(text={Text("Réactiver les alertes")},onClick={menu=false;onState(row.conversation.id,state.copy(mutedUntil=0))})
                    DropdownMenuItem(text={Text("Supprimer pour moi")},onClick={menu=false;removing=row.conversation})
                }
            }
        }
    } }
    removing?.let{thread->AlertDialog(onDismissRequest={removing=null},title={Text("Supprimer la conversation ?")},text={Text("L’historique sera masqué pour toi sur tes téléphones. Ton ami conserve sa copie. Un nouveau message fera réapparaître la discussion.")},confirmButton={TextButton(onClick={val state=states[thread.id]?:MessengerThreadState();onState(thread.id,state.copy(hiddenBefore=thread.updatedAt.coerceAtLeast(1)));removing=null}){Text("Supprimer")}},dismissButton={TextButton(onClick={removing=null}){Text("Annuler")}})}
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

@Composable internal fun MessengerFriendSearch(profiles:List<PlayerProfile>,onSelect:(PlayerProfile)->Unit) {
    var query by remember{mutableStateOf("")}
    OutlinedTextField(query,{query=it.take(32)},label={Text("Rechercher un pseudo")},singleLine=true,modifier=Modifier.fillMaxWidth())
    Text("Seuls les joueurs ayant rendu leur profil visible apparaissent. Jusqu’à 50 profils disponibles.",style=MaterialTheme.typography.bodySmall)
    if(query.trim().length>=2){
        val found=profiles.filter{it.name.contains(query.trim(),true)}.sortedBy{it.name.lowercase()}
        if(found.isEmpty())Text("Aucun joueur trouvé pour ce pseudo.")
        found.forEach{player->OutlinedCard(onClick={onSelect(player)},modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                CommunityAvatar(player.avatar,Modifier.size(42.dp));Text(player.name,Modifier.weight(1f));Text("Voir ›",color=PremiumGold)
            }
        }}
    }else Text("Saisis au moins deux caractères.")
}
