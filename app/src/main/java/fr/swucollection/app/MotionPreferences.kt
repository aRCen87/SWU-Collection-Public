package fr.swucollection.app

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("swu_appearance", 0) }
    var reduced by remember(prefs) { mutableStateOf(prefs.getBoolean("reduced_motion", false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "reduced_motion") reduced = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return reduced
}

@Composable
internal fun ReducedMotionSettings() {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Réduire les animations", style = MaterialTheme.typography.titleSmall)
                Text("Conserver le thème bleu et or avec moins de mouvements et d’effets lumineux.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(reduced, { context.getSharedPreferences("swu_appearance", 0).edit().putBoolean("reduced_motion", it).apply() })
        }
    }
}
