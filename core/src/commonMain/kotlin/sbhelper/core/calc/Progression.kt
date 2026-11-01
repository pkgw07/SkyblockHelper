package sbhelper.core.calc

import sbhelper.core.data.GameData
import sbhelper.core.model.ProfileSummary
import sbhelper.core.model.SkillsResponse

enum class Track(val label: String) {
    AREAS("지역 해금"),
    MINING("채광 · 포지"),
    DUNGEONS("던전"),
    SLAYER("슬레이어"),
}

enum class NodeStatus { DONE, NEXT, LOCKED }

/** 진행 트리의 한 칸 */
data class ProgressNode(
    val id: String,
    val track: Track,
    val title: String,
    /** 조건 설명 (위키 기준) */
    val requirement: String,
    /** 지금 상태 설명 */
    val current: String,
    val status: NodeStatus,
    /** 다음 목표까지 진행도 0.0 ~ 1.0 */
    val progress: Double,
    /** 공식 위키 문서 이름 */
    val wikiPage: String,
)

/**
 * 진행 방향 추천.
 * 해금 조건은 모두 공식 위키 데이터(gamedata.progression)에서 오고,
 * 여기서는 내 프로필과 비교해 "완료 / 다음 목표 / 잠김"을 나누고 가까운 목표부터 추천한다.
 */
object ProgressionPlanner {

