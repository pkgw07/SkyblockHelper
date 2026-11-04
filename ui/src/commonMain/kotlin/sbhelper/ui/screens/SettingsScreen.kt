package sbhelper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sbhelper.core.AppState
import sbhelper.core.calc.EventLabels
import sbhelper.ui.AppController
import sbhelper.ui.LocalController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.components.Divider
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PlayerHead
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.SwitchRow
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

@Composable
fun SettingsScreen(controller: AppController, state: AppState, wide: Boolean) {
    ScreenColumn(wide) {
        Responsive(
            wide,
            // 왼쪽: 계정·화면·업데이트·정보 / 오른쪽: 실행·오버레이·알림 (양쪽 길이를 비슷하게)
            cell(1f) { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AccountPanel(controller, state)
                    SyncPanel(controller, state)
                    DisplayPanel(controller, state)
                    UpdatePanel(controller, state)
                    AboutPanel(state)
                    RemoveAppPanel(controller)
                }
            },
            cell(1f) { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    BackgroundPanel(controller, state)
                    NotificationPanel(controller, state)
                }
            },
        )
    }
}

@Composable
private fun AccountPanel(controller: AppController, state: AppState) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.synced.username) }
    var key by remember { mutableStateOf(state.synced.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Panel(Modifier.fillMaxWidth(), caption = "Account", title = "계정 연결", accent = Sb.Aqua) {
        if (state.accountLinked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerHead(state.synced.playerUuid, state.synced.username, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.synced.username, style = SbText.body(16, weight = FontWeight.Bold))
                    Text("프로필 ${state.profiles.size}개 · ${state.selectedProfile?.cuteName ?: "-"} 선택됨", style = SbText.body(12, Sb.TextFaint))
                }
                Icon(Icons.Rounded.LinkOff, "연결 해제", tint = Sb.TextFaint,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { controller.repo.unlinkAccount() }.padding(6.dp).size(20.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Text("마인크래프트 닉네임", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        SbTextField(name, { name = it.trim() }, Modifier.fillMaxWidth(), "예: Technoblade", Icons.Rounded.Person)
        Spacer(Modifier.height(12.dp))
        Text("Hypixel API 키", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        SbTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), "developer.hypixel.net 에서 발급", Icons.Rounded.Key, secret = !showKey,
            trailing = {
                Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "보기", tint = Sb.TextFaint,
                    modifier = Modifier.clickable { showKey = !showKey }.size(18.dp))
            })
        Spacer(Modifier.height(6.dp))
        Row(Modifier.clickable { platform.openUrl("https://developer.hypixel.net/") }, verticalAlignment = Alignment.CenterVertically) {
            Text("API 키 발급하기 (Hypixel 공식 개발자 사이트)", style = SbText.body(12, Sb.Aqua))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Sb.Aqua, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.height(14.dp))
        SbButton(if (busy) "확인 중…" else if (state.accountLinked) "다시 연결" else "연결하기", {
            busy = true; message = null
            scope.launch {
                val r = controller.repo.linkAccount(name, key)
                busy = false
                message = r.exceptionOrNull()?.message ?: "연결됐어요! 프로필 ${controller.repo.state.value.profiles.size}개를 찾았어요."
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && name.length >= 3 && key.isNotBlank())
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = SbText.body(13, if (it.startsWith("연결됐")) Sb.Green else Sb.Negative))
        }
        Spacer(Modifier.height(10.dp))
        Text("키는 이 기기와 (켜 둔 경우) 내 GitHub 비공개 Gist 에만 저장돼요. 게임 내 /profile → API Settings 에서 " +
            "Skills·Inventory·Collections·Banking 을 켜면 더 많은 정보가 보여요.", style = SbText.body(12, Sb.TextFaint))
    }
}

@Composable
private fun SyncPanel(controller: AppController, state: AppState) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val sync = state.device.sync

    Panel(Modifier.fillMaxWidth(), caption = "Devices", title = "PC ↔ 휴대폰 연동", accent = Sb.Green,
        trailing = { if (sync.enabled) Pill(if (state.syncingDevices) "동기화 중…" else "연동됨", Sb.Green, filled = true) }) {
        Text("계정, 가격 알림, 관심 목록, 제작 목표, 알림 설정이 두 기기에서 똑같이 유지돼요. " +
            "서버 없이 내 GitHub 계정의 비공개 Gist 하나에 저장해요.", style = SbText.body(13, Sb.TextDim))
        Spacer(Modifier.height(12.dp))
        if (!sync.enabled) {
            Text("① 첫 기기: GitHub 토큰 (gist 권한)  ② 두 번째 기기: 첫 기기에 표시된 연동 코드", style = SbText.body(12, Sb.TextFaint))
            Spacer(Modifier.height(8.dp))
            SbTextField(input, { input = it.trim() }, Modifier.fillMaxWidth(), "GitHub 토큰 또는 SBH1: 로 시작하는 연동 코드", Icons.Rounded.Key, secret = !input.startsWith("SBH1:"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.clickable { platform.openUrl("https://github.com/settings/tokens/new?scopes=gist&description=SkyblockHelper%20Sync") },
                verticalAlignment = Alignment.CenterVertically) {
                Text("gist 권한 토큰 만들기 (GitHub)", style = SbText.body(12, Sb.Aqua))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Sb.Aqua, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SbButton(if (busy) "연결 중…" else "연동 시작", {
                    busy = true; error = null
                    scope.launch {
                        val r = controller.repo.enableSync(input)
                        busy = false
                        error = r.exceptionOrNull()?.message
                        if (r.isSuccess) { input = ""; controller.showToast("기기 연동을 켰어요") }
                    }
                }, Modifier.weight(1f), enabled = !busy && input.length > 10, icon = Icons.Rounded.CloudSync)
                platform.scanPairingCode?.let { scan ->
                    SbButton("QR 스캔", { scan { code -> input = code } }, icon = Icons.Rounded.QrCodeScanner, outlined = true)
                }
            }
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, style = SbText.body(13, Sb.Negative)) }
        } else {
            Text(state.syncMessage ?: "", style = SbText.body(13, if (state.syncMessage?.startsWith("⚠") == true) Sb.Negative else Sb.Green))
            if (sync.lastSyncAt > 0) Text("마지막 동기화 ${localTime(sync.lastSyncAt, true)}", style = SbText.body(12, Sb.TextFaint))
            Spacer(Modifier.height(10.dp))
            Text("자동 동기화 주기", style = SbText.caption(Sb.TextDim))
            Spacer(Modifier.height(6.dp))
            val syncOptions = listOf(1, 2, 5, 15, 30)
            Segmented(syncOptions.map { "${it}분" }, syncOptions.indexOf(state.device.syncMinutes).coerceAtLeast(0), { i ->
                controller.repo.updateDevice { it.copy(syncMinutes = syncOptions[i]) }
            })
            Text("설정을 바꾸면 3초 뒤 바로 올리고, 이 주기마다 다른 기기의 변경을 받아요.", style = SbText.body(12, Sb.TextFaint))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SbButton("지금 동기화", { scope.launch { controller.repo.syncDevices() } }, Modifier.weight(1f), icon = Icons.Rounded.CloudSync)
                SbButton("연동 해제", { controller.repo.disableSync() }, outlined = true, color = Sb.Red)
            }
            Spacer(Modifier.height(14.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text("다른 기기 추가하기", style = SbText.body(14, weight = FontWeight.SemiBold))
            Text("다른 기기의 [설정 → 연동]에서 아래 코드를 붙여 넣거나 QR 을 스캔하세요. 코드에는 토큰이 들어 있으니 다른 사람에게 보여 주지 마세요.",
                style = SbText.body(12, Sb.TextFaint))
            Spacer(Modifier.height(10.dp))
            val code = controller.repo.pairingCode()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SbButton("연동 코드 복사", {
                    code?.let { platform.copyToClipboard(it); controller.showToast("연동 코드를 복사했어요") }
                }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, outlined = true)
                SbButton(if (showQr) "QR 숨기기" else "QR 보기", { showQr = !showQr }, outlined = true)
            }
            if (showQr && code != null) {
                platform.qrMatrix(code)?.let { matrix ->
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { QrImage(matrix) }
                }
            }
        }
    }
}

