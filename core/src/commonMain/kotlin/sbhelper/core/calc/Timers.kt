package sbhelper.core.calc

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import sbhelper.core.data.GameData
import sbhelper.core.model.ProfileSummary

/** 포지·이벤트 말고 알려 줄 타이머 하나 */
data class ExtraTimer(
    val key: String,
    val title: String,
    val emoji: String,
    /** 이 시각에 알린다 (ms) */
    val at: Long,
    val detail: String,
    /** 근거 (위키 문서 / 공식 API) */
    val source: String,
    /** 이미 그 시각이 지났는데 계속 보여 줄 상태 (예: 컴포스터가 이미 멈춤) */
    val done: Boolean = false,
)

/**
 * 공식 위키 규칙 + 공식 API 값으로 계산하는 타이머.
 *  - Fetchur 요청 초기화, 실험대 비트 충전 초기화, 초콜릿 알 등장, 시장 교체: 위키 규칙만으로
 *  - 컴포스터 재료 소진(가든 API), Time Tower 충전(프로필 API): 공식 API 값 + 위키 규칙
 */
object ExtraTimers {
    /** 설정의 켜고 끄기 목록: 키 → 이름 */
    val KINDS = linkedMapOf(
        "fetchur" to "🧩 Fetchur 요청 초기화",
        "experiment" to "🧪 실험대 비트 충전 초기화",
        "hoppity" to "🥚 초콜릿 알 등장 (Hoppity's Hunt 기간)",
        "mayor" to "🗳 새 시장 취임",
        "composter" to "🌱 가든 컴포스터 재료 소진",
        "timeTower" to "🍫 초콜릿 공장 Time Tower 충전",
    )

    /** 처음 설치했을 때 알림이 켜져 있는 것 */
    fun enabledByDefault(key: String) = key in setOf("composter", "timeTower", "mayor")

    fun all(now: Long, data: GameData, profile: ProfileSummary?, garden: JsonObject?): List<ExtraTimer> {
        val r = data.timerRules
        val out = mutableListOf<ExtraTimer>()
        r.fetchurReset?.let { nextDaily(now, it) }?.let {
            out += ExtraTimer("fetchur", "Fetchur 요청 초기화", "🧩", it, "드워븐 광산 Fetchur 의 오늘 요청이 바뀌어요 (서버 시간 자정)", "위키 Fetchur")
        }
        r.experimentBitsReset?.let { nextDaily(now, it) }?.let {
            out += ExtraTimer("experiment", "실험대 비트 충전 초기화", "🧪", it, "비트로 산 추가 충전이 초기화돼요 (매일 00:00 GMT)", "위키 Experimentation Table")
        }
        hoppityEgg(now, data)?.let { out += it }
        data.events.firstOrNull { it.name == "Mayor Election Period" }?.let { SkyblockCalendar.nextOccurrence(it, now) }?.let { occ ->
            out += ExtraTimer("mayor", "새 시장 취임", "🗳", occ.end, "선거가 끝나고 새 시장 임기가 시작돼요", "위키 Events")
        }
        composter(now, r.composter, garden)?.let { out += it }
        timeTower(now, r.timeTower, profile)?.let { out += it }
        return out.filter { it.at > now || it.done }.sortedBy { if (it.done) Long.MIN_VALUE else it.at }
    }

    /** "America/New_York 00:00" → 다음 그 시각 */
    fun nextDaily(now: Long, rule: String): Long? = runCatching {
        val (zoneId, hm) = rule.trim().split(" ").let { it[0] to it.getOrElse(1) { "00:00" } }
        val zone = if (zoneId.equals("UTC", true)) TimeZone.UTC else TimeZone.of(zoneId)
        val (h, m) = hm.split(":").map { it.toInt() }
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        listOf(today, today.plus(1, DateTimeUnit.DAY), today.plus(2, DateTimeUnit.DAY))
            .map { d -> d.atTime(LocalTime(h, m)).toInstant(zone).toEpochMilliseconds() }
            .first { it > now }
    }.getOrNull()

