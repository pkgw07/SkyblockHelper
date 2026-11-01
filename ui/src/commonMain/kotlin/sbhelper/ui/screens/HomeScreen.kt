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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.EventLabels
import sbhelper.core.calc.EventOccurrence
import sbhelper.core.calc.Leveling
import sbhelper.core.calc.SkyblockCalendar
import sbhelper.core.model.GameMode
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.Screen
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.McText
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.StatTile
import sbhelper.ui.components.XpBar
import sbhelper.ui.components.rememberNow
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

@Composable
fun HomeScreen(controller: AppController, state: AppState, wide: Boolean) {
    val now = rememberNow()
    ScreenColumn(wide) {
        SkyblockClock(state, now, wide)

        if (!state.accountLinked) LinkAccountCard(controller)

        val profile = state.selectedProfile
        if (profile?.gameMode == GameMode.IRONMAN) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0x33AAAAAA), Color(0x11AAAAAA))))
                    .border(1.dp, Color(0x55AAAAAA), RoundedCornerShape(14.dp))
                    .clickable { controller.go(Screen.IRONMAN) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("♲", style = SbText.pixel(26, Sb.Gray, bold = true))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ironman 프로필이에요", style = SbText.body(15, weight = FontWeight.Bold))
                    Text("바자/경매장 대신 제작 플래너, 자루 NPC 가치, 컬렉션 도우미를 쓸 수 있어요.", style = SbText.body(13, Sb.TextDim))
                }
                Pill("아이언맨 도구 →", Sb.Gray)
            }
        }

        if (profile != null) {
            val picks = sbhelper.core.calc.ProgressionPlanner.recommend(
                sbhelper.core.calc.ProgressionPlanner.build(state.gameData, profile, state.skills))
            if (picks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("다음 목표 추천", style = SbText.pixel(14, Sb.Text, bold = true), modifier = Modifier.weight(1f))
                    Text("진행 트리 보기 →", style = SbText.body(12, Sb.Aqua), modifier = Modifier.clickable { controller.go(Screen.PLAN) })
                }
                RecommendRow(picks, wide, compact = true)
            }
        }

        Responsive(
            wide,
            cell(1.4f) { m -> ForgePanel(state, now, m) },
            cell(1f) { m -> EventsPanel(state, now, m) { controller.go(Screen.TIMERS) } },
        )

        if (profile != null) StatsRow(state, wide)

        Responsive(
            wide,
            cell(1f) { m -> MayorPanel(state, m) },
            cell(1f) { m -> PatchPanel(controller, state, m) },
        )
    }
}

@Composable
private fun SkyblockClock(state: AppState, now: Long, wide: Boolean) {
    val date = SkyblockCalendar.dateAt(now)
    val seasonColors = when (date.month / 3) {
        0 -> listOf(Color(0xFF1E4D2B), Color(0xFF0F1117))
        1 -> listOf(Color(0xFF5A4410), Color(0xFF0F1117))
        2 -> listOf(Color(0xFF5A2A10), Color(0xFF0F1117))
        else -> listOf(Color(0xFF1B3A5C), Color(0xFF0F1117))
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(seasonColors))
            .border(1.dp, Sb.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = if (wide) 26.dp else 18.dp, vertical = if (wide) 22.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("SKYBLOCK · YEAR ${date.year}", style = SbText.caption(Sb.Text.copy(alpha = 0.6f)))
            Spacer(Modifier.height(6.dp))
            Text("${date.monthName} ${date.day}${ordinal(date.day)}", style = SbText.pixel(if (wide) 28 else 22, Sb.Text, bold = true))
            Spacer(Modifier.height(2.dp))
            Text("${date.season} · ${date.timeText()}", style = SbText.pixel(14, Sb.Yellow))
        }
        val mayor = state.election?.mayor
        if (mayor != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("현 시장", style = SbText.caption(Sb.Text.copy(alpha = 0.6f)))
                Spacer(Modifier.height(4.dp))
                Text(mayor.name, style = SbText.pixel(18, Sb.Gold, bold = true))
                mayor.minister?.let { Text("장관 ${it.name}", style = SbText.pixel(12, Sb.TextDim)) }
            }
        }
    }
}

