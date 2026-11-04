package fr.swucollection.app

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeNavigationCard(symbol: String, title: String, subtitle: String,
    reflection: State<BorderTilt?>, badge: BadgeDefinition? = null, catalog: List<CatalogCard> = emptyList(), onClick: () -> Unit) {
    val drawable = when (title) {
        "SCANNER" -> R.drawable.home_scanner_art
        "EXTENSIONS" -> R.drawable.home_extensions_art
        "MA COLLECTION" -> R.drawable.home_collection_art
        "MES DECKS" -> R.drawable.home_decks_art
        "ÉCHANGES" -> R.drawable.home_trades_art
        else -> R.drawable.home_stats_art
    }
    val icon = when (title) {
        "SCANNER" -> Icons.Default.PhotoCamera
        "EXTENSIONS" -> Icons.Default.Style
        "MA COLLECTION" -> Icons.Default.AutoStories
        "MES DECKS" -> Icons.Default.Layers
        "HAUTS FAITS" -> Icons.Default.WorkspacePremium
        "ÉCHANGES" -> Icons.Default.SwapHoriz
        else -> Icons.Default.BarChart
    }
    val resources = LocalContext.current.resources
    val artwork = remember(resources, drawable) { BitmapFactory.decodeResource(resources, drawable) }
    val gold = Color(0xFFE6C17A)
    val navy = Color(0xFF030D19)
    Surface(onClick = onClick,
        modifier = Modifier.fillMaxWidth().goldBorderReflection(reflection),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, gold.copy(alpha = 0.8f)), color = navy) {
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
            if(title == "HAUTS FAITS") {
                Box(Modifier.align(Alignment.CenterEnd).width(124.dp).height(76.dp)) {
                    if(badge != null && catalog.isNotEmpty()) BadgePin(badge,catalog,
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end=10.dp))
                    else HomeBadgeEmblem(Modifier.matchParentSize())
                }
            } else Canvas(Modifier.matchParentSize()) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                drawContext.canvas.nativeCanvas.drawBitmap(artwork, null,
                    RectF(size.width * 0.53f, 0f, size.width, size.height), paint)
            }
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to navy, 0.48f to navy, 0.68f to navy.copy(alpha = 0.7f), 1f to Color.Transparent)))
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, tint = gold, modifier = Modifier.size(28.dp))
                Text(title, color = Color(0xFFFFE3A0), fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = subtitle.ifBlank { symbol },
                    tint = gold, modifier = Modifier.size(24.dp))
            }
        }
    }
}

