package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import sbhelper.core.calc.EventLabels
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

private val STAT_TABS = listOf("개요", "순자산", "HotM 트리", "HotF 트리", "펫", "던전", "가든·제이콥", "크림슨·리프트·낚시",
    "도감", "컬렉션", "미니언", "박물관", "기타 통계")

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
        // 탭이 많아서 옆으로 넘길 수 있게
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Segmented(STAT_TABS, tab, { controller.statsTab = it })
        }
        val details = remember(p.member, state.garden, state.gameData) {
            sbhelper.core.calc.ProfileDetailsCalc.compute(p.member, state.garden, state.gameData)
        }
        when (tab) {
            0 -> OverviewTab(state, p, details, wide)
            1 -> NetworthTab(controller, state, p, wide)
            2 -> HotmTab(state, p, wide)
            3 -> HotfTab(state, p, wide)
            4 -> PetsTab(controller, state, details)
            5 -> DungeonsTab(state, p, details)
            6 -> GardenTab(state, details)
            7 -> CrimsonRiftFishingTab(details)
            8 -> BestiaryTab(state, p)
            9 -> CollectionsTab(state, p)
            10 -> MinionsTab(state, p)
            11 -> MuseumTab(controller, state, wide)
            else -> MiscStatsTab(details)
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
    TreePanel(
        caption = "Heart of the Mountain", title = "내 HotM 트리", accent = Sb.Aqua, short = "HotM",
        xp = p.hotmXp, table = state.gameData.leveling.hotm, tokens = p.tokensSpent["mountain"],
        layout = layout, levels = levels, unmatched = unmatched, noData = p.hotmNodes.isEmpty(), wiki = "Heart of the Mountain", wide = wide,
    ) {
        StatTile("Quick Forge", if (p.quickForgeLevel > 0) "레벨 ${p.quickForgeLevel}" else "없음",
            color = if (p.quickForgeLevel > 0) Sb.Green else Sb.TextFaint,
            sub = if (p.quickForgeLevel > 0) "포지 시간 -${sbhelper.core.util.fixed(state.gameData.rules.quickForge.percent(p.quickForgeLevel), 1)}%" else null)
        p.powders.forEach { (k, v) ->
            StatTile(when (k) { "mithril" -> "미스릴 파우더"; "gemstone" -> "젬스톤 파우더"; else -> "글라사이트 파우더" }, formatNumber(v),
                color = when (k) { "mithril" -> Sb.DarkGreen; "gemstone" -> Sb.Pink; else -> Sb.Aqua })
        }
    }
}

@Composable
private fun HotfTab(state: AppState, p: ProfileSummary, wide: Boolean) {
    val layout = state.gameData.hotfLayout
    val (levels, unmatched) = remember(layout, p.hotfNodes) { hotmLevels(layout, p.hotfNodes) }
    TreePanel(
        caption = "Heart of the Forest", title = "내 HotF 트리", accent = Sb.Green, short = "HotF",
        xp = p.hotfXp, table = state.gameData.leveling.hotf, tokens = p.tokensSpent["forest"],
        layout = layout, levels = levels, unmatched = unmatched, noData = p.hotfNodes.isEmpty(), wiki = "Heart of the Forest", wide = wide,
    ) {
        levels["Center of the Forest"]?.let { StatTile("Center of the Forest", "레벨 $it", color = Sb.Green) }
    }
}

