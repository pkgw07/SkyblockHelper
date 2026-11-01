package sbhelper.core.calc

import sbhelper.core.data.GameData
import sbhelper.core.model.SkyblockItem

/** 테크트리 한 칸: 아이템 하나 (갑옷은 한 세트) */
data class GearStep(
    val ids: List<String>,
    val title: String,
    /** 바로 앞 칸을 재료로 업그레이드/제작하는 관계 */
    val upgradeFromPrev: Boolean,
    val rarity: String?,
)

data class GearCategory(val key: String, val label: String, val steps: List<GearStep>)

/**
 * 스킬별 테크트리를 종류(도구·갑옷·장비·펫·장신구)로 나누고, 각각 초반 → 후반 순서로 정렬한다.
 *
 * - 아이템 목록: 공식 위키 스킬 가이드에 나온 아이템 + 위키 Pets 목록 + 위키 Accessories 목록(이름으로 고름)
 * - 종류: 공식 API 아이템 category (HELMET, NECKLACE, DRILL …)
 * - 순서: 등급(Common → Mythic) → 위키 가이드에 나온 순서, 그리고 업그레이드/제작 관계(앞 단계가 반드시 먼저)
 */
object TechTreeCalc {
    val CATEGORIES = linkedMapOf(
        "tool" to "🛠 도구 · 무기",
        "armor" to "🛡 갑옷",
        "equipment" to "📿 장비 (목걸이·망토·벨트·장갑)",
        "pet" to "🐾 펫",
        "accessory" to "💍 장신구",
        "rodpart" to "🎣 낚싯대 부품",
        "bait" to "🪱 미끼",
        "other" to "📦 그 밖의 아이템",
    )

    /** 스킬별 관련 장신구를 위키 장신구 목록에서 고를 때 쓰는 이름 조각 */
    val ACCESSORY_WORDS = mapOf(
        "MINING" to listOf("Mine", "Mining", "Pickaxe", "Gemstone", "Dwarven", "Mithril", "Titanium", "Glacite", "Powder", "Crystal"),
        "FARMING" to listOf("Farm", "Crop", "Pest", "Squash", "Fermento", "Garden", "Cropie", "Melon", "Pumpkin"),
        "FORAGING" to listOf("Forag", "Wood", "Tree", "Log", "Park", "Leaf", "Fig", "Mangrove"),
        "FISHING" to listOf("Fish", "Sea ", "Bait", "Lava", "Water", "Shark", "Squid", "Angler"),
        "HUNTING" to listOf("Hunt", "Lasso", "Trap", "Shard"),
        "COMBAT" to listOf("Zombie", "Spider", "Wolf", "Enderman", "Blaze", "Vampire", "Slayer", "Bat ", "Catacombs"),
    )

    private val RARITY = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL")
    /** 공식 API 는 Common 아이템에 등급(tier)을 적지 않는다 → 없으면 Common */
    fun rarityRank(r: String?): Int = RARITY.indexOf(r?.uppercase()?.replace(' ', '_') ?: "COMMON").let { if (it < 0) 0 else it }

    private val TOOL = setOf("SWORD", "LONGSWORD", "BOW", "WAND", "FISHING_ROD", "FISHING_WEAPON", "PICKAXE", "DRILL", "AXE", "HOE",
        "SHOVEL", "GAUNTLET", "FISHING_NET", "LASSO", "SHEARS", "CHISEL", "FARMING_TOOL", "VACUUM", "SPADE", "WATERING_CAN", "TRAP")
    private val ARMOR = setOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
    private val EQUIP = setOf("NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET")
    private val PIECE = Regex("""_(HELMET|HAT|CHESTPLATE|TUNIC|LEGGINGS|TROUSERS|BOOTS|SLIPPERS)$""")

