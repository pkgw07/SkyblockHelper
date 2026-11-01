package sbhelper.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sbhelper.core.AppState
import sbhelper.core.calc.BestiaryCalc
import sbhelper.core.calc.CollectionCalc
import sbhelper.core.calc.Leveling
import sbhelper.core.calc.MinionCalc
import sbhelper.core.calc.NetworthCalc
import sbhelper.core.data.GameData
import sbhelper.core.model.ProfileSummary
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatNumber
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HoverTip
import sbhelper.ui.components.LoreTooltip
import sbhelper.ui.components.McSlotGrid
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PixelIcon
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.StatTile
import sbhelper.ui.components.WikiIcons
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val STAT_TABS = listOf("순자산", "HotM 트리", "HotF 트리", "도감", "컬렉션", "미니언")

/** 기록: 순자산 · HotM 트리 · 도감(Bestiary) · 컬렉션 · 미니언 */
@Composable
fun StatsScreen(controller: AppController, state: AppState, wide: Boolean) {
    val tab = controller.statsTab
    val p = state.selectedProfile
    ScreenColumn(wide) {
        if (p == null) {
            Panel { EmptyState("계정을 연결해 주세요", "기록을 보려면 계정 연결과 API 키가 필요해요.", icon = Icons.Rounded.Insights) }
            return@ScreenColumn
        }
        Segmented(STAT_TABS, tab, { controller.statsTab = it })
        when (tab) {
            0 -> NetworthTab(controller, state, p, wide)
            1 -> HotmTab(state, p, wide)
            2 -> HotfTab(state, p, wide)
            3 -> BestiaryTab(state, p)
            4 -> CollectionsTab(state, p)
            else -> MinionsTab(state, p)
        }
    }
}

