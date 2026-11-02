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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sbhelper.core.api.WikiHit
import sbhelper.core.data.CraftTarget
import sbhelper.core.data.GameData
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.Screen
import sbhelper.ui.WikiTarget
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HoverTip
import sbhelper.ui.components.ItemIcon
import sbhelper.ui.components.LoreTooltip
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PixelIcon
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.WikiIcons
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 쓰이는 곳(역방향 제작법) 색인: 재료 → 그 재료로 만드는 아이템 */
private class UsageIndex(data: GameData) {
    val usedIn: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        data.crafting.forEach { (out, recipes) -> recipes.firstOrNull()?.`in`?.keys?.forEach { getOrPut(it) { mutableListOf() } += out } }
        data.forge.forEach { (out, recipes) -> recipes.firstOrNull()?.`in`?.keys?.forEach { getOrPut(it) { mutableListOf() } += out } }
    }.mapValues { it.value.distinct() }
    /** 재료 → 그 재료가 들어가는 조합대 (결과, 배치) */
    val gridUses: Map<String, List<Pair<String, GameData.CraftGrid>>> = buildMap<String, MutableList<Pair<String, GameData.CraftGrid>>> {
        data.craftGrids.forEach { (out, grids) ->
            grids.forEach { g -> g.g.flatten().map { it.i }.distinct().forEach { getOrPut(it) { mutableListOf() } += out to g } }
        }
    }
    /** 재료 → 그 재료로 포지에서 만드는 아이템 */
    val forgeUses: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        data.forge.forEach { (out, recipes) -> recipes.firstOrNull()?.`in`?.keys?.forEach { getOrPut(it) { mutableListOf() } += out } }
    }
    val nextUpgrades: Map<String, List<String>> = data.upgrades.entries.groupBy({ it.value }, { it.key })
}


/** 위키 검색: 모든 아이템(공식 API 목록) + NPC·지역·이벤트 등 위키 문서 */
@Composable
fun SearchScreen(controller: AppController, state: AppState, wide: Boolean) {
    val target = controller.wiki
    ScreenColumn(wide) {
        SearchBox(controller)
        when (target) {
            null -> SearchResults(controller, state, wide)
            is WikiTarget.Item -> ItemPage(controller, state, target.id, wide)
            is WikiTarget.Page -> PagePanel(controller, target.title)
        }
    }
}

@Composable
private fun SearchBox(controller: AppController) {
    SbTextField(controller.searchQuery, {
        controller.searchQuery = it
        if (controller.wiki != null) controller.wiki = null
    }, Modifier.fillMaxWidth(), "아이템·NPC·지역·이벤트 검색 (예: Hyperion, Bubu, Dark Auction)", Icons.Rounded.Search)
}

