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

/**
 * 위키 문서 본문. 파란 글자(링크)를 누르면 그 문서/아이템으로 이동한다.
 * @param sections 이 제목들의 절만 (null = 전부)
 */
@Composable
fun WikiBlocks(controller: AppController, blocks: List<DocBlock>, sections: Set<String>? = null, max: Int = 200) {
    val onLink: (String) -> Unit = { page -> controller.openWikiLink(page) }
    var current: String? = null
    var shown = 0
    // 제목만 있고 내용이 없는 절은 숨긴다
    val nonEmpty = blocks.indices.filter { i ->
        val b = blocks[i]
        b !is DocBlock.Heading || blocks.getOrNull(i + 1)?.let { it !is DocBlock.Heading || it.level > b.level } == true
    }.toSet()
    Column {
        blocks.forEachIndexed { i, b ->
            if (b is DocBlock.Heading && b.level <= 1) current = b.text
            if (sections != null && current !in sections && !(b is DocBlock.Heading && b.text in sections)) return@forEachIndexed
            if (i !in nonEmpty || shown >= max) return@forEachIndexed
            if (b is DocBlock.Heading && b.text in SKIP_SECTIONS) return@forEachIndexed
            if (current in SKIP_SECTIONS) return@forEachIndexed
            shown++
            when (b) {
                is DocBlock.Heading -> Text(linkedText(b.text, b.links, onLink),
                    Modifier.padding(top = if (b.level == 1) 12.dp else 6.dp, bottom = 2.dp),
                    style = SbText.body(if (b.level == 1) 16 else 14, Sb.Gold, FontWeight.Bold))
                is DocBlock.Paragraph -> Text(linkedText(b.text, b.links, onLink), Modifier.padding(vertical = 3.dp),
                    style = SbText.body(14, Sb.TextDim))
                is DocBlock.Bullet -> Row(Modifier.padding(start = ((b.depth - 1) * 16).dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•", style = SbText.body(14, Sb.Gold))
                    Spacer(Modifier.width(8.dp))
                    Text(linkedText(b.text, b.links, onLink), style = SbText.body(14, Sb.Text))
                }
                is DocBlock.Image -> Unit
            }
        }
    }
}

/** 앱 안에서 보여 줄 필요가 없는 절 */
private val SKIP_SECTIONS = setOf("History", "Navigation", "Gallery", "Trivia", "References", "Dialogue", "Interface")
