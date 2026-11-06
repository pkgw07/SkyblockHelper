package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.CraftPlan
import sbhelper.core.calc.CraftPlanner
import sbhelper.core.calc.IronmanCalc
import sbhelper.core.calc.Method
import sbhelper.core.calc.PlanNode
import sbhelper.core.data.CraftTarget
import sbhelper.core.model.GameMode
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.core.util.formatNumber
import sbhelper.core.util.formatPercent
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.StatTile
import sbhelper.ui.components.SwitchRow
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

@Composable
fun IronmanScreen(controller: AppController, state: AppState, wide: Boolean) {
    val tab = controller.ironmanTab
    ScreenColumn(wide) {
        val p = state.selectedProfile
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 탭이 먼저 너비를 다 가져가면 오른쪽 표시가 세로로 길게 눌려서(휴대폰) 빈 공간이 생기므로 탭에 남은 너비만
            Segmented(listOf("진행 가이드", "재료 출처", "자루 NPC 가치"), tab.coerceIn(0, 2), { controller.ironmanTab = it },
                Modifier.weight(1f, fill = false))
            if (p?.gameMode == GameMode.IRONMAN) {
                Spacer(Modifier.width(8.dp))
                Pill(if (wide) "♲ Ironman · ${p.cuteName}" else "♲", Sb.Gray, filled = true)
            }
        }
        Text(
            "Ironman 은 거래를 할 수 없어서 직접 만들고 NPC 에게 팔아야 해요. 일반 프로필에서도 똑같이 쓸 수 있어요.",
            style = SbText.body(13, Sb.TextFaint),
        )
        CookieBanner(state)
        when (tab) {
            0 -> IronmanGuide(controller, state, wide)
            1 -> MaterialSources(controller, state, wide)
            else -> SackValues(state, wide)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CraftPlannerView(controller: AppController, state: AppState, wide: Boolean) {
    var query by remember { mutableStateOf(controller.plannerQuery) }
    val current = state.synced.craftCurrent ?: state.synced.craftTargets.firstOrNull()
    val target = current?.itemId
    var amountText by remember(target) { mutableStateOf((current?.amount ?: 1).toString()) }
    val useSacks = state.device.plannerUseSacks
    // 고른 목표는 바로 저장 → 다른 화면에 갔다 와도, 앱을 다시 켜도 그대로
    fun open(id: String?, amount: Int = 1) {
        controller.repo.updateSynced { s -> s.copy(craftCurrent = id?.let { CraftTarget(it, amount) }) }
    }
    val planner = remember(state.gameData) { CraftPlanner(state.gameData) }
    val sacks = state.selectedProfile?.sacks
    // 인벤토리·엔더 상자·백팩·금고에 든 재료도 (프로필이 바뀔 때만 다시 읽음)
    val invData = state.selectedProfile?.inventories
    val apiBoxes = remember(invData) { invData?.let { sbhelper.core.calc.CraftStock.byContainer(it) } }
    val inventory = remember(invData) { invData?.let { sbhelper.core.calc.CraftStock.inventory(it) } }
    val useInv = state.device.plannerUseInventory
    // 게임 모드가 연결돼 있으면 인벤토리·엔더 상자·백팩은 게임에서 바로 받은 값 (공식 API 는 1~3분 늦음)
    val live = sbhelper.ui.LocalPlatform.current.liveGame?.collectAsState()?.value
    // 게임에서 본 상자 (게임을 꺼도 공식 API 가 따라잡을 때까지 남아 있음)
    val liveBoxes = live?.containers?.takeIf { it.isNotEmpty() }
    // 상자마다: 게임에서 이번에 본 건 실시간, 아닌 건 공식 API (안 열어 본 엔더 상자·백팩도 빠지지 않게)
    val stock = remember(sacks, apiBoxes, liveBoxes, useSacks, useInv) {
        sbhelper.core.calc.CraftStock.merge(if (useSacks) sacks.orEmpty() else emptyMap(),
            if (useInv) sbhelper.core.calc.CraftStock.combine(apiBoxes.orEmpty(), liveBoxes) else emptyMap())
    }

    Panel(Modifier.fillMaxWidth(), caption = "Craft Planner", title = "무엇을 만들까요?", accent = Sb.Gold) {
        SbTextField(query, { query = it; controller.plannerQuery = it }, Modifier.fillMaxWidth(), "아이템 검색 (예: Refined Diamond, Enchanted Iron Block)", Icons.Rounded.Search)
        val q = query.trim()
        if (q.length >= 2) {
            val ids = (state.gameData.crafting.keys + state.gameData.forge.keys)
            val suggestions = ids.asSequence()
                .filter { state.itemName(it).contains(q, true) || it.contains(q, true) }
                .sortedBy { state.itemName(it).length }
                .take(8).toList()
            Spacer(Modifier.height(6.dp))
            suggestions.forEach { id ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open(id); query = ""; controller.plannerQuery = "" }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sbhelper.ui.components.ItemIcon(state, id, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    RarityName(state.itemName(id), state.itemTier(id), Modifier.weight(1f), size = 13)
                    if (state.gameData.forge.containsKey(id)) Pill("포지", Sb.Gold)
                }
            }
            if (suggestions.isEmpty()) Text("만들 수 있는 아이템 중에 찾지 못했어요.", style = SbText.body(12, Sb.TextFaint))
        }
        if (state.synced.craftTargets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("저장한 목표", style = SbText.caption())
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.synced.craftTargets.take(12).forEach { t ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (t.itemId == target) Sb.SurfaceHover else Sb.SurfaceHigh)
                            .clickable { open(t.itemId, t.amount) }
                            .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.itemName(t.itemId), style = SbText.body(12, Sb.Text))
                        Icon(Icons.Rounded.Close, "삭제", tint = Sb.TextFaint, modifier = Modifier.padding(start = 4.dp).size(14.dp)
                            .clickable {
                                // 지우면 바로 사라지고, 보던 목표였으면 다음 목표로 넘어간다
                                controller.repo.updateSynced { s ->
                                    val left = s.craftTargets.filterNot { it.itemId == t.itemId }
                                    s.copy(craftTargets = left,
                                        craftCurrent = if (s.craftCurrent?.itemId == t.itemId || (s.craftCurrent == null && target == t.itemId)) left.firstOrNull() else s.craftCurrent)
                                }
                            })
                    }
                }
            }
        }
    }

    val id = target ?: run {
        Panel(Modifier.fillMaxWidth()) {
            EmptyState("목표 아이템을 골라 주세요", "기본 재료까지 분해하고, 자루에 있는 재료를 빼서 부족한 양을 알려 드려요.", icon = Icons.Rounded.Inventory2)
        }
        return
    }
    val amount = amountText.toIntOrNull()?.coerceIn(1, 100_000) ?: 1
    // Recipe tree 에서 끈 재료 (목표마다 저장)
    val skip = state.synced.craftExcluded[id].orEmpty()
    val plan = remember(id, amount, stock, skip, state.gameData) {
        planner.plan(id, amount, stock, skip = skip)
    }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            sbhelper.ui.components.ItemIcon(state, id, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                RarityName(state.itemName(id), state.itemTier(id), size = 18, bold = true)
                Text(id, style = SbText.pixel(11, Sb.TextFaint))
            }
            Text("수량", style = SbText.body(13, Sb.TextDim))
            Spacer(Modifier.width(8.dp))
            SbTextField(amountText, { v ->
                amountText = v.filter { it.isDigit() }.take(6)
                amountText.toIntOrNull()?.let { n -> open(id, n.coerceIn(1, 100_000)) }
            }, Modifier.width(90.dp), "1", numeric = true)
            Spacer(Modifier.width(10.dp))
            SbButton("저장", {
                controller.repo.updateSynced { s ->
                    s.copy(craftTargets = (listOf(CraftTarget(id, amount)) + s.craftTargets.filterNot { it.itemId == id }).take(12))
                }
                controller.showToast("목표를 저장했어요 (기기 간 동기화)")
            }, icon = Icons.Rounded.BookmarkAdd, outlined = true)
        }
        Spacer(Modifier.height(6.dp))
        SwitchRow("자루(Sacks)에 있는 재료 사용", useSacks && sacks != null, { on -> controller.repo.updateDevice { it.copy(plannerUseSacks = on) } },
            if (sacks == null) "프로필의 인벤토리 API 가 꺼져 있거나 계정이 연결되지 않았어요." else "자루 ${sacks.size}종의 재료를 먼저 쓴다고 계산해요.",
            enabled = sacks != null)
        SwitchRow("인벤토리·엔더 상자·백팩·금고 재료 사용", useInv && inventory != null, { on -> controller.repo.updateDevice { it.copy(plannerUseInventory = on) } },
            if (inventory == null) "계정을 연결하면 인벤토리에 든 재료도 셀 수 있어요."
            else "인벤토리 등에 든 재료 ${inventory.size}종도 가진 것으로 쳐요. (게임 API 설정에서 인벤토리를 켜야 보여요)",
            enabled = inventory != null)
        OverlayControls(controller, state)
    }

    val progress = remember(id, amount, stock, skip, state.gameData, state.bazaar != null) {
        planner.progress(id, amount, stock, skip) { k ->
            state.bazaar?.products?.get(k)?.quickStatus?.buyPrice?.takeIf { it > 0 } ?: state.npcPrices[k] ?: state.lowestBin[k]
        }
    }
    // 가진 중간 재료도 기본 재료로 환산 (Enchanted Coal 2개 → Coal 320개 모은 것으로)
    val raw = remember(id, amount, stock, skip, state.gameData) {
        planner.rawProgress(id, amount, stock, skip)
    }
    // 포지 시간: 내 HotM Quick Forge 레벨 + 지금 시장 퍼크(Cole) 반영 (위키 The Forge, 감소는 더해서 적용)
    val qfLevel = state.selectedProfile?.quickForgeLevel ?: 0
    val forgeCut = (state.gameData.rules.quickForge.percent(qfLevel) +
        sbhelper.core.calc.ForgeCalc.mayorPercent(state.gameData, state.election, sbhelper.core.util.Clock.now(), 0L)).coerceIn(0.0, 95.0)
    PlanSummary(plan, progress, wide, qfLevel, forgeCut, state.selectedProfile != null)
    Responsive(
        wide,
        cell(1f) { m -> RawMaterials(controller, state, raw, m) },
        cell(1f) { m ->
            Panel(m, caption = "Recipe Tree", title = "제작 순서", accent = Sb.Aqua,
                trailing = {
                    if (skip.isNotEmpty()) Text("끈 재료 ${skip.size}개 다시 켜기", style = SbText.body(12, Sb.Aqua),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                            controller.repo.updateSynced { s -> s.copy(craftExcluded = s.craftExcluded - id) }
                        }.padding(4.dp))
                }) {
                Text("☑ 를 눌러 끄면 그 재료(와 그 아래 재료)는 계산에서 빠져요. 이미 가진 장비처럼 신경 쓰지 않을 재료를 끄세요.",
                    style = SbText.body(12, Sb.TextFaint))
                Spacer(Modifier.height(6.dp))
                PlanTree(state, plan.root, 0) { item ->
                    controller.repo.updateSynced { s ->
                        val cur = s.craftExcluded[id].orEmpty()
                        val next = if (item in cur) cur - item else cur + item
                        s.copy(craftExcluded = if (next.isEmpty()) s.craftExcluded - id else s.craftExcluded + (id to next))
                    }
                }
            }
        },
    )
}

