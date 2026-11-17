package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val bottomShortcutIcons: Map<String, ImageVector> = linkedMapOf(
    "Accueil" to Icons.Default.Home,
    "Collection" to Icons.Default.CollectionsBookmark,
    "Ajouter" to Icons.Default.Edit,
    "Mes decks" to Icons.Default.Style,
    "Mes proxies" to Icons.Default.Print,
    "Mes listes" to Icons.Default.FormatListBulleted,
    "Scanner" to Icons.Default.DocumentScanner,
    "Extensions" to Icons.Default.AutoStories,
    "Tournois & Méta" to Icons.Default.EmojiEvents,
    "Échanges" to Icons.Default.SwapHoriz,
    "Messagerie" to Icons.Default.Chat,
    "Communauté" to Icons.Default.People,
    "Hauts faits" to Icons.Default.WorkspacePremium,
    "Statistiques" to Icons.Default.BarChart,
    "Compteur" to Icons.Default.Favorite,
    "Recherche" to Icons.Default.Search,
    "Historique" to Icons.Default.History,
    "Prêts" to Icons.Default.Handshake,
    "Hors connexion" to Icons.Default.Download,
    "Paramètres" to Icons.Default.Settings,
    "Apparence" to Icons.Default.Palette,
    "Sauvegardes" to Icons.Default.CloudUpload,
    "Mises à jour" to Icons.Default.SystemUpdate,
    "À propos" to Icons.Default.Info
)

internal fun validBottomShortcuts(items: List<String>) = items.size in 1..5 &&
    items.distinct().size == items.size && items.all { it in bottomShortcutIcons }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CollectionBottomBar(section: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("bottom_shortcuts", android.content.Context.MODE_PRIVATE) }
    var shortcuts by remember { mutableStateOf(prefs.getString("order", null)?.split('|')?.takeIf(::validBottomShortcuts) ?: primaryDestinations) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(shortcuts) }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(color = Color(0xFF0B1B2D), border = BorderStroke(1.dp, Color(0xFF9C7941)),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            shortcuts.forEach { destination ->
                val selected = section == destination || (destination == "Collection" && (section == "Compléter mon set" || section in listOf("Extensions", "Ajouter") && section !in shortcuts))
                val tint = if (selected) PremiumGold else Color(0xFFB4C0D4)
                Column(Modifier.weight(1f).testTag("bottom_shortcut_$destination").semantics { this.selected = selected }.combinedClickable(role = Role.Tab,
                    onClick = { onNavigate(destination) }, onLongClickLabel = "Personnaliser la barre",
                    onLongClick = { draft = shortcuts; error = null; editing = true }).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(bottomShortcutIcons.getValue(destination), destination, tint = tint)
                    Text(if (destination == "Hauts faits") "Badges" else destination,
                        color = tint, fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Mes raccourcis") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Appui long sur la barre pour la personnaliser. Choisis jusqu’à 5 raccourcis, puis change leur ordre.")
                Text("${draft.size}/5 raccourcis", color = PremiumGold)
                draft.forEachIndexed { index, destination ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(bottomShortcutIcons.getValue(destination), null, tint = PremiumGold, modifier = Modifier.size(22.dp))
                        Text(destination, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp)
                        IconButton(enabled = index > 0, onClick = { draft = draft.toMutableList().apply { add(index - 1, removeAt(index)) } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowUpward, "Monter $destination") }
                        IconButton(enabled = index < draft.lastIndex, onClick = { draft = draft.toMutableList().apply { add(index + 1, removeAt(index)) } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowDownward, "Descendre $destination") }
                        IconButton(enabled = draft.size > 1, onClick = { draft = draft - destination }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Retirer $destination") }
                    }
                }
                HorizontalDivider()
                Text(if (draft.size == 5) "Retire un raccourci pour en ajouter un autre." else "Ajouter un raccourci")
                bottomShortcutIcons.filterKeys { it !in draft }.forEach { (destination, icon) ->
                    OutlinedButton(enabled = draft.size < 5, onClick = { draft = draft + destination }, modifier = Modifier.fillMaxWidth()) {
                        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(destination)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { draft = primaryDestinations }) { Text("Rétablir les raccourcis par défaut") }
            }
        }, confirmButton = { TextButton(onClick = {
            if (validBottomShortcuts(draft) && prefs.edit().putString("order", draft.joinToString("|")).commit()) {
                shortcuts = draft; editing = false
            } else error = "Enregistrement impossible. Réessaie."
        }) { Text("Enregistrer") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Annuler") } })
}
