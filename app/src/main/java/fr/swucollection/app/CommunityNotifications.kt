package fr.swucollection.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal data class CommunityNotice(val key: String, val tab: String, val version: Long)
internal object CommunityNoticePolicy {
    fun messages(uid: String, conversations: List<PlayerConversation>, reads: Map<String, Long>): List<CommunityNotice> = conversations
        .filter { it.lastSenderId.isNotBlank() && it.lastSenderId != uid && it.updatedAt > (reads[it.id] ?: 0L) }
        .map { CommunityNotice("message:" + it.id, "Messages", it.updatedAt) }
    fun shouldNotify(notice: CommunityNotice, last: Long) = notice.version > last && notice.version > 0
}

internal object CommunityNotifications {
    var activeThread: String? = null
    const val ACTION = "fr.swucollection.app.OPEN_COMMUNITY"
    const val OWNER = "community_owner"
    const val TAB = "community_tab"
    private const val MESSAGES = "community_messages"
    private const val FRIENDS = "community_friends"
    private const val SEARCH = "community_search_cards"
    private const val TRADES = "community_trades"
    private fun channel(tab: String) = when(tab) { "Messages" -> MESSAGES; "Échanges" -> TRADES; "Recherches" -> SEARCH; else -> FRIENDS }
    private fun prefs(context: Context) = context.getSharedPreferences("community_notifications", Context.MODE_PRIVATE)
    fun pending(context: Context, uid: String, notice: CommunityNotice) = CommunityNoticePolicy.shouldNotify(notice, prefs(context).getLong("$uid.${notice.key}.notified", 0))
    fun enabled(context: Context, uid: String, tab: String) = prefs(context).getBoolean("$uid.$tab.enabled", tab!="Recherches")
    fun setEnabled(context: Context, uid: String, tab: String, value: Boolean) {
        prefs(context).edit().putBoolean("$uid.$tab.enabled", value).apply()
        if (!value) clear(context, channel(tab))
    }
    fun channels(context: Context) {
        if (!AppChannel.supportsExperience(context.packageName)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(MESSAGES to "Messages privés", FRIENDS to "Demandes d’amis", TRADES to "Échanges", SEARCH to "Cartes recherchées").forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Messages, invitations et échanges de la communauté Test"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }
    private fun permission(context: Context) = (Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    @SuppressLint("MissingPermission")
    @Synchronized
    fun offer(context: Context, uid: String, notice: CommunityNotice): Boolean {
        if(CommunityLiveConnection.activityVisible && notice.key=="message:$activeThread")return false
        if (!AppChannel.supportsExperience(context.packageName) || !enabled(context, uid, if(notice.key.startsWith("search:"))"Recherches" else notice.tab) || !permission(context)) return false
        if (CommunityService.create(context)?.auth?.currentUser?.uid != uid) return false
        channels(context)
        val channel = if(notice.key.startsWith("search:"))SEARCH else channel(notice.tab)
        if (context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val marker = "$uid.${notice.key}.notified"
        if (!CommunityNoticePolicy.shouldNotify(notice, prefs(context).getLong(marker, 0))) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION; data = Uri.parse("swu-test://community/" + Uri.encode(uid) + "/" + Uri.encode(notice.key))
            putExtra(OWNER, uid); putExtra(TAB, notice.tab)
            if(notice.key.startsWith("message:"))putExtra("community_thread",notice.key.removePrefix("message:"))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (notice.key == "local-test") "Notification de test" else if (notice.tab == "Messages") "Nouveau message privé" else if (notice.key.startsWith("trade:")) "Échange mis à jour" else if (notice.tab == "Échanges") "Cartes recherchées chez un ami" else if(notice.key.startsWith("friend-accepted:"))"Demande d’ami acceptée" else "Nouvelle demande d’ami"
        val public = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("SWU Collection Test").setContentText("Une activité est disponible dans la communauté.").build()
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle(title).setContentText("Ouvre la communauté pour la consulter.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(false).build()
        return try {
            NotificationManagerCompat.from(context).notify("$uid:${notice.key}", 1, notification)
            prefs(context).edit().putLong(marker, notice.version).commit()
        } catch (_: SecurityException) { false }
    }
    fun clear(context: Context, channel: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.notification.channelId in listOf(MESSAGES, FRIENDS, SEARCH, TRADES) &&
            (channel == null || it.notification.channelId == channel) }.forEach { manager.cancel(it.tag, it.id) }
    }
    fun destination(context: Context, intent: Intent): String? {
        if (!AppChannel.supportsExperience(context.packageName) || intent.action != ACTION) return null
        val uid = CommunityService.create(context)?.auth?.currentUser?.uid ?: return null
        if (intent.getStringExtra(OWNER) != uid) return null
        val tab=intent.getStringExtra(TAB)?.takeIf { it in listOf("Messages", "Amis", "Échanges") }?:return null
        val thread=intent.getStringExtra("community_thread").orEmpty()
        return tab+if(tab=="Messages" && Regex("[A-Za-z0-9_-]{1,260}").matches(thread))"|$thread" else ""
    }
    suspend fun check(context: Context, service: CommunityService, includeSearchAlerts: Boolean = true) {
        val uid = service.auth.currentUser?.takeIf { it.isEmailVerified }?.uid ?: return
        if (!permission(context)) return
        if (!enabled(context, uid, "Messages") && !enabled(context, uid, "Amis") && !enabled(context, uid, "Échanges") && !enabled(context,uid,"Recherches")) return
        if (!service.allowed()) return
        val db = service.accountStore()
        val threadStates=db.collection("messenger").document(uid).collection("threads").limit(100).get(Source.SERVER).communityAwait().documents.associate{it.id to it.toThreadState()}
        if (enabled(context, uid, "Messages")) {
            val conversations = db.collection("conversations").whereArrayContains("members", uid).limit(100).get(Source.SERVER).communityAwait().documents.map {
                PlayerConversation(it.id, (it.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    it.getTimestamp("updatedAt")?.toDate()?.time ?: 0, it.getString("lastSenderId").orEmpty())
            }
            val reads = db.collection("reads").document(uid).collection("threads").limit(100).get(Source.SERVER).communityAwait().documents
                .associate { it.id to (it.getTimestamp("seenAt")?.toDate()?.time ?: 0L) }
            for (notice in CommunityNoticePolicy.messages(uid, conversations, reads)) {
                val state=threadStates[notice.key.removePrefix("message:")]?:MessengerThreadState()
                if(state.mutedUntil>System.currentTimeMillis() || notice.version<=state.hiddenBefore || !pending(context, uid, notice)) continue
                check(service.auth.currentUser?.uid == uid)
                val peer = conversations.first { "message:" + it.id == notice.key }.members.firstOrNull { it != uid } ?: continue
                if (!service.isBlocked(peer)) offer(context, uid, notice)
            }
        }
        if (enabled(context, uid, "Amis")) {
            val pending = db.collection("friendships").whereArrayContains("members", uid).limit(100).get(Source.SERVER).communityAwait()
            for (row in pending.documents.filter { (it.getString("status") == "pending" && it.getString("requester") != uid) || (it.getString("status")=="accepted" && it.getString("requester")==uid) }) {
                check(service.auth.currentUser?.uid == uid)
                val peer = (row.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull{it!=uid} ?: continue
                val notice = CommunityNotice((if(row.getString("status")=="accepted")"friend-accepted:" else "friend:") + row.id, "Amis", row.getTimestamp("updatedAt")?.toDate()?.time ?: 0)
                if (pending(context, uid, notice) && !service.isBlocked(peer)) offer(context, uid, notice)
            }
        }
        if(enabled(context,uid,"Échanges")) {
            for(row in db.collection("trades").whereArrayContains("members",uid).limit(100).get(Source.SERVER).communityAwait().documents) {
                val requester=row.getString("requester");val status=row.getString("status")
                val actor=if(status in listOf("pending","cancelled"))requester else (row.get("members") as? List<*>)?.firstOrNull{it!=requester}
                if(actor==uid)continue
                val peer=(row.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull{it!=uid}?:continue
                if(!service.isBlocked(peer))offer(context,uid,CommunityNotice("trade:"+row.id,"Échanges",row.getTimestamp("updatedAt")?.toDate()?.time?:0))
            }
        }
        if(includeSearchAlerts) SearchCardAlerts.check(context, service)
    }
}

class CommunityNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val service = CommunityService.create(applicationContext) ?: return@withContext Result.success()
        try { CommunityNotifications.check(applicationContext, service); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) {
            if (!AppChannel.supportsExperience(context.packageName)) return
            CommunityNotifications.channels(context)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("community-notifications", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CommunityNotificationWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("community-notifications") }
    }
}

internal object CommunityBackground {
    private var listener: FirebaseAuth.AuthStateListener? = null
    fun install(context: Context) {
        val service = CommunityService.create(context) ?: return
        if (listener != null) return
        listener = FirebaseAuth.AuthStateListener { auth ->
            CommunityNotifications.clear(context)
            if (auth.currentUser == null) { AutomaticBackupWorker.cancel(context); CommunityNotificationWorker.cancel(context) }
            else refresh(context)
        }
        service.auth.addAuthStateListener(listener!!)
    }
    fun refresh(context: Context) {
        val service = CommunityService.create(context) ?: return
        if (service.auth.currentUser?.isEmailVerified != true) return
        AutomaticBackupWorker.schedule(context); AutomaticBackupWorker.enqueue(context)
        CommunityNotificationWorker.schedule(context)
        CommunityLiveConnection.refresh(context)
    }
}

@Composable
internal fun CommunityNotificationControls(uid: String) {
    val context = LocalContext.current
    var testStatus by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Notifications de la communauté", style = MaterialTheme.typography.titleMedium)
        var live by remember {mutableStateOf(CommunityLiveConnection.enabled(context))}
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Text("Connexion en arrière-plan · gratuite",Modifier.weight(1f));Switch(live,{live=it;CommunityLiveConnection.setEnabled(context,it)})
        }
        Text("Une notification discrète Messagerie connectée maintient l’écoute des nouveaux messages, invitations et échanges. Tu peux l’arrêter ici ou depuis la notification. Cette connexion peut consommer davantage de batterie et de données. Android ou le téléphone peuvent l’interrompre ; rouvre alors l’application.",style=MaterialTheme.typography.bodySmall)
        listOf("Messages" to "Messages privés", "Amis" to "Demandes d’amis", "Échanges" to "Propositions et réponses d’échange", "Recherches" to "Cartes recherchées chez mes amis").forEach { (key, label) ->
            var enabled by remember(uid, key) { mutableStateOf(CommunityNotifications.enabled(context, uid, key)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); Switch(enabled, { enabled = it; CommunityNotifications.setEnabled(context, uid, key, it) })
            }
        }
        Text("En secours, une vérification périodique a lieu environ toutes les 15 minutes. Android peut la retarder quand le téléphone dort. Sans connexion en arrière-plan active, les alertes peuvent être retardées et nécessitent Internet et l’autorisation Android. Aucun texte de message n’apparaît sur l’écran verrouillé.", style = MaterialTheme.typography.bodySmall)
        Text("Alertes de recherche : active ce réglage, définis tes objectifs dans Compléter mon set, puis tes amis doivent partager leur liste d’échange. Les correspondances sont recalculées sans publier tes recherches. Une liste privée, retirée ou bloquée ne déclenche aucune alerte.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
            Text("Paramètres des notifications Android")
        }
        OutlinedButton(onClick = {
            testStatus = if (CommunityNotifications.offer(context, uid, CommunityNotice("local-test", "Messages", System.currentTimeMillis())))
                "Notification de test affichée. Aucun message n’a été envoyé à un joueur."
            else "Active les messages et l’autorisation Android pour afficher le test."
        }) { Text("Tester une alerte locale") }
        testStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