@Composable
private fun PlanSummary(plan: CraftPlan, progress: Double, wide: Boolean, qfLevel: Int, forgeCut: Double, hasProfile: Boolean) {
    val missing = plan.raw.count { it.missing > 0 }
    val pct = if (missing == 0) 1.0 else progress
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            Column(m) {
                StatTile("전체 진행도", formatPercent(pct, 0), Modifier.fillMaxWidth(), color = if (pct >= 1.0) Sb.Green else Sb.Gold,
                    sub = "재료 가치 기준 · 가진 중간 재료 포함")
                Spacer(Modifier.height(6.dp))
                XpBar(pct, Modifier.fillMaxWidth(), if (pct >= 1.0) Sb.Green else Sb.Gold, 6.dp)
            }
        },
        { m -> StatTile("기본 재료", "${plan.raw.size}종", m, color = Sb.Aqua) },
        { m -> StatTile("부족한 재료", "${missing}종", m, color = if (missing == 0) Sb.Green else Sb.Red,
            sub = if (missing == 0) "바로 만들 수 있어요!" else null) },
        { m ->
            val sec = plan.totalForgeSeconds * (1 - forgeCut / 100)
            StatTile("포지 시간 합계", if (plan.totalForgeSeconds > 0) formatDuration((sec * 1000).toLong()) else "없음", m,
                color = Sb.Gold, sub = if (plan.forgeSteps == 0) null else "포지 ${plan.forgeSteps}회 · " + when {
                    !hasProfile -> "계정 연결 시 Quick Forge 반영"
                    forgeCut > 0 -> "Quick Forge ${qfLevel}레벨 등 -${fixed(forgeCut, 1)}% 반영 (원래 ${formatDuration(plan.totalForgeSeconds * 1000)})"
                    else -> "HotM 에 Quick Forge 없음"
                })
        },
    )
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
}

