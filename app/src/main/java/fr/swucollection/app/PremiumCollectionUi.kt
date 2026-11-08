package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PremiumGold = Color(0xFFE6C578)
internal val PremiumNavy = Color(0xFF08121F)
internal val PremiumPanel = Brush.verticalGradient(listOf(Color(0xFF203145), Color(0xFF0B1726)))
internal val PremiumMetal = Brush.verticalGradient(listOf(Color(0xFFFFEDB6), Color(0xFFD7AF57), Color(0xFFFFDE93)))

/** Artwork contains no printed controls: every action remains a real accessible button. */
@Composable
internal fun PremiumHomeBanner(active: Boolean, modifier: Modifier = Modifier) {
    val orbitTime = rememberHeaderAmbientTime(active)
    BoxWithConstraints(modifier.fillMaxWidth().testTag("swu_premium_banner").clip(RoundedCornerShape(22.dp))) {
        val compact = maxWidth < 340.dp
        val bannerHeight = if (compact) 220.dp else 246.dp
        Image(painterResource(R.drawable.home_premium_space), null,
            Modifier.fillMaxWidth().height(bannerHeight).graphicsLayer {
                // A quiet orbital pan, one complete drift in three minutes.
                // Overscan keeps every edge covered; controls never inherit the transform.
                val phase = orbitTime.value * (2f * Math.PI.toFloat() / 180f)
                scaleX = 1.07f; scaleY = 1.07f
                translationX = sin(phase) * size.width * .022f
                translationY = sin(phase * .5f) * size.height * .009f
            }, contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0xB008121F), Color.Transparent, Color(0xBB08121F)))))
        Column(Modifier.align(Alignment.TopCenter).padding(top = 20.dp).widthIn(max = if (compact) 154.dp else 196.dp)
            .semantics(mergeDescendants = true) { contentDescription = "SWU Collection" },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SWU", style = TextStyle(brush = PremiumMetal, fontSize = if (compact) 38.sp else 48.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp), maxLines = 1)
            Text("COLLECTION", color = PremiumGold, fontSize = if (compact) 12.sp else 15.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(100.dp).height(1.dp).background(PremiumMetal))
        }
    }
}

internal data class PremiumShortcut(val key: String, val title: String, val subtitle: String, val icon: ImageVector)
private val premiumShortcuts = listOf(
    PremiumShortcut("MA COLLECTION", "Ma collection", "Parcourir et gérer mes cartes", Icons.Default.CollectionsBookmark),
    PremiumShortcut("SCANNER", "Scanner mes cartes", "Ajouter rapidement à ma collection", Icons.Default.DocumentScanner),
    PremiumShortcut("MES DECKS", "Mes listes", "Decks et cartes favorites", Icons.Default.FormatListBulleted),
    PremiumShortcut("TOURNOIS & MÉTA", "Méta & tournois", "Résultats et decks du moment", Icons.Default.EmojiEvents),
    PremiumShortcut("BADGES", "Badges", "Mes défis de collection", Icons.Default.WorkspacePremium),
    PremiumShortcut("STATISTIQUES", "Statistiques", "Suivre ma progression", Icons.Default.BarChart),
    PremiumShortcut("EXTENSIONS", "Extensions", "Explorer les sets", Icons.Default.Style),
    PremiumShortcut("ÉCHANGES", "Échanges", "Trouver les bonnes cartes", Icons.Default.SwapHoriz)
)

@Composable
internal fun PremiumHomeShortcuts(order: List<String>, reflection: State<BorderTilt?>, afterPrimary: @Composable () -> Unit = {}, onOpen: (String) -> Unit) {
    // Keep explicitly saved arrangements; promote the two core actions for the original default layout.
    val arranged = if (order == HomeLayoutStore.defaults) premiumShortcuts else order.mapNotNull { key -> premiumShortcuts.find { it.key == key } }
    arranged.take(2).forEach { shortcut -> PremiumHomeTile(shortcut, true, Modifier.fillMaxWidth(), reflection) { onOpen(shortcut.key) } }
    afterPrimary()
    arranged.drop(2).chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { shortcut -> PremiumHomeTile(shortcut, false, Modifier.weight(1f), reflection) { onOpen(shortcut.key) } }
        }
    }
}

@Composable
private fun PremiumHomeTile(item: PremiumShortcut, prominent: Boolean, modifier: Modifier,
    reflection: State<BorderTilt?>, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.goldBorderReflection(reflection), color = PremiumNavy,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, PremiumGold.copy(alpha = if (prominent) .85f else .35f))) {
        if (prominent) Row(Modifier.background(PremiumPanel).heightIn(min = 98.dp).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(item.icon, null, tint = PremiumGold, modifier = Modifier.size(38.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFF5F3ED))
                Text(item.subtitle, fontSize = 12.sp, color = Color(0xFFCAD3DF))
            }
            Icon(Icons.Default.ChevronRight, null, tint = PremiumGold, modifier = Modifier.size(20.dp))
        } else Column(Modifier.background(PremiumPanel).heightIn(min = 120.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(item.icon, null, tint = PremiumGold, modifier = Modifier.size(27.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFF5F3ED))
            Text(item.subtitle, fontSize = 11.sp, color = Color(0xFFCAD3DF))
        }
    }
}

/** One tile per card family, always retaining the real language and print of its thumbnail. */
@Composable
internal fun PremiumOwnedTile(rows: List<CollectionCard>, catalogByKey: Map<String, CatalogCard>,
    quotes: Map<String, MarketQuote>, status: PremierStatus?, onOpen: (CollectionCard, CatalogCard) -> Unit) {
    val first = rows.first()
    val card = catalogByKey[MarketPricing.key(first.setCode, first.number)]
    val priceLabel = remember(rows, quotes) {
        val prices = rows.mapNotNull { row -> quotes[collectionPriceKey(row.setCode, row.number, row.variant)]?.amount
            ?.takeIf { it.isFinite() && it > 0 } }
        val money = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.FRANCE)
        if (prices.isEmpty()) "Cote indisponible" else {
            val low = prices.minOrNull()!!; val high = prices.maxOrNull()!!
            "Cote unitaire : " + if (low == high) money.format(low) else "${money.format(low)} – ${money.format(high)}"
        }
    }
    Surface(onClick = { if (card != null) onOpen(first, card) }, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp), color = PremiumNavy,
        border = BorderStroke(1.dp, PremiumGold.copy(alpha = .45f))) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.716f).padding(4.dp)) {
                CardArtwork(card, Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)), language = first.language, variant = first.variant)
                Surface(Modifier.align(Alignment.TopEnd).padding(5.dp), color = Color(0xEE08121F), shape = RoundedCornerShape(6.dp)) {
                    Text(if (rows.map { it.language }.distinct().size > 1) "Multi" else first.language.uppercase(),
                        Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White)
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp), color = Color(0xEE08121F),
                    border = BorderStroke(1.dp, PremiumGold), shape = RoundedCornerShape(8.dp)) {
                    Text("× ${rows.sumOf { it.quantity }}", Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardLegalityBadge(status)
                Text(priceLabel, color = PremiumGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(first.name, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(if (rows.size > 1) "${rows.size} versions · ${first.setCode}" else "${first.setCode} · ${first.variant}",
                Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