// ------------------------------------------------------------------ 순자산
@Composable
private fun NetworthTab(controller: AppController, state: AppState, p: ProfileSummary, wide: Boolean) {
    val book = state.priceBook
    val nw = remember(p, state.lowestBinAt, state.bazaar) { NetworthCalc.compute(p, book) }
    Panel(Modifier.fillMaxWidth(), caption = "Networth", title = "순자산 ${formatCoins(nw.total)}", accent = Sb.Gold) {
        Text("공식 API 시세로 계산해요: 바자 즉시 판매가 → 경매장 최저 BIN → NPC 판매가. " +
            "강화·인챈트 같은 추가 가치는 빼고 아이템 기본 값만 더해요.", style = SbText.body(12, Sb.TextFaint))
        if (state.lowestBin.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            SbButton(if (state.loadingPrices) "경매장 시세 불러오는 중…" else "경매장 시세 불러오기 (약 30초)",
                { controller.scope.launch { controller.repo.refreshLowestBins(force = true) } }, enabled = !state.loadingPrices, outlined = true)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow2 {
            nw.parts.forEach { (label, v) ->
                StatTile(label, formatCoins(v), Modifier.width(if (wide) 180.dp else 150.dp), color = if (v > 0) Sb.Gold else Sb.TextFaint)
            }
        }
        if (nw.unpriced > 0) {
            Spacer(Modifier.height(8.dp))
            Text("시세를 모르는 아이템 ${nw.unpriced}개는 빠졌어요.", style = SbText.body(12, Sb.TextFaint))
        }
    }
    if (nw.top.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth(), caption = "Most valuable", title = "비싼 아이템", accent = Sb.Gold) {
            nw.top.forEach { (item, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    McSlotGrid(state, listOf(item), 1, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(item.plainName + if (item.count > 1) " ×${item.count}" else "",
                        style = SbText.pixel(13, McColors.rarity(item.tier)), modifier = Modifier.weight(1f), maxLines = 1)
                    Text(formatCoins(v), style = SbText.pixel(13, Sb.Gold))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

// ------------------------------------------------------------------ HotM 트리
/** API 키(예: forge_time)와 위키 이름(Quick Forge)이 다른 경우 */
private val HOTM_ALIASES = mapOf(
    "forge_time" to "quick_forge",
    "random_event" to "luck_of_the_cave",
    "mining_experience" to "seasoned_mineman",
    "experience_orbs" to "orbiter",
    "fallen_star_bonus" to "crystallized",
    "daily_effect" to "sky_mall",
    "mining_speed_2" to "mining_speed_ii",
    "mining_fortune_2" to "mining_fortune_ii",
    "pickaxe_toss" to "pickobulus",
    "special_0" to "core_of_the_mountain",
)

private fun hotmKey(name: String): String =
    name.lowercase().replace("'", "").replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** 위키 칸 이름 → 내 레벨 (없으면 null) */
private fun hotmLevels(layout: List<GameData.HotmCell>, nodes: Map<String, Int>): Pair<Map<String, Int>, Map<String, Int>> {
    val byKey = HashMap<String, Int>()
    nodes.forEach { (k, v) -> byKey[HOTM_ALIASES[k] ?: k] = v }
    val matched = HashMap<String, Int>()
    val used = HashSet<String>()
    layout.forEach { c ->
        val k = hotmKey(c.name)
        val v = byKey[k] ?: byKey[k.replace("_ii", "_2")]
        if (v != null) { matched[c.name] = v; used += k; used += k.replace("_ii", "_2") }
    }
    val unmatched = byKey.filterKeys { it !in used }
    return matched to unmatched
}

@Composable
private fun HotmTab(state: AppState, p: ProfileSummary, wide: Boolean) {
    val layout = state.gameData.hotmLayout
    val (levels, unmatched) = remember(layout, p.hotmNodes) { hotmLevels(layout, p.hotmNodes) }
    val hotmTable = state.gameData.leveling.hotm
    Panel(Modifier.fillMaxWidth(), caption = "Heart of the Mountain", title = "내 HotM 트리", accent = Sb.Aqua) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            p.hotmXp?.takeIf { hotmTable.isNotEmpty() }?.let { xp ->
                StatTile("HotM 레벨", "${Leveling.hotm(xp, hotmTable).level}", color = Sb.Aqua)
            }
            StatTile("Quick Forge", if (p.quickForgeLevel > 0) "레벨 ${p.quickForgeLevel}" else "없음",
                color = if (p.quickForgeLevel > 0) Sb.Green else Sb.TextFaint,
                sub = if (p.quickForgeLevel > 0) "포지 시간 -${state.gameData.rules.quickForge.percent(p.quickForgeLevel).let { sbhelper.core.util.fixed(it, 1) }}%" else null)
            p.powders.forEach { (k, v) ->
                StatTile(when (k) { "mithril" -> "미스릴 파우더"; "gemstone" -> "젬스톤 파우더"; else -> "글라사이트 파우더" }, formatNumber(v),
                    color = when (k) { "mithril" -> Sb.DarkGreen; "gemstone" -> Sb.Pink; else -> Sb.Aqua })
            }
        }
        Spacer(Modifier.height(10.dp))
        TreeGrid(layout, levels, unmatched, p.hotmNodes.isEmpty(), "Heart of the Mountain", wide)
    }
}

@Composable
private fun HotfTab(state: AppState, p: ProfileSummary, wide: Boolean) {
    val layout = state.gameData.hotfLayout
    val (levels, unmatched) = remember(layout, p.hotfNodes) { hotmLevels(layout, p.hotfNodes) }
    Panel(Modifier.fillMaxWidth(), caption = "Heart of the Forest", title = "내 HotF 트리", accent = Sb.Green) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            p.hotfXp?.let { StatTile("HotF 경험치", formatNumber(it.toLong()), color = Sb.Green) }
            StatTile("찍은 칸", "${levels.count { it.value > 0 }} / ${layout.size}", color = Sb.Green)
        }
        Spacer(Modifier.height(10.dp))
        TreeGrid(layout, levels, unmatched, p.hotfNodes.isEmpty(), "Heart of the Forest", wide)
    }
}

/** HotM·HotF 공통: 위키 배치도대로 칸을 그리고, 내 레벨을 색으로 */
@Composable
private fun TreeGrid(layout: List<GameData.HotmCell>, levels: Map<String, Int>, unmatched: Map<String, Int>, noData: Boolean,
                     wikiName: String, wide: Boolean) {
    Text("칸 배치와 설명은 공식 위키($wikiName) 기준이에요. 칸에 마우스를 올리면(휴대폰은 누르면) 설명이 떠요.",
        style = SbText.body(12, Sb.TextFaint))
    Spacer(Modifier.height(12.dp))
    if (layout.isEmpty()) {
        EmptyState("배치 데이터가 없어요", "위키 데이터를 받는 중이에요.")
    } else {
        val cell = if (wide) 54.dp else 38.dp
        val rows = layout.map { it.row }.distinct().sorted()
        val cols = (layout.minOf { it.col })..(layout.maxOf { it.col })
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B0D12)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cols.forEach { c ->
                        val node = layout.firstOrNull { it.row == r && it.col == c }
                        if (node == null) Box(Modifier.size(cell)) else HotmNode(node, levels[node.name], cell)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("■ 최대", Sb.Gold); Pill("■ 찍음", Sb.Green); Pill("■ 능력", Sb.Pink); Pill("■ 안 찍음", Sb.TextFaint)
        }
    }
    if (unmatched.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("위키 칸과 이름이 다른 API 항목: " + unmatched.entries.joinToString(", ") { "${it.key} ${it.value}" },
            style = SbText.body(11, Sb.TextFaint))
    }
    if (noData) {
        Spacer(Modifier.height(10.dp))
        Text("프로필에서 칸 정보를 찾지 못했어요. 설정 → '프로필 구조 복사' 로 알려 주시면 고칠게요.",
            style = SbText.body(12, Sb.Negative))
    }
}

@Composable
private fun HotmNode(node: GameData.HotmCell, level: Int?, size: Dp) {
    val lv = level ?: 0
    val color = when {
        lv <= 0 -> Sb.TextFaint
        node.ability -> Sb.Pink
        lv >= node.maxLevel -> Sb.Gold
        else -> Sb.Green
    }
    HoverTip({
        LoreTooltip(node.name, if (lv > 0) color else Sb.Gray,
            listOf(if (node.ability) "§d능력" else "§7레벨 §a$lv§7 / ${node.maxLevel}", "") +
                node.lore.map { it.trimEnd('\\') }.filter { it.isNotBlank() }.map { "§7$it" })
    }) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = if (lv > 0) 0.22f else 0.06f))
                .border(2.dp, color.copy(alpha = if (lv > 0) 0.9f else 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(node.name.split(' ').joinToString("") { it.take(1) }.take(3),
                    style = SbText.pixel(11, if (lv > 0) Sb.Text else Sb.TextFaint, bold = true))
                if (!node.ability && node.maxLevel > 1) Text(if (lv > 0) "$lv" else "-", style = SbText.pixel(9, color))
            }
        }
    }
}

