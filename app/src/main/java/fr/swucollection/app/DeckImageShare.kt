package fr.swucollection.app

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

internal data class DeckImageSection(val title: String,val entries: List<DeckEntry>)
internal object DeckImagePlan {
    fun sections(deck: PortableDeck): List<DeckImageSection> {
        val sections=listOf(DeckImageSection("LEADER ET BASE",listOfNotNull(deck.leader.takeIf {it.isNotBlank()}?.let {DeckEntry(it,1)},deck.base.takeIf {it.isNotBlank()}?.let {DeckEntry(it,1)})),DeckImageSection("DECK PRINCIPAL",deck.deck),DeckImageSection("RÉSERVE",deck.sideboard))
        require(sections.sumOf {it.entries.size} in 1..120) {"L’image accepte de 1 à 120 références."}
        sections.flatMap {it.entries}.forEach {require(it.count in 1..9999);DeckInterchange.normalizeId(it.cardId)}
        return sections.filter {it.entries.isNotEmpty()}
    }
    fun height(sections: List<DeckImageSection>)=180+sections.sumOf {80+((it.entries.size+5)/6)*310}+70
}
internal object DeckImageExporter {
    suspend fun create(context: android.content.Context,name: String,deck: PortableDeck,catalog: List<CatalogCard>): Pair<File,Int> = withContext(Dispatchers.IO) {
        val sections=DeckImagePlan.sections(deck);val height=DeckImagePlan.height(sections)
        require(height<=8000)
        val byRef=catalog.associateBy {DeckConstruction.reference(it)}
        val bitmap=Bitmap.createBitmap(1200,height,Bitmap.Config.ARGB_8888)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);val canvas=Canvas(bitmap)
        val directory=File(context.cacheDir,"updates").apply {mkdirs()}
        directory.listFiles()?.filter {it.name.startsWith("swu-deck-image-") && it.lastModified()<System.currentTimeMillis()-3*24*60*60_000L}?.forEach {it.delete()}
        val file=File.createTempFile("swu-deck-image-",".png",directory)
        var missing=0
        fun text(value: String,x: Float,y: Float,size: Float,color: Int=AndroidColor.WHITE,bold: Boolean=false) {
            paint.color=color;paint.textSize=size;paint.typeface=if(bold) Typeface.create("sans-serif",Typeface.BOLD) else Typeface.DEFAULT
            canvas.drawText(value,x,y,paint)
        }
        val gold=AndroidColor.rgb(255,212,125)
        try {
            canvas.drawColor(AndroidColor.rgb(4,13,26))
            text("SWU COLLECTION · DECK À PARTAGER",30f,42f,22f,gold,true)
            paint.textSize=38f;val title=name.ifBlank {"Mon deck"};val length=paint.breakText(title,true,1130f,null)
            text(title.take(length),30f,100f,38f,AndroidColor.WHITE,true)
            text("${deck.deck.sumOf {it.count}} cartes · réserve ${deck.sideboard.sumOf {it.count}} · quantités indiquées",30f,143f,21f)
            var y=180
            for(section in sections) {
                coroutineContext.ensureActive();text(section.title,30f,(y+34).toFloat(),25f,gold,true);y+=80
                section.entries.forEachIndexed {i,entry ->
                    coroutineContext.ensureActive()
                    val x=24+(i%6)*194;val top=y+(i/6)*310
                    val rect=RectF(x.toFloat(),top.toFloat(),(x+180).toFloat(),(top+248).toFloat())
                    paint.color=AndroidColor.rgb(18,37,58);canvas.drawRoundRect(rect,12f,12f,paint)
                    val ref=DeckInterchange.normalizeId(entry.cardId);val card=byRef[ref]
                    val loaded=try {
                        val url=card?.imageUrl.orEmpty()
                        if(url.isBlank()) null else (Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(OfflineImages.source(context,url)).allowHardware(false).size(350).build()) as? SuccessResult)?.drawable?.toBitmap()
                    } catch(cancelled: CancellationException) {throw cancelled} catch(_: Exception) {null}
                    if(loaded!=null) {
                        val scale=minOf(rect.width()/loaded.width,rect.height()/loaded.height);val w=loaded.width*scale;val h=loaded.height*scale
                        canvas.drawBitmap(loaded,null,RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),paint)
                    } else {missing++;text("Visuel indisponible",x+8f,top+110f,15f);text(ref,x+8f,top+137f,18f,gold)}
                    paint.color=AndroidColor.rgb(255,212,125);canvas.drawRoundRect(RectF(x+3f,top+3f,x+65f,top+40f),8f,8f,paint)
                    text("×${entry.count}",x+10f,top+29f,23f,AndroidColor.rgb(4,13,26),true)
                    paint.textSize=17f;val label=card?.name?:ref
                    val n=paint.breakText(label,true,180f,null)
                    text(label.take(n),x.toFloat(),top+273f,17f)
                    text(label.drop(n).take(paint.breakText(label.drop(n),true,180f,null)),x.toFloat(),top+295f,17f)
                }
                y+=((section.entries.size+5)/6)*310
            }
            text("Liste partagée par son joueur · vérifier les règles du format avant un tournoi",30f,(height-27).toFloat(),17f,gold)
            file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            file to missing
        } catch(error: Throwable) {file.delete();throw error} finally {bitmap.recycle()}
    }
}
@Composable
internal fun DeckImageShareButton(deck: PortableDeck,name: String,catalog: List<CatalogCard>) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)}
    var image by remember {mutableStateOf<File?>(null)};var missing by remember {mutableIntStateOf(0)};var status by remember {mutableStateOf<String?>(null)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri -> if(uri!=null) scope.launch {
        runCatching {withContext(Dispatchers.IO) {val source=image?:error("Image indisponible");context.contentResolver.openOutputStream(uri)?.use {out ->source.inputStream().use {it.copyTo(out)}}?:error("Fichier inaccessible")}}
            .onSuccess {status="Image enregistrée."}.onFailure {if(it is CancellationException) throw it;status="Enregistrement impossible."}
    } }
    OutlinedButton(onClick={if(!busy) scope.launch {busy=true;status=null;try {val result=DeckImageExporter.create(context,name,deck,catalog);image=result.first;missing=result.second} catch(cancelled: CancellationException) {throw cancelled} catch(error: Exception) {status=error.message?:"Image impossible à créer."} finally {busy=false}}},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(if(busy) "Création de l’image…" else "Partager une image du deck")}
    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    status?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
    image?.let {file ->AlertDialog(onDismissRequest={image=null},title={Text("Image de mon deck")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(missing>0) Text("$missing visuel(s) indisponible(s) : noms, références et quantités restent affichés.")
        AsyncImage(file,"Aperçu de l’image du deck",modifier=Modifier.fillMaxWidth().heightIn(min=240.dp,max=350.dp))
        OutlinedButton(onClick={save.launch("SWU-deck.png")}) {Text("Enregistrer l’image")}
        Text("L’application choisie recevra seulement cette image du deck.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={runCatching {
        val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
        val intent=Intent(Intent.ACTION_SEND).apply {type="image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newUri(context.contentResolver,"Mon deck",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        context.startActivity(Intent.createChooser(intent,"Partager mon deck"))
    }.onFailure {status="Aucune application ne peut partager cette image."}}) {Text("Partager")}},dismissButton={TextButton(onClick={image=null}) {Text("Fermer")} })}
}
