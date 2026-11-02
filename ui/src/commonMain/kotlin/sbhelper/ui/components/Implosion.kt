package sbhelper.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import sbhelper.core.util.Clock
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** 이스터에그: 이 아이템 그림을 클릭하면 Implosion (Wither Impact) */
val IMPLODE_IDS = setOf("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE")

/** 화면 루트가 넣어 준다. (클릭한 위치) → 폭발 */
val LocalImplode = staticCompositionLocalOf<((Offset) -> Unit)?> { null }

data class Implosion(val start: Long, val center: Offset, val seed: Int)

/**
 * Hyperion 류 그림: 우클릭(마우스) 또는 길게 누르기(터치)면 폭발.
 * 좌클릭·짧게 누르기는 건드리지 않아서, 그림을 감싼 줄을 누르면 원래대로 아이템 화면으로 간다.
 * (마우스 뒤로 버튼처럼 '누르는 순간' 이벤트에서 오른쪽 버튼을 확인한다)
 */
@Composable
fun Modifier.implodeOnClick(id: String?): Modifier {
    if (id == null || id.uppercase() !in IMPLODE_IDS) return this
    val implode = LocalImplode.current ?: return this
    var center by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { center = it.boundsInRoot().center }
        .pointerInput(id) {
            awaitPointerEventScope {
                var touchDownAt = 0L
                var fired = false
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val change = e.changes.firstOrNull() ?: continue
                    when {
                        // 마우스 우클릭
                        e.type == PointerEventType.Press && change.type == PointerType.Mouse && e.buttons.isSecondaryPressed -> {
                            implode(center)
                            e.changes.forEach { it.consume() }
                        }
                        // 터치: 누른 채로 길게
                        e.type == PointerEventType.Press && change.type != PointerType.Mouse -> { touchDownAt = change.uptimeMillis; fired = false }
                        e.type == PointerEventType.Move && change.type != PointerType.Mouse && change.pressed && !fired &&
                            change.uptimeMillis - touchDownAt > viewConfiguration.longPressTimeoutMillis -> {
                            fired = true; implode(center)
                        }
                        // 길게 눌러 터뜨렸으면 손을 뗄 때 눌림(화면 이동)으로 처리되지 않게
                        e.type == PointerEventType.Release && change.type != PointerType.Mouse && fired -> e.changes.forEach { it.consume() }
                    }
                }
            }
        }
}

private const val LIFE = 560L       // 연기 하나가 사는 시간
private const val SPREAD = 420L     // 연기들이 차례로 터지는 시간
private const val TOTAL = LIFE + SPREAD + 80

/**
 * 화면 전체 위에 마인크래프트 폭발(Implosion)을 그린다: 흰 섬광 → 회색 픽셀 연기 → 사라짐, 화면 흔들림.
 */
@Composable
fun ImplosionLayer(list: SnapshotStateList<Implosion>, content: @Composable () -> Unit) {
    var now by remember { mutableLongStateOf(0L) }
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
        val shakeAge = latest?.let { now - it } ?: Long.MAX_VALUE
        Box(Modifier.offset {
            if (shakeAge < 320) {
                val a = (1 - shakeAge / 320f) * 9f
                val r = Random(shakeAge / 16)
                IntOffset(((r.nextFloat() * 2 - 1) * a).toInt(), ((r.nextFloat() * 2 - 1) * a).toInt())
            } else IntOffset.Zero
        }) { content() }
        if (list.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            list.forEach { imp ->
                val age = now - imp.start
                // 흰 섬광
                if (age in 0..140) drawRect(Color.White.copy(alpha = 0.32f * (1 - age / 140f)))
                val rnd = Random(imp.seed)
                val unit = max(w, h) / 1400f
                repeat(28) { i ->
                    // 앞의 10개는 클릭한 곳 주변, 나머지는 화면 곳곳
                    val near = i < 10
                    val pos = if (near) {
                        val ang = rnd.nextFloat() * 6.283f
                        val rad = rnd.nextFloat() * 170f * unit
                        Offset(imp.center.x + kotlin.math.cos(ang) * rad, imp.center.y + kotlin.math.sin(ang) * rad)
                    } else Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                    val delay = if (near) (rnd.nextFloat() * 120).toLong() else (80 + rnd.nextFloat() * SPREAD).toLong()
                    val size = (if (near) 70f + rnd.nextFloat() * 70f else 50f + rnd.nextFloat() * 90f) * unit * 1.6f
                    val noise = rnd.nextInt()
                    val t = (age - delay) / LIFE.toFloat()
                    if (t in 0f..1f) puff(pos, size, t, noise)
                }
            }
        }
    }
}

/** 마인크래프트 폭발 연기 한 덩이: 8×8 픽셀 구름이 커지며 흰색 → 회색으로 바래고 사라짐 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.puff(center: Offset, size: Float, t: Float, seed: Int) {
    val grid = 8
    val s = size * (0.55f + 0.75f * t)
    val cell = s / grid
    val alpha = (1f - t).pow(1.3f)
    val bright = 1f - 0.45f * t
    val r = Random(seed)
    for (y in 0 until grid) for (x in 0 until grid) {
        val dx = (x + 0.5f) / grid * 2 - 1
        val dy = (y + 0.5f) / grid * 2 - 1
        val d = sqrt(dx * dx + dy * dy)
        val jitter = r.nextFloat() * 0.35f
        // 시간이 지나면 가장자리부터 구멍이 숭숭 (연기처럼)
        if (d + jitter * t > 1.05f) continue
        val shade = (bright - d * 0.28f - r.nextFloat() * 0.12f).coerceIn(0.25f, 1f)
        drawRect(Color(shade, shade, shade, alpha * (0.85f + 0.15f * (1 - d))),
            Offset(center.x - s / 2 + x * cell, center.y - s / 2 + y * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}