/** HotM·HotF 같은 모양: 레벨·진행도·쓴 토큰·찍은 칸 + 트리 */
@Composable
private fun TreePanel(
    caption: String, title: String, accent: Color, short: String, xp: Double?, table: List<Long>, tokens: Int?,
    layout: List<GameData.HotmCell>, levels: Map<String, Int>, unmatched: Map<String, Int>, noData: Boolean, wiki: String, wide: Boolean,
    extraTiles: @Composable () -> Unit,
) {
    Panel(Modifier.fillMaxWidth(), caption = caption, title = title, accent = accent) {
        val info = xp?.takeIf { table.isNotEmpty() }?.let { Leveling.hotm(it, table) }
        FlowRow2 {
            StatTile("$short 레벨", info?.let { "${it.level}" } ?: "-", color = accent,
                sub = info?.let { if (it.level >= it.maxLevel) "최대" else "다음까지 ${sbhelper.core.util.fixed(it.progress * 100, 0)}%" })
            xp?.let { StatTile("$short 경험치", formatNumber(it.toLong()), color = accent) }
            tokens?.let { StatTile("쓴 토큰", "$it", color = accent) }
            StatTile("찍은 칸", "${levels.count { it.value > 0 }} / ${layout.size}", color = accent)
            extraTiles()
        }
        info?.let {
            Spacer(Modifier.height(8.dp))
            XpBar(it.progress, Modifier.fillMaxWidth(), accent, 7.dp)
        }
        Spacer(Modifier.height(10.dp))
        TreeGrid(layout, levels, unmatched, noData, wiki, wide)
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


// ------------------------------------------------------------------ 박물관
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuseumTab(controller: AppController, state: AppState, wide: Boolean) {
    val museum = state.museum
    var cat by remember { mutableStateOf<String?>(null) }
    var showDonated by remember { mutableStateOf(false) }
    val slots = remember(state.items, museum) { sbhelper.core.calc.MuseumCalc.slots(state.items, museum) }
    if (museum == null) {
        Panel { EmptyState("박물관 정보가 없어요", "게임에서 박물관 API 설정을 켜야 하거나, 아직 불러오는 중이에요.") }
        return
    }
    val total = slots.size
    val done = slots.count { it.donated }
    Panel(Modifier.fillMaxWidth(), caption = "Museum", title = "박물관 — ${done}/${total}칸 기증", accent = Sb.Gold) {
        Text("칸 목록·분야·기증 경험치는 공식 아이템 API, 내 기증은 공식 museum API 기준이에요. " +
            "상위 아이템을 기증하면 하위 아이템도 기증한 것으로 쳐요(게임과 같음).", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(10.dp))
        FlowRow2 {
            museum.value?.let { StatTile("박물관 가치", formatCoins(it), color = Sb.Gold) }
            StatTile("기증 경험치", "${slots.filter { it.donated }.sumOf { it.xp }} / ${slots.sumOf { it.xp }}", color = Sb.Gold)
            StatTile("특별 아이템", "${museum.special}개", color = Sb.Purple)
        }
        Spacer(Modifier.height(10.dp))
        // 분야별 진행
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sbhelper.core.calc.MuseumCalc.CATEGORY_KO.forEach { (k, ko) ->
                val list = slots.filter { it.category == k }
                if (list.isEmpty()) return@forEach
                val d = list.count { it.donated }
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (cat == k) Sb.Gold.copy(alpha = 0.15f) else Sb.SurfaceHigh)
                        .border(1.dp, if (cat == k) Sb.Gold else Sb.Border, RoundedCornerShape(10.dp))
                        .clickable { cat = if (cat == k) null else k }.padding(10.dp),
                ) {
                    Text(ko, style = SbText.body(14, Sb.Text, FontWeight.SemiBold))
                    Text("$d / ${list.size}", style = SbText.pixel(12, if (d == list.size) Sb.Gold else Sb.TextDim))
                    Spacer(Modifier.height(4.dp))
                    XpBar(d.toDouble() / list.size.coerceAtLeast(1), Modifier.fillMaxWidth(), Sb.Gold, 5.dp)
                }
            }
        }
    }
    val shown = slots.filter { (cat == null || it.category == cat) && it.donated == showDonated }
        .sortedWith(compareBy({ sbhelper.core.calc.MuseumCalc.STAGES.indexOf(it.stage).let { i -> if (i < 0) 99 else i } }, { it.xp }))
    Panel(Modifier.fillMaxWidth(), title = (if (showDonated) "기증한 칸" else "아직 기증 안 한 칸") + " ${shown.size}개" +
        (cat?.let { " · " + sbhelper.core.calc.MuseumCalc.CATEGORY_KO[it] } ?: ""), accent = Sb.Gold,
        trailing = { Segmented(listOf("빠진 것", "기증한 것"), if (showDonated) 1 else 0, { showDonated = it == 1 }) }) {
        Text("게임 진행 단계(공식: 시작 → 마스터) 순서예요. 누르면 아이템 화면으로 가요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        var lastStage: String? = "\u0000"
        shown.take(400).forEach { slot ->
            if (slot.stage != lastStage) {
                lastStage = slot.stage
                Spacer(Modifier.height(6.dp))
                Text(sbhelper.core.calc.MuseumCalc.STAGE_KO[slot.stage] ?: "기타", style = SbText.pixel(13, Sb.Gold, bold = true))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { controller.openItem(slot.pieces.first()) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    slot.pieces.take(4).forEach { sbhelper.ui.components.ItemIcon(state, it, 24.dp) }
                }
                Spacer(Modifier.width(10.dp))
                val name = if (slot.isSet) state.itemName(slot.pieces.first())
                    .replace(Regex("""\s+(Helmet|Hat|Chestplate|Tunic|Leggings|Trousers|Boots|Slippers|Belt|Necklace|Cloak|Gloves)$"""), "") + " 세트"
                    else state.itemName(slot.key)
                Column(Modifier.weight(1f)) {
                    Text(name, style = SbText.body(13, McColors.rarity(state.itemTier(slot.pieces.first())), FontWeight.Medium), maxLines = 1)
                    slot.viaParent?.let { Text("상위 아이템(${state.itemName(it)}) 기증으로 인정", style = SbText.body(11, Sb.TextFaint)) }
                }
                Text("${sbhelper.core.calc.MuseumCalc.CATEGORY_KO[slot.category] ?: slot.category} · +${slot.xp}", style = SbText.pixel(11, Sb.TextDim))
            }
        }
    }
}


