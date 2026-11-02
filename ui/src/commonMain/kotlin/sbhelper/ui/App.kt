package sbhelper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sbhelper.core.AppState
import sbhelper.core.SkyblockRepository
import sbhelper.core.model.GameMode
import sbhelper.ui.components.PlayerHead
import sbhelper.ui.components.Segmented
import sbhelper.ui.screens.AuctionScreen
import sbhelper.ui.screens.AuctionSearchState
import sbhelper.ui.screens.BazaarScreen
import sbhelper.ui.screens.HomeScreen
import sbhelper.ui.screens.IronmanScreen
import sbhelper.ui.screens.CalculatorScreen
import sbhelper.ui.screens.MoreScreen
import sbhelper.ui.screens.PatchNotesScreen
import sbhelper.ui.screens.PatchNotesState
import sbhelper.ui.screens.ProgressionScreen
import sbhelper.ui.screens.ProfileScreen
import sbhelper.ui.screens.SettingsScreen
import sbhelper.ui.screens.TimersScreen
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText
import sbhelper.ui.theme.SkyblockTheme

enum class Screen(val label: String, val title: String, val icon: ImageVector) {
    HOME("홈", "대시보드", Icons.Rounded.Dashboard),
    PLAN("추천", "진행 방향 추천", Icons.Rounded.AccountTree),
    PROFILE("프로필", "프로필", Icons.Rounded.Person),
    SEARCH("검색", "위키 검색", Icons.Rounded.Search),
    EVENTS("이벤트", "이벤트", Icons.Rounded.Event),
    INVENTORY("인벤토리", "인벤토리 · 장신구 · 자루", Icons.Rounded.Inventory2),
    STATS("기록", "순자산 · HotM · HotF · 도감 · 미니언", Icons.Rounded.Insights),
    GEAR("테크트리", "스킬별 장비 진행", Icons.Rounded.Category),
    CALC("계산기", "계산기", Icons.Rounded.Calculate),
    TIMERS("타이머", "타이머 & 알림", Icons.Rounded.Timer),
    BAZAAR("바자", "바자", Icons.Rounded.ShowChart),
    AUCTION("경매장", "경매장", Icons.Rounded.Gavel),
    IRONMAN("아이언맨", "아이언맨 도구", Icons.Rounded.Handyman),
    PATCH("패치노트", "패치노트", Icons.Rounded.Newspaper),
    MORE("더보기", "더보기", Icons.Rounded.Apps),
    SETTINGS("설정", "설정", Icons.Rounded.Settings),
}

/** 아이템 이름 → 공식 아이템 ID (없으면 null) */
fun itemIdForName(state: AppState, name: String): String? {
    val n = name.trim()
    state.items.values.firstOrNull { it.name.replace(Regex("§."), "").equals(n, true) }?.let { return it.id }
    state.gameData.names.entries.firstOrNull { it.value.equals(n, true) }?.let { return it.key }
    if (n.endsWith(" Pet", true)) return "pet:" + n.dropLast(4)
    return null
}

/** 위키 검색 화면에서 여는 것: 아이템(공식 ID) 또는 위키 문서 */
sealed interface WikiTarget {
    data class Item(val id: String) : WikiTarget
    data class Page(val title: String) : WikiTarget
}

val LocalController = androidx.compose.runtime.staticCompositionLocalOf<AppController> { error("no controller") }