@Composable
private fun SearchResults(controller: AppController, state: AppState, wide: Boolean) {
    val q = controller.searchQuery.trim()
    if (q.length < 2) {
        Panel(Modifier.fillMaxWidth()) {
            EmptyState("무엇이든 찾아보세요", "아이템을 고르면 얻는 법·쓰이는 곳·제작법을 보여 주고, 재료를 누르면 그 아이템으로 이어져요. " +
                "마우스 뒤로 버튼(휴대폰은 뒤로 버튼)으로 돌아갈 수 있어요.", icon = Icons.AutoMirrored.Rounded.MenuBook)
        }
        return
    }
    // 아이템: 공식 API 목록 + 위키 데이터 이름
    val items = remember(q, state.items, state.gameData) {
        val names = LinkedHashMap<String, String>()
        state.items.values.forEach { names[it.id] = it.name.replace(Regex("§."), "") }
        state.gameData.names.forEach { (id, n) -> if (id !in names) names[id] = n }
        state.gameData.pets.values.flatten().forEach { p -> names.getOrPut("pet:${p.name}") { "${p.name} Pet" } }
        names.entries.filter { it.value.contains(q, true) || it.key.contains(q.replace(' ', '_'), true) }
            .sortedWith(compareBy({ !it.value.startsWith(q, true) }, { it.value.length }))
            .take(40).map { it.key }
    }
    var hits by remember(q) { mutableStateOf<List<WikiHit>?>(null) }
    LaunchedEffect(q) {
        kotlinx.coroutines.delay(350)
        hits = runCatching { controller.repo.wiki.search(q, 15) }.getOrDefault(emptyList())
    }
    val itemNames = items.map { state.itemName(it).lowercase() }.toSet()
    Panel(Modifier.fillMaxWidth(), caption = "Items", title = "아이템 ${items.size}개", accent = Sb.Gold) {
        if (items.isEmpty()) Text("아이템 이름에서 찾지 못했어요.", style = SbText.body(13, Sb.TextFaint))
        FlowGrid(wide) { items.forEach { id -> ItemLink(controller, state, id) } }
    }
    Panel(Modifier.fillMaxWidth(), caption = "Wiki pages", title = "위키 문서 (NPC·지역·이벤트 등)", accent = Sb.Aqua) {
        val list = hits?.filter { it.title.lowercase() !in itemNames }
        when {
            list == null -> Text("찾는 중…", style = SbText.body(13, Sb.TextFaint))
            list.isEmpty() -> Text("위키 문서를 찾지 못했어요.", style = SbText.body(13, Sb.TextFaint))
            else -> list.forEach { h ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { controller.openWikiLink(h.title) }
                    .padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(WikiIcons.url(h.title), h.title, null, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = SbText.body(14, Sb.Aqua, FontWeight.Medium))
                        if (h.snippet.isNotBlank()) Text(h.snippet, style = SbText.body(12, Sb.TextFaint), maxLines = 2)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowGrid(wide: Boolean, content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** 아이템 칩: 누르면 그 아이템 화면, 마우스를 올리면 요약 */
@Composable
fun ItemLink(controller: AppController, state: AppState, id: String, prefix: String? = null) {
    HoverTip({ ItemQuickTip(state, id) }) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Sb.SurfaceHigh).border(1.dp, Sb.Border, RoundedCornerShape(10.dp))
                .clickable { controller.openItem(id) }.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) { Text(prefix, style = SbText.pixel(12, Sb.TextDim)); Spacer(Modifier.width(4.dp)) }
            ItemIcon(state, id, 22.dp)
            Spacer(Modifier.width(6.dp))
            RarityName(state.itemName(id), state.itemTier(id), size = 13)
        }
    }
}

/** 마우스를 올렸을 때 뜨는 요약 */
@Composable
internal fun ItemQuickTip(state: AppState, id: String) {
    val d = state.gameData
    LoreTooltip(state.itemName(id), McColors.rarity(state.itemTier(id)), buildList {
        state.itemTier(id)?.let { add("§8${McColors.rarityLabel(it)}") }
        state.items[id]?.npcSellPrice?.let { add("§7NPC 판매가 §6${formatCoins(it)}") }
        d.npcBuy[id]?.let { add("§7NPC 구매: §e${it.npc}" + if (it.coins > 0) " §6${formatCoins(it.coins)}" else "") }
        d.crafting[id]?.firstOrNull()?.let { r -> add("§a제작: §7" + r.`in`.entries.take(4).joinToString(", ") { (k, q) -> "${fixed(q, 0)}× ${state.itemName(k)}" }) }
        d.forge[id]?.firstOrNull()?.let { f -> add("§b포지: §7${formatDuration(f.sec * 1000)}") }
        d.sources.drops[id]?.let { add("§c드롭: §7" + it.take(3).joinToString(", ")) }
        add(""); add("§8눌러서 자세히 보기")
    })
}

// ------------------------------------------------------------------ 아이템 화면
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemPage(controller: AppController, state: AppState, id: String, wide: Boolean) {
    val platform = LocalPlatform.current
    val d = state.gameData
    val name = state.itemName(id).replace(Regex("§."), "")
    val tier = state.itemTier(id)
    val index = remember(d) { UsageIndex(d) }
    val page = if (id.startsWith("pet:")) id.removePrefix("pet:") + " Pet" else name
    val blocks = rememberWikiPage(controller, page)

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIcon(state, id, 54.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                RarityName(name, tier, size = 20, bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tier?.let { Pill(McColors.rarityLabel(it), McColors.rarity(it)) }
                    state.items[id]?.category?.let { Pill(it.replace('_', ' '), Sb.TextDim) }
                    Text(id, style = SbText.pixel(11, Sb.TextFaint))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.forge.containsKey(id) || d.crafting.containsKey(id)) {
                SbButton("제작 플래너", {
                    controller.repo.updateSynced { s -> s.copy(craftTargets = (listOf(CraftTarget(id, 1)) + s.craftTargets.filterNot { it.itemId == id }).take(12),
                        craftCurrent = CraftTarget(id, 1)) }
                    controller.ironmanTab = 1
                    controller.go(Screen.IRONMAN)
                }, icon = Icons.Rounded.Handyman, outlined = true)
            }
            SbButton("위키에서 보기", { platform.openUrl(WikiApi.pageUrl(page)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true, color = Sb.Aqua)
        }
    }

    // 시세 (작게 한 줄)
    val bz = state.bazaar?.products?.get(id)?.quickStatus
    val npc = state.items[id]?.npcSellPrice
    val bin = state.lowestBin[id]
    if (bz != null || npc != null || bin != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("시세", style = SbText.caption(Sb.TextDim), modifier = Modifier.padding(top = 4.dp, end = 4.dp))
            bz?.takeIf { it.buyPrice > 0 }?.let { PriceChip("바자 구매", it.buyPrice) }
            bz?.takeIf { it.sellPrice > 0 }?.let { PriceChip("바자 판매", it.sellPrice) }
            bin?.let { PriceChip("최저 BIN", it) }
            npc?.let { PriceChip("NPC 판매", it, Sb.Yellow) }
        }
    }
    // 바자 아이템: 시세 그래프 (볼 때부터 기록 시작)
    if (bz != null) {
        androidx.compose.runtime.LaunchedEffect(id) { controller.repo.trackPrice(id) }
        Panel(Modifier.fillMaxWidth(), caption = "Price History", title = "시세 변화", accent = Sb.Gold) {
            sbhelper.ui.components.PriceChart(state.priceHistory.series[id].orEmpty())
        }
    }

    // 게임 툴팁 모양: 스탯·능력·세트 효과 (위키 → 없으면 공식 API 스탯)
    val tooltip = rememberItemTooltip(controller, page, name)
    val apiLines = remember(id, state.items) { apiTooltip(state, id) }
    val tipLines = tooltip ?: apiLines
    val gems = state.items[id]?.gemstoneSlots.orEmpty()
    if (!tipLines.isNullOrEmpty() || gems.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth(), caption = "Stats", title = "능력치 · 효과", accent = Sb.Purple) {
            sbhelper.ui.components.McTooltip(Modifier.widthIn(max = 560.dp)) {
                sbhelper.ui.components.McText(name, size = 15, default = McColors.rarity(tier))
                Spacer(Modifier.height(6.dp))
                tipLines.orEmpty().forEach { line ->
                    if (line.isBlank()) Spacer(Modifier.height(6.dp)) else sbhelper.ui.components.McText(line, size = 13)
                }
                if (gems.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    sbhelper.ui.components.McText("§7Gemstone Slots: " + gems.joinToString(" ") { "§8[§f" + gemName(it.type) + "§8]" }, size = 12)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(if (tooltip != null) "기본 아이템 기준이에요. 내 아이템의 별·리포지·인챈트 수치는 인벤토리에서 마우스를 올려 보세요."
                else "공식 아이템 API 의 기본 스탯이에요.", style = SbText.body(12, Sb.TextFaint))
        }
    }

    // 직접 만들면 얼마? (재료 시세: 바자 즉시 구매가 → 없으면 최저 BIN)
    val price: (String) -> Double? = { k -> state.bazaar?.products?.get(k)?.quickStatus?.buyPrice?.takeIf { it > 0 } ?: state.lowestBin[k] }
    val direct = remember(id, state.bazaar, state.lowestBin, d) { sbhelper.core.calc.CraftCost.direct(d, id, price) }
    val cheapest = remember(id, state.bazaar, state.lowestBin, d) { sbhelper.core.calc.CraftCost.cheapest(d, id, price) }
    if (direct != null) CraftCostPanel(controller, state, id, direct, cheapest, price(id))

    val slot = if (wide) 40.dp else 34.dp
    val grids = d.craftGrids[id].orEmpty()
    val forgeRecipe = d.forge[id]?.firstOrNull()

    // 필요 조건: 만들 때(위키 조합법) · 쓸 때(공식 아이템 API) · 포지(HotM)
    val craftReqs = grids.mapNotNull { sbhelper.core.calc.Requirements.recipe(state, it.r) }.distinctBy { it.text }
    val useReqs = useRequirements(state, id)
    val forgeReq = forgeRecipe?.hotm?.let { h -> Regex("""\d+""").find(h)?.value?.toIntOrNull() }?.let { tier ->
        sbhelper.core.calc.Requirements.item(state, kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("HEART_OF_THE_MOUNTAIN")); put("tier", kotlinx.serialization.json.JsonPrimitive(tier))
        })
    }
    if (craftReqs.isNotEmpty() || useReqs.isNotEmpty() || forgeReq != null) {
        Panel(Modifier.fillMaxWidth(), caption = "Requirements", title = "필요 조건", accent = Sb.Red) {
            if (craftReqs.isNotEmpty()) {
                Label("만들려면 (조합법 잠금 해제)")
                craftReqs.forEach { RequirementRow(controller, it) }
            }
            if (forgeReq != null) {
                Label("포지에서 만들려면")
                RequirementRow(controller, forgeReq)
            }
            if (useReqs.isNotEmpty()) {
                Label("쓰려면 (착용·사용 조건)")
                useReqs.forEach { (label, r) -> RequirementRow(controller, r, label) }
            }
            Spacer(Modifier.height(6.dp))
            Text("✔ 달성 · ✘ 아직 · • 프로필로 확인할 수 없는 조건",
                style = SbText.body(12, Sb.TextFaint))
        }
    }

    // 얻는 법
    Panel(Modifier.fillMaxWidth(), caption = "Obtaining", title = "얻는 법", accent = Sb.Green) {
        var any = false
        if (grids.isNotEmpty()) {
            any = true
            Label(if (grids.size > 1) "조합법 ${grids.size}가지" else "조합법")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                grids.forEach { g -> CraftGridView(controller, state, g, id, slot) }
            }
        } else d.crafting[id]?.firstOrNull()?.let { r ->
            // 위키에 칸 배치가 없는 조합법: 재료만
            any = true
            Label("제작 재료" + if (r.count > 1) " (한 번에 ${r.count}개)" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.`in`.forEach { (k, q) -> ItemLink(controller, state, k, "${fixed(q, if (q % 1.0 == 0.0) 0 else 1)}×") }
            }
        }
        forgeRecipe?.let { f ->
            any = true
            Label("포지 (The Forge) · ${formatDuration(f.sec * 1000)}" + if (f.coins > 0) " · ${formatCoins(f.coins)} 코인" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                f.`in`.forEach { (k, q) -> ItemLink(controller, state, k, "${fixed(q, if (q % 1.0 == 0.0) 0 else 1)}×") }
            }
        }
        d.npcBuy[id]?.let { b ->
            any = true
            Label("NPC 에게서 구매")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.npc, style = SbText.body(14, Sb.Aqua, FontWeight.Medium),
                    modifier = Modifier.clickable { controller.openPage(b.npc.split(",").first().trim()) })
                if (b.coins > 0) Text("  ·  ${formatCoins(b.coins)} 코인", style = SbText.body(14, Sb.Gold))
            }
            if (b.cost.isNotBlank()) Text("필요: ${b.cost}", style = SbText.body(13, Sb.TextDim))
        }
        d.upgrades[id]?.let { prev ->
            any = true
            Label("이전 단계에서 업그레이드")
            ItemLink(controller, state, prev)
        }
        d.sources.drops[id]?.let { mobs ->
            any = true
            Label("몹 드롭")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                mobs.take(12).forEach { m -> Text(m, style = SbText.body(13, Sb.Aqua), modifier = Modifier.clickable { controller.openPage(m) }) }
            }
        }
        d.sources.minions[id]?.let { m ->
            any = true
            Label("미니언")
            Text(m, style = SbText.body(13, Sb.Aqua), modifier = Modifier.clickable { controller.openWikiLink(m) })
        }
        d.accessories.firstOrNull { it.id == id }?.let { a ->
            if (a.source.isNotBlank()) { any = true; Label("장신구 · ${a.category}"); Text(a.source, style = SbText.body(13, Sb.TextDim)) }
        }
        // 위키 본문의 얻는 법 (조합법 표 틀은 위에서 칸으로 그렸고, 본문에서는 빠짐 — 제목만 남은 절은 자동으로 숨김)
        val wikiObtain = blocks != null && hasSections(blocks, OBTAINING_SECTIONS)
        if (wikiObtain) {
            Spacer(Modifier.height(6.dp))
            WikiBlocks(controller, blocks!!, sections = OBTAINING_SECTIONS, max = 60, page = page)
        }
        if (!any && !wikiObtain) Text("위키에 얻는 법 정보가 없어요.", style = SbText.body(13, Sb.TextFaint))
    }

    // 쓰이는 곳: 다음 단계 → 이 아이템이 들어가는 조합대 → 포지 → 위키 본문
    val gridUses = index.gridUses[id].orEmpty()
    val forgeUses = index.forgeUses[id].orEmpty()
    val gridOutputs = gridUses.map { it.first }.toSet()
    val otherUses = index.usedIn[id].orEmpty().filter { it !in gridOutputs && it !in forgeUses }
    val next = index.nextUpgrades[id].orEmpty()
    val wikiUsage = blocks != null && hasSections(blocks, USAGE_SECTIONS)
    if (gridUses.isNotEmpty() || forgeUses.isNotEmpty() || otherUses.isNotEmpty() || next.isNotEmpty() || wikiUsage) {
        Panel(Modifier.fillMaxWidth(), caption = "Usage", title = "쓰이는 곳", accent = Sb.Aqua) {
            if (next.isNotEmpty()) {
                Label("다음 단계로 업그레이드")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    next.forEach { ItemLink(controller, state, it) }
                }
            }
            if (gridUses.isNotEmpty()) {
                var all by remember(id) { mutableStateOf(false) }
                val limit = if (wide) 12 else 6
                Label("이 아이템이 들어가는 조합법 ${gridUses.size}개 (금색 칸이 이 아이템)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (if (all) gridUses.take(120) else gridUses.take(limit)).forEach { (out, g) ->
                        CraftGridView(controller, state, g, out, if (wide) 34.dp else 30.dp, highlight = id, showOutputName = true)
                    }
                }
                if (gridUses.size > limit) {
                    Spacer(Modifier.height(6.dp))
                    SbButton(if (all) "접기" else "${gridUses.size - limit}개 더 보기", { all = !all }, outlined = true)
                }
            }
            if (forgeUses.isNotEmpty()) {
                Label("포지 재료로 (${forgeUses.size}개)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    forgeUses.take(60).forEach { ItemLink(controller, state, it) }
                }
            }
            if (otherUses.isNotEmpty()) {
                Label("그 밖의 재료로 (${otherUses.size}개)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    otherUses.take(60).forEach { ItemLink(controller, state, it) }
                }
            }
            if (wikiUsage) {
                Spacer(Modifier.height(6.dp))
                WikiBlocks(controller, blocks!!, sections = USAGE_SECTIONS, max = 60, page = page)
            }
        }
    }

    // 위키 설명 (링크를 누르면 그 문서/아이템으로)
    Panel(Modifier.fillMaxWidth().widthIn(max = 1100.dp), caption = "Wiki", title = "위키 설명", accent = Sb.TextDim,
        trailing = { TranslateButton(controller, page, blocks) }) {
        when {
            // 얻는 법·쓰이는 곳은 위 칸에 나눠 넣었으므로 여기서는 뺀다
            blocks != null -> WikiBlocks(controller, blocks, max = 120, exclude = OBTAINING_SECTIONS + USAGE_SECTIONS, page = page)
            WikiPages.errors[page] != null -> Text("위키 문서가 없거나 불러오지 못했어요.", style = SbText.body(13, Sb.TextFaint))
            else -> Text("불러오는 중…", style = SbText.body(13, Sb.TextFaint))
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = SbText.caption(Sb.TextDim))
    Spacer(Modifier.height(4.dp))
}