// ------------------------------------------------------------------ SkyCrypt 수준 상세 기록 (공식 API 원본 + 위키 레벨표)
private fun nice(key: String) = key.replace('_', ' ').lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun timeText(ms: Long?): String = ms?.let { val s = it / 1000; "${s / 60}:${(s % 60).toString().padStart(2, '0')}" } ?: "-"

@Composable
private fun NoDetails() = Panel { EmptyState("정보가 없어요", "프로필을 불러오는 중이거나, 게임 API 설정이 꺼져 있어요.") }

@Composable
private fun OverviewTab(state: AppState, p: ProfileSummary, d: sbhelper.core.calc.ProfileDetails?, wide: Boolean) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Overview", title = "개요", accent = Sb.Gold) {
        FlowRow2 {
            d.skyblockLevel?.let { StatTile("스카이블록 레벨", sbhelper.core.util.fixed(it, 2), color = Sb.Aqua) }
            d.magicalPower?.let { StatTile("매지컬 파워", formatNumber(it.toLong()), color = Sb.Gold, sub = d.selectedPower?.let { s -> "파워: ${nice(s)}" }) }
            d.fairySouls?.let { StatTile("요정 영혼", "$it", color = Sb.Pink) }
            d.petScore?.let { StatTile("펫 점수", "$it", color = Sb.Green) }
            StatTile("지갑", formatCoins(p.purse), color = Sb.Gold)
            p.bank?.let { StatTile("은행", formatCoins(it), color = Sb.Gold) }
            d.motes?.let { StatTile("리프트 모트", formatNumber(it.toLong()), color = Sb.Pink) }
            d.cookieActive?.let { StatTile("부스터 쿠키", if (it) "사용 중" else "없음", color = if (it) Sb.Green else Sb.TextFaint) }
            d.firstJoin?.let { StatTile("처음 시작", localTime(it, true).substringBefore(' '), color = Sb.TextDim) }
        }
        Spacer(Modifier.height(8.dp))
        Text("스카이블록 레벨 = SkyBlock XP ÷ ${state.gameData.timerRules.skyblockXpPerLevel} (위키 SkyBlock Levels). 나머지는 공식 프로필 API 값이에요.",
            style = SbText.body(12, Sb.TextFaint))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetsTab(controller: AppController, state: AppState, d: sbhelper.core.calc.ProfileDetails?) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Pets", title = "펫 ${d.pets.size}마리" + (d.petScore?.let { " · 펫 점수 $it" } ?: ""), accent = Sb.Green) {
        Text("레벨은 위키 펫 레벨표(Module:Pet/LevelingData)로 계산해요. 펫을 누르면 위키 정보로 가요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.pets.forEach { pet ->
                val color = McColors.rarity(pet.tier)
                HoverTip({
                    LoreTooltip("[Lvl ${pet.level}] ${pet.name}", color, buildList {
                        add("§8${McColors.rarityLabel(pet.tier)}")
                        add("§7경험치: §a${formatNumber(pet.exp.toLong())}")
                        if (pet.level < pet.maxLevel) add("§7다음 레벨까지 §e${sbhelper.core.util.fixed(pet.progress * 100, 1)}%")
                        pet.heldItem?.let { add("§7들고 있는 아이템: §f${state.itemName(it)}") }
                        if (pet.candyUsed > 0) add("§7사탕: §a${pet.candyUsed}")
                        if (pet.active) add("§a지금 데리고 다니는 펫")
                    })
                }) {
                    Column(
                        Modifier.width(150.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (pet.active) Sb.Green.copy(alpha = 0.12f) else Sb.SurfaceHigh)
                            .border(1.dp, if (pet.active) Sb.Green else Sb.Border, RoundedCornerShape(10.dp))
                            .clickable { controller.openItem("pet:" + pet.name) }.padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIcon(WikiIcons.url(pet.name + " Pet"), pet.name + " Pet", pet.tier, 28.dp, id = "pet:" + pet.name)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Lvl ${pet.level}", style = SbText.pixel(11, Sb.TextDim))
                                Text(pet.name, style = SbText.pixel(12, color, bold = true), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        XpBar(if (pet.level >= pet.maxLevel) 1.0 else pet.progress, Modifier.fillMaxWidth(), color, 4.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DungeonsTab(state: AppState, p: ProfileSummary, d: sbhelper.core.calc.ProfileDetails?) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Dungeons", title = "던전", accent = Sb.Red) {
        FlowRow2 {
            p.catacombsXp?.takeIf { state.gameData.leveling.catacombs.isNotEmpty() }?.let { xp ->
                StatTile("카타콤 레벨", "${Leveling.catacombs(xp, state.gameData.leveling.catacombs).level}", color = Sb.Red)
            }
            d.secrets?.let { StatTile("찾은 시크릿", formatNumber(it), color = Sb.Aqua) }
            d.selectedClass?.let { StatTile("선택한 클래스", nice(it), color = Sb.Gold) }
            d.highestFloor?.let { StatTile("최고 층", "F$it", color = Sb.Red) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("층" to 70, "클리어" to 80, "S+ 최고 기록" to 110, "S 최고 기록" to 110, "최고 점수" to 80).forEach { (h, w) ->
                Text(h, style = SbText.caption(Sb.TextDim), modifier = Modifier.width(w.dp))
            }
        }
        d.floors.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text((if (f.master) "M" else if (f.floor == 0) "입구" else "F") + (if (f.floor == 0 && !f.master) "" else "${f.floor}"),
                    style = SbText.pixel(12, if (f.master) Sb.Red else Sb.Text, bold = true), modifier = Modifier.width(70.dp))
                Text(formatNumber(f.completions.toLong()), style = SbText.body(13), modifier = Modifier.width(80.dp))
                Text(timeText(f.fastestSPlusMs), style = SbText.body(13, Sb.Gold), modifier = Modifier.width(110.dp))
                Text(timeText(f.fastestSMs), style = SbText.body(13, Sb.TextDim), modifier = Modifier.width(110.dp))
                Text(f.bestScore?.toString() ?: "-", style = SbText.body(13, Sb.TextDim), modifier = Modifier.width(80.dp))
            }
        }
    }
}

@Composable
private fun GardenTab(state: AppState, d: sbhelper.core.calc.ProfileDetails?) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Garden", title = "가든", accent = Sb.Green) {
        if (d.gardenXp == null) { Text("가든 정보가 없어요 (가든을 연 적이 없거나 불러오는 중).", style = SbText.body(13, Sb.TextFaint)); return@Panel }
        FlowRow2 {
            d.gardenLevel?.let { StatTile("가든 레벨", "$it", color = Sb.Green, sub = "Garden XP ${formatNumber(d.gardenXp.toLong())}") }
            d.visitorsServed?.let { StatTile("방문자 요청 완료", formatNumber(it.toLong()), color = Sb.Aqua) }
            d.visitorsUnique?.let { StatTile("만난 방문자", "$it 명", color = Sb.Aqua) }
            d.plots?.let { StatTile("연 구역", "$it 칸", color = Sb.Green) }
        }
        if (d.cropUpgrades.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("작물 업그레이드", style = SbText.caption(Sb.TextDim))
            d.cropUpgrades.forEach { (crop, lv) -> InfoRow(EventLabels.crop(cropName(crop)) + " (" + cropName(crop) + ")", "Lv $lv") }
        }
        Spacer(Modifier.height(6.dp))
        Text("가든 레벨은 위키 The Garden 레벨표로 계산해요.", style = SbText.body(12, Sb.TextFaint))
    }
    Panel(Modifier.fillMaxWidth(), caption = "Jacob's Contest", title = "제이콥 농사대회", accent = Sb.Gold) {
        FlowRow2 {
            listOf("bronze" to "동", "silver" to "은", "gold" to "금", "platinum" to "백금", "diamond" to "다이아").forEach { (k, ko) ->
                d.jacobMedals[k]?.let { StatTile("$ko 메달", "$it", color = Sb.Gold) }
            }
            StatTile("고유 금메달 작물", "${d.jacobUniqueGolds}", color = Sb.Gold)
        }
        if (d.jacobPerks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            d.jacobPerks.forEach { (k, v) -> InfoRow(when (k) { "double_drops" -> "2배 드롭"; "farming_level_cap" -> "농사 최대 레벨 +"; else -> nice(k) }, "$v") }
        }
        if (d.jacobPersonalBests.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("작물별 개인 최고 기록", style = SbText.caption(Sb.TextDim))
            d.jacobPersonalBests.entries.sortedByDescending { it.value }.forEach { (crop, n) ->
                InfoRow(EventLabels.crop(cropName(crop)) + " (" + cropName(crop) + ")", formatNumber(n))
            }
        }
    }
}

