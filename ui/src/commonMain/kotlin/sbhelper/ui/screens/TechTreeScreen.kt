package sbhelper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import sbhelper.core.AppState
import sbhelper.core.calc.Leveling
import sbhelper.core.calc.Requirement
import sbhelper.core.calc.Requirements
import sbhelper.core.data.SkillGuide
import sbhelper.core.nbt.ItemParser
import sbhelper.ui.AppController
import sbhelper.ui.components.ItemIcon
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/**
 * 스킬별 장비 진행 가이드 (사용자 제공 가이드 내용 + 공식 아이템 데이터).
 * 스킬마다 구성이 다르다: 채광은 HotM 단계 사다리, 농사는 Fortune 막대, 낚시는 갈래 탭, 사냥은 다섯 갈래, 전투는 여러 탭.
 */
@Composable
fun TechTreeScreen(controller: AppController, state: AppState, wide: Boolean) {
    val skills = SkillGuide.skills
    val tab = controller.gearTab.coerceIn(0, skills.lastIndex)
    val skill = skills[tab]
    val ctx = remember(state.items, state.selectedProfile, state.museum, state.collections, state.gameData) { GuideContext(state) }
    ScreenColumn(wide) {
        Segmented(skills.map { it.label }, tab, { controller.gearTab = it })
        SkillHeader(state, ctx, skill)
        val sub = if (skill.tabs.size > 1) (controller.gearSubTabs[skill.key] ?: 0).coerceIn(0, skill.tabs.lastIndex) else 0
        if (skill.tabs.size > 1) Segmented(skill.tabs.map { it.label }, sub, { controller.gearSubTabs[skill.key] = it })
        skill.tabs[sub].sections.forEach { sec -> Section(controller, state, ctx, sec, wide) }
        Text("출처: ${SkillGuide.SOURCE}. 아이템 그림·보유(✔)·조건 달성은 공식 API 와 내 프로필 기준이에요. 게임 업데이트에 따라 추천은 바뀔 수 있어요.",
            style = SbText.body(12, Sb.TextFaint))
    }
}

// ---------------------------------------------------------------- 이름 → 아이템, 보유, 조건
/** 가이드 이름을 공식 아이템으로 찾고, 내가 가졌는지·조건을 채웠는지 판단 */
class GuideContext(val state: AppState) {
    private val byName: Map<String, String> = state.items.values.associate { it.name.replace(Regex("§."), "").trim().lowercase() to it.id }
    private val armorCats = setOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
    private val collectionNames: Set<String> = state.collections?.collections?.values?.flatMap { c -> c.items.values.map { it.name.lowercase() } }?.toSet().orEmpty()
    val owned: Set<String> = run {
        val p = state.selectedProfile ?: return@run emptySet()
        val ids = HashSet<String>()
        p.inventories.values.forEach { d -> ItemParser.parse(d).filterNotNull().forEach { it.skyblockId?.let(ids::add) } }
        p.wardrobe?.let { w -> (w.armor.values + w.equipment.values).flatten().forEach { d -> ItemParser.parse(d).filterNotNull().forEach { it.skyblockId?.let(ids::add) } } }
        p.pets.forEach { pet -> ids += "pet:" + pet.type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) } }
        ids + sbhelper.core.calc.MuseumCalc.ownedIds(state.items, state.museum)
    }
    private val ownedLower = owned.map { it.lowercase() }.toSet()
    private val cache = HashMap<String, String?>()

    private fun clean(name: String) = name.trim().removeSuffix(" 유지").replace(Regex("""\s*\(.*\)$"""), "").trim()

    /** 세트 이름("Sorrow Armor", "Armor of Divan")이면 그 세트 부위들의 공통 앞부분 */
    private fun setBase(name: String): String? = when {
        name.endsWith(" Armor", true) -> name.dropLast(6)
        name.startsWith("Armor of ", true) -> null
        else -> null
    }

    fun id(name: String): String? = cache.getOrPut(name) {
        val n = clean(name)
        if (n.startsWith("pet:")) return@getOrPut n
        for (part in n.split(" 또는 ", " / ")) {
            val p = part.trim()
            byName[p.lowercase()]?.let { return@getOrPut it }
            setBase(p)?.let { base ->
                val pieces = state.items.values.filter { it.name.replace(Regex("§."), "").startsWith("$base ", true) && it.category in armorCats }
                (pieces.firstOrNull { it.category == "CHESTPLATE" } ?: pieces.firstOrNull())?.let { return@getOrPut it.id }
            }
            Regex("""^Armor of (.+)$""", RegexOption.IGNORE_CASE).find(p)?.let { m ->
                byName["chestplate of ${m.groupValues[1].lowercase()}"]?.let { return@getOrPut it }
            }
        }
        null
    }

    fun isOwned(name: String): Boolean {
        val n = clean(name)
        if (n.startsWith("pet:")) return n.lowercase() in ownedLower
        val id = id(name) ?: return false
        if (id in owned) return true
        // 세트: 부위 하나라도 있으면
        val base = setBase(n) ?: Regex("""^Armor of (.+)$""", RegexOption.IGNORE_CASE).find(n)?.let { "of " + it.groupValues[1] }
        if (base != null) return owned.any { o -> state.itemName(o).replace(Regex("§."), "").let { it.startsWith("$base ", true) || it.endsWith(" $base", true) } }
        return false
    }

    fun reqs(cond: String): List<Requirement> =
        Requirements.parseGuide(cond) { it.lowercase() in collectionNames }.map { Requirements.item(state, it) }

    /** 머리 카드·단계표의 "지금 내 값" */
    fun metric(key: String?): Int? {
        val p = state.selectedProfile ?: return null
        return when {
            key == null -> null
            key == "HOTM" -> p.hotmXp?.let { xp -> state.gameData.leveling.hotm.takeIf { it.isNotEmpty() }?.let { Leveling.hotm(xp, it).level } }
            key == "AP" -> ((p.member?.get("accessory_bag_storage") as? kotlinx.serialization.json.JsonObject)?.get("highest_magical_power") as? JsonPrimitive)?.doubleOrNull?.toInt()
            key.startsWith("SKILL:") -> {
                val k = key.removePrefix("SKILL:")
                val def = state.skills?.skills?.get(k) ?: return null
                val xp = p.skillXp?.get(k) ?: return null
                Leveling.skill(xp, def, p.skillCap(k, def.maxLevel)).level
            }
            else -> null
        }
    }
}

