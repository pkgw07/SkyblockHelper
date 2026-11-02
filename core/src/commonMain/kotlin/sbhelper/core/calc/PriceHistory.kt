package sbhelper.core.calc

import kotlinx.serialization.Serializable

/** 바자 시세 한 점 (b = 즉시 구매가, s = 즉시 판매가) */
@Serializable
data class PricePoint(val t: Long, val b: Double, val s: Double)

/**
 * 바자 시세 기록 (공식 API 에 기록이 없어서 앱이 직접 모은다).
 * 관심 목록 + 최근 본 아이템만, 10분에 한 점, 14일 보관.
 */
@Serializable
data class PriceHistory(
    val series: Map<String, List<PricePoint>> = emptyMap(),
    /** 최근 검색에서 본 바자 아이템 (최근 것이 앞) */
    val recent: List<String> = emptyList(),
) {
    companion object {
        const val STEP = 10 * 60_000L
        const val KEEP = 14 * 24 * 3_600_000L
        const val MAX_RECENT = 30
    }

    /** 이 아이템들의 지금 시세를 더한다 (10분이 안 지났으면 그대로) */
    fun record(now: Long, ids: Collection<String>, price: (String) -> Pair<Double, Double>?): PriceHistory {
        var changed = false
        val out = series.toMutableMap()
        for (id in ids) {
            val (b, s) = price(id) ?: continue
            val list = out[id].orEmpty()
            if (list.isNotEmpty() && now - list.last().t < STEP) continue
            out[id] = (list + PricePoint(now, b, s)).filter { now - it.t <= KEEP }
            changed = true
        }
        // 더 이상 보지 않는 아이템 기록은 정리
        val keep = ids.toSet()
        out.keys.filter { it !in keep && (out[it]?.lastOrNull()?.t ?: 0) < now - KEEP }.forEach { out.remove(it); changed = true }
        return if (changed) copy(series = out) else this
    }

    fun track(id: String): PriceHistory = copy(recent = (listOf(id) + recent.filterNot { it == id }).take(MAX_RECENT))
}
