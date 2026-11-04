package fr.swucollection.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.graphics.drawscope.clipRect

internal val badgeGold = Color(0xFFE9C77F)
internal val badgeSilver = Color(0xFFB9C7D7)
internal val badgeNavy = Color(0xFF071423)
internal val badgeTierNames = listOf("Rencontre", "Alliance", "Épopée", "Saga", "Légende")
private fun pinPath(w: Float, h: Float) = Path().apply {
    moveTo(w*.18f,h*.03f);lineTo(w*.82f,h*.03f);lineTo(w*.96f,h*.17f)
    lineTo(w*.96f,h*.69f);lineTo(w*.86f,h*.86f);lineTo(w*.65f,h*.96f)
    lineTo(w*.35f,h*.96f);lineTo(w*.14f,h*.86f);lineTo(w*.04f,h*.69f)
    lineTo(w*.04f,h*.17f);close()
}
private object BadgePinShape: Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(pinPath(size.width,size.height))
}
/** Sculpted, layered collectible pin; every portrait is cropped from its catalogue card. */
@Composable
internal fun BadgePin(badge: BadgeDefinition, catalog: List<CatalogCard>, modifier: Modifier = Modifier, locked: Boolean = false, shine: Float? = null) {
    val art = remember(badge.id, catalog) { badge.art.mapNotNull { id -> catalog.firstOrNull { DeckConstruction.reference(it)==id } }.take(3) }
    val metal = if(badge.tier >= 2) badgeGold else badgeSilver
    val darkMetal = if(badge.tier >= 2) Color(0xFF69501E) else Color(0xFF424C59)
    val lightMetal = if(badge.tier >= 2) Color(0xFFFFEDB5) else Color(0xFFF5F6FB)
    val cyan = Color(0xFF4BD3FF)
    BoxWithConstraints(modifier.aspectRatio(.78f)) {
        val inset = maxWidth*.105f
        val verticalInset = maxHeight*.09f
        val artBottom = maxHeight*.13f
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(metal.copy(alpha=if(locked).08f else .20f),Color.Transparent)),radius=size.minDimension*.62f,center=center)
            withTransform({translate(0f,size.height*.015f)}) {drawPath(pinPath(size.width,size.height),Color.Black.copy(alpha=.85f))}
            drawPath(pinPath(size.width,size.height),darkMetal)
        }
        Box(Modifier.fillMaxSize().padding(horizontal=inset,vertical=verticalInset).clip(BadgePinShape).background(badgeNavy)) {
            if(art.isEmpty()) Icon(Icons.Default.AutoAwesome,null,tint=metal,modifier=Modifier.align(Alignment.Center).size(56.dp))
            else Row(Modifier.fillMaxSize().padding(bottom=artBottom)) {
                art.forEach { card ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RectangleShape)) {
                        BadgeIllustration(card, locked, Modifier.fillMaxSize())
                    }
                }
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha=.1f),.55f to Color.Transparent,1f to Color(0xFF02070D))))
            if(locked)Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.12f)))
        }
        Canvas(Modifier.matchParentSize()) {
            val w=size.width;val h=size.height;val center=Offset(w/2,h/2)
            val bright=Brush.linearGradient(listOf(darkMetal,lightMetal,metal,darkMetal,metal,lightMetal,darkMetal),Offset.Zero,Offset(w,h))
            val edge=Brush.linearGradient(listOf(lightMetal,darkMetal,lightMetal,metal,darkMetal),Offset(w,0f),Offset(0f,h))
            val rim=pinPath(w,h)
            drawPath(rim,bright,style=Stroke(w*.045f))
            drawPath(rim,lightMetal.copy(alpha=.85f),style=Stroke(w*.006f))
            withTransform({scale(.93f,.93f,center)}) {drawPath(rim,Color(0xFF10151D),style=Stroke(w*.022f));drawPath(rim,edge,style=Stroke(w*.010f))}
            withTransform({scale(.86f,.86f,center)}) {drawPath(rim,bright,style=Stroke(w*.022f));drawPath(rim,lightMetal.copy(alpha=.8f),style=Stroke(w*.004f))}
            fun polygon(vararg coords: Float): Path = Path().apply {
                coords.toList().chunked(2).forEachIndexed { i,p -> if(i==0)moveTo(w*p[0],h*p[1])else lineTo(w*p[0],h*p[1]) };close()
            }
            // Stacked shoulder fins cast dark edges against the artwork frame.
            for(side in listOf(-1f,1f)) withTransform({scale(side,1f,center)}) {
                for(i in 0 until (if(badge.tier>=3)3 else 2)) {
                    val y=.20f+i*.062f
                    val fin=polygon(.045f,y,.0f,y-.025f,.015f,y+.045f,.075f,y+.078f,.10f,y+.038f)
                    drawPath(fin,bright);drawPath(fin,lightMetal.copy(alpha=.8f),style=Stroke(w*.004f))
                }
                val panel=polygon(.016f,.70f,.18f,.675f,.25f,.72f,.23f,.83f,.08f,.855f,.015f,.81f)
                drawPath(panel,bright);drawPath(panel,lightMetal,style=Stroke(w*.008f))
                withTransform({scale(.96f,.96f,Offset(w*.12f,h*.77f))}){drawPath(panel,darkMetal.copy(alpha=.5f),style=Stroke(w*.005f))}
                drawLine(cyan.copy(alpha=if(locked).35f else .9f),Offset(w*.077f,h*.40f),Offset(w*.077f,h*.62f),w*.013f)
                drawLine(Color.White.copy(alpha=.65f),Offset(w*.077f,h*.40f),Offset(w*.077f,h*.62f),w*.003f)
            }
            // Raised enamel plate with a bevel and a small engraved title.
            val plate=polygon(.30f,.685f,.70f,.685f,.82f,.74f,.77f,.875f,.64f,.925f,.36f,.925f,.23f,.875f,.18f,.74f)
            drawPath(plate,bright)
            withTransform({scale(.94f,.94f,Offset(w*.5f,h*.805f))}){drawPath(plate,Color(0xFF04080D));drawPath(plate,edge,style=Stroke(w*.010f))}
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=lightMetal.toArgb();typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
                textAlign=android.graphics.Paint.Align.CENTER;textSize=w*.049f
                setShadowLayer(w*.004f,0f,w*.004f,android.graphics.Color.BLACK)
            }
            val words=badge.title.uppercase(java.util.Locale.FRANCE).split(" ")
            var lines=mutableListOf<String>();var line=""
            for(word in words){val next=if(line.isEmpty())word else "$line $word";if(paint.measureText(next)>w*.48f && line.isNotEmpty()){lines.add(line);line=word}else line=next}
            if(line.isNotEmpty())lines.add(line)
            if(lines.size>3){paint.textSize=w*.037f;lines=mutableListOf();line="";for(word in words){val next=if(line.isEmpty())word else "$line $word";if(paint.measureText(next)>w*.48f && line.isNotEmpty()){lines.add(line);line=word}else line=next};if(line.isNotEmpty())lines.add(line)}
            val titleY=h*.748f-(lines.size-2).coerceAtLeast(0)*h*.013f
            lines.take(3).forEachIndexed{i,text ->drawContext.canvas.nativeCanvas.drawText(text,w*.5f,titleY+i*h*.037f,paint)}
            drawLine(metal.copy(alpha=.8f),Offset(w*.32f,h*.839f),Offset(w*.68f,h*.839f),w*.004f)
            paint.textSize=w*.032f;paint.typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
            drawContext.canvas.nativeCanvas.drawText(badgeTierNames[badge.tier].uppercase(java.util.Locale.FRANCE),w*.5f,h*.874f,paint)
            // Star ranks and a small family symbol in the shoulder plates.
            for(i in 0..badge.tier) {
                val x=w*.82f+(i-badge.tier/2f)*w*.023f
                val star=Path().apply{moveTo(x,h*.747f);lineTo(x+w*.008f,h*.767f);lineTo(x+w*.018f,h*.773f);lineTo(x+w*.008f,h*.779f);lineTo(x,h*.799f);lineTo(x-w*.008f,h*.779f);lineTo(x-w*.018f,h*.773f);lineTo(x-w*.008f,h*.767f);close()}
                drawPath(star,Color(0xFF17202A))
            }
            val symbolPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=Color(0xFF17202A).toArgb();textSize=w*.09f;textAlign=android.graphics.Paint.Align.CENTER;typeface=android.graphics.Typeface.DEFAULT_BOLD}
            val symbol=when(badge.category){"Sagas"->"✦";"Application"->"◆";"Collection","Prestige"->"▣";else->"✧"}
            drawContext.canvas.nativeCanvas.drawText(symbol,w*.12f,h*.80f,symbolPaint)
            // Higher difficulties add a raised crest and a blue inset crystal.
            if(badge.tier>=1) {
                val crest=if(badge.tier>=3)polygon(.33f,.055f,.42f,.055f,.5f,0f,.58f,.055f,.67f,.055f,.61f,.12f,.39f,.12f)
                    else polygon(.35f,.025f,.65f,.025f,.61f,.085f,.39f,.085f)
                drawPath(crest,bright);drawPath(crest,lightMetal,style=Stroke(w*.009f))
                if(badge.tier>=2){val gem=polygon(.50f,.014f,.54f,.062f,.50f,.11f,.46f,.062f);drawPath(gem,Brush.linearGradient(listOf(Color.White,cyan,Color(0xFF03619C))));drawPath(gem,lightMetal,style=Stroke(w*.005f))}
            } else drawLine(cyan,Offset(w*.40f,h*.052f),Offset(w*.60f,h*.052f),w*.012f)
            val bottom=polygon(.40f,.945f,.60f,.945f,.50f,.995f)
            drawPath(bottom,bright);drawPath(bottom,lightMetal,style=Stroke(w*.006f))
            drawLine(cyan.copy(alpha=if(locked).35f else .9f),Offset(w*.43f,h*.957f),Offset(w*.57f,h*.957f),w*.009f)
            if(shine != null && shine > 0f && shine < 1f) clipPath(rim) {
                val x=-w*.6f+shine*w*2.2f
                drawRect(Brush.linearGradient(listOf(Color.Transparent,lightMetal.copy(alpha=.45f),Color.Transparent),Offset(x-w*.18f,0f),Offset(x+w*.18f,h)))
            }
        }
    }
}

