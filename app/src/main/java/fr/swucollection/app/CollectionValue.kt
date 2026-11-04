package fr.swucollection.app

import java.math.BigDecimal

internal data class CollectionValue(val low: BigDecimal,val high: BigDecimal,val priced: Int,val unpriced: Int)
internal fun collectionValue(cards: List<CollectionCard>,quotes: Map<String,MarketQuote>): CollectionValue {
    var low=BigDecimal.ZERO;var high=BigDecimal.ZERO;var priced=0;var unknown=0
    cards.forEach {c ->
        val q=quotes[collectionPriceKey(c.setCode,c.number,c.variant)];val price=q?.amount?.takeIf {it.isFinite()&&it>0}
        if(price==null)unknown+=c.quantity else {
            low+=BigDecimal.valueOf(price)*c.quantity.toBigDecimal()
            high+=BigDecimal.valueOf(q?.maxAmount?.takeIf {it.isFinite()&&it>=price}?:price)*c.quantity.toBigDecimal();priced+=c.quantity
        }
    }
    return CollectionValue(low,high,priced,unknown)
}
