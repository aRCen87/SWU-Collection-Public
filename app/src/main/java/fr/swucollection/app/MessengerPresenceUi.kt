package fr.swucollection.app
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

@Composable internal fun rememberPlayerPresence(service:CommunityService,id:String): PlayerPresence {
    var presence by remember(service,id){mutableStateOf(PlayerPresence())}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(service,id,owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val listener=service.presence(id){presence=it}
            try { awaitCancellation() } finally { listener.remove() }
        }
    }
    return presence
}
@Composable internal fun MessengerPeerAvatar(player:PlayerProfile,presence:PlayerPresence,now:Long) {
    Box(Modifier.size(56.dp)) {
        CommunityAvatar(player.avatar,Modifier.fillMaxSize())
        if(presence.at>0) Box(Modifier.align(Alignment.BottomEnd).size(14.dp)
            .background(if(presence.isOnline(now))Color(0xFF72D8A3) else MaterialTheme.colorScheme.outline,CircleShape)
            .border(2.dp,MaterialTheme.colorScheme.surface,CircleShape)
            .semantics{contentDescription=if(presence.isOnline(now))"${player.name} en ligne" else "${player.name} hors ligne"})
    }
}
@Composable internal fun FriendPresence(service:CommunityService,id:String) {
    val p=rememberPlayerPresence(service,id);var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(id){while(true){delay(15_000);now=System.currentTimeMillis()}}
    Text((if(p.online && now-p.at in 0..75_000)"● " else "")+p.label(now),fontSize=10.sp,color=if(p.online && now-p.at in 0..75_000)androidx.compose.ui.graphics.Color(0xFF72D8A3) else MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun CommunityPresenceHost() {
    val context=LocalContext.current;val service=remember{CommunityService.create(context)}?:return
    var user by remember{mutableStateOf(service.auth.currentUser)};val owner=LocalLifecycleOwner.current
    var settings by remember{mutableStateOf(MessengerPreferences())}
    DisposableEffect(service){val l=FirebaseAuth.AuthStateListener{user=it.currentUser};service.auth.addAuthStateListener(l);onDispose{service.auth.removeAuthStateListener(l)}}
    DisposableEffect(user?.uid){val l=if(user?.isEmailVerified==true)service.messengerPreferences({settings=it},{}) else null;onDispose{l?.remove()}}
    LaunchedEffect(user?.uid,settings.sharePresence,owner){
        val uid=user?.takeIf{it.isEmailVerified}?.uid?:return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            try{while(isActive && service.auth.currentUser?.uid==uid){runCatching{service.publishPresence(settings.sharePresence,true)};delay(45_000)}}
            finally{withContext(NonCancellable){withTimeoutOrNull(3000){if(service.auth.currentUser?.uid==uid)runCatching{service.publishPresence(settings.sharePresence,false)}}}}
        }
    }
}
