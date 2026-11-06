package fr.swucollection.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class PlayerProfile(
    val id: String, val name: String, val bio: String = "", val avatar: String = "preset:star",
    val discoverable: Boolean = false, val shareBadges: Boolean = false, val badgeIds: List<String> = emptyList()
)
internal data class PlayerConversation(val id: String, val members: List<String>, val updatedAt: Long)
internal data class PlayerMessage(val id: String, val senderId: String, val kind: String, val text: String,
    val deckName: String, val deckJson: String, val createdAt: Long)

internal object CommunityValidation {
    val avatars = linkedMapOf("star" to "✦", "moon" to "☾", "planet" to "🪐", "robot" to "🤖", "ship" to "🚀", "comet" to "☄", "sun" to "☀", "crystal" to "💎")
    fun name(raw: String): String = raw.trim().also { require(it.length in 2..32 && it.none(Char::isISOControl)) { "Choisis un pseudo de 2 à 32 caractères." } }
    fun text(raw: String): String = raw.trim().also { require(it.length in 1..2000) { "Le message doit contenir de 1 à 2 000 caractères." } }
    fun avatar(raw: String): String = raw.also {
        require(it.removePrefix("preset:") in avatars.keys && it.startsWith("preset:") ||
            it.startsWith("jpeg:") && it.length <= 18000 && Regex("jpeg:[A-Za-z0-9+/=]+").matches(it)) { "Avatar invalide ou trop volumineux." }
    }
    fun pair(a: String, b: String): String {
        require(a.isNotBlank() && b.isNotBlank() && a != b && '/' !in a && '/' !in b)
        return listOf(a, b).sorted().joinToString("__")
    }
    fun deck(raw: String): PortableDeck {
        require(raw.length <= 40000) { "Le deck est trop volumineux pour la messagerie." }
        return DeckInterchange.importJson(raw)
    }
}

