package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.BazaarCalc
import sbhelper.core.data.PriceAlert
import sbhelper.core.data.PriceSide
import sbhelper.core.util.Clock
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatNumber
import sbhelper.core.util.formatPercent
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HeaderCell
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.McTooltip
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.TableRow
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private enum class BzTab(val label: String) { FLIP("바자 플립"), NPC("NPC 플립"), WATCH("관심 목록"), ALERTS("가격 알림") }
private enum class BzSort { PROFIT, MARGIN, VOLUME, POTENTIAL }

@Composable
fun BazaarScreen(controller: AppController, state: AppState, wide: Boolean) {
    var tab by remember { mutableStateOf(BzTab.FLIP) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(BzSort.POTENTIAL) }
    var minVolume by remember { mutableStateOf("10000") }
    var selected by remember { mutableStateOf<String?>(null) }

    val products = state.bazaar?.products.orEmpty()
    val tax = state.bazaarTaxRate

    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 28.dp else 14.dp).padding(bottom = 20.dp, top = if (wide) 4.dp else 10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (state.selectedProfile?.gameMode?.canTrade == false) {
                Text("${state.selectedProfile?.gameMode?.badge} ${state.selectedProfile?.gameMode?.label} 프로필은 바자를 쓸 수 없어요. 시세 참고용으로만 보여 드려요.",
                    style = SbText.body(13, Sb.Gray), modifier = Modifier.padding(bottom = 10.dp))
            }
            Segmented(BzTab.entries.map { it.label }, tab.ordinal, { tab = BzTab.entries[it] })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbTextField(query, { query = it }, Modifier.weight(1f), "아이템 검색", Icons.Rounded.Search)
                if (tab == BzTab.FLIP || tab == BzTab.NPC) {
                    Spacer(Modifier.width(10.dp))
                    SbTextField(minVolume, { v -> minVolume = v.filter { it.isDigit() } }, Modifier.width(150.dp), "최소 주간 거래량", numeric = true)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (products.isEmpty()) {
                Panel { EmptyState("바자 시세를 불러오는 중이에요", state.networkError ?: "공식 API 에서 1분마다 새로 고쳐요.") }
                return@Column
            }
            val minVol = minVolume.toLongOrNull() ?: 0
            val q = query.trim()
            fun matches(id: String) = q.isEmpty() || state.itemName(id).contains(q, true) || id.contains(q, true)

            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
                .border(1.dp, Sb.Border, RoundedCornerShape(14.dp)).padding(8.dp)) {
                when (tab) {
                    BzTab.FLIP, BzTab.WATCH -> {
                        val flips = BazaarCalc.flips(products, tax, if (tab == BzTab.WATCH) 0 else minVol)
                            .filter { matches(it.id) && (tab == BzTab.FLIP || it.id in state.synced.watchlist) }
                            .let { list ->
                                when (sort) {
                                    BzSort.PROFIT -> list.sortedByDescending { it.profitPerItem }
                                    BzSort.MARGIN -> list.sortedByDescending { it.marginRatio }
                                    BzSort.VOLUME -> list.sortedByDescending { it.weeklyVolume }
                                    BzSort.POTENTIAL -> list.sortedByDescending { it.hourlyPotential }
                                }
                            }
                        if (flips.isEmpty()) {
                            EmptyState(if (tab == BzTab.WATCH) "관심 목록이 비어 있어요" else "조건에 맞는 아이템이 없어요",
                                if (tab == BzTab.WATCH) "☆ 를 눌러 아이템을 관심 목록에 추가하세요." else "검색어나 최소 거래량을 바꿔 보세요.")
                        } else Column {
                            TableRow {
                                HeaderCell("아이템", Modifier.weight(2.4f))
                                HeaderCell("구매 주문", Modifier.weight(1f))
                                HeaderCell("판매 주문", Modifier.weight(1f))
                                HeaderCell("개당 이익", Modifier.weight(1f), sort == BzSort.PROFIT) { sort = BzSort.PROFIT }
                                if (wide) HeaderCell("마진", Modifier.weight(0.8f), sort == BzSort.MARGIN) { sort = BzSort.MARGIN }
                                if (wide) HeaderCell("주간 거래", Modifier.weight(1f), sort == BzSort.VOLUME) { sort = BzSort.VOLUME }
                                HeaderCell("잠재 이익/h", Modifier.weight(1f), sort == BzSort.POTENTIAL) { sort = BzSort.POTENTIAL }
                                Spacer(Modifier.width(30.dp))
                            }
                            LazyColumn {
                                items(flips.take(400), key = { it.id }) { f ->
                                    TableRow(onClick = { selected = f.id }, selected = selected == f.id) {
                                        Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                                            ItemGlyph(state.itemName(f.id), state.itemTier(f.id), 26.dp)
                                            Spacer(Modifier.width(10.dp))
                                            RarityName(state.itemName(f.id), state.itemTier(f.id), size = 13)
                                        }
                                        Cell(formatCoins(f.buyOrderPrice), Modifier.weight(1f))
                                        Cell(formatCoins(f.sellOfferPrice), Modifier.weight(1f))
                                        Cell(formatCoins(f.profitPerItem), Modifier.weight(1f), if (f.profitPerItem > 0) Sb.Positive else Sb.Negative)
                                        // 마진이 비정상적으로 크면 가격 조작·거래 부족일 가능성이 높다
                                        if (wide) Cell((if (f.marginRatio > 1.0) "⚠ " else "") + formatPercent(f.marginRatio), Modifier.weight(0.8f),
                                            if (f.marginRatio > 1.0) Sb.Red else Sb.TextDim)
                                        if (wide) Cell(formatNumber(f.weeklyVolume), Modifier.weight(1f), Sb.TextDim)
                                        Cell(formatCoins(f.hourlyPotential), Modifier.weight(1f), Sb.Gold)
                                        StarButton(f.id in state.synced.watchlist) { controller.repo.toggleWatch(f.id) }
                                    }
                                }
                            }
                        }
                    }
                    BzTab.NPC -> {
                        val flips = BazaarCalc.npcFlips(products, state.npcPrices, minVol).filter { matches(it.id) }
                        if (flips.isEmpty()) EmptyState("NPC 플립 기회가 없어요", "최소 거래량을 낮춰 보세요.")
                        else Column {
                            Text("바자에서 구매 주문으로 사서 NPC 에게 팔면 남는 아이템이에요.", style = SbText.body(12, Sb.TextFaint),
                                modifier = Modifier.padding(8.dp))
                            TableRow {
                                HeaderCell("아이템", Modifier.weight(2.4f))
                                HeaderCell("구매 주문", Modifier.weight(1f))
                                HeaderCell("NPC 판매가", Modifier.weight(1f))
                                HeaderCell("개당 이익", Modifier.weight(1f))
                                if (wide) HeaderCell("주간 판매", Modifier.weight(1f))
                            }
                            LazyColumn {
                                items(flips.take(300), key = { it.id }) { f ->
                                    TableRow(onClick = { selected = f.id }, selected = selected == f.id) {
                                        Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                                            ItemGlyph(state.itemName(f.id), state.itemTier(f.id), 26.dp)
                                            Spacer(Modifier.width(10.dp))
                                            RarityName(state.itemName(f.id), state.itemTier(f.id), size = 13)
                                        }
                                        Cell(formatCoins(f.buyOrderPrice), Modifier.weight(1f))
                                        Cell(formatCoins(f.npcPrice), Modifier.weight(1f))
                                        Cell(formatCoins(f.profitPerItem), Modifier.weight(1f), Sb.Positive)
                                        if (wide) Cell(formatNumber(f.weeklyVolume), Modifier.weight(1f), Sb.TextDim)
                                    }
                                }
                            }
                        }
                    }
                    BzTab.ALERTS -> AlertsList(controller, state)
                }
            }
        }
        val sel = selected
        if (sel != null && products.containsKey(sel)) {
            Spacer(Modifier.width(if (wide) 18.dp else 0.dp))
            if (wide) {
                Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ItemDetail(controller, state, sel) { selected = null }
                }
            }
        }
    }
    // 휴대폰: 상세 정보를 아래에서 띄운다
    val sel = selected
    if (!wide && sel != null && products.containsKey(sel)) {
        Box(Modifier.fillMaxSize().background(Sb.Window.copy(alpha = 0.7f)).clickable { selected = null }, contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clickable(enabled = false) {}.padding(12.dp)) { ItemDetail(controller, state, sel) { selected = null } }
        }
    }
}