@Composable
private fun RawMaterials(controller: AppController, state: AppState, raw: List<sbhelper.core.calc.RawNeed>, modifier: Modifier) {
    val enchanted = state.device.materialsEnchanted
    val comp = remember(state.gameData) { sbhelper.core.calc.Compressor(state.gameData) }
    Panel(modifier, caption = "Materials", title = "필요한 기본 재료", accent = Sb.Green) {
        Text("가진 중간 재료(예: Enchanted Coal)도 기본 재료로 바꿔 세요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        MaterialFormToggle(enchanted) { on -> controller.repo.updateDevice { it.copy(materialsEnchanted = on) } }
        Spacer(Modifier.height(4.dp))
        raw.forEach { r ->
            val shown = shownMaterial(comp, r.itemId, r.needed, enchanted)
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    sbhelper.ui.components.ItemIcon(state, shown.id, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        RarityName(state.itemName(shown.id), state.itemTier(shown.id), Modifier, size = 13)
                        if (shown.parts.size > 1 || shown.id != r.itemId)
                            Text(partsText(state, shown.parts), style = SbText.body(11, Sb.TextFaint))
                    }
                    Text(if (shown.ratio == 1.0) "${formatNumber(r.have.toLong())} / ${formatNumber(kotlin.math.ceil(r.needed).toLong())}"
                        else "${amountText(r.have / shown.ratio)} / ${amountText(r.needed / shown.ratio)}",
                        style = SbText.pixel(12, if (r.missing <= 0) Sb.Green else Sb.Text))
                    // 이 재료를 몇 % 모았는지 (예: 1,000 / 10,000 → 10%)
                    val pct = if (r.needed > 0) (r.have / r.needed).coerceIn(0.0, 1.0) else 1.0
                    Text(formatPercent(pct, if (pct > 0 && pct < 0.01) 1 else 0), style = SbText.pixel(12, if (r.missing <= 0) Sb.Green else Sb.Gold, bold = true),
                        modifier = Modifier.padding(start = 8.dp).widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Spacer(Modifier.height(4.dp))
                XpBar(if (r.needed > 0) r.have / r.needed else 1.0, Modifier.fillMaxWidth(), if (r.missing <= 0) Sb.Green else Sb.Aqua, 5.dp)
            }
        }
    }
}

@Composable
private fun PlanTree(state: AppState, node: PlanNode, depth: Int, onToggle: (String) -> Unit) {
    var open by remember(node.itemId, depth) { mutableStateOf(depth < 2) }
    val off = node.method == Method.SKIP
    val (label, color) = when (node.method) {
        Method.SKIP -> "계산 안 함" to Sb.TextFaint
        Method.CRAFT -> "제작 ×${node.crafts}" to Sb.Aqua
        Method.FORGE -> "포지 ×${node.crafts} · ${formatDuration(node.forgeSeconds * 1000)}" to Sb.Gold
        Method.STOCK -> "보유" to Sb.Green
        Method.BASE -> "재료" to Sb.TextDim
    }
    Row(
        Modifier.fillMaxWidth().padding(start = (depth * 16).dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = node.children.isNotEmpty()) { open = !open }
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) Box(Modifier.width(2.dp).height(18.dp).background(Sb.Border))
        Spacer(Modifier.width(6.dp))
        if (node.children.isNotEmpty()) {
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Sb.TextFaint, modifier = Modifier.size(16.dp))
        } else Spacer(Modifier.width(16.dp))
        Spacer(Modifier.width(4.dp))
        // 켜기/끄기 (최종 목표는 끌 수 없음)
        if (depth > 0) {
            Text(if (off) "☐" else "☑", style = SbText.body(16, if (off) Sb.TextFaint else Sb.Green),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onToggle(node.itemId) }.padding(horizontal = 4.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text("${fixed(node.needed, if (node.needed % 1.0 == 0.0) 0 else 1)}×", style = SbText.pixel(12, Sb.TextDim))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f).let { if (off) it.alpha(0.45f) else it }) {
            RarityName(state.itemName(node.itemId), state.itemTier(node.itemId), size = 13)
        }
        if (node.fromStock > 0 && node.method != Method.STOCK) {
            Text("보유 ${formatNumber(node.fromStock.toLong())}  ", style = SbText.body(11, Sb.Green))
        }
        Pill(label, color)
    }
    if (open) node.children.forEach { PlanTree(state, it, depth + 1, onToggle) }
}