// ------------------------------------------------------------------ 위키 문서 화면 (NPC·지역·이벤트 등)
@Composable
private fun PagePanel(controller: AppController, title: String) {
    val platform = LocalPlatform.current
    val blocks = rememberWikiPage(controller, title)
    Panel(Modifier.fillMaxWidth(), caption = "Wiki", title = title, accent = Sb.Aqua,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslateButton(controller, title, blocks)
                SbButton("위키에서 보기", { platform.openUrl(WikiApi.pageUrl(title)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true, color = Sb.Aqua)
            }
        }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(WikiIcons.url(title), title, null, 48.dp)
        }
        Spacer(Modifier.height(8.dp))
        when {
            blocks != null -> WikiBlocks(controller, blocks, max = 160, page = title)
            WikiPages.errors[title] != null -> Text("⚠ ${WikiPages.errors[title]}", style = SbText.body(13, Sb.Negative))
            else -> Text("불러오는 중…", style = SbText.body(13, Sb.TextFaint))
        }
    }
}

@Composable
private fun PriceChip(label: String, v: Double, color: androidx.compose.ui.graphics.Color = Sb.Gold) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Sb.SurfaceHigh).border(1.dp, Sb.Border, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SbText.body(11, Sb.TextDim))
        Spacer(Modifier.width(6.dp))
        Text(formatCoins(v), style = SbText.pixel(12, color, bold = true))
    }
}

