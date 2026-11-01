package sbhelper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.Requirement
import sbhelper.core.calc.Requirements
import sbhelper.core.data.GameData
import sbhelper.ui.AppController
import sbhelper.ui.components.HoverTip
import sbhelper.ui.components.LoreTooltip
import sbhelper.ui.components.McIdSlot
import sbhelper.ui.components.McPanel
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/**
 * 위키와 같은 조합대: 3×3 칸 → 결과.
 * @param output 결과 아이템 ID
 * @param highlight 금색 테두리로 표시할 재료 (쓰이는 곳에서 "지금 보는 아이템이 여기 들어가요")
 * @param showOutputName 결과 이름을 위에 (쓰이는 곳 목록용)
 */
@Composable
fun CraftGridView(
    controller: AppController, state: AppState, grid: GameData.CraftGrid, output: String, slot: Dp,
    highlight: String? = null, showOutputName: Boolean = false,
) {
    val title = when {
        showOutputName -> state.itemName(output).replace(Regex("§."), "") + if (grid.c > 1) " ×${grid.c}" else ""
        grid.e -> "강화 조합 (Enchanted)"
        else -> "제작대 (Crafting Table)"
    }
    McPanel(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in 0 until 3) {
                            val cell = grid.g.getOrNull(r * 3 + c).orEmpty()
                            SlotWithTip(controller, state, cell, slot, highlight != null && cell.any { it.i == highlight })
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("➜", style = SbText.pixel(22, Color(0xFF404040), bold = true))
            Spacer(Modifier.width(10.dp))
            SlotWithTip(controller, state, listOf(GameData.GridSlot(output, grid.c)), slot * 1.25f, false)
        }
    }
}

@Composable
private fun SlotWithTip(controller: AppController, state: AppState, cell: List<GameData.GridSlot>, size: Dp, highlight: Boolean) {
    if (cell.isEmpty()) { McIdSlot(state, emptyList(), 0, size); return }
    HoverTip({
        if (cell.size == 1) ItemQuickTip(state, cell.first().i)
        else LoreTooltip("이 중 하나", Sb.Gold, cell.map { "§7· §f${state.itemName(it.i)}" + if (it.n > 1) " §8×${it.n}" else "" })
    }) {
        McIdSlot(state, cell.map { it.i }, cell.first().n, size, highlight) { controller.openItem(it) }
    }
}

/** 조건 한 줄: ✔ 달성 / ✘ 아직 / • 알 수 없음. 누르면 그 위키 문서 */
@Composable
fun RequirementRow(controller: AppController, r: Requirement, label: String? = null) {
    val (mark, color) = when (r.met) { true -> "✔" to Sb.Green; false -> "✘" to Sb.Red; null -> "•" to Sb.TextFaint }
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mark, style = SbText.body(14, color, FontWeight.Bold), modifier = Modifier.width(20.dp))
        if (label != null) { Text(label, style = SbText.caption(Sb.TextDim)); Spacer(Modifier.width(6.dp)) }
        Text(r.text, style = SbText.body(14, if (r.page != null) Sb.Aqua else Sb.Text, FontWeight.Medium),
            modifier = if (r.page != null) Modifier.clickable { controller.openWikiLink(r.page!!) } else Modifier)
        r.mine?.let { Spacer(Modifier.width(8.dp)); Text("($it)", style = SbText.body(12, if (r.met == false) Sb.Red else Sb.TextFaint)) }
    }
}

/** 아이템을 쓰는 데 필요한 조건 (공식 아이템 API) — 없으면 빈 목록 */
fun useRequirements(state: AppState, id: String): List<Pair<String?, Requirement>> {
    val item = state.items[id] ?: return emptyList()
    return item.requirements.map { null to Requirements.item(state, it) } +
        item.catacombsRequirements.map { "던전 안에서" to Requirements.item(state, it) }
}
