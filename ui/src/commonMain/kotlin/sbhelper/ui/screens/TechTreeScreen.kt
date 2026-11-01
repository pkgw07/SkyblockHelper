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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.WikiApi
import sbhelper.core.calc.Leveling
import sbhelper.core.data.GameData
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HoverTip
import sbhelper.ui.components.LoreTooltip
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PixelIcon
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.WikiIcons
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val SKILL_TABS = listOf(
    "MINING" to "채광", "FARMING" to "농사", "FORAGING" to "벌목", "FISHING" to "낚시", "HUNTING" to "사냥", "COMBAT" to "전투",
)

/** 스킬별 관련 장신구를 위키 장신구 목록에서 고를 때 쓰는 이름 조각 */
private val ACCESSORY_WORDS = mapOf(
    "MINING" to listOf("Mine", "Mining", "Pickaxe", "Gemstone", "Dwarven", "Mithril", "Titanium", "Glacite", "Powder", "Crystal"),
    "FARMING" to listOf("Farm", "Crop", "Pest", "Squash", "Fermento", "Garden", "Cropie", "Melon", "Pumpkin"),
    "FORAGING" to listOf("Forag", "Wood", "Tree", "Log", "Park", "Leaf", "Fig", "Mangrove"),
    "FISHING" to listOf("Fish", "Sea ", "Bait", "Lava", "Water", "Shark", "Squid", "Angler"),
    "HUNTING" to listOf("Hunt", "Lasso", "Trap", "Shard", "Attribute"),
    "COMBAT" to listOf("Zombie", "Spider", "Wolf", "Enderman", "Blaze", "Vampire", "Slayer", "Bat ", "Dungeon", "Catacombs"),
)

/** 스킬별 장비·펫·장신구 테크트리 (공식 위키 가이드 기준) — 상자와 화살표로 진행 순서를 보여 준다 */
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
        val stages = remember(state.gameData, key) { withExtras(state.gameData, key) }
        val level = currentLevel(state, key)
        Text(
            "공식 위키 가이드 순서대로 위에서 아래로 진행해요. 화살표(→)는 앞의 아이템을 재료로 다음 아이템을 만든다는 뜻이에요. " +
                "아이템을 누르면 제작법·NPC 구매·드롭을 볼 수 있어요." + (level?.let { " 내 $label 레벨: $it" } ?: ""),
            style = SbText.body(13, Sb.TextFaint),
        )
        val content: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                stages.forEachIndexed { i, st ->
                    if (i > 0) DownArrow()
                    StageBox(state, stages, st, i, level, selected) { selected = it }
                }
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

/** 위키 가이드 단계 + (가이드에 없으면) 스킬 펫, 관련 장신구 단계 */
private fun withExtras(data: GameData, key: String): List<GameData.GuideStage> {
    val stages = data.techTrees[key].orEmpty().toMutableList()
    val inTree = stages.flatMap { it.items }.toSet()
    val pets = data.pets[key].orEmpty().map { "pet:${it.name}" }.filter { it !in inTree }
    if (pets.isNotEmpty()) {
        stages += GameData.GuideStage("Pets", "$key 펫 (위키 Pets 목록)", "가이드에 없는 이 스킬의 펫이에요. 마우스를 올리면 능력을 볼 수 있어요.",
            pets, "Pets")
    }
    val words = ACCESSORY_WORDS[key].orEmpty()
    val accs = data.accessories.filter { a -> a.id !in inTree && words.any { a.name.contains(it, ignoreCase = true) } }
        // 업그레이드 줄에서는 첫 단계만 (화살표로 다음 단계가 이어진다)
        .let { list -> val ids = list.map { it.id }.toSet(); list.filter { it.from == null || it.from !in ids } }
    if (accs.isNotEmpty()) {
        val byId = data.accessories.associateBy { it.id }
        val lines = accs.flatMap { root -> generateSequence(root) { it.to?.let(byId::get) }.take(6).map { it.id }.toList() }.distinct()
        stages += GameData.GuideStage("Accessories", "관련 장신구 (위키 Accessories 목록)", "", lines.take(60), "Accessories")
    }
    return stages
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

/** 이 아이템을 만들 때 재료로 쓰는 (트리 안의) 이전 아이템 */
private fun predecessor(data: GameData, id: String, candidates: Set<String>): String? {
    data.upgrades[id]?.let { if (it in candidates) return it }
    data.accessories.firstOrNull { it.id == id }?.from?.let { if (it in candidates) return it }
    val inputs = (data.crafting[id]?.firstOrNull()?.`in`?.keys.orEmpty() + data.forge[id]?.firstOrNull()?.`in`?.keys.orEmpty())
    return inputs.firstOrNull { it in candidates && it != id }
}

/** 단계 안의 아이템을 업그레이드 사슬(A → B → C)로 묶는다 */
private fun chains(data: GameData, items: List<String>): List<List<String>> {
    val set = items.toSet()
    val prev = items.associateWith { predecessor(data, it, set) }
    val next = HashMap<String, String>()
    prev.forEach { (b, a) -> if (a != null && a !in next) next[a] = b }
    val used = HashSet<String>()
    val out = mutableListOf<List<String>>()
    for (start in items) {
        if (start in used || (prev[start] != null && prev[start] in set && next[prev[start]] == start)) continue
        val chain = generateSequence(start) { next[it] }.takeWhile { used.add(it) }.take(10).toList()
        if (chain.isNotEmpty()) out += chain
    }
    items.filter { it !in used }.forEach { out += listOf(it) }
    return out
}

@Composable
private fun DownArrow() {
    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp, 30.dp)) {
            val c = Sb.Gold.copy(alpha = 0.7f)
            val x = size.width / 2
            drawLine(c, Offset(x, 0f), Offset(x, size.height - 8.dp.toPx()), strokeWidth = 3.dp.toPx())
            drawPath(Path().apply {
                moveTo(x - 7.dp.toPx(), size.height - 10.dp.toPx())
                lineTo(x + 7.dp.toPx(), size.height - 10.dp.toPx())
                lineTo(x, size.height)
                close()
            }, c)
        }
    }
}

