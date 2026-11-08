package fr.swucollection.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

class TestCommunityApplication:Application() {
    override fun onCreate(){super.onCreate();val service=CommunityService.create(this)?:return
        val options=service.auth.app.options
        if(FirebaseApp.getApps(this).none{it.name==FirebaseApp.DEFAULT_APP_NAME})FirebaseApp.initializeApp(this,FirebaseOptions.Builder(options).setGcmSenderId(options.applicationId.split(':').getOrNull(1)).build())
        service.auth.addAuthStateListener{auth->if(auth.currentUser?.isEmailVerified==true)CommunityPushRegistration.register(this)}
        service.auth.addIdTokenListener(FirebaseAuth.IdTokenListener{auth->if(auth.currentUser?.isEmailVerified==true)CommunityPushRegistration.register(this)})
    }
}
internal object CommunityPushRegistration {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun register(context:android.content.Context){scope.launch{
        try{val service=CommunityService.create(context)?:return@launch;val user=service.auth.currentUser?.takeIf{it.isEmailVerified}?:return@launch
            if(!service.allowed())return@launch
            val token=FirebaseMessaging.getInstance().token.communityAwait()
            if(service.auth.currentUser?.uid!=user.uid)return@launch
            val id=java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)}
            service.accountStore().collection("pushDevices").document(user.uid).collection("devices").document(id)
                .set(mapOf("token" to token,"package" to AppChannel.TEST_PACKAGE,"updatedAt" to FieldValue.serverTimestamp())).communityAwait()
        }catch(_:Exception){/* Periodic alerts remain available until the relay is configured. */}
    }}
}
class CommunityPushService:FirebaseMessagingService(){
    override fun onNewToken(token:String){CommunityPushRegistration.register(applicationContext)}
    override fun onMessageReceived(message:RemoteMessage){
        val service=CommunityService.create(this)?:return
        val owner=message.data["owner"]?:return
        if(service.auth.currentUser?.uid!=owner || service.auth.currentUser?.isEmailVerified!=true)return
        val tab=message.data["tab"]?.takeIf{it in listOf("Messages","Amis","Échanges")}?:return
        val key=message.data["key"]?:return;val version=message.data["version"]?.toLongOrNull()?:return
        runBlocking{withTimeoutOrNull(7000){
            try{
                if(tab=="Messages"){
                    val thread=key.removePrefix("message:");val state=service.accountStore().collection("messenger").document(owner).collection("threads").document(thread).get().communityAwait().toThreadState()
                    if(state.mutedUntil>System.currentTimeMillis() || version<=state.hiddenBefore)return@withTimeoutOrNull
                }
                CommunityNotifications.offer(applicationContext,owner,CommunityNotice(key,tab,version))
            }catch(_:Exception){}
        }}
    }
}
