package sbhelper.core.calc

import sbhelper.core.data.GameData
import sbhelper.core.model.ElectionResponse

/** 스카이블럭 날짜 */
data class SkyblockDate(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    val monthName get() = SkyblockCalendar.MONTHS[month]
    val season get() = SkyblockCalendar.SEASONS[month / 3]
    fun timeText(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val ampm = if (hour < 12) "am" else "pm"
        return "$h12:${minute.toString().padStart(2, '0')}$ampm"
    }
}

/** 이벤트 한 번의 진행 기간. end == start 이면 시작 시각만 있는 이벤트(예: 다크 옥션) */
data class EventOccurrence(val name: String, val page: String, val start: Long, val end: Long) {
    fun isActive(now: Long) = now in start until end
    val key get() = "$name@$start"
}

/**
 * 스카이블럭 달력. 모든 수치는 공식 위키 기준:
 *  - Time Systems: 기준점(1년 Early Spring 1일 00:00) = 유닉스 시간 1560275700 → gamedata.rules.skyblockEpochSeconds
 *  - Events: "A SkyBlock Year is exactly 5 days and 4 hours" → 1년 = 12달 × 31일 × 20분
 *  - 이벤트 일정: 위키 Events 페이지의 타이머 (gamedata.events)
 */
object SkyblockCalendar {
    /** 위키 Time Systems 의 기준점 (gamedata 를 불러오면 그 값으로 바뀜) */
    var epoch: Long = 1_560_275_700_000L
    const val HOUR = 50_000L
    const val DAY = 24 * HOUR
    const val MONTH = 31 * DAY
    const val YEAR = 12 * MONTH

    val MONTHS = listOf(
        "Early Spring", "Spring", "Late Spring",
        "Early Summer", "Summer", "Late Summer",
        "Early Autumn", "Autumn", "Late Autumn",
        "Early Winter", "Winter", "Late Winter",
    )
    val SEASONS = listOf("봄", "여름", "가을", "겨울")

    fun dateAt(time: Long): SkyblockDate {
        val elapsed = time - epoch
        val year = (elapsed / YEAR).toInt() + 1
        val inYear = elapsed % YEAR
        val month = (inYear / MONTH).toInt()
        val inMonth = inYear % MONTH
        val day = (inMonth / DAY).toInt() + 1
        val inDay = inMonth % DAY
        val hour = (inDay / HOUR).toInt()
        val minute = ((inDay % HOUR) * 60 / HOUR).toInt() / 10 * 10
        return SkyblockDate(year, month, day, hour, minute)
    }

    fun startOf(year: Int, month: Int, day: Int): Long =
        epoch + (year - 1L) * YEAR + month * MONTH + (day - 1L) * DAY

    private fun anchorTime(a: GameData.SbAnchor) = startOf(a.year, a.month, a.day) + a.hour * HOUR + a.minute * HOUR / 60

    /** 위키 타이머(기준 시각 + 반복 주기)에서 now 이후 첫 진행 기간 */
    fun nextOccurrence(e: GameData.WikiEvent, now: Long): EventOccurrence? {
        val cycle = e.cycle.map { it * 1000 }
        val period = cycle.sum()
        if (period <= 0) return null
        val anchor = anchorTime(e.anchor)
        var base = anchor + (now - anchor).floorDiv(period) * period - period
        repeat(3) {
            var t = base
            cycle.forEachIndexed { i, len ->
                if (i % 2 == 0) {
                    val end = t + len
                    if (end > now || (len == 0L && t >= now)) return EventOccurrence(e.name, e.page, t, end)
                }
                t += len
            }
            base += period
        }
        return null
    }

    /** 현 시장 임기 시작 시각 = 가장 최근 "Mayor Election Period" 종료 시각 */
    fun currentTermStart(data: GameData, now: Long): Long? {
        val election = data.events.firstOrNull { it.name == "Mayor Election Period" } ?: return null
        val period = election.cycle.sum() * 1000
        if (period <= 0) return null
        val activeLen = (election.cycle.firstOrNull() ?: return null) * 1000
        val anchor = anchorTime(election.anchor)
        var end = anchor + activeLen + (now - anchor - activeLen).floorDiv(period) * period
        if (end > now) end -= period
        return end
    }