private fun cropName(id: String) = when (id) {
    "CARROT_ITEM" -> "Carrot"; "POTATO_ITEM" -> "Potato"; "NETHER_STALK" -> "Nether Wart"; "INK_SACK:3" -> "Cocoa Beans"
    "MUSHROOM_COLLECTION" -> "Mushroom"; "SUGAR_CANE" -> "Sugar Cane"; else -> nice(id)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SbText.body(13, Sb.TextDim), modifier = Modifier.weight(1f))
        Text(value, style = SbText.pixel(13, Sb.Text))
    }
}

@Composable
private fun CrimsonRiftFishingTab(d: sbhelper.core.calc.ProfileDetails?) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Crimson Isle", title = "크림슨 아일", accent = Sb.Red) {
        FlowRow2 {
            d.faction?.let { StatTile("세력", if (it == "mages") "마법사 (Mages)" else if (it == "barbarians") "바바리안 (Barbarians)" else nice(it), color = Sb.Red) }
            d.reputation["mages"]?.let { StatTile("마법사 평판", formatNumber(it), color = Sb.Purple) }
            d.reputation["barbarians"]?.let { StatTile("바바리안 평판", formatNumber(it), color = Sb.Red) }
        }
        if (d.kuudra.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("쿠드라 클리어", style = SbText.caption(Sb.TextDim))
            d.kuudra.forEach { (tier, n) -> InfoRow(when (tier) { "none" -> "Basic"; else -> nice(tier) }, "${n}회") }
        }
        if (d.dojo.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("도조 시험 점수 (합 ${d.dojo.values.sum()})", style = SbText.caption(Sb.TextDim))
            d.dojo.forEach { (test, pts) -> InfoRow(nice(test), "$pts") }
        }
        if (d.faction == null && d.kuudra.isEmpty() && d.dojo.isEmpty()) Text("크림슨 아일 기록이 없어요.", style = SbText.body(13, Sb.TextFaint))
    }
    Panel(Modifier.fillMaxWidth(), caption = "Trophy Fish", title = "트로피 피시" + (d.trophyTotal?.let { " · 총 ${formatNumber(it.toLong())}마리" } ?: ""), accent = Sb.Gold) {
        if (d.trophyFish.isEmpty()) { Text("트로피 피시 기록이 없어요.", style = SbText.body(13, Sb.TextFaint)); return@Panel }
        Row(Modifier.fillMaxWidth()) {
            Text("물고기", style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(1f))
            listOf("동", "은", "금", "다이아").forEach { Text(it, style = SbText.caption(Sb.TextDim), modifier = Modifier.width(52.dp)) }
        }
        d.trophyFish.entries.sortedBy { it.key }.forEach { (fish, tiers) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(nice(fish), style = SbText.body(13), modifier = Modifier.weight(1f))
                listOf("bronze", "silver", "gold", "diamond").forEach { t ->
                    Text(tiers[t]?.toString() ?: "-", style = SbText.pixel(12, if ((tiers[t] ?: 0) > 0) Sb.Text else Sb.TextFaint), modifier = Modifier.width(52.dp))
                }
            }
        }
    }
    Panel(Modifier.fillMaxWidth(), caption = "Rift · Mining", title = "리프트 · 채광", accent = Sb.Pink) {
        FlowRow2 {
            d.motes?.let { StatTile("모트 (지금)", formatNumber(it.toLong()), color = Sb.Pink) }
            d.powderTotals.forEach { (k, v) -> StatTile("${when (k) { "mithril" -> "미스릴"; "gemstone" -> "젬스톤"; else -> "글라사이트" }} 파우더 (누적)", formatNumber(v), color = Sb.Aqua) }
            d.skyMall?.let { StatTile("오늘의 Sky Mall", nice(it), color = Sb.Gold) }
        }
        if (d.crystals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("크리스탈 할로우 크리스탈", style = SbText.caption(Sb.TextDim))
            d.crystals.forEach { (k, st) -> InfoRow(nice(k.removeSuffix("_crystal")), if (st == "PLACED") "설치함" else if (st == "FOUND") "찾음" else nice(st)) }
        }
    }
}