/** 화면 상태 + 저장소 */
class AppController(val repo: SkyblockRepository, val scope: CoroutineScope) {
    var screen by mutableStateOf(Screen.HOME)
    var toast by mutableStateOf<String?>(null)
    var inventoryTab by mutableStateOf(0)
    var ironmanTab by mutableStateOf(0)
    /** 계산기: 0 제작 플래너, 1 미니언 수익 */
    var calcTab by mutableStateOf(0)
    /** 테크트리: 고른 스킬 탭, 넓은 화면에서 정보창에 띄운 아이템 */
    var gearTab by mutableStateOf(0)
    var gearSelected by mutableStateOf<String?>(null)
    /** 이스터에그: 화면에서 터지는 중인 Implosion */
    val implosions = androidx.compose.runtime.mutableStateListOf<sbhelper.ui.components.Implosion>()
    private var lastImplode = 0L
    /** @return 터뜨렸으면 true (0.7초 안에 다시 누르면 무시) */
    fun implode(at: androidx.compose.ui.geometry.Offset): Boolean {
        val now = sbhelper.core.util.Clock.now()
        if (now - lastImplode < 700) return false
        lastImplode = now
        implosions += sbhelper.ui.components.Implosion(now, at, (now % Int.MAX_VALUE).toInt())
        return true
    }
    /** 다른 플레이어 보기 (null = 내 프로필) */
    var viewing by mutableStateOf<sbhelper.core.ViewedPlayer?>(null)
    var viewingBusy by mutableStateOf(false)
    var viewingError by mutableStateOf<String?>(null)
    fun viewPlayer(name: String, profileId: String? = null) {
        if (name.isBlank() || viewingBusy) return
        viewingBusy = true; viewingError = null
        scope.launch {
            runCatching { repo.lookupPlayer(name.trim(), profileId) }
                .onSuccess { viewing = it }
                .onFailure { viewingError = it.message ?: "불러오지 못했어요" }
            viewingBusy = false
        }
    }
    fun stopViewing() { viewing = null; viewingError = null }
    /** 프로필 바꾸기: 다른 플레이어를 보는 중이면 그 사람의 프로필, 아니면 내 프로필 */
    fun pickProfile(id: String) { val v = viewing; if (v != null) viewPlayer(v.name, id) else repo.selectProfile(id) }
    /** 테크트리: 스킬별로 고른 갈래 탭 (낚시 일반 물/용암, 전투 무기/방어구 …) */
    val gearSubTabs = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    /** 화면·탭별 스크롤 위치 (다른 화면에 갔다 와도 유지) */
    private val scrolls = HashMap<String, androidx.compose.foundation.ScrollState>()
    fun scrollState(key: String) = scrolls.getOrPut(key) { androidx.compose.foundation.ScrollState(0) }
    /** 탭이 있는 화면은 탭마다 따로 */
    fun tabKey(): String = when (screen) {
        Screen.STATS -> ":$statsTab"
        Screen.INVENTORY -> ":$inventoryTab"
        Screen.IRONMAN -> ":$ironmanTab"
        Screen.CALC -> ":$calcTab"
        Screen.GEAR -> ":$gearTab:" + (gearSubTabs[sbhelper.core.data.SkillGuide.skills.getOrNull(gearTab)?.key] ?: 0)
        Screen.EVENTS -> ":$eventsTab"
        Screen.SEARCH -> ":" + (wiki?.toString() ?: "results")
        else -> ""
    }

    /** 이번 실행에서 "업데이트할까요?" 에 답한 버전 */
    var updateAsked by mutableStateOf<String?>(null)
    /** 제작 플래너 입력 (화면을 옮겨도 유지) */
    var plannerQuery by mutableStateOf("")
    var statsTab by mutableStateOf(0)
    val auctionSearch = AuctionSearchState(repo.api, scope)
    val patchNotes = PatchNotesState()

    // ---------------------------------------------------------------- 화면 이동 + 뒤로 가기
    /** 이벤트 화면: 고른 이벤트 이름, 0 = 일정 / 1 = 제이콥 농사대회 */
    var event by mutableStateOf<String?>(null)
    var eventsTab by mutableStateOf(0)
    /** 위키 검색 화면에서 보는 것 */
    var wiki by mutableStateOf<WikiTarget?>(null)
    var searchQuery by mutableStateOf("")

    private data class NavEntry(val screen: Screen, val event: String?, val eventsTab: Int, val wiki: WikiTarget?)
    private val history = ArrayList<NavEntry>()
    /** 뒤로 간 화면들 (앞으로 가기용). 새 화면으로 가면 비운다 */
    private val forwardStack = ArrayList<NavEntry>()
    private var historySize by mutableStateOf(0)
    val canGoBack get() = historySize > 0

