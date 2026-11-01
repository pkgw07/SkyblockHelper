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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.WikiApi
import sbhelper.core.calc.EventLabels
import sbhelper.core.calc.EventOccurrence
import sbhelper.core.calc.SkyblockCalendar
import sbhelper.core.util.formatDuration
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PixelIcon
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.WikiIcons
import sbhelper.ui.components.rememberNow
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 이벤트: 일정 + 이벤트별 정보(공식 위키) / 제이콥 농사대회 예정 작물 */
@Composable
fun EventsScreen(controller: AppController, state: AppState, wide: Boolean) {
    val now = rememberNow()
    ScreenColumn(wide) {
        Segmented(listOf("이벤트 일정·정보", "🌾 제이콥 농사대회 작물"), controller.eventsTab, { controller.eventsTab = it })
        if (controller.eventsTab == 1) {
            JacobPage(controller, state, now, wide)
            return@ScreenColumn
        }
        // 다음 진행 시각 순 (진행 중 먼저). 시장 전용 이벤트는 지금 시장일 때만 일정에 나온다
        val upcoming = state.upcomingEvents(now)
        val names = (upcoming.map { it.name } + state.gameData.events.map { it.name }).distinct()
        val selected = controller.event ?: names.firstOrNull()
        val list: @Composable (Modifier) -> Unit = { m ->
            Panel(m, caption = "Calendar · Year ${SkyblockCalendar.dateAt(now).year}", title = "이벤트 일정", accent = Sb.Aqua) {
                names.forEach { name ->
                    val occ = upcoming.firstOrNull { it.name == name }
                    EventRow(controller, state, name, occ, now, name == selected) { controller.event = name }
                }
            }
        }
        val detail: @Composable (Modifier) -> Unit = { m ->
            if (selected != null) EventDetail(controller, state, selected, upcoming.firstOrNull { it.name == selected }, now, m)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                list(Modifier.width(380.dp))
                detail(Modifier.weight(1f))
            }
        } else {
            detail(Modifier.fillMaxWidth())
            list(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EventRow(controller: AppController, state: AppState, name: String, occ: EventOccurrence?, now: Long, selected: Boolean, onClick: () -> Unit) {
    val active = occ?.isActive(now) == true
    val on = state.synced.notify.eventEnabled(name)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(when { selected -> Sb.SurfaceHover; active -> Sb.Aqua.copy(alpha = 0.08f); else -> Color.Transparent })
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(EventLabels.emoji(name), style = SbText.body(16))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(EventLabels.label(name), style = SbText.body(14, weight = FontWeight.Medium))
            Text(
                when {
                    occ == null -> "지금 시장의 이벤트가 아니에요"
                    active -> "진행 중 · ${formatDuration(occ.end - now)} 남음"
                    else -> "${formatDuration(occ.start - now)} 후 · ${localTime(occ.start, true)}"
                },
                style = SbText.body(12, if (active) Sb.Aqua else Sb.TextFaint),
            )
        }
        Icon(if (on) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone, "알림",
            tint = if (on) Sb.Gold else Sb.TextFaint,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                controller.repo.updateSynced { s -> s.copy(notify = s.notify.copy(events = s.notify.events + (name to !on))) }
            }.padding(6.dp).size(20.dp))
    }
}