    fun category(id: String, items: Map<String, SkyblockItem>, data: GameData): String {
        if (id.startsWith("pet:")) return "pet"
        if (data.accessories.any { it.id == id }) return "accessory"
        val c = items[id]?.category?.uppercase().orEmpty()
        return when {
            c in TOOL -> "tool"
            c in ARMOR || PIECE.containsMatchIn(id) -> "armor"
            c in EQUIP -> "equipment"
            c == "ACCESSORY" -> "accessory"
            c == "FISHING_ROD_PART" -> "rodpart"
            c == "BAIT" -> "bait"
            else -> "other"
        }
    }

    /** 이 아이템의 이전 단계 (위키 upgrades_from → 장신구 from → 제작·포지 재료) 중 candidates 에 있는 것 */
    fun predecessor(data: GameData, id: String, candidates: Set<String>): String? {
        data.upgrades[id]?.let { if (it in candidates && it != id) return it }
        data.accessories.firstOrNull { it.id == id }?.from?.let { if (it in candidates && it != id) return it }
        val inputs = data.crafting[id]?.firstOrNull()?.`in`?.keys.orEmpty() + data.forge[id]?.firstOrNull()?.`in`?.keys.orEmpty()
        return inputs.firstOrNull { it in candidates && it != id }
    }

    fun build(data: GameData, items: Map<String, SkyblockItem>, skill: String, itemName: (String) -> String): List<GearCategory> {
        // 1) 위키 가이드에 나온 순서
        val guide = data.techTrees[skill].orEmpty().flatMap { it.items }.distinct()
        val pool = LinkedHashMap<String, Int>()
        guide.forEachIndexed { i, id -> pool.putIfAbsent(id, i) }
        data.gearPools[skill].orEmpty().forEachIndexed { i, id -> pool.putIfAbsent(id, 5_000 + i) }
        data.pets[skill].orEmpty().forEach { p -> pool.putIfAbsent("pet:${p.name}", 10_000) }
        val words = ACCESSORY_WORDS[skill].orEmpty()
        data.accessories.filter { a -> words.any { a.name.contains(it, ignoreCase = true) } }.forEach { pool.putIfAbsent(it.id, 20_000) }

        fun rarity(id: String): String? = if (id.startsWith("pet:"))
            data.pets[skill].orEmpty().firstOrNull { "pet:${it.name}" == id }?.rarity?.uppercase()?.takeIf { it.isNotBlank() }
        else items[id]?.tier

        return CATEGORIES.mapNotNull { (key, label) ->
            val ids = pool.keys.filter { category(it, items, data) == key }
            if (ids.isEmpty()) return@mapNotNull null
            val steps = when (key) {
                "armor" -> armorSteps(ids, pool, ::rarity, itemName)
                // 위키 펫 목록의 등급은 최고 등급이라 순서에 쓰지 않는다: 가이드에 나온 순서 → 이름
                "pet" -> ids.sortedWith(compareBy({ pool[it] ?: 0 }, { it }))
                    .map { GearStep(listOf(it), itemName(it), false, rarity(it)) }
                else -> orderedSteps(data, ids, pool, ::rarity, itemName).let { merged ->
                    val m = mergeTierFamilies(merged, itemName)
                    if (m === merged) m else relink(data, m.sortedBy { rarityRank(it.rarity) }, ids.toSet())
                }
            }
            GearCategory(key, label, steps)
        }
    }

    private fun orderedSteps(data: GameData, ids: List<String>, pool: Map<String, Int>, rarity: (String) -> String?,
                             itemName: (String) -> String): List<GearStep> {
        val set = ids.toSet()
        val order = ids.sortedWith(compareBy({ rarityRank(rarity(it)) }, { pool[it] ?: 0 })).toMutableList()
        // 업그레이드 관계: 이전 단계가 뒤에 있으면 그 바로 뒤로 옮긴다
        repeat(order.size) {
            var moved = false
            for (i in order.indices) {
                val p = predecessor(data, order[i], set) ?: continue
                val pi = order.indexOf(p)
                if (pi > i) { val x = order.removeAt(i); order.add(order.indexOf(p) + 1, x); moved = true; break }
            }
            if (!moved) return@repeat
        }
        return order.mapIndexed { i, id ->
            val prev = order.getOrNull(i - 1)
            GearStep(listOf(id), itemName(id), prev != null && predecessor(data, id, set) == prev, rarity(id))
        }
    }