    private fun current() = NavEntry(screen, event, eventsTab, wiki)

    private fun push() {
        history += current()
        if (history.size > 60) history.removeAt(0)
        historySize = history.size
        forwardStack.clear()
    }

    fun showToast(message: String) {
        toast = message
        scope.launch { delay(2600); if (toast == message) toast = null }
    }

    fun go(screen: Screen) {
        if (screen != this.screen) push()
        this.screen = screen
    }

    /** 이전 화면으로 (마우스 뒤로 버튼 / 휴대폰 뒤로 버튼). 더 갈 곳이 없으면 false */
    fun back(): Boolean {
        val e = history.removeLastOrNull() ?: return false
        historySize = history.size
        forwardStack += current()
        screen = e.screen; event = e.event; eventsTab = e.eventsTab; wiki = e.wiki
        return true
    }

    /** 뒤로 갔던 화면으로 다시 (마우스 앞으로 버튼). 없으면 false */
    fun forward(): Boolean {
        val e = forwardStack.removeLastOrNull() ?: return false
        history += current()
        historySize = history.size
        screen = e.screen; event = e.event; eventsTab = e.eventsTab; wiki = e.wiki
        return true
    }

    fun openEvent(name: String) { push(); screen = Screen.EVENTS; eventsTab = 0; event = name }
    fun openJacob() { push(); screen = Screen.EVENTS; eventsTab = 1 }
    fun openItem(id: String) { push(); screen = Screen.SEARCH; wiki = WikiTarget.Item(id) }
    fun openPage(title: String) { push(); screen = Screen.SEARCH; wiki = WikiTarget.Page(title) }

    /** 위키 링크: 아이템 이름이면 아이템 화면, 아니면 위키 문서 */
    fun openWikiLink(page: String) {
        val id = itemIdForName(repo.state.value, page)
        if (id != null) openItem(id) else openPage(page)
    }

    /** 알림을 눌렀을 때 (AppNotification.target) */
    fun openTarget(target: String?) {
        when {
            target == null -> Unit
            target == "jacob" -> openJacob()
            target.startsWith("event:") -> openEvent(target.removePrefix("event:"))
            target == "timers" -> go(Screen.TIMERS)
            target == "patch" -> go(Screen.PATCH)
        }
    }
}

@Composable
fun SkyblockApp(
    controller: AppController,
    platform: Platform,
    modifier: Modifier = Modifier,
    titleBar: (@Composable () -> Unit)? = null,
) {
    val state by controller.repo.state.collectAsState()
    SkyblockTheme(textScale = state.device.textScale, textWeight = state.device.fontWeight) {
        val implode: (androidx.compose.ui.geometry.Offset) -> Unit = { p ->
            if (controller.implode(p) && state.device.alertSound)
                platform.playSound(sbhelper.core.util.Synth.witherImpact(state.device.alertVolume / 100f, (p.x + p.y).toInt() + controller.implosions.size))
        }
        CompositionLocalProvider(LocalPlatform provides platform, LocalController provides controller,
            sbhelper.ui.components.LocalImplode provides implode,
            sbhelper.ui.components.LocalIconPackVersion provides state.iconPackVersion,
            sbhelper.ui.screens.LocalContentWidth provides state.device.contentWidth) {
            // 이스터에그 폭발이 화면 전체 위에 그려지도록 맨 바깥에서 감싼다
            sbhelper.ui.components.ImplosionLayer(controller.implosions) {
            Column(Modifier.fillMaxSize().background(Sb.Window).then(modifier)
                // 마우스 뒤로(MB4) / 앞으로(MB5) 버튼.
                // 버튼이 '새로 눌린 순간' 한 번만, 그리고 0.3초 안의 반복(매크로·더블클릭)은 무시
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var wasBack = false; var wasFwd = false; var lastAt = 0L
                        while (true) {
                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val back = e.buttons.isBackPressed
                            val fwd = e.buttons.isForwardPressed
                            val t = e.changes.firstOrNull()?.uptimeMillis ?: 0L
                            if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press && t - lastAt > 300) {
                                if (back && !wasBack) { lastAt = t; controller.back() }
                                else if (fwd && !wasFwd) { lastAt = t; controller.forward() }
                            }
                            wasBack = back; wasFwd = fwd
                        }
                    }
                }) {
                titleBar?.invoke()
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 760.dp
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            SideRail(controller, state)
                            Column(Modifier.fillMaxSize().background(Sb.Background)) {
                                TopBar(controller, state, compact = false)
                                UpdateBanner(controller, state, platform)
                                ScreenContent(controller, state, wide = true, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().background(Sb.Background)) {
                            TopBar(controller, state, compact = true)
                            UpdateBanner(controller, state, platform)
                            ScreenContent(controller, state, wide = false, modifier = Modifier.weight(1f))
                            BottomNav(controller, state)
                        }
                    }
                    Toast(controller.toast, Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
                    UpdateAvailableDialog(controller, state, platform)
                    UpdateReadyDialog(controller, state, platform)
                    WhatsNewDialog(controller, state, platform)
                }
            }
            }
        }
    }
}