@Composable
private fun QrImage(matrix: Array<BooleanArray>) {
    Canvas(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)) {
        val n = matrix.size
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[y][x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

@Composable
private fun BackgroundPanel(controller: AppController, state: AppState) {
    val platform = LocalPlatform.current
    val d = state.device
    Panel(Modifier.fillMaxWidth(), caption = "Background", title = "백그라운드 실행", accent = Sb.Gold) {
        SwitchRow("백그라운드에서 계속 실행", d.runInBackground, { on ->
            controller.repo.updateDevice { it.copy(runInBackground = on) }
            platform.onBackgroundSettingChanged(on)
        }, if (platform.isDesktop) "창을 닫아도 작업 표시줄 트레이에서 계속 알림을 보내요. 끄면 창을 닫을 때 완전히 종료돼요."
        else "앱을 닫아도 주기적으로 확인해서 알림을 보내요 (배터리 최적화에 따라 몇 분 늦을 수 있어요).")
        SwitchRow(if (platform.isDesktop) "컴퓨터 시작 시 자동 실행" else "휴대폰 재시작 후에도 알림 유지", d.startWithSystem, { on ->
            if (platform.setStartWithSystem(on)) controller.repo.updateDevice { it.copy(startWithSystem = on) }
            else controller.showToast("이 환경에서는 자동 실행을 설정할 수 없어요")
        }, enabled = d.runInBackground || !platform.isDesktop)
        if (platform.isDesktop) {
            SwitchRow("시작할 때 트레이로 숨기기", d.startMinimized, { on -> controller.repo.updateDevice { it.copy(startMinimized = on) } },
                enabled = d.runInBackground)
        }
        Spacer(Modifier.height(12.dp))
        Text("프로필 확인 주기", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        val options = listOf(1, 2, 5, 10)
        Segmented(options.map { "${it}분" }, options.indexOf(d.pollMinutes).coerceAtLeast(0), { i ->
            controller.repo.updateDevice { it.copy(pollMinutes = options[i]) }
        })
        Spacer(Modifier.height(6.dp))
        Text("포지·이벤트 알림은 정해진 시각에 바로 울리고, 이 주기는 새 포지/경매/스탯을 가져오는 간격이에요. " +
            "Hypixel 서버가 프로필을 저장해야 값이 바뀌어서, 게임과 1~3분 정도 차이가 날 수 있어요.", style = SbText.body(12, Sb.TextFaint))
        if (state.lastProfileSync > 0) Text("마지막 프로필 갱신 ${localTime(state.lastProfileSync, true)}", style = SbText.body(12, Sb.TextDim))
    }
}

@Composable
private fun DisplayPanel(controller: AppController, state: AppState) {
    val d = state.device
    Panel(Modifier.fillMaxWidth(), caption = "Display", title = "화면", accent = Sb.Purple) {
        // 글자 크기: 슬라이더 (놓았을 때 저장 — 끌고 있는 동안은 미리보기만)
        var scale by remember { mutableStateOf(d.textScale) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("글자 크기", style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(1f))
            Text("${kotlin.math.round(scale * 100).toInt()}%", style = SbText.pixel(12, Sb.Gold))
        }
        androidx.compose.material3.Slider(
            value = scale, onValueChange = { scale = (kotlin.math.round(it * 20) / 20f) },
            onValueChangeFinished = { controller.repo.updateDevice { it.copy(textScale = scale) } },
            valueRange = 0.9f..1.7f, steps = 15,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Sb.Gold, activeTrackColor = Sb.Gold, inactiveTrackColor = Sb.Border),
        )
        Text("가나다 ABC 123 — 이 정도 크기로 보여요", style = SbText.body(14).let { it.copy(fontSize = it.fontSize * (scale / d.textScale)) })
        Spacer(Modifier.height(8.dp))
        Text("글자 굵기 (글꼴은 그대로)", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("기본", "굵게", "아주 굵게"), d.fontWeight.coerceIn(0, 2), { i ->
            controller.repo.updateDevice { it.copy(fontWeight = i) }
        })
        Spacer(Modifier.height(12.dp))
        Text("가운데 화면 너비", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("기본", "넓게", "아주 넓게", "창 끝까지"), d.contentWidth.coerceIn(0, 3), { i ->
            controller.repo.updateDevice { it.copy(contentWidth = i) }
        })
        Spacer(Modifier.height(4.dp))
        Text("큰 모니터에서 양옆 빈 공간을 줄이고 내용을 더 넓게 보여 줘요. 휴대폰에서는 차이가 없어요.", style = SbText.body(12, Sb.TextFaint))
        if (LocalPlatform.current.isDesktop) {
            Spacer(Modifier.height(8.dp))
            SwitchRow("📖 위키를 전용 창으로 열기", d.wikiWindow, { on -> controller.repo.updateDevice { it.copy(wikiWindow = on) } },
                "Windows 의 Edge 로 탭·주소창 없는 위키 창 하나를 띄워 계속 다시 써요. 창의 '앱에서 보기' 를 누르면 앱 검색 화면으로. 끄면 기본 브라우저로 열어요.")
        }
        Divider(Modifier.padding(vertical = 10.dp))
        SwitchRow("🌱 농사대회 작물 표시", d.showContestCrops, { on ->
            controller.repo.updateDevice { it.copy(showContestCrops = on) }
            controller.scope.launch { controller.repo.refreshContests() }
        }, "비공식 출처(EliteBot, 농사 커뮤니티)예요. 공식 API·위키에는 작물 정보가 없어요.")
        SwitchRow("🌐 패치노트 한국어 번역", d.translatePatchNotes, { on -> controller.repo.updateDevice { it.copy(translatePatchNotes = on) } },
            "비공식 Google 번역을 써요. 번역이 안 되면 원문을 보여 줘요.")
    }
}

/** 업데이트: 자동 확인 · 지금 확인 · 업데이트 내역 (이번 버전만, 더 보기로 이전 버전) */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun UpdatePanel(controller: AppController, state: AppState) {
    val platform = LocalPlatform.current
    val d = state.device
    Panel(Modifier.fillMaxWidth(), caption = "Update · Changelog", title = "업데이트", accent = Sb.Gold) {
        SwitchRow("새 버전 자동 확인", d.autoUpdate, { on -> controller.repo.updateDevice { it.copy(autoUpdate = on) } },
            "새 버전이 나오면 알려 드려요.")
        val scope = rememberCoroutineScope()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("현재 버전 ${platform.versionName}", style = SbText.body(13, Sb.TextDim), modifier = Modifier.weight(1f))
            SbButton("업데이트 확인", {
                scope.launch {
                    val r = runCatching { controller.repo.checkForUpdate() }
                    controller.showToast(r.exceptionOrNull()?.message ?: r.getOrNull()?.let { "새 버전 ${it.version} 이 있어요" } ?: "최신 버전이에요")
                }
            }, outlined = true)
        }
        // 게임 모드 (PC): 설치·자동 업데이트
        platform.modManager?.let { mm ->
            Divider(Modifier.padding(vertical = 10.dp))
            val st by mm.status.collectAsState()
            Text("게임 모드 (Minecraft 26.2)", style = SbText.body(15, Sb.Text, FontWeight.SemiBold))
            Text("모드: $st", style = SbText.body(13, Sb.TextDim))
            Text("mods 폴더: " + d.modsDir.ifBlank { "자동 (%APPDATA%\\.minecraft\\mods)" }, style = SbText.body(12, Sb.TextFaint))
            Text("새 버전은 게임을 켤 때 모드가 직접 물어보고, 게임을 다시 시작하면 적용돼요.", style = SbText.body(12, Sb.TextFaint))
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SbButton("모드 설치", { mm.install() }, outlined = true)
                SbButton("mods 폴더 고르기", { mm.chooseFolder() }, outlined = true)
                SbButton("mods 폴더 열기", { mm.openFolder() }, outlined = true)
            }
        }
        ChangelogList(platform.versionName)
    }
}

