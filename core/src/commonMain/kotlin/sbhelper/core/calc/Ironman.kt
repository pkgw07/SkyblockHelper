package sbhelper.core.calc

import sbhelper.core.data.GameData
import sbhelper.core.model.CollectionsResponse
import kotlin.math.ceil
import kotlin.math.min

enum class Method { STOCK, CRAFT, FORGE, BASE }

/** 제작 계획 트리의 한 칸 */
data class PlanNode(
    val itemId: String,
    val needed: Double,
    val fromStock: Double,
    val method: Method,
    /** 한 번 만들 때 나오는 개수 × 만드는 횟수 */
    val crafts: Int = 0,
    val forgeSeconds: Long = 0,
    val children: List<PlanNode> = emptyList(),
) {
    val missing get() = needed - fromStock
}

data class RawNeed(val itemId: String, val needed: Double, val have: Double) {
    val missing get() = (needed - have).coerceAtLeast(0.0)
}

data class CraftPlan(
    val root: PlanNode,
    val raw: List<RawNeed>,
    /** 포지 단계 시간의 합 (같은 단계를 여러 슬롯에서 병렬로 돌리면 더 짧아짐) */
    val totalForgeSeconds: Long,
    val forgeSteps: Int,
)

/**
 * Ironman 제작 플래너. 제작법·포지 레시피는 공식 위키 데이터(gamedata.json).
 * 목표 아이템을 기본 재료까지 분해하고, 자루(sacks)에 있는 재료를 먼저 사용한다고 계산한다.
 */
class CraftPlanner(private val data: GameData) {

    fun plan(target: String, amount: Int, stock: Map<String, Long> = emptyMap(), maxDepth: Int = 10): CraftPlan {
        val remainingStock = stock.mapValues { it.value.toDouble() }.toMutableMap()
        val rawNeeded = LinkedHashMap<String, Double>()
        val rawHave = HashMap<String, Double>()
        var forgeSeconds = 0L
        var forgeSteps = 0

        fun record(id: String, qty: Double, used: Double) {
            rawNeeded[id] = (rawNeeded[id] ?: 0.0) + qty
            rawHave[id] = (rawHave[id] ?: 0.0) + used
        }

        fun expand(id: String, qty: Double, depth: Int, path: Set<String>): PlanNode {
            val craft = data.crafting[id]?.firstOrNull { r -> usable(id, r.`in`.keys, path) }
            val forge = data.forge[id]?.firstOrNull { r -> usable(id, r.`in`.keys, path) }
            val isBase = depth >= maxDepth || (craft == null && forge == null)

            // 최종 목표 아이템은 보유분과 상관없이 새로 만든다고 가정
            val use = if (depth == 0) 0.0 else min(remainingStock[id] ?: 0.0, qty)
            if (use > 0) remainingStock[id] = (remainingStock[id] ?: 0.0) - use
            val left = qty - use
            if (isBase) {
                record(id, qty, use)
                return PlanNode(id, qty, use, if (left <= 1e-9) Method.STOCK else Method.BASE)
            }
            if (left <= 1e-9) return PlanNode(id, qty, use, Method.STOCK)

            val nextPath = path + id
            return if (craft != null) {
                val times = ceil(left / craft.count).toInt()
                val kids = craft.`in`.map { (input, q) -> expand(input, q * times, depth + 1, nextPath) }
                PlanNode(id, qty, use, Method.CRAFT, times, children = kids)
            } else {
                val f = forge!!
                val times = ceil(left / f.count).toInt()
                forgeSeconds += f.sec * times
                forgeSteps += times
                val kids = f.`in`.map { (input, q) -> expand(input, q * times, depth + 1, nextPath) }
                PlanNode(id, qty, use, Method.FORGE, times, f.sec, kids)
            }
        }

        val root = expand(target, amount.toDouble(), 0, emptySet())
        val raw = rawNeeded.map { (id, need) ->
            RawNeed(id, need, rawHave[id] ?: 0.0)
        }.sortedByDescending { it.missing }
        return CraftPlan(root, raw, forgeSeconds, forgeSteps)
    }

    /**
     * 사용할 수 있는 제작법인가.
     * 블록 ↔ 낱개처럼 서로를 재료로 쓰는 "압축/해체" 관계(Diamond ↔ Diamond Block)는 분해하지 않는다.
     */
    private fun usable(output: String, inputs: Set<String>, path: Set<String>): Boolean = inputs.none { input ->
        input == output || input in path || data.crafting[input]?.any { output in it.`in` } == true
    }

