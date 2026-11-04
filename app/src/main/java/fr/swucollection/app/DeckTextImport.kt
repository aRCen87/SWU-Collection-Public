package fr.swucollection.app

import java.text.Normalizer

/** Name matching is explicit: an ambiguous name never silently selects a different card. */
internal object DeckTextImport {
    private fun nameKey(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase()
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun parse(raw: String, catalog: List<CatalogCard>, details: CardDetails): PortableDeck {
        require(raw.length <= 1_000_000) { "Fichier trop volumineux." }
        val index = mutableMapOf<String, MutableList<CatalogCard>>()
        catalog.forEach { card ->
            listOfNotNull(card.name, details.get(card)?.englishName).forEach { name ->
                index.getOrPut(nameKey(name)) { mutableListOf() }.add(card)
            }
        }
        var zone = ""
        var leader: String? = null
        var base: String? = null
        val main = linkedMapOf<String, Int>()
        val side = linkedMapOf<String, Int>()
        raw.removePrefix("\uFEFF").lineSequence().forEachIndexed { number, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            val heading = nameKey(line)
            if (heading in setOf("leader", "leaders", "base", "deck", "maindeck", "main deck", "sideboard", "reserve")) {
                zone = heading
                return@forEachIndexed
            }
            require(zone.isNotBlank()) { "Ligne ${number + 1} : commence par Leader, Base, Deck ou Sideboard." }
            val row = Regex("^(\\d+)\\s*(?:\\|\\s*|\\s+)(.+)$").matchEntire(line)
                ?: throw IllegalArgumentException("Ligne ${number + 1} : quantité ou nom illisible.")
            val count = row.groupValues[1].toIntOrNull() ?: 0
            require(count in 1..99) { "Ligne ${number + 1} : quantité invalide." }
            val label = row.groupValues[2].trim()
            val id = runCatching { DeckInterchange.normalizeId(label) }.getOrNull() ?: run {
                val matches = index[nameKey(label)].orEmpty().distinctBy(details::family)
                require(matches.size == 1) {
                    "Ligne ${number + 1} : « $label » ${if (matches.isEmpty()) "est introuvable" else "est ambigu"}. Utilise SET-001 ou le nom complet."
                }
                DeckConstruction.reference(matches.single())
            }
            when (zone) {
                "leader", "leaders" -> { require(leader == null && count == 1) { "Un seul leader est pris en charge." }; leader = id }
                "base" -> { require(base == null && count == 1) { "Une seule base est nécessaire." }; base = id }
                else -> {
                    val target = if (zone in setOf("sideboard", "reserve")) side else main
                    val total = (target[id] ?: 0) + count
                    require(total <= 99) { "Trop d’exemplaires pour $id." }
                    target[id] = total
                }
            }
        }
        return PortableDeck(requireNotNull(leader) { "Leader absent." }, requireNotNull(base) { "Base absente." },
            main.map { DeckEntry(it.key, it.value) }, side.map { DeckEntry(it.key, it.value) })
    }
}
