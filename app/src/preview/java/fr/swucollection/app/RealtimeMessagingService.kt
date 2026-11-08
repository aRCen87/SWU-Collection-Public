package fr.swucollection.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*

/** User-visible private messaging connection. No paid relay, polling loop or wake lock. */
class RealtimeMessagingService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var listeners=emptyList<ListenerRegistration>();private var authListener:FirebaseAuth.AuthStateListener?=null
    private var backendService:CommunityService?=null;private var owner:String?=null;private var checkJob:Job?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){CommunityLiveConnection.setEnabled(this,false);stopSelf();return START_NOT_STICKY}
        val backend=CommunityService.create(this)?:run{stopSelf();return START_NOT_STICKY}
        val user=backend.auth.currentUser
        if(!CommunityLiveConnection.enabled(this) || user?.isEmailVerified!=true){stopSelf();return START_NOT_STICKY}
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Connexion de messagerie",NotificationManager.IMPORTANCE_LOW).apply{description="Connexion gratuite pour les messages, invitations et échanges"})
        val open=Intent(this,MainActivity::class.java).setAction(CommunityNotifications.ACTION).putExtra(CommunityNotifications.OWNER,user.uid).putExtra(CommunityNotifications.TAB,"Messages").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val stop=Intent(this,RealtimeMessagingService::class.java).setAction(STOP)
        val notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("Messagerie connectée").setContentText("Alertes de messages, amis et échanges actives")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(PendingIntent.getActivity(this,901,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(0,"Arrêter",PendingIntent.getService(this,902,stop,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        ServiceCompat.startForeground(this,NOTIFICATION,notification,if(Build.VERSION.SDK_INT>=34)ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if(owner==user.uid)return START_STICKY
        listeners.forEach{it.remove()};owner=user.uid;backendService=backend
        authListener?.let{backend.auth.removeAuthStateListener(it)}
        authListener=FirebaseAuth.AuthStateListener{if(it.currentUser?.uid!=owner || it.currentUser?.isEmailVerified!=true)stopSelf()}
        backend.auth.addAuthStateListener(authListener!!)
        fun changed(){checkJob?.cancel();checkJob=scope.launch{delay(600);withTimeoutOrNull(20_000){runCatching{CommunityNotifications.check(applicationContext,backend,includeSearchAlerts=false)}}}}
        fun failed(error:Exception){if(error is com.google.firebase.firestore.FirebaseFirestoreException && error.code==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)stopSelf()}
        listeners=listOf(backend.conversations({changed()},{failed(it)}),backend.friendships({changed()},{failed(it)}),backend.trades({changed()},{failed(it)}))
        return START_STICKY
    }
    override fun onDestroy(){listeners.forEach{it.remove()};authListener?.let{backendService?.auth?.removeAuthStateListener(it)};scope.cancel();super.onDestroy()}
    companion object{const val CHANNEL="community_connection";const val NOTIFICATION=1001;const val STOP="fr.swucollection.app.STOP_LIVE_MESSAGES"}
}