private fun metricLabel(key: String?) = when {
    key == "HOTM" -> "HotM"
    key == "AP" -> "Accessory Power"
    key?.startsWith("SKILL:") == true -> key.removePrefix("SKILL:").lowercase().replaceFirstChar(Char::uppercase) + " 레벨"
    else -> ""
}

// ---------------------------------------------------------------- 머리 카드
@Composable
private fun SkillHeader(state: AppState, ctx: GuideContext, skill: SkillGuide.Skill) {
    val mine = ctx.metric(skill.metric)
    val stages = skill.tabs.flatMap { it.sections }.filterIsInstance<SkillGuide.Stages>().firstOrNull { it.metric == skill.metric }
    val current = if (mine != null && stages != null) currentStage(stages, mine) else -1
    Panel(Modifier.fillMaxWidth(), caption = "Skill guide", title = skill.label + if (skill.key != "COMMON") " 장비 진행" else "", accent = Sb.Gold,
        trailing = {
            if (mine != null) Pill("내 ${metricLabel(skill.metric)} $mine", Sb.Green)
        }) {
        skill.intro.forEach { Text(it, style = SbText.body(14, Sb.TextDim), modifier = Modifier.padding(vertical = 2.dp)) }
        if (stages != null && current >= 0) {
            Spacer(Modifier.height(8.dp))
            val st = stages.rows[current]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("지금: ${st.label}", Sb.Gold)
                Spacer(Modifier.width(8.dp))
                Text("목표 — ${st.goal}", style = SbText.body(14, Sb.Text, FontWeight.Medium))
            }
        }
        if (state.selectedProfile == null) Text("계정을 연결하면 내 레벨·보유 장비로 ✔ 와 다음 추천을 보여 줘요.", style = SbText.body(12, Sb.TextFaint))
    }
}

private fun currentStage(st: SkillGuide.Stages, v: Int): Int =
    st.rows.indexOfLast { r -> (r.from ?: Int.MIN_VALUE) <= v }.takeIf { st.rows.any { it.from != null } } ?: -1

