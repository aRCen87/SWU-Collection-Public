package fr.swucollection.app

import java.text.Normalizer
import java.util.Locale

/** Fallback evidence from distinct rules words, never from artwork or phone settings. */
internal object ScanTextLanguage {
    private val vocabulary = mapOf(
        "FR" to "vous votre vos cette adversaire infligez piochez epuisez detruisez bouclier sentinelle quand unite evenement amelioration gagnez jouez",
        "EN" to "you your this opponent damage draw exhaust defeat shielded sentinel when unit event upgrade gains play",
        "DE" to "deine deiner deinen eine einer einen wenn schaden ziehe erschopfe gegner einheit aufwertung ereignis",
        "ES" to "tu tus esta este cuando adversario dano roba agota derrota escudo centinela unidad mejora juega",
        "IT" to "tua tue tuoi questa questo quando avversario danni pesca esaurisci sconfiggi scudo sentinella unita miglioria gioca"
    ).mapValues { (_, words) -> words.split(' ').toSet() }.let { all ->
        // Shared words are not language evidence.
        all.mapValues { (language, words) -> words.filter { word -> all.none { it.key != language && word in it.value } }.toSet() }
    }
    fun detect(raw: String): String? {
        val words = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).split(Regex("[^a-z]+")).toSet()
        val scores = vocabulary.map { (language, tokens) -> language to tokens.count { it in words } }.sortedByDescending { it.second }
        val best = scores.first()
        return best.first.takeIf { best.second >= 3 && best.second - scores[1].second >= 2 }
    }
}
