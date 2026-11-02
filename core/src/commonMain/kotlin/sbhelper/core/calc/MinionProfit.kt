package sbhelper.core.calc

import sbhelper.core.data.GameData

/**
 * 미니언에 넣은 것 (위키 Module:Minion 의 계산과 같은 규칙).
 *  - 속도 보너스는 모두 더한다 (연료 + Minion Expander 5% + Flycatcher 20% + Mithril Infusion 10% + Crystal 10% + Beacon 2%/단계)
 *  - Catalyst 처럼 x 배수 연료는 속도가 아니라 나오는 아이템 수를 곱한다 (위키 Minion Fuel)
 *  - Diamond Spreading: 기본 아이템 10개마다 Diamond 1개
 *  - 업그레이드 칸은 2개 (Expander, Flycatcher, Diamond Spreading 합쳐서)
 */
data class MinionSetup(
    val fuel: GameData.MinionFuel? = null,
    val expanders: Int = 0,
    val flycatchers: Int = 0,
    val diamondSpreading: Boolean = false,
    val infusion: Boolean = false,
    val crystal: Boolean = false,
    /** 0 = 없음, 1..5 = Beacon I..V */
    val beacon: Int = 0,
) {
    val upgradeSlotsUsed get() = expanders + flycatchers + (if (diamondSpreading) 1 else 0)

    fun speedBonus(m: GameData.Minion): Double =
        (fuel?.speed ?: 0.0) + 0.05 * expanders + 0.2 * flycatchers + (if (infusion) 0.1 else 0.0) +
            (if (crystal && m.crystal) 0.1 else 0.0) + 0.02 * beacon.coerceIn(0, 5)
}

/** 팔 곳 */
enum class SellTo { BEST, BAZAAR, NPC }

data class MinionOutput(val id: String, val perDay: Double, val unitPrice: Double?, val where: String) {
    val coinsPerDay get() = unitPrice?.let { it * perDay }
}

data class MinionTierResult(
    val tier: Int,
    val secondsPerAction: Double,
    val outputs: List<MinionOutput>,
    /** 연료 하루 비용 (영구 연료는 0) */
    val fuelPerDay: Double,
    /** 저장 칸이 가득 차는 데 걸리는 시간(시간). 압축 업그레이드 없을 때 */
    val hoursToFill: Double?,
) {
    val grossPerDay get() = outputs.sumOf { it.coinsPerDay ?: 0.0 }
    val netPerDay get() = grossPerDay - fuelPerDay
}

/** 다음 단계 재료 하나: 필요 / 가진 것 / 이 미니언이 하루에 만드는 양 */
data class TierNeed(val id: String, val need: Double, val have: Double, val perDay: Double?) {
    val missing get() = (need - have).coerceAtLeast(0.0)
    /** 이 미니언(들)이 모으는 데 걸리는 날 (이 미니언이 만들지 않는 재료면 null) */
    val days get() = perDay?.takeIf { it > 0 }?.let { missing / it }
}

data class NextTierPlan(val tier: Int, val needs: List<TierNeed>, val coins: Long) {
    /** 이 미니언으로 모을 수 있는 재료 중 가장 오래 걸리는 것 (나머지는 따로 구해야 함) */
    val days get() = needs.mapNotNull { it.days }.maxOrNull()
    val external get() = needs.filter { it.perDay == null && it.missing > 0 }
}

object MinionProfit {
    const val DAY = 86_400.0

