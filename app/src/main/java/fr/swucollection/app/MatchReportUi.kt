package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal fun matchResultTitle(result: String) = when(result) {"win" -> "Victoire"; "loss" -> "Défaite"; else -> "Égalité"}
@Composable
internal fun MatchReportDialog(session: CounterSession, decks: List<DeckStore.SavedDeck>, leaders: List<CatalogCard>,
    onClose: () -> Unit, onSave: (MatchReport) -> Unit) {
    var report by remember { mutableStateOf(MatchReport.fromScores(session)) }
    var deckMenu by remember {mutableStateOf(false)}
    val leaderChoices=remember(report.opponentLeader,leaders) { if(report.opponentLeader.length<2) emptyList() else leaders.filter { DeckBrowser.searchKey(it.name).contains(DeckBrowser.searchKey(report.opponentLeader)) }.distinctBy {it.name}.take(5) }
    AlertDialog(onDismissRequest=onClose,title={Text("Bilan de mon match")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(session.playerCount==1) "Mode solo · résultat à renseigner" else "Score : ${session.frame.players[0].name} ${session.frame.scores[0]} — ${session.frame.scores[1]} ${session.frame.players[1].name}")
        Text("Je suis")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {session.frame.players.take(session.playerCount).forEachIndexed { i,p ->
            FilterChip(report.player==i,{report=report.copy(player=i,result=MatchReport.fromScores(session,i).result)},label={Text(p.name)})
        } }
        Box {OutlinedButton(onClick={deckMenu=true},modifier=Modifier.fillMaxWidth()) {Text(report.deckName.ifBlank {"Choisir mon deck (facultatif)"})};DropdownMenu(deckMenu,{deckMenu=false}) {
            DropdownMenuItem(text={Text("Sans deck associé")},onClick={report=report.copy(deckId="",deckName="");deckMenu=false})
            decks.forEach { d ->DropdownMenuItem(text={Text(d.name)},onClick={report=report.copy(deckId=d.id,deckName=d.name);deckMenu=false})}
        } }
        OutlinedTextField(report.opponentLeader,{report=report.copy(opponentLeader=it.take(160))},label={Text("Leader adverse (facultatif)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        leaderChoices.forEach {c ->TextButton(onClick={report=report.copy(opponentLeader=c.name)}) {Text(c.name)}}
        Text("Résultat pour moi · à confirmer")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("win","loss","draw").forEach {r ->FilterChip(report.result==r,{report=report.copy(result=r)},label={Text(matchResultTitle(r))})} }
        OutlinedTextField(report.note,{report=report.copy(note=it.take(500))},label={Text("Note de partie")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Text("Le bilan reste privé. Enregistrer démarre un nouveau match avec tes designs et tes réglages.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={onSave(report.copy(opponentLeader=report.opponentLeader.trim(),note=report.note.trim()))}) {Text("Enregistrer et recommencer")}},dismissButton={TextButton(onClick=onClose) {Text("Revenir au match")}})
}
@Composable
internal fun MatchStatisticsPanel(rows: List<JSONObject>) {
    var selectedDeck by remember {mutableStateOf<String?>(null)}
    var selectedOpponent by remember {mutableStateOf<String?>(null)}
    var deckMenu by remember {mutableStateOf(false)}; var opponentMenu by remember {mutableStateOf(false)}
    val reports=remember(rows) {rows.mapNotNull {it.optJSONObject("report")?.let(MatchReport::read)}}
    Text("Mes résultats",style=MaterialTheme.typography.titleMedium)
    Text("${reports.size} bilan(s) sur les ${rows.size} matchs conservés. Les anciens matchs sans bilan ne sont pas inclus.",style=MaterialTheme.typography.bodySmall)
    Box {OutlinedButton(onClick={deckMenu=true}) {Text(reports.firstOrNull {it.deckId==selectedDeck}?.deckName?.ifBlank {"Sans deck"} ?: "Tous mes decks")};DropdownMenu(deckMenu,{deckMenu=false}) {
        DropdownMenuItem(text={Text("Tous mes decks")},onClick={selectedDeck=null;deckMenu=false})
        reports.distinctBy {it.deckId}.forEach {r ->DropdownMenuItem(text={Text(r.deckName.ifBlank {"Sans deck"})},onClick={selectedDeck=r.deckId;deckMenu=false})}
    } }
    Box {OutlinedButton(onClick={opponentMenu=true}) {Text(selectedOpponent ?: "Tous les leaders adverses")};DropdownMenu(opponentMenu,{opponentMenu=false}) {
        DropdownMenuItem(text={Text("Tous les leaders adverses")},onClick={selectedOpponent=null;opponentMenu=false})
        reports.map {it.opponentLeader}.filter {it.isNotBlank()}.distinct().sorted().forEach {leader ->DropdownMenuItem(text={Text(leader)},onClick={selectedOpponent=leader;opponentMenu=false})}
    } }
    val stats=matchStats(rows,selectedDeck,selectedOpponent)
    Text("${stats.played} matchs · ${stats.wins} V · ${stats.losses} D · ${stats.draws} N · ${stats.percent} % de victoires",color=ListPalette.Gold)
    LinearProgressIndicator(progress={if(stats.played==0) 0f else stats.wins.toFloat()/stats.played},modifier=Modifier.fillMaxWidth())
}