    /**
     * 다가오는 이벤트 (진행 중 포함), 시간순.
     * 시장/장관 전용 이벤트는 현재 시장·장관과 퍼크에 맞을 때만 넣는다.
     */
    fun upcoming(now: Long, data: GameData, election: ElectionResponse?): List<EventOccurrence> {
        val mayor = election?.mayor
        val leaders = listOfNotNull(mayor?.name, mayor?.minister?.name)
        val perkText = buildList {
            mayor?.perks?.forEach { add(it.name + " " + it.description) }
            mayor?.minister?.perk?.let { add(it.name + " " + it.description) }
        }.joinToString(" ")
        val termStart = currentTermStart(data, now)

        val result = data.events.mapNotNull { e ->
            val npc = e.requiresNpc
            if (npc != null) {
                if (npc !in leaders) return@mapNotNull null
                // Foxy 의 "Extra 이벤트"는 퍼크 설명에 그 이벤트가 적혀 있을 때만
                if (e.name.contains("Extra ")) {
                    val what = e.name.substringAfter("Extra ").trim()
                    if (!perkText.contains(what, ignoreCase = true)) return@mapNotNull null
                }
                // 위키 Cole 페이지(0.27.2): Mining Fiesta 는 임기 내내 열린다
                if (npc == "Cole" && data.rules.miningFiestaWholeTerm) {
                    if (!perkText.contains("Mining Fiesta", ignoreCase = true) || termStart == null) return@mapNotNull null
                    return@mapNotNull EventOccurrence(e.name, e.page, termStart, termStart + YEAR)
                }
            }
            nextOccurrence(e, now)
        }
        return result.sortedBy { if (it.isActive(now)) Long.MIN_VALUE + it.start else it.start }
    }
}

/** 화면에 보일 한국어 이름과 아이콘 (번역만, 게임 정보는 아님) */
object EventLabels {
    private val ko = mapOf(
        "Traveling Zoo" to "떠돌이 동물원",
        "Spooky Fishing" to "스푸키 낚시",
        "Spooky Festival" to "스푸키 축제",
        "Winter Island" to "겨울 섬 개방",
        "Season of Jerry Event" to "제리의 계절",
        "New Year Celebration" to "새해 축하",
        "Mayor Election Period" to "시장 선거 기간",
        "Harvest Feast" to "수확 축제",
        "Jacob's Farming Contest" to "제이콥 농사 대회",
        "Dark Auction" to "다크 옥션",
        "Fallen Star Meeting" to "타락한 별의 교단 모임",
        "Bank Interest" to "은행 이자",
        "Marina's Fishing Festivals" to "낚시 축제 (Marina)",
        "Cole's Mining Fiestas" to "채광 축제 (Cole)",
        "Foxy's Extra Spooky Fishing" to "추가 스푸키 낚시 (Foxy)",
        "Foxy's Extra Spooky Festival" to "추가 스푸키 축제 (Foxy)",
        "Foxy's Extra Fishing Festival" to "추가 낚시 축제 (Foxy)",
        "Foxy's Extra Mining Fiesta" to "추가 채광 축제 (Foxy)",
    )

    fun label(name: String): String = ko[name] ?: name.replace("Year of the", "올해의 동물:")

    fun emoji(name: String): String = when {
        name.contains("Zoo") -> "🦁"
        name.contains("Spooky") -> "🎃"
        name.contains("Winter") || name.contains("Jerry") -> "❄"
        name.contains("New Year") -> "🎆"
        name.contains("Election") -> "🗳"
        name.contains("Harvest") -> "🌽"
        name.contains("Jacob") -> "🌾"
        name.contains("Dark Auction") -> "🕯"
        name.contains("Fallen Star") -> "⭐"
        name.contains("Bank") -> "💰"
        name.contains("Fishing") -> "🎣"
        name.contains("Mining") -> "⛏"
        name.startsWith("Year of") -> "🐾"
        else -> "📅"
    }

    /** 처음 설치했을 때 알림이 켜져 있는 이벤트 */
    fun enabledByDefault(name: String): Boolean = when {
        name == "Dark Auction" || name == "Fallen Star Meeting" || name == "Bank Interest" -> false
        name.startsWith("Year of") -> false
        else -> true
    }
}
