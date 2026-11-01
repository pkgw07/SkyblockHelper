package sbhelper.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

enum class GameMode(val apiValue: String?, val label: String, val badge: String) {
    NORMAL(null, "일반", ""),
    IRONMAN("ironman", "Ironman", "♲"),
    STRANDED("island", "Stranded", "☀"),
    BINGO("bingo", "Bingo", "Ⓑ");

    /** 바자/경매장을 쓸 수 있는 모드인가 */
    val canTrade get() = this == NORMAL

    companion object {
        fun from(value: String?): GameMode = entries.firstOrNull { it.apiValue == value } ?: NORMAL
    }
}

data class ForgeProcess(
    val forgeType: String,   // 보통 "forge_1"
    val slot: Int,
    val itemId: String,
    val startTime: Long,
)

data class PetInfo(val type: String, val tier: String, val exp: Double, val active: Boolean)

/** 프로필 JSON 에서 앱이 쓰는 값만 뽑은 요약. 값이 없으면(API 설정 꺼짐 등) null. */
data class ProfileSummary(
    val profileId: String,
    val cuteName: String,
    val selected: Boolean,
    val gameMode: GameMode,
    val memberCount: Int,
    val purse: Double?,
    val bank: Double?,
    val skyblockXp: Double?,
    val fairySouls: Int?,
    /** 키: FARMING, MINING ... (공식 skills 리소스 키와 같음). null 이면 스킬 API 꺼짐 */
    val skillXp: Map<String, Double>?,
    val farmingCapBonus: Int,
    /** George 에게 준 펫 종류 수 → Taming 최대 레벨 50 + n (위키 Taming). null = 정보 없음 */
    val tamingCapBonus: Int? = null,
    val slayerXp: Map<String, Double>,
    val catacombsXp: Double?,
    val classXp: Map<String, Double>,
    val selectedClass: String?,
    val hotmXp: Double?,
    val quickForgeLevel: Int,
    val forge: List<ForgeProcess>,
    /** null 이면 인벤토리 API 꺼짐 */
    val sacks: Map<String, Long>?,
    /** null 이면 컬렉션 API 꺼짐 */
    val collections: Map<String, Long>?,
    val essence: Map<String, Long>,
    val pets: List<PetInfo>,
    /** 카타콤 층별 클리어 횟수 (0 = Entrance, 1 = Floor I …). 없으면 비어 있음 */
    val floorCompletions: Map<Int, Int> = emptyMap(),
    val masterCompletions: Map<Int, Int> = emptyMap(),
    /** 슬레이어 보스별, 단계(1~5)별 처치 수 */
    val slayerBossKills: Map<String, Map<Int, Int>> = emptyMap(),
    /** 인벤토리 원본 (키 → base64 NBT). 키: inv_contents, talisman_bag, backpack_0 … */
    val inventories: Map<String, String> = emptyMap(),
    /** HotM 트리 노드 → 레벨 (API 의 노드 키 그대로) */
    val hotmNodes: Map<String, Int> = emptyMap(),
    /** 도감 처치 수 (API 키 그대로, 예: zombie_1) */
    val bestiaryKills: Map<String, Int> = emptyMap(),
    /** 만든 미니언 (예: COBBLESTONE_5) */
    val craftedMinions: List<String> = emptyList(),
    /** 파우더 (mithril, gemstone, glacite) 현재 보유량 */
    val powders: Map<String, Long> = emptyMap(),
) {
    val skyblockLevel: Double? get() = skyblockXp?.div(100.0)

    /**
     * 사람마다 다른 스킬 최대 레벨 (공식 위키):
     *  - Farming: 기본 50, 제이콥 대회 보상(Anita)으로 최대 60
     *  - Taming: 기본 50, George 에게 펫을 주면 최대 60
     */
    fun skillCap(key: String, maxLevel: Int): Int? = when (key) {
        "FARMING" -> (50 + farmingCapBonus).coerceAtMost(maxLevel)
        "TAMING" -> tamingCapBonus?.let { (50 + it).coerceAtMost(maxLevel) }
        else -> null
    }
    val activePet get() = pets.firstOrNull { it.active }
}

/**
 * Hypixel 프로필 JSON 을 읽는다.
 * 공식 문서에 멤버 내부 구조가 자세히 나와 있지 않아서, 모든 값을 "있으면 읽고 없으면 null" 방식으로 읽는다.
 */
object ProfileParser {