@Composable
private fun SackValues(state: AppState, wide: Boolean) {
    val sacks = state.selectedProfile?.sacks
    if (sacks == null) {
        Panel(Modifier.fillMaxWidth()) {
            EmptyState("자루 정보를 볼 수 없어요", "계정을 연결하고, 게임 API 설정에서 Inventory 를 켜 주세요.", icon = Icons.Rounded.Inventory2)
        }
        return
    }
    val values = IronmanCalc.sackValues(sacks, state.npcPrices)
    val total = values.sumOf { it.total }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile("NPC 판매 시 총 가치", formatCoins(total), Modifier.weight(1f), color = Sb.Gold)
        StatTile("자루 아이템 종류", "${sacks.size}종", Modifier.weight(1f), color = Sb.Aqua)
    }
    Panel(Modifier.fillMaxWidth(), caption = "Sacks", title = "NPC 에게 팔면 많이 남는 순", accent = Sb.Gold) {
        values.take(if (wide) 60 else 30).forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                sbhelper.ui.components.ItemIcon(state, v.itemId, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    RarityName(state.itemName(v.itemId), state.itemTier(v.itemId), size = 13)
                    Text("${formatNumber(v.count)}개 × ${formatCoins(v.npcPrice)}", style = SbText.body(11, Sb.TextFaint))
                }
                Text(formatCoins(v.total), style = SbText.pixel(13, Sb.Gold, bold = true))
            }
        }
    }
    Text("💡 NPC 판매가는 Hypixel 공식 아이템 정보(npc_sell_price) 기준이에요.", style = SbText.body(12, Sb.TextFaint))
}



