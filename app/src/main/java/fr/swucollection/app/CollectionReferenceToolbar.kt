package fr.swucollection.app

import androidx.compose.foundation.background
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.remember

internal fun collectionReferenceDesign(context: android.content.Context): Boolean =
    context.packageName == AppChannel.TEST_PACKAGE ||
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            context.getSharedPreferences("collection_filters", 0).getBoolean("reference_design_preview", false))

/** Render only the planet strip from the supplied reference; all controls remain real UI. */
@Composable
internal fun CollectionReferenceHeader() {
    val resources = LocalContext.current.resources
    val image = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.collection_reference).asImageBitmap() }
    val painter = remember(image) { BitmapPainter(image, IntOffset(202, 100), IntSize(522, 80)) }
    Box(Modifier.fillMaxWidth().height(64.dp).clipToBounds()) {
        Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // The original mockup's printed title is covered before drawing the live title.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Color(0xFF0B1429), .50f to Color(0xFF0B1429), .64f to Color.Transparent)))
        Text("Ma collection", Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp),
            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CollectionReferenceToolbar(search: String, onSearch: (String) -> Unit, count: Int, onFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).height(44.dp).background(Color(0xFF142238), RoundedCornerShape(9.dp))
            .border(1.dp, Color(0xFFA4B3CB), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
            BasicTextField(search, onSearch, singleLine = true,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Rechercher une carte" },
                cursorBrush = SolidColor(PremiumGold), textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                decorationBox = { input -> Box { if (search.isEmpty()) Text("Rechercher une carte…", color = Color.White, fontSize = 14.sp); input() } })
            if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Effacer la recherche", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        OutlinedButton(onClick = onFilters, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(9.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PremiumGold), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Icon(Icons.Outlined.FilterAlt, null, tint = PremiumGold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (count == 0) "Filtrer" else "Filtrer ($count)", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
