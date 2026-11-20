package fr.swucollection.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class RarityProgress(val rarity:String,val owned:Int,val total:Int)
internal fun rarityProgress(catalog:List<CatalogCard>,quantities:Map<String,Int>)=catalog.groupBy{it.rarity}.map{(rarity,rows)->
    RarityProgress(rarity,rows.count{(quantities[MarketPricing.key(it.setCode,it.number)]?:0)>0},rows.size)
}
@Composable internal fun ExtensionProgressPanel(code:String,catalog:List<CatalogCard>,quantities:Map<String,Int>,compactTitle:String?=null) {
    var expanded by remember(code){mutableStateOf(false)}
    val rows=remember(catalog,code,quantities){rarityProgress(catalog.filter{it.setCode==code},quantities)}
    if(compactTitle==null) TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Masquer la progression par rareté" else "Progression par rareté")}
    else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(compactTitle,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=1,
            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        TextButton(onClick={expanded=!expanded},modifier=Modifier.semantics {
            contentDescription=if(expanded)"Masquer la progression par rareté" else "Afficher la progression par rareté"
        }) { Text(if(expanded)"Raretés ▴" else "Raretés ▾") }
    }
    if(expanded) rows.forEach{r->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(r.rarity.ifBlank{"Non renseignée"},Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress={if(r.total==0)0f else r.owned.toFloat()/r.total},modifier=Modifier.width(80.dp))
            Text("${r.owned}/${r.total}",style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable internal fun MissingSilhouette(card:CatalogCard,modifier:Modifier=Modifier) {
    Surface(modifier,shape=RoundedCornerShape(9.dp),color=ListPalette.Raised){
        Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Icon(Icons.Default.Style,null,tint=ListPalette.Secondary,modifier=Modifier.size(32.dp))
            Text("À trouver",color=ListPalette.Secondary,style=MaterialTheme.typography.labelSmall)
            Text("${card.setCode} · ${card.number}",style=MaterialTheme.typography.labelSmall)
        }
    }
}

/** A short entry animation keeps the card recognisable without delaying its controls. */
@Composable internal fun rememberCardEntryScale(key:Any):Float {
    var entered by remember(key){mutableStateOf(false)}
    LaunchedEffect(key){entered=true}
    val scale by animateFloatAsState(if(entered)1f else .88f,tween(if(LocalReducedMotion.current)0 else 180),label="Ouverture de la carte")
    return scale
}
@Composable internal fun FoilFinishToggle(variant:String?,modifier:Modifier=Modifier):Modifier {
    var enabled by remember(variant){mutableStateOf(false)}
    if(variant?.contains("foil",true)==true) {
        Row(verticalAlignment=Alignment.CenterVertically){Switch(enabled,{enabled=it});Text("Reflet de finition",style=MaterialTheme.typography.labelMedium)}
    }
    return if(enabled && !LocalReducedMotion.current && variant?.contains("foil",true)==true)modifier.drawWithContent{
        drawContent();drawRect(Brush.linearGradient(listOf(Color.Transparent,Color(0x33B5E4FF),Color(0x22FCE8AC),Color.Transparent)))
    } else modifier
}
@Composable internal fun VariantFan(card:CatalogCard,catalog:List<CatalogCard>,owned:List<CollectionCard>,details:CardDetails,variant:String,language:String,alwaysExpanded:Boolean=false,onOpen:(CatalogCard,String,String)->Unit) {
    val prints=remember(card,catalog,owned,details){familyPrints(card,catalog,owned,details)}
    var expanded by remember(details.family(card)){mutableStateOf(false)}
    if(!alwaysExpanded) TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Replier les variantes" else "Déployer les variantes · ${prints.size}")}
    if(!alwaysExpanded && !expanded) Box(Modifier.fillMaxWidth().height(130.dp).clickable{expanded=true},contentAlignment=Alignment.Center){
        prints.take(5).forEachIndexed{i,p->CardArtwork(p.card,Modifier.size(74.dp,104.dp).graphicsLayer{
            translationX=(i-(minOf(prints.size,5)-1)/2f)*22.dp.toPx();rotationZ=(i-(minOf(prints.size,5)-1)/2f)*7f
        },variant=p.variant,language=language,showLanguageLabel=false)}
    } else {
        LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            items(prints){p->Column(Modifier.width(104.dp).clickable{onOpen(p.card,p.variant,language)}){
                CardArtwork(p.card,Modifier.fillMaxWidth().height(146.dp),language=language,variant=p.variant)
                Text("${p.card.setCode} · ${p.card.number}",style=MaterialTheme.typography.labelSmall)
                Text(p.variant,style=MaterialTheme.typography.labelSmall,color=if(p.variant==variant && p.card==card)ListPalette.Gold else ListPalette.Secondary)
                Text("${p.quantity} possédée(s)",style=MaterialTheme.typography.labelSmall)
            }}
        }
    }
}
@Composable internal fun CompareCardsDialog(first:CatalogCard,catalog:List<CatalogCard>,details:CardDetails,close:()->Unit) {
    var second by remember{mutableStateOf<CatalogCard?>(null)};var query by remember{mutableStateOf("")}
    val results=remember(query,catalog){catalog.filter{query.isNotBlank() && (it.name.contains(query,true)||"${it.setCode} ${it.number}".contains(query,true))}.take(40)}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val compact = LocalConfiguration.current.screenHeightDp < 480
        val comparisonKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Surface(Modifier.fillMaxSize().safeDrawingPadding().testTag("design_compare_dialog").semantics { stateDescription = if(comparisonKeyboardOpen) "Clavier ouvert" else "Clavier fermé" },color=ListPalette.Background,contentColor=ListPalette.Text){Column(Modifier.padding(if(compact) 8.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 4.dp else 8.dp)){
            if(second != null || !compact) Row(verticalAlignment=Alignment.CenterVertically){Text("Comparer deux cartes",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=close){Text("Fermer")}}
            if(second==null) Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(query,{query=it.take(100)},label={Text("Choisir la seconde carte")},singleLine=true,modifier=Modifier.weight(1f))
                if(compact) TextButton(onClick=close){Text("Fermer")}
            }
            if(second==null) LazyColumn(Modifier.weight(1f).testTag("design_compare_results")){items(results){c->TextButton(onClick={second=c;focus.clearFocus(force=true);keyboard?.hide()}){Text("${c.name} · ${c.setCode} ${c.number}")}};if(query.isBlank())item{Text("Recherche un nom ou une référence.")}}
            else {
                TextButton(onClick={second=null}){Text("Changer la seconde carte")}
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    listOf(first,second!!).forEach{c->Column(Modifier.weight(1f)){
                        CardArtwork(c,Modifier.weight(1f).fillMaxWidth())
                        Text(deckCardLabel(c.name),style=MaterialTheme.typography.labelMedium)
                        Text("${c.setCode} ${c.number} · ${c.rarity}",style=MaterialTheme.typography.labelSmall)
                        details.get(c)?.let{d->Text("Coût : ${d.cost?.toString()?:"—"} · ${d.arena}",style=MaterialTheme.typography.bodySmall);Text(d.traits.joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
                    }}
                }
            }
        }}
    }
}

@Composable internal fun AddedCardReceipt(card:CatalogCard) {
    var arrived by remember(card){mutableStateOf(false)}
    LaunchedEffect(card){arrived=true}
    val travel by animateFloatAsState(if(arrived)1f else 0f,tween(if(LocalReducedMotion.current)0 else 260),label="Carte ajoutée au compteur")
    Box(Modifier.size(72.dp,48.dp)) {
        CardArtwork(card,Modifier.size(32.dp,44.dp).graphicsLayer{translationX=travel*20.dp.toPx();scaleX=1f-travel*.15f;scaleY=1f-travel*.15f})
        Text("+1",Modifier.align(Alignment.BottomEnd),color=ListPalette.Gold,style=MaterialTheme.typography.labelMedium)
    }
}