@Composable
private fun NotificationPanel(controller: AppController, state: AppState) {
    val n = state.synced.notify
    val platform = LocalPlatform.current
    fun update(block: (sbhelper.core.data.NotifyPrefs) -> sbhelper.core.data.NotifyPrefs) =
        controller.repo.updateSynced { it.copy(notify = block(it.notify)) }

    Panel(Modifier.fillMaxWidth(), caption = "Notifications", title = "알림", accent = Sb.Pink,
        trailing = {
            SbButton("테스트", { platform.requestNotificationPermission(); controller.repo.sendTestNotification() },
                icon = Icons.Rounded.NotificationsActive, outlined = true, color = Sb.Pink)
        }) {
        SwitchRow("이 기기에서 알림 받기", state.device.notificationsOnThisDevice,
            { on -> controller.repo.updateDevice { it.copy(notificationsOnThisDevice = on) }; if (on) platform.requestNotificationPermission() },
            "PC 와 휴대폰 중 한쪽에서만 받고 싶을 때 끄세요.")
        SwitchRow("🔊 알림 소리", state.device.alertSound,
            { on -> controller.repo.updateDevice { it.copy(alertSound = on) }; platform.onAlertSettingsChanged() },
            if (platform.isDesktop) "알림이 뜰 때 짧은 소리를 내요. 게임을 전체화면으로 해서 알림 창이 가려질 때도 들려요."
            else "끄면 알림이 소리 없이 와요.")
        if (state.device.alertSound) {
            val dev = state.device
            Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text("소리 종류", style = SbText.caption(Sb.TextDim))
                Spacer(Modifier.height(4.dp))
                Segmented(sbhelper.core.util.Synth.TONES, dev.alertTone.coerceIn(0, sbhelper.core.util.Synth.TONES.lastIndex), { i ->
                    controller.repo.updateDevice { it.copy(alertTone = i) }
                    platform.playSound(sbhelper.core.util.Synth.alert(i, dev.alertVolume / 100f))
                })
                Spacer(Modifier.height(8.dp))
                var vol by remember(dev.alertVolume) { mutableStateOf(dev.alertVolume.toFloat()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("소리 크기", style = SbText.caption(Sb.TextDim), modifier = Modifier.weight(1f))
                    Text("${vol.toInt()}%", style = SbText.pixel(12, Sb.Gold))
                }
                androidx.compose.material3.Slider(
                    value = vol, onValueChange = { vol = (kotlin.math.round(it / 5) * 5) },
                    onValueChangeFinished = {
                        controller.repo.updateDevice { it.copy(alertVolume = vol.toInt()) }
                        platform.playSound(sbhelper.core.util.Synth.alert(dev.alertTone, vol / 100f))
                    },
                    valueRange = 0f..100f, steps = 19,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Sb.Gold, activeTrackColor = Sb.Gold, inactiveTrackColor = Sb.Border),
                )
                SbButton("미리 듣기", { platform.playSound(sbhelper.core.util.Synth.alert(dev.alertTone, vol / 100f)) },
                    icon = Icons.Rounded.NotificationsActive, outlined = true, color = Sb.Gold)
            }
        }
        if (!platform.isDesktop) {
            SwitchRow("📱 정시 알림", state.device.phoneExactAlerts,
                { on -> controller.repo.updateDevice { it.copy(phoneExactAlerts = on) }; platform.onAlertSettingsChanged() },
                "앱을 꺼 두어도 포지 완료·이벤트·타이머 시각에 정확히 알려요. 끄면 15분마다 확인하는 방식이라 늦을 수 있어요.")
        }
        // 방해 금지 시간대 (이 기기)
        SwitchRow("🌙 방해 금지 시간대", state.device.quietEnabled,
            { on -> controller.repo.updateDevice { it.copy(quietEnabled = on) } },
            "정한 시간에는 조용히. 이 기기에만 적용돼요.")
        if (state.device.quietEnabled) {
            val dev = state.device
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HourStepper("시작", dev.quietStartHour) { h -> controller.repo.updateDevice { it.copy(quietStartHour = h) } }
                Text("  ~  ", style = SbText.body(14, Sb.TextDim))
                HourStepper("끝", dev.quietEndHour) { h -> controller.repo.updateDevice { it.copy(quietEndHour = h) } }
            }
            Segmented(listOf("소리만 끄기", "알림도 안 띄우기"), dev.quietMode.coerceIn(0, 1), { i -> controller.repo.updateDevice { it.copy(quietMode = i) } })
            Text("알림 기록에는 그대로 남아요.", style = SbText.body(12, Sb.TextFaint), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        }
        Text("PC 에서 전체화면 게임 중 알림 창이 안 보이면: Windows 설정 → 시스템 → 알림 → '자동으로 방해 금지 켜기' 에서 " +
            "'게임을 할 때'·'전체 화면 모드' 를 끄거나, 우선순위 알림에 SkyblockHelper 를 추가하세요.", style = SbText.body(12, Sb.TextFaint))
        Divider(Modifier.padding(vertical = 6.dp))
        SwitchRow("⚒ 포지 완료", n.forge, { on -> update { it.copy(forge = on) } })
        SwitchRow("💰 내 경매 판매/종료", n.auctions, { on -> update { it.copy(auctions = on) } }, "일반 프로필만 해당")
        SwitchRow("📈 바자 가격 알림", n.priceAlerts, { on -> update { it.copy(priceAlerts = on) } })
        SwitchRow("📰 새 패치노트", n.patchNotes, { on -> update { it.copy(patchNotes = on) } }, "새 패치가 올라오면")
        Divider(Modifier.padding(vertical = 6.dp))
        Text("이벤트 (시작 ${n.eventLeadMinutes}분 전)", style = SbText.caption(Sb.TextDim))
        val leads = listOf(1, 3, 5, 10, 15)
        Spacer(Modifier.height(6.dp))
        Segmented(leads.map { "${it}분" }, leads.indexOf(n.eventLeadMinutes).coerceAtLeast(0), { i -> update { it.copy(eventLeadMinutes = leads[i]) } })
        Spacer(Modifier.height(4.dp))
        Text("시장 전용 이벤트는 그 시장일 때만 열려요.", style = SbText.body(12, Sb.TextFaint))
        val names = state.gameData.events.map { it.name }.distinct()
        Foldable("이벤트별 알림", "${names.count { n.eventEnabled(it) }} / ${names.size}개 켜짐") {
            names.forEach { name ->
                SwitchRow("${EventLabels.emoji(name)} ${EventLabels.label(name)}", n.eventEnabled(name),
                    { on -> update { it.copy(events = it.events + (name to on)) } })
            }
        }
        Foldable("다른 타이머 알림", "${sbhelper.core.calc.ExtraTimers.KINDS.keys.count { n.timerEnabled(it) }} / ${sbhelper.core.calc.ExtraTimers.KINDS.size}개 켜짐") {
            Text("필요 없는 타이머는 끄세요. 꺼도 타이머 화면에서는 보여요.", style = SbText.body(12, Sb.TextFaint))
            sbhelper.core.calc.ExtraTimers.KINDS.forEach { (key, label) ->
                SwitchRow(label, n.timerEnabled(key), { on -> update { it.copy(timers = it.timers + (key to on)) } })
            }
        }
        Divider(Modifier.padding(vertical = 6.dp))
        val wikiTax = state.gameData.rules.bazaarTaxPercent
        var tax by remember { mutableStateOf((state.synced.bazaarTaxPercent ?: wikiTax).toString()) }
        Text("바자 판매 수수료 (%) — 위키 기본값 ${wikiTax}%, Bazaar Flipper 업그레이드가 있으면 낮춰 주세요", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        SbTextField(tax, { v ->
            tax = v.filter { it.isDigit() || it == '.' }
            tax.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { t -> controller.repo.updateSynced { it.copy(bazaarTaxPercent = t) } }
        }, Modifier.width(140.dp), "1.25", numeric = true)
    }
}

