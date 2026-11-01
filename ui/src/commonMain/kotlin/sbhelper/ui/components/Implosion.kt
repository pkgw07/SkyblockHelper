package sbhelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import sbhelper.core.util.Clock
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 이스터에그: 이 아이템 그림을 우클릭(휴대폰은 길게 누르기)하면 Wither Impact */
val IMPLODE_IDS = setOf("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE")

/** 화면 루트가 넣어 준다. (그림 위치) → Wither Impact */
val LocalImplode = staticCompositionLocalOf<((Offset) -> Unit)?> { null }

data class Implosion(val start: Long, val center: Offset, val seed: Int)

/**
 * Hyperion 류 그림: 우클릭(마우스) 또는 길게 누르기(터치)면 Wither Impact.
 * 좌클릭·짧게 누르기는 건드리지 않아서, 그림을 감싼 줄을 누르면 원래대로 아이템 화면으로 간다.
 */
@Composable
fun Modifier.implodeOnClick(id: String?): Modifier {
    if (id == null || id.uppercase() !in IMPLODE_IDS) return this
    val implode = LocalImplode.current ?: return this
    var center by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { center = it.boundsInRoot().center }
        .pointerInput(id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type == PointerType.Mouse) {
                    // 우클릭: 바로 발동하고 이 클릭은 여기서 끝 (메뉴·화면 이동 없음)
                    if (currentEvent.buttons.isSecondaryPressed) {
                        down.consume()
                        implode(center)
                        waitForUpOrCancellation()?.consume()
                    }
                } else {
                    // 터치: 길게 누르면 발동 (손을 뗄 때 눌림으로 처리되지 않게 막음)
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (up == null) {
                        implode(center)
                        waitForUpOrCancellation()?.consume()
                    }
                }
            }
        }
}

// 시간 (ms)
private const val WARP = 160L          // 보라 포털 입자 (순간이동)
private const val BOOM_AT = 60L        // 폭발 시작
private const val PUFF_LIFE = 620L
private const val PUFF_SPREAD = 260L
private const val NUM_LIFE = 1100L
private const val TOTAL = 1400L

/**
 * 화면 전체 위에 Wither Impact 를 그린다:
 * 순간이동(보라 포털 입자) → 흰 섬광 → 그림 자리 중심의 거대 폭발 연기(마인크래프트 폭발 입자 여러 개가 겹침)
 * → 주변에 크리티컬 데미지 숫자(✧1,234,567✧)가 떠오름, 화면 흔들림.
 */
