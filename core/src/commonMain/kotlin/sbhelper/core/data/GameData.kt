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
    val forge: Map<String, List<ForgeRecipe>> = emptyMap(),
    val leveling: Leveling = Leveling(),
    val events: List<WikiEvent> = emptyList(),
    val rules: Rules = Rules(),
    val progression: Progression = Progression(),
) {
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
    )

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
