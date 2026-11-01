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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
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
    INVENTORY("인벤토리", "인벤토리 · 장신구 · 자루", Icons.Rounded.Inventory2),
    STATS("기록", "순자산 · HotM · 도감 · 미니언", Icons.Rounded.Insights),
    GEAR("테크트리", "장비 · 펫 테크트리", Icons.Rounded.Category),
    TIMERS("타이머", "타이머 & 알림", Icons.Rounded.Timer),
    BAZAAR("바자", "바자", Icons.Rounded.ShowChart),
    AUCTION("경매장", "경매장", Icons.Rounded.Gavel),
    IRONMAN("아이언맨", "아이언맨 도구", Icons.Rounded.Handyman),
    PATCH("패치노트", "패치노트", Icons.Rounded.Newspaper),
    MORE("더보기", "더보기", Icons.Rounded.Apps),
    SETTINGS("설정", "설정", Icons.Rounded.Settings),
}

val LocalController = androidx.compose.runtime.staticCompositionLocalOf<AppController> { error("no controller") }

/** 화면 상태 + 저장소 */
class AppController(val repo: SkyblockRepository, val scope: CoroutineScope) {
    var screen by mutableStateOf(Screen.HOME)
    var toast by mutableStateOf<String?>(null)
    val auctionSearch = AuctionSearchState(repo.api, scope)
    val patchNotes = PatchNotesState()

    fun showToast(message: String) {
        toast = message
        scope.launch { delay(2600); if (toast == message) toast = null }
    }

    fun go(screen: Screen) { this.screen = screen }
}

@Composable
fun SkyblockApp(
    controller: AppController,
    platform: Platform,
    modifier: Modifier = Modifier,
    titleBar: (@Composable () -> Unit)? = null,
) {
    val state by controller.repo.state.collectAsState()
    SkyblockTheme(textScale = state.device.textScale) {
        CompositionLocalProvider(LocalPlatform provides platform, LocalController provides controller) {
            Column(Modifier.fillMaxSize().background(Sb.Window).then(modifier)) {
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
                Screen.PROFILE -> ProfileScreen(controller, state, wide)
                Screen.INVENTORY -> sbhelper.ui.screens.InventoryScreen(controller, state, wide)
                Screen.STATS -> sbhelper.ui.screens.StatsScreen(controller, state, wide)
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
                Screen.PLAN -> ProgressionScreen(controller, state, wide)
                Screen.GEAR -> sbhelper.ui.screens.TechTreeScreen(controller, state, wide)
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
        val main = listOf(Screen.HOME, Screen.PLAN, Screen.GEAR, Screen.PROFILE, Screen.INVENTORY, Screen.STATS, Screen.TIMERS, Screen.BAZAAR, Screen.AUCTION, Screen.IRONMAN, Screen.PATCH)
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
                (s == Screen.MORE && controller.screen in listOf(Screen.PROFILE, Screen.INVENTORY, Screen.STATS, Screen.GEAR, Screen.IRONMAN, Screen.PATCH, Screen.SETTINGS))
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
        Column(Modifier.weight(1f)) {
            Text(controller.screen.title, style = SbText.pixel(if (compact) 17 else 21, bold = true))
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
            Text(if (staged != null) "새 버전 $staged 준비 완료 — 다시 시작하면 바로 적용돼요" else "새 버전 ${release?.version} 이 나왔어요",
                style = SbText.body(13, weight = FontWeight.SemiBold))
            val msg = state.updateMessage
            if (msg != null && staged == null) Text(msg, style = SbText.body(12, if (msg.startsWith("⚠")) Sb.Negative else Sb.TextDim))
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
            sbhelper.ui.components.SbButton("지금 다시 시작", { controller.repo.stopLoops(); updater.restart() }, color = Sb.Aqua)
        } else {
            sbhelper.ui.components.SbButton("업데이트 받기", {
                controller.scope.launch { UpdateFlow.run(controller, platform) }
            }, enabled = state.updateProgress == null, color = Sb.Aqua)
        }
    }
}

/** 처음 실행 시 데이터 로딩 */
@Composable
fun StartRepository(controller: AppController) {
    LaunchedEffect(Unit) {
        controller.repo.startLoops()
    }
}
