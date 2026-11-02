package sbhelper.core.calc

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import sbhelper.core.data.GameData
import sbhelper.core.data.SyncedSettings

/** 오늘 할 일 한 줄 */
data class TodayItem(
    val key: String,
    val emoji: String,
    val title: String,
    val detail: String = "",
    /** 체크 칸이 있는 항목(매일 하는 일)이면 true */
    val checkable: Boolean = false,
    val done: Boolean = false,
    /** 지금 바로 할 수 있는 일 (강조) */
    val ready: Boolean = false,
    /** 다음 초기화 시각 (체크 항목) */
    val resetAt: Long? = null,
    val custom: Boolean = false,
)

/**
 * 홈 '오늘 할 일' 카드.
 *  - 자동: 포지 수령, 컴포스터 멈춤, Time Tower 충전 (공식 API 값)
 *  - 매일 체크: Fetchur, 실험대 (위키의 초기화 시각), 직접 추가한 항목 (이 기기 자정)
 */
object Today {
    /** 기본 매일 할 일: 키 → (이모지, 이름, 초기화 규칙 고르기) */
    val DAILY = listOf(
        Triple("fetchur", "🧩", "Fetchur 요청"),
        Triple("experiment", "🧪", "실험대 (Experimentation Table)"),
    )

    fun customKey(name: String) = "custom:$name"

    /** HotM·HotF 일일 보너스 퍼크 (위키 HotM/HotF 칸 이름): 키, 이름, 설명, 트리 */
    val PERK_DAILY = listOf(
        Quad("perk:daily_powder", "Daily Powder (HotM)", "오늘 첫 광석 캐기 · 파우더 보너스", "hotm"),
        Quad("perk:daily_grind", "Daily Grind (HotM)", "광산마다 오늘 첫 의뢰 완료 · 파우더 보너스", "hotm"),
        Quad("perk:daily_wishes", "Daily Wishes (HotF)", "Galatea 에서 오늘 첫 통나무 베기 · Whispers 보너스", "hotf"),
    )

    data class Quad(val first: String, val second: String, val third: String, val fourth: String)

    /** 직접 추가한 항목: 이 기기 시각 자정 */
    fun nextLocalMidnight(now: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        return today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** 체크가 아직 유효한지: 체크한 시각이 마지막 초기화보다 뒤 */
    fun isDone(checkedAt: Long?, nextReset: Long): Boolean = checkedAt != null && checkedAt > nextReset - 24 * 3_600_000L

    fun items(
        now: Long,
        data: GameData,
        synced: SyncedSettings,
        forge: List<ForgeSlotStatus>,
        timers: List<ExtraTimer>,
        timeTowerCharges: Int?,
        hotmNodes: Map<String, Int> = emptyMap(),
        hotfNodes: Map<String, Int> = emptyMap(),
    ): List<TodayItem> {
        val out = mutableListOf<TodayItem>()
        // 포지: 다 된 것
        val ready = forge.count { it.isDone(now) }
        if (ready > 0) out += TodayItem("forge", "⚒", "포지에서 ${ready}개 받기", ready = true)
        else forge.mapNotNull { it.remaining(now) }.minOrNull()?.let { left ->
            if (left < 24 * 3_600_000L) out += TodayItem("forge", "⚒", "포지", "다음 완료까지 ${sbhelper.core.util.formatDuration(left)}")
        }
        // 컴포스터
        timers.firstOrNull { it.key == "composter" }?.let { t ->
            if (t.done) out += TodayItem("composter", "🌱", "컴포스터에 재료 넣기", "멈춰 있어요", ready = true)
            else if (t.at - now < 24 * 3_600_000L) out += TodayItem("composter", "🌱", "컴포스터", "${sbhelper.core.util.formatDuration(t.at - now)} 뒤 재료가 떨어져요")
        }
        // Time Tower: 충전이 있으면 쓸 수 있음
        if ((timeTowerCharges ?: 0) > 0) {
            val max = data.timerRules.timeTower?.maxCharges
            out += TodayItem("timeTower", "🍫", "Time Tower 쓰기", "충전 $timeTowerCharges" + (max?.let { "/$it" } ?: ""), ready = true)
        }
        // 매일 할 일
        val r = data.timerRules
        DAILY.filter { it.first !in synced.dailyHidden }.forEach { (key, emoji, title) ->
            val rule = when (key) { "fetchur" -> r.fetchurReset; else -> r.experimentBitsReset } ?: return@forEach
            val next = ExtraTimers.nextDaily(now, rule) ?: return@forEach
            out += TodayItem(key, emoji, title, checkable = true, done = isDone(synced.dailyDone[key], next), resetAt = next)
        }
        val midnight = nextLocalMidnight(now)
        // HotM·HotF 일일 보너스: 퍼크를 찍었을 때만 (공식 API 에 '오늘 받았는지' 기록이 없어 직접 체크)
        PERK_DAILY.filter { (key, _, _, tree) -> (if (tree == "hotf") hotfNodes else hotmNodes)[key.removePrefix("perk:")]?.let { it > 0 } == true }
            .filter { it.first !in synced.dailyHidden }
            .forEach { (key, title, detail, _) ->
                out += TodayItem(key, if (key.contains("wish")) "🌳" else "⛏", title, detail, checkable = true,
                    done = isDone(synced.dailyDone[key], midnight), resetAt = midnight)
            }
        synced.dailyCustom.forEach { name ->
            val key = customKey(name)
            out += TodayItem(key, "📌", name, checkable = true, done = isDone(synced.dailyDone[key], midnight), resetAt = midnight, custom = true)
        }
        return out
    }

    /** 체크/해제 (지난 체크 기록은 정리) */
    fun toggle(s: SyncedSettings, item: TodayItem, now: Long): SyncedSettings {
        val keep = s.dailyDone.filterValues { now - it < 3 * 24 * 3_600_000L }
        val next = if (item.done) keep - item.key else keep + (item.key to now)
        return s.copy(dailyDone = next)
    }
}