// ------------------------------------------------------------------ 도감
@Composable
private fun BestiaryTab(state: AppState, p: ProfileSummary) {
    var query by remember { mutableStateOf("") }
    var openAreas by remember { mutableStateOf(setOf<String>()) }
    val entries = remember(state.gameData.bestiary, p.bestiaryKills) { BestiaryCalc.entries(state.gameData, p.bestiaryKills) }
    if (p.bestiaryKills.isEmpty()) {
        Panel { EmptyState("도감 정보가 없어요", "게임 API 설정이 꺼져 있거나 아직 잡은 몹이 없어요.") }
        return
    }
    val maxed = entries.count { it.maxed }
    val tiers = entries.sumOf { it.tier }
    Panel(Modifier.fillMaxWidth(), caption = "Bestiary · Wiki", title = "도감 — 최대 ${maxed}/${entries.size} · 단계 합 $tiers", accent = Sb.Red) {
        Text("몹 목록과 단계별 처치 수는 공식 위키(Bestiary) 기준, 처치 수는 공식 API 기준이에요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        SbTextField(query, { query = it }, Modifier.fillMaxWidth(), "몹 검색", Icons.Rounded.Search)
        Spacer(Modifier.height(8.dp))
        // 지역별로 접어 두고, 누르면 펼친다 (검색 중이면 맞는 지역은 펼쳐서)
        entries.filter { query.isBlank() || it.family.contains(query, true) || it.section.contains(query, true) }
            .groupBy { it.section.ifBlank { "기타" } }.forEach { (section, list) ->
                val key = section
                val open = query.isNotBlank() || key in openAreas
                val areaMax = list.count { it.maxed }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Sb.SurfaceHigh)
                        .clickable { openAreas = if (key in openAreas) openAreas - key else openAreas + key }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (open) "▾" else "▸", style = SbText.body(14, Sb.TextDim))
                    Spacer(Modifier.width(8.dp))
                    Text(section, style = SbText.body(14, Sb.Text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    XpBar(if (list.isEmpty()) 0.0 else list.sumOf { it.tier }.toDouble() / list.sumOf { it.maxTier }.coerceAtLeast(1),
                        Modifier.width(120.dp), if (areaMax == list.size) Sb.Gold else Sb.Red, 6.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("최대 $areaMax/${list.size}", style = SbText.pixel(12, if (areaMax == list.size) Sb.Gold else Sb.TextDim))
                }
                if (open) list.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.family, style = SbText.body(13, if (e.kills > 0) Sb.Text else Sb.TextFaint), modifier = Modifier.width(170.dp), maxLines = 1)
                        XpBar(e.progress, Modifier.weight(1f), if (e.maxed) Sb.Gold else Sb.Red, 6.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("${e.tier}/${e.maxTier}", style = SbText.pixel(12, if (e.maxed) Sb.Gold else Sb.TextDim), modifier = Modifier.width(48.dp))
                        Text(if (e.maxed) formatNumber(e.kills.toLong()) else "${formatNumber(e.kills.toLong())}/${formatNumber(e.nextKills ?: 0)}",
                            style = SbText.body(11, Sb.TextFaint), modifier = Modifier.width(110.dp), maxLines = 1)
                    }
                }
            }
    }
}

