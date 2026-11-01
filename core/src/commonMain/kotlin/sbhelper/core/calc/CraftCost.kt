package sbhelper.core.calc

import sbhelper.core.data.GameData

/**
 * 직접 만들면 드는 비용.
 * - direct: 조합법(또는 포지) 재료를 지금 시세로 사서 한 번 만들 때
 * - cheapest: 재료마다 "사기 vs 만들기" 중 싼 쪽을 끝까지 따라간 비용
 * 가격을 알 수 없는 재료(소울바운드·거래 불가·시세 없음)는 합계에서 빼고 따로 돌려준다.
 */
object CraftCost {
    data class Line(val id: String, val qty: Double, val unit: Double?) {
        val total: Double? get() = unit?.let { it * qty }
    }

    data class Result(
        /** 재료별 (한 번 만들 때) */
        val lines: List<Line>,
        /** 포지·제작에 드는 코인 */
        val coins: Double,
        /** 가격을 아는 것의 합 (+ 코인), 결과물 1개 기준 */
        val total: Double,
        /** 가격을 모르는 재료 (합계에서 빠짐) */
        val unpriced: List<Line>,
        /** 한 번에 나오는 개수 */
        val count: Int,
        val forge: Boolean,
    )

    data class Cheapest(
        /** 결과물 1개 기준 최저 비용 (가격 아는 부분만) */
        val total: Double,
        /** 최종적으로 사야 하는 것들 (재료 ID → 개수, 단가) */
        val buy: List<Line>,
        /** 가격을 모르는 기본 재료 */
        val unpriced: List<Line>,
        val coins: Double,
    )

    private fun recipe(data: GameData, id: String): Triple<Map<String, Double>, Int, Double>? {
        data.crafting[id]?.firstOrNull()?.let { return Triple(it.`in`, it.count.coerceAtLeast(1), 0.0) }
        data.forge[id]?.firstOrNull()?.let { return Triple(it.`in`, it.count.coerceAtLeast(1), it.coins) }
        return null
    }

    /** @param price 아이템 1개를 지금 살 때 값 (모르면 null) */
    fun direct(data: GameData, id: String, price: (String) -> Double?): Result? {
        val (inputs, count, coins) = recipe(data, id) ?: return null
        val lines = inputs.map { (k, q) -> Line(k, q, price(k)) }
        val priced = lines.filter { it.unit != null }
        return Result(lines, coins, (priced.sumOf { it.total!! } + coins) / count, lines.filter { it.unit == null }, count, data.forge.containsKey(id) && !data.crafting.containsKey(id))
    }

    /** 재료마다 사기/만들기 중 싼 쪽을 끝까지 (최대 깊이 6, 순환 방지) */
    fun cheapest(data: GameData, id: String, price: (String) -> Double?): Cheapest? {
        recipe(data, id) ?: return null
        val memo = HashMap<String, Double?>()

        // 1개를 얻는 최저 비용 (모르면 null)
        fun unitCost(item: String, depth: Int, visiting: Set<String>): Double? {
            memo[item]?.let { return it }
            val buy = price(item)
            val r = if (depth < 6 && item !in visiting) recipe(data, item) else null
            val make = r?.let { (inputs, count, coins) ->
                var sum = coins
                for ((k, q) in inputs) sum += (unitCost(k, depth + 1, visiting + item) ?: return@let null) * q
                sum / count
            }
            val best = listOfNotNull(buy, make).minOrNull()
            memo[item] = best
            return best
        }

        // 최종 장바구니: 사는 게 싼 것은 사고, 만드는 게 싼 것은 재료로 펼친다
        val buy = HashMap<String, Double>()
        val unknown = HashMap<String, Double>()
        var coins = 0.0
        fun expand(item: String, qty: Double, depth: Int, visiting: Set<String>, top: Boolean) {
            val r = if (depth < 6 && item !in visiting) recipe(data, item) else null
            val b = if (top) null else price(item)
            val makeUnit = r?.let { (inputs, count, c) ->
                var sum = c
                for ((k, q) in inputs) sum += (unitCost(k, depth + 1, visiting + item) ?: return@let null) * q
                sum / count
            }
            when {
                r != null && (b == null || (makeUnit != null && makeUnit < b)) -> {
                    val (inputs, count, c) = r
                    val times = qty / count
                    coins += c * times
                    inputs.forEach { (k, q) -> expand(k, q * times, depth + 1, visiting + item, false) }
                }
                b != null -> buy[item] = (buy[item] ?: 0.0) + qty
                else -> unknown[item] = (unknown[item] ?: 0.0) + qty
            }
        }
        expand(id, 1.0, 0, emptySet(), true)
        val lines = buy.map { (k, q) -> Line(k, q, price(k)) }.sortedByDescending { it.total ?: 0.0 }
        return Cheapest(lines.sumOf { it.total ?: 0.0 } + coins, lines, unknown.map { (k, q) -> Line(k, q, null) }, coins)
    }
}