@Composable
private fun AboutPanel(state: AppState) {
    val platform = LocalPlatform.current
    Panel(Modifier.fillMaxWidth(), caption = "About", title = "정보", accent = Sb.TextDim) {
        Text("SkyblockHelper ${platform.versionName}", style = SbText.body(14, weight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text("• 실시간 데이터: Hypixel 공식 API (api.hypixel.net), Mojang 공식 API", style = SbText.body(12, Sb.TextDim))
        Text("• 게임 정보(제작법, 포지, 이벤트, 경험치표, 진행 조건, 패치노트): 공식 Hypixel SkyBlock 위키 " +
            "(hypixelskyblock.minecraft.wiki, ${state.gameData.source.license}) — ${state.gameData.source.generated} 데이터",
            style = SbText.body(12, Sb.TextDim))
        Row(Modifier.clickable { platform.openUrl("https://hypixelskyblock.minecraft.wiki/") }, verticalAlignment = Alignment.CenterVertically) {
            Text("위키 열기", style = SbText.body(12, Sb.Aqua))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Sb.Aqua, modifier = Modifier.size(13.dp))
        }
        Text("• 글꼴: Pretendard, Galmuri (SIL Open Font License)", style = SbText.body(12, Sb.TextDim))
        Text("• Hypixel 과 무관한 비공식 팬 도구예요. 게임 규칙상 허용되지 않는 자동화 기능은 없어요.", style = SbText.body(12, Sb.TextFaint))
        Spacer(Modifier.height(10.dp))
        val controllerRef = LocalController.current
        SbButton("프로필 구조 복사 (문제 신고용)", {
            platform.copyToClipboard(controllerRef.repo.profileStructure())
            controllerRef.showToast("복사했어요. 개발자에게 붙여 넣어 주세요 (키 이름만, 값은 없어요)")
        }, outlined = true, color = Sb.TextDim)
        Text("API 키·코인 같은 값은 빼고 항목 이름만 복사해요. HotM·인벤토리 항목 이름을 맞추는 데 써요.", style = SbText.body(11, Sb.TextFaint))
    }
}


/** 눌러서 펼치는 목록 (설정이 너무 길어지지 않게) */
@Composable
private fun Foldable(title: String, summary: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Sb.SurfaceHigh).clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", style = SbText.body(14, Sb.TextDim))
        Spacer(Modifier.width(8.dp))
        Text(title, style = SbText.body(14, Sb.Text, androidx.compose.ui.text.font.FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text(summary, style = SbText.body(12, Sb.TextFaint))
    }
    if (open) Column(Modifier.padding(start = 8.dp, top = 4.dp)) { content() }
}

/** 앱 삭제: 확인 창 → 기기의 삭제 화면 */
@Composable
private fun RemoveAppPanel(controller: AppController) {
    val platform = LocalPlatform.current
    if (!platform.canUninstall) return
    var ask by remember { mutableStateOf(false) }
    var removeData by remember { mutableStateOf(true) }
    Panel(Modifier.fillMaxWidth(), caption = "Uninstall", title = "앱 삭제", accent = Sb.Red) {
        Text(if (platform.isDesktop) "이 PC 에서 SkyblockHelper 를 지워요. Windows 삭제 화면이 뜨고 앱은 꺼져요."
            else "이 휴대폰에서 SkyblockHelper 를 지워요. 설정·기록도 함께 지워져요.", style = SbText.body(13, Sb.TextDim))
        Spacer(Modifier.height(8.dp))
        SbButton("앱 삭제", { ask = true }, outlined = true, color = Sb.Red)
    }
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            containerColor = Sb.Surface,
            title = { Text("정말 지울까요?", style = SbText.body(17, Sb.Text, FontWeight.Bold)) },
            text = {
                Column {
                    Text("다른 기기와 연동해 두었다면 그쪽 설정(GitHub Gist)은 그대로 남아요.", style = SbText.body(13, Sb.TextDim))
                    if (platform.isDesktop) {
                        Spacer(Modifier.height(8.dp))
                        SwitchRow("설정·기록·알림 기록도 지우기", removeData, { removeData = it },
                            "끄면 다시 설치했을 때 그대로 이어서 쓸 수 있어요.")
                    }
                }
            },
            confirmButton = {
                SbButton("삭제", {
                    ask = false
                    platform.uninstall(removeData)?.let { controller.toast = it }
                }, color = Sb.Red)
            },
            dismissButton = { SbButton("취소", { ask = false }, outlined = true) },
        )
    }
}