// ---------------------------------------------------------------- 섹션
@Composable
private fun Section(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Sec, wide: Boolean) {
    when (sec) {
        is SkillGuide.Text -> TextSection(sec)
        is SkillGuide.Stages -> StagesSection(ctx, sec, wide)
        is SkillGuide.Flow -> Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) { FlowSteps(controller, state, ctx, sec.steps, wide); Notes(sec.notes) }
        is SkillGuide.Ladder -> LadderSection(controller, state, ctx, sec, wide)
        is SkillGuide.Columns -> ColumnsSection(controller, state, ctx, sec, wide)
        is SkillGuide.SlotGrid -> SlotGridSection(controller, state, ctx, sec, wide)
        is SkillGuide.Compare -> CompareSection(controller, state, ctx, sec)
        is SkillGuide.Cards -> CardsSection(controller, state, ctx, sec, wide)
        is SkillGuide.Bars -> BarsSection(controller, state, ctx, sec)
        is SkillGuide.Table -> TableSection(controller, state, ctx, sec, wide)
        is SkillGuide.Bullets -> Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Aqua) {
            sec.items.forEach { Row(Modifier.padding(vertical = 2.dp)) { Text("•", style = SbText.body(14, Sb.Gold)); Spacer(Modifier.width(8.dp)); Text(it, style = SbText.body(14, Sb.Text)) } }
        }
        is SkillGuide.Final -> FinalSection(controller, state, ctx, sec)
    }
}

@Composable
private fun Notes(notes: List<String>) {
    if (notes.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    notes.forEach { Text("· $it", style = SbText.body(13, Sb.TextDim), modifier = Modifier.padding(vertical = 1.dp)) }
}

@Composable
private fun TextSection(sec: SkillGuide.Text) {
    if (sec.tip) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sb.Gold.copy(alpha = 0.10f)).border(1.dp, Sb.Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            Text("💡", style = SbText.body(16)); Spacer(Modifier.width(8.dp))
            Column { sec.lines.forEach { Text(it, style = SbText.body(14, Sb.Text)) } }
        }
    } else Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Aqua) {
        sec.lines.forEach { Text(it, style = SbText.body(14, Sb.TextDim), modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

// ---- 진행 단계: 지금 단계 강조
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagesSection(ctx: GuideContext, sec: SkillGuide.Stages, wide: Boolean) {
    val mine = ctx.metric(sec.metric)
    val cur = if (mine != null) currentStage(sec, mine) else -1
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Green,
        trailing = { if (mine != null) Pill("내 ${metricLabel(sec.metric)} $mine", Sb.Green) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sec.rows.forEachIndexed { i, r ->
                val done = cur > i; val now = cur == i
                Column(
                    Modifier.width(if (wide) 210.dp else 160.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (now) Sb.Gold.copy(alpha = 0.14f) else Sb.SurfaceHigh)
                        .border(if (now) 2.dp else 1.dp, if (now) Sb.Gold else Sb.Border, RoundedCornerShape(12.dp)).padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = SbText.pixel(11, Sb.TextFaint)); Spacer(Modifier.width(6.dp))
                        Text(r.label, style = SbText.body(15, if (now) Sb.Gold else if (done) Sb.Green else Sb.Text, FontWeight.Bold))
                        if (done) { Spacer(Modifier.width(4.dp)); Text("✔", style = SbText.body(13, Sb.Green, FontWeight.Bold)) }
                    }
                    if (r.cond.isNotBlank()) Text(r.cond, style = SbText.pixel(11, Sb.TextDim))
                    Spacer(Modifier.height(4.dp))
                    Text(r.goal, style = SbText.body(13, Sb.Text))
                    if (now) Text("◀ 지금 여기", style = SbText.pixel(11, Sb.Gold, bold = true))
                }
            }
        }
    }
}