private fun ordinal(d: Int) = when {
    d in 11..13 -> "th"
    d % 10 == 1 -> "st"
    d % 10 == 2 -> "nd"
    d % 10 == 3 -> "rd"
    else -> "th"
}

@Composable
private fun LinkAccountCard(controller: AppController) {
    Panel(caption = "시작하기", title = "마인크래프트 계정을 연결해 주세요", accent = Sb.Aqua) {
        Text("닉네임과 Hypixel API 키를 입력하면 포지 완료, 경매 판매, 이벤트 알림을 받을 수 있어요.\n" +
            "시세·이벤트 정보는 계정 연결 없이도 볼 수 있어요.", style = SbText.body(14, Sb.TextDim))
        Spacer(Modifier.height(14.dp))
        SbButton("계정 연결하기", { controller.go(Screen.SETTINGS) }, icon = Icons.Rounded.Link)
    }
}

@Composable
private fun ForgePanel(state: AppState, now: Long, modifier: Modifier) {
    val slots = state.forgeStatuses()
    Panel(modifier, caption = "The Forge", title = "포지", accent = Sb.Gold,
        trailing = { if (slots.isNotEmpty()) Pill("${slots.count { it.isDone(now) }}/${slots.size} 완료", Sb.Gold) }) {
        when {
            !state.accountLinked -> EmptyState("계정 연결 필요", "계정을 연결하면 포지 진행 상황이 보여요.")
            state.selectedProfile == null -> EmptyState("프로필 불러오는 중", "잠시만 기다려 주세요.")
            slots.isEmpty() -> EmptyState("포지가 비어 있어요", "Dwarven Mines 의 포지에 아이템을 넣으면 여기에 나타나요.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                slots.forEach { slot ->
                    val done = slot.isDone(now)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemGlyph(slot.itemName, state.itemTier(slot.process.itemId), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${slot.process.slot}", style = SbText.pixel(11, Sb.TextFaint))
                                Spacer(Modifier.width(6.dp))
                                Text(slot.itemName, style = SbText.body(14, weight = FontWeight.SemiBold), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                val remaining = slot.remaining(now)
                                Text(
                                    when {
                                        remaining == null -> "시간 정보 없음"
                                        done -> "완료!"
                                        else -> formatDuration(remaining)
                                    },
                                    style = SbText.pixel(13, if (done) Sb.Green else Sb.Text, bold = done),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            XpBar(slot.progress(now) ?: 0.0, Modifier.fillMaxWidth(), if (done) Sb.Green else Sb.Gold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsPanel(state: AppState, now: Long, modifier: Modifier, onMore: () -> Unit) {
    val events = state.upcomingEvents(now).take(6)
    Panel(modifier, caption = "Calendar", title = "다가오는 이벤트", accent = Sb.Aqua,
        trailing = { Text("전체 보기", style = SbText.body(12, Sb.Aqua), modifier = Modifier.clickable(onClick = onMore)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            events.forEach { EventLine(it, now, state.cropsFor(it)) }
        }
    }
}

@Composable
fun EventLine(e: EventOccurrence, now: Long, crops: List<String>? = null) {
    val active = e.isActive(now)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (active) Sb.Aqua.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(EventLabels.emoji(e.name), style = SbText.body(16))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(EventLabels.label(e.name), style = SbText.body(14, weight = FontWeight.Medium), maxLines = 1)
            if (crops != null) CropLine(crops)
        }
        if (active) {
            Pill("진행 중 · ${formatDuration(e.end - now)}", Sb.Aqua, filled = true)
        } else {
            Text(formatDuration(e.start - now), style = SbText.pixel(12, Sb.TextDim))
        }
    }
}

/** 농사대회 작물 (비공식 출처 EliteBot 표시) */
@Composable
fun CropLine(crops: List<String>) {
    Text("🌱 " + crops.joinToString(" · ") { EventLabels.crop(it) } + "  (EliteBot)",
        style = SbText.body(12, Sb.Green), maxLines = 1)
}

@Composable
private fun StatsRow(state: AppState, wide: Boolean) {
    val p = state.selectedProfile ?: return
    val skills = state.skills?.skills
    val skillXp = p.skillXp
    val avg = if (skills != null && skillXp != null) {
        val counted = listOf("FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "ENCHANTING", "ALCHEMY", "TAMING", "CARPENTRY")
        val levels = counted.mapNotNull { k -> skills[k]?.let { def ->
            val cap = p.skillCap(k, def.maxLevel)
            Leveling.skill(skillXp[k] ?: 0.0, def, cap).let { it.level + it.progress }
        } }
        if (levels.isEmpty()) null else levels.average()
    } else null
    val cata = p.catacombsXp?.let { xp -> state.gameData.leveling.catacombs.takeIf { it.isNotEmpty() }?.let { Leveling.catacombs(xp, it) } }

    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> StatTile("지갑", formatCoins(p.purse), m, color = Sb.Gold, icon = Icons.Rounded.MonetizationOn) },
        { m -> StatTile("은행", p.bank?.let { formatCoins(it) } ?: "API 꺼짐", m, color = Sb.Gold, icon = Icons.Rounded.AccountBalance,
            sub = if (p.memberCount > 1) "협동 ${p.memberCount}명 공용" else null) },
        { m -> StatTile("스카이블럭 레벨", p.skyblockLevel?.let { fixed(it, 2) } ?: "-", m, color = Sb.Aqua, icon = Icons.Rounded.Star) },
        { m -> StatTile("스킬 평균", avg?.let { fixed(it, 2) } ?: if (p.skillXp == null) "API 꺼짐" else "-", m, color = Sb.Green, icon = Icons.Rounded.AutoAwesome) },
        { m -> StatTile("카타콤", cata?.let { fixed(it.level + it.progress, 2) } ?: "-", m, color = Sb.Red, icon = Icons.Rounded.Castle) },
    )
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { it(Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MayorPanel(state: AppState, modifier: Modifier) {
    val mayor = state.election?.mayor
    Panel(modifier, caption = "Election", title = mayor?.let { "시장 ${it.name}" } ?: "시장 정보", accent = Sb.Pink) {
        if (mayor == null) {
            EmptyState("불러오는 중", "공식 API 에서 선거 정보를 가져오고 있어요.")
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            mayor.perks.forEach { perk ->
                Column {
                    Text(perk.name, style = SbText.pixel(13, Sb.Gold, bold = true))
                    McText(perk.description, size = 12)
                }
            }
            mayor.minister?.let { m ->
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.Border))
                Text("장관 ${m.name}", style = SbText.body(13, Sb.TextDim, FontWeight.SemiBold))
                m.perk?.let { p ->
                    Text(p.name, style = SbText.pixel(13, Sb.Aqua, bold = true))
                    McText(p.description, size = 12)
                }
            }
            state.election?.current?.let { cur ->
                val leader = cur.candidates.maxByOrNull { it.votes }
                if (leader != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("🗳 ${cur.year}년 선거 진행 중 — 1위 ${leader.name}", style = SbText.body(13, Sb.Yellow))
                }
            }
        }
    }
}

@Composable
private fun PatchPanel(controller: AppController, state: AppState, modifier: Modifier) {
    Panel(modifier, caption = "Patch Notes · Wiki", title = "최신 패치노트", accent = Sb.Green,
        trailing = { Text("전체 보기", style = SbText.body(12, Sb.Green), modifier = Modifier.clickable { controller.go(Screen.PATCH) }) }) {
        if (state.patchNotes.isEmpty()) {
            EmptyState("불러오는 중", "공식 위키 Changelog 에서 가져오고 있어요.")
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.patchNotes.take(5).forEach { n ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        controller.patchNotes.selected = n.page
                        controller.go(Screen.PATCH)
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title.replace(Regex("""^\[[^\]]+]\s*"""), ""), style = SbText.body(14, weight = FontWeight.Medium),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.date + if (n.alpha) " · 알파 서버" else "", style = SbText.body(12, if (n.alpha) Sb.Pink else Sb.TextFaint))
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Sb.TextFaint, modifier = Modifier.size(16.dp))
                }
            }
            if (state.fireSales.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                state.fireSales.forEach { s ->
                    Text("🔥 파이어 세일: ${state.itemName(s.itemId)} — ${s.price} 젬", style = SbText.body(13, Sb.Red))
                }
            }
        }
    }
}
