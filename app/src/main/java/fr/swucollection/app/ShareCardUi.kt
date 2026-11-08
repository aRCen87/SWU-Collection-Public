package fr.swucollection.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun CardShareDialog(shared:SharedPlayerCard,catalog:List<CatalogCard>,onClose:()->Unit) {
    val context=LocalContext.current;val service=remember{CommunityService.create(context)};val uid=service?.auth?.currentUser?.uid
    var friends by remember{mutableStateOf<List<PlayerProfile>>(emptyList())};var loaded by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<Set<String>>(emptySet())};var search by remember{mutableStateOf("")};var note by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var result by remember{mutableStateOf<String?>(null)};var loadError by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val ids=remember{mutableMapOf<String,String>()}
    LaunchedEffect(uid){
        if(service==null || uid==null || service.auth.currentUser?.isEmailVerified!=true){loaded=true;return@LaunchedEffect}
        try{val db=service.accountStore();val rows=db.collection("friendships").whereArrayContains("members",uid).limit(100).get().communityAwait()
            friends=rows.documents.filter{it.getString("status")=="accepted"}.mapNotNull{row->
                val peer=(row.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull{it!=uid}
                peer?.let{runCatching{service.profile(it)}.getOrNull()}
            }.sortedBy{it.name.lowercase()}
        }catch(e:CancellationException){throw e}catch(e:Exception){loadError=communityError(e)}finally{loaded=true}
    }
    StableCollectorDialog(onDismissRequest={if(!busy)onClose()},title={Text("Partager à des amis")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            val card=catalog.firstOrNull{it.setCode==shared.set && it.number.toIntOrNull()==shared.number.toIntOrNull()}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                CardArtwork(card,Modifier.size(52.dp,73.dp),language=shared.language,variant=shared.variant)
                Column{Text(card?.name?:"${shared.set} ${shared.number}",fontWeight=FontWeight.Bold);Text("${shared.language} · ${shared.variant}",style=MaterialTheme.typography.bodySmall)}
            }
            if(uid==null)Text("Connecte-toi dans Mon profil pour partager une carte.")
            else if(!loaded)LinearProgressIndicator(Modifier.fillMaxWidth())
            else if(loadError==null && friends.isEmpty())Text("Ajoute un ami ou accepte une demande dans Messagerie → Amis.")
            loadError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(friends.isNotEmpty()){
                OutlinedTextField(search,{search=it.take(80)},label={Text("Rechercher un pseudo")},singleLine=true,modifier=Modifier.fillMaxWidth())
                friends.filter{it.name.contains(search.trim(),true)}.forEach {friend->
                    Row(Modifier.fillMaxWidth().clickable(enabled=!busy){selected=if(friend.id in selected)selected-friend.id else selected+friend.id}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        CommunityAvatar(friend.avatar,Modifier.size(42.dp));Text(friend.name,Modifier.weight(1f));Checkbox(friend.id in selected,{v->if(!busy)selected=if(v)selected+friend.id else selected-friend.id},enabled=!busy)
                    }
                }
                OutlinedTextField(note,{note=it.take(2000)},label={Text("Message facultatif")},maxLines=3,enabled=!busy && result==null,modifier=Modifier.fillMaxWidth())
                Text("Un message privé distinct sera envoyé à chaque ami sélectionné. Aucune carte ne sera déplacée.",style=MaterialTheme.typography.bodySmall)
            }
            result?.let{Text(it,color=PremiumGold)}
        }
    },confirmButton={Button(onClick={if(service!=null && !busy)scope.launch{
        busy=true;result=null;val targets=selected.toList();val failed=mutableSetOf<String>();var sent=0
        for(peer in targets)try{
            withTimeout(20_000){val thread=service.openConversation(peer);service.sendRich(thread,ids.getOrPut(peer){UUID.randomUUID().toString()},note,card=shared)};ids.remove(peer);sent++
        }catch(e:CancellationException){if(e is TimeoutCancellationException)failed+=peer else throw e}catch(_:Exception){failed+=peer}
        selected=failed;busy=false;result=if(failed.isEmpty())"Carte envoyée à $sent ami(s)." else "$sent envoi(s) confirmé(s). ${failed.size} envoi(s) à réessayer ; les autres ne seront pas renvoyés."
    }},enabled=selected.isNotEmpty()&&!busy){Text(if(busy)"Envoi…" else "Envoyer à ${selected.size} ami${if(selected.size>1)"s" else ""}")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Fermer")}})
}