    /**
     * 기본 재료를 '전체 필요량 기준' 으로: 가진 중간 재료도 기본 재료로 환산해서 모은 양에 넣는다.
     * 예) Coal 1000 이 필요하고 Enchanted Coal(=160 Coal) 2개가 있으면 → Coal 320 / 1000
     */
    fun rawProgress(target: String, amount: Int, stock: Map<String, Long>): List<RawNeed> {
        val full = plan(target, amount)
        if (stock.isEmpty()) return full.raw
        val missing = plan(target, amount, stock).raw.associate { it.itemId to it.missing }
        return full.raw.map { r -> RawNeed(r.itemId, r.needed, (r.needed - (missing[r.itemId] ?: 0.0)).coerceIn(0.0, r.needed)) }
            .sortedByDescending { it.missing }
    }

    /**
     * 전체 진행도 (0..1). 재료마다 개수 차이가 커서(코블스톤 수천 개 vs 희귀 재료 1개) 재료 가치로 무게를 둔다.
     * 가진 것 없이 분해한 전체 재료 가치 대비, 가진 것(중간 재료 포함)을 빼고 남은 재료 가치.
     * @param price 재료 하나의 가치 (없으면 null → 시세가 있는 재료들의 중간값으로)
     */
    fun progress(target: String, amount: Int, stock: Map<String, Long>, price: (String) -> Double?): Double {
        if (stock.isEmpty()) return 0.0
        val full = plan(target, amount)
        val now = plan(target, amount, stock)
        val known = (full.raw + now.raw).map { it.itemId }.distinct().associateWith { price(it)?.takeIf { p -> p > 0 } }
        val median = known.values.filterNotNull().sorted().let { if (it.isEmpty()) 1.0 else it[it.size / 2] }
        fun w(id: String) = known[id] ?: median
        val total = full.raw.sumOf { it.needed * w(it.itemId) }
        if (total <= 0) return 0.0
        val missing = now.raw.sumOf { it.missing * w(it.itemId) }
        return (1 - missing / total).coerceIn(0.0, 1.0)
    }

    /** 만들 수 있는 아이템인가 */
    fun isCraftable(id: String) = data.crafting.containsKey(id) || data.forge.containsKey(id)
}

/** 자루에 든 아이템을 NPC 에게 팔았을 때의 가치 */
data class SackValue(val itemId: String, val count: Long, val npcPrice: Double) {
    val total get() = count * npcPrice
}

object IronmanCalc {
    fun sackValues(sacks: Map<String, Long>, npcPrices: Map<String, Double>): List<SackValue> =
        sacks.mapNotNull { (id, count) -> npcPrices[id]?.let { SackValue(id, count, it) } }
            .sortedByDescending { it.total }
}

/** 컬렉션 진행도 */
data class CollectionProgress(
    val itemId: String,
    val name: String,
    val category: String,
    val amount: Long,
    val tier: Int,
    val maxTier: Int,
    val nextRequired: Long?,
    val prevRequired: Long,
    val nextUnlocks: List<String>,
) {
    val maxed get() = nextRequired == null
    val progress: Double get() = nextRequired?.let {
        ((amount - prevRequired).toDouble() / (it - prevRequired).coerceAtLeast(1)).coerceIn(0.0, 1.0)
    } ?: 1.0
}

object CollectionCalc {
    fun progress(resource: CollectionsResponse, amounts: Map<String, Long>): List<CollectionProgress> =
        resource.collections.flatMap { (_, cat) ->
            cat.items.map { (id, def) ->
                val amount = amounts[id] ?: 0L
                val tiers = def.tiers.sortedBy { it.tier }
                val reached = tiers.lastOrNull { amount >= it.amountRequired }
                val next = tiers.firstOrNull { amount < it.amountRequired }
                CollectionProgress(id, def.name, cat.name, amount, reached?.tier ?: 0, def.maxTiers,
                    next?.amountRequired, reached?.amountRequired ?: 0, next?.unlocks.orEmpty())
            }
        }
}

/** 제작 플래너에 쓸 '가진 재료': 자루 + 인벤토리·엔더 상자·백팩·개인 금고 (장비·옷장·장신구는 빼고) */
object CraftStock {
    val INVENTORY_KEYS = listOf("inv_contents", "ender_chest_contents", "personal_vault_contents")

    fun inventory(inventories: Map<String, String>): Map<String, Long> {
        val out = HashMap<String, Long>()
        val keys = INVENTORY_KEYS + inventories.keys.filter { it.startsWith("backpack_") && !it.endsWith("_icons") }
        keys.forEach { k ->
            sbhelper.core.nbt.ItemParser.parse(inventories[k]).filterNotNull().forEach { item ->
                if (item.pet != null) return@forEach
                val id = item.skyblockId ?: return@forEach
                out[id] = (out[id] ?: 0L) + item.count
            }
        }
        return out
    }

    fun merge(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
        if (b.isEmpty()) a else if (a.isEmpty()) b else (a.keys + b.keys).associateWith { (a[it] ?: 0L) + (b[it] ?: 0L) }
}

