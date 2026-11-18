package fr.swucollection.app

/** Landscape faces must not reserve the height of a portrait card in a collection row. */
internal fun collectionArtworkRatio(card: CatalogCard?): Float =
    if (card != null && DeckConstruction.type(card) in setOf("leader", "chef", "base")) 1.4f else 5f / 7f
