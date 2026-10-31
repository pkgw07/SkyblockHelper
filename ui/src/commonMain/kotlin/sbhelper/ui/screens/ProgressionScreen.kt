package sbhelper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.WikiApi
import sbhelper.core.calc.NodeStatus
import sbhelper.core.calc.ProgressNode
import sbhelper.core.calc.ProgressionPlanner
import sbhelper.core.calc.Track
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.Screen
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private fun trackColor(t: Track) = when (t) {
    Track.AREAS -> Sb.Green
    Track.MINING -> Sb.Aqua
    Track.DUNGEONS -> Sb.Red
    Track.SLAYER -> Sb.Pink
}

@Composable
fun ProgressionScreen(controller: AppController, state: AppState, wide: Boolean) {
    val profile = state.selectedProfile
    ScreenColumn(wide) {
        if (profile == null) {
            Panel {
                EmptyState("계정을 연결해 주세요", "내 프로필과 위키의 해금 조건을 비교해서 다음 목표를 추천해요.", icon = Icons.Rounded.AccountTree,
                    action = { SbButton("설정으로 이동", { controller.go(Screen.SETTINGS) }) })
            }
            return@ScreenColumn
        }
        val nodes = ProgressionPlanner.build(state.gameData, profile, state.skills)
        val picks = ProgressionPlanner.recommend(nodes)
        Text("해금 조건은 모두 공식 위키 기준이에요. 내 프로필에서 거의 다 온 목표부터 추천해요.", style = SbText.body(13, Sb.TextFaint))
        RecommendRow(picks, wide)
        val tracks = Track.entries.map { t -> t to nodes.filter { it.track == t } }.filter { it.second.isNotEmpty() }
        tracks.chunked(if (wide) 2 else 1).forEach { row ->
            Responsive(wide, *row.map { (track, list) -> cell(1f) { m -> TrackPanel(track, list, m) } }.toTypedArray())
        }
    }
}

/** 홈 화면용 작은 추천 카드 묶음 */
@Composable
fun RecommendRow(picks: List<ProgressNode>, wide: Boolean, compact: Boolean = false) {
    if (picks.isEmpty()) return
    val cards: List<@Composable (Modifier) -> Unit> = picks.map { n -> { m: Modifier -> RecommendCard(n, m, compact) } }
    if (wide) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
    } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { it(Modifier.fillMaxWidth()) } }
}

@Composable
private fun RecommendCard(n: ProgressNode, modifier: Modifier, compact: Boolean) {
    val platform = LocalPlatform.current
    val c = trackColor(n.track)
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.14f), Sb.Surface)))
            .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("다음 목표 · ${n.track.label}", style = SbText.caption(c), modifier = Modifier.weight(1f))
            Text("${(n.progress * 100).toInt()}%", style = SbText.pixel(12, c, bold = true))
        }
        Spacer(Modifier.height(8.dp))
        Text(n.title, style = SbText.pixel(16, Sb.Text, bold = true))
        Spacer(Modifier.height(4.dp))
        Text(n.requirement, style = SbText.body(12, Sb.TextDim), maxLines = 2)
        Spacer(Modifier.height(10.dp))
        XpBar(n.progress, Modifier.fillMaxWidth(), c, 7.dp)
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("현재: ${n.current}", style = SbText.body(12, Sb.TextFaint), modifier = Modifier.weight(1f))
                Row(Modifier.clickable { platform.openUrl(WikiApi.pageUrl(n.wikiPage)) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("위키", style = SbText.body(12, Sb.Aqua))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Sb.Aqua, modifier = Modifier.padding(start = 3.dp).size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackPanel(track: Track, nodes: List<ProgressNode>, modifier: Modifier) {
    val c = trackColor(track)
    val platform = LocalPlatform.current
    Panel(modifier, caption = "Progression", title = track.label, accent = c,
        trailing = { Pill("${nodes.count { it.status == NodeStatus.DONE }}/${nodes.size}", c) }) {
        nodes.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // 트리의 세로 선 + 상태 점
                Box(Modifier.width(26.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Canvas(Modifier.width(26.dp).fillMaxHeight()) {
                        val x = size.width / 2
                        val lineColor = if (n.status == NodeStatus.DONE) c.copy(alpha = 0.6f) else Sb.Border
                        if (i > 0) drawLine(lineColor, Offset(x, 0f), Offset(x, 12.dp.toPx()), 2.dp.toPx())
                        if (i < nodes.lastIndex) drawLine(lineColor, Offset(x, 12.dp.toPx()), Offset(x, size.height), 2.dp.toPx())
                        val r = 6.dp.toPx()
                        when (n.status) {
                            NodeStatus.DONE -> drawCircle(c, r, Offset(x, 12.dp.toPx()))
                            NodeStatus.NEXT -> {
                                drawCircle(c.copy(alpha = 0.25f), r * 1.8f, Offset(x, 12.dp.toPx()))
                                drawCircle(c, r, Offset(x, 12.dp.toPx()), style = Stroke(2.dp.toPx()))
                            }
                            NodeStatus.LOCKED -> drawCircle(Sb.BorderStrong, r, Offset(x, 12.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable { platform.openUrl(WikiApi.pageUrl(n.wikiPage)) }
                        .padding(bottom = 12.dp, end = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, style = SbText.body(14, when (n.status) {
                            NodeStatus.DONE -> Sb.Text
                            NodeStatus.NEXT -> c
                            NodeStatus.LOCKED -> Sb.TextFaint
                        }, if (n.status == NodeStatus.NEXT) FontWeight.Bold else FontWeight.Medium), modifier = Modifier.weight(1f))
                        Text(when (n.status) { NodeStatus.DONE -> "완료"; NodeStatus.NEXT -> "진행 중"; NodeStatus.LOCKED -> "잠김" },
                            style = SbText.pixel(11, if (n.status == NodeStatus.LOCKED) Sb.TextFaint else c))
                    }
                    Text(n.requirement, style = SbText.body(12, Sb.TextFaint))
                    if (n.status == NodeStatus.NEXT && n.progress > 0) {
                        Spacer(Modifier.height(4.dp))
                        XpBar(n.progress, Modifier.fillMaxWidth(), c, 5.dp)
                        Text("현재: ${n.current}", style = SbText.body(11, Sb.TextDim))
                    }
                }
            }
        }
    }
}
