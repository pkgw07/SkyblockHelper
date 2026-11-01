package sbhelper.core.calc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sbhelper.core.data.GameData
import sbhelper.core.model.Auction
import sbhelper.core.model.BazaarProduct
import sbhelper.core.model.ProfileSummary
import sbhelper.core.nbt.ItemParser
import sbhelper.core.nbt.SbItem

// ------------------------------------------------------------------ 도감 (Bestiary)
data class BestiaryEntry(val family: String, val section: String, val kills: Int, val tier: Int, val maxTier: Int,
                         val nextKills: Long?, val prevKills: Long) {
    val maxed get() = tier >= maxTier
    val progress get() = nextKills?.let { ((kills - prevKills).toDouble() / (it - prevKills).coerceAtLeast(1)).coerceIn(0.0, 1.0) } ?: 1.0
}

object BestiaryCalc {
    private fun norm(s: String) = s.lowercase().replace("'", "").replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** 앞에 붙는 말 (mayor_jerry_green, farming_chicken, pond_squid …) */
    private val PREFIXES = setOf("mayor", "farming", "pond", "master")

    private fun words(s: String) = s.split('_').filter { it.isNotEmpty() }.toSet()

    /**
     * API 키(zombie_1, magma_cube_75, chillblade_31 …)를 위키 가족에 맞춰 합친다.
     * 순서: 가족 이름과 같음 → 위키 몹 이름과 같음 → 단어가 같음(순서 무관, 앞말 무시) → 끝이 같음
     */
    /** API 이름이 위키 가족 이름과 많이 다른 몹 (게임 안 이름 기준) */
    private val ALIASES = mapOf(
        "obsidian_wither" to "Obsidian Defender", "corrupted_protector" to "End Stone Protector",
        "ice_walker" to "Glacite Walker", "crystal_sentry" to "Star Sentry", "zombie_deep" to "Miner Zombie",
        "sea_guardian" to "Guardian Defender", "bal_boss" to "Bal", "tentaclees" to "Tentacle", "master_tentaclees" to "Tentacle",
        "pest_worm" to "Earthworm", "horseman_horse" to "Headless Horseman", "frosty_the_snowman" to "Frosty",
        "diamond_guy" to "Angry Archaeologist", "master_diamond_guy" to "Angry Archaeologist",
    )

    fun familyOf(data: GameData, base: String, famNorm: Map<String, String>): String? {
        famNorm[base]?.let { return it }
        ALIASES[base]?.let { a -> if (famNorm.containsValue(a)) return a }
        // 고블린 계열 (goblin_knife_thrower, goblin_weakling_bow …) → Goblin
        if (base.startsWith("goblin_") && famNorm.containsValue("Goblin")) return "Goblin"
        data.bestiary.mobs[base]?.let { return it }
        val w = words(base)
        val core = w - PREFIXES
        famNorm.entries.firstOrNull { (n, _) -> words(n) == w || words(n) == core || words(n.replace("_s_", "_")) == core }?.let { return it.value }
        // 아포스트로피 가족 (Arachne's Brood → arachnes_brood, 키는 arachne_brood)
        famNorm.entries.firstOrNull { (n, _) -> n.replace("s_", "_") == base }?.let { return it.value }
        return famNorm.entries.firstOrNull { (n, _) -> base.endsWith("_$n") }?.value
    }

    fun entries(data: GameData, kills: Map<String, Int>): List<BestiaryEntry> {
        val famNorm = data.bestiary.families.associate { norm(it.family) to it.family }
        val byFamily = HashMap<String, Int>()
        kills.forEach { (k, v) ->
            if (k == "last_killed_mob") return@forEach
            val base = if (k.substringAfterLast('_').all { it.isDigit() }) k.substringBeforeLast('_') else k
            val fam = familyOf(data, base, famNorm) ?: return@forEach
            byFamily[fam] = (byFamily[fam] ?: 0) + v
        }
        return data.bestiary.families.map { f ->
            val k = byFamily[f.family] ?: 0
            val table = data.bestiary.brackets[f.bracket.toString()].orEmpty().take(f.maxTier)
            val tier = table.count { k >= it }
            BestiaryEntry(f.family, f.section, k, tier, f.maxTier, table.getOrNull(tier), table.getOrNull(tier - 1) ?: 0)
        }.sortedWith(compareBy({ it.section }, { it.family }))
    }
}

// ------------------------------------------------------------------ 미니언
data class MinionEntry(val id: String, val name: String, val tiers: Set<Int>) {
    val highest get() = tiers.maxOrNull() ?: 0
}

object MinionCalc {
    /** player_data.crafted_generators (예: COBBLESTONE_5) → 미니언별 만든 단계 */
    fun crafted(data: GameData, crafted: List<String>, itemName: (String) -> String): List<MinionEntry> {
        val byType = HashMap<String, MutableSet<Int>>()
        crafted.forEach { g ->
            val tier = g.substringAfterLast('_').toIntOrNull() ?: return@forEach
            byType.getOrPut(g.substringBeforeLast('_')) { mutableSetOf() }.add(tier)
        }
        val all = (data.sources.minions.keys + byType.keys).toSet()
        return all.map { id -> MinionEntry(id, data.sources.minions[id] ?: (itemName(id) + " Minion"), byType[id].orEmpty()) }
            .sortedWith(compareByDescending<MinionEntry> { it.highest }.thenBy { it.name })
    }
}

