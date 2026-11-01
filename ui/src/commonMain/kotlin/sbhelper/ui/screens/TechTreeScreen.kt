package sbhelper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.GearStep
import sbhelper.core.calc.Leveling
import sbhelper.core.calc.TechTreeCalc
import sbhelper.core.nbt.ItemParser
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HoverTip
import sbhelper.ui.components.ItemIcon
import sbhelper.ui.components.LoreTooltip
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.Segmented
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val SKILL_TABS = listOf(
    "MINING" to "채광", "FARMING" to "농사", "FORAGING" to "벌목", "FISHING" to "낚시", "HUNTING" to "사냥", "COMBAT" to "전투",
)

/**
 * 스킬별 테크트리: 도구·갑옷·장비·펫·장신구를 따로, 각각 초반 → 후반 순서로.
 * 내가 가진 아이템은 ✔, 그다음 칸은 "다음 추천".
 */
@Composable
fun TechTreeScreen(controller: AppController, state: AppState, wide: Boolean) {
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    ScreenColumn(wide) {
        Segmented(SKILL_TABS.map { it.second }, tab, { tab = it; selected = null })
        val (key, label) = SKILL_TABS[tab]
        val cats = remember(state.gameData, state.items, key) { TechTreeCalc.build(state.gameData, state.items, key) { state.itemName(it) } }
        // 가진 아이템 = 인벤토리·가방·펫 + 박물관에 기증한 아이템
        val owned = remember(state.selectedProfile, state.museum, state.items) {
            ownedIds(state) + sbhelper.core.calc.MuseumCalc.ownedIds(state.items, state.museum)
        }
        val level = currentLevel(state, key)
        Text(
            "공식 위키의 $label 가이드·도구 목록에 나온 아이템을 종류별로 나누고, 등급과 업그레이드 관계에 따라 초반 → 후반 순서로 놓았어요. " +
                "→ 는 앞 아이템을 재료로 업그레이드, › 는 다음 추천이에요. ✔ 는 내가 가진(박물관 기증 포함) 아이템." + (level?.let { " 내 $label 레벨: $it" } ?: ""),
            style = SbText.body(13, Sb.TextFaint),
        )
        if (cats.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) { EmptyState("테크트리 데이터가 없어요", "위키 데이터를 받는 중이에요.") }
            return@ScreenColumn
        }
        val content: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                cats.forEach { c -> CategoryPanel(controller, state, c.label, c.steps, owned, selected, wide) { selected = it } }
            }
        }
        val sel = selected
        if (wide && sel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                content(Modifier.weight(1f))
                Column(Modifier.width(340.dp)) { ItemInfo(controller, state, sel, onClose = { selected = null }) }
            }
        } else content(Modifier.fillMaxWidth())
    }
}

/** 내 인벤토리·엔더 상자·백팩·옷장·장신구 가방·금고 + 펫 */
private fun ownedIds(state: AppState): Set<String> {
    val p = state.selectedProfile ?: return emptySet()
    val ids = HashSet<String>()
    p.inventories.values.forEach { data -> ItemParser.parse(data).filterNotNull().forEach { it.skyblockId?.let(ids::add) } }
    p.pets.forEach { pet -> ids += "pet:" + pet.type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) } }
    return ids
}

private fun currentLevel(state: AppState, key: String): Int? {
    val p = state.selectedProfile ?: return null
    val def = state.skills?.skills?.get(key) ?: return null
    val xp = p.skillXp?.get(key) ?: return null
    return Leveling.skill(xp, def, p.skillCap(key, def.maxLevel)).level
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPanel(
    controller: AppController, state: AppState, label: String, steps: List<GearStep>, owned: Set<String>,
    selected: String?, wide: Boolean, onSelect: (String) -> Unit,
) {
    val isOwned: (GearStep) -> Boolean = { st -> st.ids.any { it in owned || (it.startsWith("pet:") && owned.any { o -> o.equals(it, true) }) } }
    val lastOwned = steps.indexOfLast(isOwned)
    val next = if (owned.isEmpty()) -1 else (lastOwned + 1).takeIf { it < steps.size } ?: -1
    Panel(Modifier.fillMaxWidth(), title = "$label · ${steps.size}단계", accent = Sb.Gold,
        trailing = { if (lastOwned >= 0) Pill("${steps.count(isOwned)}개 보유", Sb.Green) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { i, st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) Arrow(st.upgradeFromPrev)
                    StepBox(controller, state, st, i + 1, isOwned(st), i == next, st.ids.contains(selected), wide, onSelect)
                }
            }
        }
    }
}

