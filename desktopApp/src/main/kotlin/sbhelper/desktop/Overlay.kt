package sbhelper.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import sbhelper.core.SkyblockRepository
import sbhelper.core.calc.CraftPlanner
import sbhelper.core.util.formatNumber
import sbhelper.core.util.formatPercent
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private const val PLACE_BAR = 28

/**
 * 게임 오버레이: Hypixel SkyBlock 안에서 마인크래프트 화면 구석에 제작 플래너 진행도.
 *  - 스카이블록에 들어가면 창을 만들고, 나가거나 게임이 꺼지면 없앤다
 *  - 다른 창으로 전환하면 숨김, 단축키(Ctrl+Shift+O)로 숨김
 *  - 데이터가 바뀔 때만 다시 그림 (애니메이션·시계 없음)
 *  - 클릭 통과, 작업 표시줄에 안 나옴, 포커스를 가져가지 않음
 */
@Composable
fun GameOverlay(repo: SkyblockRepository) {
    if (!GameWatch.supported) return
    val app by repo.state.collectAsState()
    val game by GameWatch.state.collectAsState()
    val d = app.device
    // 스카이블록 안(기록 파일을 못 찾으면 게임이 켜져 있는 동안), 또는 미리 보기
    if (!game.preview && !game.placing && (!d.overlayEnabled || !game.active)) return

    // 제작 플래너 목표 → 기본 재료 진행도 (목표·자루·데이터가 바뀔 때만 계산)
    val target = app.synced.craftCurrent
    val profile = app.selectedProfile
    // 제작 플래너와 같은 설정: 자루·인벤토리 사용 여부, Recipe tree 에서 끈 재료
    val sacks = remember(profile?.sacks, profile?.inventories, d.plannerUseSacks, d.plannerUseInventory) {
        sbhelper.core.calc.CraftStock.merge(if (d.plannerUseSacks) profile?.sacks.orEmpty() else emptyMap(),
            if (d.plannerUseInventory) profile?.inventories?.let { sbhelper.core.calc.CraftStock.inventory(it) }.orEmpty() else emptyMap())
    }
    val skip = target?.let { app.synced.craftExcluded[it.itemId] }.orEmpty()
    val view = remember(target, sacks, skip, app.gameData, app.bazaar != null) {
        target?.let { t ->
            val planner = CraftPlanner(app.gameData)
            val raw = planner.rawProgress(t.itemId, t.amount, sacks, skip)
            val pct = if (raw.all { it.missing <= 0 }) 1.0 else planner.progress(t.itemId, t.amount, sacks, skip) { k ->
                app.bazaar?.products?.get(k)?.quickStatus?.buyPrice?.takeIf { it > 0 } ?: app.npcPrices[k]
            }
            Triple(t, raw.filter { it.missing > 0 }, pct)
        }
    }
    // 크기 설정: 글씨 크기 %, 상자 너비, 재료 줄 수
    val ts = d.overlayTextScale.coerceIn(70, 200) / 100f
    val W = d.overlayWidth.coerceIn(180, 600)
    val maxRows = d.overlayRows.coerceIn(1, 20)
    val rows = view?.second?.size?.coerceAtMost(maxRows) ?: 0
    val more = (view?.second?.size ?: 0) - rows
    val placing = game.placing
    val height = ((if (view == null) 64 else 78 + rows * 20 + (if (more > 0) 18 else 0)) * ts).toInt() + (if (placing) PLACE_BAR else 0)

    val state = rememberWindowState(size = DpSize(W.dp, height.dp), position = WindowPosition(0.dp, 0.dp))
    LaunchedEffect(height, W) { state.size = DpSize(W.dp, height.dp) }
    val visible = game.shown

    Window(
        onCloseRequest = {},
        state = state,
        visible = visible,
        title = "SkyblockHelper Overlay",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        // 평소에는 클릭 통과, 위치를 정하는 동안만 끌 수 있게
        LaunchedEffect(placing) { GameWatch.makeClickThrough(window, on = !placing) }
        // 게임 창 (미리 보기·위치 정하기라 게임이 없으면 화면 전체) 의 범위 (화면 좌표, dp)
        fun area(): DoubleArray? {
            val scale = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            val r = GameWatch.mcRect() ?: window.graphicsConfiguration?.bounds?.let { b ->
                intArrayOf((b.x * scale).toInt(), (b.y * scale).toInt(), ((b.x + b.width) * scale).toInt(), ((b.y + b.height) * scale).toInt())
            } ?: return null
            // 창 모드면 제목 표시줄 아래부터
            val topPad = if (r[1] > 0 || r[0] > 0) 32.0 else 0.0
            return doubleArrayOf(r[0] / scale, r[1] / scale + topPad, r[2] / scale, r[3] / scale)
        }
        // 보이는 동안: 위치 맞추기 + '항상 위' 다시 걸기 (F11 전체 화면 대응), 1초마다. 위치를 정하는 중에는 옮기지 않음
        // 게임이 맨 앞으로 올 때마다(topTick) 바로 다시 건다. 전체 화면이면 0.3초마다 (게임 창이 위로 올라오는 것 대비)
        LaunchedEffect(visible, d.overlayCorner, d.overlayX, d.overlayY, height, W, placing, game.topTick) {
            var first = true
            while (visible) {
                if (first || !placing) area()?.let { (left, top, right, bottom) ->
                    val margin = 12
                    val (x, y) = if (d.overlayCorner == 4 && d.overlayX >= 0f) {
                        left + d.overlayX * (right - left - W).coerceAtLeast(0.0) to top + d.overlayY * (bottom - top - height).coerceAtLeast(0.0)
                    } else {
                        val c = if (d.overlayCorner == 4) 1 else d.overlayCorner
                        (if (c % 2 == 0) left + margin else right - W - margin) to (if (c < 2) top + margin else bottom - height - margin)
                    }
                    val pos = WindowPosition(x.toFloat().dp, y.toFloat().dp)
                    if (state.position != pos) state.position = pos
                }
                first = false
                GameWatch.keepOnTop(window)
                delay(if (GameWatch.state.value.fullscreen) 300 else 1000)
            }
        }
        val windowScope = this
        if (placing) {
            Column {
                // 위치 정하기: 이 막대를 끌어서 옮기고 '저장'
                sbhelper.ui.theme.SkyblockTheme(textScale = 1f) {
                    with(windowScope) {
                        WindowDraggableArea {
                            Row(Modifier.fillMaxWidth().height(PLACE_BAR.dp).clip(RoundedCornerShape(8.dp)).background(Sb.Gold)
                                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✥ 끌어서 옮기기", style = SbText.body(12, Color(0xFF1A1200)), modifier = Modifier.weight(1f))
                                Text("저장", style = SbText.body(12, Color(0xFF1A1200), androidx.compose.ui.text.font.FontWeight.Bold),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33000000))
                                        .clickable {
                                            area()?.let { (left, top, right, bottom) ->
                                                val fx = ((window.x - left) / (right - left - W).coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
                                                val fy = ((window.y - top) / (bottom - top - height).coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
                                                repo.updateDevice { it.copy(overlayCorner = 4, overlayX = fx, overlayY = fy) }
                                            }
                                            GameWatch.endPlace()
                                        }.padding(horizontal = 8.dp, vertical = 2.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("취소", style = SbText.body(12, Color(0xFF1A1200)),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { GameWatch.endPlace() }.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
                OverlayCard(app, view, more, d.overlayOpacity, ts, maxRows)
            }
        } else OverlayCard(app, view, more, d.overlayOpacity, ts, maxRows)
    }
}

/**
 * 오버레이 내용. 앱 본창과 따로 뜨는 창이라 글꼴·글자 크기 설정을 여기서 직접 넣는다
 * (없으면 글자를 그리는 순간 "fonts not provided" 로 앱이 꺼짐).
 */
@Composable
fun OverlayCard(app: sbhelper.core.AppState, view: Triple<sbhelper.core.data.CraftTarget, List<sbhelper.core.calc.RawNeed>, Double>?,
                more: Int, opacity: Int, textScale: Float = 1f, maxRows: Int = 5) {
    sbhelper.ui.theme.SkyblockTheme(textScale = textScale, textWeight = app.device.fontWeight) {
        val bg = Color(0xFF0F1117).copy(alpha = opacity.coerceIn(20, 100) / 100f)
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, Sb.Gold.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Column {
                Text("SkyblockHelper · 제작 플래너", style = SbText.pixel(10, Sb.TextDim))
                Spacer(Modifier.height(4.dp))
                if (view == null) {
                    Text("앱의 제작 플래너에서 목표를 정하면 여기에 떠요", style = SbText.body(12, Sb.Text), maxLines = 2)
                } else {
                    val (t, missing, pct) = view
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.itemName(t.itemId).replace(Regex("§."), "") + if (t.amount > 1) " ×${t.amount}" else "",
                            style = SbText.body(13, Sb.Gold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatPercent(pct, 0), style = SbText.pixel(13, if (pct >= 1) Sb.Green else Sb.Gold, bold = true))
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(pct.toFloat().coerceIn(0f, 1f)).height(4.dp).background(if (pct >= 1) Sb.Green else Sb.Gold))
                    }
                    Spacer(Modifier.height(5.dp))
                    if (missing.isEmpty()) Text("재료를 다 모았어요!", style = SbText.body(12, Sb.Green))
                    missing.take(maxRows).forEach { r ->
                        Row(Modifier.height((20 * textScale).dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.itemName(r.itemId).replace(Regex("§."), ""), style = SbText.body(12, Sb.Text), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${formatNumber(r.have.toLong())}/${formatNumber(kotlin.math.ceil(r.needed).toLong())}",
                                style = SbText.pixel(11, Sb.TextDim))
                            Spacer(Modifier.width(6.dp))
                            Text(formatPercent((r.have / r.needed).coerceIn(0.0, 1.0), 0), style = SbText.pixel(11, Sb.Gold),
                                modifier = Modifier.width((34 * textScale).dp))
                        }
                    }
                    if (more > 0) Text("+ ${more}종 더", style = SbText.body(11, Sb.TextFaint))
                }
            }
        }

    }
}