/** 위키 Ironman 페이지: 아이언맨도 바자에서 Booster Cookie 는 즉시 구매할 수 있다 */
@Composable
private fun CookieBanner(state: AppState) {
    val q = state.bazaar?.products?.get("BOOSTER_COOKIE")?.quickStatus ?: return
    if (state.selectedProfile?.gameMode != GameMode.IRONMAN) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sb.Gold.copy(alpha = 0.08f))
            .border(1.dp, Sb.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🍪", style = SbText.body(18))
        Spacer(Modifier.width(10.dp))
        Text("아이언맨도 바자에서 Booster Cookie 는 즉시 구매할 수 있어요", style = SbText.body(13, Sb.Text), modifier = Modifier.weight(1f))
        Text("현재 ${formatCoins(q.buyPrice)}", style = SbText.pixel(13, Sb.Gold, bold = true))
    }
}

/** 위키 Tutorial:Ironman Guide 단계별 체크리스트 (체크는 기기 간 동기화) */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IronmanGuide(controller: AppController, state: AppState, wide: Boolean) {
    val stages = state.gameData.ironmanGuide
    var selected by remember { mutableStateOf<String?>(null) }
    val checked = state.synced.ironmanChecked
    if (stages.isEmpty()) {
        Panel(Modifier.fillMaxWidth()) { EmptyState("가이드 데이터가 없어요", "위키 데이터를 받는 중이에요.") }
        return
    }
    val total = stages.sumOf { it.items.size }
    val done = stages.sumOf { st -> st.items.count { "${st.title}|$it" in checked } }
    Panel(Modifier.fillMaxWidth(), caption = "Ironman Guide · Wiki", title = "아이언맨 진행 체크리스트", accent = Sb.Gray,
        trailing = { Pill("$done / $total", Sb.Green) }) {
        XpBar(if (total == 0) 0.0 else done.toDouble() / total, Modifier.fillMaxWidth(), Sb.Green, 7.dp)
        Spacer(Modifier.height(6.dp))
        Text("Ironman Guide 순서예요. ☐ 를 눌러 끝낸 항목을 표시하고, 이름을 누르면 제작법·드롭을 볼 수 있어요.",
            style = SbText.body(12, Sb.TextFaint))
    }
    stages.forEach { st ->
        Panel(Modifier.fillMaxWidth(), caption = st.group, title = st.title, accent = Sb.Gray) {
            if (st.desc.isNotBlank()) {
                Text(st.desc, style = SbText.body(12, Sb.TextDim), maxLines = 3)
                Spacer(Modifier.height(8.dp))
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                st.items.forEach { id ->
                    val key = "${st.title}|$id"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (key in checked) "☑" else "☐", style = SbText.body(16, if (key in checked) Sb.Green else Sb.TextFaint),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                controller.repo.updateSynced { s ->
                                    s.copy(ironmanChecked = if (key in s.ironmanChecked) s.ironmanChecked - key else s.ironmanChecked + key)
                                }
                            }.padding(4.dp))
                        // 누르면 아이템 화면으로 (뒤로 가기로 돌아옴) — 목록 위에 정보를 끼워 넣어 스크롤이 튀지 않게
                        ItemChip(state, id, false) { controller.openItem(id) }
                    }
                }
            }
        }
    }
}

