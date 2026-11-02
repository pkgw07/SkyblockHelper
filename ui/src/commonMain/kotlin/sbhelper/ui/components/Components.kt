package sbhelper.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sbhelper.core.util.Clock
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 1초(기본)마다 바뀌는 현재 시각 */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = Clock.now()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

/** 카드. 상단에 픽셀 글꼴 캡션 + 제목 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    caption: String? = null,
    accent: Color = Sb.Accent,
    trailing: @Composable RowScope.() -> Unit = {},
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Sb.Surface)
            .border(1.dp, Sb.Border, RoundedCornerShape(14.dp))
            .padding(padding),
    ) {
        if (title != null || caption != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    if (caption != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(accent))
                            Spacer(Modifier.width(6.dp))
                            Text(caption.uppercase(), style = SbText.caption(accent.copy(alpha = 0.9f)))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (title != null) Text(title, style = SbText.body(17, weight = FontWeight.Bold))
                }
                trailing()
            }
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

/** 큰 숫자 하나를 보여 주는 타일 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null,
             color: Color = Sb.Text, icon: ImageVector? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Sb.SurfaceHigh, Sb.Surface)))
            .border(1.dp, Sb.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = SbText.caption(Sb.TextDim))
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = SbText.pixel(22, color, bold = true), maxLines = 1)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, style = SbText.body(12, Sb.TextFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 마인크래프트 경험치 바처럼 생긴 진행 막대 */
@Composable
fun XpBar(progress: Double, modifier: Modifier = Modifier, color: Color = Sb.Green, height: Dp = 8.dp) {
    val animated by animateFloatAsState(progress.toFloat().coerceIn(0f, 1f), tween(600))
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0A0B0F))
            .border(1.dp, Color(0xFF000000), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(animated)
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f)))),
        )
        // 게임 경험치 바처럼 칸 나누기
        Box(Modifier.matchParentSize().drawBehind {
            val segments = 20
            for (i in 1 until segments) {
                val x = size.width * i / segments
                drawLine(Color(0x55000000), Offset(x, 0f), Offset(x, size.height), 1f)
            }
        })
    }
}

/** 스킬 한 줄: 아이콘 + 이름 + 레벨 + 경험치 바 (SkyCrypt 스타일) */
@Composable
fun LevelRow(
    name: String, level: Int, maxLevel: Int, progress: Double, detail: String,
    color: Color, icon: ImageVector, modifier: Modifier = Modifier,
) {
    val maxed = level >= maxLevel
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = if (maxed) 0.9f else 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, style = SbText.body(14, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text(if (maxed) "MAX $level" else "$level", style = SbText.pixel(13, if (maxed) Sb.Gold else Sb.Text, bold = true))
                Text(" / $maxLevel", style = SbText.pixel(11, Sb.TextFaint))
            }
            Spacer(Modifier.height(5.dp))
            XpBar(if (maxed) 1.0 else progress, Modifier.fillMaxWidth(), if (maxed) Sb.Gold else color)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = SbText.body(11, Sb.TextFaint))
        }
    }
}

/** 마인크래프트 아이템 툴팁 모양의 상자 */
@Composable
fun McTooltip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Sb.TooltipBg, RoundedCornerShape(3.dp))
            .border(2.dp, Brush.verticalGradient(listOf(Sb.TooltipBorderTop, Sb.TooltipBorderBottom)), RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** § 코드가 섞인 문자열을 그대로 색칠해서 보여 준다 */
@Composable
fun McText(text: String, modifier: Modifier = Modifier, size: Int = 13, default: Color = Sb.Gray, maxLines: Int = Int.MAX_VALUE) {
    Text(McColors.format(text, default), modifier, style = SbText.pixel(size, default), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis)
}

@Composable
fun RarityName(name: String, tier: String?, modifier: Modifier = Modifier, size: Int = 14, bold: Boolean = false) {
    Text(name, modifier, style = SbText.body(size, McColors.rarity(tier), if (bold) FontWeight.SemiBold else FontWeight.Medium),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 아이템 아이콘 자리: 등급 색 테두리의 정사각형 + 첫 글자 */
@Composable
fun ItemGlyph(name: String, tier: String?, size: Dp = 32.dp) {
    val c = McColors.rarity(tier)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.06f))))
            .border(1.dp, c.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", style = SbText.pixel((size.value * 0.42f).toInt(), c, bold = true))
    }
}

