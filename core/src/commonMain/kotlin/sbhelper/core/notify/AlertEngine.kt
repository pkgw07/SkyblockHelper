package sbhelper.core.notify

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import sbhelper.core.calc.EventLabels
import sbhelper.core.calc.EventOccurrence
import sbhelper.core.calc.ForgeSlotStatus
import sbhelper.core.data.NotifyPrefs
import sbhelper.core.data.PriceAlert
import sbhelper.core.data.PriceSide
import sbhelper.core.model.Auction
import sbhelper.core.model.BazaarProduct
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.core.util.stripColor

enum class AlertCategory(val label: String) { FORGE("포지"), EVENT("이벤트"), AUCTION("경매"), PRICE("가격"), PATCH("패치노트"), SYSTEM("시스템") }

data class AppNotification(
    val key: String,
    val title: String,
    val body: String,
    val category: AlertCategory,
    val time: Long,
)

/** 플랫폼별 알림 표시 (데스크톱: 트레이 알림, Android: 시스템 알림) */
fun interface Notifier {
    fun show(notification: AppNotification)
}

data class AlertInputs(
    val profileId: String?,
    val forge: List<ForgeSlotStatus>,
    val events: List<EventOccurrence>,
    val myAuctions: List<Auction>,
    val bazaar: Map<String, BazaarProduct>,
    val priceAlerts: List<PriceAlert>,
    val itemName: (String) -> String,
    val patchNotes: List<sbhelper.core.api.PatchNote> = emptyList(),
    val jacobCrops: Map<Long, List<String>> = emptyMap(),
)

data class AlertResult(
    val notifications: List<AppNotification>,
    /** 다시 울릴 수 있도록 지울 키 (가격 조건이 풀린 경우 등) */
    val forgetKeys: Set<String>,
)

/**
 * "무엇이 완료되었는가"를 판단하는 엔진. 화면/플랫폼과 무관한 순수 로직.
 * 같은 키의 알림은 한 번만 보낸다.
 */
object AlertEngine {
    private const val FRESH_FORGE = 12 * 3_600_000L
    private const val FRESH_AUCTION = 24 * 3_600_000L

