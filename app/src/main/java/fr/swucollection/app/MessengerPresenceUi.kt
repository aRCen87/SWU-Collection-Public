package fr.swucollection.app
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

@Composable internal fun FriendPresence(service:CommunityService,id:String) {
    var p by remember(id){mutableStateOf(PlayerPresence())};var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    DisposableEffect(id){val listener=service.presence(id){p=it};onDispose{listener.remove()}}
    LaunchedEffect(id){while(true){delay(15_000);now=System.currentTimeMillis()}}
    Text((if(p.online && now-p.at in 0..75_000)"● " else "")+p.label(now),fontSize=10.sp,color=if(p.online && now-p.at in 0..75_000)androidx.compose.ui.graphics.Color(0xFF72D8A3) else MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun CommunityPresenceHost() {
    val context=LocalContext.current;val service=remember{CommunityService.create(context)}?:return
    var revision by remember { mutableIntStateOf(0) }
    var user by remember{mutableStateOf(service.auth.currentUser)};val owner=LocalLifecycleOwner.current
    var settings by remember{mutableStateOf(MessengerPreferences())}
    DisposableEffect(service){val l=FirebaseAuth.AuthStateListener{user=it.currentUser};service.auth.addAuthStateListener(l);onDispose{service.auth.removeAuthStateListener(l)}}
    DisposableEffect(user?.uid){val l=if(user?.isEmailVerified==true)service.messengerPreferences({settings=it},{}) else null;onDispose{l?.remove()}}
    DisposableEffect(user?.uid){
        val subscriptions=if(user?.isEmailVerified==true)listOf(service.conversations({revision++},{}),service.friendships({revision++},{}),service.trades({revision++},{})) else emptyList()
        onDispose{subscriptions.forEach{it.remove()}}
    }
    LaunchedEffect(revision,user?.uid){delay(700);if(user?.isEmailVerified==true)runCatching{CommunityNotifications.check(context,service)}}
    LaunchedEffect(user?.uid,settings.sharePresence,owner){
        val uid=user?.takeIf{it.isEmailVerified}?.uid?:return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){
            try{while(isActive && service.auth.currentUser?.uid==uid){runCatching{service.publishPresence(settings.sharePresence,true)};delay(45_000)}}
            finally{withContext(NonCancellable){withTimeoutOrNull(3000){if(service.auth.currentUser?.uid==uid)runCatching{service.publishPresence(settings.sharePresence,false)}}}}
        }
    }
}