/** 재료 출처: 공식 위키의 몹 드롭·미니언·제작·포지 정보 검색 */
@Composable
private fun MaterialSources(controller: AppController, state: AppState, wide: Boolean) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    val data = state.gameData
    Panel(Modifier.fillMaxWidth(), caption = "Sources · Wiki", title = "재료를 어디서 얻나요?", accent = Sb.Red) {
        SbTextField(query, { query = it }, Modifier.fillMaxWidth(), "재료 이름 (예: Enchanted Ender Pearl, Mithril)", Icons.Rounded.Search)
        val q = query.trim()
        if (q.length >= 2) {
            val ids = (data.sources.drops.keys + data.sources.minions.keys + data.crafting.keys + data.forge.keys).toSet()
            val hits = ids.asSequence().filter { state.itemName(it).contains(q, true) }.sortedBy { state.itemName(it).length }.take(10).toList()
            Spacer(Modifier.height(6.dp))
            hits.forEach { id ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { selected = id; query = "" }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    sbhelper.ui.components.ItemIcon(state, id, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    RarityName(state.itemName(id), state.itemTier(id), Modifier.weight(1f), size = 13)
                    if (id in data.sources.drops) Pill("드롭", Sb.Red)
                    if (id in data.sources.minions) Pill("미니언", Sb.Yellow)
                }
            }
        }
    }
    selected?.let { ItemInfo(controller, state, it, onClose = { selected = null }) }
}