// ---- 스텝 상자 (흐름·사다리·갈래 공통)
@Composable
private fun StepBox(controller: AppController, state: AppState, ctx: GuideContext, st: SkillGuide.Step, next: Boolean, width: Dp) {
    val id = ctx.id(st.name)
    val owned = ctx.isOwned(st.name)
    val reqs = ctx.reqs(st.cond)
    val tier = id?.let { state.itemTier(it) }
    val color = if (id != null) McColors.rarity(tier) else Sb.TextDim
    val shown = if (st.name.startsWith("pet:")) st.name.removePrefix("pet:") else st.name
    Column(
        Modifier.width(width).clip(RoundedCornerShape(10.dp))
            .background(when { next -> Sb.Gold.copy(alpha = 0.10f); owned -> Sb.Green.copy(alpha = 0.07f); else -> Sb.SurfaceHigh })
            .border(if (next) 2.dp else 1.dp, when { next -> Sb.Gold; owned -> Sb.Green.copy(alpha = 0.7f); st.optional -> Sb.Border.copy(alpha = 0.6f); else -> Sb.Border }, RoundedCornerShape(10.dp))
            .let { m -> if (id != null && !id.startsWith("pet:")) m.clickable { controller.openItem(id) } else if (id != null) m.clickable { controller.openPage(shown + " Pet") } else m }
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (id != null) { ItemIcon(state, id, 28.dp); Spacer(Modifier.width(6.dp)) }
            Text(shown, style = SbText.body(13, color, FontWeight.SemiBold), maxLines = 2, modifier = Modifier.weight(1f))
            if (owned) Text("✔", style = SbText.body(13, Sb.Green, FontWeight.Bold))
        }
        if (st.optional) Text("선택", style = SbText.pixel(10, Sb.TextFaint))
        if (reqs.isNotEmpty()) reqs.forEach { r -> ReqChip(r) }
        else if (st.cond.isNotBlank()) Text(st.cond, style = SbText.pixel(11, Sb.TextDim))
        if (st.note.isNotBlank()) Text(st.note, style = SbText.body(12, Sb.TextFaint), maxLines = 3)
        if (next) Text("다음 추천", style = SbText.pixel(10, Sb.Gold, bold = true))
    }
}

@Composable
private fun ReqChip(r: Requirement) {
    val (mark, c) = when (r.met) { true -> "✔" to Sb.Green; false -> "✘" to Sb.Red; null -> "•" to Sb.TextDim }
    Text("$mark ${r.text}", style = SbText.body(11, c, FontWeight.Medium), maxLines = 2)
}

/** 마지막으로 가진 (선택 아닌) 단계 다음 = 다음 추천 */
private fun nextIndex(ctx: GuideContext, steps: List<SkillGuide.Step>): Int {
    if (ctx.owned.isEmpty()) return -1
    val last = steps.indexOfLast { ctx.isOwned(it.name) }
    return (last + 1 until steps.size).firstOrNull { !steps[it].optional && ctx.id(steps[it].name) != null } ?: -1
}

@Composable
private fun Arrow(dashed: Boolean = false) {
    val c = if (dashed) Sb.TextFaint else Sb.Gold
    Canvas(Modifier.padding(horizontal = 3.dp).size(22.dp, 16.dp)) {
        val y = size.height / 2
        drawLine(c, Offset(1.dp.toPx(), y), Offset(size.width - 7.dp.toPx(), y), strokeWidth = 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null)
        drawPath(Path().apply { moveTo(size.width - 8.dp.toPx(), y - 5.dp.toPx()); lineTo(size.width - 8.dp.toPx(), y + 5.dp.toPx()); lineTo(size.width, y); close() }, c)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowSteps(controller: AppController, state: AppState, ctx: GuideContext, steps: List<SkillGuide.Step>, wide: Boolean) {
    val next = nextIndex(ctx, steps)
    FlowRow(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, st ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Arrow(dashed = st.optional)
                StepBox(controller, state, ctx, st, i == next, if (wide) 180.dp else 150.dp)
            }
        }
    }
}

// ---- 사다리: 해금 단계별 묶음 (지금 단계 줄 강조)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LadderSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Ladder, wide: Boolean) {
    // 각 단계 이름("HotM 3")을 조건으로 보고, 채운 마지막 단계를 "지금"
    val met = sec.groups.map { (label, _) -> ctx.reqs(label.replace("~", " ").split(' ').take(2).joinToString(" ")).let { r -> if (r.isEmpty()) null else r.all { it.met == true } } }
    val cur = met.indexOfLast { it == true }
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sec.groups.forEachIndexed { i, (label, steps) ->
                val now = i == cur
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (now) Sb.Gold.copy(alpha = 0.08f) else Color.Transparent).padding(6.dp)) {
                    Column(Modifier.width(if (wide) 96.dp else 74.dp).padding(top = 6.dp)) {
                        Text(label, style = SbText.pixel(13, when { now -> Sb.Gold; met[i] == true -> Sb.Green; met[i] == false -> Sb.TextDim; else -> Sb.Text }, bold = true))
                        if (met[i] == true) Text(if (now) "◀ 지금" else "✔ 열림", style = SbText.pixel(10, if (now) Sb.Gold else Sb.Green))
                        else if (met[i] == false) Text("🔒", style = SbText.body(12))
                    }
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEach { st -> StepBox(controller, state, ctx, st.copy(cond = ""), false, if (wide) 190.dp else 124.dp) }
                    }
                }
            }
        }
        Notes(sec.notes)
    }
}

