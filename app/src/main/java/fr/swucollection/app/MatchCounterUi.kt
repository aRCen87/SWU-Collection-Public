package fr.swucollection.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal fun CounterStyle.ink() = when(this) {
    CounterStyle.GALAXY -> Color(0xFF76D9FF); CounterStyle.SITH -> Color(0xFFFF8B8B)
    CounterStyle.JEDI -> Color(0xFF9CF0B5); CounterStyle.NEBULA -> Color(0xFFDDB1FF)
    CounterStyle.ICE -> Color(0xFFD6F7FF); CounterStyle.CONSOLE -> Color(0xFFFFD482)
}
@Composable
private fun CounterBackdrop(style: CounterStyle, fraction: Float, modifier: Modifier = Modifier) {
    val accent=style.ink(); val progress by animateFloatAsState(fraction.coerceIn(0f,1f),label="État de la base")
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha=.22f),Color(0xFF06101D)),center=Offset(size.width*.5f,size.height*.55f),radius=size.maxDimension))
        val center=Offset(size.width/2,size.height/2); val radius=size.minDimension*.36f
        when(style) {
            CounterStyle.CONSOLE -> {
                for(i in 1..12) { val x=size.width*i/12;drawLine(accent.copy(alpha=.09f),Offset(x,0f),Offset(x,size.height),1f) }
                for(i in 1..6) { val y=size.height*i/6;drawLine(accent.copy(alpha=.09f),Offset(0f,y),Offset(size.width,y),1f) }
                drawRoundRect(accent.copy(alpha=.28f),topLeft=Offset(size.width*.2f,size.height*.15f),size=Size(size.width*.6f,size.height*.7f),style=Stroke(2f))
            }
            CounterStyle.ICE, CounterStyle.SITH -> {
                val path=Path(); val sides=if(style==CounterStyle.ICE) 6 else 8
                for(i in 0..sides) { val a=2*Math.PI*i/sides-Math.PI/2; val x=center.x+cos(a).toFloat()*radius;val y=center.y+sin(a).toFloat()*radius
                    if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
                drawPath(path,accent.copy(alpha=.24f),style=Stroke(3f));drawLine(accent.copy(alpha=.3f),Offset(0f,center.y),Offset(center.x-radius,center.y),2f)
                drawLine(accent.copy(alpha=.3f),Offset(center.x+radius,center.y),Offset(size.width,center.y),2f)
            }
            else -> {
                repeat(40) { i -> drawCircle(accent.copy(alpha=.18f+(i%3)*.08f),1f+(i%2),Offset(((i*173+37)%997)/997f*size.width,((i*277+17)%991)/991f*size.height)) }
                drawCircle(accent.copy(alpha=.12f),radius,center,style=Stroke(7f))
                drawArc(accent.copy(alpha=.5f),-90f,360f*progress,false,Offset(center.x-radius,center.y-radius),Size(radius*2,radius*2),style=Stroke(5f))
                if(style==CounterStyle.NEBULA) drawCircle(accent.copy(alpha=.1f),radius*1.16f,center,style=Stroke(2f))
            }
        }
    }
}
@Composable
internal fun MatchCounterPanel(database: CollectionDatabase, onClose: () -> Unit) {
    val store=remember(database) { MatchCounterStore(database) }
    var failure by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf(runCatching { store.read() }.getOrElse { failure="La partie enregistrée est illisible. Elle est conservée ; restaure une sauvegarde avant de continuer."; CounterSession() }) }
    val readOnly=failure?.contains("illisible")==true
    val view=LocalView.current
    DisposableEffect(view) { val lease=CounterAwake(view);onDispose { lease.close() } }
    BackHandler(onBack=onClose)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis();delay(1000) } }
    var editing by remember { mutableIntStateOf(-1) };var dialog by remember { mutableStateOf("") }
    var previousMatches by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    val haptic=LocalHapticFeedback.current
    fun save(next: CounterSession) { if(!readOnly) runCatching { store.save(next) }.onSuccess { session=next;failure=null }.onFailure { failure="Modification non enregistrée. Libère de l’espace puis réessaie." } }
    fun action(next: CounterFrame,label: String) { save(session.change(next,label));haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val duration=session.duration(now)/1000
    Column(Modifier.fillMaxSize().background(Color(0xFF030A17)).safeDrawingPadding().padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onClose) { Icon(Icons.Default.ArrowBack,"Fermer le compteur") }
            Column(Modifier.weight(1f)) { Text("POINTS DE VIE",fontSize=12.sp,color=ListPalette.Gold,fontWeight=FontWeight.Bold)
                Text(if(session.playerCount==1) "Solo · Manche ${session.frame.round}" else "Manche ${session.frame.round} · ${session.frame.scores[0]} — ${session.frame.scores[1]}",fontSize=12.sp) }
            TextButton(onClick={ save(session.toggleTimer(System.currentTimeMillis())) }) { Text("${duration/60}:${(duration%60).toString().padStart(2,'0')} ${if(session.since>0) "Ⅱ" else "▶"}") }
            IconButton(onClick={ dialog="options" }) { Icon(Icons.Default.Settings,"Options de la partie") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
            Text("Compteur pour",style=MaterialTheme.typography.labelMedium)
            listOf(1,2).forEach { count -> FilterChip(session.playerCount==count, { save(session.withPlayerCount(count)) }, enabled=!readOnly,
                label={Text(if(count==1) "1 joueur" else "2 joueurs")}) }
        }
        failure?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=8.dp),fontSize=12.sp) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide=maxWidth>maxHeight
            val player: @Composable (Int,Modifier)->Unit = { i,m ->
                CounterPlayerPane(session.frame.players[i],i,session.frame.scores[i],session.frame.initiative==i,session.damageMode,session.tokens,
                    m.graphicsLayer { rotationZ=if(i==1 && session.faceToFace) 180f else 0f },
                    onHit={ delta -> action(session.frame.player(i) { it.hit(delta) },"${session.frame.players[i].name} : ${if(delta>0) "+" else ""}$delta dégât(s)") },
                    onEdit={ editing=i },onInitiative={ action(session.frame.copy(initiative=if(session.frame.initiative==i) -1 else i),"Initiative : ${session.frame.players[i].name}") },
                    onToken={ shield,delta -> action(session.frame.player(i) { if(shield) it.copy(shields=(it.shields+delta).coerceIn(0,99)) else it.copy(experience=(it.experience+delta).coerceIn(0,99)) },"Jetons : ${session.frame.players[i].name}") })
            }
            if(session.playerCount==1) player(0,Modifier.fillMaxSize())
            else if(wide) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { player(0,Modifier.weight(1f).fillMaxHeight()); player(1,Modifier.weight(1f).fillMaxHeight()) }
            else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) { player(1,Modifier.weight(1f).fillMaxWidth()); player(0,Modifier.weight(1f).fillMaxWidth()) }
        }
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={ save(session.undoLast()) },enabled=session.undo.isNotEmpty() && !readOnly) { Text("Annuler") }
            OutlinedButton(onClick={ if(session.playerCount==1) save(session.nextSoloRound()) else dialog="round" },enabled=!readOnly) { Text(if(session.playerCount==1) "Nouvelle manche" else "Fin de manche") }
            OutlinedButton(onClick={ dialog="events" }) { Text("Journal") }
        }
    }
    if(editing in 0..1) CounterPlayerEditor(session.frame.players[editing],onDismiss={ editing=-1 }) { next ->
        action(session.frame.player(editing) { next },"Réglages de ${next.name}"); editing=-1
    }
    if(dialog=="round") AlertDialog(onDismissRequest={dialog=""},title={Text("Qui remporte la manche ?")},text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Les points de vie et les jetons seront remis à zéro pour la manche suivante. Tu peux annuler cette action ensuite.")
        session.frame.players.forEachIndexed { i,p -> Button(onClick={action(session.frame.won(i),"Manche gagnée : ${p.name}");dialog=""},modifier=Modifier.fillMaxWidth()) {Text(p.name)} }
    } },confirmButton={TextButton(onClick={dialog=""}) {Text("Revenir à la partie")} })
    if(dialog=="options") AlertDialog(onDismissRequest={dialog=""},title={Text("Ma table de jeu")},text={ Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("L’écran reste allumé pendant que ce compteur est affiché. La partie est enregistrée après chaque action.")
        if(session.playerCount==2) CounterSwitch("Joueurs face à face",session.faceToFace) { save(session.copy(faceToFace=it)) }
        CounterSwitch("Afficher les dégâts reçus",session.damageMode) {save(session.copy(damageMode=it))}
        CounterSwitch("Compteurs de jetons",session.tokens) {save(session.copy(tokens=it))}
        Text("Les jetons sont un aide-mémoire par joueur, sans automatisation des règles des cartes.",fontSize=12.sp)
        OutlinedButton(onClick={dialog="";editing=0},modifier=Modifier.fillMaxWidth()) {Text("Design et base du joueur 1")}
        if(session.playerCount==2) OutlinedButton(onClick={dialog="";editing=1},modifier=Modifier.fillMaxWidth()) {Text("Design et base du joueur 2")}
        OutlinedButton(onClick={previousMatches=store.history();dialog="history"},modifier=Modifier.fillMaxWidth()) {Text("Mes matchs enregistrés")}
        OutlinedButton(onClick={dialog="finish"},modifier=Modifier.fillMaxWidth(),enabled=!readOnly) {Text("Enregistrer ce match et recommencer")}
    } },confirmButton={TextButton(onClick={dialog=""}) {Text("Reprendre")} })
    if(dialog=="finish") MatchReportDialog(session,
        remember { DeckStore(view.context.applicationContext).use { it.all() } },
        remember { database.allCatalogSets().flatMap { database.catalogCardsForSet(it.code) }.filter { DeckConstruction.type(it) in listOf("leader","chef") } },
        onClose={dialog=""},onSave={report ->runCatching {store.finish(session,System.currentTimeMillis(),report)}.onSuccess {session=it;dialog=""}.onFailure {failure="Match non enregistré. Réessaie."}})
    if(dialog=="events" || dialog=="history") AlertDialog(onDismissRequest={dialog=""},title={Text(if(dialog=="events") "Journal de la partie" else "Mes 50 derniers matchs")},text={ Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(dialog=="events") { if(session.events.isEmpty()) Text("Aucune action pour le moment.");session.events.asReversed().forEach {Text(it)} }
        else { MatchStatisticsPanel(previousMatches); HorizontalDivider(); if(previousMatches.isEmpty()) Text("Aucun match enregistré.");previousMatches.forEach {r ->
            val p=r.getJSONArray("players");val s=r.getJSONArray("scores")
            Text(SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.FRANCE).format(Date(r.getLong("date"))) + if(r.optInt("playerCount",2)==1) " · Solo · ${p.getString(0)} · ${r.getLong("duration")/60000} min" else " · ${p.getString(0)} ${s.getInt(0)} — ${s.getInt(1)} ${p.getString(1)} · ${r.getLong("duration")/60000} min")
            r.optJSONObject("report")?.let { raw -> val report=MatchReport.read(raw)
                Text("${matchResultTitle(report.result)} · ${report.deckName.ifBlank { "Sans deck" }} · contre ${report.opponentLeader.ifBlank { "leader non renseigné" }}",style=MaterialTheme.typography.bodySmall)
                if(report.note.isNotBlank()) Text(report.note,style=MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        } }
    } },confirmButton={TextButton(onClick={dialog=""}) {Text("Fermer")} })
}
@Composable
private fun CounterSwitch(label: String, value: Boolean, change: (Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(value,change)} }
@Composable
private fun CounterPlayerPane(p: CounterPlayer,index: Int,score: Int,initiative: Boolean,damageMode: Boolean,tokens: Boolean,
    modifier: Modifier,onHit: (Int)->Unit,onEdit: ()->Unit,onInitiative: ()->Unit,onToken: (Boolean,Int)->Unit) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).border(1.dp,p.style.ink().copy(alpha=.6f),RoundedCornerShape(24.dp))) {
        val compact=maxHeight<260.dp
        CounterBackdrop(p.style,p.life.toFloat()/p.maximum,Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().then(if(maxHeight < 200.dp) Modifier.verticalScroll(rememberScrollState()).heightIn(min=196.dp) else Modifier).padding(if(compact) 8.dp else 12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onEdit,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp)) {Text(p.name+"  ✎",color=Color.White,maxLines=1,fontWeight=FontWeight.Bold)}
                TextButton(onClick=onInitiative) {Text(if(initiative) "★ Initiative" else "Initiative",color=p.style.ink(),fontSize=12.sp)}
                Text("$score",color=p.style.ink(),fontWeight=FontWeight.Bold)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text(if(damageMode) p.damage.toString() else p.life.toString(),fontSize=if(compact) 52.sp else 82.sp,fontWeight=FontWeight.Black,color=if(p.life==0) Color(0xFFFF9B9B) else Color.White,lineHeight=if(compact) 54.sp else 86.sp,
                    modifier=Modifier.semantics {contentDescription="Joueur ${index+1}, ${p.name}, ${p.life} points de vie restants sur ${p.maximum}, ${p.damage} dégâts"})
                Text(if(p.life==0) "BASE DÉTRUITE" else if(damageMode) "DÉGÂTS · ${p.life} PV restants" else "POINTS DE VIE · ${p.damage} dégâts / ${p.maximum}",color=p.style.ink(),fontSize=11.sp,fontWeight=FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={onHit(5)},enabled=p.life>0,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(2.dp)) {Text("−5 PV",color=p.style.ink())}
                Button(onClick={onHit(1)},enabled=p.life>0,modifier=Modifier.weight(1f).heightIn(min=52.dp),colors=ButtonDefaults.buttonColors(containerColor=p.style.ink(),contentColor=Color(0xFF06101D)),contentPadding=PaddingValues(2.dp)) {Text("−1 PV",fontWeight=FontWeight.Bold)}
                Button(onClick={onHit(-1)},enabled=p.damage>0,modifier=Modifier.weight(1f).heightIn(min=52.dp),colors=ButtonDefaults.buttonColors(containerColor=p.style.ink().copy(alpha=.2f),contentColor=Color.White),contentPadding=PaddingValues(2.dp)) {Text("+1 PV",fontWeight=FontWeight.Bold)}
                OutlinedButton(onClick={onHit(-5)},enabled=p.damage>0,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(2.dp)) {Text("+5 PV",color=p.style.ink())}
            }
            if(tokens && compact) TextButton(onClick=onEdit,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=36.dp)) {
                Text("Boucliers ${p.shields} · Exp. ${p.experience}  ✎",fontSize=11.sp,color=p.style.ink())
            }
            if(tokens && !compact) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                listOf(true to p.shields,false to p.experience).forEach { (shield,n) ->
                    TextButton(onClick={onToken(shield,-1)},enabled=n>0,contentPadding=PaddingValues(2.dp)) {Text("−")}
                    Text("${if(shield) "Boucliers" else "Exp."} $n",fontSize=11.sp,color=Color.White)
                    TextButton(onClick={onToken(shield,1)},enabled=n<99,contentPadding=PaddingValues(2.dp)) {Text("+")}
                }
            }
        }
    }
}
@Composable
private fun CounterPlayerEditor(player: CounterPlayer,onDismiss: ()->Unit,onSave: (CounterPlayer)->Unit) {
    var name by remember {mutableStateOf(player.name)};var maximum by remember {mutableStateOf(player.maximum.toString())};var style by remember {mutableStateOf(player.style)};var shields by remember {mutableStateOf(player.shields)};var experience by remember {mutableStateOf(player.experience)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Mon compteur")},text={ Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name,{name=it.take(40)},label={Text("Nom du joueur")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(maximum,{maximum=it.filter(Char::isDigit).take(3)},label={Text("Points de vie de la base (1 à 100)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf(25,30,35,40).forEach {v -> FilterChip(maximum==v.toString(),{maximum=v.toString()},label={Text("$v PV")})}}
        Text("Jetons (aide-mémoire)",fontWeight=FontWeight.Bold)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Boucliers : $shields",Modifier.weight(1f));TextButton(onClick={shields--},enabled=shields>0) {Text("−")};TextButton(onClick={shields++},enabled=shields<99) {Text("+")}
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Expérience : $experience",Modifier.weight(1f));TextButton(onClick={experience--},enabled=experience>0) {Text("−")};TextButton(onClick={experience++},enabled=experience<99) {Text("+")}
        }
        Text("Choisir mon design",fontWeight=FontWeight.Bold)
        CounterStyle.entries.chunked(2).forEach {row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {skin ->
            OutlinedCard(onClick={style=skin},modifier=Modifier.weight(1f).height(110.dp),border=androidx.compose.foundation.BorderStroke(if(style==skin) 3.dp else 1.dp,skin.ink())) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CounterBackdrop(skin,.8f,Modifier.fillMaxSize());Column(horizontalAlignment=Alignment.CenterHorizontally) {Text("30",fontSize=32.sp,fontWeight=FontWeight.Black,color=Color.White);Text(skin.title,fontSize=10.sp,color=skin.ink())}}
            }
        } } }
        Text("Le design est indépendant de celui de l’autre joueur. Changer les PV maximum conserve les dégâts déjà reçus.",fontSize=12.sp)
    } },confirmButton={TextButton(onClick={val hp=maximum.toInt();onSave(player.copy(name=name.trim(),maximum=hp,damage=player.damage.coerceAtMost(hp),style=style,shields=shields,experience=experience))},enabled=name.isNotBlank() && (maximum.toIntOrNull() ?: 0) in 1..100) {Text("Appliquer")}},dismissButton={TextButton(onClick=onDismiss) {Text("Annuler")} })
}
