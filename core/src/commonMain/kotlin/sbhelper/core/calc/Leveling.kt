package sbhelper.core.calc

import sbhelper.core.model.SkillDefinition

data class LevelInfo(
    val level: Int,
    val maxLevel: Int,
    /** 현재 레벨 안에서의 진행도 0.0 ~ 1.0 */
    val progress: Double,
    val xpIntoLevel: Double,
    val xpForNext: Double,
    val totalXp: Double,
) {
    val maxed get() = level >= maxLevel
}

object Leveling {
    /**
     * 누적 경험치표(cumulative)로 레벨 계산.
     * @param cumulative i번째 값 = 레벨 i+1 이 되기 위한 누적 경험치
     */
    fun fromCumulative(xp: Double, cumulative: List<Double>, cap: Int = cumulative.size): LevelInfo {
        val limit = cap.coerceAtMost(cumulative.size)
        var level = 0
        while (level < limit && xp >= cumulative[level]) level++
        if (level >= limit) {
            return LevelInfo(limit, limit, 1.0, 0.0, 0.0, xp)
        }
        val prev = if (level == 0) 0.0 else cumulative[level - 1]
        val next = cumulative[level]
        val into = xp - prev
        val need = next - prev
        return LevelInfo(level, limit, (into / need).coerceIn(0.0, 1.0), into, need, xp)
    }

    /** 레벨별 필요 경험치(per-level) 목록 -> 누적 목록 */
    fun perLevelToCumulative(perLevel: List<Long>): List<Double> {
        var sum = 0.0
        return perLevel.map { sum += it; sum }
    }

    /** Hypixel 공식 스킬 정의 기준 레벨. capOverride: 예) 농사는 Anita 보너스에 따라 50~60 */
    fun skill(xp: Double, def: SkillDefinition, capOverride: Int? = null): LevelInfo {
        val cumulative = def.levels.sortedBy { it.level }.map { it.totalExpRequired }
        return fromCumulative(xp, cumulative, capOverride ?: def.maxLevel)
    }

    /** 슬레이어 경험치표는 누적값이다. */
    fun slayer(xp: Double, thresholds: List<Long>): LevelInfo =
        fromCumulative(xp, thresholds.map { it.toDouble() })

    /** 카타콤 경험치표는 레벨별 필요량이다. 50레벨까지가 정식 레벨. */
    fun catacombs(xp: Double, perLevel: List<Long>): LevelInfo =
        fromCumulative(xp, perLevelToCumulative(perLevel), cap = 50)

    /** HotM 경험치표는 레벨별 필요량이다 (첫 값 0은 1레벨). */
    fun hotm(xp: Double, perLevel: List<Long>): LevelInfo {
        val cumulative = perLevelToCumulative(perLevel.drop(1))
        val info = fromCumulative(xp, cumulative)
        return info.copy(level = info.level + 1, maxLevel = info.maxLevel + 1)
    }
}
