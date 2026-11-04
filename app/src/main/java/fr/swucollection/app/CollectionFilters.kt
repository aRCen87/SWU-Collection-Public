package fr.swucollection.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberCollectionFilter(key: String, fallback: String): MutableState<String> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("collection_filters", 0) }
    val state = remember(key) { mutableStateOf(prefs.getString(key, fallback) ?: fallback) }
    LaunchedEffect(state.value) { prefs.edit().putString(key, state.value).apply() }
    return state
}
