package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CardLoan(val id: String,val set: String,val number: String,val variant: String,val language: String,
    val name: String,val borrower: String,val quantity: Int,val returned: Int=0,val date: Long,val due: Long=0) {
    val outstanding get()=quantity-returned
    val key get()=loanKey(set,number,variant,language)
    fun json()=JSONObject().put("id",id).put("set",set).put("number",number).put("variant",variant).put("language",language).put("name",name).put("borrower",borrower).put("quantity",quantity).put("returned",returned).put("date",date).put("due",due)
    companion object {
        fun read(j: JSONObject)=CardLoan(j.getString("id"),j.getString("set"),j.getString("number"),j.getString("variant"),j.getString("language"),j.getString("name"),j.getString("borrower"),toolInt(j,"quantity",1..9999),toolInt(j,"returned",0..9999),j.getLong("date"),j.getLong("due")).also {
            require(it.id.length in 1..100 && it.set.matches(Regex("[A-Za-z0-9]{2,12}")) && it.number.matches(Regex("[0-9]{1,5}")) && it.variant.length in 1..60 && CardLanguages.valid(it.language))
            require(it.name.length in 1..350 && it.borrower.isNotBlank() && it.borrower.length<=80 && it.quantity in 1..9999 && it.returned in 0..it.quantity && it.date>=0 && it.due>=0)
        }
    }
}
internal fun loanKey(set: String,number: String,variant: String,language: String)=MarketPricing.key(set,number)+":"+variant+":"+language
internal fun loanKey(row: CollectionCard)=loanKey(row.setCode,row.number,row.variant,row.language)
internal data class LoanBook(val rows: List<CardLoan> = emptyList()) {
    fun reserved()=rows.groupBy { it.key }.mapValues { (_,loans)->loans.sumOf { it.outstanding.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    fun add(card: CollectionCard,borrower: String,quantity: Int,due: Long,now: Long): LoanBook {
        require(rows.size<2000) {"Le registre contient déjà 2 000 prêts."}
        require(quantity in 1..(card.quantity-(reserved()[loanKey(card)]?:0))) {"Il n’y a pas assez d’exemplaires disponibles."}
        val loan=CardLoan(UUID.randomUUID().toString(),card.setCode,card.number,card.variant,card.language,card.name,borrower.trim(),quantity,date=now,due=due)
        CardLoan.read(loan.json()); return copy(rows=listOf(loan)+rows)
    }
    fun returned(id: String,count: Int): LoanBook {
        val row=rows.firstOrNull {it.id==id} ?: error("Prêt introuvable.")
        require(count in 1..row.outstanding) {"Quantité de retour invalide."}
        return copy(rows=rows.map {if(it.id==id) it.copy(returned=it.returned+count) else it})
    }
    fun json()=JSONObject().put("items",JSONArray().apply {rows.forEach {put(it.json())}})
    companion object {fun read(j: JSONObject): LoanBook {val a=j.getJSONArray("items");require(a.length()<=2000);val rows=(0 until a.length()).map {CardLoan.read(a.getJSONObject(it))};require(rows.map {it.id}.distinct().size==rows.size);return LoanBook(rows)}}
}
internal fun CollectionDatabase.loans()=tool("loans")?.let(LoanBook::read) ?: LoanBook()
