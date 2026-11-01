package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.WikiApi
import sbhelper.core.calc.Leveling
import sbhelper.core.data.GameData
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.Pill
import sbhelper.ui.components.Segmented
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val SKILL_TABS = listOf(
    "MINING" to "채광", "FARMING" to "농사", "FORAGING" to "벌목", "FISHING" to "낚시", "HUNTING" to "사냥", "COMBAT" to "전투",
)

/** 스킬별 장비·펫 테크트리 (공식 위키 가이드 기준) */
@Composable
fun TechTreeScreen(controller: AppController, state: AppState, wide: Boolean) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    val tabs = SKILL_TABS.filter { state.gameData.techTrees[it.first]?.isNotEmpty() == true }
    ScreenColumn(wide) {
        if (tabs.isEmpty()) {
            EmptyState("테크트리 데이터가 없어요", "위키 데이터를 받는 중이에요.")
            return@ScreenColumn
        }
        Segmented(tabs.map { it.second }, tab.coerceAtMost(tabs.lastIndex), { tab = it; selected = null })
        val (key, label) = tabs[tab.coerceAtMost(tabs.lastIndex)]
        val stages = state.gameData.techTrees[key].orEmpty()
        val level = currentLevel(state, key)
        Text(
            "공식 위키 가이드에 나온 순서대로 장비와 펫을 보여 줘요. 아이템을 누르면 제작법·드롭·미니언을 볼 수 있어요." +
                (level?.let { " 내 $label 레벨: $it" } ?: ""),
            style = SbText.body(13, Sb.TextFaint),
        )
        val content: @Composable (androidx.compose.ui.Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stages.forEachIndexed { i, st -> StageCard(state, st, i, level, selected) { selected = it } }
            }
        }
        val sel = selected
        if (wide && sel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                content(Modifier.weight(1f))
                Column(Modifier.width(340.dp)) { ItemInfo(controller, state, sel, onClose = { selected = null }) }
            }
        } else {
            if (sel != null) ItemInfo(controller, state, sel, onClose = { selected = null })
            content(Modifier.fillMaxWidth())
        }
    }
}

/** 스킬 레벨 (전투는 Combat). 정보가 없으면 null */
private fun currentLevel(state: AppState, key: String): Int? {
    val p = state.selectedProfile ?: return null
    val def = state.skills?.skills?.get(key) ?: return null
    val xp = p.skillXp?.get(key) ?: return null
    return Leveling.skill(xp, def, p.skillCap(key, def.maxLevel)).level
}

/** 단계 제목의 레벨 범위(예: "Farming 20-29")에 내 레벨이 들어가면 현재 단계 */
private fun isCurrentStage(title: String, level: Int?): Boolean {
    if (level == null) return false
    val m = Regex("""(\d+)\s*-\s*(\d+)""").find(title) ?: return false
    return level in m.groupValues[1].toInt()..m.groupValues[2].toInt()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageCard(state: AppState, st: GameData.GuideStage, index: Int, level: Int?, selected: String?, onSelect: (String) -> Unit) {
    val platform = LocalPlatform.current
    val current = isCurrentStage(st.title, level)
    val c = if (current) Sb.Gold else Sb.Aqua
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
            .border(1.dp, if (current) Sb.Gold.copy(alpha = 0.6f) else Sb.Border, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = SbText.pixel(14, c, bold = true), modifier = Modifier.width(26.dp))
            Column(Modifier.weight(1f)) {
                if (st.group != st.title) Text(st.group, style = SbText.caption(Sb.TextFaint))
                Text(st.title, style = SbText.body(15, weight = FontWeight.SemiBold))
            }
            if (current) Pill("지금 단계", Sb.Gold, filled = true)
            Spacer(Modifier.width(8.dp))
            Text("위키", style = SbText.body(12, Sb.Aqua), modifier = Modifier.clickable { platform.openUrl(WikiApi.pageUrl(st.page)) })
        }
        if (st.desc.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(st.desc, style = SbText.body(12, Sb.TextDim), maxLines = 3)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            st.items.take(40).forEach { id -> ItemChip(state, id, id == selected) { onSelect(id) } }
        }
    }
}