private fun gemName(t: String) = t.lowercase().replaceFirstChar(Char::uppercase)

/** 위키 게임 툴팁 (받아 둔 것은 다시 받지 않음) */
@Composable
private fun rememberItemTooltip(controller: AppController, page: String, name: String): List<String>? {
    val key = "$page|$name"
    LaunchedEffect(key) {
        if (key !in WikiPages.tooltips) {
            runCatching { controller.repo.wiki.itemTooltip(page, name) }.onSuccess { WikiPages.tooltips[key] = it?.lines.orEmpty() }
        }
    }
    return WikiPages.tooltips[key]?.takeIf { it.isNotEmpty() }
}

/** 공식 아이템 API 의 기본 스탯을 툴팁 줄로 */
private fun apiTooltip(state: AppState, id: String): List<String>? {
    val item = state.items[id] ?: return null
    if (item.stats.isEmpty()) return null
    val red = setOf("DAMAGE", "STRENGTH", "CRITICAL_CHANCE", "CRITICAL_DAMAGE", "ATTACK_SPEED", "FEROCITY", "CRIT_CHANCE", "CRIT_DAMAGE")
    val lines = item.stats.map { (k, v) ->
        val key = k.uppercase()
        val label = key.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
            .replace("Critical", "Crit").replace("Walk Speed", "Speed").replace("Attack Speed", "Bonus Attack Speed")
        val num = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        "§7$label: " + (if (key in red) "§c" else "§a") + (if (v >= 0) "+" else "") + num + if (key.contains("CRIT") || key == "ATTACK_SPEED") "%" else ""
    }
    return lines + listOfNotNull(
        "",
        if (item.soulbound != null) "§8* 소울바운드 (거래 불가) *" else null,
        item.tier?.let { "§l" + it.replace('_', ' ') + (item.category?.let { c -> " " + c.replace('_', ' ') } ?: "") },
    )
}

