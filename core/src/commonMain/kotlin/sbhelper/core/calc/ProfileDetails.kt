package sbhelper.core.calc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import sbhelper.core.data.GameData

/** 펫 하나 (레벨은 위키 펫 레벨표로 계산) */
data class PetEntry(
    val type: String, val tier: String, val exp: Double, val level: Int, val maxLevel: Int, val progress: Double,
    val heldItem: String?, val candyUsed: Int, val active: Boolean, val skin: String?,
) {
    val name get() = type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
}

data class DungeonFloor(
    val floor: Int, val master: Boolean, val completions: Int, val fastestSPlusMs: Long?, val fastestSMs: Long?, val bestScore: Int?,
    /** 들어간 횟수 (클리어 못 한 판 포함, 기록이 없으면 null) */
    val timesPlayed: Int? = null,
)

data class ProfileDetails(
    // 개요
    val skyblockLevel: Double?,
    val magicalPower: Int?,
    val selectedPower: String?,
    val fairySouls: Int?,
    val petScore: Int?,
    val firstJoin: Long?,
    val cookieActive: Boolean?,
    val motes: Double?,
    // 펫
    val pets: List<PetEntry>,
    // 던전
    val floors: List<DungeonFloor>,
    val secrets: Long?,
    val selectedClass: String?,
    val highestFloor: Int?,
    // 크림슨 아일
    val faction: String?,
    val reputation: Map<String, Long>,
    val kuudra: Map<String, Int>,
    val dojo: Map<String, Int>,
    // 트로피 피시
    val trophyFish: Map<String, Map<String, Int>>,
    val trophyTotal: Int?,
    // 가든·제이콥
    val gardenXp: Double?,
    val gardenLevel: Int?,
    val cropUpgrades: Map<String, Int>,
    val visitorsServed: Int?,
    val visitorsUnique: Int?,
    val plots: Int?,
    val jacobMedals: Map<String, Int>,
    val jacobPersonalBests: Map<String, Long>,
    val jacobPerks: Map<String, Int>,
    val jacobUniqueGolds: Int,
    // 채광
    val powderTotals: Map<String, Long>,
    val crystals: Map<String, String>,
    val skyMall: String?,
    // 기타 통계
    val kills: List<Pair<String, Long>>,
    val deaths: List<Pair<String, Long>>,
    val totalKills: Long,
    val totalDeaths: Long,
    val auctions: Map<String, Double>,
    val itemsFished: Long?,
    val seaCreatureKills: Long?,
    val candy: Long?,
)

/**
 * 공식 프로필 API 의 원본(member)에서 SkyCrypt 수준 상세 기록을 꺼낸다.
 * 항목 위치는 실제 프로필 구조로 확인함 (2026-11, 진단 작업). 없는 항목은 null/빈 값.
 */
