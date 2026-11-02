package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
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
import sbhelper.core.AppState
import sbhelper.ui.AppController
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/**
 * 프로필·기록·인벤토리·테크트리 위에 붙는 "다른 플레이어 보기" 줄.
 * 다른 플레이어를 보는 중이면 화면에는 그 사람의 프로필이 들어간 상태를 넘긴다 (읽기 전용, 내 설정은 그대로).
 */
@Composable
fun PlayerViewFrame(controller: AppController, state: AppState, wide: Boolean, content: @Composable (AppState) -> Unit) {
    val v = controller.viewing
    val shown = remember(state, v) { if (v != null) state.viewAs(v) else state }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = contentMaxWidth(LocalContentWidth.current)).fillMaxWidth().padding(horizontal = if (wide) 28.dp else 14.dp)) {
                if (v == null) LookupRow(controller) else ViewingBanner(controller, v)
            }
        }
        Box(Modifier.weight(1f)) { content(shown) }
    }
}

@Composable
private fun LookupRow(controller: AppController) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SbTextField(name, { name = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) }, Modifier.weight(1f),
            "다른 플레이어 닉네임", Icons.Rounded.PersonSearch, onSubmit = { if (!controller.viewingBusy) controller.viewPlayer(name) })
        Spacer(Modifier.width(8.dp))
        SbButton(if (controller.viewingBusy) "찾는 중…" else "보기", { controller.viewPlayer(name) }, outlined = true, color = Sb.Aqua)
    }
    controller.viewingError?.let { Text("⚠ $it", style = SbText.body(12, Sb.Negative), modifier = Modifier.padding(top = 4.dp)) }
}

@Composable
private fun ViewingBanner(controller: AppController, v: sbhelper.core.ViewedPlayer) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Sb.Aqua.copy(alpha = 0.10f))
            .border(1.dp, Sb.Aqua.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👤 ${v.name} 님 프로필 보는 중", style = SbText.body(14, Sb.Aqua, FontWeight.Bold), modifier = Modifier.weight(1f).padding(end = 6.dp), maxLines = 2)
            if (controller.viewingBusy) Text("불러오는 중… ", style = SbText.body(12, Sb.TextDim))
            SbButton("내 프로필로", { controller.stopViewing() }, outlined = true, color = Sb.Gold)
        }
        // 그 사람의 프로필 (코옵·아이언맨 등)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            v.profiles.forEach { p ->
                val on = p.profileId == v.selectedId
                Text(
                    p.cuteName + (if (p.gameMode != sbhelper.core.model.GameMode.NORMAL) " · " + p.gameMode.badge + p.gameMode.label else "") + if (p.selected) " ★" else "",
                    style = SbText.body(13, if (on) Sb.Text else Sb.TextDim, if (on) FontWeight.SemiBold else FontWeight.Normal),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Sb.SurfaceHover else Sb.Surface)
                        .clickable { controller.pickProfile(p.profileId) }.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