@Composable
private fun ScreenContent(controller: AppController, state: AppState, wide: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth()) {
        Crossfade(controller.screen, animationSpec = tween(180)) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(controller, state, wide)
                Screen.PROFILE -> sbhelper.ui.screens.PlayerViewFrame(controller, state, wide) { s -> ProfileScreen(controller, s, wide) }
                Screen.INVENTORY -> sbhelper.ui.screens.PlayerViewFrame(controller, state, wide) { s -> sbhelper.ui.screens.InventoryScreen(controller, s, wide) }
                Screen.STATS -> sbhelper.ui.screens.PlayerViewFrame(controller, state, wide) { s -> sbhelper.ui.screens.StatsScreen(controller, s, wide) }
                Screen.SEARCH -> sbhelper.ui.screens.SearchScreen(controller, state, wide)
                Screen.EVENTS -> sbhelper.ui.screens.EventsScreen(controller, state, wide)
                Screen.TIMERS -> TimersScreen(controller, state, wide)
                Screen.BAZAAR, Screen.AUCTION -> if (wide) {
                    if (screen == Screen.BAZAAR) BazaarScreen(controller, state, wide) else AuctionScreen(controller, state, wide)
                } else {
                    // 휴대폰: 바자/경매장을 한 탭에서 전환
                    Column {
                        Segmented(listOf("바자", "경매장"), if (screen == Screen.BAZAAR) 0 else 1,
                            { controller.go(if (it == 0) Screen.BAZAAR else Screen.AUCTION) },
                            Modifier.padding(start = 16.dp, top = 12.dp))
                        if (screen == Screen.BAZAAR) BazaarScreen(controller, state, wide) else AuctionScreen(controller, state, wide)
                    }
                }
                Screen.IRONMAN -> IronmanScreen(controller, state, wide)
                Screen.CALC -> CalculatorScreen(controller, state, wide)
                Screen.PLAN -> ProgressionScreen(controller, state, wide)
                Screen.GEAR -> sbhelper.ui.screens.PlayerViewFrame(controller, state, wide) { s -> sbhelper.ui.screens.TechTreeScreen(controller, s, wide) }
                Screen.PATCH -> PatchNotesScreen(controller, state, wide)
                Screen.MORE -> MoreScreen(controller)
                Screen.SETTINGS -> SettingsScreen(controller, state, wide)
            }
        }
    }
}