@Composable
private fun Cell(text: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = Sb.Text) {
    Text(text, modifier, style = SbText.pixel(12, color), maxLines = 1)
}

@Composable
private fun StarButton(on: Boolean, onClick: () -> Unit) {
    Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, "관심", tint = if (on) Sb.Gold else Sb.TextFaint,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp).size(20.dp))
}

@Composable
private fun ItemDetail(controller: AppController, state: AppState, id: String, onClose: () -> Unit) {
    val q = state.bazaar?.products?.get(id)?.quickStatus ?: return
    val name = state.itemName(id)
    val tier = state.itemTier(id)
    var side by remember(id) { mutableStateOf(0) }
    var dir by remember(id) { mutableStateOf(0) }
    var target by remember(id) { mutableStateOf(formatPlain(q.buyPrice)) }

    McTooltip(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = SbText.pixel(15, McColors.rarity(tier), bold = true), modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, "닫기", tint = Sb.Gray, modifier = Modifier.clickable(onClick = onClose).size(18.dp))
        }
        Text(id, style = SbText.pixel(11, androidx.compose.ui.graphics.Color(0xFF555555)))
        Spacer(Modifier.height(10.dp))
        TooltipLine("즉시 구매가", formatCoins(q.buyPrice), Sb.Gold)
        TooltipLine("즉시 판매가", formatCoins(q.sellPrice), Sb.Gold)
        TooltipLine("판매 주문 / 구매 주문", "${formatNumber(q.sellOrders)} / ${formatNumber(q.buyOrders)}", Sb.Gray)
        TooltipLine("주간 구매량", formatNumber(q.buyMovingWeek), Sb.Aqua)
        TooltipLine("주간 판매량", formatNumber(q.sellMovingWeek), Sb.Aqua)
        state.items[id]?.npcSellPrice?.let { TooltipLine("NPC 판매가", formatCoins(it), Sb.Yellow) }
        Spacer(Modifier.height(8.dp))
        Text(McColors.rarityLabel(tier), style = SbText.pixel(12, McColors.rarity(tier), bold = true))
    }
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth(), caption = "Price Alert", title = "가격 알림 만들기", accent = Sb.Pink) {
        Segmented(listOf("즉시 구매가", "즉시 판매가"), side, { side = it })
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("이하로 내려가면", "이상으로 오르면"), dir, { dir = it })
        Spacer(Modifier.height(8.dp))
        SbTextField(target, { v -> target = v.filter { it.isDigit() || it == '.' } }, Modifier.fillMaxWidth(), "목표 가격", numeric = true)
        Spacer(Modifier.height(10.dp))
        SbButton("알림 추가", {
            val price = target.toDoubleOrNull() ?: return@SbButton
            controller.repo.addPriceAlert(PriceAlert(
                id = "${id}_${Clock.now()}", itemId = id,
                side = if (side == 0) PriceSide.INSTA_BUY else PriceSide.INSTA_SELL, above = dir == 1, target = price,
            ))
            controller.showToast("가격 알림을 추가했어요")
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.AddAlert)
        Spacer(Modifier.height(6.dp))
        Text("알림은 PC 와 휴대폰에 함께 동기화돼요.", style = SbText.body(11, Sb.TextFaint))
    }
}

