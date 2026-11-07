package fr.swucollection.app

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.random.Random

internal data class HomeFilmQuote(val text: String, val speaker: String, val film: String) {
    val displayText get() = "« $text »"
    val credit get() = "$speaker · $film"
}

// Short film excerpts; French licensed wording for Yoda and Luke:
// lego.com/fr-fr/product/dagobah-jedi-training-diorama-75330
// lego.com/fr-fr/product/emperors-throne-room-diorama-75352
// Additional French excerpts checked against film transcriptions and contemporary film coverage.
internal val homeFilmQuotes = listOf(
    HomeFilmQuote("Que la Force soit avec toi.", "Han Solo", "Un nouvel espoir"),
    HomeFilmQuote("Fais-le ou ne le fais pas, mais il n’y a pas d’essai.", "Yoda", "L’Empire contre-attaque"),
    HomeFilmQuote("Je suis un Jedi, comme mon père l’a été avant moi.", "Luke Skywalker", "Le Retour du Jedi"),
    HomeFilmQuote("Au secours Obi-Wan Kenobi, vous êtes mon seul espoir.", "Leia Organa", "Un nouvel espoir"),
    HomeFilmQuote("Non, je suis ton père.", "Dark Vador", "L’Empire contre-attaque"),
    HomeFilmQuote("Il y a toujours un plus gros poisson.", "Qui-Gon Jinn", "La Menace fantôme"),
    HomeFilmQuote("L’espoir est à la base de toutes les rébellions.", "Jyn Erso", "Rogue One"),
    HomeFilmQuote("Seuls les Sith sont aussi absolus.", "Obi-Wan Kenobi", "La Revanche des Sith"),
    HomeFilmQuote("Le plus grand maître, l’échec est.", "Yoda", "Les Derniers Jedi"),
    HomeFilmQuote("C’est un piège !", "Amiral Ackbar", "Le Retour du Jedi"),
    HomeFilmQuote("La peur est le chemin du côté obscur.", "Yoda", "La Menace fantôme"),
    HomeFilmQuote("J’ai un très bon pressentiment.", "Han Solo", "Solo: A Star Wars Story"),
    HomeFilmQuote("Beaucoup encore il te reste à apprendre.", "Yoda", "L’Attaque des clones"),
    HomeFilmQuote("Je sais.", "Han Solo", "L’Empire contre-attaque"),
    HomeFilmQuote("Ainsi s’éteint la liberté, sous une pluie d’applaudissements.", "Padmé Amidala", "La Revanche des Sith"),
    HomeFilmQuote("La Force est avec moi, je fais corps avec la Force.", "Chirrut Îmwe", "Rogue One"),
    HomeFilmQuote("Tu avais raison à mon sujet.", "Anakin Skywalker", "Le Retour du Jedi"),
    HomeFilmQuote("Dieu me débranche !", "C-3PO", "L’Attaque des clones"),
    HomeFilmQuote("Tu étais l’Élu, c’était toi !", "Obi-Wan Kenobi", "La Revanche des Sith"),
    HomeFilmQuote("Je t’aime.", "Leia Organa", "L’Empire contre-attaque"),
    HomeFilmQuote("Je ne suis personne.", "Rey", "Le Réveil de la Force"),
    HomeFilmQuote("Chewie, nous sommes chez nous.", "Han Solo", "Le Réveil de la Force"),
    HomeFilmQuote("Personne n’est jamais vraiment parti.", "Luke Skywalker", "Les Derniers Jedi"),
    HomeFilmQuote("En sauvant ceux qu’on aime.", "Rose Tico", "Les Derniers Jedi"),
    HomeFilmQuote("Nous sommes l’étincelle qui allumera l’incendie.", "Poe Dameron", "Les Derniers Jedi"),
    HomeFilmQuote("Je suis tous les Jedi.", "Rey", "L’Ascension de Skywalker"),
    HomeFilmQuote("Je suis tous les Sith.", "Palpatine", "L’Ascension de Skywalker"),
    HomeFilmQuote("Ce n’est pas parce que tu parles que tu es intelligent.", "Qui-Gon Jinn", "La Menace fantôme"),
    HomeFilmQuote("J’ai un mauvais pressentiment.", "Obi-Wan Kenobi", "La Menace fantôme"),
    HomeFilmQuote("Je n’aime pas le sable.", "Anakin Skywalker", "L’Attaque des clones")
)

@Composable
internal fun rememberHomeFilmQuote(active: Boolean): State<Int> {
    val index = rememberSaveable { mutableStateOf(Random.nextInt(homeFilmQuotes.size)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        if (active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(20_000L)
                // A nonzero random offset gives every other quote the same chance.
                index.value = (index.value + Random.nextInt(1, homeFilmQuotes.size)) % homeFilmQuotes.size
            }
        }
    }
    return index
}

@Composable
internal fun HomeFilmQuoteCard(index: State<Int>, modifier: Modifier = Modifier) {
    val quoteStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
    val creditStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF07182A),
        border = BorderStroke(1.dp, Color(0xFFE6C17A).copy(alpha = 0.4f))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Reserve the tallest excerpt and credit at the current text size so the home buttons stay still.
            val quoteHeight = remember(measurer, constraints.maxWidth, quoteStyle, density) {
                homeFilmQuotes.maxOf { measurer.measure(AnnotatedString(it.displayText),
                    style = quoteStyle, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height }
            }
            val creditHeight = remember(measurer, constraints.maxWidth, creditStyle, density) {
                homeFilmQuotes.maxOf { measurer.measure(AnnotatedString(it.credit),
                    style = creditStyle, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height }
            }
            Crossfade(targetState = index.value.mod(homeFilmQuotes.size),
                animationSpec = tween(if (ValueAnimator.areAnimatorsEnabled()) 400 else 0),
                label = "Citation des films") { current ->
                val quote = homeFilmQuotes[current]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(with(density) { quoteHeight.toDp() }), contentAlignment = Alignment.Center) {
                        Text(quote.displayText, Modifier.fillMaxWidth(), color = Color(0xFFF3DCAA), style = quoteStyle)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(with(density) { creditHeight.toDp() }), contentAlignment = Alignment.Center) {
                        Text(quote.credit, Modifier.fillMaxWidth(), color = Color(0xFFAEBED2), style = creditStyle)
                    }
                }
            }
        }
    }
}