// ---------------------------------------------------------------- 왼쪽 사이드바 (데스크톱)
@Composable
private fun SideRail(controller: AppController, state: AppState) {
    Column(
        Modifier.width(84.dp).fillMaxHeight().background(Sb.Rail)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo()
        Spacer(Modifier.height(18.dp))
        val main = listOf(Screen.HOME, Screen.PROFILE, Screen.SEARCH, Screen.PLAN, Screen.GEAR, Screen.CALC, Screen.INVENTORY, Screen.STATS,
            Screen.TIMERS, Screen.EVENTS, Screen.BAZAAR, Screen.AUCTION, Screen.IRONMAN, Screen.PATCH)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            main.forEach { s ->
                val dim = (s == Screen.BAZAAR || s == Screen.AUCTION) && state.selectedProfile?.gameMode?.canTrade == false
                RailItem(s, controller.screen == s, dim) { controller.go(s) }
            }
        }
        if (state.device.sync.enabled) {
            Icon(Icons.Rounded.CloudSync, "기기 연동", tint = if (state.syncingDevices) Sb.Aqua else Sb.TextFaint,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
        }
        RailItem(Screen.SETTINGS, controller.screen == Screen.SETTINGS, false) { controller.go(Screen.SETTINGS) }
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFC23D), Color(0xFFE07B00))))
            .border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("SB", style = SbText.pixel(17, Color(0xFF241500), bold = true)) }
}

@Composable
private fun RailItem(screen: Screen, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(if (selected) 28.dp else 0.dp).clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(Sb.Gold))
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) Sb.SurfaceHigh else Color.Transparent)
                .clickable(onClick = onClick).padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val tint = when {
                selected -> Sb.Gold
                dim -> Sb.TextFaint.copy(alpha = 0.5f)
                else -> Sb.TextDim
            }
            Icon(screen.icon, screen.label, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(screen.label, style = SbText.pixel(10, if (selected) Sb.Text else tint))
        }
    }
}

// ---------------------------------------------------------------- 하단 탭 (휴대폰)
@Composable
private fun BottomNav(controller: AppController, state: AppState) {
    val items = listOf(Screen.HOME, Screen.PLAN, Screen.TIMERS, Screen.BAZAAR, Screen.MORE)
    Row(
        Modifier.fillMaxWidth().background(Sb.Rail).border(1.dp, Sb.Border).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { s ->
            val selected = controller.screen == s || (s == Screen.BAZAAR && controller.screen == Screen.AUCTION) ||
                (s == Screen.MORE && controller.screen in listOf(Screen.PROFILE, Screen.SEARCH, Screen.EVENTS, Screen.INVENTORY, Screen.STATS, Screen.GEAR, Screen.CALC, Screen.IRONMAN, Screen.PATCH, Screen.SETTINGS))
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { controller.go(s) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(s.icon, s.label, tint = if (selected) Sb.Gold else Sb.TextDim, modifier = Modifier.size(22.dp))
                Text(if (s == Screen.BAZAAR) "거래" else s.label, style = SbText.pixel(10, if (selected) Sb.Text else Sb.TextFaint))
            }
        }
    }
}

// ---------------------------------------------------------------- 상단 바
@Composable
private fun TopBar(controller: AppController, state: AppState, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controller.canGoBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", tint = Sb.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { controller.back() }.padding(6.dp).size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            // 휴대폰: 짧은 이름 한 줄 (긴 제목이 여러 줄로 꺾이지 않게)
            Text(if (compact) controller.screen.label else controller.screen.title, style = SbText.pixel(if (compact) 17 else 21, bold = true),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val sub = when {
                state.loadingProfile -> "프로필을 불러오는 중…"
                state.profileError != null -> "⚠ ${state.profileError}"
                state.networkError != null -> "⚠ ${state.networkError}"
                else -> null
            }
            if (sub != null) Text(sub, style = SbText.body(12, if (sub.startsWith("⚠")) Sb.Negative else Sb.TextFaint), maxLines = 1)
        }
        RefreshButton(state.loadingProfile) {
            controller.scope.launch {
                controller.repo.api.clearCache()
                controller.repo.refreshProfile()
                controller.repo.refreshBazaar()
                controller.repo.refreshElectionAndNews()
            }
        }
        Spacer(Modifier.width(10.dp))
        AccountChip(controller, state, compact)
        if (compact) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Settings, "설정", tint = if (controller.screen == Screen.SETTINGS) Sb.Gold else Sb.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { controller.go(Screen.SETTINGS) }.padding(8.dp).size(22.dp))
        }
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition()
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart))
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Sb.Surface).border(1.dp, Sb.Border, RoundedCornerShape(10.dp))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Refresh, "새로 고침", tint = if (loading) Sb.Gold else Sb.TextDim,
            modifier = Modifier.size(19.dp).let { if (loading) it.rotate(angle) else it })
    }
}