@Composable
private fun RightArrow(color: Color) {
    Canvas(Modifier.size(26.dp, 18.dp)) {
        val y = size.height / 2
        drawLine(color, Offset(2.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), strokeWidth = 2.5.dp.toPx())
        drawPath(Path().apply {
            moveTo(size.width - 10.dp.toPx(), y - 6.dp.toPx())
            lineTo(size.width - 10.dp.toPx(), y + 6.dp.toPx())
            lineTo(size.width, y)
            close()
        }, color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageBox(
    state: AppState, all: List<GameData.GuideStage>, st: GameData.GuideStage, index: Int, level: Int?,
    selected: String?, onSelect: (String) -> Unit,
) {
    val platform = LocalPlatform.current
    val current = isCurrentStage(st.title, level)
    val c = when {
        current -> Sb.Gold
        st.group == "Pets" -> Sb.Pink
        st.group == "Accessories" -> Sb.Purple
        else -> Sb.Aqua
    }
    // 앞 단계에 있는 아이템
    val earlier = remember(all, index) { all.take(index).flatMap { it.items }.toSet() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
            .border(if (current) 2.dp else 1.dp, if (current) Sb.Gold.copy(alpha = 0.8f) else c.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = SbText.pixel(14, c, bold = true))
            }
            Spacer(Modifier.width(10.dp))
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
        Spacer(Modifier.height(10.dp))
        val groups = remember(st.items, state.gameData) { chains(state.gameData, st.items.take(60)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { chain ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 이전 단계 아이템에서 이어지는 경우
                    val from = predecessor(state.gameData, chain.first(), earlier)
                    if (from != null) {
                        Text(state.itemName(from), style = SbText.pixel(10, Sb.TextFaint), maxLines = 1, modifier = Modifier.widthIn(max = 110.dp))
                        RightArrow(Sb.TextFaint)
                    }
                    chain.forEachIndexed { i, id ->
                        if (i > 0) RightArrow(c)
                        ItemBox(state, id, id == selected) { onSelect(id) }
                    }
                }
            }
        }
    }
}

/** 상자 안의 아이템 (아이콘 + 이름). 마우스를 올리면 게임 툴팁 */
@Composable
private fun ItemBox(state: AppState, id: String, selected: Boolean, onClick: () -> Unit) {
    val name = state.itemName(id)
    val tier = state.itemTier(id)
    val isPet = id.startsWith("pet:")
    val pet = if (isPet) state.gameData.pets.values.flatten().firstOrNull { it.name == id.removePrefix("pet:") } else null
    val color = McColors.rarity(tier ?: pet?.rarity?.uppercase())
    HoverTip({
        LoreTooltip(name, color, buildList {
            pet?.lore?.take(14)?.forEach { add("§7$it") }
            state.gameData.npcBuy[id]?.let {
                add("§7NPC 구매: §e${it.npc}" + if (it.coins > 0) " §6${sbhelper.core.util.formatCoins(it.coins)}" else "")
                if (it.cost.isNotBlank()) add("§7  필요: §f${it.cost}")
            }
            state.gameData.upgrades[id]?.let { add("§7이전 단계: §f${state.itemName(it)}") }
            if (state.gameData.crafting.containsKey(id)) add("§a제작 가능")
            if (state.gameData.forge.containsKey(id)) add("§b포지에서 제작")
            add(""); add("§8눌러서 자세히 보기")
        })
    }) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (selected) color.copy(alpha = 0.18f) else Sb.SurfaceHigh)
                .border(1.dp, if (selected) color else Sb.Border, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(WikiIcons.url(name), name, tier, 26.dp)
            Spacer(Modifier.width(7.dp))
            Text(name, style = SbText.pixel(12, color, bold = selected), maxLines = 1, modifier = Modifier.widthIn(max = 180.dp))
        }
    }
}