/** 앱 업데이트 내역 (짧게) */
@Composable
private fun ChangelogList(current: String) {
    val entries = sbhelper.ui.rememberChangelog()
    if (entries.isEmpty()) return
    var all by remember { mutableStateOf(false) }
    // 기본은 이번 버전 내역만 (없으면 가장 최근 것)
    val head = entries.firstOrNull { it.version == current } ?: entries.first()
    Divider(Modifier.padding(vertical = 10.dp))
    Text("업데이트 내역", style = SbText.body(15, Sb.Gold, FontWeight.Bold))
    (if (all) entries else listOf(head)).forEach { e ->
        Text("v${e.version}", style = SbText.pixel(13, Sb.TextDim, bold = true), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        e.items.forEach { line ->
            Row(Modifier.padding(vertical = 1.dp)) {
                Text("•", style = SbText.body(14, Sb.Gold)); Spacer(Modifier.width(8.dp))
                Text(line, style = SbText.body(14, Sb.Text))
            }
        }
    }
    if (entries.size > 1) {
        Spacer(Modifier.height(8.dp))
        SbButton(if (all) "접기" else "이전 버전 더 보기", { all = !all }, outlined = true)
    }
}

/** 시(0~23) 고르기: − 23시 + */
@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.width(6.dp))
        Text("−", style = SbText.body(18, Sb.Gold, FontWeight.Bold),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange((hour + 23) % 24) }.padding(horizontal = 10.dp, vertical = 2.dp))
        Text("${hour}시", style = SbText.pixel(14, Sb.Text, bold = true), modifier = Modifier.widthIn(min = 40.dp))
        Text("+", style = SbText.body(18, Sb.Gold, FontWeight.Bold),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange((hour + 1) % 24) }.padding(horizontal = 10.dp, vertical = 2.dp))
    }
}

