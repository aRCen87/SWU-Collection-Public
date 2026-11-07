package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object PrivatePayload {
    const val MAX_BYTES = 16 * 1024 * 1024
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun encode(raw: String): String {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Sauvegarde trop volumineuse (16 Mo maximum)." }
        val stream = ByteArrayOutputStream()
        GZIPOutputStream(stream).use { it.write(bytes) }
        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }
    fun decode(encoded: String, limit: Int = MAX_BYTES): String {
        require(encoded.length <= 6_400_000)
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { stream ->
            val buffer = ByteArray(8192)
            while (true) { val n = stream.read(buffer); if (n < 0) break
                require(output.size().toLong() + n <= limit) { "Données décompressées trop volumineuses." }; output.write(buffer, 0, n) }
        }
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
    }
}
internal data class AccountSnapshot(val id: String, val created: Long, val parts: Int, val bytes: Int, val hash: String)
internal fun accountBackupJson(context: Context, database: CollectionDatabase): String = JSONObject(database.exportJson())
    .put("homeLayout", JSONObject(HomeLayoutStore.json(HomeLayoutStore(context).read())))
    .put("deckPins", JSONArray(DeckPins(context).all())).toString()
internal fun validateAccountBackup(raw: String): BackupSummary {
    val summary = validateBackup(raw); val root = JSONObject(raw)
    root.optJSONObject("homeLayout")?.let { HomeLayoutStore.parse(it.toString()) }
    root.optJSONArray("deckPins")?.let { pins ->
        require(pins.length() <= 3)
        (0 until pins.length()).forEach { require(pins.getString(it).length in 1..150) }
    }
    return summary
}
internal suspend fun CommunityService.backupHead(): String? = accountStore().collection("accountBackups").document(uid)
    .get(Source.SERVER).communityAwait().getString("head")
internal suspend fun CommunityService.accountSnapshots(): List<AccountSnapshot> = accountStore().collection("accountBackups").document(uid)
    .collection("snapshots").orderBy("createdAt", Query.Direction.DESCENDING).limit(3).get(Source.SERVER).communityAwait().documents.map {
        AccountSnapshot(it.id, it.getTimestamp("createdAt")?.toDate()?.time ?: 0, it.getLong("parts")!!.toInt(), it.getLong("bytes")!!.toInt(), it.getString("hash")!!)
    }
internal suspend fun CommunityService.saveAccountBackup(raw: String, expectedHead: String?, expectedOwner: String): String {
    check(uid == expectedOwner) { "Le compte a changé." }
    validateAccountBackup(raw)
    val chunks = PrivatePayload.encode(raw).chunked(200000)
    require(chunks.size in 1..32) { "Sauvegarde compressée trop volumineuse." }
    val owner = uid; val id = UUID.randomUUID().toString(); val db = accountStore()
    val root = db.collection("accountBackups").document(owner); val snapshot = root.collection("snapshots").document(id)
    val bytes = raw.toByteArray(Charsets.UTF_8); val hash = PrivatePayload.hash(bytes)
    db.runTransaction { tx ->
        check(tx.get(root).getString("head") == expectedHead) { "Une sauvegarde plus récente existe sur le compte. Actualise avant d’enregistrer." }
        tx.set(snapshot, mapOf("parts" to chunks.size, "bytes" to bytes.size, "hash" to hash, "createdAt" to FieldValue.serverTimestamp()))
        chunks.forEachIndexed { i, part -> tx.set(snapshot.collection("chunks").document(i.toString()), mapOf("index" to i, "data" to part)) }
        tx.set(root, mapOf("head" to id, "updatedAt" to FieldValue.serverTimestamp()))
    }.communityAwait()
    check(auth.currentUser?.uid == owner) { "Le compte a changé. Reconnecte-toi avant de continuer." }
    val verified = readAccountBackup(AccountSnapshot(id, 0, chunks.size, bytes.size, hash))
    check(verified == raw) { "La sauvegarde distante n’a pas pu être vérifiée." }
    return id
}
internal suspend fun CommunityService.readAccountBackup(snapshot: AccountSnapshot): String {
    require(snapshot.parts in 1..32 && snapshot.bytes in 1..PrivatePayload.MAX_BYTES && Regex("[a-f0-9]{64}").matches(snapshot.hash))
    val owner = uid; val ref = accountStore().collection("accountBackups").document(owner).collection("snapshots").document(snapshot.id)
    val encoded = StringBuilder()
    repeat(snapshot.parts) { i ->
        val part = ref.collection("chunks").document(i.toString()).get(Source.SERVER).communityAwait()
        require(part.getLong("index") == i.toLong()) { "Sauvegarde incomplète." }
        val data = part.getString("data") ?: error("Sauvegarde incomplète.")
        require(data.length in 1..200000); encoded.append(data)
    }
    check(auth.currentUser?.uid == owner) { "Le compte a changé." }
    val raw = PrivatePayload.decode(encoded.toString()); val bytes = raw.toByteArray(Charsets.UTF_8)
    require(bytes.size == snapshot.bytes && PrivatePayload.hash(bytes) == snapshot.hash) { "Sauvegarde incomplète ou altérée. Aucune donnée n’a été restaurée." }
    validateAccountBackup(raw)
    return raw
}

