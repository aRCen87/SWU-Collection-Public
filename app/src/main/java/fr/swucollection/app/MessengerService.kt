package fr.swucollection.app

import com.google.firebase.firestore.*
import org.json.JSONObject

internal data class SharedPlayerCard(val set: String, val number: String, val language: String, val variant: String) {
    fun json(): String = JSONObject().put("set",set).put("number",number).put("language",language).put("variant",variant).toString()
    companion object {
        fun parse(raw: String): SharedPlayerCard {
            require(raw.length <= 300)
            val o=JSONObject(raw)
            return SharedPlayerCard(o.getString("set"),o.getString("number"),o.getString("language"),o.getString("variant")).also {
                require(Regex("[A-Z0-9]{2,8}").matches(it.set) && Regex("[0-9]{1,5}").matches(it.number))
                require(it.language in CardLanguages.supported && it.variant.length <= 60)
            }
        }
    }
}
internal data class MessengerPreferences(val sharePresence: Boolean = true, val shareRead: Boolean = true)
internal data class MessengerThreadState(val hiddenBefore: Long = 0, val pinned: Boolean = false, val mutedUntil: Long = 0, val hiddenMessages: List<String> = emptyList())
internal data class MessengerSignal(val deliveredAt: Long = 0, val seenAt: Long = 0, val typingAt: Long = 0, val typing: Boolean = false)
internal data class PlayerPresence(val online: Boolean = false, val at: Long = 0) {
    fun label(now: Long): String = when {
        at<=0 -> "Statut masqué"
        online && now-at in 0..75_000 -> "En ligne"
        now-at<120_000 -> "Actif à l’instant"
        now-at<3_600_000 -> "Actif il y a ${(now-at).coerceAtLeast(0)/60_000} min"
        else -> "Hors ligne"
    }
}
internal fun messengerVisible(thread: PlayerConversation,state: MessengerThreadState) = state.hiddenBefore==0L || thread.updatedAt>state.hiddenBefore
internal fun DocumentSnapshot.toThreadState() = MessengerThreadState(getLong("hiddenBefore")?:0,getBoolean("pinned")==true,getLong("mutedUntil")?:0,
    (get("hiddenMessages") as? List<*>)?.filterIsInstance<String>().orEmpty())
internal fun CommunityService.messengerPreferences(onValue:(MessengerPreferences)->Unit,onError:(Exception)->Unit): ListenerRegistration =
    accountStore().collection("messengerSettings").document(uid).addSnapshotListener { d,e -> if(e!=null)onError(e) else onValue(MessengerPreferences(d?.getBoolean("sharePresence")!=false,d?.getBoolean("shareRead")!=false)) }
internal suspend fun CommunityService.saveMessengerPreferences(p: MessengerPreferences) {
    accountStore().collection("messengerSettings").document(uid).set(mapOf("sharePresence" to p.sharePresence,"shareRead" to p.shareRead)).communityAwait()
    publishPresence(p.sharePresence,true)
}
internal fun CommunityService.threadStates(onValue:(Map<String,MessengerThreadState>)->Unit,onError:(Exception)->Unit): ListenerRegistration =
    accountStore().collection("messenger").document(uid).collection("threads").limit(100).addSnapshotListener { d,e -> if(e!=null)onError(e) else onValue(d?.documents.orEmpty().associate {it.id to it.toThreadState()}) }
internal suspend fun CommunityService.saveThreadState(id:String,state:MessengerThreadState) {
    require(state.hiddenMessages.distinct().size<=500){"500 messages sont déjà masqués. Tu peux supprimer la conversation pour masquer son historique."}
    accountStore().collection("messenger").document(uid).collection("threads").document(id).set(mapOf("hiddenBefore" to state.hiddenBefore,"pinned" to state.pinned,"mutedUntil" to state.mutedUntil,"hiddenMessages" to state.hiddenMessages.distinct())).communityAwait()
}
internal suspend fun CommunityService.publishPresence(visible:Boolean,online:Boolean) {
    accountStore().collection("presence").document(uid).set(mapOf("visible" to visible,"online" to online,"updatedAt" to FieldValue.serverTimestamp())).communityAwait()
}
internal fun CommunityService.presence(peer:String,onValue:(PlayerPresence)->Unit):ListenerRegistration =
    accountStore().collection("presence").document(peer).addSnapshotListener { d,e -> onValue(if(e==null && d?.getBoolean("visible")==true) PlayerPresence(d.getBoolean("online")==true,d.getTimestamp("updatedAt")?.toDate()?.time?:0) else PlayerPresence()) }
internal fun CommunityService.signal(thread:String,peer:String,onValue:(MessengerSignal)->Unit):ListenerRegistration =
    accountStore().collection("conversations").document(thread).collection("signals").document(peer).addSnapshotListener {d,e ->
        onValue(if(e!=null)MessengerSignal() else MessengerSignal(d?.getTimestamp("deliveredAt")?.toDate()?.time?:0,d?.getTimestamp("seenAt")?.toDate()?.time?:0,d?.getTimestamp("typingAt")?.toDate()?.time?:0,d?.getBoolean("typing")==true))
    }
internal suspend fun CommunityService.publishSignal(thread:String,read:Boolean,typing:Boolean) {
    accountStore().collection("conversations").document(thread).collection("signals").document(uid).set(mapOf(
        "deliveredAt" to FieldValue.serverTimestamp(),"seenAt" to if(read)FieldValue.serverTimestamp() else null,
        "typingAt" to FieldValue.serverTimestamp(),"typing" to typing)).communityAwait()
}
internal suspend fun CommunityService.sendRich(thread:String,id:String,text:String,deck:DeckStore.SavedDeck?=null,card:SharedPlayerCard?=null,reply:PlayerMessage?=null) {
    val me=uid;val raw=deck?.let {DeckInterchange.exportJson(it.deck,it.name)}.orEmpty()
    if(deck!=null)CommunityValidation.deck(raw)
    val cardRaw=card?.json().orEmpty();if(card!=null)SharedPlayerCard.parse(cardRaw)
    val content=if(deck==null && card==null)CommunityValidation.text(text) else text.trim().take(2000)
    val ref=accountStore().collection("conversations").document(thread);val msg=ref.collection("messages").document(id)
    // Retrying the same send ID after a lost acknowledgement never duplicates or edits a message.
    accountStore().runTransaction {tx ->
        if(!tx.get(msg).exists()) {
            tx.set(msg,mapOf("senderId" to me,"kind" to if(card!=null)"card" else if(deck!=null)"deck" else "text","text" to content,
                "deckName" to (deck?.name?.take(120)?:""),"deckJson" to raw,"cardJson" to cardRaw,
                "replyId" to (reply?.id?:""),"replyText" to (reply?.text?.ifBlank {if(reply.kind=="card")"Carte partagée" else reply.deckName}?.take(180)?:""),"createdAt" to FieldValue.serverTimestamp()))
            tx.update(ref,mapOf("updatedAt" to FieldValue.serverTimestamp(),"lastSenderId" to me,
                "lastText" to content.ifBlank {deck?.name?:if(card!=null)"Carte partagée" else "Message"}.take(160),
                "lastKind" to if(card!=null)"card" else if(deck!=null)"deck" else "text"))
        }
    }.communityAwait()
}