internal suspend fun <T> Task<T>.communityAwait(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/** The named test backend is never initialized for the official application. */
internal class CommunityService private constructor(val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    val uid: String get() = auth.currentUser?.uid ?: error("Connecte-toi d’abord.")
    suspend fun register(email: String, password: String) {
        require(password.length in 12..128) { "Utilise un mot de passe de 12 caractères minimum." }
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).communityAwait().user ?: error("Création impossible.")
        user.sendEmailVerification().communityAwait()
    }
    suspend fun login(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).communityAwait() }
    suspend fun resetPassword(email: String) { require(email.trim().contains('@')); auth.sendPasswordResetEmail(email.trim()).communityAwait() }
    suspend fun refreshIdentity() { auth.currentUser?.reload()?.communityAwait(); auth.currentUser?.getIdToken(true)?.communityAwait() }
    suspend fun resendVerification() { auth.currentUser?.sendEmailVerification()?.communityAwait() }
    fun signOut() { auth.signOut() }
    suspend fun allowed(): Boolean = db.collection("testMembers").document(uid).get(Source.SERVER).communityAwait().getBoolean("enabled") == true

    fun ownProfile(onValue: (PlayerProfile?) -> Unit, onError: (Exception) -> Unit): ListenerRegistration =
        db.collection("profiles").document(uid).addSnapshotListener { row, error ->
            if (error != null) onError(error) else onValue(row?.takeIf { it.exists() }?.profile())
        }
    fun profiles(onValue: (List<PlayerProfile>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration =
        db.collection("profiles").whereEqualTo("discoverable", true).limit(50).addSnapshotListener { rows, error ->
            if (error != null) onError(error) else onValue(rows?.documents.orEmpty().map { it.profile() }.sortedBy { it.name.lowercase() })
        }
    suspend fun profile(id: String): PlayerProfile? = db.collection("profiles").document(id).get().communityAwait().takeIf { it.exists() }?.profile()
    suspend fun saveProfile(profile: PlayerProfile, earnedIds: List<String>) {
        require(profile.id == uid)
        require(profile.bio.length <= 200)
        db.collection("profiles").document(uid).set(mapOf(
            "name" to CommunityValidation.name(profile.name), "bio" to profile.bio.trim(),
            "avatar" to CommunityValidation.avatar(profile.avatar), "discoverable" to profile.discoverable,
            "shareBadges" to profile.shareBadges, "badgeIds" to if (profile.shareBadges) earnedIds.distinct().take(500) else emptyList<String>(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).communityAwait()
    }
    fun conversations(onValue: (List<PlayerConversation>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration =
        db.collection("conversations").whereArrayContains("members", uid).limit(100).addSnapshotListener { rows, error ->
            if (error != null) onError(error) else onValue(rows?.documents.orEmpty().map { row ->
                PlayerConversation(row.id, (row.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty(), row.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
            }.sortedByDescending { it.updatedAt })
        }
    fun messages(conversation: String, onValue: (List<PlayerMessage>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration =
        db.collection("conversations").document(conversation).collection("messages").orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { rows, error ->
                if (error != null) onError(error) else onValue(rows?.documents.orEmpty().filterNot { it.metadata.hasPendingWrites() }.map { row ->
                    PlayerMessage(row.id, row.getString("senderId").orEmpty(), row.getString("kind").orEmpty(),
                        row.getString("text").orEmpty(), row.getString("deckName").orEmpty(), row.getString("deckJson").orEmpty(),
                        row.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                })
            }
    suspend fun openConversation(peer: String): String {
        val me = uid
        val id = CommunityValidation.pair(me, peer)
        val ref = db.collection("conversations").document(id)
        ref.set(mapOf("members" to listOf(me, peer).sorted(), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).communityAwait()
        return id
    }
    suspend fun send(conversation: String, messageId: String, text: String, deck: DeckStore.SavedDeck? = null) {
        val raw = deck?.let { DeckInterchange.exportJson(it.deck, it.name) }.orEmpty()
        if (deck != null) CommunityValidation.deck(raw)
        val content = if (deck == null) CommunityValidation.text(text) else text.trim().take(2000)
        val ref = db.collection("conversations").document(conversation)
        val batch = db.batch()
        batch.set(ref.collection("messages").document(messageId), mapOf(
            "senderId" to uid, "kind" to if (deck == null) "text" else "deck", "text" to content,
            "deckName" to (deck?.name?.take(120) ?: ""), "deckJson" to raw, "createdAt" to FieldValue.serverTimestamp()))
        batch.update(ref, "updatedAt", FieldValue.serverTimestamp())
        batch.commit().communityAwait()
    }
    suspend fun block(peer: String, blocked: Boolean) {
        require(peer != uid)
        val ref = db.collection("blocks").document(uid).collection("users").document(peer)
        if (blocked) ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).communityAwait() else ref.delete().communityAwait()
    }
    suspend fun isBlocked(peer: String): Boolean = db.collection("blocks").document(uid).collection("users").document(peer).get().communityAwait().exists()

    private fun DocumentSnapshot.profile() = PlayerProfile(id, getString("name").orEmpty(), getString("bio").orEmpty(),
        getString("avatar") ?: "preset:star", getBoolean("discoverable") == true, getBoolean("shareBadges") == true,
        (get("badgeIds") as? List<*>)?.filterIsInstance<String>().orEmpty())

    companion object {
        fun create(context: Context): CommunityService? {
            if (context.packageName != "fr.swucollection.app.test") return null
            val project = context.getString(R.string.swu_test_project_id)
            val apiKey = context.getString(R.string.swu_test_api_key)
            val appId = context.getString(R.string.swu_test_app_id)
            if (!project.endsWith("-test") || apiKey.isBlank() || appId.isBlank()) return null
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == "swu-community-test" }
                ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId(project).setApiKey(apiKey).setApplicationId(appId).build(), "swu-community-test")
            val store = FirebaseFirestore.getInstance(app)
            // No shared persistent social cache between people using the same phone.
            if (instances[project] == null) store.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
            return instances.getOrPut(project) { CommunityService(FirebaseAuth.getInstance(app), store) }
        }
        private val instances = mutableMapOf<String, CommunityService>()
    }
}
