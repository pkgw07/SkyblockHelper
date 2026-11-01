package sbhelper.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import sbhelper.core.AppState
import sbhelper.core.calc.EventLabels
import sbhelper.core.calc.SkyblockCalendar
import sbhelper.core.notify.AlertCategory
import sbhelper.core.util.formatDuration
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.XpBar
import sbhelper.ui.components.rememberNow
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

fun localTime(millis: Long, withDate: Boolean = false): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    val hm = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    return if (withDate) "${t.monthNumber}/${t.dayOfMonth} $hm" else hm
}

@Composable
fun TimersScreen(controller: AppController, state: AppState, wide: Boolean) {
    val now = rememberNow()
    ScreenColumn(wide) {
        Responsive(
            wide,
            cell(1f) { m -> ForgeDetail(state, now, m) },
            cell(1f) { m -> EventSchedule(controller, state, now, m) },
        )
        HistoryPanel(controller, state)
    }
}

@Composable
private fun ForgeDetail(state: AppState, now: Long, modifier: Modifier) {
    val slots = state.forgeStatuses()
    val p = state.selectedProfile
    Panel(modifier, caption = "Forge", title = "포지 슬롯", accent = Sb.Gold,
        trailing = { if (state.synced.notify.forge) Pill("완료 알림 켜짐", Sb.Green) else Pill("알림 꺼짐", Sb.TextFaint) }) {
        if (slots.isEmpty()) {
            EmptyState("진행 중인 포지 없음", if (state.accountLinked) "포지에 아이템을 넣으면 완료 시각과 알림이 자동으로 잡혀요." else "계정을 연결해 주세요.")
            return@Panel
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            slots.forEach { slot ->
                val done = slot.isDone(now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemGlyph(slot.itemName, state.itemTier(slot.process.itemId), 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("슬롯 ${slot.process.slot} · ${slot.itemName}", style = SbText.body(14, weight = FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        XpBar(slot.progress(now) ?: 0.0, Modifier.fillMaxWidth(), if (done) Sb.Green else Sb.Gold)
                        Spacer(Modifier.height(3.dp))
                        val end = slot.endTime
                        Text(
                            when {
                                end == null -> "이 아이템의 포지 시간 정보가 없어요"
                                done -> "완료됨 · ${localTime(end, true)}"
                                else -> "${formatDuration(end - now)} 남음 · ${localTime(end, true)} 완료 예정"
                            },
                            style = SbText.body(12, if (done) Sb.Green else Sb.TextFaint),
                        )
                    }
                }
            }
            if (p != null) {
                Text(
                    "Quick Forge 레벨 ${p.quickForgeLevel} · 시장 퍼크 반영 (위키 The Forge 기준, 감소 효과는 더해서 계산)",
                    style = SbText.body(11, Sb.TextFaint),
                )
            }
        }
    }
}

@Composable
private fun EventSchedule(controller: AppController, state: AppState, now: Long, modifier: Modifier) {
    val events = state.upcomingEvents(now)
    val prefs = state.synced.notify
    val date = SkyblockCalendar.dateAt(now)
    Panel(modifier, caption = "Calendar · Year ${date.year}", title = "이벤트 일정", accent = Sb.Aqua,
        trailing = { Pill("${prefs.eventLeadMinutes}분 전 알림", Sb.Aqua) }) {
        Text("🔔 을 눌러 이벤트별 알림을 켜고 끌 수 있어요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(8.dp))
        events.forEach { e ->
            val active = e.isActive(now)
            val on = prefs.eventEnabled(e.name)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) Sb.Aqua.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(EventLabels.emoji(e.name), style = SbText.body(16))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(EventLabels.label(e.name), style = SbText.body(14, weight = FontWeight.Medium))
                    state.cropsAt(e.start)?.let { CropLine(it) }
                    Text(
                        if (active) "진행 중 · ${formatDuration(e.end - now)} 남음"
                        else "${formatDuration(e.start - now)} 후 · ${localTime(e.start, true)}",
                        style = SbText.body(12, if (active) Sb.Aqua else Sb.TextFaint),
                    )
                }
                Icon(
                    if (on) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone, "알림",
                    tint = if (on) Sb.Gold else Sb.TextFaint,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        controller.repo.updateSynced { s -> s.copy(notify = s.notify.copy(events = s.notify.events + (e.name to !on))) }
                    }.padding(6.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(controller: AppController, state: AppState) {
    val history = state.stored.history
    Panel(Modifier.fillMaxWidth(), caption = "History", title = "알림 기록", accent = Sb.Pink,
        trailing = {
            if (history.isNotEmpty()) Icon(Icons.Rounded.DeleteSweep, "기록 지우기", tint = Sb.TextFaint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { controller.repo.clearHistory() }.padding(6.dp).size(20.dp))
        }) {
        if (history.isEmpty()) {
            EmptyState("아직 알림이 없어요", "포지 완료, 경매 판매, 이벤트, 가격 알림이 여기에 쌓여요.", icon = Icons.Rounded.NotificationsOff)
            return@Panel
        }
        history.take(50).forEach { h ->
            val cat = runCatching { AlertCategory.valueOf(h.category) }.getOrDefault(AlertCategory.SYSTEM)
            val color = when (cat) {
                AlertCategory.FORGE -> Sb.Gold
                AlertCategory.EVENT -> Sb.Aqua
                AlertCategory.AUCTION -> Sb.Green
                AlertCategory.PRICE -> Sb.Pink
                AlertCategory.PATCH -> Sb.Green
                AlertCategory.SYSTEM -> Sb.TextDim
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = SbText.body(14, weight = FontWeight.SemiBold))
                    Text(h.body, style = SbText.body(12, Sb.TextDim))
                }
                Text(localTime(h.time, true), style = SbText.pixel(11, Sb.TextFaint))
            }
        }
    }
}
