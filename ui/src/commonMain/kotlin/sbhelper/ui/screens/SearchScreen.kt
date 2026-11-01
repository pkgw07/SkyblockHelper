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
            list == null -> Text("공식 위키에서 찾는 중…", style = SbText.body(13, Sb.TextFaint))
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
private fun ItemQuickTip(state: AppState, id: String) {
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

    // 시세
    val bz = state.bazaar?.products?.get(id)?.quickStatus
    val npc = state.items[id]?.npcSellPrice
    val bin = state.lowestBin[id]
    if (bz != null || npc != null || bin != null) {
        Panel(Modifier.fillMaxWidth(), caption = "Price", title = "시세", accent = Sb.Gold) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bz?.let {
                    sbhelper.ui.components.StatTile("바자 즉시 구매", formatCoins(it.buyPrice), color = Sb.Gold)
                    sbhelper.ui.components.StatTile("바자 즉시 판매", formatCoins(it.sellPrice), color = Sb.Gold)
                }
                bin?.let { sbhelper.ui.components.StatTile("경매장 최저 BIN", formatCoins(it), color = Sb.Gold) }
                npc?.let { sbhelper.ui.components.StatTile("NPC 판매가", formatCoins(it), color = Sb.Yellow) }
            }
        }
    }

    // 얻는 법
    Panel(Modifier.fillMaxWidth(), caption = "Obtaining", title = "얻는 법", accent = Sb.Green) {
        var any = false
        d.upgrades[id]?.let { prev ->
            any = true
            Label("이전 단계에서 업그레이드")
            ItemLink(controller, state, prev)
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
        d.crafting[id]?.firstOrNull()?.let { r ->
            any = true
            Label("제작" + if (r.count > 1) " (한 번에 ${r.count}개)" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.`in`.forEach { (k, q) -> ItemLink(controller, state, k, "${fixed(q, if (q % 1.0 == 0.0) 0 else 1)}×") }
            }
        }
        d.forge[id]?.firstOrNull()?.let { f ->
            any = true
            Label("포지 · ${formatDuration(f.sec * 1000)} · HotM ${f.hotm}" + if (f.coins > 0) " · ${formatCoins(f.coins)} 코인" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                f.`in`.forEach { (k, q) -> ItemLink(controller, state, k, "${fixed(q, if (q % 1.0 == 0.0) 0 else 1)}×") }
            }
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
        if (!any) Text("위키 제작·구매 데이터에 없어요. 아래 위키 설명을 확인해 주세요.", style = SbText.body(13, Sb.TextFaint))
    }

    // 쓰이는 곳 + 다음 단계
    val used = index.usedIn[id].orEmpty()
    val next = index.nextUpgrades[id].orEmpty()
    if (used.isNotEmpty() || next.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth(), caption = "Usage", title = "쓰이는 곳", accent = Sb.Aqua) {
            if (next.isNotEmpty()) {
                Label("다음 단계로 업그레이드")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    next.forEach { ItemLink(controller, state, it) }
                }
            }
            if (used.isNotEmpty()) {
                Label("재료로 쓰여요 (${used.size}개)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    used.take(80).forEach { ItemLink(controller, state, it) }
                }
            }
        }
    }

    // 위키 설명 (링크를 누르면 그 문서/아이템으로)
    Panel(Modifier.fillMaxWidth().widthIn(max = 1100.dp), caption = "Wiki", title = "위키 설명", accent = Sb.TextDim) {
        when {
            blocks != null -> WikiBlocks(controller, blocks, max = 120)
            WikiPages.errors[page] != null -> Text("위키 문서가 없거나 불러오지 못했어요.", style = SbText.body(13, Sb.TextFaint))
            else -> Text("공식 위키에서 불러오는 중…", style = SbText.body(13, Sb.TextFaint))
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
        trailing = { SbButton("위키에서 보기", { platform.openUrl(WikiApi.pageUrl(title)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true, color = Sb.Aqua) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(WikiIcons.url(title), title, null, 48.dp)
        }
        Spacer(Modifier.height(8.dp))
        when {
            blocks != null -> WikiBlocks(controller, blocks, max = 160)
            WikiPages.errors[title] != null -> Text("⚠ ${WikiPages.errors[title]}", style = SbText.body(13, Sb.Negative))
            else -> Text("공식 위키에서 불러오는 중…", style = SbText.body(13, Sb.TextFaint))
        }
    }
}
