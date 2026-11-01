package sbhelper.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import sbhelper.core.SkyblockRepository
import sbhelper.core.api.HypixelApi
import sbhelper.core.api.defaultHttpClient
import sbhelper.core.notify.AlertCategory
import sbhelper.core.sync.GistSync
import sbhelper.ui.AppController
import sbhelper.ui.AppInfo
import sbhelper.ui.UpdateFlow
import sbhelper.ui.SkyblockApp
import sbhelper.ui.generated.Res
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText
import java.awt.Dimension
import java.awt.Frame

fun main(args: Array<String>) {
    // 이미 실행 중이면 그 창을 띄우고 종료 (트레이 앱이라 중복 실행 방지)
    if (!SingleInstance.acquire()) return

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = defaultHttpClient()
    var repoRef: SkyblockRepository? = null
    val api = HypixelApi(http) { repoRef?.state?.value?.synced?.apiKey }
    val notifier = DesktopNotifier()
    val repo = SkyblockRepository(
        api = api,
        gistSync = GistSync(http),
        storage = FileStorage(AppDirs.dataDir()),
        notifier = notifier,
        scope = scope,
        loadGameData = { Res.readBytes("files/gamedata.json").decodeToString() },
        appVersion = AppInfo.VERSION,
    )
    repoRef = repo
    runBlocking { repo.load() }
    repo.startLoops()
    val controller = AppController(repo, scope)
    val platform = DesktopPlatform()
    platform.onQuitForUpdate = { repo.stopLoops(); kotlin.system.exitProcess(0) }

    // 새 버전 확인만: 켤 때 + 6시간마다. 받기·적용은 사용자가 동의해야 (화면에 "업데이트할까요?" 창)
    scope.launch {
        while (true) {
            platform.inAppUpdater?.stagedVersion?.let { repo.setUpdateStaged(it) }
            if (repo.state.value.device.autoUpdate) {
                val release = runCatching { repo.checkForUpdate() }.getOrNull()
                if (release != null && repo.state.value.updateStaged != release.version) {
                    notifier.show(sbhelper.core.notify.AppNotification("update:${release.version}", "새 버전 ${release.version}",
                        "앱을 열어 업데이트할지 골라 주세요.", AlertCategory.SYSTEM, sbhelper.core.util.Clock.now()))
                }
            }
            kotlinx.coroutines.delay(6 * 3_600_000L)
        }
    }

    val smokeFile = args.firstOrNull { it.startsWith("--smoke-test=") }?.removePrefix("--smoke-test=")
    val startHidden = smokeFile == null && ("--minimized" in args ||
        (repo.state.value.device.startMinimized && repo.state.value.device.runInBackground))

    var lastNotice: Pair<String?, Long> = null to 0L
    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val trayState = rememberTrayState()
        LaunchedEffect(Unit) {
            notifier.sink = { n ->
                lastNotice = n.target to System.currentTimeMillis()
                trayState.sendNotification(Notification(n.title, n.body, when (n.category) {
                    AlertCategory.PRICE, AlertCategory.AUCTION -> Notification.Type.Info
                    else -> Notification.Type.None
                }))
            }
            SingleInstance.onShowRequest = { visible = true; bringToFront() }
        }

        fun quit() {
            repo.stopLoops()
            exitApplication()
        }

        Tray(
            icon = painterResource("tray.png"),
            state = trayState,
            tooltip = "SkyblockHelper",
            onAction = {
                visible = true; bringToFront()
                // Windows 알림 풍선을 누르면 여기로 온다 (어느 알림인지는 알려 주지 않아 최근 알림 기준)
                val (target, at) = lastNotice
                if (target != null && System.currentTimeMillis() - at < 3 * 60_000) controller.openTarget(target)
                lastNotice = null to 0L
            },
            menu = {
                Item("열기", onClick = { visible = true; bringToFront() })
                Item("지금 새로 고침", onClick = { scope.launch { repo.refreshProfile(); repo.checkAlerts() } })
                Separator()
                Item("종료", onClick = ::quit)
            },
        )

        val windowState = rememberWindowState(size = DpSize(1320.dp, 860.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = {
                if (repo.state.value.device.runInBackground) {
                    visible = false
                    if (!notifier.toldAboutTray) {
                        notifier.toldAboutTray = true
                        trayState.sendNotification(Notification("SkyblockHelper", "트레이에서 계속 실행 중이에요. 완전히 끄려면 트레이 아이콘 → 종료"))
                    }
                } else quit()
            },
            state = windowState,
            visible = visible,
            title = "SkyblockHelper",
            icon = painterResource("app.png"),
            undecorated = true,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(900, 620) }
            // CI 확인용: 창이 그려지면 버전을 파일에 쓰고 끝낸다 (앱 안 업데이트로 실행되는지 검사)
            smokeFile?.let { f ->
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(4000)
                    java.io.File(f).writeText("${AppInfo.VERSION} patch=${System.getProperty(Patches.PROP_RUNNING) ?: "none"}")
                    kotlin.system.exitProcess(0)
                }
            }
            // 테두리 없는 창은 OS 최대화가 작업 표시줄까지 덮으므로, 작업 영역(작업 표시줄 제외)에 직접 맞춘다
            var maximized by remember { mutableStateOf(false) }
            var restore by remember { mutableStateOf<java.awt.Rectangle?>(null) }
            val toggleMaximize = {
                if (!maximized) {
                    restore = window.bounds
                    window.bounds = workArea(window)
                    maximized = true
                } else {
                    restore?.let { window.bounds = it }
                    maximized = false
                }
            }
            SkyblockApp(controller, platform, titleBar = {
                TitleBar(
                    maximized = maximized,
                    onMinimize = { window.extendedState = Frame.ICONIFIED },
                    onMaximize = toggleMaximize,
                    onClose = {
                        if (repo.state.value.device.runInBackground) visible = false else quit()
                    },
                )
            })
        }
    }
}

/** 창이 있는 모니터의 작업 영역 (작업 표시줄 제외) */
private fun workArea(window: java.awt.Window): java.awt.Rectangle {
    val gc = window.graphicsConfiguration
    val b = gc.bounds
    val i = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return java.awt.Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
}

private fun bringToFront() {
    Frame.getFrames().filter { it.isVisible }.forEach { f ->
        f.extendedState = f.extendedState and Frame.ICONIFIED.inv()
        f.toFront()
        f.requestFocus()
    }
}

/** 런처 스타일의 테두리 없는 창 제목 표시줄 */
@Composable
private fun WindowScope.TitleBar(maximized: Boolean, onMinimize: () -> Unit, onMaximize: () -> Unit, onClose: () -> Unit) {
    WindowDraggableArea {
        Row(
            Modifier.fillMaxWidth().height(34.dp).background(Sb.Window).padding(start = 14.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onMaximize() }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SKYBLOCK HELPER", style = SbText.caption(Sb.TextFaint))
            Spacer(Modifier.weight(1f))
            TitleButton(Icons.Rounded.Remove, Sb.SurfaceHigh, onMinimize)
            TitleButton(if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare, Sb.SurfaceHigh, onMaximize)
            TitleButton(Icons.Rounded.Close, Color(0xFFC42B1C), onClose)
        }
    }
}

@Composable
private fun TitleButton(icon: ImageVector, hoverColor: Color, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier.width(46.dp).fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverTracker { hovered = it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Sb.TextDim, modifier = Modifier.size(16.dp)) }
}
