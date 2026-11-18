package fr.swucollection.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage

internal data class DeckChange(val zone: String, val reference: String, val before: Int, val after: Int)
internal object DesignDeckPlan {
    fun changes(before: PortableDeck, after: PortableDeck): List<DeckChange> {
        fun zone(title: String, old: List<DeckEntry>, next: List<DeckEntry>): List<DeckChange> {
            val a=old.groupBy{it.cardId}.mapValues{it.value.sumOf{e->e.count}}
            val b=next.groupBy{it.cardId}.mapValues{it.value.sumOf{e->e.count}}
            return (a.keys+b.keys).sorted().mapNotNull { id ->
                val x=a[id]?:0;val y=b[id]?:0
                if(x==y)null else DeckChange(title,id,x,y)
            }
        }
        fun identity(id:String)=if(id.isBlank())emptyList() else listOf(DeckEntry(id,1))
        return zone("Leader",identity(before.leader),identity(after.leader))+zone("Base",identity(before.base),identity(after.base))+
            zone("Deck",before.deck,after.deck)+zone("Réserve",before.sideboard,after.sideboard)
    }
    fun group(entry: DeckEntry, byId: Map<String,CatalogCard>, details: CardDetails, mode: String): String {
        val card=byId[entry.cardId]?:return "Références à vérifier"
        val d=details.get(card)
        return if(mode=="Coût") d?.cost?.let{"Coût $it"}?:"Coût inconnu" else when(DeckConstruction.type(card)) {
            "event","événement","evenement" -> "Événements"
            "upgrade","amélioration","amelioration" -> "Améliorations"
            else -> when(d?.arena?.lowercase()) {"space","spatiale","spatial"->"Unités spatiales";"ground","terrestre"->"Unités terrestres";else->"Autres cartes"}
        }
    }
}
@Composable internal fun DeckChangesDialog(before:PortableDeck,after:PortableDeck,catalog:List<CatalogCard>,close:()->Unit) {
    val byId=remember(catalog){catalog.associateBy(DeckConstruction::reference)}
    val changes=remember(before,after){DesignDeckPlan.changes(before,after)}
    AlertDialog(onDismissRequest=close,title={Text("Avant / après")},text={
        LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {Text("Comparaison avec la version de départ du deck. Aucune quantité de collection n’est modifiée.",style=MaterialTheme.typography.bodySmall)}
            if(changes.isEmpty())item{Text("Aucune modification du deck.")}
            items(changes){c->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CardArtwork(byId[c.reference],Modifier.size(42.dp,60.dp))
                Column(Modifier.weight(1f)){Text(deckCardLabel(byId[c.reference]?.name?:c.reference));Text(c.zone,style=MaterialTheme.typography.labelSmall)}
                Text("${c.before} → ${c.after}",color=if(c.after>c.before)ListPalette.Gold else ListPalette.Secondary)
            }}
        }
    },confirmButton={TextButton(onClick=close){Text("Fermer")}})
}
@Composable internal fun DeckBuildDock(deck:PortableDeck,catalog:List<CatalogCard>,leaderChosen:Boolean,baseChosen:Boolean,onIdentity:(String)->Unit) {
    val byId=remember(catalog){catalog.associateBy(DeckConstruction::reference)}
    Surface(color=ListPalette.Raised,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().testTag("design_deck_dock")) {
        Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Leader" to if(leaderChosen)byId[deck.leader] else null,"Base" to if(baseChosen)byId[deck.base] else null).forEach{(label,card)->
                Column(Modifier.weight(1f).clickable{onIdentity(label)}.padding(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        CardArtwork(card,Modifier.size(40.dp,28.dp));Text(label,style=MaterialTheme.typography.labelSmall)
                    }
                    Text(card?.name?.let(::deckCardLabel)?.substringBefore(" — ")?:"À choisir",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.padding(4.dp)){Text("${deck.deck.sumOf{it.count}} / 50",color=ListPalette.Gold);Text("${deck.sideboard.sumOf{it.count}} / 10 réserve",style=MaterialTheme.typography.labelSmall)}
        }
    }
}
internal data class DeckCover(val reference:String,val focus:Float)
internal class DeckCoverStore(context:android.content.Context) {
    private val prefs=context.applicationContext.getSharedPreferences("swu_deck_covers",0)
    fun read(id:String)=DeckCover(prefs.getString("$id.ref","").orEmpty(),prefs.getFloat("$id.focus",.5f).coerceIn(0f,1f))
    fun write(id:String,cover:DeckCover){require(cover.focus.isFinite() && cover.focus in 0f..1f);check(prefs.edit().putString("$id.ref",cover.reference).putFloat("$id.focus",cover.focus).commit()){"Couverture non enregistrée. Réessaie."}}
}
@Composable internal fun DeckCoverArtwork(card:CatalogCard?,focus:Float,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val url=card?.let { CardLanguageArtwork.displayFace(context,it,it.language,null,false).first }.orEmpty()
    Box(modifier.clipToBounds().background(ListPalette.Raised),contentAlignment=Alignment.Center){
        if(url.isNotBlank()) AsyncImage(OfflineImages.source(context,url),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alignment=BiasAlignment(0f,focus*2f-1f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,ListPalette.Background.copy(alpha=.5f)))))
    }
}
@Composable internal fun DeckCoverDialog(id:String,deck:PortableDeck,catalog:List<CatalogCard>,close:()->Unit,saved:()->Unit) {
    val context=LocalContext.current;val store=remember(context){DeckCoverStore(context)}
    val byId=remember(catalog){catalog.associateBy(DeckConstruction::reference)}
    val choices=remember(deck,catalog){(listOf(deck.leader,deck.base)+(deck.deck+deck.sideboard).map{it.cardId}).distinct().mapNotNull{byId[it]}}
    var cover by remember(id){mutableStateOf(store.read(id))};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=close,title={Text("Couverture du deck")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        DeckCoverArtwork(byId[cover.reference]?:byId[deck.leader],cover.focus,Modifier.fillMaxWidth().height(112.dp))
        Text("Cadrage vertical",style=MaterialTheme.typography.labelMedium)
        Slider(cover.focus,{cover=cover.copy(focus=it)})
        LazyColumn(Modifier.heightIn(max=220.dp)){
            item{TextButton(onClick={cover=DeckCover("",.5f)}){Text("Couverture automatique")}}
            items(choices){card->TextButton(onClick={cover=cover.copy(reference=DeckConstruction.reference(card))}){Text(deckCardLabel(card.name))}}
        }
        error?.let{Text(it,color=ListPalette.Danger)}
    }},confirmButton={TextButton(onClick={runCatching{store.write(id,cover)}.onSuccess{saved();close()}.onFailure{error=it.message}}){Text("Enregistrer")}},dismissButton={TextButton(onClick=close){Text("Annuler")}})
}
