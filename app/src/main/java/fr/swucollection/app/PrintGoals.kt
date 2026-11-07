package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class PrintGoal(val finish: String, val language: String = "") {
    fun json() = JSONObject().put("finish", finish).put("language", language)
    companion object { fun read(j: JSONObject) = PrintGoal(j.getString("finish"), j.getString("language")).also {
        require(it.finish in ScanVariants.options && (it.language.isBlank() || it.language in CardLanguages.supported))
    } }
}
internal fun readPrintGoals(root: JSONObject): Map<String, PrintGoal> {
    require(root.length() <= 100)
    return root.keys().asSequence().associateWith { key -> require(key.matches(Regex("[A-Z0-9]{2,12}"))); PrintGoal.read(root.getJSONObject(key)) }
}
internal fun CollectionDatabase.printGoals() = tool("printGoals")?.let(::readPrintGoals).orEmpty()
internal fun CollectionDatabase.setPrintGoal(set: String, goal: PrintGoal?) {
    val root = tool("printGoals") ?: JSONObject()
    if (goal == null) root.remove(set) else root.put(set, goal.json())
    putTool("printGoals", root)
}
internal fun printGoalRows(catalog: List<CatalogCard>, owned: List<CollectionCard>, playset: Boolean, goal: PrintGoal): List<PlanRow> {
    val quantities = owned.filter { it.variant == goal.finish && (goal.language.isBlank() || it.language == goal.language) }
        .groupBy { MarketPricing.key(it.setCode, it.number) }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    return catalog.filter { val choices = ScanVariants.detect(it).choices; choices.size < ScanVariants.options.size && goal.finish in choices }.distinctBy { MarketPricing.key(it.setCode, it.number) }.map { card ->
        PlanRow(card, quantities[MarketPricing.key(card.setCode, card.number)] ?: 0, CollectionPlan.target(card, playset), goal.finish, goal.language)
    }
}
@Composable
internal fun PrintGoalControls(goal: PrintGoal?, change: (PrintGoal?) -> Unit) {
    Row { Switch(goal != null, { change(if (it) PrintGoal("Normale", "FR") else null) }); Text("Choisir une langue et une finition précises", Modifier.padding(8.dp)) }
    goal?.let { selected ->
        Text("Les autres versions et langues restent dans la collection et ne comptent pas dans cet objectif.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (listOf("") + CardLanguages.supported).forEach { language ->
            FilterChip(selected.language == language, { change(selected.copy(language = language)) }, label = { Text(if (language.isBlank()) "Toutes langues" else language) })
        } }
        var menu by remember { mutableStateOf(false) }
        Box { OutlinedButton(onClick = { menu = true }) { Text("${selected.finish} ▾") }; DropdownMenu(menu, { menu = false }) {
            ScanVariants.options.forEach { finish -> DropdownMenuItem(text = { Text(finish) }, onClick = { change(selected.copy(finish = finish)); menu = false }) }
        } }
    }
}