@Composable
private fun EventDetail(controller: AppController, state: AppState, name: String, occ: EventOccurrence?, now: Long, modifier: Modifier) {
    val platform = LocalPlatform.current
    val ev = state.gameData.events.firstOrNull { it.name == name }
    val page = ev?.page ?: name
    val blocks = rememberWikiPage(controller, page)
    Panel(modifier, caption = "Event · Wiki", title = "${EventLabels.emoji(name)} ${EventLabels.label(name)}", accent = Sb.Gold) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                occ == null -> Pill("지금 시장의 이벤트가 아님", Sb.TextFaint)
                occ.isActive(now) -> Pill("진행 중 · ${formatDuration(occ.end - now)} 남음", Sb.Aqua, filled = true)
                else -> Pill("${formatDuration(occ.start - now)} 후 시작", Sb.Gold)
            }
            ev?.requiresNpc?.let { Pill("$it 시장/장관일 때", Sb.Purple) }
        }
        if (occ != null) {
            Spacer(Modifier.height(8.dp))
            Text(if (occ.end - occ.start < 60_000) "다음: ${localTime(occ.start, true)}"
                else "다음: ${localTime(occ.start, true)} ~ ${localTime(occ.end, true)} (진행 ${formatDuration(occ.end - occ.start)})",
                style = SbText.body(13, Sb.TextDim))
            state.cropsFor(occ)?.let { CropLine(it) }
        }
        ev?.cycle?.takeIf { it.size >= 2 }?.let { c ->
            Text("반복: ${formatDuration(c.sum() * 1000)}마다", style = SbText.body(12, Sb.TextFaint))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (name.contains("Jacob")) SbButton("🌾 예정 작물 보기", { controller.openJacob() }, color = Sb.Green)
            SbButton("위키에서 보기", { platform.openUrl(WikiApi.pageUrl(page)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew,
                outlined = true, color = Sb.Aqua)
        }
        Spacer(Modifier.height(12.dp))
        when {
            blocks != null -> WikiBlocks(controller, blocks, max = 60)
            WikiPages.errors[page] != null -> Text("⚠ ${WikiPages.errors[page]}", style = SbText.body(13, Sb.Negative))
            else -> Text("공식 위키에서 설명을 불러오는 중…", style = SbText.body(13, Sb.TextFaint))
        }
    }
}

/** 제이콥 농사대회: 앞으로 열릴 대회와 작물 (비공식: EliteBot) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JacobPage(controller: AppController, state: AppState, now: Long, wide: Boolean) {
    var crop by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (state.jacobCrops.isEmpty()) controller.repo.refreshContests() }
    val contests = state.jacobCrops.entries.filter { it.key + 20 * 60_000L > now }.sortedBy { it.key }
    val allCrops = contests.flatMap { it.value }.distinct().sorted()
    Panel(Modifier.fillMaxWidth(), caption = "Jacob's Farming Contest", title = "예정 작물", accent = Sb.Green) {
        Text("대회 시각은 공식 위키 일정, 작물은 비공식 출처(EliteBot, 농사 커뮤니티)예요. 작물을 누르면 그 작물이 나오는 대회만 보여요.",
            style = SbText.body(12, Sb.TextFaint))
        if (!state.device.showContestCrops) {
            Spacer(Modifier.height(6.dp))
            Text("설정에서 '농사대회 작물 표시'가 꺼져 있어요.", style = SbText.body(12, Sb.Negative))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CropChip("전체", null, crop == null) { crop = null }
            allCrops.forEach { c -> CropChip("${EventLabels.crop(c)} ($c)", c, crop == c) { crop = if (crop == c) null else c } }
        }
    }
    val shown = contests.filter { crop == null || crop in it.value }.take(if (wide) 60 else 30)
    if (shown.isEmpty()) {
        Panel(Modifier.fillMaxWidth()) { EmptyState("예정 작물 정보가 없어요", "EliteBot 에서 아직 받지 못했거나, 올해 정보가 아직 없어요.", icon = Icons.Rounded.Event) }
        return
    }
    Panel(Modifier.fillMaxWidth()) {
        shown.forEachIndexed { i, (start, crops) ->
            val active = now in start until start + 20 * 60_000L
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) Sb.Green.copy(alpha = 0.10f) else if (i == 0) Sb.SurfaceHigh else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(if (wide) 190.dp else 120.dp)) {
                    Text(localTime(start, true), style = SbText.pixel(13, if (active) Sb.Green else Sb.Text, bold = true))
                    Text(if (active) "진행 중" else "${formatDuration(start - now)} 후", style = SbText.body(11, Sb.TextFaint))
                }
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    crops.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIcon(WikiIcons.url(c), c, null, 22.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(EventLabels.crop(c), style = SbText.body(13, if (c == crop) Sb.Gold else Sb.Text, FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropChip(text: String, crop: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Sb.Green.copy(alpha = 0.2f) else Sb.SurfaceHigh)
            .border(1.dp, if (selected) Sb.Green else Sb.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (crop != null) { PixelIcon(WikiIcons.url(crop), crop, null, 18.dp); Spacer(Modifier.width(4.dp)) }
        Text(text, style = SbText.body(12, if (selected) Sb.Green else Sb.TextDim))
    }
}