@Composable
internal fun AccountBackupPanel(database: CollectionDatabase, onAccount: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val service = remember(context) { CommunityService.create(context) } ?: return
    var owner by remember { mutableStateOf(service.auth.currentUser?.uid) }
    DisposableEffect(service) {
        val listener = FirebaseAuth.AuthStateListener { owner = it.currentUser?.uid }
        service.auth.addAuthStateListener(listener); onDispose { service.auth.removeAuthStateListener(listener) }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sauvegarde privée de mon compte", style = MaterialTheme.typography.titleMedium)
        Text("Collection, langues, variantes, listes, decks, badges et accueil. Les modifications sont sauvegardées automatiquement après connexion. Sur un autre téléphone, choisis la sauvegarde à restaurer. Chaque remplacement demande confirmation.")
        if (owner == null || service.auth.currentUser?.isEmailVerified != true) {
            OutlinedButton(onClick = onAccount) { Text("Me connecter à mon compte") }
        } else key(owner) { AccountBackupSession(service, database, onRestored) }
    } }
}
@Composable
private fun AccountBackupSession(service: CommunityService, database: CollectionDatabase, onRestored: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val owner = service.uid
    var busy by remember { mutableStateOf(true) }; var allowed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }; var head by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<AccountSnapshot>>(emptyList()) }; var pending by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    suspend fun load() { allowed = service.allowed(); check(allowed) { "Ce compte est désactivé." }; head = service.backupHead(); rows = service.accountSnapshots() }
    fun action(block: suspend () -> Unit) { if (!busy) scope.launch {
        busy = true; status = null
        try { check(service.uid == owner); block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { status = error.message?.takeIf { error is IllegalArgumentException || error is IllegalStateException } ?: communityError(error) }
        finally { busy = false }
    } }
    LaunchedEffect(owner) { try { load() } catch (e: Exception) { status = communityError(e) } finally { busy = false } }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    AutomaticBackupControls(owner, database)
    status?.let { Text(it) }
    OutlinedButton(onClick = { action { load() } }, enabled = !busy) { Text("Actualiser les sauvegardes du compte") }
    Button(onClick = { action {
        pendingSave = withContext(Dispatchers.IO) { accountBackupJson(context, database) }
    } }, enabled = !busy && allowed) { Text("Conserver ce téléphone et sauvegarder") }
    pendingSave?.let { raw ->
        val summary = remember(raw) { validateAccountBackup(raw) }
        AlertDialog(onDismissRequest = { pendingSave = null }, title = { Text("Conserver les données de ce téléphone ?") }, text = {
            Text("${summary.references} références, ${summary.copies} exemplaires, ${summary.lists} listes et ${summary.decks} decks. Cette copie deviendra la sauvegarde courante du compte. Les sauvegardes précédentes seront conservées. Aucun cumul de quantités ne sera effectué.")
        }, confirmButton = { TextButton(onClick = { pendingSave = null; action {
            AutomaticAccountBackup.exclusive {
                val id = service.saveAccountBackup(raw, head, owner)
                AutomaticAccountBackup.acknowledge(context, owner, id, raw)
            }
            load(); status = "Sauvegarde du compte enregistrée et vérifiée."
        } }) { Text("Utiliser ce téléphone") } }, dismissButton = { TextButton(onClick = { pendingSave = null }) { Text("Annuler") } })
    }
    if (rows.isEmpty() && !busy && allowed) Text("Aucune sauvegarde sur ce compte pour le moment.")
    rows.forEach { row -> OutlinedButton(onClick = { action { pending = service.readAccountBackup(row) } }, enabled = !busy) {
        Text("Restaurer · " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date(row.created)))
    } }
    pending?.let { raw ->
        val summary = remember(raw) { validateAccountBackup(raw) }
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Restaurer la sauvegarde du compte ?") }, text = {
            Text("${summary.references} références, ${summary.copies} exemplaires, ${summary.lists} listes et ${summary.decks} decks.\n\nCette sauvegarde remplacera les données de ce téléphone, sans additionner les quantités. Les prêts et parties seront aussi restaurés ; une ancienne sauvegarde ne contient pas ces outils. Une copie de récupération locale sera créée avant la restauration.")
        }, confirmButton = { TextButton(onClick = { pending = null; action {
            check(service.auth.currentUser?.uid == owner)
            AutomaticAccountBackup.exclusive { withContext(Dispatchers.IO) {
                validateAccountBackup(raw); UpdateBackups.create(context, database)
                database.importJson(raw, replace = true)
                val root = JSONObject(raw)
                root.optJSONObject("homeLayout")?.let { HomeLayoutStore(context).write(HomeLayoutStore.parse(it.toString())) }
                root.optJSONArray("deckPins")?.let { pins -> DeckPins(context).restore((0 until pins.length()).map { pins.getString(it) }, DeckStore(context).all().map { it.id }.toSet()) }
                AutomaticAccountBackup.acknowledge(context, owner, head, accountBackupJson(context, database), pending = true)
            } }
            onRestored(); status = "Sauvegarde restaurée sur ce téléphone."
        } }, enabled = !busy) { Text("Restaurer sur ce téléphone") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } })
    }
}