// ---- 나란한 갈래 (사냥 도구)
@Composable
private fun ColumnsSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Columns, wide: Boolean) {
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            sec.lines.forEach { (label, steps) ->
                val next = nextIndex(ctx, steps)
                Column(Modifier.width(if (wide) 200.dp else 160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = SbText.body(14, Sb.Gold, FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    steps.forEachIndexed { i, st ->
                        if (i > 0) Text("↓", style = SbText.body(16, Sb.Gold))
                        StepBox(controller, state, ctx, st, i == next, if (wide) 200.dp else 160.dp)
                    }
                }
            }
        }
        Notes(sec.notes)
    }
}

// ---- 칸 하나 (아이템이면 그림, 아니면 설명 글)
@Composable
private fun Cell(controller: AppController, state: AppState, ctx: GuideContext, text: String, modifier: Modifier = Modifier) {
    val id = ctx.id(text)
    val owned = id != null && ctx.isOwned(text)
    Row(modifier.let { m -> if (id != null && !id.startsWith("pet:")) m.clip(RoundedCornerShape(8.dp)).clickable { controller.openItem(id) } else m }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (id != null) { ItemIcon(state, id, 24.dp); Spacer(Modifier.width(6.dp)) }
        Text(text.removePrefix("pet:"), style = SbText.body(13, if (id != null) McColors.rarity(state.itemTier(id)) else Sb.TextDim, if (id != null) FontWeight.Medium else FontWeight.Normal),
            maxLines = 3, modifier = Modifier.weight(1f, fill = false))
        if (owned) { Spacer(Modifier.width(4.dp)); Text("✔", style = SbText.body(12, Sb.Green, FontWeight.Bold)) }
    }
}

// ---- 단계 × 슬롯 격자
@Composable
private fun SlotGridSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.SlotGrid, wide: Boolean) {
    val cw = if (wide) 200.dp else 150.dp
    val lw = if (wide) 110.dp else 80.dp
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.padding(vertical = 4.dp)) {
                Spacer(Modifier.width(lw))
                sec.slots.forEach { Text(it, style = SbText.caption(Sb.TextDim), modifier = Modifier.width(cw).padding(start = 4.dp)) }
            }
            sec.rows.forEachIndexed { r, (label, cells) ->
                Row(Modifier.background(if (r % 2 == 0) Sb.SurfaceHigh.copy(alpha = 0.5f) else Color.Transparent).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = SbText.pixel(12, Sb.Gold, bold = true), modifier = Modifier.width(lw).padding(start = 6.dp))
                    cells.forEach { c -> Cell(controller, state, ctx, c, Modifier.width(cw)) }
                }
            }
        }
        Notes(sec.notes)
    }
}

// ---- 슬롯별 초중반 → 후반
@Composable
private fun CompareSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Compare) {
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) {
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("슬롯", style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(0.7f))
            Text(sec.headers.first, style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(1.5f))
            Spacer(Modifier.width(28.dp))
            Text(sec.headers.second, style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(1.5f))
        }
        sec.rows.forEach { (slot, a, b) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(slot, style = SbText.pixel(12, Sb.Gold, bold = true), modifier = Modifier.weight(0.7f))
                Cell(controller, state, ctx, a, Modifier.weight(1.5f))
                Arrow()
                Cell(controller, state, ctx, b, Modifier.weight(1.5f))
            }
        }
        Notes(sec.notes)
    }
}

// ---- 카드 (펫 등)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardsSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Cards, wide: Boolean) {
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Aqua) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sec.cards.forEach { c ->
                val id = ctx.id(c.name)
                val owned = ctx.isOwned(c.name)
                Row(
                    Modifier.width(if (wide) 280.dp else 330.dp).clip(RoundedCornerShape(12.dp)).background(if (owned) Sb.Green.copy(alpha = 0.07f) else Sb.SurfaceHigh)
                        .border(1.dp, if (owned) Sb.Green.copy(alpha = 0.6f) else Sb.Border, RoundedCornerShape(12.dp))
                        .let { m -> if (id != null && c.name.startsWith("pet:")) m.clickable { controller.openPage(c.name.removePrefix("pet:") + " Pet") } else if (id != null) m.clickable { controller.openItem(id) } else m }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (id != null) { ItemIcon(state, id, 40.dp); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name.removePrefix("pet:"), style = SbText.body(15, Sb.Text, FontWeight.Bold))
                            if (owned) { Spacer(Modifier.width(6.dp)); Text("✔ 보유", style = SbText.pixel(11, Sb.Green)) }
                        }
                        if (c.cond.isNotBlank()) Text(c.cond, style = SbText.pixel(12, Sb.Gold))
                        if (c.note.isNotBlank()) Text(c.note, style = SbText.body(13, Sb.TextDim))
                    }
                }
            }
        }
        Notes(sec.notes)
    }
}

