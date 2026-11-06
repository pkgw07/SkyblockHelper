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

/** 업그레이드 한 단계: 이 단계로 올리는 재료 (그대로 · 기본 재료), 코인, 이 미니언으로 모으는 날 (바로 전 단계 속도로) */
data class UpgradeStep(
    val tier: Int,
    val asIs: Map<String, Double>,
    val raw: Map<String, Double>,
    val coins: Long,
    val days: Double?,
)

/**
 * from 단계 → to 단계 업그레이드 계산.
 *  - asIs: 단계 재료 그대로 (예: Enchanted Acacia Log), raw: 기본 재료로 나눈 것 (예: Acacia Log)
 *  - external: 이 미니언이 만들지 않아서 따로 구해야 하는 재료 (기본 재료 기준)
 *  - daysStay: 지금 단계 그대로 두고 모두 모으면, daysRush: 단계마다 모이는 대로 바로 올리면
 */
data class UpgradePath(
    val from: Int,
    val to: Int,
    val steps: List<UpgradeStep>,
    val asIs: Map<String, Double>,
    val raw: Map<String, Double>,
    val coins: Long,
    val external: Map<String, Double>,
    val daysStay: Double?,
    val daysRush: Double?,
    /** 이미 가진 양 (기본 재료 기준, Enchanted 형태로 가진 것도 풀어서) */
    val have: Map<String, Double> = emptyMap(),
)

object MinionUpgrade {
    /** 단계(1부터)의 기본 재료 하루 생산량 (미니언 수 반영) */
    fun ratesPerDay(m: GameData.Minion, tier: Int, setup: MinionSetup, count: Int): Map<String, Double> {
        val r = MinionProfit.tier(m, tier, setup, SellTo.NPC, { null }, { null }, { null }) ?: return emptyMap()
        return r.outputs.associate { it.id to it.perDay * count.coerceAtLeast(1) }
    }

    fun plan(data: GameData, m: GameData.Minion, from: Int, to: Int, setup: MinionSetup, count: Int, stock: Map<String, Long> = emptyMap()): UpgradePath {
        val planner = CraftPlanner(data)
        val start = from.coerceIn(1, m.tiers.size)
        val end = to.coerceIn(start, m.tiers.size)
        val steps = mutableListOf<UpgradeStep>()
        val produced = ratesPerDay(m, 1, setup, 1).keys + m.items.map { it.id }
        for (t in start + 1..end) {
            val tierData = m.tiers[t - 1]
            val asIs = LinkedHashMap<String, Double>()
            val raw = LinkedHashMap<String, Double>()
            var coins = 0L
            for ((id, n) in tierData.costPairs()) {
                if (id == "COINS") { coins += n; continue }
                // 바로 전 단계 미니언 자체 (XII 단계 재료 등) 는 재료로 세지 않는다
                if (id.contains("_GENERATOR_")) continue
                asIs[id] = (asIs[id] ?: 0.0) + n
                if (id in produced) { raw[id] = (raw[id] ?: 0.0) + n; continue }
                val p = planner.plan(id, n.toInt())
                if (p.raw.isEmpty()) raw[id] = (raw[id] ?: 0.0) + n
                else p.raw.forEach { r -> raw[r.itemId] = (raw[r.itemId] ?: 0.0) + r.needed }
            }
            val rates = ratesPerDay(m, t - 1, setup, count)
            steps += UpgradeStep(t, asIs, raw, coins, daysFor(raw, rates))
        }
        fun sum(pick: (UpgradeStep) -> Map<String, Double>): Map<String, Double> {
            val out = LinkedHashMap<String, Double>()
            steps.forEach { s -> pick(s).forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v } }
            return out
        }
        val raw = sum { it.raw }
        val external = raw.filterKeys { it !in produced }
        // 가진 재료: 기본 재료 + Enchanted 형태로 가진 것을 풀어서
        val comp = Compressor(data)
        val have = raw.keys.associateWith { id ->
            val forms = comp.chain(id).sumOf { (f, ratio) -> (stock[f] ?: 0L) * ratio }
            ((stock[id] ?: 0L) + forms).coerceAtMost(raw.getValue(id))
        }.filterValues { it > 0 }
        fun left(need: Map<String, Double>, pool: MutableMap<String, Double>): Map<String, Double> = need.mapValues { (id, n) ->
            val use = minOf(pool[id] ?: 0.0, n)
            pool[id] = (pool[id] ?: 0.0) - use
            n - use
        }
        val stay = daysFor(left(raw, have.toMutableMap()), ratesPerDay(m, start, setup, count))
        // 바로바로 올리면: 단계마다 남은 가진 재료를 먼저 쓰고, 모자란 만큼을 바로 전 단계 속도로
        val pool = have.toMutableMap()
        val rushSteps = steps.map { s -> s.copy(days = daysFor(left(s.raw, pool), ratesPerDay(m, s.tier - 1, setup, count))) }
        val rush = rushSteps.sumOf { it.days ?: 0.0 }
        return UpgradePath(start, end, rushSteps, sum { it.asIs }, raw, steps.sumOf { it.coins }, external, stay, rush, have)
    }

    /** 이 미니언이 만드는 재료를 다 모으는 날 (여러 재료면 동시에 모이니 가장 오래 걸리는 것). 만드는 재료가 없으면 0 */
    private fun daysFor(raw: Map<String, Double>, rates: Map<String, Double>): Double {
        val mine = raw.filterKeys { (rates[it] ?: 0.0) > 0 }
        if (mine.isEmpty()) return 0.0
        return mine.maxOf { (id, n) -> n / rates.getValue(id) }
    }
}

