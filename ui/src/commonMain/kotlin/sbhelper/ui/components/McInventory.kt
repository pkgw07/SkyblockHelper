package sbhelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import sbhelper.core.AppState
import sbhelper.core.nbt.SbItem
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 공식 위키의 아이템 그림 주소 (파일 이름 = 아이템 이름) */
object WikiIcons {
    private const val BASE = "https://hypixelskyblock.minecraft.wiki/w/Special:FilePath/"
    fun url(name: String): String {
        val n = name.trim().replace(' ', '_')
        val enc = buildString {
            n.encodeToByteArray().forEach { b ->
                val c = b.toInt().toChar()
                if (b >= 0 && (c.isLetterOrDigit() || c in "_-.()'")) append(c)
                else append('%').append(((b.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
            }
        }
        // 작은 크기로 받는다 (움직이는 GIF 는 원본이 수백 KB)
        return "$BASE$enc.png?width=64"
    }

    /** 아이템 → 위키 그림 이름 */
    fun nameFor(state: AppState, item: SbItem): String = when {
        item.pet != null -> item.pet!!.type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) } + " Pet"
        item.skyblockId != null -> state.items[item.skyblockId]?.name?.replace(Regex("§."), "") ?: item.plainName
        else -> item.plainName
    }
}

/** 위키 파일 이름이 아이템 이름과 다를 때: 그 문서의 대표 그림(PageImages)을 찾는다 */
object IconResolver {
    private val wiki by lazy { sbhelper.core.api.WikiApi() }
    private val cache = HashMap<String, String?>()
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun pageImage(title: String): String? {
        val key = title.trim()
        if (key.isEmpty()) return null
        lock.lock(); try { if (cache.containsKey(key)) return cache[key] } finally { lock.unlock() }
        val url = runCatching { wiki.pageImages(listOf(key))[key] }.getOrNull()
        lock.lock(); try { cache[key] = url } finally { lock.unlock() }
        return url
    }

    /** 공식 API 의 머리 스킨 값(base64) → 스킨 그림 주소 */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun skinUrl(value: String?): String? = runCatching {
        val json = kotlin.io.encoding.Base64.decode(value ?: return null).decodeToString()
        Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.replace("http://", "https://")
    }.getOrNull()
}

/**
 * 픽셀 그림을 뭉개지 않고 확대해서 그린다 (마인크래프트 아이콘).
 * 그림 찾는 순서: 위키 파일(아이템 이름) → 위키 문서 대표 그림 → 머리 아이템이면 스킨의 얼굴 → 글자 아이콘
 */
@Composable
fun PixelIcon(url: String, fallbackName: String, tier: String?, size: Dp, skinUrl: String? = null, id: String? = null) {
    // 이스터에그: Hyperion 류 그림을 클릭하면 화면 전체 Implosion
    if (id != null && id.uppercase() in IMPLODE_IDS) {
        Box(Modifier.implodeOnClick(id)) { PixelIconInner(url, fallbackName, tier, size, skinUrl, id) }
    } else PixelIconInner(url, fallbackName, tier, size, skinUrl, id)
}

@Composable
private fun PixelIconInner(url: String, fallbackName: String, tier: String?, size: Dp, skinUrl: String?, id: String?) {
    // 1) 아이콘 묶음 (바로) 2) 위키 3) 위키 문서 대표 그림 4) 머리 스킨 얼굴 5) 글자 아이콘
    val packVersion = LocalIconPackVersion.current
    val packed = remember(id, fallbackName, packVersion) { ImageCache.fromPack(id, fallbackName) }
    if (packed != null) { PixelBitmap(packed, size); return }
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var face by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, skinUrl) {
        var i = ImageCache.load(url)
        if (i == null) IconResolver.pageImage(fallbackName)?.let { i = ImageCache.load(it) }
        if (i == null && skinUrl != null) {
            i = ImageCache.load(skinUrl)
            face = i != null
        }
        img = i
        failed = i == null
    }
    val i = img
    if (i != null) {
        Canvas(Modifier.size(size)) {
            if (face && i.width >= 64) {
                // 스킨 그림(64×64)에서 얼굴(8,8)과 모자 겹(40,8) 부분만
                val s = minOf(this.size.width, this.size.height).toInt()
                val off = IntOffset(((this.size.width - s) / 2).toInt(), ((this.size.height - s) / 2).toInt())
                val unit = i.width / 64
                drawImage(i, IntOffset(8 * unit, 8 * unit), IntSize(8 * unit, 8 * unit), off, IntSize(s, s), filterQuality = FilterQuality.None)
                drawImage(i, IntOffset(40 * unit, 8 * unit), IntSize(8 * unit, 8 * unit), off, IntSize(s, s), filterQuality = FilterQuality.None)
            } else {
                val scale = minOf(this.size.width / i.width, this.size.height / i.height)
                val w = (i.width * scale).toInt()
                val h = (i.height * scale).toInt()
                drawImage(i, IntOffset.Zero, IntSize(i.width, i.height),
                    IntOffset(((this.size.width - w) / 2).toInt(), ((this.size.height - h) / 2).toInt()), IntSize(w, h),
                    filterQuality = FilterQuality.None)
            }
        }
    } else if (failed) {
        ItemGlyph(fallbackName, tier, size)
    } else {
        Box(Modifier.size(size))
    }
}