// ---- 막대 비교 (농사 방어구 Fortune, 낚시 속도 상한)
@Composable
private fun BarsSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Bars) {
    val max = sec.steps.maxOf { it.value ?: 0 }.coerceAtLeast(1)
    val next = nextIndex(ctx, sec.steps)
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Gold) {
        sec.steps.forEachIndexed { i, st ->
            val id = ctx.id(st.name)
            val owned = ctx.isOwned(st.name)
            val reqs = ctx.reqs(st.cond)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(220.dp).let { m -> if (id != null) m.clickable { controller.openItem(id) } else m }, verticalAlignment = Alignment.CenterVertically) {
                    if (id != null) { ItemIcon(state, id, 24.dp); Spacer(Modifier.width(6.dp)) }
                    Text(st.name, style = SbText.body(13, if (i == next) Sb.Gold else if (id != null) McColors.rarity(state.itemTier(id)) else Sb.Text, FontWeight.Medium), maxLines = 1)
                    if (owned) { Spacer(Modifier.width(4.dp)); Text("✔", style = SbText.body(12, Sb.Green, FontWeight.Bold)) }
                }
                Column(Modifier.weight(1f)) {
                    XpBar((st.value ?: 0).toDouble() / max, Modifier.fillMaxWidth(), if (i == next) Sb.Gold else Sb.Green, 10.dp)
                    if (reqs.isNotEmpty()) Row { reqs.forEach { ReqChip(it); Spacer(Modifier.width(8.dp)) } }
                }
                Text("+${st.value} ", style = SbText.pixel(13, Sb.Gold, bold = true), modifier = Modifier.padding(start = 8.dp).widthIn(min = 44.dp))
            }
        }
        Text("단위: ${sec.unit}", style = SbText.body(11, Sb.TextFaint))
        Notes(sec.notes)
    }
}

// ---- 일반 표 ("@이름" 은 아이템 그림)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Table, wide: Boolean) {
    Panel(Modifier.fillMaxWidth(), title = sec.title, accent = Sb.Aqua) {
        val weights = sec.headers.indices.map { if (it == 0) 1f else 1.4f }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            sec.headers.forEachIndexed { i, h -> Text(h, style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(weights[i])) }
        }
        sec.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().background(if (r % 2 == 0) Sb.SurfaceHigh.copy(alpha = 0.5f) else Color.Transparent).padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { i, cell ->
                    Box(Modifier.weight(weights.getOrElse(i) { 1f })) {
                        if ('@' in cell) {
                            FlowRow(verticalArrangement = Arrangement.Center) {
                                Regex("""@[^@/]+|[^@]+""").findAll(cell).forEach { m ->
                                    val t = m.value
                                    if (t.startsWith("@")) Cell(controller, state, ctx, t.removePrefix("@").trim())
                                    else if (t.isNotBlank()) Text(t.trim(), style = SbText.body(13, Sb.TextDim), modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp))
                                }
                            }
                        } else Text(cell, style = SbText.body(13, if (i == 0) Sb.Text else Sb.TextDim, if (i == 0) FontWeight.Medium else FontWeight.Normal))
                    }
                }
            }
        }
        Notes(sec.notes)
    }
}

// ---- 최종 목표 세팅 카드
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinalSection(controller: AppController, state: AppState, ctx: GuideContext, sec: SkillGuide.Final) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Gold.copy(alpha = 0.08f))
            .border(2.dp, Sb.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Text("🏆 " + sec.title, style = SbText.body(17, Sb.Gold, FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        sec.lines.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = SbText.pixel(13, Sb.Gold, bold = true), modifier = Modifier.width(96.dp))
                // 이름이 아이템이면 그림과 함께 (여러 개는 / 나 + 로 나뉨)
                FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    v.split(" / ", " + ").forEachIndexed { i, part ->
                        if (i > 0) Text(" · ", style = SbText.body(13, Sb.TextFaint), modifier = Modifier.padding(vertical = 6.dp))
                        if (ctx.id(part) != null) Cell(controller, state, ctx, part.trim())
                        else Text(part.trim(), style = SbText.body(14, Sb.Text), modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}