    /** Hoppity's Hunt 기간에만: 다음 초콜릿 알 (게임 속 7시·14시·21시 등) */
    private fun hoppityEgg(now: Long, data: GameData): ExtraTimer? {
        val hours = data.timerRules.hoppityEggHours.takeIf { it.isNotEmpty() } ?: return null
        val hunt = data.events.firstOrNull { it.name == "Hoppity's Hunt" } ?: return null
        val occ = SkyblockCalendar.nextOccurrence(hunt, now) ?: return null
        if (!occ.isActive(now)) return null
        val dayStart = SkyblockCalendar.epoch + (now - SkyblockCalendar.epoch).floorDiv(SkyblockCalendar.DAY) * SkyblockCalendar.DAY
        val next = (0..1).flatMap { d -> hours.map { h -> dayStart + d * SkyblockCalendar.DAY + h * SkyblockCalendar.HOUR } }
            .firstOrNull { it > now && it < occ.end } ?: return null
        val hour = ((next - SkyblockCalendar.epoch).mod(SkyblockCalendar.DAY) / SkyblockCalendar.HOUR).toInt()
        return ExtraTimer("hoppity", "초콜릿 알 등장", "🥚", next, "게임 속 ${hour}시에 새 초콜릿 알이 나타나요", "위키 Hoppity's Hunt")
    }

    /** 가든 API composter_data → 유기물·연료가 떨어지는 시각 */
    private fun composter(now: Long, rule: GameData.Composter?, garden: JsonObject?): ExtraTimer? {
        rule ?: return null
        val c = garden?.get("composter_data") as? JsonObject ?: return null
        fun num(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.doubleOrNull
        val organic = num(c, "organic_matter") ?: return null
        val fuel = num(c, "fuel_units") ?: return null
        val lastSave = num(c, "last_save")?.toLong() ?: return null
        val up = c["upgrades"] as? JsonObject
        val speed = num(up, "speed") ?: 0.0
        val reduce = (num(up, "cost_reduction") ?: 0.0) * rule.costReductionPerTier
        val omPer = rule.organicPerCompost * (1 - reduce)
        val fuelPer = rule.fuelPerCompost * (1 - reduce)
        val composts = minOf(organic / omPer, fuel / fuelPer)
        val secondsEach = rule.secondsPerCompost / (1 + speed * rule.speedPerTier)
        val what = if (organic / omPer <= fuel / fuelPer) "유기물(Organic Matter)" else "연료(Machine Fuel)"
        // 재료가 1개 분량도 없으면 마지막 저장 때 이미 멈춘 것
        val at = if (composts < 1) lastSave else lastSave + (composts.toLong() * secondsEach * 1000).toLong()
        if (at <= now) {
            return ExtraTimer("composter", "컴포스터 멈춤", "🌱", at, "$what 이 떨어져 컴포스터가 멈춰 있어요. 가든에서 재료를 넣어 주세요.",
                "공식 가든 API + 위키 Composter", done = true)
        }
        return ExtraTimer("composter", "컴포스터 멈춤", "🌱", at, "$what 이 떨어져 컴포스터가 멈춰요 (약 ${composts.toInt()}개 더 만듦)", "공식 가든 API + 위키 Composter")
    }

    /** 프로필 events.easter.time_tower → 다음 충전 */
    private fun timeTower(now: Long, rule: GameData.TimeTower?, p: ProfileSummary?): ExtraTimer? {
        rule ?: return null
        val charges = p?.timeTowerCharges ?: return null
        val last = p.timeTowerLastCharge ?: return null
        if (charges >= rule.maxCharges) return null
        val step = rule.hoursPerCharge * 3_600_000L
        var next = last + step
        while (next <= now) next += step
        return ExtraTimer("timeTower", "Time Tower 충전", "🍫", next, "Time Tower 충전이 하나 더 쌓여요 (지금 $charges/${rule.maxCharges})", "공식 프로필 API + 위키 Chocolate Factory")
    }
}
