package sbhelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import sbhelper.core.calc.PricePoint
import sbhelper.core.util.Clock
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 시세 그래프 범위 */
private val RANGES = listOf("24시간" to 24 * 3_600_000L, "3일" to 3 * 24 * 3_600_000L, "14일" to 14 * 24 * 3_600_000L)

/**
 * 바자 시세 선 그래프 (구매가 = 금색, 판매가 = 하늘색).
 * 마우스를 올리거나(PC) 누르면(휴대폰) 그 시각의 값을 보여 준다.
 */
@Composable
fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier, height: Dp = 150.dp, compact: Boolean = false) {
    var range by remember { mutableIntStateOf(if (compact) 1 else 0) }
    var hover by remember { mutableStateOf<Int?>(null) }
    val now = Clock.now()
    val shown = remember(points, range) { points.filter { now - it.t <= RANGES[range].second } }

    Column(modifier) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(Sb.Gold, "구매가")
                Spacer(Modifier.width(10.dp))
                Legend(Sb.Aqua, "판매가")
                Spacer(Modifier.weight(1f))
                Segmented(RANGES.map { it.first }, range, { range = it; hover = null })
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.size < 2) {
            Box(Modifier.fillMaxWidth().height(if (compact) 40.dp else 70.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (compact) "시세 모으는 중" else "시세를 모으는 중이에요. 앱이 켜져 있는 동안 10분마다 한 번씩 기록해요.",
                    style = SbText.body(12, Sb.TextFaint),
                )
            }
            return@Column
        }
        val lo = shown.minOf { minOf(it.b, it.s) }
        val hi = shown.maxOf { maxOf(it.b, it.s) }
        val span = (hi - lo).takeIf { it > 0 } ?: (hi * 0.02).coerceAtLeast(1.0)
        val t0 = shown.first().t
        val t1 = shown.last().t.coerceAtLeast(t0 + 1)

        // 맨 위 요약: 지금 값 + 범위 안 변화
        if (!compact) {
            val first = shown.first(); val last = shown.last()
            val sel = hover?.let { shown.getOrNull(it) } ?: last
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (hover != null) timeLabel(sel.t) else "지금", style = SbText.caption(Sb.TextDim))
                Text("구매 ${formatCoins(sel.b)}", style = SbText.body(13, Sb.Gold))
                Text("판매 ${formatCoins(sel.s)}", style = SbText.body(13, Sb.Aqua))
                if (hover == null && first.b > 0) {
                    val ch = (last.b - first.b) / first.b
                    Text((if (ch >= 0) "▲ " else "▼ ") + fixed(kotlin.math.abs(ch) * 100, 1) + "%",
                        style = SbText.body(12, if (ch >= 0) Sb.Positive else Sb.Negative))
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Canvas(
            Modifier.fillMaxWidth().height(height).then(if (compact) Modifier else Modifier.pointerInput(shown) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.firstOrNull()?.position?.x ?: continue
                        hover = when (e.type) {
                            PointerEventType.Exit -> null
                            else -> {
                                val t = t0 + (x / size.width).coerceIn(0f, 1f) * (t1 - t0)
                                shown.indices.minByOrNull { kotlin.math.abs(shown[it].t - t) }
                            }
                        }
                    }
                }
            })
        ) {
            val w = size.width; val h = size.height
            fun px(p: PricePoint) = (p.t - t0).toFloat() / (t1 - t0) * w
            fun py(v: Double) = (h - ((v - lo) / span * (h * 0.86) + h * 0.07)).toFloat()
            // 가로 보조선
            repeat(3) { i ->
                val y = h * (i + 1) / 4f
                drawLine(Sb.Border, Offset(0f, y), Offset(w, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            fun line(c: Color, v: (PricePoint) -> Double) {
                val path = Path()
                shown.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p), py(v(p))) else path.lineTo(px(p), py(v(p))) }
                drawPath(path, c, style = Stroke(width = if (compact) 1.5f else 2.2f))
            }
            line(Sb.Aqua) { it.s }
            line(Sb.Gold) { it.b }
            hover?.let { shown.getOrNull(it) }?.let { p ->
                val x = px(p)
                drawLine(Sb.TextFaint, Offset(x, 0f), Offset(x, h), 1f)
                drawCircle(Sb.Gold, 4f, Offset(x, py(p.b)))
                drawCircle(Sb.Aqua, 4f, Offset(x, py(p.s)))
            }
        }
        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Row {
                Text(timeLabel(t0), style = SbText.caption())
                Spacer(Modifier.weight(1f))
                Text("최저 ${formatCoins(lo)} · 최고 ${formatCoins(hi)}", style = SbText.caption())
                Spacer(Modifier.weight(1f))
                Text(timeLabel(t1), style = SbText.caption())
            }
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, style = SbText.body(12, Sb.TextDim))
    }
}

private fun timeLabel(t: Long): String {
    val d = Instant.fromEpochMilliseconds(t).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${d.monthNumber}/${d.dayOfMonth} ${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}
