package sbhelper.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.rounded.Translate
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import sbhelper.core.api.DocBlock
import sbhelper.core.api.WikiLink
import sbhelper.ui.AppController
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 위키 문서 본문 캐시 (화면을 옮겨도 다시 받지 않게) */
object WikiPages {
    val bodies = mutableStateMapOf<String, List<DocBlock>>()
    val errors = mutableStateMapOf<String, String>()
    /** 문서별 번역 (블록 순서와 같음) */
    val translated = mutableStateMapOf<String, List<String>>()
    /** 번역을 보여 주는 중인 문서 */
    val showTranslated = mutableStateMapOf<String, Boolean>()
    val translating = mutableStateMapOf<String, Boolean>()
}

private fun DocBlock.plain(): String = when (this) {
    is DocBlock.Heading -> text
    is DocBlock.Paragraph -> text
    is DocBlock.Bullet -> text
    is DocBlock.Image -> ""
}

/** "번역 / 원문" 버튼. 처음 누르면 번역을 받아 온다 (패치노트와 같은 번역기, 결과는 저장) */
@Composable
fun TranslateButton(controller: AppController, page: String, blocks: List<DocBlock>?) {
    if (blocks == null) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val on = WikiPages.showTranslated[page] == true
    val busy = WikiPages.translating[page] == true
    sbhelper.ui.components.SbButton(
        when { busy -> "번역 중…"; on -> "원문 보기"; else -> "한국어로 번역" },
        {
            if (on) WikiPages.showTranslated[page] = false
            else if (page in WikiPages.translated) WikiPages.showTranslated[page] = true
            else if (!busy) {
                WikiPages.translating[page] = true
                scope.launch {
                    val lines = blocks.map { it.plain() }
                    val t = runCatching { controller.repo.translatePatch("wiki:$page", lines) }.getOrNull()
                    if (t != null) {
                        WikiPages.translated[page] = lines.indices.map { i -> t[i] ?: lines[i] }
                        WikiPages.showTranslated[page] = true
                    } else controller.toast = "번역하지 못했어요. 잠시 뒤 다시 눌러 주세요."
                    WikiPages.translating[page] = false
                }
            }
        },
        icon = androidx.compose.material.icons.Icons.Rounded.Translate, outlined = true, color = Sb.Gold,
    )
}

/** 위키 문서를 받아 온다 (이미 받았으면 그대로) */
@Composable
fun rememberWikiPage(controller: AppController, page: String?): List<DocBlock>? {
    if (page == null) return null
    LaunchedEffect(page) {
        if (page !in WikiPages.bodies) {
            runCatching { controller.repo.wiki.page(page) }
                .onSuccess { WikiPages.bodies[page] = it; WikiPages.errors.remove(page) }
                .onFailure { WikiPages.errors[page] = it.message ?: "불러오지 못했어요" }
        }
    }
    return WikiPages.bodies[page]
}

/** 링크(위키 문서 이름)를 누르면 onLink */
fun linkedText(text: String, links: List<WikiLink>, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    append(text)
    links.forEach { l ->
        if (l.start < l.end && l.end <= text.length) {
            addLink(
                LinkAnnotation.Clickable(l.page, TextLinkStyles(SpanStyle(color = Sb.Aqua, textDecoration = TextDecoration.Underline))) { onLink(l.page) },
                l.start, l.end,
            )
        }
    }
}

/** 위키 절 이름 묶음 */
val OBTAINING_SECTIONS = setOf("Obtaining", "Obtain", "How to Obtain", "Acquisition", "Sources")
val USAGE_SECTIONS = setOf("Usage", "Uses", "Use")

/** 이 절들 안에 내용이 있는지 */
fun hasSections(blocks: List<DocBlock>, sections: Set<String>): Boolean {
    var cur: String? = null
    return blocks.any { b ->
        if (b is DocBlock.Heading && b.level <= 1) { cur = b.text; false }
        else cur in sections && b !is DocBlock.Image
    }
}

/**
 * 위키 문서 본문. 파란 글자(링크)를 누르면 그 문서/아이템으로 이동한다.
 * @param sections 이 절들만 보여 준다 (절 제목은 숨김). null = 전부
 * @param exclude 이 절들은 빼고 보여 준다
 */
@Composable
fun WikiBlocks(controller: AppController, blocks: List<DocBlock>, sections: Set<String>? = null, max: Int = 200,
               exclude: Set<String> = emptySet(), skipSubsections: Set<String> = emptySet(), page: String? = null) {
    val onLink: (String) -> Unit = { p -> controller.openWikiLink(p) }
    // 번역을 켠 문서면 번역문 (링크 없이)
    val tr = page?.let { p -> WikiPages.translated[p]?.takeIf { WikiPages.showTranslated[p] == true && it.size == blocks.size } }
    var current: String? = null
    var sub: String? = null
    var shown = 0
    // 제목만 있고 내용이 없는 절은 숨긴다
    val nonEmpty = blocks.indices.filter { i ->
        val b = blocks[i]
        b !is DocBlock.Heading || blocks.getOrNull(i + 1)?.let { it !is DocBlock.Heading || it.level > b.level } == true
    }.toSet()
    Column {
        blocks.forEachIndexed { i, b ->
            if (b is DocBlock.Heading && b.level <= 1) sub = null
            if (b is DocBlock.Heading && b.level == 2) sub = b.text
            // 앱이 따로 그리는 절(조합법·제작 트리 등)은 뺀다
            if (sub != null && skipSubsections.any { it.equals(sub, true) }) return@forEachIndexed
            if (b is DocBlock.Heading && b.level <= 1) {
                current = b.text
                // 고른 절의 제목은 위에 칸 이름으로 이미 보이므로 숨긴다
                if (sections != null || b.text in exclude) return@forEachIndexed
            }
            if (sections != null && current !in sections) return@forEachIndexed
            if (current in exclude) return@forEachIndexed
            if (i !in nonEmpty || shown >= max) return@forEachIndexed
            if (b is DocBlock.Heading && b.text in SKIP_SECTIONS) return@forEachIndexed
            if (current in SKIP_SECTIONS) return@forEachIndexed
            shown++
            val t = tr?.getOrNull(i)
            when (b) {
                is DocBlock.Heading -> Text(if (t != null) AnnotatedString(t) else linkedText(b.text, b.links, onLink),
                    Modifier.padding(top = if (b.level == 1) 12.dp else 6.dp, bottom = 2.dp),
                    style = SbText.body(if (b.level == 1) 16 else 14, Sb.Gold, FontWeight.Bold))
                is DocBlock.Paragraph -> Text(if (t != null) AnnotatedString(t) else linkedText(b.text, b.links, onLink), Modifier.padding(vertical = 3.dp),
                    style = SbText.body(14, Sb.TextDim))
                is DocBlock.Bullet -> Row(Modifier.padding(start = ((b.depth - 1) * 16).dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•", style = SbText.body(14, Sb.Gold))
                    Spacer(Modifier.width(8.dp))
                    Text(if (t != null) AnnotatedString(t) else linkedText(b.text, b.links, onLink), style = SbText.body(14, Sb.Text))
                }
                is DocBlock.Image -> Unit
            }
        }
    }
}

/** 앱 안에서 보여 줄 필요가 없는 절 */
private val SKIP_SECTIONS = setOf("History", "Navigation", "Gallery", "Trivia", "References", "Dialogue", "Interface")
