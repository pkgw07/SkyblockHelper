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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.ui.AppController
import sbhelper.core.calc.MinionProfit
import sbhelper.core.calc.MinionSetup
import sbhelper.core.calc.SellTo
import sbhelper.core.data.GameData
import sbhelper.core.model.GameMode
import sbhelper.core.model.ProfileSummary
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatNumber
import sbhelper.core.util.formatPercent
import sbhelper.ui.components.ItemIcon
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.StatTile
import sbhelper.ui.components.SwitchRow
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

/**
 * 미니언 수익 계산 (위키 Module:Minion/Data · Minion Fuel 기준):
 * 단계별 하루 수익, 연료·업그레이드 반영, 다음 단계 재료를 이 미니언으로 모으는 데 며칠.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinionCalcView(controller: AppController, state: AppState, p: ProfileSummary?, wide: Boolean) {
    val data = state.gameData
    if (data.minions.isEmpty()) {
        Panel(Modifier.fillMaxWidth()) { Text("미니언 데이터를 불러오는 중이에요.", style = SbText.body(13, Sb.TextFaint)) }
        return
    }
    val crafted = p?.craftedMinions.orEmpty()
    // 처음엔 내가 가장 높이 올린 미니언
    val defaultName = remember(crafted, data) {
        data.minions.maxByOrNull { MinionProfit.craftedTier(it, crafted) }?.takeIf { MinionProfit.craftedTier(it, crafted) > 0 }?.name ?: "Cobblestone"
    }
    var name by rememberSaveable { mutableStateOf(defaultName) }
    val m = data.minions.firstOrNull { it.name == name } ?: data.minions.first()
    val myTier = MinionProfit.craftedTier(m, crafted)
    var tier by rememberSaveable(m.name) { mutableIntStateOf(myTier.coerceIn(1, m.tiers.size)) }
    var query by remember { mutableStateOf("") }
    var fuelId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanders by rememberSaveable { mutableIntStateOf(0) }
    var flycatchers by rememberSaveable { mutableIntStateOf(0) }
    var diamond by rememberSaveable { mutableStateOf(false) }
    var infusion by rememberSaveable { mutableStateOf(false) }
    var crystal by rememberSaveable { mutableStateOf(false) }
    var beacon by rememberSaveable { mutableIntStateOf(0) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    val ironman = p?.gameMode == GameMode.IRONMAN
    var sell by rememberSaveable { mutableIntStateOf(if (ironman) 2 else 0) }

    val setup = MinionSetup(data.minionFuels.firstOrNull { it.id == fuelId }, expanders, flycatchers, diamond, infusion, crystal, beacon)
    val sellTo = listOf(SellTo.BEST, SellTo.BAZAAR, SellTo.NPC)[sell]
    val bzSell: (String) -> Double? = { state.bazaar?.products?.get(it)?.quickStatus?.sellPrice }
    val bzBuy: (String) -> Double? = { state.bazaar?.products?.get(it)?.quickStatus?.buyPrice }
    val npcSell: (String) -> Double? = { state.npcPrices[it] }
    val results = remember(m, setup, sellTo, state.bazaar, state.npcPrices) {
        (1..m.tiers.size).mapNotNull { MinionProfit.tier(m, it, setup, sellTo, bzSell, npcSell, bzBuy) }
    }
    val cur = results.getOrNull(tier - 1) ?: return

    // ---------------- 미니언·단계 고르기
    Panel(Modifier.fillMaxWidth(), caption = "Minion Calculator · Wiki", title = "미니언 수익 계산", accent = Sb.Yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            m.icon?.let { ItemIcon(state, it, 40.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text("${m.name} Minion ${ROMAN.getOrElse(tier - 1) { "$tier" }}", style = SbText.body(16, Sb.Text, FontWeight.SemiBold))
                Text(m.type + (if (myTier > 0) " · 내가 만든 단계 ${ROMAN.getOrElse(myTier - 1) { "$myTier" }}" else ""),
                    style = SbText.body(12, Sb.TextDim))
            }
        }
        Spacer(Modifier.height(10.dp))
        SbTextField(query, { query = it }, Modifier.fillMaxWidth(), "다른 미니언 찾기 (예: Snow, Clay)", Icons.Rounded.Search)
        val q = query.trim()
        if (q.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.minions.filter { it.name.contains(q, true) }.take(16).forEach { o ->
                    Chip(o.name, false) { name = o.name; query = "" }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Segmented(m.tiers.indices.map { ROMAN.getOrElse(it) { "${it + 1}" } }, tier - 1, { tier = it + 1 })
    }

    // ---------------- 연료·업그레이드
    Panel(Modifier.fillMaxWidth(), caption = "Fuel · Upgrades", title = "연료와 업그레이드", accent = Sb.Gold) {
        Label("연료")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("없음", fuelId == null) { fuelId = null }
            data.minionFuels.forEach { f ->
                val effect = if (f.mult > 1) "x${fixed(f.mult, 0)}" else "+${fixed(f.speed * 100, 0)}%"
                Chip("${f.name} $effect" + (f.hours?.let { " · ${fixed(it, if (it % 1.0 == 0.0) 0 else 1)}h" } ?: " · 영구"), fuelId == f.id) { fuelId = f.id }
            }
        }
        Spacer(Modifier.height(10.dp))
        Label("업그레이드 칸 (2칸)  ·  ${setup.upgradeSlotsUsed}/2 사용")
        val free = 2 - setup.upgradeSlotsUsed
        Stepper("Minion Expander (+5%)", expanders, 0, expanders + free) { expanders = it }
        Stepper("Flycatcher (+20%)", flycatchers, 0, flycatchers + free) { flycatchers = it }
        SwitchRow("Diamond Spreading (10개마다 Diamond 1개)", diamond, { diamond = it && (diamond || free > 0) },
            enabled = diamond || free > 0)
        Spacer(Modifier.height(6.dp))
        Label("그 밖의 보너스")
        SwitchRow("Mithril Infusion (+10%)", infusion, { infusion = it })
        if (m.crystal) SwitchRow("Minion Crystal (+10%)", crystal, { crystal = it })
        Stepper("Beacon 단계 (단계당 +2%)", beacon, 0, 5) { beacon = it }
        Spacer(Modifier.height(10.dp))
        Stepper("이 미니언 개수", count, 1, 31) { count = it }
        Spacer(Modifier.height(10.dp))
        Label("팔 곳")
        Segmented(listOf("더 비싼 쪽", "바자", "NPC"), sell, { sell = it })
    }

    // ---------------- 지금 단계 결과
    val c = count.coerceAtLeast(1)
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { mod -> StatTile("하루 수익" + if (c > 1) " (×$c)" else "", formatCoins(cur.netPerDay * c), mod, color = Sb.Gold,
            sub = if (cur.fuelPerDay > 0) "연료비 ${formatCoins(cur.fuelPerDay * c)} 뺌" else null) },
        { mod -> StatTile("하루 아이템" + if (c > 1) " (×$c)" else "", "${formatNumber((cur.outputs.sumOf { it.perDay } * c).toLong())}개", mod,
            color = Sb.Green, sub = cur.outputs.joinToString(" · ") { "${state.itemName(it.id)} ${formatNumber((it.perDay * c).toLong())}" }) },
        { mod -> StatTile("행동 간격", "${fixed(cur.secondsPerAction, 1)}초", mod, color = Sb.Aqua,
            sub = "속도 +${fixed(setup.speedBonus(m) * 100, 0)}%") },
        { mod -> StatTile("가득 차는 시간", cur.hoursToFill?.let { hoursText(it) } ?: "-", mod, color = Sb.Green,
            sub = "저장 ${formatNumber(m.tiers[tier - 1].storage.toLong())}개 · 압축 업그레이드 없을 때") },
    )
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }

    Responsive(
        wide,
        cell(1f) { mod ->
            Panel(mod, caption = "Output", title = "하루에 나오는 것", accent = Sb.Green) {
                cur.outputs.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(state, o.id, 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(state.itemName(o.id), style = SbText.body(13), modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${formatNumber((o.perDay * c).toLong())}개", style = SbText.pixel(12, Sb.Text))
                        Spacer(Modifier.width(10.dp))
                        Text(o.coinsPerDay?.let { formatCoins(it * c) + " (${o.where})" } ?: "시세 없음",
                            style = SbText.pixel(12, if (o.coinsPerDay != null) Sb.Gold else Sb.TextFaint),
                            modifier = Modifier.widthIn(min = 96.dp), textAlign = TextAlign.End)
                    }
                }
            }
        },
        cell(1f) { mod -> NextTierPanel(controller, state, p, m, tier, cur, c, mod) },
    )

    // ---------------- XI · XII 까지 업그레이드
    UpgradePanel(controller, state, p, m, tier, setup, c, wide)

    // ---------------- 단계별 비교
    Panel(Modifier.fillMaxWidth(), caption = "All tiers", title = "단계별 하루 아이템 · 수익" + if (c > 1) " (×$c)" else "", accent = Sb.Aqua) {
        val best = results.maxOf { it.netPerDay }.coerceAtLeast(1.0)
        results.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (r.tier == tier) Sb.SurfaceHover else Color.Transparent)
                    .clickable { tier = r.tier }.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ROMAN.getOrElse(r.tier - 1) { "${r.tier}" }, style = SbText.pixel(12, if (r.tier <= myTier) Sb.Yellow else Sb.TextDim),
                    modifier = Modifier.width(40.dp))
                Text("${fixed(r.secondsPerAction, 1)}초", style = SbText.body(12, Sb.TextDim), modifier = Modifier.width(64.dp))
                XpBar(r.netPerDay / best, Modifier.weight(1f), if (r.tier == tier) Sb.Gold else Sb.Aqua, 6.dp)
                Spacer(Modifier.width(10.dp))
                Text("${formatNumber((r.outputs.sumOf { it.perDay } * c).toLong())}개", style = SbText.pixel(12, Sb.Green),
                    modifier = Modifier.widthIn(min = 70.dp), textAlign = TextAlign.End)
                Text(formatCoins(r.netPerDay * c), style = SbText.pixel(12, Sb.Gold), modifier = Modifier.widthIn(min = 70.dp), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("위키 Module:Minion 과 같은 계산: 수확 1번 = 행동 ${m.actions}번, 속도 보너스는 더해서 적용. 노란 단계 = 내가 만든 단계.",
            style = SbText.body(11, Sb.TextFaint))
    }
}

/** 다음 단계 재료: 필요 / 가진 것(자루) / 이 미니언으로 며칠 */
@Composable
private fun NextTierPanel(
    controller: AppController, state: AppState, p: ProfileSummary?, m: GameData.Minion, tier: Int, cur: sbhelper.core.calc.MinionTierResult, count: Int, mod: Modifier,
) {
    val stock = remember(p?.sacks, p?.inventories) {
        sbhelper.core.calc.CraftStock.merge(p?.sacks.orEmpty(), p?.inventories?.let { sbhelper.core.calc.CraftStock.inventory(it) }.orEmpty())
    }
    val plan = remember(m, tier, cur, count, stock, state.gameData) {
        MinionProfit.nextTier(state.gameData, m, tier, cur, count, stock)
    }
    Panel(mod, caption = "Next tier", title = if (plan == null) "최고 단계예요" else "${ROMAN.getOrElse(tier) { "${tier + 1}" }} 단계로 올리려면", accent = Sb.Pink) {
        if (plan == null) {
            Text("더 올릴 단계가 없어요.", style = SbText.body(13, Sb.TextFaint)); return@Panel
        }
        plan.days?.let { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 미니언${if (count > 1) " ${count}개" else ""}로 모으면", style = SbText.body(13, Sb.TextDim))
                Spacer(Modifier.width(8.dp))
                Pill(if (d <= 0) "이미 다 모았어요" else "약 " + daysText(d), Sb.Pink, filled = true)
            }
            Spacer(Modifier.height(8.dp))
        }
        val enchanted = state.device.materialsEnchanted
        val comp = remember(state.gameData) { sbhelper.core.calc.Compressor(state.gameData) }
        plan.needs.forEach { n ->
            val shown = shownMaterial(comp, n.id, n.need, enchanted)
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(state, shown.id, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.itemName(shown.id), style = SbText.body(13), modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (shown.ratio == 1.0) "${formatNumber(n.have.toLong())} / ${formatNumber(n.need.toLong())}"
                        else "${amountText(n.have / shown.ratio)} / ${amountText(n.need / shown.ratio)}",
                        style = SbText.pixel(12, if (n.missing <= 0) Sb.Green else Sb.Text))
                    Text(formatPercent(if (n.need > 0) (n.have / n.need).coerceIn(0.0, 1.0) else 1.0, 0),
                        style = SbText.pixel(12, if (n.missing <= 0) Sb.Green else Sb.Gold, bold = true),
                        modifier = Modifier.padding(start = 8.dp).widthIn(min = 40.dp), textAlign = TextAlign.End)
                }
                Text(
                    when {
                        n.missing <= 0 -> "다 모았어요"
                        n.days != null -> "이 미니언으로 ${daysText(n.days!!)}"
                        else -> "이 미니언이 만들지 않아요 — 따로 구해야 해요"
                    },
                    style = SbText.body(11, if (n.days == null && n.missing > 0) Sb.Red else Sb.TextFaint),
                    modifier = Modifier.padding(start = 30.dp),
                )
                XpBar(if (n.need > 0) n.have / n.need else 1.0, Modifier.fillMaxWidth().padding(top = 3.dp), if (n.missing <= 0) Sb.Green else Sb.Pink, 4.dp)
            }
        }
        if (plan.coins > 0) Text("+ ${formatCoins(plan.coins.toDouble())} 코인" + (m.tiers.getOrNull(tier)?.npc?.let { " ($it 와 교환)" } ?: ""),
            style = SbText.body(12, Sb.Gold), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(6.dp))
        Text("가진 양은 자루·인벤토리·엔더 상자·백팩·금고 기준, 제작 재료는 기본 재료로 나눠서 셌어요.", style = SbText.body(11, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        MaterialFormToggle(enchanted) { on -> controller.repo.updateDevice { it.copy(materialsEnchanted = on) } }
    }
}

