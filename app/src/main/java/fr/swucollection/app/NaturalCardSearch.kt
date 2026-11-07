package fr.swucollection.app

/** Small offline vocabulary. Unknown words remain name terms instead of being guessed. */
internal data class NaturalCardSearch(val text: String, val type: String? = null, val rarity: String? = null,
    val aspect: String? = null, val cost: Int? = null, val language: String? = null, val scope: String? = null) {
    fun matches(card: CatalogCard, detail: CardDetail?, owned: List<CollectionCard>, index: GlobalSearchIndex): Boolean {
        if (!index.cardMatches(card, text)) return false
        if (type != null && canonicalType(card) != type) return false
        if (rarity != null && !DeckBrowser.searchKey(card.rarity).startsWith(rarity)) return false
        if (aspect != null && detail?.aspects?.contains(aspect) != true) return false
        if (cost != null && detail?.cost != cost) return false
        val quantity = if (language == null) index.stock(card) else index.stock(card, language)
        return when(scope) { "missing" -> quantity == 0; "doubles" -> quantity > 1; "owned" -> quantity > 0; else -> language == null || quantity > 0 }
    }
    val description get() = listOfNotNull(type, rarity, aspect?.let { aspectNames[it] }, cost?.let { "$it ressources" }, language,
        when(scope) { "missing" -> "non possédées"; "doubles" -> "doublons"; "owned" -> "possédées"; else -> null }).joinToString(" · ")
    companion object {
        private fun canonicalType(card: CatalogCard) = when(val kind=DeckConstruction.type(card)) { "unit" -> "unite"; "event" -> "evenement"; "upgrade" -> "amelioration"; "chef" -> "leader"; else -> kind }
        fun parse(query: String): NaturalCardSearch {
            var text = DeckBrowser.searchKey(query)
            fun take(pattern: String): String? {
                val found = Regex("\\b(?:$pattern)\\b").find(text) ?: return null
                text = text.removeRange(found.range); return found.value
            }
            val cost = Regex("\\b(?:a |cout |cout de )?(\\d{1,2}) ressources?\\b").find(text)?.let {
                text = text.removeRange(it.range); it.groupValues[1].toInt()
            }
            val type = when { take("unites?|units?") != null -> "unite"; take("evenements?|events?") != null -> "evenement";
                take("ameliorations?|upgrades?") != null -> "amelioration"; take("leaders?") != null -> "leader"; take("bases?") != null -> "base"; else -> null }
            val rarity = when { take("legendaires?|legendary") != null -> "legendaire"; take("rares?") != null -> "rare";
                take("peu communes?|uncommons?") != null -> "peu commune"; take("communes?|commons?") != null -> "commune"; else -> null }
            val aspect = when { take("rouges?|agressivite|aggression") != null -> "Aggression"; take("bleues?|bleus?|vigilance") != null -> "Vigilance";
                take("vertes?|verts?|commandement") != null -> "Command"; take("jaunes?|ruse") != null -> "Cunning";
                take("heroiques?|heroisme") != null -> "Heroism"; take("infamie") != null -> "Villainy"; else -> null }
            val language = when { take("anglaises?|anglais|english") != null -> "EN"; take("francaises?|francais|french") != null -> "FR";
                take("allemandes?|allemands?") != null -> "DE"; take("espagnoles?|espagnols?") != null -> "ES"; take("italiennes?|italiens?") != null -> "IT"; else -> null }
            val scope = when { take("manquantes?|manquants?") != null -> "missing"; take("doubles?|doublons?") != null -> "doubles";
                take("possedees?|possedes?|collection") != null -> "owned"; else -> null }
            text = text.replace(Regex("\\b(?:mes|les|des|cartes|carte|de|a)\\b"), " ").trim().replace(Regex("\\s+"), " ")
            return NaturalCardSearch(text,type,rarity,aspect,cost,language,scope)
        }
    }
}