@Composable
fun Pill(text: String, color: Color = Sb.TextDim, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, style = SbText.pixel(11, color)) }
}

/** 금색 픽셀 버튼 (런처의 PLAY 버튼 느낌) */
@Composable
fun SbButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: ImageVector? = null, color: Color = Sb.Gold, outlined: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            !enabled -> Sb.SurfaceHigh
            outlined -> if (hovered) color.copy(alpha = 0.12f) else Color.Transparent
            hovered -> color.copy(alpha = 0.9f)
            else -> color
        },
    )
    val fg = when {
        !enabled -> Sb.TextFaint
        outlined -> color
        else -> Color(0xFF15100A)
    }
    Row(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (outlined) color.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
            .hoverable(source)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = SbText.body(14, fg, FontWeight.Bold))
    }
}

/** 어두운 입력 칸 */
@Composable
fun SbTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", leading: ImageVector? = null, secret: Boolean = false,
    numeric: Boolean = false, singleLine: Boolean = true, trailing: @Composable (() -> Unit)? = null,
    /** 엔터(휴대폰 키보드의 검색/완료 키)를 누르면 실행 */
    onSubmit: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = SbText.body(14),
        cursorBrush = SolidColor(Sb.Gold),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (onSubmit != null) androidx.compose.ui.text.input.ImeAction.Search else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit?.invoke() }),
        modifier = if (onSubmit == null) modifier else modifier.onPreviewKeyEvent { e ->
            // PC: 엔터 키
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                (e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.NumPadEnter)) {
                onSubmit(); true
            } else false
        },
        decorationBox = { inner ->
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Sb.Window)
                    .border(1.dp, Sb.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = Sb.TextFaint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = SbText.body(14, Sb.TextFaint), maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled) { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SbText.body(14, if (enabled) Sb.Text else Sb.TextFaint, FontWeight.Medium))
            if (description != null) Text(description, style = SbText.body(12, Sb.TextFaint))
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF15100A), checkedTrackColor = Sb.Gold,
                uncheckedThumbColor = Sb.TextDim, uncheckedTrackColor = Sb.Window, uncheckedBorderColor = Sb.BorderStrong,
            ),
        )
    }
}

/** 탭처럼 생긴 선택 버튼 묶음 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // 한 줄로만: 칸이 모자라면(휴대폰) 글자를 줄바꿈하지 않고 옆으로 밀어서 보게
    val scroll = rememberScrollState()
    val lefts = remember { HashMap<Int, Pair<Int, Int>>() } // 칸 번호 → (왼쪽 x, 너비)
    var viewport by remember { mutableStateOf(0) }
    // 고른 칸이 화면 밖이면 보이게 옮긴다 (예: 휴대폰에서 뒤쪽 탭을 골라 둔 채로 돌아왔을 때)
    LaunchedEffect(selected, viewport) {
        val (x, w) = lefts[selected] ?: return@LaunchedEffect
        if (viewport <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + viewport) scroll.scrollTo((x - (viewport - w) / 2).coerceAtLeast(0))
    }
    Box(modifier.onSizeChanged { viewport = it.width }.horizontalScroll(scroll)) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Sb.Window).border(1.dp, Sb.Border, RoundedCornerShape(12.dp))
                .padding(4.dp),
        ) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Box(
                    Modifier.onGloballyPositioned { c -> lefts[i] = (c.positionInParent().x.toInt() + 4) to c.size.width }
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) Sb.SurfaceHover else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label, style = SbText.body(13, if (active) Sb.Text else Sb.TextDim, if (active) FontWeight.SemiBold else FontWeight.Normal),
                        maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Inbox,
               action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Sb.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Sb.TextFaint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = SbText.body(15, weight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(description, style = SbText.body(13, Sb.TextFaint))
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** 표의 한 줄 (마우스를 올리면 밝아짐) */
@Composable
fun TableRow(onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) Sb.SurfaceHover else if (hovered) Sb.SurfaceHigh else Color.Transparent)
            .hoverable(source)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text, modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        style = SbText.caption(if (active) Sb.Gold else Sb.TextFaint), maxLines = 1,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Sb.Border))
}

@Composable
fun annotated(text: String) = AnnotatedString(text)
