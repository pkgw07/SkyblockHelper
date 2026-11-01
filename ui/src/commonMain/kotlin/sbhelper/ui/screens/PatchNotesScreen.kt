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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.DocBlock
import sbhelper.core.api.PatchNote
import sbhelper.core.api.WikiApi
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.Pill
import sbhelper.ui.components.RemoteImage
import sbhelper.ui.components.SbButton
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 패치노트 화면 상태 (화면을 나갔다 와도 유지) */
class PatchNotesState {
    var selected by mutableStateOf<String?>(null)
    val bodies = mutableStateMapOf<String, List<DocBlock>>()
    val errors = mutableStateMapOf<String, String>()
    /** 페이지 → [제목, 블록1, 블록2 …] 한국어 번역 */
    val translated = mutableStateMapOf<String, List<String>>()
    var showOriginal by mutableStateOf(false)
}

private fun DocBlock.text(): String = when (this) {
    is DocBlock.Heading -> text
    is DocBlock.Paragraph -> text
    is DocBlock.Bullet -> text
    is DocBlock.Image -> ""
}

@Composable
fun PatchNotesScreen(controller: AppController, state: AppState, wide: Boolean) {
    val s = controller.patchNotes
    val notes = state.patchNotes
    LaunchedEffect(Unit) { if (notes.isEmpty()) runCatching { controller.repo.refreshPatchNotes() } }
    if (s.selected == null && wide) s.selected = notes.firstOrNull()?.page
    val selected = notes.firstOrNull { it.page == s.selected }

    Row(Modifier.fillMaxSize().padding(horizontal = if (wide) 28.dp else 14.dp).padding(bottom = 20.dp, top = if (wide) 4.dp else 10.dp)) {
        if (wide || selected == null) {
            Column(Modifier.let { if (wide) it.width(360.dp) else it.fillMaxWidth() }.fillMaxHeight()) {
                Text("공식 Hypixel SkyBlock 위키 Changelog 기준 · 새 패치가 올라오면 알림을 보내요", style = SbText.body(12, Sb.TextFaint))
                Spacer(Modifier.height(10.dp))
                if (notes.isEmpty()) {
                    EmptyState("패치노트를 불러오는 중이에요", "공식 위키에서 가져오고 있어요.", icon = Icons.Rounded.Newspaper)
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notes, key = { it.page }) { n -> NoteCard(n, n.page == s.selected) { s.selected = n.page } }
                }
            }
        }
        if (selected != null) {
            if (wide) Spacer(Modifier.width(18.dp))
            NoteDetail(controller, selected, wide) { s.selected = null }
        }
    }
}

@Composable
private fun NoteCard(n: PatchNote, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (active) Sb.SurfaceHover else Sb.Surface)
            .border(1.dp, if (active) Sb.Gold.copy(alpha = 0.5f) else Sb.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${n.year}. ${n.date}", style = SbText.pixel(11, Sb.TextFaint), modifier = Modifier.weight(1f))
            if (n.alpha) Pill("알파 서버", Sb.Pink) else Pill("정식 서버", Sb.Green)
        }
        Spacer(Modifier.height(6.dp))
        Text(n.title.replace(Regex("""^\[[^\]]+]\s*"""), ""), style = SbText.body(14, weight = FontWeight.SemiBold), maxLines = 2)
        if (n.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(n.description, style = SbText.body(12, Sb.TextDim), maxLines = 3)
        }
    }
}

@Composable
private fun NoteDetail(controller: AppController, n: PatchNote, wide: Boolean, onBack: () -> Unit) {
    val s = controller.patchNotes
    val platform = LocalPlatform.current
    LaunchedEffect(n.page) {
        if (n.page !in s.bodies) {
            runCatching { controller.repo.wiki.page(n.page) }
                .onSuccess { s.bodies[n.page] = it }
                .onFailure { s.errors[n.page] = it.message ?: "불러오지 못했어요" }
        }
    }
    val blocks = s.bodies[n.page]
    val translateOn = controller.repo.state.value.device.translatePatchNotes
    LaunchedEffect(n.page, blocks, translateOn) {
        if (translateOn && blocks != null && n.page !in s.translated) {
            val lines = listOf(n.title) + blocks.map { it.text() }
            runCatching { controller.repo.translatePatch(n.page, lines) }.getOrNull()
                ?.takeIf { it != lines }?.let { s.translated[n.page] = it }
        }
    }
    val tr = s.translated[n.page]?.takeIf { translateOn && !s.showOriginal }
    LazyColumn(
        Modifier.fillMaxHeight().fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
            .border(1.dp, Sb.Border, RoundedCornerShape(14.dp)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (wide) 26.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (!wide) {
                Row(Modifier.clickable(onClick = onBack).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Sb.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("목록", style = SbText.body(13, Sb.TextDim))
                }
            }
            Text("${n.year}. ${n.date}" + if (n.alpha) " · 알파 서버" else "", style = SbText.caption(if (n.alpha) Sb.Pink else Sb.Green))
            Spacer(Modifier.height(6.dp))
            Text(tr?.getOrNull(0) ?: n.title, style = SbText.pixel(if (wide) 20 else 16, Sb.Text, bold = true))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (n.link.isNotBlank()) SbButton("공식 포럼 원문", { platform.openUrl(n.link) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true)
                SbButton("위키에서 보기", { platform.openUrl(WikiApi.pageUrl(n.page)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true, color = Sb.Aqua)
                if (translateOn && s.translated[n.page] != null) {
                    SbButton(if (s.showOriginal) "번역 보기" else "원문 보기", { s.showOriginal = !s.showOriginal }, outlined = true, color = Sb.Pink)
                }
            }
            if (translateOn) {
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        blocks != null && s.translated[n.page] == null -> "번역 중… (Google 번역, 비공식)"
                        tr != null -> "Google 번역(비공식)으로 옮긴 글이에요. 정확한 내용은 원문을 확인하세요."
                        else -> ""
                    }, style = SbText.body(11, Sb.TextFaint),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            blocks == null && s.errors[n.page] != null -> item { Text("⚠ ${s.errors[n.page]}", style = SbText.body(13, Sb.Negative)) }
            blocks == null -> item { Text("본문을 불러오는 중…", style = SbText.body(13, Sb.TextFaint)) }
            else -> items(blocks.size) { i -> Block(blocks[i], tr?.getOrNull(i + 1)) }
        }
    }
}

@Composable
private fun Block(b: DocBlock, translated: String? = null) {
    when (b) {
        is DocBlock.Heading -> Text(translated ?: b.text, Modifier.padding(top = if (b.level == 1) 12.dp else 6.dp),
            style = SbText.pixel(if (b.level == 1) 16 else 14, if (b.level == 1) Sb.Gold else Sb.Yellow, bold = true))
        is DocBlock.Paragraph -> Text(translated ?: b.text, style = SbText.body(14, Sb.TextDim))
        is DocBlock.Bullet -> Row(Modifier.padding(start = (18 * (b.depth - 1)).dp)) {
            Text(if (b.depth == 1) "•" else "–", style = SbText.body(14, Sb.Gold), modifier = Modifier.width(16.dp))
            Text(translated ?: b.text, style = SbText.body(14, Sb.Text))
        }
        is DocBlock.Image -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            RemoteImage(b.url, Modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
}