@Composable
private fun TooltipLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, style = SbText.pixel(12, Sb.Gray), modifier = Modifier.weight(1f))
        Text(value, style = SbText.pixel(12, color))
    }
}

private fun formatPlain(v: Double): String = if (v >= 100) v.toLong().toString() else ((v * 10).toLong() / 10.0).toString()

@Composable
private fun AlertsList(controller: AppController, state: AppState) {
    val alerts = state.synced.priceAlerts
    if (alerts.isEmpty()) {
        EmptyState("가격 알림이 없어요", "다른 탭에서 아이템을 누르면 가격 알림을 만들 수 있어요.", icon = Icons.Rounded.AddAlert)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(alerts, key = { it.id }) { a ->
            val q = state.bazaar?.products?.get(a.itemId)?.quickStatus
            val current = if (a.side == PriceSide.INSTA_BUY) q?.buyPrice else q?.sellPrice
            TableRow {
                Column(Modifier.weight(1f)) {
                    RarityName(state.itemName(a.itemId), state.itemTier(a.itemId), size = 14, bold = true)
                    Text("${if (a.side == PriceSide.INSTA_BUY) "즉시 구매가" else "즉시 판매가"} ${formatCoins(a.target)} ${if (a.above) "이상" else "이하"}",
                        style = SbText.body(12, Sb.TextDim))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("현재 ${formatCoins(current)}", style = SbText.pixel(12))
                    val hit = current != null && (if (a.above) current >= a.target else current <= a.target)
                    if (hit) Pill("조건 충족", Sb.Green, filled = true)
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.Delete, "삭제", tint = Sb.TextFaint,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { controller.repo.removePriceAlert(a.id) }.padding(6.dp).size(18.dp))
            }
        }
    }
}
