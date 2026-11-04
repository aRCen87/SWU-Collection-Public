package fr.swucollection.app

import java.util.Locale

enum class PremierStatus { OUT_OF_ROTATION, SUSPENDED }

/** Rules are downloaded independently of application updates.
 * Reprints are matched by the complete title (including subtitle) AND card type.
 * Promotional/supplementary products do not make a card Premier-legal by themselves.
 */
class PremierLegality(catalog: List<CatalogCard>, rules: LegalityRules = LegalityRules.DEFAULT) {
    private val activeSets = rules.activeSets
    private val rotatedSets = rules.rotatedSets
    private val byReference = catalog.associateBy { reference(it.setCode, it.number) }
    private val activeIdentities = catalog.filter { it.setCode.uppercase(Locale.ROOT) in activeSets }
        .map { identity(it) }.toSet() + rules.legalCards.map { normalize(it.type) to normalize(it.name) }
    private val rotatedIdentities = catalog.filter { it.setCode.uppercase(Locale.ROOT) in rotatedSets }
        .map { identity(it) }.toSet()
    private val suspendedIdentities = rules.suspended.map { normalize(it.type) to normalize(it.name) }.toSet()
    private val suspendedReferences = rules.suspended.map { reference(it.setCode, it.number) }.toSet()

    fun status(card: CatalogCard): PremierStatus? {
        val id = identity(card)
        if (id in suspendedIdentities || reference(card.setCode, card.number) in suspendedReferences) {
            return PremierStatus.SUSPENDED
        }
        if (id in activeIdentities) return null
        if (card.setCode.uppercase(Locale.ROOT) in rotatedSets || id in rotatedIdentities) {
            return PremierStatus.OUT_OF_ROTATION
        }
        // Unknown cards/products are not labelled as illegal merely because of their set code.
        return null
    }

    fun status(card: CollectionCard): PremierStatus? =
        byReference[reference(card.setCode, card.number)]?.let { status(it) }

    private fun identity(card: CatalogCard) = normalize(card.type) to normalize(card.name)

    private fun reference(set: String, number: String) =
        set.trim().uppercase(Locale.ROOT) + ":" + (number.trim().toIntOrNull()?.toString() ?: number.trim())

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
        .replace('’', '\'').replace('–', '—').replace(Regex("\\s+"), " ")
}