/** 받아 둔 아이콘 묶음 버전 (바뀌면 아이콘을 다시 찾음) */
val LocalIconPackVersion = androidx.compose.runtime.staticCompositionLocalOf { "" }

@Composable
private fun PixelBitmap(i: ImageBitmap, size: Dp) {
    Canvas(Modifier.size(size)) {
        val scale = minOf(this.size.width / i.width, this.size.height / i.height)
        val w = (i.width * scale).toInt()
        val h = (i.height * scale).toInt()
        drawImage(i, IntOffset.Zero, IntSize(i.width, i.height),
            IntOffset(((this.size.width - w) / 2).toInt(), ((this.size.height - h) / 2).toInt()), IntSize(w, h),
            filterQuality = FilterQuality.None)
    }
}

/** 아이템 ID 로 그림 (공식 API 이름·등급·머리 스킨 사용) */
@Composable
fun ItemIcon(state: AppState, id: String, size: Dp) {
    val name = state.itemName(id).replace(Regex("§."), "")
    val skin = IconResolver.skinUrl(state.items[id]?.skin?.value)
    PixelIcon(WikiIcons.url(name), name, state.itemTier(id), size, skin, id)
}

// 마인크래프트 GUI 색
private val GuiBg = Color(0xFFC6C6C6)
private val GuiLight = Color(0xFFFFFFFF)
private val GuiDark = Color(0xFF555555)
private val SlotBg = Color(0xFF8B8B8B)
private val SlotDark = Color(0xFF373737)

/** 마인크래프트 상자처럼 생긴 패널 */
@Composable
fun McPanel(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(GuiBg)
            .bevel(GuiLight, GuiDark, 3.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (title != null) {
            Text(title, style = SbText.pixel(13, Color(0xFF404040)))
            Spacer(Modifier.height(6.dp))
        }
        content()
    }
}

/** 위/왼쪽은 밝게, 아래/오른쪽은 어둡게 (마인크래프트 입체 테두리) */
fun Modifier.bevel(topLeft: Color, bottomRight: Color, width: Dp): Modifier = this.drawBehind {
    val w = width.toPx()
    drawRect(topLeft, Offset.Zero, Size(size.width, w))
    drawRect(topLeft, Offset.Zero, Size(w, size.height))
    drawRect(bottomRight, Offset(0f, size.height - w), Size(size.width, w))
    drawRect(bottomRight, Offset(size.width - w, 0f), Size(w, size.height))
}

/**
 * 인벤토리 칸 격자. 칸에 마우스를 올리면(휴대폰은 누르면) 게임 툴팁이 마우스 옆에 뜬다.
 * @param items 칸 순서대로 (null = 빈 칸)
 */
@Composable
fun McSlotGrid(
    state: AppState,
    items: List<SbItem?>,
    columns: Int,
    slotSize: Dp = 40.dp,
    minRows: Int = 0,
    extraTooltip: (SbItem) -> List<String> = { emptyList() },
) {
    var hovered by remember { mutableStateOf<SbItem?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val rows = maxOf(minRows, (items.size + columns - 1) / columns)
    Box(
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    e.changes.firstOrNull()?.let { pointer = it.position }
                    if (e.type == PointerEventType.Exit) hovered = null
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in 0 until columns) {
                        val item = items.getOrNull(r * columns + c)
                        McSlot(state, item, slotSize, onHover = { if (it) hovered = item else if (hovered == item) hovered = null })
                    }
                }
            }
        }
        val h = hovered
        if (h != null) {
            Popup(offset = IntOffset(pointer.x.toInt() + 18, pointer.y.toInt() + 6), properties = PopupProperties(focusable = false)) {
                ItemTooltip(h, extraTooltip(h))
            }
        }
    }
}

