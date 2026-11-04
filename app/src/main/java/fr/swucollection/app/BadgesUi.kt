package fr.swucollection.app

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ColumnScope.BadgesPanel(progress: List<BadgeProgress>, catalog: List<CatalogCard>, initialSelection: String? = null, onSelectionConsumed: () -> Unit = {}, onCard: (CatalogCard) -> Unit) {
    var category by rememberSaveable { mutableStateOf("Tous") }
    var status by rememberSaveable { mutableStateOf(if(initialSelection != null) "Obtenus" else "En cours") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(initialSelection) }
    LaunchedEffect(Unit) { onSelectionConsumed() }
    val earned = progress.count { it.earned }
    val active = progress.count { it.available || it.earned }
    val visible = remember(progress,category,status,query) {
        val q=badgeNormalize(query)
        progress.filter { p -> (category=="Tous" || p.badge.category==category) &&
            (q.isBlank() || badgeNormalize(p.badge.title+" "+p.badge.chapter+" "+p.badge.story).contains(q)) &&
            when(status) { "Obtenus" -> p.earned; "À venir" -> !p.available && !p.earned; else -> p.available && !p.earned } }
            .sortedWith(compareByDescending<BadgeProgress> { it.fraction }.thenBy { it.badge.chapter }.thenBy { it.badge.title })
    }
    LazyVerticalGrid(columns=GridCells.Adaptive(148.dp),modifier=Modifier.fillMaxWidth().weight(1f),
        contentPadding=PaddingValues(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="summary",span={GridItemSpan(maxLineSpan)}) {
            Surface(color=badgeNavy,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,badgeGold.copy(alpha=.35f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.WorkspacePremium,null,tint=badgeGold,modifier=Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ta légende se construit",fontWeight=FontWeight.Bold,color=badgeGold)
                        Text("$earned / $active hauts faits obtenus",style=MaterialTheme.typography.bodyMedium,color=Color(0xFFE4EAF2))
                        Text("Histoires, sagas et exploits de collection",style=MaterialTheme.typography.bodySmall,color=badgeSilver)
                    }
                }
            }
        }
        item(key="search",span={GridItemSpan(maxLineSpan)}) {
            OutlinedTextField(query,{ query=it },singleLine=true,modifier=Modifier.fillMaxWidth(),
                label={Text("Rechercher un haut fait")},leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Effacer la recherche")}})
        }
        item(key="categories",span={GridItemSpan(maxLineSpan)}) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Tous","Histoires","Sagas","Application","Collection","Prestige").forEach { label ->
                    FilterChip(category==label,{category=label},label={Text(label)})
                }
            }
        }
        item(key="statuses",span={GridItemSpan(maxLineSpan)}) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("En cours","Obtenus","À venir").forEach { label -> FilterChip(status==label,{status=label},label={Text(label)}) }
            }
        }
        if(progress.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
            Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        } else if(visible.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
            Text(if(status=="Obtenus") "Tes badges gagnés apparaîtront ici." else "Aucun haut fait dans cette sélection.",color=badgeSilver)
        }
        items(visible,key={it.badge.id}) { p ->
            Surface(onClick={selected=p.badge.id},color=badgeNavy,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,if(p.earned)badgeGold else Color(0xFF24394E))) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    BadgePin(p.badge,catalog,Modifier.fillMaxWidth(),locked=!p.earned)
                    Text(p.badge.title,fontWeight=FontWeight.SemiBold,color=if(p.earned)badgeGold else Color(0xFFF0F4FA),minLines=2)
                    Text(p.badge.chapter,style=MaterialTheme.typography.labelSmall,color=badgeSilver)
                    Text(badgeTierNames[p.badge.tier],style=MaterialTheme.typography.labelSmall,color=badgeGold)
                    if(p.earned) Text("Obtenu",color=badgeGold,style=MaterialTheme.typography.labelMedium)
                    else if(!p.available) Text("À venir avec le catalogue",color=badgeSilver,style=MaterialTheme.typography.labelSmall)
                    else {
                        LinearProgressIndicator(progress={p.fraction},modifier=Modifier.fillMaxWidth(),color=badgeGold,trackColor=Color(0xFF203447))
                        Text("${p.current.coerceAtMost(p.target)} / ${p.target}",style=MaterialTheme.typography.labelMedium,color=badgeSilver)
                    }
                }
            }
        }
    }
    progress.firstOrNull { it.badge.id==selected }?.let { p -> BadgeDetailsDialog(p,progress,catalog,onDismiss={selected=null},onBadge={selected=it},onCard={selected=null;onCard(it)}) }
}
@Composable
internal fun BadgeDetailsDialog(p: BadgeProgress, all: List<BadgeProgress>, catalog: List<CatalogCard>,
    onDismiss: () -> Unit, onBadge: (String) -> Unit, onCard: (CatalogCard) -> Unit) {
    var alternatives by remember(p.badge.id) { mutableStateOf<BadgeCheck?>(null) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=Color(0xFF040B14)) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Haut fait",modifier=Modifier.weight(1f),color=badgeGold,fontWeight=FontWeight.Bold)
                    IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Fermer le haut fait",tint=badgeGold)}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    BadgePin(p.badge,catalog,Modifier.width(230.dp),locked=!p.earned)
                    Text(p.badge.title,fontSize=25.sp,fontWeight=FontWeight.Bold,color=badgeGold)
                    Text("${p.badge.chapter} · ${badgeTierNames[p.badge.tier]}",color=badgeSilver)
                    Text(p.badge.story,color=Color(0xFFF0F4FA))
                    if(p.badge.prerequisites.isNotEmpty()) Text("Termine chaque chapitre ci-dessous pour gagner cette saga.",color=badgeGold)
                    if(p.earned) {
                        val date=java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.FRANCE).format(java.util.Date(p.award!!.time))
                        Text("Obtenu le $date · acquis définitivement",color=badgeGold)
                    } else if(!p.available) Text("En réserve : certaines cartes ou conditions ne sont pas encore disponibles. Les futures extensions pourront ouvrir ce haut fait.",color=badgeSilver)
                    else {
                        LinearProgressIndicator(progress={p.fraction},modifier=Modifier.fillMaxWidth(),color=badgeGold)
                        Text("${p.current.coerceAtMost(p.target)} / ${p.target}",color=badgeSilver)
                    }
                    if(p.badge.requirements.isNotEmpty()) Text("Un exemplaire de chaque élément suffit. Les finitions alternatives de la même carte sont acceptées ; les cartes d’une scène restent précisées ci-dessous.",style=MaterialTheme.typography.bodySmall,color=badgeSilver)
                    p.checks.forEachIndexed { index, check ->
                        Surface(color=badgeNavy,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(if(p.earned || check.complete)Icons.Default.CheckCircle else if(!check.available)Icons.Default.Schedule else Icons.Default.RadioButtonUnchecked,null,tint=if(p.earned || check.complete)badgeGold else badgeSilver)
                                Column(Modifier.weight(1f)) {
                                    Text(check.label,color=Color.White,fontWeight=FontWeight.Medium)
                                    Text(if(p.earned)"Condition accomplie" else if(check.complete)"Possédé ou accompli" else if(check.available)"À compléter" else "Carte attendue",style=MaterialTheme.typography.bodySmall,color=badgeSilver)
                                    if(check.candidates.isNotEmpty() && p.badge.prerequisites.isEmpty()) TextButton(onClick={alternatives=check}) {Text("Voir les cartes acceptées")}
                                    p.badge.prerequisites.getOrNull(index)?.let { id -> TextButton(onClick={onBadge(id)}){Text("Ouvrir ce chapitre")} }
                                }
                            }
                        }
                    }
                    if(p.badge.metric.isNotEmpty()) Text(p.badge.story,color=badgeSilver,style=MaterialTheme.typography.bodyMedium)
                    if(p.earned && p.award!!.evidence.isNotEmpty()) {
                        Text("Cartes réunies lors du déblocage",color=badgeGold,fontWeight=FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            p.award.evidence.mapNotNull { id -> catalog.firstOrNull { DeckConstruction.reference(it)==id } }.take(12).forEach { card ->
                                Column(Modifier.width(100.dp).clickable{onCard(card)}) {
                                    CardArtwork(card,Modifier.width(100.dp).height(140.dp))
                                    Text(card.name.substringBefore(" — "),color=badgeSilver,style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    alternatives?.let { check -> AlertDialog(onDismissRequest={alternatives=null},title={Text(check.label)},text={
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(check.candidates,key={DeckConstruction.reference(it)}) { card ->
                Row(Modifier.fillMaxWidth().clickable{alternatives=null;onCard(card)},horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    CardArtwork(card,Modifier.width(66.dp).height(92.dp))
                    Column(Modifier.weight(1f)) {Text(card.name);Text("${card.setCode} · ${card.number}",style=MaterialTheme.typography.bodySmall,color=badgeGold)}
                }
            }
        }
    },confirmButton={TextButton(onClick={alternatives=null}){Text("Fermer")}}) }
}

@Composable
internal fun BadgeCelebration(pending: List<BadgeProgress>,catalog: List<CatalogCard>,onContinue: () -> Unit,onView: (String) -> Unit) {
    val first=pending.firstOrNull() ?: return
    val reveal=remember(first.badge.id) { Animatable(if(ValueAnimator.areAnimatorsEnabled())0f else 1f) }
    LaunchedEffect(first.badge.id) { if(ValueAnimator.areAnimatorsEnabled())reveal.animateTo(1f,tween(850)) }
    Dialog(onDismissRequest=onContinue,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            val pinWidth = minOf(240.dp, maxHeight * .32f)
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(badgeGold.copy(alpha=.13f*reveal.value),Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(if(pending.size==1)"Haut fait débloqué" else "${pending.size} hauts faits débloqués",color=badgeGold,fontWeight=FontWeight.SemiBold,fontSize=20.sp)
                BadgePin(first.badge,catalog,Modifier.width(pinWidth).graphicsLayer {
                    alpha=reveal.value;scaleX=.72f+.28f*reveal.value;scaleY=scaleX;rotationY=(1f-reveal.value)*25f
                }, shine=reveal.value)
                Text(first.badge.title,fontWeight=FontWeight.Bold,fontSize=28.sp,color=badgeGold)
                Text(first.badge.story,color=Color(0xFFE7EBF0))
                Text(badgeTierNames[first.badge.tier],color=badgeSilver)
                if(pending.size>1) Text(pending.drop(1).take(5).joinToString("\n"){"✦ ${it.badge.title}"} + if(pending.size>6) "\nEt ${pending.size-6} autres à découvrir dans ta galerie." else "",color=badgeSilver)
                if(first.award?.evidence?.isNotEmpty()==true) {
                    Text("Les cartes de ton exploit",color=badgeGold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        first.award.evidence.mapNotNull { id -> catalog.firstOrNull { DeckConstruction.reference(it)==id } }.take(6).forEach { card ->
                            CardArtwork(card,Modifier.width(86.dp).height(120.dp))
                        }
                    }
                }
            }
                Button(onClick={onView(first.badge.id)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=badgeGold,contentColor=Color.Black)) {Text(if(pending.size==1)"Voir mon badge" else "Voir mes badges")}
                OutlinedButton(onClick=onContinue,modifier=Modifier.fillMaxWidth()) {Text("Continuer",color=badgeGold)}
            }
        }
    }
}