    /** 순서를 바꾼 뒤 "앞 칸에서 업그레이드" 표시를 다시 계산 */
    private fun relink(data: GameData, steps: List<GearStep>, set: Set<String>): List<GearStep> = steps.mapIndexed { i, st ->
        val prev = steps.getOrNull(i - 1)
        val link = when {
            prev == null -> false
            st.ids.size == 1 && prev.ids.size == 1 -> predecessor(data, st.ids[0], set) == prev.ids[0]
            st.ids.size > 1 && prev.ids.size > 1 -> st.ids.any { a -> prev.ids.any { b -> family(a) == family(b) } }
            else -> false
        }
        st.copy(upgradeFromPrev = link)
    }

    private val TIER_SUFFIX = Regex("""_(\d)$""")
    private fun family(id: String) = TIER_SUFFIX.replace(id, "")
    private fun tierOf(id: String) = TIER_SUFFIX.find(id)?.groupValues?.get(1)?.toInt() ?: 1

    /**
     * 같은 종류가 여러 갈래로 나뉜 도구(작물별 농사 도구 Mk. I~III 등)는 단계별로 한 칸에 묶는다.
     * 단계가 있는 갈래가 4개 이상일 때만.
     */
    private fun mergeTierFamilies(steps: List<GearStep>, itemName: (String) -> String): List<GearStep> {
        val ids = steps.flatMap { it.ids }
        val fams = ids.groupBy(::family).filter { (base, list) -> list.size >= 2 && list.any { TIER_SUFFIX.containsMatchIn(it) } }
        if (fams.size < 4) return steps
        val famIds = fams.values.flatten().toSet()
        val byTier = famIds.groupBy(::tierOf).toSortedMap()
        val out = mutableListOf<GearStep>()
        var inserted = false
        for (st in steps) {
            val id = st.ids.single()
            if (id !in famIds) { out += st; continue }
            if (inserted) continue
            byTier.forEach { (tier, list) ->
                out += GearStep(list, "전용 도구 ${tier}단계 (${list.size}종)", out.isNotEmpty() && tier > byTier.firstKey(), steps.firstOrNull { it.ids.single() in list }?.rarity)
            }
            inserted = true
        }
        return out
    }

    /** 갑옷은 세트(투구·갑옷·바지·신발)로 묶는다 */
    private fun armorSteps(ids: List<String>, pool: Map<String, Int>, rarity: (String) -> String?, itemName: (String) -> String): List<GearStep> {
        val sets = ids.groupBy { PIECE.replace(it, "").removePrefix("STARRED_") }
        return sets.values.sortedWith(compareBy({ s -> s.maxOf { rarityRank(rarity(it)) } }, { s -> s.minOf { pool[it] ?: 0 } }))
            .map { pieces ->
                val sorted = pieces.sortedBy { id -> listOf("HELMET", "HAT", "CHESTPLATE", "TUNIC", "LEGGINGS", "TROUSERS", "BOOTS", "SLIPPERS")
                    .indexOfFirst { id.endsWith(it) }.let { if (it < 0) 9 else it } }
                val title = itemName(sorted.first())
                    .replace(Regex("""\s+(Helmet|Hat|Chestplate|Tunic|Leggings|Trousers|Boots|Slippers)$"""), "")
                GearStep(sorted, if (pieces.size > 1) "$title 세트" else itemName(sorted.first()), false,
                    sorted.maxByOrNull { rarityRank(rarity(it)) }?.let(rarity))
            }
    }
}