@Composable
private fun AccountChip(controller: AppController, state: AppState, compact: Boolean) {
    var open by remember { mutableStateOf(false) }
    val profile = state.selectedProfile
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Sb.Surface).border(1.dp, Sb.Border, RoundedCornerShape(12.dp))
                .clickable {
                    if (!state.accountLinked) controller.go(Screen.SETTINGS) else open = true
                }
                .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.accountLinked) {
                PlayerHead(state.synced.playerUuid, state.synced.username, 30.dp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(state.synced.username, style = SbText.body(13, weight = FontWeight.SemiBold))
                    if (!compact && profile != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.cuteName, style = SbText.pixel(10, Sb.TextDim))
                            if (profile.gameMode != GameMode.NORMAL) {
                                Text("  ${profile.gameMode.badge} ${profile.gameMode.label}", style = SbText.pixel(10, Sb.Gray))
                            }
                        }
                    }
                }
            } else {
                Icon(Icons.Rounded.AccountCircle, null, tint = Sb.Gold, modifier = Modifier.padding(4.dp).size(22.dp))
                Spacer(Modifier.width(4.dp))
                Text("계정 연결", style = SbText.body(13, Sb.Gold, FontWeight.SemiBold))
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(Sb.SurfaceHigh).widthIn(min = 220.dp)) {
            state.profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.cuteName, style = SbText.body(14, if (p.profileId == profile?.profileId) Sb.Gold else Sb.Text, FontWeight.Medium))
                            if (p.gameMode != GameMode.NORMAL) Text("  ${p.gameMode.badge} ${p.gameMode.label}", style = SbText.pixel(11, Sb.Gray))
                            if (p.selected) Text("  · 게임에서 선택됨", style = SbText.body(11, Sb.TextFaint))
                        }
                    },
                    onClick = { open = false; controller.repo.selectProfile(p.profileId) },
                )
            }
            DropdownMenuItem(text = { Text("계정 설정…", style = SbText.body(13, Sb.TextDim)) },
                onClick = { open = false; controller.go(Screen.SETTINGS) })
        }
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier) {
    AnimatedVisibility(message != null, modifier, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Sb.SurfaceHover).border(1.dp, Sb.BorderStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Sb.Gold))
            Spacer(Modifier.width(10.dp))
            Text(message ?: "", style = SbText.body(13, weight = FontWeight.Medium))
        }
    }
}

