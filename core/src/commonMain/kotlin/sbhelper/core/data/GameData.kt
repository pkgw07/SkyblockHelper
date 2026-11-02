package sbhelper.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sbhelper.core.api.SbJson

/**
 * 공식 Hypixel SkyBlock 위키(hypixelskyblock.minecraft.wiki)에서 만든 게임 데이터.
 * tools/wiki_data.py 가 생성하고, GitHub Actions 가 매일 새로 만들어 data 브랜치에 올린다.
 * 앱은 최신 data 브랜치 파일을 받아 쓰고, 받지 못하면 앱에 들어 있는 파일을 쓴다.
 *
 * 아이템 키: Hypixel 공식 API 의 아이템 ID. 공식 ID 와 이름이 맞지 않는 위키 항목은 "name:이름".
 */
@Serializable
data class GameData(
    val source: Source = Source(),
    val names: Map<String, String> = emptyMap(),
    val crafting: Map<String, List<CraftRecipe>> = emptyMap(),
    /** 위키와 같은 조합대 칸 배치 (결과 ID → 조합법들) */
    val craftGrids: Map<String, List<CraftGrid>> = emptyMap(),
    val forge: Map<String, List<ForgeRecipe>> = emptyMap(),
    val leveling: Leveling = Leveling(),
    val events: List<WikiEvent> = emptyList(),
    val rules: Rules = Rules(),
    val progression: Progression = Progression(),
    /** 스킬별 장비·펫 테크트리 (위키 가이드). 키: MINING, FARMING, FORAGING, FISHING, HUNTING, COMBAT */
    val techTrees: Map<String, List<GuideStage>> = emptyMap(),
    /** 스킬별 도구 목록 (위키 Pickaxes, Drills, Farming Tools, Axes, Fishing Rods, Lassos 문서) */
    val gearPools: Map<String, List<String>> = emptyMap(),
    /** 위키 Tutorial:Ironman Guide 단계 */
    val ironmanGuide: List<GuideStage> = emptyList(),
    /** 재료 출처 (위키 Bucket:Mob drops, Bucket:Minions) */
    val sources: Sources = Sources(),
    /** 스킬 종류별 펫 (위키 Module:Pet/UI). 키: MINING, FARMING … */
    val pets: Map<String, List<WikiPet>> = emptyMap(),
    /** HotM 칸 배치 (위키 Template:Heart of the Mountain/UI) */
    val hotmLayout: List<HotmCell> = emptyList(),
    /** HotF 칸 배치 (위키 Template:Heart of the Forest/UI) */
    val hotfLayout: List<HotmCell> = emptyList(),
    /** 타이머용 위키 규칙 (Hoppity's Hunt, Composter, Chocolate Factory, Fetchur, Experimentation Table) */
    val timerRules: TimerRules = TimerRules(),
    /** 장신구 목록 (위키 Accessories 하위 문서) */
    val accessories: List<Accessory> = emptyList(),
    val bestiary: Bestiary = Bestiary(),
    /** NPC 구매처 (위키 아이템 정보 상자 merchant/buy) */
    val npcBuy: Map<String, NpcBuy> = emptyMap(),
    /** 업그레이드 이전 단계 (위키 정보 상자 upgrades_from): 아이템 → 이전 아이템 */
    val upgrades: Map<String, String> = emptyMap(),
    /** 미니언 (위키 Module:Minion/Data) */
    val minions: List<Minion> = emptyList(),
    /** 미니언 연료 (위키 Minion Fuel 표) */
    val minionFuels: List<MinionFuel> = emptyList(),
    /** Hunting 샤드의 Attribute (위키 Bucket:Shards) */
    val attributes: List<Attribute> = emptyList(),
    /** 희귀도 → 레벨마다 필요한 누적 샤드 수 (위키 Attributes 표) */
    val attributeLevels: Map<String, List<Int>> = emptyMap(),
) {
    @Serializable
    data class Attribute(
        /** 샤드 번호 (예: C1, U28, L9) */
        val id: String,
        /** 샤드 이름 (예: Grove Shard) */
        val shard: String = "",
        /** Attribute 이름 (예: Nature Elemental) */
        val name: String,
        val rarity: String = "",
        val rank: Int = 0,
        /** 영향 받는 스킬 (Global, Combat, Mining …) */
        val skill: String = "",
        /** 잡는 곳 (Forest, Water, Combat) */
        val area: String = "",
        val families: List<String> = emptyList(),
        /** 효과 (단계 1~10 범위, 예: Grants +2-20 Health.) */
        val effect: String = "",
        /** 필요 조건 (예: Hunting V (5)) */
        val req: String = "",
        /** 공식 API 의 샤드 ID */
        val sb: String = "",
        /** 얻는 방법 (위키 샤드 문서의 Obtaining): 방법 이름 + 한 줄 설명 */
        val obtain: List<Obtain> = emptyList(),
    )

    @Serializable
    data class Obtain(val m: String, val t: String = "")

    /** 미니언 한 종류. tiers[0] = I 단계 */
    @Serializable
    data class Minion(
        val name: String,
        val type: String = "",
        val icon: String? = null,
        /** 수확 한 번에 필요한 행동 수 (대부분 2: 놓기 + 캐기) */
        val actions: Int = 2,
        /** 위키 Minion Crystal 이 적용되는지 */
        val crystal: Boolean = false,
        val items: List<MinionItem> = emptyList(),
        val tiers: List<MinionTier> = emptyList(),
    )

    @Serializable
    data class MinionItem(
        val id: String,
        /** 수확 한 번에 평균 몇 개 */
        val avg: Double = 1.0,
        /** 이 업그레이드가 있을 때만 (Auto Smelter, Enchanted Egg …) */
        val cond: String? = null,
        val replaces: String? = null,
    )

    @Serializable
    data class MinionTier(
        /** 행동 사이 시간 (초) */
        val tba: Double,
        val storage: Int = 0,
        /** 이 단계로 올리는 재료 (I 단계는 처음 만드는 재료). "COINS" = 코인 */
        val cost: List<List<kotlinx.serialization.json.JsonPrimitive>> = emptyList(),
        /** 재료 대신 NPC 와 교환 (XII 단계 등) */
        val npc: String? = null,
    ) {
        fun costPairs(): List<Pair<String, Long>> = cost.mapNotNull { c ->
            val id = c.getOrNull(0)?.content ?: return@mapNotNull null
            val n = c.getOrNull(1)?.content?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
            id to n
        }
    }

    @Serializable
    data class MinionFuel(
        val id: String,
        val name: String = "",
        /** 속도 증가 (0.25 = +25%) */
        val speed: Double = 0.0,
        /** 아이템 배수 (Catalyst x3) */
        val mult: Double = 1.0,
        /** 지속 시간 (없으면 영구) */
        val hours: Double? = null,
    )

    @Serializable
    data class TimerRules(
        /** 초콜릿 알이 나오는 게임 속 시각 (예: 7, 14, 21시) */
        val hoppityEggHours: List<Int> = emptyList(),
        val composter: Composter? = null,
        val timeTower: TimeTower? = null,
        /** "America/New_York 00:00" 형식 */
        val fetchurReset: String? = null,
        val experimentBitsReset: String? = null,
        /** 스카이블록 레벨 1 = SkyBlock XP 몇 (위키 SkyBlock Levels) */
        val skyblockXpPerLevel: Int = 100,
    )

    @Serializable
    data class Composter(val secondsPerCompost: Int = 600, val organicPerCompost: Int = 4000, val fuelPerCompost: Int = 2000,
                         val speedPerTier: Double = 0.2, val costReductionPerTier: Double = 0.01)

    @Serializable
    data class TimeTower(val maxCharges: Int = 3, val hoursPerCharge: Int = 8)

    @Serializable
    data class WikiPet(val name: String, val rarity: String = "", val lore: List<String> = emptyList())

    @Serializable
    data class HotmCell(val row: Int, val col: Int, val name: String, val maxLevel: Int = 1,
                        val ability: Boolean = false, val lore: List<String> = emptyList())

    @Serializable
    data class Accessory(val id: String, val name: String, val category: String = "", val source: String = "",
                         val coins: Double? = null, val npc: String? = null, val from: String? = null, val to: String? = null)

    @Serializable
    data class Bestiary(
        val families: List<BestiaryFamily> = emptyList(),
        val brackets: Map<String, List<Long>> = emptyMap(),
        /** 몹 이름(소문자_밑줄) → 가족 이름 (위키 Bucket:Mob bestiary) */
        val mobs: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class BestiaryFamily(val family: String, val section: String = "", val bracket: Int = 1, val maxTier: Int = 1)

    @Serializable
    data class NpcBuy(val npc: String = "", val coins: Double = 0.0, val cost: String = "")

    @Serializable
    data class Sack(val name: String, val capacity: Long = 0, val items: List<String> = emptyList())

    /** 위키 가이드의 한 단계. items: 공식 아이템 ID 또는 "pet:이름" */
    @Serializable
    data class GuideStage(val group: String = "", val title: String = "", val desc: String = "",
                          val items: List<String> = emptyList(), val page: String = "")

    @Serializable
    data class Sources(val drops: Map<String, List<String>> = emptyMap(), val minions: Map<String, String> = emptyMap(),
                       val sacks: Map<String, Sack> = emptyMap())

    /** 진행 단계 (위키: 지역 정보 상자, Slayer, HotM 표, The Catacombs) */
    @Serializable
    data class Progression(
        val areas: List<Area> = emptyList(),
        val hotmForgeSlots: Map<String, Int> = emptyMap(),
        val slayerUnlocks: List<SlayerUnlock> = emptyList(),
        val dungeonFloors: List<String> = emptyList(),
        val masterModeAfterFloorVII: Boolean = false,
    )

    @Serializable
    data class Area(val name: String, val page: String, val requires: List<Requirement> = emptyList())

    @Serializable
    data class Requirement(val type: String, val skill: String? = null, val level: Int? = null, val tier: Int? = null)

    @Serializable
    data class SlayerUnlock(val boss: String, val name: String, val requires: BossTier? = null, val note: String = "")

    @Serializable
    data class BossTier(val boss: String? = null, val tier: Int)

    @Serializable
    data class Source(
        val name: String = "",
        val url: String = "",
        val license: String = "",
        val generated: String = "",
        val pages: List<PageRef> = emptyList(),
    )

    @Serializable
    data class PageRef(val page: String, val revid: Long? = null)

    @Serializable
    data class CraftRecipe(val `in`: Map<String, Double>, val count: Int = 1)

    /** 위키 조합대 배치 (Bucket:Crafting recipes). g = 칸 9개(A1..C3, 칸마다 '이 중 하나' 재료), c = 결과 개수, r = 제작 조건(위키 링크), e = 강화 조합 */
    @Serializable
    data class CraftGrid(val g: List<List<GridSlot>> = emptyList(), val c: Int = 1, val r: String = "", val e: Boolean = false)

    @Serializable
    data class GridSlot(val i: String, val n: Int = 1)

    @Serializable
    data class ForgeRecipe(
        val `in`: Map<String, Double>,
        val count: Int = 1,
        val sec: Long = 0,
        val coins: Double = 0.0,
        /** 필요한 HotM 단계 (로마 숫자) */
        val hotm: String = "",
    )

    @Serializable
    data class Leveling(
        /** 레벨별 필요 XP (위키 Catacombs/Levels) */
        val catacombs: List<Long> = emptyList(),
        /** 누적 XP (위키 Slayer) */
        @SerialName("slayer_xp") val slayerXp: Map<String, List<Long>> = emptyMap(),
        /** 단계별 필요 XP, 첫 값 0 = 1단계 (위키 Heart of the Mountain/Leveling Table) */
        @SerialName("HOTM") val hotm: List<Long> = emptyList(),
        /** 단계별 필요 XP (위키 Heart of the Forest/Leveling Table) */
        @SerialName("HOTF") val hotf: List<Long> = emptyList(),
        /** 펫 레벨표 (위키 Module:Pet/LevelingData) */
        @SerialName("PET") val pet: PetLeveling = PetLeveling(),
        /** 가든 레벨별 Garden XP (위키 The Garden/Leveling Rewards) */
        @SerialName("GARDEN") val garden: List<Long> = emptyList(),
    )

    @Serializable
    data class PetLeveling(val xp: List<Long> = emptyList(), val offset: Map<String, Int> = emptyMap())

    /** 위키 Events 페이지의 타이머. anchor 시각부터 cycle(활성, 비활성, 활성, …초)이 반복된다. */
    @Serializable
    data class WikiEvent(
        val name: String,
        val page: String = name,
        val anchor: SbAnchor = SbAnchor(),
        val cycle: List<Long> = emptyList(),
        /** 이 NPC 가 시장(또는 장관)일 때만 열리는 이벤트 */
        val requiresNpc: String? = null,
    )

    @Serializable
    data class SbAnchor(val year: Int = 1, val month: Int = 0, val day: Int = 1, val hour: Int = 0, val minute: Int = 0)

    @Serializable
    data class Rules(
        val bazaarTaxPercent: Double = 1.25,
        val taxMultiplierByMayor: Map<String, Double> = emptyMap(),
        val forgeReductionStacking: String = "additive",
        val mayorForgePerks: List<MayorForgePerk> = emptyList(),
        val quickForge: QuickForge = QuickForge(),
        val skyblockEpochSeconds: Long = 1_560_275_700,
        val miningFiestaWholeTerm: Boolean = false,
    )

    @Serializable
    data class MayorForgePerk(val mayor: String, val perk: String, val percent: Double, val onlyStartedDuringTerm: Boolean = true)

    /** 위키 공식: min(max, base + 레벨×perLevel + ⌊레벨×bonusFactor⌋×bonus) % */
    @Serializable
    data class QuickForge(val base: Double = 10.0, val perLevel: Double = 0.5, val bonusFactor: Double = 0.05,
                          val bonus: Double = 10.0, val max: Double = 30.0) {
        fun percent(level: Int): Double = if (level <= 0) 0.0
        else minOf(max, base + level * perLevel + kotlin.math.floor(level * bonusFactor) * bonus)
    }

    fun forgeDurationSeconds(itemId: String): Long? = forge[itemId]?.firstOrNull()?.sec?.takeIf { it > 0 }

    companion object {
        fun parse(text: String): GameData = SbJson.decodeFromString(serializer(), text)
        val EMPTY = GameData()
    }
}
