package sbhelper.core.calc

import sbhelper.core.data.GameData
import sbhelper.core.model.ElectionResponse
import sbhelper.core.model.ForgeProcess

data class ForgeSlotStatus(
    val process: ForgeProcess,
    val itemName: String,
    /** 포지 시간을 모르면 null (위키 표에 없는 아이템) */
    val durationMillis: Long?,
    val endTime: Long?,
    /** 적용된 시간 감소율 (0.0 ~ 1.0) */
    val reduction: Double,
) {
    fun remaining(now: Long): Long? = endTime?.let { (it - now).coerceAtLeast(0) }
    fun progress(now: Long): Double? = durationMillis?.let { d ->
        if (d <= 0) 1.0 else ((now - process.startTime).toDouble() / d).coerceIn(0.0, 1.0)
    }
    fun isDone(now: Long): Boolean = endTime != null && now >= endTime
}

/**
 * 포지 완료 시각 계산. 위키 "The Forge" 기준:
 *  - 감소 효과는 더해서(additively) 적용
 *  - Quick Forge: 위키 공식 (gamedata.rules.quickForge), 이미 진행 중인 포지에도 적용
 *  - Cole 의 Molten Forge: 그의 임기가 시작된 뒤 시작한 포지에만 적용
 */
object ForgeCalc {

    fun status(
        process: ForgeProcess,
        data: GameData,
        itemName: String,
        quickForgeLevel: Int,
        election: ElectionResponse?,
        termStart: Long?,
    ): ForgeSlotStatus {
        val base = data.forgeDurationSeconds(process.itemId)
        var percent = data.rules.quickForge.percent(quickForgeLevel)
        percent += mayorPercent(data, election, process.startTime, termStart)
        val reduction = (percent / 100.0).coerceIn(0.0, 0.95)
        val duration = base?.let { (it * 1000 * (1 - reduction)).toLong() }
        return ForgeSlotStatus(process, itemName, duration, duration?.let { process.startTime + it }, reduction)
    }

    /** 현재 시장/장관 퍼크 중 포지 시간을 줄이는 것 (위키 The Forge 표) */
    fun mayorPercent(data: GameData, election: ElectionResponse?, startedAt: Long, termStart: Long?): Double {
        val mayor = election?.mayor ?: return 0.0
        val perks = mayor.perks.map { it.name } + listOfNotNull(mayor.minister?.perk?.name)
        return data.rules.mayorForgePerks.filter { it.perk in perks }.sumOf { perk ->
            val validStart = !perk.onlyStartedDuringTerm || (termStart != null && startedAt >= termStart)
            if (validStart) perk.percent else 0.0
        }
    }
}