/**
 * 지금 단계 → XI · XII 단계까지: 필요한 재료 전체, 코인,
 * 지금 단계 그대로 모으면 며칠 / 단계마다 모이는 대로 바로 올리면 며칠
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpgradePanel(
    controller: AppController, state: AppState, p: ProfileSummary?, m: GameData.Minion, tier: Int, setup: MinionSetup, count: Int, wide: Boolean,
) {
    val max = m.tiers.size
    val targets = listOf(11, 12).filter { it in (tier + 1)..max }.ifEmpty { if (tier < max) listOf(max) else emptyList() }
    var pick by rememberSaveable(m.name) { mutableIntStateOf(0) }
    val target = targets.getOrNull(pick.coerceIn(0, (targets.size - 1).coerceAtLeast(0)))
    val title = if (target == null) "업그레이드 계산" else "${ROMAN.getOrElse(tier - 1) { "$tier" }} → ${ROMAN.getOrElse(target - 1) { "$target" }} 업그레이드"
    Panel(Modifier.fillMaxWidth(), caption = "Upgrade path", title = title, accent = Sb.Purple) {
        if (target == null) {
            Text("이미 최고 단계예요.", style = SbText.body(13, Sb.TextFaint)); return@Panel
        }
        val stock = remember(p?.sacks, p?.inventories) {
            sbhelper.core.calc.CraftStock.merge(p?.sacks.orEmpty(), p?.inventories?.let { sbhelper.core.calc.CraftStock.inventory(it) }.orEmpty())
        }
        val path = remember(m, tier, target, setup, count, stock, state.gameData) {
            sbhelper.core.calc.MinionUpgrade.plan(state.gameData, m, tier, target, setup, count, stock)
        }
        val enchanted = state.device.materialsEnchanted
        val comp = remember(state.gameData) { sbhelper.core.calc.Compressor(state.gameData) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (targets.size > 1) Segmented(targets.map { "${ROMAN.getOrElse(it - 1) { "$it" }}까지" }, pick.coerceIn(0, targets.size - 1), { pick = it })
            MaterialFormToggle(enchanted) { on -> controller.repo.updateDevice { it.copy(materialsEnchanted = on) } }
        }
        Spacer(Modifier.height(12.dp))
        val who = "이 미니언" + if (count > 1) " ${count}개" else ""
        fun days(d: Double?) = when {
            d == null -> "-"
            d <= 0 -> "다 모았어요"
            else -> "약 " + daysText(d)
        }
        val tiles: List<@Composable (Modifier) -> Unit> = listOf(
            { mod -> StatTile("지금 단계 그대로 모으면", days(path.daysStay), mod, color = Sb.Pink,
                sub = "$who 를 ${ROMAN.getOrElse(tier - 1) { "$tier" }} 단계로 두고 전부 모을 때") },
            { mod -> StatTile("바로바로 올리면", days(path.daysRush), mod, color = Sb.Green,
                sub = "재료가 모일 때마다 바로 다음 단계로 올릴 때") },
            { mod -> StatTile("코인", if (path.coins > 0) formatCoins(path.coins.toDouble()) else "없음", mod, color = Sb.Gold,
                sub = "${path.steps.size}단계 업그레이드" + if (count > 1) " (미니언 1개 기준 재료)" else "") },
        )
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
        if (path.external.isNotEmpty()) Text("이 미니언이 만들지 않는 재료(빨간 표시)는 따로 구해야 해서 시간 계산에서 뺐어요.",
            style = SbText.body(11, Sb.TextFaint), modifier = Modifier.padding(top = 8.dp))

        Spacer(Modifier.height(10.dp))
        Label("필요한 재료 전체")
        path.raw.forEach { (id, need) ->
            val have = path.have[id] ?: 0.0
            val shown = shownMaterial(comp, id, need, enchanted)
            val done = have >= need - 1e-6
            val ext = id in path.external
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(state, shown.id, 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.itemName(shown.id), style = SbText.body(13, if (ext && !done) Sb.Red else Sb.Text), maxLines = 1)
                        if (shown.parts.size > 1 || shown.id != id) Text(partsText(state, shown.parts), style = SbText.body(11, Sb.TextFaint))
                    }
                    Text(if (shown.ratio == 1.0) "${formatNumber(have.toLong())} / ${formatNumber(kotlin.math.ceil(need).toLong())}"
                        else "${amountText(have / shown.ratio)} / ${amountText(need / shown.ratio)}",
                        style = SbText.pixel(12, if (done) Sb.Green else Sb.Text))
                    Text(formatPercent(if (need > 0) (have / need).coerceIn(0.0, 1.0) else 1.0, 0),
                        style = SbText.pixel(12, if (done) Sb.Green else Sb.Gold, bold = true),
                        modifier = Modifier.padding(start = 8.dp).widthIn(min = 40.dp), textAlign = TextAlign.End)
                }
                XpBar(if (need > 0) have / need else 1.0, Modifier.fillMaxWidth().padding(top = 3.dp), if (done) Sb.Green else Sb.Purple, 4.dp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Label("단계별 (바로바로 올릴 때)")
        var total = 0.0
        path.steps.forEach { st ->
            total += st.days ?: 0.0
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ROMAN.getOrElse(st.tier - 1) { "${st.tier}" }, style = SbText.pixel(12, Sb.Yellow), modifier = Modifier.width(40.dp))
                Text(st.raw.entries.joinToString(" · ") { (id, n) -> partsText(state, shownMaterial(comp, id, n, enchanted).parts) } +
                    if (st.coins > 0) " · ${formatCoins(st.coins.toDouble())} 코인" else "",
                    style = SbText.body(12, Sb.TextDim), modifier = Modifier.weight(1f), maxLines = 2)
                Spacer(Modifier.width(8.dp))
                Text(days(st.days), style = SbText.pixel(12, Sb.Text), modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End)
                Text("누적 " + (if (total <= 0) "-" else daysText(total)), style = SbText.pixel(12, Sb.TextFaint),
                    modifier = Modifier.widthIn(min = 84.dp), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("단계 재료는 기본 재료로 나눠서 셌고, 바로 전 단계 속도로 모은다고 계산했어요. 가진 재료(자루·인벤토리 등)는 먼저 썼어요." +
            if (count > 1) " 미니언 ${count}개가 같이 모으지만 재료는 한 개를 올리는 양이에요." else "",
            style = SbText.body(11, Sb.TextFaint))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = SbText.caption(Sb.TextDim), modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, style = SbText.body(12, if (selected) Sb.Window else Sb.Text, if (selected) FontWeight.SemiBold else FontWeight.Normal),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Sb.Gold else Sb.SurfaceHigh)
            .border(1.dp, if (selected) Sb.Gold else Sb.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun Stepper(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SbText.body(13), modifier = Modifier.weight(1f))
        StepButton("−", value > min) { onChange(value - 1) }
        Text("$value", style = SbText.pixel(13, Sb.Text, bold = true), modifier = Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center)
        StepButton("+", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(text, style = SbText.body(15, if (enabled) Sb.Text else Sb.TextFaint, FontWeight.Bold),
        textAlign = TextAlign.Center,
        modifier = Modifier.width(34.dp).clip(RoundedCornerShape(8.dp)).background(Sb.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp))
}

private fun hoursText(h: Double): String = when {
    h < 1 -> "${fixed(h * 60, 0)}분"
    h < 48 -> "${fixed(h, 1)}시간"
    else -> "${fixed(h / 24, 1)}일"
}

private fun daysText(d: Double): String = when {
    d < 1.0 / 24 -> "${fixed(d * 24 * 60, 0)}분"
    d < 1 -> "${fixed(d * 24, 1)}시간"
    else -> "${fixed(d, 1)}일"
}
