package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class HomeLayout(val order: List<String> = HomeLayoutStore.defaults, val set: String = "")
internal class HomeLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)
    fun read(): HomeLayout = runCatching { parse(prefs.getString("layout", "{}")!!) }.getOrDefault(HomeLayout())
    fun write(layout: HomeLayout) { val raw = json(layout); parse(raw); check(prefs.edit().putString("layout", raw).commit()) { "Personnalisation non enregistrée." } }
    companion object {
        val defaults = listOf("SCANNER", "EXTENSIONS", "MA COLLECTION", "MES DECKS", "TOURNOIS & MÉTA", "ÉCHANGES", "BADGES", "STATISTIQUES")
        fun json(layout: HomeLayout) = JSONObject().put("order", JSONArray(layout.order)).put("set", layout.set).toString()
        fun parse(raw: String): HomeLayout {
            val root = JSONObject(raw)
            val array = root.optJSONArray("order") ?: JSONArray(defaults)
            val order = (0 until array.length()).map { array.getString(it) }
            require(order.size == defaults.size && order.toSet() == defaults.toSet()) { "Ordre de l’accueil invalide." }
            val set = root.optString("set", "")
            require(set.isEmpty() || Regex("[A-Z0-9]{2,12}").matches(set))
            return HomeLayout(order, set)
        }
    }
}

@Composable
internal fun HomeLayoutEditor(initial: HomeLayout, sets: List<CatalogSet>, onDismiss: () -> Unit, onSave: (HomeLayout) -> Unit) {
    var layout by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Personnaliser mon accueil") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ordre des raccourcis")
            layout.order.forEachIndexed { index, title -> Row(Modifier.fillMaxWidth()) {
                Text(title, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
                fun move(target: Int) { val order = layout.order.toMutableList(); order.add(target, order.removeAt(index)); layout = layout.copy(order = order) }
                TextButton(onClick = { move(index - 1) }, enabled = index > 0, contentPadding = PaddingValues(4.dp)) { Text("↑") }
                TextButton(onClick = { move(index + 1) }, enabled = index < layout.order.lastIndex, contentPadding = PaddingValues(4.dp)) { Text("↓") }
            } }
            Text("Extension épinglée")
            FilterChip(layout.set.isEmpty(), { layout = layout.copy(set = "") }, label = { Text("Aucune") })
            sets.forEach { set -> FilterChip(layout.set == set.code, { layout = layout.copy(set = set.code) }, label = { Text(set.name) }) }
            Text("Pour épingler un deck, utilise l’étoile dans Mes decks.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onSave(layout) }) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