    fun build(data: GameData, profile: ProfileSummary, skills: SkillsResponse?): List<ProgressNode> {
        val p = data.progression
        val nodes = mutableListOf<ProgressNode>()
        val skillLevel: (String) -> Int? = { key ->
            val def = skills?.skills?.get(key)
            val xp = profile.skillXp?.get(key)
            if (def != null && xp != null) Leveling.skill(xp, def, profile.skillCap(key, def.maxLevel)).level else null
        }
        val hotmTier = profile.hotmXp?.takeIf { data.leveling.hotm.isNotEmpty() }?.let { Leveling.hotm(it, data.leveling.hotm) }
        val sbLevel = profile.skyblockLevel

        // ---- 지역 해금 ----
        for (area in p.areas) {
            val parts = area.requires.map { r ->
                when (r.type) {
                    "skill" -> {
                        val have = skillLevel(r.skill ?: "")
                        Triple("${skillName(r.skill)} ${r.level}", have?.let { "${skillName(r.skill)} $it" } ?: "스킬 API 꺼짐",
                            ratio(have?.toDouble(), r.level))
                    }
                    "sblevel" -> Triple("SkyBlock Level ${r.level}", sbLevel?.let { "SkyBlock Level ${it.toInt()}" } ?: "-",
                        ratio(sbLevel, r.level))
                    "hotm" -> Triple("HotM ${r.tier}", hotmTier?.let { "HotM ${it.level}" } ?: "-",
                        ratio(hotmTier?.level?.toDouble(), r.tier))
                    else -> Triple(r.type, "-", 0.0)
                }
            }
            val progress = parts.minOfOrNull { it.third } ?: 0.0
            nodes += ProgressNode(
                id = "area:${area.name}", track = Track.AREAS, title = area.name,
                requirement = parts.joinToString(" + ") { it.first },
                current = parts.joinToString(" · ") { it.second },
                status = if (progress >= 1.0) NodeStatus.DONE else NodeStatus.NEXT,
                progress = progress, wikiPage = area.page,
            )
        }

        // ---- HotM 단계 → 포지 슬롯 ----
        val hotmTable = data.leveling.hotm
        if (hotmTable.isNotEmpty()) {
            val cumulative = Leveling.perLevelToCumulative(hotmTable.map { it })
            val tier = hotmTier?.level ?: 0
            for (t in 2..hotmTable.size) {
                val slots = p.hotmForgeSlots[t.toString()]
                val prevSlots = p.hotmForgeSlots[(t - 1).toString()] ?: 0
                val unlocksArea = p.areas.filter { a -> a.requires.any { it.type == "hotm" && it.tier == t } }.map { it.name }
                val reward = buildList {
                    if (slots != null && slots > prevSlots) add(if (t == 2) "포지 사용 · 슬롯 $slots" else "포지 슬롯 $slots")
                    unlocksArea.forEach { add("$it 입장") }
                }
                val need = cumulative.getOrElse(t - 1) { 0.0 }
                val xp = profile.hotmXp ?: 0.0
                nodes += ProgressNode(
                    id = "hotm:$t", track = Track.MINING, title = "Heart of the Mountain $t",
                    requirement = "HotM XP ${need.toLong()}" + if (reward.isNotEmpty()) " → ${reward.joinToString(", ")}" else "",
                    current = "HotM $tier",
                    status = when { tier >= t -> NodeStatus.DONE; t == tier + 1 -> NodeStatus.NEXT; else -> NodeStatus.LOCKED },
                    progress = if (need <= 0) 1.0 else (xp / need).coerceIn(0.0, 1.0),
                    wikiPage = "Heart of the Mountain",
                )
            }
        }

        // ---- 던전 층 (위키: 층을 깨면 다음 층이 열린다) ----
        val floors = p.dungeonFloors
        if (floors.isNotEmpty()) {
            val cleared = floors.indices.filter { (profile.floorCompletions[it] ?: 0) > 0 }
            val highest = cleared.maxOrNull() ?: -1
            floors.forEachIndexed { i, name ->
                nodes += ProgressNode(
                    id = "floor:$i", track = Track.DUNGEONS, title = "Catacombs $name",
                    requirement = if (i == 0) "던전 입장" else "${floors[i - 1]} 클리어",
                    current = "클리어 ${profile.floorCompletions[i] ?: 0}회",
                    status = when { i <= highest -> NodeStatus.DONE; i == highest + 1 -> NodeStatus.NEXT; else -> NodeStatus.LOCKED },
                    progress = if (i <= highest) 1.0 else 0.0,
                    wikiPage = if (i == 0) "The Catacombs - Entrance" else "The Catacombs - $name",
                )
            }
            if (p.masterModeAfterFloorVII) {
                val f7 = (profile.floorCompletions[floors.size - 1] ?: 0) > 0
                val anyMaster = profile.masterCompletions.values.any { it > 0 }
                nodes += ProgressNode(
                    id = "master", track = Track.DUNGEONS, title = "Master Mode",
                    requirement = "${floors.last()} 클리어 (Necron 처치)", current = if (anyMaster) "진행 중" else "-",
                    status = when { anyMaster -> NodeStatus.DONE; f7 -> NodeStatus.NEXT; else -> NodeStatus.LOCKED },
                    progress = if (anyMaster) 1.0 else 0.0, wikiPage = "Master Mode",
                )
            }
        }

        // ---- 슬레이어 해금 순서 ----
        for (u in p.slayerUnlocks) {
            val xp = profile.slayerXp[u.boss] ?: 0.0
            val req = u.requires
            val unlocked = xp > 0 || (req == null && u.note.contains("default", true)) ||
                (req?.boss != null && (profile.slayerBossKills[req.boss]?.filterKeys { it >= req.tier }?.values?.sum() ?: 0) > 0)
            val prereqUnlocked = req?.boss == null || (profile.slayerXp[req.boss] ?: 0.0) > 0
            val table = data.leveling.slayerXp[u.boss]
            val level = table?.let { Leveling.slayer(xp, it) }
            nodes += ProgressNode(
                id = "slayer:${u.boss}", track = Track.SLAYER, title = u.name,
                requirement = when {
                    req?.boss != null -> "${bossName(p, req.boss)} Tier ${req.tier} 처치"
                    u.note.isNotBlank() -> u.note
                    else -> "-"
                } + (table?.let { " · 최대 LVL ${it.size}" } ?: ""),
                current = if (unlocked) "LVL ${level?.level ?: 0}" else "미해금",
                status = when {
                    unlocked && level?.maxed == true -> NodeStatus.DONE
                    unlocked || prereqUnlocked -> NodeStatus.NEXT
                    else -> NodeStatus.LOCKED
                },
                progress = if (!unlocked) 0.0 else level?.let { (it.level + it.progress) / it.maxLevel } ?: 0.0,
                wikiPage = u.name,
            )
        }
        return nodes
    }

    /** 지금 하면 좋은 목표: 진행 중인 것 중 거의 다 온 것부터 */
    fun recommend(nodes: List<ProgressNode>, count: Int = 3): List<ProgressNode> =
        nodes.filter { it.status == NodeStatus.NEXT }
            .sortedWith(compareByDescending<ProgressNode> { it.progress }.thenBy { it.track.ordinal })
            .distinctBy { it.track }
            .take(count)

    private fun ratio(have: Double?, need: Int?): Double =
        if (have == null || need == null || need <= 0) 0.0 else (have / need).coerceIn(0.0, 1.0)

    private fun skillName(key: String?) = key?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""

    private fun bossName(p: GameData.Progression, boss: String) = p.slayerUnlocks.firstOrNull { it.boss == boss }?.name ?: boss
}
