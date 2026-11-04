package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal data class BadgeRequirement(val label: String, val refs: List<String>, val names: List<String>)
internal data class BadgeDefinition(val id: String, val title: String, val category: String, val chapter: String,
    val story: String, val tier: Int, val requirements: List<BadgeRequirement>, val metric: String,
    val target: Int, val prerequisites: List<String>, val art: List<String>)
internal data class BadgeCheck(val label: String, val complete: Boolean, val candidates: List<CatalogCard>, val available: Boolean = true)
internal data class BadgeAward(val id: String, val time: Long, val seen: Boolean, val evidence: List<String>)
internal data class BadgeProgress(val badge: BadgeDefinition, val current: Int, val target: Int,
    val available: Boolean, val checks: List<BadgeCheck>, val evidence: List<String>, val award: BadgeAward? = null) {
    val earned get() = award != null
    val fraction get() = if (earned) 1f else (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
}
internal data class BadgeCatalogue(val definitions: List<BadgeDefinition>, val classicSets: Map<String, List<String>>) {
    companion object {
        fun load(context: Context) = context.assets.open("achievements.json").bufferedReader().use { parse(it.readText()) }
        fun parse(json: String): BadgeCatalogue {
            val root = JSONObject(json)
            require(root.getInt("version") == 1)
            val rows = root.getJSONArray("definitions")
            val definitions = (0 until rows.length()).map { i -> rows.getJSONObject(i).let { d ->
                val requirements = d.getJSONArray("requirements")
                BadgeDefinition(d.getString("id"), d.getString("title"), d.getString("category"), d.getString("chapter"),
                    d.getString("story"), d.getInt("tier"), (0 until requirements.length()).map { j ->
                        requirements.getJSONObject(j).let { BadgeRequirement(it.getString("label"), it.badgeStrings("refs"), it.badgeStrings("names")) }
                    }, d.getString("metric"), d.getInt("target"), d.badgeStrings("prerequisites"), d.badgeStrings("art"))
            } }
            require(definitions.map { it.id }.distinct().size == definitions.size)
            require(definitions.all { d -> d.target > 0 && d.tier in 0..4 && d.prerequisites.all { id -> definitions.any { it.id == id && it.prerequisites.isEmpty() } } })
            val sets = root.getJSONObject("classicSets")
            return BadgeCatalogue(definitions, sets.keys().asSequence().associateWith { key -> sets.badgeStrings(key) })
        }
    }
}
internal fun JSONObject.badgeStrings(key: String): List<String> = getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
internal fun badgeNormalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('’', '\'').trim()
internal fun badgeFingerprint(value: String) = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Pure, read-only rules. Edition/finish differences cannot replace a missing character. */
internal object BadgeEngine {
    fun evaluate(book: BadgeCatalogue, catalog: List<CatalogCard>, owned: List<CollectionCard>, details: CardDetails,
        events: Map<String, Long>, awards: Map<String, BadgeAward>): List<BadgeProgress> {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val ownedCards = owned.filter { it.quantity > 0 }.mapNotNull { row ->
            DeckConstruction.stockReference(row.setCode, row.number)?.let { byId[it] }?.let { it to row }
        }
        val families = ownedCards.map { details.family(it.first) }.toSet()
        val verifiedFamilyCards = ownedCards.filter { details.get(it.first) != null }
        val verifiedFamilies = verifiedFamilyCards.map { details.family(it.first) }.toSet()
        val ownedIds = ownedCards.map { DeckConstruction.reference(it.first) }.toSet()
        val byName = catalog.groupBy { badgeNormalize(it.name.substringBefore(" — ")) }
        val byFamily = catalog.groupBy(details::family)
        val classicStock = owned.filter { it.variant == "Normale" && it.quantity > 0 }.groupBy {
            DeckConstruction.stockReference(it.setCode, it.number)
        }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        val setChecks = book.classicSets.map { (set, refs) ->
            val recognized = refs.mapNotNull { byId[it] }
            val ready = recognized.size == refs.size && refs.isNotEmpty()
            val one = ready && refs.all { (classicStock[it] ?: 0) >= 1 }
            val play = ready && recognized.all { (classicStock[DeckConstruction.reference(it)] ?: 0) >= CollectionPlan.target(it, true) }
            val ninety = ready && refs.count { (classicStock[it] ?: 0) >= 1 }.toFloat() / refs.size >= .9f
            Triple(set, listOf(one, play, ninety), ready)
        }
        val results = mutableListOf<BadgeProgress>()
        val familyCapacity = catalog.filter { details.get(it) != null }.map(details::family).distinct().size
        val scans = events["scan"] ?: 0L
        val uniqueScans = events["scan_unique"] ?: 0L
        fun count(key: String) = events[key] ?: 0L
        for (d in book.definitions) {
            val checks = d.requirements.map { requirement ->
                val names = requirement.names.map(::badgeNormalize).toSet()
                val pinnedCards = requirement.refs.mapNotNull { byId[it] }
                val pinned = pinnedCards.map(details::family).toSet()
                val pinnedNames = pinnedCards.flatMap { card -> listOf(card.name.substringBefore(" — "),
                    details.get(card)?.englishName?.substringBefore(" — ").orEmpty()) }.map(::badgeNormalize).filter { it.isNotEmpty() }.toSet()
                val accepted = if(names.isNotEmpty()) names.flatMap { byName[it].orEmpty() }
                    .distinctBy(DeckConstruction::reference)
                else pinned.flatMap { byFamily[it].orEmpty() }.filter { card ->
                    badgeNormalize(card.name.substringBefore(" — ")) in pinnedNames
                }
                val acceptedIds = accepted.map(DeckConstruction::reference).toSet()
                val complete = acceptedIds.any { it in ownedIds }
                val candidates = accepted.distinctBy(details::family)
                BadgeCheck(requirement.label,complete,candidates,candidates.isNotEmpty())
            }
            var available = checks.all { it.available }
            var evidence = checks.filter { it.complete }.mapNotNull { check -> check.candidates.firstOrNull { details.family(it) in families }?.let(DeckConstruction::reference) }
            var rows = checks
            var n = checks.count { it.complete }.toLong()
            if (d.prerequisites.isNotEmpty()) {
                rows = d.prerequisites.map { id ->
                    val previous = results.first { it.badge.id == id }
                    val done = previous.earned || (previous.available && previous.current >= previous.target)
                    BadgeCheck(previous.badge.title, done, previous.badge.art.mapNotNull { byId[it] }, previous.available || previous.earned)
                }
                available = rows.all { it.available }
                n = rows.count { it.complete }.toLong()
                evidence = results.filter { it.badge.id in d.prerequisites }.flatMap { it.evidence }.distinct().take(30)
            }
            if (d.metric.isNotEmpty()) {
                n = when(d.metric) {
                    "families" -> { available = d.target <= familyCapacity; verifiedFamilies.size.toLong() }
                    "hyperspace", "foil" -> verifiedFamilyCards.filter { (_, row) ->
                        if (d.metric == "hyperspace") row.variant.contains("Hyperspace", true) else row.variant.contains("Foil", true)
                    }.map { details.family(it.first) }.distinct().size.toLong()
                    "sets", "playsets", "sets90" -> {
                        val index = if(d.metric == "sets") 0 else if(d.metric == "playsets") 1 else 2
                        rows = setChecks.map { (set, flags, ready) -> BadgeCheck(set, flags[index], emptyList(), ready) }
                        available = rows.count { it.available } >= d.target
                        rows.count { it.complete }.toLong()
                    }
                    "variants_four_sets" -> {
                        val selected = ownedCards.filter { (card, row) -> row.variant != "Normale" || !CollectionPlan.isClassic(card) }
                        available = book.classicSets.size >= 4
                        if (selected.map { it.first.setCode }.distinct().size < 4) 0 else
                            selected.map { (card, row) -> DeckConstruction.reference(card) + ":" + row.variant }.distinct().size.toLong()
                    }
                    "historian" -> {
                        val stories = results.filter { it.badge.category == "Histoires" }
                        val required = stories.filter { it.badge.id in listOf("hoth", "republic_fall") }
                        available = required.all { it.available }
                        if(required.all { it.earned || (it.available && it.current >= it.target) }) stories.count { it.earned || (it.available && it.current >= it.target) }.toLong() else 0
                    }
                    "app_master" -> {
                        rows = listOf(BadgeCheck("1 000 familles scannées",uniqueScans >= 1000,emptyList()),
                            BadgeCheck("10 decks conformes différents",count("deck") >= 10,emptyList()),
                            BadgeCheck("25 échanges confirmés",count("trade") >= 25,emptyList()),
                            BadgeCheck("10 sélections de proxies enregistrées",count("proxy") >= 10,emptyList()),
                            BadgeCheck("Un export de deck",count("export") >= 1,emptyList()),
                            BadgeCheck("Une sauvegarde vérifiée",count("backup") >= 1,emptyList()))
                        rows.count { it.complete }.toLong()
                    }
                    "scan" -> scans
                    else -> count(d.metric)
                }
                evidence = when(d.metric) {
                    "families" -> verifiedFamilyCards.distinctBy { details.family(it.first) }.take(12).map { DeckConstruction.reference(it.first) }
                    "hyperspace", "foil" -> verifiedFamilyCards.filter { (_, row) -> row.variant.contains(if(d.metric=="hyperspace")"Hyperspace" else "Foil",true) }
                        .distinctBy { details.family(it.first) }.take(12).map { DeckConstruction.reference(it.first) }
                    "variants_four_sets" -> ownedCards.filter { (card,row) -> row.variant!="Normale" || !CollectionPlan.isClassic(card) }
                        .take(12).map { DeckConstruction.reference(it.first) }
                    "sets", "playsets", "sets90" -> {
                        val completed=rows.filter { it.complete }.map { it.label }.toSet()
                        ownedCards.filter { (card,row) -> card.setCode in completed && row.variant=="Normale" && CollectionPlan.isClassic(card) }
                            .take(12).map { DeckConstruction.reference(it.first) }
                    }
                    else -> emptyList()
                }
            }
            results += BadgeProgress(d, n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), d.target, available, rows, evidence, awards[d.id])
        }
        return results
    }
    fun deckSignature(deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails): String {
        val byId = catalog.associateBy(DeckConstruction::reference)
        fun family(id: String) = byId[id]?.let(details::family) ?: id
        fun zone(rows: List<DeckEntry>) = rows.groupBy { family(it.cardId) }.toSortedMap().entries.joinToString(";") { (id, entries) -> "$id:${entries.sumOf { it.count }}" }
        return badgeFingerprint(family(deck.leader) + "|" + family(deck.base) + "|" + zone(deck.deck) + "|" + zone(deck.sideboard))
    }
}