/** 직접 제작 비용: 재료 사서 만들기 / 원재료부터 가장 싸게 / 시세와 비교 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CraftCostPanel(controller: AppController, state: AppState, id: String, direct: sbhelper.core.calc.CraftCost.Result,
                           cheapest: sbhelper.core.calc.CraftCost.Cheapest?, market: Double?) {
    fun qty(q: Double) = if (q % 1.0 == 0.0) formatNumberLocal(q.toLong()) else fixed(q, 1)
    @Composable
    fun Unpriced(list: List<sbhelper.core.calc.CraftCost.Line>) {
        if (list.isEmpty()) return
        Spacer(Modifier.height(4.dp))
        Text("가격 없음 (합계에서 뺐어요)", style = SbText.caption(Sb.Red))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { l ->
                val why = when { state.items[l.id]?.soulbound != null -> "소울바운드"; l.id.startsWith("name:") || state.items[l.id] == null -> "거래 불가"; else -> "시세 없음" }
                ItemLink(controller, state, l.id, "${qty(l.qty)}× · $why")
            }
        }
    }
    Panel(Modifier.fillMaxWidth(), caption = "Craft cost", title = "직접 만들면", accent = Sb.Green) {
        // 1) 재료를 사서 한 번 만들기
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (direct.forge) "포지 재료를 사서 만들기" else "재료를 사서 만들기", style = SbText.body(14, Sb.Text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(formatCoins(direct.total) + if (direct.count > 1) " (1개당)" else "", style = SbText.pixel(15, Sb.Gold, bold = true))
        }
        direct.lines.filter { it.unit != null }.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { controller.openItem(l.id) }, verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(state, l.id, 20.dp); Spacer(Modifier.width(6.dp))
                    RarityName(state.itemName(l.id), state.itemTier(l.id), size = 13)
                }
                Text("${qty(l.qty)} × ${formatCoins(l.unit!!)} = ", style = SbText.body(12, Sb.TextDim))
                Text(formatCoins(l.total!!), style = SbText.pixel(12, Sb.Gold))
            }
        }
        if (direct.coins > 0) Text("포지 코인 ${formatCoins(direct.coins)}", style = SbText.body(12, Sb.TextDim))
        if (direct.count > 1) Text("한 번에 ${direct.count}개가 나와요 (위 합계는 1개 기준)", style = SbText.body(12, Sb.TextFaint))
        Unpriced(direct.unpriced)

        // 2) 원재료부터 가장 싸게
        if (cheapest != null && (cheapest.total < direct.total - 0.5 || cheapest.unpriced.size != direct.unpriced.size)) {
            sbhelper.ui.components.Divider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("원재료부터 가장 싸게", style = SbText.body(14, Sb.Text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text(formatCoins(cheapest.total), style = SbText.pixel(15, Sb.Green, bold = true))
            }
            Text("재료마다 사는 것과 만드는 것 중 싼 쪽을 끝까지 따라갔어요. 사야 할 것:", style = SbText.body(12, Sb.TextFaint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cheapest.buy.take(24).forEach { l -> ItemLink(controller, state, l.id, "${qty(l.qty)}×") }
            }
            if (cheapest.coins > 0) Text("포지 코인 ${formatCoins(cheapest.coins)} 포함", style = SbText.body(12, Sb.TextDim))
            Unpriced(cheapest.unpriced)
        }

        // 3) 그냥 사는 것과 비교
        if (market != null) {
            sbhelper.ui.components.Divider(Modifier.padding(vertical = 8.dp))
            val best = minOf(direct.total, cheapest?.total ?: direct.total)
            val diff = market - best
            val partial = direct.unpriced.isNotEmpty() || (cheapest?.unpriced?.isNotEmpty() == true)
            Text(
                (if (diff > 0) "직접 만들면 약 ${formatCoins(diff)} 아껴요" else "그냥 사는 게 약 ${formatCoins(-diff)} 싸요") +
                    " (완성품 ${formatCoins(market)})" + if (partial) " — 가격 없는 재료는 빼고 계산했어요" else "",
                style = SbText.body(14, if (diff > 0) Sb.Green else Sb.Gold, FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("재료 값: 바자 즉시 구매가, 없으면 경매장 최저 BIN (계정 연결 시 30분마다 갱신).", style = SbText.body(11, Sb.TextFaint))
    }
}

private fun formatNumberLocal(n: Long) = sbhelper.core.util.formatNumber(n)

