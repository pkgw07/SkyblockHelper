package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sbhelper.core.AppState
import sbhelper.core.api.ApiException
import sbhelper.core.api.HypixelApi
import sbhelper.core.model.Auction
import sbhelper.core.util.Clock
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.core.util.stripColor
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.HeaderCell
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.McText
import sbhelper.ui.components.McTooltip
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.TableRow
import sbhelper.ui.components.XpBar
import sbhelper.ui.components.rememberNow
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 같은 이름의 BIN 매물 묶음 */
data class BinGroup(val name: String, val tier: String, val cheapest: Auction, val second: Double?, val count: Int)

/** 경매장 검색 상태 (화면을 나갔다 와도 유지) */
class AuctionSearchState(private val api: HypixelApi, private val scope: CoroutineScope) {
    var query by mutableStateOf("")
    var loading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var results by mutableStateOf<List<BinGroup>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<BinGroup?>(null)
    var searchedAt by mutableStateOf(0L)
    private var job: Job? = null

    fun search() {
        val q = query.trim()
        if (q.length < 2) { error = "두 글자 이상 입력해 주세요."; return }
        job?.cancel()
        job = scope.launch {
            loading = true; error = null; progress = 0f
            try {
                val all = api.allAuctions { done, total -> progress = done.toFloat() / total }
                results = all.asSequence()
                    .filter { it.bin && it.bids.isEmpty() && it.itemName.stripColor().contains(q, ignoreCase = true) }
                    .groupBy { it.itemName.stripColor() }
                    .map { (name, list) ->
                        val sorted = list.sortedBy { it.startingBid }
                        BinGroup(name, sorted.first().tier, sorted.first(), sorted.getOrNull(1)?.startingBid, list.size)
                    }
                    .sortedBy { it.cheapest.startingBid }
                    .take(200)
                    .toList()
                selected = results.firstOrNull()
                searchedAt = Clock.now()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun AuctionScreen(controller: AppController, state: AppState, wide: Boolean) {
    val s = controller.auctionSearch
    val platform = LocalPlatform.current
    val now = rememberNow(5_000)
    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 28.dp else 14.dp).padding(bottom = 20.dp, top = if (wide) 4.dp else 10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.selectedProfile?.gameMode?.canTrade == false) {
                Text("이 프로필은 경매장을 쓸 수 없어요. 시세 참고용으로만 보여 드려요.", style = SbText.body(13, Sb.Gray))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SbTextField(s.query, { s.query = it }, Modifier.weight(1f), "아이템 이름 (예: Hyperion, Juju)", Icons.Rounded.Search)
                Spacer(Modifier.width(10.dp))
                SbButton(if (s.loading) "검색 중…" else "최저 BIN 검색", { s.search() }, enabled = !s.loading)
            }
            if (s.loading) {
                XpBar(s.progress.toDouble(), Modifier.fillMaxWidth(), Sb.Gold, 6.dp)
                Text("경매장 전체 페이지를 읽는 중이에요… ${(s.progress * 100).toInt()}%", style = SbText.body(12, Sb.TextFaint))
            }
            s.error?.let { Text("⚠ $it", style = SbText.body(13, Sb.Negative)) }

            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
                .border(1.dp, Sb.Border, RoundedCornerShape(14.dp)).padding(8.dp)) {
                if (s.results.isEmpty()) {
                    EmptyState(if (s.searchedAt > 0) "검색 결과가 없어요" else "BIN 최저가 검색",
                        "공식 API 의 진행 중인 경매 전체에서 가장 싼 즉시 구매(BIN) 매물을 찾아요.", icon = Icons.Rounded.Search)
                } else Column {
                    TableRow {
                        HeaderCell("아이템", Modifier.weight(2.4f))
                        HeaderCell("최저가", Modifier.weight(1f))
                        if (wide) HeaderCell("2위 가격", Modifier.weight(1f))
                        HeaderCell("매물", Modifier.weight(0.6f))
                    }
                    LazyColumn {
                        items(s.results, key = { it.cheapest.uuid }) { g ->
                            TableRow(onClick = { s.selected = g }, selected = s.selected == g) {
                                Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                                    ItemGlyph(g.name, g.tier, 26.dp)
                                    Spacer(Modifier.width(10.dp))
                                    RarityName(g.name, g.tier, size = 13)
                                }
                                Text(formatCoins(g.cheapest.startingBid), Modifier.weight(1f), style = SbText.pixel(12, Sb.Gold))
                                if (wide) Text(g.second?.let { formatCoins(it) } ?: "-", Modifier.weight(1f), style = SbText.pixel(12, Sb.TextDim))
                                Text("${g.count}", Modifier.weight(0.6f), style = SbText.pixel(12, Sb.TextDim))
                            }
                        }
                    }
                }
            }
            MyAuctions(state, now)
        }
        val sel = s.selected
        if (wide && sel != null) {
            Spacer(Modifier.width(18.dp))
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                McTooltip(Modifier.fillMaxWidth()) {
                    Text(sel.name, style = SbText.pixel(15, McColors.rarity(sel.tier), bold = true))
                    Spacer(Modifier.height(6.dp))
                    sel.cheapest.itemLore.split('\n').forEach { line -> McText(line, size = 12) }
                }
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Text("최저가 ${formatCoins(sel.cheapest.startingBid)} 코인", style = SbText.body(16, Sb.Gold, FontWeight.Bold))
                    sel.second?.let {
                        val gap = it - sel.cheapest.startingBid
                        Text("2위와 차이 ${formatCoins(gap)} (${(gap / it * 100).toInt()}%)", style = SbText.body(13, Sb.TextDim))
                    }
                    Text("종료까지 ${formatDuration(sel.cheapest.end - now)}", style = SbText.body(13, Sb.TextDim))
                    Spacer(Modifier.height(12.dp))
                    SbButton("/viewauction 명령어 복사", {
                        platform.copyToClipboard("/viewauction ${sel.cheapest.uuid}")
                        controller.showToast("복사했어요. 게임 채팅창에 붙여 넣으세요.")
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentCopy, outlined = true)
                }
            }
        }
    }
}

@Composable
private fun MyAuctions(state: AppState, now: Long) {
    if (!state.accountLinked || state.myAuctions.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), caption = "My Auctions", title = "내 경매", accent = Sb.Green) {
        state.myAuctions.sortedBy { it.end }.take(8).forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityName(a.itemName.stripColor(), a.tier, Modifier.weight(1f), size = 13)
                val (label, color) = when {
                    a.bin && a.bids.isNotEmpty() -> "판매됨" to Sb.Green
                    a.end <= now && a.bids.isNotEmpty() -> "종료 · 낙찰" to Sb.Green
                    a.end <= now -> "만료" to Sb.Red
                    else -> "${formatDuration(a.end - now)} 남음" to Sb.TextDim
                }
                Text(formatCoins(if (a.highestBidAmount > 0) a.highestBidAmount else a.startingBid), style = SbText.pixel(12, Sb.Gold))
                Spacer(Modifier.width(8.dp))
                Pill(label, color)
            }
        }
    }
}