/** Load the full face; crop in source coordinates before fitting it into a pin segment. */
@Composable
private fun BadgeIllustration(card: CatalogCard, locked: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val model = remember(context, card.imageUrl) {
        ImageRequest.Builder(context).data(OfflineImages.source(context, card.imageUrl))
            .size(900).build()
    }
    val painter = rememberAsyncImagePainter(model)
    val ready = painter.state is AsyncImagePainter.State.Success
    val filter = remember(locked) {
        if (locked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.5f) }) else null
    }
    Canvas(modifier) {
        if (!ready) {
            // AsyncImagePainter needs a bounded draw even while loading. The full card stays hidden.
            clipRect(0f,0f,0f,0f) { with(painter) { draw(size) } }
        }
        val original = painter.intrinsicSize
        if (ready && original.width > 0f && original.height > 0f &&
            original.width.isFinite() && original.height.isFinite() && size.height > 0f && size.width > 0f) {
            val source = BadgeArtCrop.illustration(DeckConstruction.reference(card), card.type,
                original.width, original.height).fit(size.width / size.height)
            clipRect {
                withTransform({
                    scale(size.width / source.width, size.height / source.height, Offset.Zero)
                    translate(-source.left, -source.top)
                }) {
                    with(painter) { draw(original, colorFilter = filter) }
                }
            }
        }
    }
}

@Composable
internal fun HomeBadgeEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width*.55f;val h=size.height*.92f
        withTransform({translate(size.width*.35f,size.height*.04f)}) {
            val frame=pinPath(w,h)
            drawPath(frame,Brush.linearGradient(listOf(badgeSilver,badgeGold,Color(0xFF665133),badgeGold)))
            withTransform({scale(.86f,.86f,Offset(w/2,h/2))}) {drawPath(frame,Color(0xFF071524))}
            drawPath(frame,badgeGold,style=Stroke(1.8.dp.toPx()))
            val star=Path()
            for(i in 0 until 10) {
                val angle=-Math.PI/2+i*Math.PI/5
                val r=if(i%2==0)h*.26f else h*.115f
                val x=w*.5f+kotlin.math.cos(angle).toFloat()*r
                val y=h*.43f+kotlin.math.sin(angle).toFloat()*r
                if(i==0)star.moveTo(x,y) else star.lineTo(x,y)
            }
            star.close();drawPath(star,Brush.linearGradient(listOf(Color(0xFFFFEFC4),badgeGold,Color(0xFFAA7833))))
            drawLine(badgeGold,Offset(w*.32f,h*.79f),Offset(w*.68f,h*.79f),3.dp.toPx())
        }
    }
}
