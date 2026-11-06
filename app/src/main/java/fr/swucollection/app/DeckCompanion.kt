package fr.swucollection.app

import android.content.Context
import org.json.JSONArray

/** Display preferences stay independent of collection quantities and saved deck payloads. */
internal class DeckPins(context: Context) {
    private val prefs = context.getSharedPreferences("deck_home_pins", Context.MODE_PRIVATE)
    fun all(): List<String> = runCatching {
        val values = JSONArray(prefs.getString("ids", "[]"))
        (0 until values.length()).map { values.getString(it) }.distinct().take(3)
    }.getOrDefault(emptyList())
    fun restore(ids: List<String>, saved: Set<String>) {
        require(ids.size <= 3)
        check(prefs.edit().putString("ids", JSONArray(ids.distinct().filter { it in saved }).toString()).commit())
    }
    fun toggle(id: String): List<String> {
        val current = all()
        val next = if (id in current) current - id else {
            require(current.size < 3) { "Trois decks sont déjà épinglés. Retire une étoile pour en choisir un autre." }
            current + id
        }
        check(prefs.edit().putString("ids", JSONArray(next).toString()).commit()) { "Épinglage impossible. Réessaie." }
        return next
    }
    fun retain(ids: Set<String>): List<String> {
        val next = all().filter { it in ids }
        check(prefs.edit().putString("ids", JSONArray(next).toString()).commit())
        return next
    }
}

internal object DeckCompanion {
    /** Keep the selected reference when starting a new deck. */
    fun seed(deck: PortableDeck, card: CatalogCard): PortableDeck = when (DeckConstruction.type(card)) {
        "leader", "chef" -> deck.copy(leader = DeckConstruction.reference(card))
        "base" -> deck.copy(base = DeckConstruction.reference(card))
        else -> {
            require(DeckConstruction.isDraw(card)) { "Ce type de carte ne peut pas être ajouté au deck." }
            DeckEditor.setQuantity(deck, DeckConstruction.reference(card), 1, DeckEditor.Zone.MAIN)
        }
    }

    fun rows(deck: PortableDeck) = listOf(DeckEntry(deck.leader, 1), DeckEntry(deck.base, 1)) + deck.deck + deck.sideboard
    fun usages(card: CatalogCard, decks: List<DeckStore.SavedDeck>, catalog: List<CatalogCard>, details: CardDetails): List<Pair<DeckStore.SavedDeck, Int>> {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val family = details.family(card)
        return decks.mapNotNull { saved ->
            val count = rows(saved.deck).filter { row -> byId[row.cardId]?.let { details.family(it) == family } == true }.sumOf { it.count }
            if (count > 0) saved to count else null
        }
    }
    fun stock(card: CatalogCard, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails): Int {
        val byId = catalog.associateBy(DeckConstruction::reference)
        return owned.filter { row -> DeckConstruction.stockReference(row.setCode, row.number)?.let { byId[it] }?.let { details.family(it) == details.family(card) } == true }.sumOf { it.quantity }
    }
    fun add(deck: PortableDeck, card: CatalogCard, catalog: List<CatalogCard>, details: CardDetails, zone: DeckEditor.Zone): PortableDeck {
        val id = DeckConstruction.reference(card)
        return when (DeckConstruction.type(card)) {
            "leader", "chef" -> deck.copy(leader = id)
            "base" -> deck.copy(base = id)
            else -> {
                require(DeckConstruction.isDraw(card)) { "Ce type de carte ne peut pas être ajouté au deck." }
                val byId = catalog.associateBy(DeckConstruction::reference)
                val count = (deck.deck + deck.sideboard).filter { byId[it.cardId]?.let { c -> details.family(c) == details.family(card) } == true }.sumOf { it.count }
                require(count < 3) { "Ce deck utilise déjà trois exemplaires, versions et réserve comprises." }
                require(zone != DeckEditor.Zone.SIDEBOARD || deck.sideboard.sumOf { it.count } < 10) { "La réserve contient déjà dix cartes." }
                DeckEditor.setQuantity(deck, id, DeckEditor.quantity(deck, id, zone) + 1, zone)
            }
        }
    }
    fun shopping(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails): String {
        val missing = DeckInsights.missingPrints(deck, owned, catalog, details)
        return if (missing.isEmpty()) "Aucune carte manquante." else missing.joinToString("\n") {
            "${it.quantity} × ${it.card.name} · ${it.card.setCode} ${it.card.number}"
        }
    }
}
