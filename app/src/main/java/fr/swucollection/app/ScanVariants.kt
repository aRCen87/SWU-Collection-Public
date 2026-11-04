package fr.swucollection.app

import java.util.Locale

internal data class DetectedPrint(val variant: String?, val choices: List<String>, val message: String) {
    fun resolve(fallback: String): String? = variant ?: fallback.takeIf { it in choices }
}

/** Publisher serial identifiers: expansion + treatment + exact collector number.
 * The collector number is never converted to the standard printing's number.
 * See the official card-list variantTypes.variantId / serialCode fields.
 */
internal object ScanVariants {
    val options = listOf("Normale", "Foil", "Hyperspace", "Hyperspace Foil", "Showcase",
        "Prestige", "Prestige Foil", "Serialized", "Sérialisée or rose", "Sérialisée or", "Weekly Play", "Promo")
    private val expansions = mapOf("SOR" to "01", "SHD" to "02", "TWI" to "03", "JTL" to "04",
        "LOF" to "05", "SEC" to "06", "LAW" to "07", "ASH" to "08", "HMW" to "09")
    private val treatments = mapOf("01" to "Normale", "02" to "Hyperspace", "03" to "Showcase",
        "31" to "Foil", "32" to "Hyperspace Foil", "06" to "Prestige", "33" to "Prestige Foil", "34" to "Serialized", "36" to "Sérialisée or rose", "37" to "Sérialisée or")
    private val serial = Regex("^card_(\\d{2})(\\d{2})(\\d{3,4})(?=_|FR|EN|DE|ES|IT)", RegexOption.IGNORE_CASE)

    fun detect(card: CatalogCard): DetectedPrint {
        val code = card.setCode.uppercase(Locale.ROOT)
        val explicit = ScanPrintCatalogue.choices(card)
        if (!explicit.isNullOrEmpty()) {
            if (explicit.size > 1) return DetectedPrint(null, explicit,
                "Ce numéro existe dans plusieurs finitions : confirme celle de ta carte.")
            val only = explicit.single()
            if (code !in listOf("SOR","SHD","TWI") || only !in listOf("Normale","Foil","Hyperspace","Hyperspace Foil"))
                return DetectedPrint(only,listOf(only),"Variante détectée par la référence ${card.setCode} ${card.number}.")
        }
        val expected = expansions[code]
        val file = card.imageUrl.substringAfterLast('/')
        val identifier = serial.find(file)
        var treatment: String? = null
        if (expected != null && identifier != null && identifier.groupValues[1] == expected &&
            identifier.groupValues[3].toIntOrNull() == card.number.toIntOrNull()) {
            treatment = treatments[identifier.groupValues[2]]
        } else if (code == "SOR" && file.startsWith("card_SWH_", true)) {
            // Original SOR file names predate serialCode-based CDN paths.
            val number = Regex("^card_SWH_01(?:fr|en|de|es|it)?_(\\d{3})_", RegexOption.IGNORE_CASE).find(file)
            if (number?.groupValues?.get(1)?.toIntOrNull() == card.number.toIntOrNull()) {
                treatment = when {
                    Regex("_SHO_", RegexOption.IGNORE_CASE).containsMatchIn(file) -> "Showcase"
                    Regex("_HYP_", RegexOption.IGNORE_CASE).containsMatchIn(file) -> "Hyperspace"
                    else -> "Normale"
                }
            }
        }
        if (file.contains("_Carb_C_",true) && treatment in listOf("Serialized","Sérialisée or rose","Sérialisée or"))
            return DetectedPrint(null,listOf("Serialized","Sérialisée or rose","Sérialisée or"),
                "Les finitions sérialisées ont le même numéro : vérifie la bordure et choisis la finition.")
        // In the first three expansions, finish can share a printed collector number.
        // A foil image in the catalogue is not evidence that the physical scanned copy is foil.
        if (code in listOf("SOR", "SHD", "TWI")) {
            val choices = when (treatment) {
                "Normale", "Foil" -> listOf("Normale", "Foil")
                "Hyperspace", "Hyperspace Foil" -> listOf("Hyperspace", "Hyperspace Foil")
                else -> null
            }
            if (choices != null) return DetectedPrint(null, choices,
                "Référence reconnue. Ce numéro peut désigner plusieurs finitions : confirme si ta carte est foil.")
        }
        return if (treatment != null) DetectedPrint(treatment, listOf(treatment),
            "Variante détectée par la référence ${card.setCode} ${card.number}.")
        else DetectedPrint(null, options,
            "Référence reconnue, mais sa variante n’est pas identifiée avec certitude. Choisis-la pour cet ajout.")
    }
}