    fun evaluate(inputs: AlertInputs, prefs: NotifyPrefs, sent: Set<String>, now: Long): AlertResult {
        val out = mutableListOf<AppNotification>()
        val forget = mutableSetOf<String>()
        fun emit(key: String, title: String, body: String, cat: AlertCategory) {
            if (key !in sent && out.none { it.key == key }) out += AppNotification(key, title, body, cat, now)
        }

        // 1) 포지 완료
        if (prefs.forge && inputs.profileId != null) {
            for (slot in inputs.forge) {
                // 너무 오래전에 끝난 것은 (앱을 오랜만에 켰을 때) 알리지 않는다
                if (slot.isDone(now) && now - slot.endTime!! < FRESH_FORGE) {
                    val p = slot.process
                    emit("forge:${inputs.profileId}:${p.slot}:${p.startTime}",
                        "⚒ 포지 완료!", "${slot.itemName} (슬롯 ${p.slot}) 이(가) 완성되었어요.", AlertCategory.FORGE)
                }
            }
        }

        // 2) 이벤트 시작 전/시작
        val lead = prefs.eventLeadMinutes * 60_000L
        for (e in inputs.events) {
            if (!prefs.eventEnabled(e.name)) continue
            if (now >= e.start - lead && now < maxOf(e.end, e.start + 60_000)) {
                val left = e.start - now
                val crops = if (!e.name.contains("Jacob")) null
                    else inputs.jacobCrops.entries.firstOrNull { kotlin.math.abs(it.key - e.start) < 60_000 }?.value
                val cropText = crops?.let { " 작물: " + it.joinToString(", ") { c -> EventLabels.crop(c) } }.orEmpty()
                val body = (if (left > 0) "${formatDuration(left)} 후 시작해요." else "지금 진행 중이에요!") + cropText
                emit("event:${e.key}", "${EventLabels.emoji(e.name)} ${EventLabels.label(e.name)}", body, AlertCategory.EVENT)
            }
        }

        // 3) 내 경매
        if (prefs.auctions) {
            for (a in inputs.myAuctions) {
                if (a.claimed) continue
                val lastActivity = maxOf(a.bids.maxOfOrNull { it.timestamp } ?: 0L, if (a.end <= now) a.end else 0L)
                if (now - lastActivity > FRESH_AUCTION) continue
                val name = a.itemName.stripColor()
                when {
                    a.bin && a.bids.isNotEmpty() ->
                        emit("ah:sold:${a.uuid}", "💰 BIN 판매 완료", "$name — ${formatCoins(a.highestBidAmount)} 코인", AlertCategory.AUCTION)
                    !a.bin && a.end <= now && a.bids.isNotEmpty() ->
                        emit("ah:ended:${a.uuid}", "🔨 경매 종료", "$name — 최고 입찰 ${formatCoins(a.highestBidAmount)} 코인", AlertCategory.AUCTION)
                    a.end <= now && a.bids.isEmpty() ->
                        emit("ah:expired:${a.uuid}", "⌛ 경매 만료", "$name — 입찰이 없었어요. 회수해 주세요.", AlertCategory.AUCTION)
                }
            }
        }

        // 4) 바자 가격 알림 (조건이 풀리면 다시 울릴 수 있게 키를 지운다)
        if (prefs.priceAlerts) {
            for (alert in inputs.priceAlerts) {
                if (!alert.enabled) continue
                val q = inputs.bazaar[alert.itemId]?.quickStatus ?: continue
                val price = if (alert.side == PriceSide.INSTA_BUY) q.buyPrice else q.sellPrice
                val hit = if (alert.above) price >= alert.target else price <= alert.target
                val key = "price:${alert.id}"
                if (hit) {
                    val sideText = if (alert.side == PriceSide.INSTA_BUY) "즉시 구매가" else "즉시 판매가"
                    val cmp = if (alert.above) "이상" else "이하"
                    emit(key, "📈 가격 알림: ${inputs.itemName(alert.itemId)}",
                        "$sideText ${formatCoins(price)} (목표 ${formatCoins(alert.target)} $cmp)", AlertCategory.PRICE)
                } else {
                    forget += key
                }
            }
        }
        // 5) 새 패치노트 (위키 Changelog) — 최근 3일 안에 올라온 것만
        if (prefs.patchNotes) {
            for (note in inputs.patchNotes.take(5)) {
                val published = patchDate(note) ?: continue
                if (now - published > 3 * 86_400_000L) continue
                emit("patch:${note.key}", if (note.alpha) "🧪 알파 서버 패치노트" else "📰 새 패치노트",
                    note.title, AlertCategory.PATCH)
            }
        }
        return AlertResult(out, forget)
    }

    /** 다음에 확인해야 할 시각 (Android 백그라운드 예약용). 없으면 null */
    fun nextWakeUp(inputs: AlertInputs, prefs: NotifyPrefs, now: Long): Long? {
        val times = mutableListOf<Long>()
        if (prefs.forge) inputs.forge.mapNotNullTo(times) { it.endTime?.takeIf { t -> t > now } }
        val lead = prefs.eventLeadMinutes * 60_000L
        inputs.events.filter { prefs.eventEnabled(it.name) }.mapNotNullTo(times) { (it.start - lead).takeIf { t -> t > now } }
        return times.minOrNull()
    }

    /** 위키 date_int(예: 10290 = 10월 29일)를 시각으로 */
    fun patchDate(note: sbhelper.core.api.PatchNote): Long? {
        val month = note.dateInt / 1000
        val day = (note.dateInt % 1000) / 10
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching { LocalDate(note.year, month, day).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }.getOrNull()
    }
}