/**
 * 기본 재료 ↔ 강화(enchanted) 형태 바꿔 보기 (제작법으로):
 * 재료 하나만 넣는 제작법을 따라가서 가장 많이 압축되는 Enchanted 아이템까지 (예: Diamond → Enchanted Diamond → Enchanted Diamond Block).
 */
class Compressor(private val data: GameData) {
    /** 재료 → (결과, 결과 하나에 드는 재료 수) */
    private val single: Map<String, List<Pair<String, Double>>> by lazy {
        val out = HashMap<String, MutableList<Pair<String, Double>>>()
        data.crafting.forEach { (product, recipes) ->
            recipes.forEach { r ->
                if (r.`in`.size == 1) {
                    val (ing, q) = r.`in`.entries.first()
                    val per = q / r.count.coerceAtLeast(1)
                    if (per > 1 && ing != product) out.getOrPut(ing) { mutableListOf() } += product to per
                }
            }
        }
        out
    }
    private val cache = HashMap<String, List<Pair<String, Double>>>()

    /** raw → [(1단계 결과, 누적 비율), (2단계 결과, 누적 비율) …]. 마지막은 Enchanted 아이템. 없으면 빈 목록 */
    fun chain(raw: String): List<Pair<String, Double>> = cache.getOrPut(raw) {
        var best: List<Pair<String, Double>> = emptyList()
        fun walk(id: String, ratio: Double, path: List<Pair<String, Double>>, depth: Int) {
            val bestRatio = best.lastOrNull()?.second ?: 0.0
            // 더 많이 압축되는 쪽, 같으면 단계가 적은 쪽 (Diamond → Diamond Block → … 보다 Diamond → Enchanted Diamond)
            if (path.isNotEmpty() && path.last().first.startsWith("ENCHANTED_") && (ratio > bestRatio + 1e-9 || (kotlin.math.abs(ratio - bestRatio) < 1e-9 && path.size < best.size))) best = path
            if (depth >= 4) return
            single[id].orEmpty().forEach { (p, per) ->
                if (path.none { it.first == p }) walk(p, ratio * per, path + (p to ratio * per), depth + 1)
            }
        }
        walk(raw, 1.0, emptyList(), 0)
        // 끝이 Enchanted 인 단계까지만
        best
    }

    /** 기본 재료 양을 큰 형태부터 나눠서: [(아이템, 개수)] (예: 1 Enchanted Diamond Block + 12 Enchanted Diamond + 40 Diamond) */
    fun split(raw: String, amount: Double): List<Pair<String, Double>> {
        val c = chain(raw)
        if (c.isEmpty() || amount <= 0) return listOf(raw to amount)
        var left = amount
        val out = mutableListOf<Pair<String, Double>>()
        for ((id, ratio) in c.reversed()) {
            val n = kotlin.math.floor(left / ratio)
            if (n > 0) { out += id to n; left -= n * ratio }
        }
        if (left > 1e-6) out += raw to left
        return out
    }
}