// ------------------------------------------------------------------ 컬렉션
@Composable
private fun CollectionsTab(state: AppState, p: ProfileSummary) {
    var query by remember { mutableStateOf("") }
    val resource = state.collections
    val amounts = p.collections
    when {
        amounts == null -> { Panel { EmptyState("컬렉션 API 가 꺼져 있어요", "게임에서 API Settings → Collections 를 켜 주세요.") }; return }
        resource == null -> { Panel { EmptyState("불러오는 중", "") }; return }
    }
    val list = remember(resource, amounts) { CollectionCalc.progress(resource!!, amounts!!) }
    val maxed = list.count { it.maxed }
    Panel(Modifier.fillMaxWidth(), caption = "Collections", title = "컬렉션 — 최대 $maxed/${list.size}", accent = Sb.Yellow) {
        Text("코옵 프로필이면 코옵 멤버 전체 합계예요 (게임과 같은 방식).", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        SbTextField(query, { query = it }, Modifier.fillMaxWidth(), "컬렉션 검색", Icons.Rounded.Search)
        list.filter { query.isBlank() || it.name.contains(query, true) }.groupBy { it.category }.forEach { (cat, items) ->
            Spacer(Modifier.height(10.dp))
            Text("$cat · 최대 ${items.count { it.maxed }}/${items.size}", style = SbText.pixel(13, Sb.Yellow, bold = true))
            items.forEach { c ->
                HoverTip({
                    LoreTooltip(c.name, if (c.maxed) Sb.Gold else Sb.Yellow,
                        listOf("§7모은 수: §a${formatNumber(c.amount)}") +
                            (if (c.maxed) listOf("§6최대 단계") else listOf("§7다음 단계: §e${formatNumber(c.nextRequired ?: 0)}", "") +
                                c.nextUnlocks.take(6).map { "§7· $it" }))
                }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(WikiIcons.url(c.name), c.name, null, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(c.name, style = SbText.body(13), modifier = Modifier.width(170.dp), maxLines = 1)
                        XpBar(c.progress, Modifier.weight(1f), if (c.maxed) Sb.Gold else Sb.Yellow, 6.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (c.maxed) "MAX" else toRomanStats(c.tier), style = SbText.pixel(12, if (c.maxed) Sb.Gold else Sb.TextDim),
                            modifier = Modifier.width(44.dp))
                        Text(formatNumber(c.amount), style = SbText.body(11, Sb.TextFaint), modifier = Modifier.width(100.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun toRomanStats(n: Int): String {
    if (n <= 0) return "0"
    val map = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var x = n
    return buildString { map.forEach { (v, s) -> while (x >= v) { append(s); x -= v } } }
}

// ------------------------------------------------------------------ 미니언
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinionsTab(state: AppState, p: ProfileSummary) {
    val list = remember(p.craftedMinions, state.gameData) { MinionCalc.crafted(state.gameData, p.craftedMinions) { state.itemName(it) } }
    if (p.craftedMinions.isEmpty()) {
        Panel { EmptyState("미니언 정보가 없어요", "아직 만든 미니언이 없거나 정보를 받지 못했어요.") }
        return
    }
    val slotsTiers = list.sumOf { it.tiers.size }
    Panel(Modifier.fillMaxWidth(), caption = "Crafted minions", title = "만든 미니언 — ${list.count { it.highest > 0 }}종 · 단계 합 $slotsTiers", accent = Sb.Yellow) {
        Text("만든 미니언 단계 수가 많을수록 미니언 칸이 늘어나요. 흐린 것은 아직 안 만든 미니언이에요 (목록: 공식 위키).",
            style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { m ->
                HoverTip({
                    LoreTooltip(m.name, if (m.highest > 0) Sb.Yellow else Sb.Gray,
                        listOf(if (m.tiers.isEmpty()) "§7아직 만들지 않았어요" else "§7만든 단계: §a" + m.tiers.sorted().joinToString(", ") { toRomanStats(it) }))
                }) {
                    Row(
                        Modifier.width(210.dp).clip(RoundedCornerShape(10.dp)).background(Sb.SurfaceHigh)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelIcon(WikiIcons.url(m.name), m.name, null, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(m.name.removeSuffix(" Minion"), style = SbText.body(13, if (m.highest > 0) Sb.Text else Sb.TextFaint, FontWeight.Medium),
                            modifier = Modifier.weight(1f), maxLines = 1)
                        Text(if (m.highest > 0) toRomanStats(m.highest) else "-", style = SbText.pixel(12, if (m.highest >= 11) Sb.Gold else Sb.Yellow))
                    }
                }
            }
        }
    }
}
