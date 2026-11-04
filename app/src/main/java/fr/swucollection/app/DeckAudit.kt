package fr.swucollection.app

internal data class DeckNeed(val card: CatalogCard,val required: Int,val owned: Int) {val missing get()=(required-owned).coerceAtLeast(0)}
internal data class DeckReport(val drawCount: Int,val issues: List<String>,val needs: List<DeckNeed>,val unknown: Int)
internal object DeckAudit {
    private fun kind(card: CatalogCard)=card.type.lowercase().replace('é','e').replace('è','e')
    fun report(list: CardList,owned: List<CollectionCard>,catalog: List<CatalogCard>,details: CardDetails,rules: LegalityRules): DeckReport {
        val byReference=catalog.associateBy {MarketPricing.key(it.setCode,it.number)}
        val stock=owned.groupBy { row ->byReference[MarketPricing.key(row.setCode,row.number)]?.let {details.family(it)}?:"unknown:${MarketPricing.key(row.setCode,row.number)}" }
            .mapValues {it.value.sumOf { c ->c.quantity }}
        val groups=list.entries.groupBy {details.family(it.card)}
        val needs=groups.values.map { entries ->val c=entries.first().card;DeckNeed(c,entries.sumOf {it.quantity},stock[details.family(c)]?:0) }
        val leaders=list.entries.filter {kind(it.card).contains("leader")||kind(it.card)=="chef"}
        val bases=list.entries.filter {kind(it.card)=="base"}
        val draw=list.entries-leaders.toSet()-bases.toSet()
        val count=draw.sumOf {it.quantity};val issues=mutableListOf<String>()
        val unknown=list.entries.count {details.get(it.card)==null}
        if(list.format!="Liste") {
            val twin=list.format=="Twin Suns";val minimum=if(twin)80 else 50
            if(leaders.sumOf {it.quantity}!=(if(twin)2 else 1))issues+="${if(twin)"Deux leaders différents sont nécessaires" else "Un seul leader est nécessaire"}."
            if(twin&&leaders.map {details.family(it.card)}.distinct().size!=2)issues+="Choisis deux leaders différents."
            if(bases.sumOf {it.quantity}!=1)issues+="Une seule base est nécessaire."
            if(count<minimum)issues+="Deck principal : $count / $minimum cartes minimum."
            draw.filter {kind(it.card) !in listOf("unite","unit","evenement","event","amelioration","upgrade")}.forEach {issues+="${it.card.name} : ce type de carte ne va pas dans le deck principal."}
            val max=if(twin)1 else 3
            groups.values.filter {it.sumOf {entry ->entry.quantity}>max}.forEach {issues+="${it.first().card.name} : ${it.sumOf {entry->entry.quantity}} exemplaires, maximum $max toutes versions confondues."}
            if(twin) {
                val aspects=leaders.flatMap {details.get(it.card)?.aspects.orEmpty()}.toSet()
                if("Heroism" in aspects&&"Villainy" in aspects)issues+="Les leaders ne peuvent pas mélanger Héroïsme et Infâmie."
            }
            if(list.format=="Premier") {
                val legality=PremierLegality(catalog,rules)
                val active=catalog.filter {it.setCode in rules.activeSets}.map {details.family(it)}.toSet()
                val suspendedFamilies=rules.suspended.mapNotNull {byReference[MarketPricing.key(it.setCode,it.number)]?.let(details::family)}.toSet()
                list.entries.forEach {entry ->
                    val family=details.family(entry.card)
                    val status=legality.status(entry.card)
                    when {
                        family in suspendedFamilies||status==PremierStatus.SUSPENDED ->issues+="${entry.card.name} : suspendue en Premier."
                        family in active -> Unit
                        status==PremierStatus.OUT_OF_ROTATION ->issues+="${entry.card.name} : hors rotation Premier."
                        rules.legalCards.none {it.name.equals(entry.card.name,true)&&it.type.equals(entry.card.type,true)} ->issues+="${entry.card.name} : légalité Premier à vérifier."
                    }
                }
            } else {
                val suspended=rules.formatSuspensions[list.format].orEmpty()
                val families=suspended.mapNotNull {byReference[MarketPricing.key(it.setCode,it.number)]?.let(details::family)}.toSet()
                list.entries.filter {entry->details.family(entry.card) in families||suspended.any {it.name.equals(entry.card.name,true)||MarketPricing.key(it.setCode,it.number)==MarketPricing.key(entry.card.setCode,entry.card.number)}}
                    .forEach {issues+="${it.card.name} : suspendue dans ce format."}
            }
            if(unknown>0)issues+="$unknown référence(s) sans informations complètes : contrôle partiel."
        }
        return DeckReport(count,issues.distinct(),needs,unknown)
    }
}