    /** 지금 넣은 것으로 이 단계(1부터)의 하루 결과 */
    fun tier(
        m: GameData.Minion, tier: Int, setup: MinionSetup, sellTo: SellTo,
        bazaarSell: (String) -> Double?, npcSell: (String) -> Double?, bazaarBuy: (String) -> Double?,
    ): MinionTierResult? {
        val t = m.tiers.getOrNull(tier - 1) ?: return null
        val speed = setup.speedBonus(m)
        val tba = t.tba / (1 + speed)
        val harvestsPerDay = DAY / (tba * m.actions)
        val mult = setup.fuel?.mult?.takeIf { it > 1 } ?: 1.0
        val base = m.items.filter { it.cond == null || it.cond == "Enchanted Egg" }
        val produced = LinkedHashMap<String, Double>()
        base.forEach { produced[it.id] = (produced[it.id] ?: 0.0) + it.avg * harvestsPerDay * mult }
        if (setup.diamondSpreading) {
            val baseItems = base.sumOf { it.avg } * harvestsPerDay
            produced["DIAMOND"] = (produced["DIAMOND"] ?: 0.0) + baseItems / 10
        }
        val outputs = produced.map { (id, n) ->
            val bz = bazaarSell(id)?.takeIf { it > 0 }
            val npc = npcSell(id)?.takeIf { it > 0 }
            val (price, where) = when (sellTo) {
                SellTo.BAZAAR -> bz to "바자"
                SellTo.NPC -> npc to "NPC"
                SellTo.BEST -> if ((bz ?: 0.0) >= (npc ?: 0.0)) (bz ?: npc) to (if (bz != null) "바자" else "NPC") else npc to "NPC"
            }
            MinionOutput(id, n, price, if (price == null) "" else where)
        }
        val fuelPerDay = setup.fuel?.let { f -> f.hours?.let { h -> (bazaarBuy(f.id) ?: 0.0) * 24 / h } } ?: 0.0
        val itemsPerDay = produced.values.sum()
        val hoursToFill = if (itemsPerDay > 0 && t.storage > 0) t.storage / itemsPerDay * 24 else null
        return MinionTierResult(tier, tba, outputs, fuelPerDay, hoursToFill)
    }

    /**
     * 다음 단계(tier+1)로 올리는 재료를 이 미니언으로 모으면 며칠? 재료를 기본 재료까지 분해(제작법)해서,
     * 이 미니언이 만드는 기본 재료는 (하루 생산량 × 미니언 수) 로 나눈다.
     */
    fun nextTier(
        data: GameData, m: GameData.Minion, tier: Int, result: MinionTierResult, count: Int, stock: Map<String, Long>,
    ): NextTierPlan? {
        val next = m.tiers.getOrNull(tier) ?: return null // tiers[tier] = (tier+1) 단계
        val pairs = next.costPairs()
        val coins = pairs.filter { it.first == "COINS" }.sumOf { it.second }
        val planner = CraftPlanner(data)
        val raw = LinkedHashMap<String, Double>()
        val have = HashMap<String, Double>()
        val left = stock.mapValues { it.value.toDouble() }.toMutableMap()
        val perDay = result.outputs.associate { it.id to it.perDay * count.coerceAtLeast(1) }
        for ((id, n) in pairs) {
            if (id == "COINS") continue
            // 이 미니언이 바로 만드는 아이템이거나 다른 미니언 같은 것은 분해하지 않는다
            val plan = if (id in perDay || id.contains("_GENERATOR_")) null else planner.plan(id, n.toInt(), left.mapValues { it.value.toLong() })
            if (plan == null || plan.raw.isEmpty()) {
                val use = minOf(left[id] ?: 0.0, n.toDouble())
                left[id] = (left[id] ?: 0.0) - use
                raw[id] = (raw[id] ?: 0.0) + n
                have[id] = (have[id] ?: 0.0) + use
            } else {
                // 목표 아이템 자체를 가지고 있으면 먼저 쓴다
                val own = minOf(left[id] ?: 0.0, n.toDouble())
                if (own >= n) {
                    left[id] = (left[id] ?: 0.0) - own
                    raw[id] = (raw[id] ?: 0.0) + n; have[id] = (have[id] ?: 0.0) + n
                    continue
                }
                plan.raw.forEach { r ->
                    raw[r.itemId] = (raw[r.itemId] ?: 0.0) + r.needed
                    have[r.itemId] = (have[r.itemId] ?: 0.0) + r.have
                    left[r.itemId] = ((left[r.itemId] ?: 0.0) - r.have).coerceAtLeast(0.0)
                }
            }
        }
        val needs = raw.map { (id, n) -> TierNeed(id, n, have[id] ?: 0.0, perDay[id]) }
        return NextTierPlan(tier + 1, needs, coins)
    }

    /** 프로필 crafted_generators 에서 이 미니언의 가장 높은 단계 (예: COBBLESTONE_7) */
    fun craftedTier(m: GameData.Minion, crafted: List<String>): Int {
        val prefix = m.icon?.removeSuffix("_GENERATOR_1")?.let { "${it}_" } ?: return 0
        return crafted.filter { it.startsWith(prefix) }.mapNotNull { it.removePrefix(prefix).toIntOrNull() }.maxOrNull() ?: 0
    }
}
