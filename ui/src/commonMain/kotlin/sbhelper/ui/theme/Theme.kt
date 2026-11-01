package sbhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import sbhelper.ui.generated.Res
import sbhelper.ui.generated.galmuri11
import sbhelper.ui.generated.galmuri11_bold
import sbhelper.ui.generated.pretendard_bold
import sbhelper.ui.generated.pretendard_medium
import sbhelper.ui.generated.pretendard_regular
import sbhelper.ui.generated.pretendard_semibold

/**
 * 게임 런처(Lunar Client, Modrinth App) 느낌의 어두운 테마 +
 * 마인크래프트 색상(§ 코드)을 강조색으로 쓴다.
 */
@Immutable
object Sb {
    val Window = Color(0xFF08090D)
    val Rail = Color(0xFF0B0C11)
    val Background = Color(0xFF0F1117)
    val Surface = Color(0xFF151821)
    val SurfaceHigh = Color(0xFF1C202B)
    val SurfaceHover = Color(0xFF232837)
    val Border = Color(0xFF262B38)
    val BorderStrong = Color(0xFF353B4C)
    val Text = Color(0xFFE9EAF0)
    val TextDim = Color(0xFFA2A8BA)
    val TextFaint = Color(0xFF666D82)

    // 마인크래프트 색상
    val Gold = Color(0xFFFFAA00)
    val Yellow = Color(0xFFFFFF55)
    val Green = Color(0xFF55FF55)
    val DarkGreen = Color(0xFF00AA00)
    val Aqua = Color(0xFF55FFFF)
    val DarkAqua = Color(0xFF00AAAA)
    val Blue = Color(0xFF5555FF)
    val Purple = Color(0xFFAA00AA)
    val Pink = Color(0xFFFF55FF)
    val Red = Color(0xFFFF5555)
    val DarkRed = Color(0xFFAA0000)
    val Gray = Color(0xFFAAAAAA)

    val Accent = Gold
    val Positive = Color(0xFF4ADE80)
    val Negative = Color(0xFFF87171)

    // 마인크래프트 툴팁
    val TooltipBg = Color(0xF0100010)
    val TooltipBorderTop = Color(0xFF5000FF)
    val TooltipBorderBottom = Color(0xFF28007F)
}

@Immutable
data class SbFonts(val body: FontFamily, val pixel: FontFamily)

val LocalFonts = staticCompositionLocalOf<SbFonts> { error("fonts not provided") }

@Composable
fun rememberFonts(): SbFonts = SbFonts(
    body = FontFamily(
        Font(Res.font.pretendard_regular, FontWeight.Normal),
        Font(Res.font.pretendard_medium, FontWeight.Medium),
        Font(Res.font.pretendard_semibold, FontWeight.SemiBold),
        Font(Res.font.pretendard_bold, FontWeight.Bold),
    ),
    pixel = FontFamily(
        Font(Res.font.galmuri11, FontWeight.Normal),
        Font(Res.font.galmuri11_bold, FontWeight.Bold),
    ),
)

/** 자주 쓰는 글자 스타일 */
/** 글자 크기 배율 (설정 → 글자 크기). 1.0 = 작게, 1.15 = 보통(기본), 1.3 = 크게 */
val LocalTextScale = staticCompositionLocalOf { 1.15f }

/** 글자 굵기 (설정): 0 = 기본, 1 = 굵게, 2 = 아주 굵게 — 글꼴은 그대로, 같은 글꼴의 더 두꺼운 굵기를 쓴다 */
val LocalTextWeight = staticCompositionLocalOf { 1 }

private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** 설정 굵기만큼 한두 단계 더 두껍게 */
private fun heavier(w: FontWeight, step: Int): FontWeight {
    val i = WEIGHTS.indexOfFirst { it.weight >= w.weight }.let { if (it < 0) WEIGHTS.lastIndex else it }
    return WEIGHTS[(i + step).coerceAtMost(WEIGHTS.lastIndex)]
}

object SbText {
    /** 크기를 정수로 맞춰서 픽셀 글꼴이 뭉개지지 않게 한다 */
    @Composable private fun scaled(size: Int): Int = kotlin.math.round(size * LocalTextScale.current).toInt()

    /** 밝은 글자에는 마인크래프트처럼 아래·오른쪽 그림자를 줘서 또렷하게 */
    @Composable private fun pixelShadow(color: Color): androidx.compose.ui.graphics.Shadow? {
        val step = LocalTextWeight.current
        if (step <= 0 || color.luminance() < 0.25f) return null
        return androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (step >= 2) 0.85f else 0.6f),
            androidx.compose.ui.geometry.Offset(1.2f, 1.2f), 0f)
    }

    @Composable fun pixel(size: Int = 12, color: Color = Sb.Text, bold: Boolean = false): TextStyle {
        val s = scaled(size)
        // 픽셀 글꼴은 보통/굵게 두 가지뿐 → "아주 굵게"에서는 굵은 글꼴
        val b = bold || LocalTextWeight.current >= 2
        return TextStyle(fontFamily = LocalFonts.current.pixel, fontSize = s.sp, color = color,
            fontWeight = if (b) FontWeight.Bold else FontWeight.Normal, lineHeight = kotlin.math.ceil(s * 1.35).sp,
            shadow = pixelShadow(color))
    }

    @Composable fun body(size: Int = 14, color: Color = Sb.Text, weight: FontWeight = FontWeight.Normal): TextStyle {
        val s = scaled(size)
        return TextStyle(fontFamily = LocalFonts.current.body, fontSize = s.sp, color = color,
            fontWeight = heavier(weight, LocalTextWeight.current), lineHeight = kotlin.math.ceil(s * 1.45).sp)
    }

    @Composable fun caption(color: Color = Sb.TextFaint) =
        TextStyle(fontFamily = LocalFonts.current.pixel, fontSize = scaled(11).sp, color = color, letterSpacing = 0.5.sp,
            fontWeight = if (LocalTextWeight.current >= 2) FontWeight.Bold else FontWeight.Normal)
}

@Composable
fun SkyblockTheme(textScale: Float = 1.15f, textWeight: Int = 1, content: @Composable () -> Unit) {
    val fonts = rememberFonts()
    val scheme = darkColorScheme(
        primary = Sb.Gold,
        onPrimary = Color(0xFF1A1200),
        secondary = Sb.Aqua,
        background = Sb.Background,
        onBackground = Sb.Text,
        surface = Sb.Surface,
        onSurface = Sb.Text,
        surfaceVariant = Sb.SurfaceHigh,
        onSurfaceVariant = Sb.TextDim,
        outline = Sb.Border,
        error = Sb.Negative,
    )
    val base = MaterialTheme.typography
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts.body),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts.body),
        bodySmall = base.bodySmall.copy(fontFamily = fonts.body),
        labelLarge = base.labelLarge.copy(fontFamily = fonts.body),
        labelMedium = base.labelMedium.copy(fontFamily = fonts.body),
        labelSmall = base.labelSmall.copy(fontFamily = fonts.body),
        titleMedium = base.titleMedium.copy(fontFamily = fonts.body),
        titleSmall = base.titleSmall.copy(fontFamily = fonts.body),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalFonts provides fonts, LocalTextScale provides textScale, LocalTextWeight provides textWeight) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
