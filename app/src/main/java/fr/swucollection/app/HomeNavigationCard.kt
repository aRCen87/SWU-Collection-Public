package fr.swucollection.app

import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
        "HAUTS FAITS", "BADGES" -> R.drawable.home_badges_art
        "TOURNOIS & MÉTA" -> R.drawable.home_meta_art
        "ÉCHANGES" -> R.drawable.home_trades_art
        else -> R.drawable.home_stats_art
    }
    val icon = when (title) {
        "SCANNER" -> Icons.Default.PhotoCamera
        "EXTENSIONS" -> Icons.Default.Style
        "MA COLLECTION" -> Icons.Default.AutoStories
        "MES DECKS" -> Icons.Default.Layers
        "HAUTS FAITS", "BADGES" -> Icons.Default.WorkspacePremium
        "TOURNOIS & MÉTA" -> Icons.Default.EmojiEvents
        "ÉCHANGES" -> Icons.Default.SwapHoriz
        else -> Icons.Default.BarChart
    }
    val resources = LocalContext.current.resources
    val privateLayout = AppChannel.supportsExperience(LocalContext.current.packageName)
    val artwork = remember(resources, drawable) { BitmapFactory.decodeResource(resources, drawable) }
    val gold = Color(0xFFE6C17A)
    val navy = Color(0xFF030D19)
    val interactions = remember { MutableInteractionSource() }
    Surface(onClick = onClick,
        interactionSource = interactions,
        modifier = Modifier.fillMaxWidth().homeButtonMotion(interactions).goldBorderReflection(reflection),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, gold.copy(alpha = 0.8f)), color = navy) {
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                val canvas = drawContext.canvas.nativeCanvas
                val area = RectF(size.width * 0.53f, 0f, size.width, size.height)
                if (privateLayout) {
                    // Crop to the available area with one scale factor, never
                    // stretch a card/display when the phone opens or rotates.
                    val scale = maxOf(area.width() / artwork.width, area.height() / artwork.height)
                    val width = artwork.width * scale
                    val height = artwork.height * scale
                    val saved = canvas.save()
                    canvas.clipRect(area)
                    canvas.drawBitmap(artwork, null, RectF(area.centerX() - width / 2f,
                        area.centerY() - height / 2f, area.centerX() + width / 2f,
                        area.centerY() + height / 2f), paint)
                    canvas.restoreToCount(saved)
                } else canvas.drawBitmap(artwork, null, area, paint)
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

