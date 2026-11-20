package fr.swucollection.app

internal data class PrintCard(val card: CatalogCard, val quantity: Int, val section: String = "", val back: Boolean = false)
internal data class PdfPlacement(val item: PrintCard, val x: Float, val y: Float, val width: Float, val height: Float)
internal object CardPdfPlan {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MAX_CARDS = 360
    fun mm(value: Float) = value * 72f / 25.4f
    fun fromDeck(deck: PortableDeck, catalog: List<CatalogCard>): List<PrintCard> {
        val byId = catalog.associateBy(DeckConstruction::reference)
        fun item(id: String, count: Int, section: String) = PrintCard(
            requireNotNull(byId[id]) { "La référence $id est inconnue. Actualise le catalogue avant l’export." }, count, section)
        return listOf(item(deck.leader, 1, "Leader"), item(deck.base, 1, "Base")) +
            deck.deck.map { item(it.cardId, it.count, "Deck") } + deck.sideboard.map { item(it.cardId, it.count, "Réserve") }
    }
    fun automaticLeaderBack(card: CatalogCard) =
        DeckConstruction.type(card) in setOf("leader", "chef") && card.backImageUrl.isNotBlank()
    private fun includeBack(item: PrintCard, backs: Boolean) =
        !item.back && item.card.backImageUrl.isNotBlank() && (backs || automaticLeaderBack(item.card))
    fun pages(items: List<PrintCard>, proxies: Boolean, backs: Boolean, large: Boolean = false): List<List<PdfPlacement>> {
        require(items.isNotEmpty()) { "Choisis au moins une carte." }
        require(items.all { it.quantity in 1..99 }) { "Quantité invalide." }
        val requested = if (proxies) items.sumOf { it.quantity.toLong() * if (includeBack(it, backs)) 2 else 1 } else items.size.toLong()
        require(requested <= MAX_CARDS) { "Choisis au maximum $MAX_CARDS cartes ou faces par PDF." }
        val expanded = if (proxies) items.flatMap { item ->
            List(item.quantity) { item.copy(quantity = 1) } +
                if (includeBack(item, backs)) List(item.quantity) { item.copy(quantity = 1, back = true) } else emptyList()
        } else items
        require(expanded.size <= MAX_CARDS) { "Choisis au maximum $MAX_CARDS cartes ou faces par PDF." }
        val width = if (proxies) mm(if (large) 63.5f else 63f) else 155f
        val height = if (proxies) mm(if (large) 88.9f else 88f) else 217f
        val gapX = if (proxies) mm(2f) else 27f
        val gapY = if (proxies) mm(2f) else 28f
        val startX = (PAGE_WIDTH - (3 * width + 2 * gapX)) / 2
        val startY = if (proxies) (PAGE_HEIGHT - (3 * height + 2 * gapY)) / 2 else 60f
        return expanded.chunked(9).map { page -> page.mapIndexed { i, item ->
            PdfPlacement(item, startX + (i % 3) * (width + gapX), startY + (i / 3) * (height + gapY), width, height)
        } }
    }
}