object ProfileDetailsCalc {
    private fun JsonElement?.at(vararg keys: String): JsonElement? {
        var cur: JsonElement? = this
        for (k in keys) cur = (cur as? JsonObject)?.get(k) ?: return null
        return cur
    }
    private fun JsonElement?.num(vararg keys: String): Double? = (at(*keys) as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.str(vararg keys: String): String? = (at(*keys) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.obj(vararg keys: String): JsonObject? = at(*keys) as? JsonObject
    private fun JsonObject?.numbers(): Map<String, Double> =
        this?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }?.toMap().orEmpty()

    /** 펫 레벨: 위키 표의 등급별 시작 위치부터 99칸 (레전더리 이상은 100 넘어 200까지 같은 값 반복) */
    fun petLevel(data: GameData, tier: String, exp: Double, type: String): Triple<Int, Int, Double> {
        val table = data.leveling.pet
        if (table.xp.isEmpty()) return Triple(1, 100, 0.0)
        val off = table.offset[tier.uppercase()] ?: 0
        val maxLevel = if (type in setOf("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON")) 200 else 100
        val steps = (0 until maxLevel - 1).map { i -> table.xp.getOrElse(off + i) { table.xp.getOrElse(off + 98) { table.xp.last() } } }
        var left = exp
        var level = 1
        for (need in steps) {
            if (left < need) return Triple(level, maxLevel, left / need)
            left -= need
            level++
        }
        return Triple(maxLevel, maxLevel, 1.0)
    }

    fun compute(member: JsonObject?, garden: JsonObject?, data: GameData): ProfileDetails? {
        member ?: return null
        val pets = (member.at("pets_data", "pets") as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val type = o.str("type") ?: return@mapNotNull null
            val tier = o.str("tier") ?: "COMMON"
            val exp = o.num("exp") ?: 0.0
            val (lv, max, prog) = petLevel(data, tier, exp, type)
            PetEntry(type, tier, exp, lv, max, prog, o.str("heldItem"), o.num("candyUsed")?.toInt() ?: 0,
                (o["active"] as? JsonPrimitive)?.booleanOrNull == true, o.str("skin"))
        }.sortedWith(compareByDescending<PetEntry> { it.active }.thenByDescending { TechTreeCalc.rarityRank(it.tier) }.thenByDescending { it.level })

        fun floors(type: String, master: Boolean): List<DungeonFloor> {
            val d = member.obj("dungeons", "dungeon_types", type) ?: return emptyList()
            val comp = d.obj("tier_completions").numbers()
            val played = d.obj("times_played").numbers()
            return (comp.keys + played.keys).mapNotNull { it.toIntOrNull() }.distinct().sorted().map { f ->
                val k = f.toString()
                DungeonFloor(f, master, comp[k]?.toInt() ?: 0, d.num("fastest_time_s_plus", k)?.toLong(), d.num("fastest_time_s", k)?.toLong(),
                    d.num("best_score", k)?.toInt(), played[k]?.toInt())
            }
        }

        val nether = member.obj("nether_island_player_data")
        val trophy = member.obj("trophy_fish")
        val trophyMap = HashMap<String, MutableMap<String, Int>>()
        trophy?.forEach { (k, v) ->
            val n = (v as? JsonPrimitive)?.doubleOrNull?.toInt() ?: return@forEach
            val tier = listOf("_bronze", "_silver", "_gold", "_diamond").firstOrNull { k.endsWith(it) }
            if (k == "total_caught" || k == "last_caught") return@forEach
            val fish = if (tier != null) k.removeSuffix(tier) else k
            trophyMap.getOrPut(fish) { linkedMapOf() }[tier?.removePrefix("_") ?: "total"] = n
        }

        val gardenXp = garden.num("garden_experience")
        val gardenLevel = gardenXp?.let { xp ->
            val t = data.leveling.garden
            if (t.isEmpty()) null else {
                var total = 0.0; var lv = 0
                for (need in t) { if (xp >= total + need) { total += need; lv++ } else break }
                lv.coerceAtLeast(1)
            }
        }
        val jacob = member.obj("jacobs_contest")
        val stats = member.obj("player_stats")
        fun topOf(o: JsonObject?): List<Pair<String, Long>> =
            o.numbers().filterKeys { it != "total" }.map { it.key to it.value.toLong() }.sortedByDescending { it.second }
        val kills = topOf(stats.obj("kills"))
        val deaths = topOf(stats.obj("deaths"))
        val mining = member.obj("mining_core")

        return ProfileDetails(
            skyblockLevel = member.num("leveling", "experience")?.let { it / data.timerRules.skyblockXpPerLevel },
            magicalPower = member.num("accessory_bag_storage", "highest_magical_power")?.toInt(),
            selectedPower = member.str("accessory_bag_storage", "selected_power"),
            fairySouls = member.num("fairy_soul", "total_collected")?.toInt(),
            petScore = member.num("leveling", "highest_pet_score")?.toInt(),
            firstJoin = member.num("profile", "first_join")?.toLong(),
            cookieActive = (member.at("profile", "cookie_buff_active") as? JsonPrimitive)?.booleanOrNull,
            motes = member.num("currencies", "motes_purse"),
            pets = pets,
            floors = floors("catacombs", false) + floors("master_catacombs", true),
            secrets = member.num("dungeons", "secrets")?.toLong(),
            selectedClass = member.str("dungeons", "selected_dungeon_class"),
            highestFloor = member.num("dungeons", "dungeon_types", "catacombs", "highest_tier_completed")?.toInt(),
            faction = nether.str("selected_faction"),
            reputation = listOfNotNull(nether.num("mages_reputation")?.let { "mages" to it.toLong() },
                nether.num("barbarians_reputation")?.let { "barbarians" to it.toLong() }).toMap(),
            kuudra = nether.obj("kuudra_completed_tiers").numbers().filterKeys { !it.startsWith("highest_wave") }.mapValues { it.value.toInt() },
            dojo = nether.obj("dojo").numbers().filterKeys { it.startsWith("dojo_points_") }.mapKeys { it.key.removePrefix("dojo_points_") }.mapValues { it.value.toInt() },
            trophyFish = trophyMap,
            trophyTotal = trophy.num("total_caught")?.toInt(),
            gardenXp = gardenXp,
            gardenLevel = gardenLevel,
            cropUpgrades = garden.obj("crop_upgrade_levels").numbers().mapValues { it.value.toInt() },
            visitorsServed = garden.num("commission_data", "total_completed")?.toInt(),
            visitorsUnique = garden.num("commission_data", "unique_npcs_served")?.toInt(),
            plots = (garden.at("unlocked_plots_ids") as? JsonArray)?.size,
            jacobMedals = jacob.obj("medals_inv").numbers().mapValues { it.value.toInt() },
            jacobPersonalBests = jacob.obj("personal_bests").numbers().mapValues { it.value.toLong() },
            jacobPerks = jacob.obj("perks").numbers().mapValues { it.value.toInt() },
            jacobUniqueGolds = (jacob.at("unique_brackets", "gold") as? JsonArray)?.size ?: 0,
            powderTotals = listOf("mithril", "gemstone", "glacite").mapNotNull { t ->
                mining.num("powder_${t}_total")?.let { t to it.toLong() }
            }.toMap(),
            crystals = mining.obj("crystals")?.mapNotNull { (k, v) -> (v as? JsonObject).str("state")?.let { k to it } }?.toMap().orEmpty(),
            skyMall = mining.str("current_daily_effect"),
            kills = kills, deaths = deaths,
            totalKills = stats.num("kills", "total")?.toLong() ?: kills.sumOf { it.second },
            totalDeaths = stats.num("deaths", "total")?.toLong() ?: deaths.sumOf { it.second },
            auctions = stats.obj("auctions").numbers(),
            itemsFished = stats.num("items_fished", "total")?.toLong(),
            seaCreatureKills = stats.num("sea_creature_kills")?.toLong(),
            candy = stats.num("candy_collected", "total")?.toLong(),
        )
    }
}