/** 새 버전 안내 띠 */
@Composable
private fun UpdateBanner(controller: AppController, state: AppState, platform: Platform) {
    val staged = state.updateStaged
    val release = state.availableUpdate
    if (staged == null && release == null) return
    if (staged == null && platform.updateAssetSuffix == null && platform.inAppUpdater == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
            .background(Sb.Aqua.copy(alpha = 0.10f)).border(1.dp, Sb.Aqua.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Sb.Aqua, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (staged != null) (if (platform.isDesktop) "새 버전 $staged 준비 완료 — 다시 시작하면 바로 적용돼요"
                else "새 버전 $staged 을 받았어요 — 설치하면 앱이 닫혔다가 알림으로 다시 열어요") else "새 버전 ${release?.version} 이 나왔어요",
                style = SbText.body(13, weight = FontWeight.SemiBold))
            val msg = state.updateMessage
            if (msg != null && (staged == null || !platform.isDesktop)) Text(msg, style = SbText.body(12, if (msg.startsWith("⚠")) Sb.Negative else Sb.TextDim))
            state.updateProgress?.let { p ->
                if (staged == null) {
                    Spacer(Modifier.height(4.dp))
                    sbhelper.ui.components.XpBar(p, Modifier.fillMaxWidth(), Sb.Aqua, 5.dp)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val updater = platform.inAppUpdater
        if (staged != null && updater != null) {
            sbhelper.ui.components.SbButton(if (platform.isDesktop) "지금 다시 시작" else "설치하고 다시 시작",
                { applyUpdate(controller, platform, updater) }, color = Sb.Aqua)
        } else {
            sbhelper.ui.components.SbButton("업데이트 받기", {
                controller.scope.launch { UpdateFlow.run(controller, platform) }
            }, enabled = state.updateProgress == null, color = Sb.Aqua)
        }
    }
}

/** 새 버전이 나오면 묻는다: 업데이트할까요? (동의해야 받는다) */
@Composable
private fun UpdateAvailableDialog(controller: AppController, state: AppState, platform: Platform) {
    val release = state.availableUpdate ?: return
    if (state.updateStaged != null || state.updateProgress != null) return
    if (platform.inAppUpdater == null && platform.updateAssetSuffix == null) return
    if (controller.updateAsked == release.version) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { controller.updateAsked = release.version },
        containerColor = Sb.SurfaceHigh,
        title = { Text("새 버전 ${release.version}", style = SbText.body(17, weight = FontWeight.Bold)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("새 버전이 나왔어요. 지금 업데이트할까요?", style = SbText.body(14, Sb.TextDim))
                ReleaseNotes(release.body)
            }
        },
        confirmButton = {
            sbhelper.ui.components.SbButton("업데이트", {
                controller.updateAsked = release.version
                controller.scope.launch { UpdateFlow.run(controller, platform) }
            }, color = Sb.Aqua)
        },
        dismissButton = { sbhelper.ui.components.SbButton("나중에", { controller.updateAsked = release.version }, outlined = true) },
    )
}

/**
 * 릴리스 노트 보여 주기: '### 새 기능' 같은 제목은 굵고 크게, '- 항목' 은 • 줄로.
 * 마크다운 기호(#, **, -)는 그대로 보이지 않게 지운다. 맨 위 '## 앱 이름 버전' 줄은 창 제목과 겹쳐서 뺀다.
 */
@Composable
private fun ReleaseNotes(body: String?) {
    val lines = body?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("## ") }?.toList().orEmpty()
    if (lines.isEmpty()) return
    fun clean(t: String) = t.replace("**", "").replace("__", "").replace("`", "").trim()
    lines.take(24).forEach { line ->
        when {
            line.startsWith("#") -> {
                Spacer(Modifier.height(12.dp))
                Text(clean(line.trimStart('#')), style = SbText.body(15, Sb.Gold, FontWeight.Bold))
            }
            line.startsWith("- ") || line.startsWith("* ") -> Row(Modifier.padding(top = 4.dp)) {
                Text("•", style = SbText.body(14, Sb.Gold)); Spacer(Modifier.width(8.dp))
                Text(clean(line.drop(2)), style = SbText.body(14, Sb.Text))
            }
            else -> Text(clean(line), style = SbText.body(13, Sb.TextDim), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 업데이트를 다 받으면 한 번 묻는다: 지금 다시 시작할까요? */
@Composable
private fun UpdateReadyDialog(controller: AppController, state: AppState, platform: Platform) {
    val staged = state.updateStaged ?: return
    val updater = platform.inAppUpdater ?: return
    var answered by remember { mutableStateOf<String?>(null) }
    if (answered == staged) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { answered = staged },
        containerColor = Sb.SurfaceHigh,
        title = { Text("업데이트 준비 완료", style = SbText.body(17, weight = FontWeight.Bold)) },
        text = {
            Text(
                if (platform.isDesktop) "새 버전 $staged 을 받았어요.\n지금 다시 시작하면 바로 적용돼요. (나중에 하면 다음에 켤 때 적용돼요)"
                else "새 버전 $staged 을 받았어요.\n지금 설치할까요?\n\n• 처음 한 번은 '알 수 없는 앱 설치' 허용 화면이 떠요 → 허용하고 뒤로\n• Android 가 '업데이트할까요?' 물으면 '업데이트'\n• 설치하는 동안 앱이 닫혀요 → '업데이트 완료' 알림을 눌러 다시 열기",
                style = SbText.body(14, Sb.TextDim),
            )
        },
        confirmButton = {
            sbhelper.ui.components.SbButton(if (platform.isDesktop) "지금 다시 시작" else "설치하고 다시 시작",
                { answered = staged; applyUpdate(controller, platform, updater) }, color = Sb.Aqua)
        },
        dismissButton = { sbhelper.ui.components.SbButton("나중에", { answered = staged }, outlined = true) },
    )
}

private fun applyUpdate(controller: AppController, platform: Platform, updater: InAppUpdater) {
    if (platform.isDesktop) controller.repo.stopLoops()
    updater.restart()
}

/** 처음 실행 시 데이터 로딩 */
@Composable
fun StartRepository(controller: AppController) {
    LaunchedEffect(Unit) {
        controller.repo.startLoops()
    }
}

/** 앱 안 업데이트 내역 (files/changelog.json) */
@Composable
fun rememberChangelog(): List<sbhelper.core.data.ChangelogEntry> {
    val entries by androidx.compose.runtime.produceState(emptyList<sbhelper.core.data.ChangelogEntry>()) {
        value = sbhelper.core.data.Changelog.parse(sbhelper.ui.generated.Res.readBytes("files/changelog.json").decodeToString())
    }
    return entries
}

/** 업데이트한 뒤 처음 켰을 때 한 번: 새로워진 점 (처음 설치했을 때는 안 띄움) */
@Composable
private fun WhatsNewDialog(controller: AppController, state: AppState, platform: Platform) {
    val current = platform.versionName
    val seen = state.device.lastSeenVersion
    val entries = rememberChangelog()
    LaunchedEffect(seen, state.device.onboardingDone, state.accountLinked) {
        // 처음 설치: 그냥 지금 버전을 봤다고 기록
        if (seen.isEmpty() && !state.device.onboardingDone && !state.accountLinked) controller.repo.updateDevice { it.copy(lastSeenVersion = current) }
    }
    if (seen == current || entries.isEmpty()) return
    if (seen.isEmpty() && !state.device.onboardingDone && !state.accountLinked) return
    // 지난번에 본 버전 이후의 내역. 이 기능이 생기기 전 버전에서 올라왔으면(기록 없음) 지금 버전만
    val newer = entries.filter { e -> if (seen.isEmpty()) e.version == current else sbhelper.core.update.Updates.isNewer(e.version, seen) }
        .filter { e -> !sbhelper.core.update.Updates.isNewer(e.version, current) }.take(4)
        .ifEmpty { entries.filter { it.version == current } }
    if (newer.isEmpty()) { LaunchedEffect(current) { controller.repo.updateDevice { it.copy(lastSeenVersion = current) } }; return }
    val close = { controller.repo.updateDevice { it.copy(lastSeenVersion = current) } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        containerColor = Sb.SurfaceHigh,
        title = { Text("업데이트 완료 · v$current", style = SbText.body(17, weight = FontWeight.Bold)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("새로워진 점", style = SbText.body(15, Sb.Gold, FontWeight.Bold))
                newer.forEach { e ->
                    Spacer(Modifier.height(8.dp))
                    if (newer.size > 1) Text("v${e.version}", style = SbText.pixel(12, Sb.TextDim, bold = true))
                    e.items.forEach { line ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("•", style = SbText.body(14, Sb.Gold)); Spacer(Modifier.width(8.dp))
                            Text(line, style = SbText.body(14, Sb.Text))
                        }
                    }
                }
            }
        },
        confirmButton = { sbhelper.ui.components.SbButton("확인", { close() }, color = Sb.Gold) },
    )
}

