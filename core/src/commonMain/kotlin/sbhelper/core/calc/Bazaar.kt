package sbhelper.core.calc

import sbhelper.core.model.BazaarProduct
import kotlin.math.min

/** 바자 플립: 구매 주문(즉시판매가+0.1)으로 사서 판매 주문(즉시구매가-0.1)으로 판다. */
data class BazaarFlip(
    val id: String,
    val buyOrderPrice: Double,
    val sellOfferPrice: Double,
    val profitPerItem: Double,
    val marginRatio: Double,
    val weeklyVolume: Long,
) {
    /** 주간 거래량의 일부만 내가 잡는다고 가정한 시간당 잠재 이익 (참고용) */
    val hourlyPotential get() = profitPerItem * weeklyVolume / 168.0 * 0.05
}

/** NPC 플립: 바자에서 구매 주문으로 산 뒤 NPC 에게 판다. */
data class NpcFlip(
    val id: String,
    val buyOrderPrice: Double,
    val npcPrice: Double,
    val profitPerItem: Double,
    val weeklyVolume: Long,
)

object BazaarCalc {
    private const val TICK = 0.1

    fun flips(products: Map<String, BazaarProduct>, taxRate: Double, minWeeklyVolume: Long = 0): List<BazaarFlip> =
        products.values.mapNotNull { p ->
            val q = p.quickStatus
            if (q.buyPrice <= 0 || q.sellPrice <= 0) return@mapNotNull null
            val buy = q.sellPrice + TICK
            val sell = q.buyPrice - TICK
            val profit = sell * (1 - taxRate) - buy
            val volume = min(q.buyMovingWeek, q.sellMovingWeek)
            if (volume < minWeeklyVolume) return@mapNotNull null
            BazaarFlip(p.productId, buy, sell, profit, profit / buy, volume)
        }.sortedByDescending { it.hourlyPotential }

    fun npcFlips(products: Map<String, BazaarProduct>, npcPrices: Map<String, Double>, minWeeklyVolume: Long = 0): List<NpcFlip> =
        products.values.mapNotNull { p ->
            val npc = npcPrices[p.productId] ?: return@mapNotNull null
            val q = p.quickStatus
            if (q.sellPrice <= 0) return@mapNotNull null
            val buy = q.sellPrice + TICK
            val profit = npc - buy
            val volume = q.sellMovingWeek
            if (profit <= 0 || volume < minWeeklyVolume) return@mapNotNull null
            NpcFlip(p.productId, buy, npc, profit, volume)
        }.sortedByDescending { it.profitPerItem * min(it.weeklyVolume, 100_000) }
}
