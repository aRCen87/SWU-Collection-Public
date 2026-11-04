package fr.swucollection.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppDestination(
    val section: String = "Accueil",
    val setCode: String? = null,
    val catalogSearch: String = ""
)

/** In-memory screen history, independent from collection data. */
class AppNavigation {
    var current by mutableStateOf(AppDestination())
        private set
    private val history = mutableListOf<AppDestination>()
    val canGoBack: Boolean get() = current.section != "Accueil"

    fun navigateTo(section: String, setCode: String? = null, catalogSearch: String? = null) {
        if (section == "Accueil") {
            history.clear()
            current = AppDestination()
            return
        }
        val targetSet = if (section in listOf("Extensions", "Mes listes", "Compléter mon set"))
            setCode?.takeIf { it.isNotBlank() } else null
        val query = catalogSearch ?: if (current.section == section && current.setCode == targetSet)
            current.catalogSearch else ""
        val next = AppDestination(section, targetSet, query)
        if (next == current) return
        history.add(current)
        current = next
    }

    fun updateCatalogSearch(query: String) {
        current = current.copy(catalogSearch = query)
    }

    /** False only at home, where Android's normal exit behaviour is retained. */
    fun back(): Boolean {
        if (!canGoBack) return false
        current = if (history.isNotEmpty()) history.removeAt(history.lastIndex) else AppDestination()
        return true
    }

    /** The explicit Extensions link goes up without creating a detail/list loop. */
    fun upToExtensions() {
        if (current.section == "Extensions" && current.setCode == null) return
        if (history.lastOrNull()?.let { it.section == "Extensions" && it.setCode == null } == true) {
            back()
        } else {
            current = AppDestination("Extensions")
        }
    }

    fun upToLists() {
        if (current.section == "Mes listes" && current.setCode == null) return
        if (history.lastOrNull()?.let { it.section == "Mes listes" && it.setCode == null } == true) {
            back()
        } else {
            current = AppDestination("Mes listes")
        }
    }
}


