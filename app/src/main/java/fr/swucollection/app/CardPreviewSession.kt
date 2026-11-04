package fr.swucollection.app

import java.util.Locale

/** Nullable variant lets catalogue browsing retain the user's chosen finish. */
data class CardPreviewEntry(val card: CatalogCard, val variant: String? = null, val language: String = card.language)

/** A snapshot of the visible, filtered list: collection edits do not move the next card. */
data class CardPreviewSession private constructor(
    val entries: List<CardPreviewEntry>,
    val index: Int
) {
    val current: CardPreviewEntry get() = entries[index]
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < entries.lastIndex

    fun move(delta: Int): CardPreviewSession =
        copy(index = (index + delta).coerceIn(0, entries.lastIndex))

    companion object {
        private fun key(entry: CardPreviewEntry) = listOf(
            entry.card.setCode.uppercase(Locale.ROOT),
            entry.card.number.toIntOrNull()?.toString() ?: entry.card.number,
            entry.variant, entry.language)

        fun open(clicked: CardPreviewEntry, visible: List<CardPreviewEntry>): CardPreviewSession {
            val entries = visible.distinctBy { key(it) }
            val index = entries.indexOfFirst { key(it) == key(clicked) }
            return if (index >= 0) CardPreviewSession(entries, index)
                else CardPreviewSession(listOf(clicked), 0)
        }
    }
}