/** 칸 사이 화살표: 업그레이드면 진한 →, 그냥 다음 추천이면 흐린 점선 › */
@Composable
private fun Arrow(upgrade: Boolean) {
    val c = if (upgrade) Sb.Gold else Sb.TextFaint
    Canvas(Modifier.padding(horizontal = 3.dp).size(24.dp, 16.dp)) {
        val y = size.height / 2
        drawLine(c, Offset(1.dp.toPx(), y), Offset(size.width - 7.dp.toPx(), y), strokeWidth = if (upgrade) 2.5.dp.toPx() else 1.5.dp.toPx(),
            pathEffect = if (upgrade) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        drawPath(Path().apply {
            moveTo(size.width - 8.dp.toPx(), y - 5.dp.toPx()); lineTo(size.width - 8.dp.toPx(), y + 5.dp.toPx()); lineTo(size.width, y); close()
        }, c)
    }
}

@Composable
private fun StepBox(
    controller: AppController, state: AppState, st: GearStep, number: Int, owned: Boolean, next: Boolean, selected: Boolean,
    wide: Boolean, onSelect: (String) -> Unit,
) {
    val color = McColors.rarity(st.rarity)
    val border = when { selected -> color; next -> Sb.Gold; owned -> Sb.Green.copy(alpha = 0.7f); else -> Sb.Border }
    HoverTip({
        LoreTooltip(st.title, color, buildList {
            st.rarity?.let { add("§8${McColors.rarityLabel(it)}") }
            if (st.ids.size > 1) st.ids.take(14).forEach { add("§7· ${state.itemName(it)}") }
            val id = st.ids.first()
            state.gameData.npcBuy[id]?.let { add("§7NPC 구매: §e${it.npc}") }
            state.gameData.upgrades[id]?.let { add("§7이전 단계: §f${state.itemName(it)}") }
            if (state.gameData.crafting.containsKey(id)) add("§a제작 가능")
            if (state.gameData.forge.containsKey(id)) add("§b포지에서 제작")
            state.gameData.pets.values.flatten().firstOrNull { "pet:${it.name}" == id }?.lore?.take(10)?.forEach { add("§7$it") }
            if (owned) add("§a✔ 가지고 있어요")
            add(""); add("§8눌러서 자세히 보기")
        })
    }) {
        Column(
            Modifier.widthIn(min = 92.dp, max = 150.dp).clip(RoundedCornerShape(10.dp))
                .background(when { next -> Sb.Gold.copy(alpha = 0.10f); owned -> Sb.Green.copy(alpha = 0.07f); else -> Sb.SurfaceHigh })
                .border(if (next || selected) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
                .clickable {
                    // 넓은 화면: 옆에 정보 / 휴대폰: 아이템 화면으로 (스크롤이 튀지 않게)
                    if (wide) onSelect(st.ids.first()) else controller.openItem(st.ids.first())
                }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", style = SbText.pixel(10, Sb.TextFaint))
                Spacer(Modifier.width(4.dp))
                if (st.ids.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { st.ids.take(4).forEach { ItemIcon(state, it, 22.dp) } }
                } else ItemIcon(state, st.ids.first(), 30.dp)
                if (owned) { Spacer(Modifier.width(4.dp)); Text("✔", style = SbText.body(13, Sb.Green, FontWeight.Bold)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(st.title.removePrefix("pet:"), style = SbText.pixel(11, color, bold = selected), maxLines = 2)
            if (next) Text("다음 추천", style = SbText.pixel(10, Sb.Gold))
        }
    }
}
