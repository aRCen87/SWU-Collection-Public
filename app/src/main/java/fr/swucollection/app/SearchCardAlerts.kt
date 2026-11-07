package fr.swucollection.app

import android.content.Context
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException

internal object SearchCardAlertPolicy {
    fun fingerprint(matches: List<TradeMatch>): String = PrivatePayload.hash(matches.map {it.offered.key+":"+it.wanted.key+":"+it.quantity}.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
    fun changed(previous: String?, current: String, hasMatches: Boolean) = hasMatches && previous!=current
}
internal object SearchCardAlerts {
    suspend fun check(context: Context,service: CommunityService) {
        val owner=service.uid
        if(!CommunityNotifications.enabled(context,owner,"Échanges")) return
        val prefs=context.getSharedPreferences("community_notifications",Context.MODE_PRIVATE)
        val mine=CollectionDatabase(context.applicationContext).use {db ->
            val catalog=db.allCatalogSets().flatMap {db.catalogCardsForSet(it.code)}
            TradeProfile.build(catalog,db.allCards(),db.goals(),CardDetails.load(context),variantsBySet=db.goalVariants(),reserved=db.loans().reserved())
        }
        if(mine.needs.isEmpty()) return
        val rows=service.accountStore().collection("friendships").whereArrayContains("members",owner).limit(100).get(Source.SERVER).communityAwait()
        for(row in rows.documents.filter {it.getString("status")=="accepted"}) {
            check(service.auth.currentUser?.uid==owner)
            val peer=(row.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull {it!=owner} ?: continue
            try {
                if(service.isBlocked(peer)) continue
                val theirs=service.tradeInventory(peer) ?: continue
                val matches=TradeComparison.matching(theirs,mine)
                val fingerprint=SearchCardAlertPolicy.fingerprint(matches)
                val key="$owner.search.$peer"
                if(SearchCardAlertPolicy.changed(prefs.getString(key,null),fingerprint,matches.isNotEmpty())) {
                    if(!CommunityNotifications.offer(context,owner,CommunityNotice("search:$peer","Échanges",System.currentTimeMillis()))) continue
                }
                prefs.edit().putString(key,fingerprint).apply()
            } catch(cancelled: CancellationException) {throw cancelled}
            catch(_: Exception) { /* Revoked/blocked/private/offline inventories produce no alert. */ }
        }
    }
}