@Composable
fun ImplosionLayer(list: SnapshotStateList<Implosion>, content: @Composable () -> Unit) {
    var now by remember { mutableLongStateOf(0L) }
    val measurer = rememberTextMeasurer()
    val font = sbhelper.ui.theme.LocalFonts.current.pixel
    LaunchedEffect(list.size) {
        while (list.isNotEmpty()) {
            withFrameMillis { now = Clock.now() }
            list.removeAll { now - it.start > TOTAL }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val latest = list.maxOfOrNull { it.start }
        val shakeAge = latest?.let { now - it - BOOM_AT } ?: Long.MAX_VALUE
        Box(Modifier.offset {
            if (shakeAge in 0..360) {
                val a = (1 - shakeAge / 360f) * 12f
                val r = Random(shakeAge / 16)
                IntOffset(((r.nextFloat() * 2 - 1) * a).toInt(), ((r.nextFloat() * 2 - 1) * a).toInt())
            } else IntOffset.Zero
        }) { content() }
        if (list.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            val unit = min(w, h) / 900f
            list.forEach { imp ->
                val age = now - imp.start
                val rnd = Random(imp.seed)
                // 1) 순간이동: 보라색 포털 입자가 그림 쪽으로 빨려 들어감
                if (age < WARP + 120) repeat(40) {
                    val ang = rnd.nextFloat() * 6.283f
                    val dist = (60 + rnd.nextFloat() * 160) * unit
                    val t = (age / (WARP + 120f)).coerceIn(0f, 1f)
                    val d = dist * (1 - t)
                    val p = Offset(imp.center.x + cos(ang) * d, imp.center.y + sin(ang) * d - t * 20 * unit)
                    val c = if (rnd.nextBoolean()) Color(0xFFB24BF3) else Color(0xFF6A1FB0)
                    val s = (4 + rnd.nextFloat() * 5) * unit
                    drawRect(c.copy(alpha = (1 - t) * 0.95f), Offset(p.x - s / 2, p.y - s / 2), Size(s, s))
                }
                val boomAge = age - BOOM_AT
                if (boomAge < 0) return@forEach
                // 2) 흰 섬광
                if (boomAge < 120) drawRect(Color.White.copy(alpha = 0.42f * (1 - boomAge / 120f)))
                // 3) 거대 폭발: 중심에 큰 덩어리, 반경 6블록 느낌으로 둘러싸는 연기들
                val radius = max(w, h) * 0.20f
                repeat(22) { i ->
                    val ring = i / 22f
                    val ang = rnd.nextFloat() * 6.283f
                    val rad = radius * sqrt(rnd.nextFloat()) * (if (i < 6) 0.35f else 1f)
                    val pos = Offset(imp.center.x + cos(ang) * rad, imp.center.y + sin(ang) * rad * 0.8f)
                    val delay = (ring * PUFF_SPREAD + rnd.nextFloat() * 60).toLong()
                    val size = (if (i < 6) 170f else 110f + rnd.nextFloat() * 70f) * unit
                    val seed = rnd.nextInt()
                    val t = (boomAge - delay) / PUFF_LIFE.toFloat()
                    if (t in 0f..1f) puff(pos, size, t, seed)
                }
                // 4) 크리티컬 데미지 숫자 (여러 마리가 맞은 것처럼)
                repeat(7) { i ->
                    val ang = rnd.nextFloat() * 6.283f
                    val rad = radius * (0.25f + rnd.nextFloat() * 0.75f)
                    val base = Offset(imp.center.x + cos(ang) * rad, imp.center.y + sin(ang) * rad * 0.7f)
                    val dmg = 180_000 + rnd.nextInt(2_600_000)
                    val delay = 40L + i * 35L
                    val t = (boomAge - delay) / NUM_LIFE.toFloat()
                    if (t in 0f..1f) critNumber(measurer, font, base.copy(y = base.y - t * 70 * unit), dmg, t, unit)
                }
            }
        }
    }
}

/** 크리티컬 숫자: ✧ 와 숫자 글자마다 흰색 → 노랑 → 주황 → 빨강을 번갈아 (게임의 크리티컬 표시) */
private fun DrawScope.critNumber(measurer: androidx.compose.ui.text.TextMeasurer, font: androidx.compose.ui.text.font.FontFamily,
                                 at: Offset, dmg: Int, t: Float, unit: Float) {
    val text = "✧" + dmg.toString().reversed().chunked(3).joinToString(",").reversed() + "✧"
    val colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFFF55), Color(0xFFFFAA00), Color(0xFFFF5555))
    val alpha = if (t < 0.75f) 1f else (1 - (t - 0.75f) / 0.25f)
    val pop = if (t < 0.08f) 0.6f + t / 0.08f * 0.5f else 1.1f - min(0.1f, (t - 0.08f))
    val str = buildAnnotatedString {
        text.forEachIndexed { i, ch -> withStyle(SpanStyle(color = colors[i % colors.size].copy(alpha = alpha))) { append(ch) } }
    }
    val style = TextStyle(fontFamily = font, fontSize = (22 * unit * pop).coerceAtLeast(10f).sp,
        shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF3F3F3F).copy(alpha = alpha), Offset(2f, 2f)))
    val layout = measurer.measure(str, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** 마인크래프트 폭발 입자 한 덩이: 8×8 픽셀 구름이 커지며 흰색 → 회색으로 바래고 가장자리부터 흩어짐 */
private fun DrawScope.puff(center: Offset, size: Float, t: Float, seed: Int) {
    val grid = 8
    val s = size * (0.55f + 0.8f * t)
    val cell = s / grid
    val alpha = (1f - t).pow(1.2f)
    val bright = 1f - 0.5f * t
    val r = Random(seed)
    for (y in 0 until grid) for (x in 0 until grid) {
        val dx = (x + 0.5f) / grid * 2 - 1
        val dy = (y + 0.5f) / grid * 2 - 1
        val d = sqrt(dx * dx + dy * dy)
        val jitter = r.nextFloat() * 0.35f
        if (d + jitter * t > 1.05f) continue
        val shade = (bright - d * 0.28f - r.nextFloat() * 0.12f).coerceIn(0.22f, 1f)
        drawRect(Color(shade, shade, shade, alpha * (0.85f + 0.15f * (1 - d))),
            Offset(center.x - s / 2 + x * cell, center.y - s / 2 + y * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}
