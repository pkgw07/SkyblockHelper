package sbhelper.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import sbhelper.core.util.percentColorsToSection

/** 마인크래프트 § 색상 코드 */
object McColors {
    val codes = mapOf(
        '0' to Color(0xFF000000), '1' to Color(0xFF0000AA), '2' to Color(0xFF00AA00), '3' to Color(0xFF00AAAA),
        '4' to Color(0xFFAA0000), '5' to Color(0xFFAA00AA), '6' to Color(0xFFFFAA00), '7' to Color(0xFFAAAAAA),
        '8' to Color(0xFF555555), '9' to Color(0xFF5555FF), 'a' to Color(0xFF55FF55), 'b' to Color(0xFF55FFFF),
        'c' to Color(0xFFFF5555), 'd' to Color(0xFFFF55FF), 'e' to Color(0xFFFFFF55), 'f' to Color(0xFFFFFFFF),
    )

    /** 아이템 등급 색 */
    fun rarity(tier: String?): Color = when (tier?.uppercase()) {
        "COMMON" -> Color(0xFFFFFFFF)
        "UNCOMMON" -> Color(0xFF55FF55)
        "RARE" -> Color(0xFF5555FF)
        "EPIC" -> Color(0xFFAA00AA)
        "LEGENDARY" -> Color(0xFFFFAA00)
        "MYTHIC" -> Color(0xFFFF55FF)
        "DIVINE" -> Color(0xFF55FFFF)
        "SPECIAL", "VERY_SPECIAL" -> Color(0xFFFF5555)
        "SUPREME" -> Color(0xFFAA0000)
        "ULTIMATE" -> Color(0xFFAA0000)
        else -> Sb.TextDim
    }

    fun rarityLabel(tier: String?): String = tier?.replace('_', ' ') ?: ""

    /**
     * § 코드가 섞인 문자열을 색이 입혀진 글자로 바꾼다.
     * 예) "§6Legendary §lSword" -> 금색 Legendary + 굵은 Sword
     */
    fun format(text: String, default: Color = Color(0xFFAAAAAA)): AnnotatedString = buildAnnotatedString {
        // 위키 글은 &7 처럼 & 를 쓴다 → § 로 바꿔서 색으로 표시 (글자로 새지 않게)
        val src = text.percentColorsToSection().replace(Regex("&([0-9a-fk-orA-FK-OR])"), "§$1")
        var color = default
        var bold = false
        var italic = false
        var strike = false
        var underline = false
        var i = 0
        val sb = StringBuilder()
        fun flush() {
            if (sb.isEmpty()) return
            val deco = listOfNotNull(
                if (strike) TextDecoration.LineThrough else null,
                if (underline) TextDecoration.Underline else null,
            )
            pushStyle(SpanStyle(
                color = color,
                fontWeight = if (bold) FontWeight.Bold else null,
                fontStyle = if (italic) FontStyle.Italic else null,
                textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
            ))
            append(sb.toString())
            pop()
            sb.clear()
        }
        while (i < src.length) {
            val c = src[i]
            if (c == '§' && i + 1 < src.length) {
                flush()
                val code = src[i + 1].lowercaseChar()
                when {
                    codes.containsKey(code) -> { color = codes.getValue(code); bold = false; italic = false; strike = false; underline = false }
                    code == 'l' -> bold = true
                    code == 'o' -> italic = true
                    code == 'm' -> strike = true
                    code == 'n' -> underline = true
                    code == 'r' -> { color = default; bold = false; italic = false; strike = false; underline = false }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        flush()
    }
}
