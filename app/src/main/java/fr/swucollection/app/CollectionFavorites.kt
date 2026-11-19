package fr.swucollection.app

import android.content.ContentValues

/** Favorites reuse personal lists so existing backups preserve them. */
internal const val FAVORITE_CARDS_LIST = "swu-favorite-cards-v1"
internal fun CollectionDatabase.favoriteCardKeys(): Set<String> =
    cardLists().firstOrNull { it.id == FAVORITE_CARDS_LIST }?.entries.orEmpty()
        .map { MarketPricing.key(it.card.setCode, it.card.number) }.toSet()

internal fun CollectionDatabase.setFavoriteCard(card: CatalogCard, favorite: Boolean) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        if (favorite) db.insertWithOnConflict("card_lists", null, ContentValues().apply {
            put("id", FAVORITE_CARDS_LIST); put("name", "Mes cartes favorites")
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        if (favorite || cardLists().any { it.id == FAVORITE_CARDS_LIST })
            putListCard(FAVORITE_CARDS_LIST, card, if (favorite) 1 else 0)
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
