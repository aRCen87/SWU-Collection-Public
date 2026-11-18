package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Aspect identity uses a symbol and a label as well as colour. */
@Composable
internal fun AspectBadge(aspect: String, showColorName: Boolean = false) {
    val color = when (aspect) {
        "Vigilance" -> Color(0xFF2878BF)
        "Command" -> Color(0xFF27834D)
        "Aggression" -> Color(0xFFBF3939)
        "Cunning" -> Color(0xFFF1CD57)
        "Heroism" -> Color(0xFFF0F2F5)
        else -> Color(0xFF253143)
    }
    val symbol = when (aspect) {
        "Vigilance" -> Icons.Default.Shield
        "Command" -> Icons.Default.MilitaryTech
        "Aggression" -> Icons.Default.LocalFireDepartment
        "Cunning" -> Icons.Default.AutoAwesome
        "Heroism" -> Icons.Default.Star
        else -> Icons.Default.Bolt
    }
    val colorName = when (aspect) {
        "Vigilance" -> "Bleu"; "Command" -> "Vert"; "Aggression" -> "Rouge"
        "Cunning" -> "Jaune"; "Heroism" -> "Blanc"; else -> "Noir"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = color, shape = CircleShape, border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))) {
            Icon(symbol, null, modifier = Modifier.padding(4.dp).size(16.dp),
                tint = if (aspect in listOf("Cunning", "Heroism")) Color(0xFF08121F) else Color.White)
        }
        Text((if (showColorName) "$colorName · " else "") + (aspectNames[aspect] ?: aspect), style = MaterialTheme.typography.labelMedium)
    }
}