// ------------------------------------------------------------------ 시세 (바자 + 경매장 최저 BIN)
class PriceBook(val bazaar: Map<String, BazaarProduct>, val lowestBin: Map<String, Double>, val npc: Map<String, Double>) {
    /** 팔 때 받을 수 있는 값 (바자 즉시 판매가 → 최저 BIN → NPC 판매가) */
    fun valueOf(id: String): Double? =
        bazaar[id]?.quickStatus?.sellPrice?.takeIf { it > 0 } ?: lowestBin[id] ?: npc[id]

    /** 살 때 드는 값 (바자 즉시 구매가 → 최저 BIN) */
    fun costOf(id: String): Double? = bazaar[id]?.quickStatus?.buyPrice?.takeIf { it > 0 } ?: lowestBin[id]

    companion object {
        /** 경매장 BIN 매물에서 아이템 ID 별 최저가 (펫은 PET_종류) */
        fun lowestBins(auctions: List<Auction>): Map<String, Double> {
            val out = HashMap<String, Double>()
            auctions.asSequence().filter { it.bin && it.bids.isEmpty() }.forEach { a ->
                val raw = when (val b = a.itemBytes) {
                    is JsonPrimitive -> b.content
                    is JsonObject -> (b["data"] as? JsonPrimitive)?.content
                    else -> null
                } ?: return@forEach
                val item = ItemParser.parse(raw).firstOrNull() ?: return@forEach
                val id = item.pet?.let { "PET_${it.type}" } ?: item.skyblockId ?: return@forEach
                if (a.startingBid < (out[id] ?: Double.MAX_VALUE)) out[id] = a.startingBid
            }
            return out
        }
    }
}

// ------------------------------------------------------------------ 빠진 장신구
data class MissingAccessory(val acc: GameData.Accessory, val upgrade: Boolean, val price: Double?, val ease: Int)

object AccessoryCalc {
    /** 가진 장신구 ID (장신구 가방 + 인벤토리 등) */
    fun owned(items: List<SbItem>): Set<String> = items.mapNotNull { it.skyblockId }.toSet()

    /**
     * 업그레이드 줄(Talisman → Ring → Artifact …)마다 가진 것보다 한 단계 위, 또는 하나도 없으면 첫 단계를 보여 준다.
     */
    fun missing(data: GameData, owned: Set<String>, prices: PriceBook?): List<MissingAccessory> {
        val byId = data.accessories.associateBy { it.id }
        val roots = data.accessories.filter { it.from == null || it.from !in byId }
        val out = mutableListOf<MissingAccessory>()
        for (root in roots) {
            val line = generateSequence(root) { a -> a.to?.let { byId[it] } }.take(8).toList()
            val best = line.indexOfLast { it.id in owned }
            val next = line.getOrNull(best + 1) ?: continue
            val price = next.coins ?: prices?.costOf(next.id)
            val ease = when (next.category) { "NPC 상점" -> 0; "컬렉션 제작" -> 1; "기타 제작" -> 2; "슬레이어" -> 3; "몹 드롭" -> 4; "퀘스트" -> 4; "던전" -> 5; else -> 6 }
            out += MissingAccessory(next, best >= 0, price, ease)
        }
        return out
    }
}

// ------------------------------------------------------------------ 순자산
data class Networth(
    val total: Double,
    val parts: Map<String, Double>,
    /** 값을 모르는 아이템 수 */
    val unpriced: Int,
    val top: List<Pair<SbItem, Double>>,
)

object NetworthCalc {
    fun compute(profile: ProfileSummary, prices: PriceBook): Networth {
        val parts = LinkedHashMap<String, Double>()
        parts["지갑"] = profile.purse ?: 0.0
        parts["은행"] = profile.bank ?: 0.0
        parts["자루"] = profile.sacks.orEmpty().entries.sumOf { (id, n) -> (prices.valueOf(id) ?: 0.0) * n }
        var unpriced = 0
        val valued = mutableListOf<Pair<SbItem, Double>>()
        val groups = mapOf(
            "인벤토리" to listOf("inv_contents", "inv_armor", "equipment_contents"),
            "엔더 상자·백팩" to profile.inventories.keys.filter { it == "ender_chest_contents" || it.startsWith("backpack_") },
            "옷장" to listOf("wardrobe_contents"),
            "장신구" to listOf("talisman_bag"),
            "기타 가방·금고" to listOf("personal_vault_contents", "potion_bag", "fishing_bag", "quiver"),
        )
        groups.forEach { (label, keys) ->
            var sum = 0.0
            keys.forEach { k ->
                ItemParser.parse(profile.inventories[k]).filterNotNull().forEach { item ->
                    val id = item.pet?.let { "PET_${it.type}" } ?: item.skyblockId ?: return@forEach
                    val v = prices.valueOf(id)
                    if (v == null) unpriced++ else {
                        val total = v * item.count
                        sum += total
                        valued += item to total
                    }
                }
            }
            parts[label] = sum
        }
        // 옷장 (공식 API loadout)
        profile.wardrobe?.let { w ->
            var sum = 0.0
            (w.armor.values + w.equipment.values).flatten().filterNotNull().forEach { data ->
                ItemParser.parse(data).filterNotNull().forEach { item ->
                    val id = item.skyblockId ?: return@forEach
                    val v = prices.valueOf(id)
                    if (v == null) unpriced++ else { sum += v * item.count; valued += item to v * item.count }
                }
            }
            parts["옷장"] = (parts["옷장"] ?: 0.0) + sum
        }
        return Networth(parts.values.sum(), parts, unpriced, valued.sortedByDescending { it.second }.take(15))
    }
}