/** 게임 오버레이 (PC): 켜기·위치·진하기·미리 보기 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OverlayControls(controller: AppController, state: AppState) {
    val platform = sbhelper.ui.LocalPlatform.current
    val overlay = platform.gameOverlay ?: return
    val d = state.device
    // 게임 모드 (Minecraft 26.2): 연결되면 게임 화면 안에 HUD 로 그리고, 인벤토리를 실시간으로 받는다
    val live = platform.liveGame?.collectAsState()?.value
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (live?.connected == true) "● 게임 모드 연결됨" + (if (live.inSkyblock) " · 스카이블록" + (if (live.area.isNotBlank()) " ${live.area}" else "") else "") +
                " · 인벤토리 실시간 · HUD 는 게임 안에서 (O 키, /sbh hud)"
            else "게임 모드 (Minecraft 26.2 · Fabric) 를 쓰면 전체 화면에서도 게임 안에 바로 보이고 인벤토리가 실시간으로 반영돼요",
            style = SbText.body(12, if (live?.connected == true) Sb.Green else Sb.TextFaint), modifier = Modifier.weight(1f),
        )
        if (live?.connected != true) platform.modManager?.let { mm -> SbButton("모드 설치", { mm.install() }, outlined = true) }
    }
    platform.modManager?.let { mm ->
        val st by mm.status.collectAsState()
        Text("모드: $st", style = SbText.body(12, Sb.TextFaint), modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(6.dp))
    SwitchRow("게임 오버레이 (PC)", d.overlayEnabled, { on -> controller.repo.updateDevice { it.copy(overlayEnabled = on) } },
        "마인크래프트 화면 구석에 이 목표의 진행도를 보여 줘요. 게임 중 Ctrl+Shift+O 로 켜고 끄기.")
    if (!d.overlayEnabled) return
    val status by overlay.status.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("● $status", style = SbText.body(12, Sb.TextDim), modifier = Modifier.weight(1f))
        SbButton("미리 보기", { overlay.preview() }, outlined = true)
    }
    Spacer(Modifier.height(8.dp))
    Text("위치", style = SbText.caption(Sb.TextDim))
    Spacer(Modifier.height(6.dp))
    // 4개 구석 + 직접 지정 (끌어서 옮긴 곳을 저장)
    Segmented(listOf("왼쪽 위", "오른쪽 위", "왼쪽 아래", "오른쪽 아래", "직접 지정"), d.overlayCorner.coerceIn(0, 4), { i ->
        if (i == 4 && d.overlayX < 0f) overlay.place()
        else controller.repo.updateDevice { it.copy(overlayCorner = i) }
    })
    if (d.overlayCorner == 4) {
        Spacer(Modifier.height(6.dp))
        SbButton("위치 다시 정하기", { overlay.place() }, outlined = true)
        Text("오버레이 위의 노란 막대를 끌어서 옮긴 뒤 '저장' 을 누르세요.", style = SbText.body(12, Sb.TextFaint), modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(10.dp))
    Text("투명도", style = SbText.caption(Sb.TextDim))
    Spacer(Modifier.height(6.dp))
    // 저장 값은 배경 불투명도 (투명도 = 100 - 불투명도)
    val trans = listOf(0, 20, 40, 60, 80)
    val cur = trans.minByOrNull { kotlin.math.abs((100 - d.overlayOpacity) - it) } ?: 20
    Segmented(trans.map { "$it%" }, trans.indexOf(cur), { i ->
        controller.repo.updateDevice { it.copy(overlayOpacity = 100 - trans[i]) }
    })
    Spacer(Modifier.height(10.dp))
    Text("글씨 크기", style = SbText.caption(Sb.TextDim))
    Spacer(Modifier.height(6.dp))
    val scales = listOf(80, 100, 120, 140, 170)
    Segmented(scales.map { "$it%" }, scales.indexOf(d.overlayTextScale).let { if (it < 0) 1 else it }, { i ->
        controller.repo.updateDevice { it.copy(overlayTextScale = scales[i]) }
    })
    Spacer(Modifier.height(10.dp))
    Text("상자 크기", style = SbText.caption(Sb.TextDim))
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val widths = listOf(220 to "좁게", 270 to "보통", 340 to "넓게", 420 to "아주 넓게")
        Segmented(widths.map { it.second }, widths.indexOfFirst { it.first == d.overlayWidth }.let { if (it < 0) 1 else it }, { i ->
            controller.repo.updateDevice { it.copy(overlayWidth = widths[i].first) }
        })
        val rows = listOf(3, 5, 8, 12)
        Segmented(rows.map { "재료 ${it}줄" }, rows.indexOf(d.overlayRows).let { if (it < 0) 1 else it }, { i ->
            controller.repo.updateDevice { it.copy(overlayRows = rows[i]) }
        })
    }
    Spacer(Modifier.height(10.dp))
    // 전체 화면: F11(독점 전체 화면)은 다른 창이 위에 못 뜰 수 있어서, 같은 모양의 '테두리 없는 전체 화면' 을 쓴다
    SwitchRow("게임을 켜면 자동으로 전체 화면", d.overlayAutoBorderless, { on -> controller.repo.updateDevice { it.copy(overlayAutoBorderless = on) } },
        "F11 대신 이걸 쓰면 전체 화면에서도 오버레이가 보여요. 게임 중 Ctrl+Shift+F 로 직접 켜고 끌 수도 있어요.")
    SbButton("지금 게임을 전체 화면으로 / 되돌리기", { overlay.toggleBorderless() }, outlined = true)
}