    fun parse(profile: JsonObject, playerUuid: String): ProfileSummary? {
        val uuid = playerUuid.replace("-", "")
        val members = profile.obj("members") ?: return null
        val member = members.obj(uuid) ?: return null
        val activeMembers = members.values.count { (it as? JsonObject)?.path("profile", "deletion_notice") == null }

        val experience = member.obj("player_data", "experience")
        val skillXp = experience?.entries
            ?.filter { it.key.startsWith("SKILL_") }
            ?.associate { it.key.removePrefix("SKILL_") to (it.value.num() ?: 0.0) }

        val forge = buildList {
            member.obj("forge", "forge_processes")?.forEach { (forgeType, slots) ->
                (slots as? JsonObject)?.forEach { (slotKey, value) ->
                    val p = value as? JsonObject ?: return@forEach
                    val id = p.str("id") ?: return@forEach
                    val start = p.long("startTime") ?: return@forEach
                    add(ForgeProcess(forgeType, p.long("slot")?.toInt() ?: slotKey.toIntOrNull() ?: 0, id, start))
                }
            }
        }.sortedBy { it.slot }

        val pets = (member.path("pets_data", "pets") as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PetInfo(o.str("type") ?: return@mapNotNull null, o.str("tier") ?: "COMMON",
                o.num("exp") ?: 0.0, o.bool("active") ?: false)
        }.orEmpty()

        return ProfileSummary(
            profileId = profile.str("profile_id") ?: "",
            cuteName = profile.str("cute_name") ?: "Profile",
            selected = profile.bool("selected") ?: false,
            gameMode = GameMode.from(profile.str("game_mode")),
            memberCount = activeMembers,
            purse = member.num("currencies", "coin_purse"),
            bank = profile.num("banking", "balance"),
            skyblockXp = member.num("leveling", "experience"),
            fairySouls = member.num("fairy_soul", "total_collected")?.toInt(),
            skillXp = skillXp,
            farmingCapBonus = member.num("jacobs_contest", "perks", "farming_level_cap")?.toInt() ?: 0,
            tamingCapBonus = (member.path("pets_data", "pet_care", "pet_types_sacrificed") as? JsonArray)?.size,
            slayerXp = member.obj("slayer", "slayer_bosses")?.mapNotNull { (boss, v) ->
                (v as? JsonObject)?.num("xp")?.let { boss to it }
            }?.toMap().orEmpty(),
            catacombsXp = member.num("dungeons", "dungeon_types", "catacombs", "experience"),
            classXp = member.obj("dungeons", "player_classes")?.mapNotNull { (c, v) ->
                (v as? JsonObject)?.num("experience")?.let { c to it }
            }?.toMap().orEmpty(),
            selectedClass = member.str("dungeons", "selected_dungeon_class"),
            // HotM 데이터는 skill_tree(새 구조)와 mining_core(옛 구조) 중 있는 쪽, 둘 다 있으면 큰 값
            hotmXp = listOfNotNull(
                member.num("skill_tree", "experience", "mining"),
                member.num("mining_core", "experience"),
            ).maxOrNull(),
            quickForgeLevel = quickForge(hotmNodes(member)),
            forge = forge,
            sacks = member.obj("inventory", "sacks_counts")?.mapNotNull { (k, v) ->
                v.num()?.toLong()?.takeIf { it > 0 }?.let { k to it }
            }?.toMap(),
            // 컬렉션은 협동 프로필이면 모든 멤버의 합계 (게임 안 표시와 같게)
            collections = coopCollections(members, member),
            essence = member.obj("currencies", "essence")?.mapNotNull { (k, v) ->
                (v as? JsonObject)?.num("current")?.toLong()?.let { k to it }
            }?.toMap().orEmpty(),
            pets = pets,
            floorCompletions = completions(member, "catacombs"),
            masterCompletions = completions(member, "master_catacombs"),
            inventories = inventories(member),
            hotmNodes = hotmNodes(member),
            bestiaryKills = member.obj("bestiary", "kills")?.mapNotNull { (k, v) -> v.num()?.toInt()?.let { k to it } }?.toMap().orEmpty(),
            craftedMinions = (member.path("player_data", "crafted_generators") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
            powders = listOf("mithril", "gemstone", "glacite").mapNotNull { t ->
                (member.num("mining_core", "powder_$t") ?: member.num("skill_tree", "powder", t)
                    ?: member.num("currencies", "powder_$t"))?.let { t to it.toLong() }
            }.toMap(),
            slayerBossKills = member.obj("slayer", "slayer_bosses")?.mapNotNull { (boss, v) ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val kills = o.entries.mapNotNull { (k, n) ->
                    Regex("""boss_kills_tier_(\d+)""").matchEntire(k)?.let { m -> (m.groupValues[1].toInt() + 1) to (n.num()?.toInt() ?: 0) }
                }.toMap()
                boss to kills
            }?.toMap().orEmpty(),
        )
    }

    /** HotM 노드: 새 구조(skill_tree.nodes.mining) 와 옛 구조(mining_core.nodes) 를 합친다 (큰 값) */
    fun hotmNodes(member: JsonObject): Map<String, Int> {
        val out = HashMap<String, Int>()
        listOfNotNull(member.obj("skill_tree", "nodes", "mining"), member.obj("mining_core", "nodes")).forEach { o ->
            o.forEach { (k, v) ->
                val n = (v as? JsonPrimitive)?.let { it.doubleOrNull?.toInt() ?: it.booleanOrNull?.let { b -> if (b) 1 else 0 } }
                if (n != null && !k.startsWith("toggle_")) out[k] = maxOf(out[k] ?: 0, n)
            }
        }
        return out
    }

    /** Quick Forge 노드 레벨. 키 이름이 바뀌어도 찾도록 forge 가 들어간 키를 찾는다 */
    fun quickForge(nodes: Map<String, Int>): Int =
        nodes["forge_time"] ?: nodes["quick_forge"]
            ?: nodes.entries.firstOrNull { it.key.contains("forge", ignoreCase = true) }?.value ?: 0

    private fun inventories(member: JsonObject): Map<String, String> {
        val inv = member.obj("inventory") ?: return emptyMap()
        val out = HashMap<String, String>()
        fun data(o: JsonElement?) = ((o as? JsonObject)?.get("data") as? JsonPrimitive)?.content
        listOf("inv_contents", "inv_armor", "equipment_contents", "ender_chest_contents", "wardrobe_contents", "personal_vault_contents")
            .forEach { k -> data(inv[k])?.let { out[k] = it } }
        inv.obj("bag_contents")?.forEach { (k, v) -> data(v)?.let { out[k] = it } }
        inv.obj("backpack_contents")?.forEach { (k, v) -> data(v)?.let { out["backpack_$k"] = it } }
        return out
    }

    /** 진단용: JSON 의 키 구조만 (값 없이) */
    fun structure(e: JsonElement?, depth: Int = 0, maxDepth: Int = 5): String = when {
        e is JsonObject && depth < maxDepth -> e.entries.joinToString(", ", "{", "}") { (k, v) ->
            if (k.length > 24 && k.all { it.isLetterOrDigit() }) "<id>:${structure(v, depth + 1, maxDepth)}"
            else "$k:${structure(v, depth + 1, maxDepth)}"
        }.let { if (it.length > 2000) it.take(2000) + "…}" else it }
        e is JsonObject -> "{…${e.size}}"
        e is JsonArray -> "[${e.size}]"
        e is JsonPrimitive && e.isString -> if (e.content.length > 40) "str(${e.content.length})" else "str"
        e is JsonPrimitive -> "num"
        else -> "null"
    }

    private fun coopCollections(members: JsonObject, me: JsonObject): Map<String, Long>? {
        if (me.obj("collection") == null) return null
        val total = HashMap<String, Long>()
        members.values.forEach { m ->
            (m as? JsonObject)?.obj("collection")?.forEach { (k, v) ->
                v.num()?.toLong()?.let { total[k] = (total[k] ?: 0L) + it }
            }
        }
        return total
    }

    private fun completions(member: JsonObject, type: String): Map<Int, Int> =
        member.obj("dungeons", "dungeon_types", type, "tier_completions")?.mapNotNull { (k, v) ->
            k.toIntOrNull()?.let { it to (v.num()?.toInt() ?: 0) }
        }?.toMap().orEmpty()

    // ---- 작은 JSON 도우미 ----
    fun JsonObject.path(vararg keys: String): JsonElement? {
        var cur: JsonElement? = this
        for (k in keys) cur = (cur as? JsonObject)?.get(k) ?: return null
        return cur
    }

    private fun JsonObject.obj(vararg keys: String) = path(*keys) as? JsonObject
    private fun JsonObject.str(vararg keys: String) = (path(*keys) as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(vararg keys: String) = path(*keys).num()
    private fun JsonObject.long(vararg keys: String) = (path(*keys) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(vararg keys: String) = (path(*keys) as? JsonPrimitive)?.booleanOrNull
    private fun JsonElement?.num() = (this as? JsonPrimitive)?.doubleOrNull
}