@Composable
private fun MiscStatsTab(d: sbhelper.core.calc.ProfileDetails?) {
    if (d == null) { NoDetails(); return }
    Panel(Modifier.fillMaxWidth(), caption = "Kills · Deaths", title = "처치 ${formatNumber(d.totalKills)} · 사망 ${formatNumber(d.totalDeaths)}", accent = Sb.Red) {
        Text("가장 많이 잡은 몹", style = SbText.caption(Sb.TextDim))
        d.kills.take(15).forEach { (k, n) -> InfoRow(nice(k), formatNumber(n)) }
        Spacer(Modifier.height(8.dp))
        Text("가장 많이 죽은 원인", style = SbText.caption(Sb.TextDim))
        d.deaths.take(10).forEach { (k, n) -> InfoRow(nice(k), formatNumber(n)) }
    }
    Panel(Modifier.fillMaxWidth(), caption = "Other", title = "그 밖의 기록", accent = Sb.TextDim) {
        d.itemsFished?.let { InfoRow("낚은 아이템", formatNumber(it)) }
        d.seaCreatureKills?.let { InfoRow("잡은 바다 생물", formatNumber(it)) }
        d.candy?.let { InfoRow("모은 사탕 (스푸키 축제)", formatNumber(it)) }
        val a = d.auctions
        if (a.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("경매장", style = SbText.caption(Sb.TextDim))
            a["created"]?.let { InfoRow("올린 경매", formatNumber(it.toLong())) }
            a["completed"]?.let { InfoRow("끝난 경매", formatNumber(it.toLong())) }
            a["gold_earned"]?.let { InfoRow("번 코인", formatCoins(it)) }
            a["gold_spent"]?.let { InfoRow("쓴 코인", formatCoins(it)) }
            a["fees"]?.let { InfoRow("수수료", formatCoins(it)) }
            a["highest_bid"]?.let { InfoRow("가장 높은 입찰", formatCoins(it)) }
        }
    }
}