@Composable
fun McSlot(state: AppState, item: SbItem?, size: Dp, onHover: (Boolean) -> Unit = {}) {
    Box(
        Modifier.size(size).background(SlotBg).bevel(SlotDark, GuiLight, 2.dp)
            .pointerInput(item) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        when (e.type) {
                            PointerEventType.Enter -> onHover(true)
                            PointerEventType.Exit -> onHover(false)
                            PointerEventType.Press -> onHover(true)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (item != null) {
            PixelIcon(WikiIcons.url(WikiIcons.nameFor(state, item)), WikiIcons.nameFor(state, item), item.tier, size - 8.dp,
                item.skinUrl ?: IconResolver.skinUrl(item.skyblockId?.let { state.items[it]?.skin?.value }),
                item.pet?.let { "pet:" + WikiIcons.nameFor(state, item).removeSuffix(" Pet") } ?: item.skyblockId)
            if (item.count > 1) {
                Text("${item.count}", Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
                    style = TextStyle(fontFamily = sbhelper.ui.theme.LocalFonts.current.pixel, fontSize = 12.sp,
                        color = Color.White, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/**
 * 아이템 ID 로 그리는 칸 (조합대 등). [ids] 가 여러 개면 "이 중 하나" — 위키처럼 번갈아 보여 준다.
 * @param highlight 금색 테두리 (지금 보고 있는 아이템이 들어간 칸)
 */
@Composable
fun McIdSlot(state: AppState, ids: List<String>, count: Int, size: Dp, highlight: Boolean = false, onClick: ((String) -> Unit)? = null) {
    var index by remember(ids) { mutableStateOf(0) }
    if (ids.size > 1) {
        androidx.compose.runtime.LaunchedEffect(ids) {
            while (true) { kotlinx.coroutines.delay(1_400); index = (index + 1) % ids.size }
        }
    }
    val id = ids.getOrNull(index)
    Box(
        Modifier.size(size).background(SlotBg).bevel(SlotDark, GuiLight, 2.dp)
            .let { if (highlight) it.border(2.dp, Color(0xFFFFAA00)) else it }
            .let { m -> if (onClick != null && id != null) m.clickable { onClick(id) } else m },
        contentAlignment = Alignment.Center,
    ) {
        if (id != null) {
            ItemIcon(state, id, size - 8.dp)
            if (count > 1) {
                Text("$count", Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
                    style = TextStyle(fontFamily = sbhelper.ui.theme.LocalFonts.current.pixel, fontSize = 12.sp,
                        color = Color.White, fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF3F3F3F), Offset(2f, 2f))))
            }
            if (ids.size > 1) {
                Text("${index + 1}/${ids.size}", Modifier.align(Alignment.TopStart).padding(start = 2.dp),
                    style = TextStyle(fontFamily = sbhelper.ui.theme.LocalFonts.current.pixel, fontSize = 9.sp, color = Color(0xFFFFFF55)))
            }
        }
    }
}

/** 게임 툴팁 (이름 + 로어) */
@Composable
fun ItemTooltip(item: SbItem, extra: List<String> = emptyList()) {
    McTooltip(Modifier.widthIn(max = 380.dp)) {
        McText(item.name, size = 14, default = McColors.rarity(item.tier))
        Spacer(Modifier.height(4.dp))
        (item.lore + extra).forEach { line ->
            if (line.isBlank()) Spacer(Modifier.height(6.dp)) else McText(line, size = 12)
        }
    }
}

/** 장비 칸처럼 아이템 하나를 큰 칸으로 */
@Composable
fun ItemCell(state: AppState, item: SbItem?, size: Dp = 44.dp, onClick: (() -> Unit)? = null) {
    Box(Modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }) { McSlotGrid(state, listOf(item), 1, size) }
}

/**
 * 마우스를 올리면(휴대폰은 누르면) 게임 툴팁처럼 [tooltip] 을 마우스 옆에 띄운다.
 */
@Composable
fun HoverTip(tooltip: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    e.changes.firstOrNull()?.let { pointer = it.position }
                    when (e.type) {
                        PointerEventType.Enter -> show = true
                        PointerEventType.Exit -> show = false
                        PointerEventType.Press -> if (e.changes.firstOrNull()?.type != androidx.compose.ui.input.pointer.PointerType.Mouse) show = !show
                    }
                }
            }
        },
    ) {
        content()
        if (show) {
            Popup(offset = IntOffset(pointer.x.toInt() + 18, pointer.y.toInt() + 6),
                onDismissRequest = { show = false }, properties = PopupProperties(focusable = false)) {
                tooltip()
            }
        }
    }
}

/** 이름 + 로어 줄로 된 게임 툴팁 */
@Composable
fun LoreTooltip(title: String, titleColor: Color, lines: List<String>) {
    McTooltip(Modifier.widthIn(max = 380.dp)) {
        McText(title, size = 14, default = titleColor)
        if (lines.isNotEmpty()) Spacer(Modifier.height(4.dp))
        lines.forEach { line -> if (line.isBlank()) Spacer(Modifier.height(6.dp)) else McText(line, size = 12) }
    }
}
