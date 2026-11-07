package fr.swucollection.app

import android.content.ContentValues
import org.json.JSONObject
import java.security.MessageDigest

internal fun tradeFinish(finish: String) = when(finish) { "Classique" -> "Normale"; "Hyperspace foil" -> "Hyperspace Foil"; else -> finish }
internal fun tradeReceiptKey(uid: String, id: String) = MessageDigest.getInstance("SHA-256").digest("$uid:$id".toByteArray()).joinToString("") {"%02x".format(it)}
internal fun CollectionDatabase.tradeApplied(uid: String, id: String) = tool("tradeReceipts")?.has(tradeReceiptKey(uid,id)) == true
internal fun validateTradeReceipts(j: JSONObject) {
    require(j.length()<=10000)
    j.keys().forEach { key -> require(key.matches(Regex("[a-f0-9]{64}")) && j.getLong(key)>=0) }
}
/** A user's physical handover confirmation only changes this device, atomically and once. */
internal fun CollectionDatabase.applyConfirmedTrade(uid: String, trade: PlayerTrade, catalog: List<CatalogCard>): Boolean {
    require(uid.isNotBlank() && uid in trade.members && trade.members.size==2 && trade.status=="accepted") { "Cet échange doit d’abord être accepté par les deux joueurs." }
    return withHistory("Échange remis · ${trade.id.take(12)}") {
        if(tradeApplied(uid,trade.id)) return@withHistory false
        val receipts=tool("tradeReceipts") ?: JSONObject()
        require(receipts.length()<10000) { "Le registre d’échanges est plein." }
        val requester=uid==trade.requester
        val outgoing=CommunitySocial.decodeTrade(if(requester) trade.offer else trade.wanted)
        val incoming=CommunitySocial.decodeTrade(if(requester) trade.wanted else trade.offer)
        val byId=catalog.associateBy(DeckConstruction::reference)
        fun key(item: TradeSelection)=listOf(item.cardId,item.language,tradeFinish(item.finish)).joinToString(":")
        val stock=allCards()
        fun rows(item: TradeSelection)=stock.filter { DeckConstruction.stockReference(it.setCode,it.number)==item.cardId && it.language==item.language && it.variant==tradeFinish(item.finish) }
        (outgoing+incoming).forEach { item ->
            val card=byId[item.cardId] ?: error("Référence absente du catalogue : ${item.cardId}")
            require(tradeFinish(item.finish) in ScanVariants.detect(card).choices) { "Finition incompatible : ${card.name}" }
        }
        val reserved=loans().reserved()
        outgoing.groupBy(::key).values.forEach { group ->
            val found=rows(group.first()); val available=found.sumOf { it.quantity-(reserved[loanKey(it)] ?: 0) }
            require(available>=group.sumOf {it.quantity}) { "Exemplaires disponibles insuffisants pour ${group.first().cardId} · ${group.first().language} · ${group.first().finish}." }
        }
        val deltas=(outgoing.map {it to -it.quantity}+incoming.map {it to it.quantity}).groupBy {key(it.first)}
        deltas.values.forEach { group ->
            val item=group.first().first; val card=byId.getValue(item.cardId);val old=rows(item)
            val quantity=old.sumOf {it.quantity}+group.sumOf {it.second}
            require(quantity in 0..9999) { "Quantité finale invalide : ${item.cardId}" }
            // Readable exact reference retained; all deletions are inside this transaction.
            old.forEach { writableDatabase.delete("collection_cards","id=?",arrayOf(it.id.toString())) }
            if(quantity>0) writableDatabase.insertOrThrow("collection_cards",null,ContentValues().apply {
                old.firstOrNull()?.let {put("id",it.id)}
                put("name",card.name);put("set_code",card.setCode);put("card_number",card.number)
                put("variant",tradeFinish(item.finish));put("language",item.language);put("quantity",quantity)
            })
        }
        receipts.put(tradeReceiptKey(uid,trade.id),System.currentTimeMillis());putTool("tradeReceipts",receipts)
        true
    }
}
