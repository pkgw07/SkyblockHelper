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
            cell(1f) { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AccountPanel(controller, state)
                    SyncPanel(controller, state)
                }
            },
            cell(1f) { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    BackgroundPanel(controller, state)
                    NotificationPanel(controller, state)
                    AboutPanel(state)
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
        Spacer(Modifier.height(8.dp))
        Text("프로필 확인 주기", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.height(6.dp))
        val options = listOf(1, 3, 5, 10)
        Segmented(options.map { "${it}분" }, options.indexOf(d.pollMinutes).coerceAtLeast(0), { i ->
            controller.repo.updateDevice { it.copy(pollMinutes = options[i]) }
        })
        Spacer(Modifier.height(6.dp))
        Text("포지·이벤트 알림은 정해진 시각에 바로 울리고, 이 주기는 새 포지/경매를 발견하는 간격이에요.", style = SbText.body(12, Sb.TextFaint))
        Divider(Modifier.padding(vertical = 10.dp))
        SwitchRow("자동 업데이트", d.autoUpdate, { on -> controller.repo.updateDevice { it.copy(autoUpdate = on) } },
            if (platform.isDesktop) "새 버전이 나오면 프로그램을 켤 때 자동으로 내려받아 설치해요."
            else "새 버전이 나오면 내려받아 설치 화면을 띄워요.")
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
        Divider(Modifier.padding(vertical = 6.dp))
        SwitchRow("⚒ 포지 완료", n.forge, { on -> update { it.copy(forge = on) } })
        SwitchRow("💰 내 경매 판매/종료", n.auctions, { on -> update { it.copy(auctions = on) } }, "일반 프로필만 해당")
        SwitchRow("📈 바자 가격 알림", n.priceAlerts, { on -> update { it.copy(priceAlerts = on) } })
        SwitchRow("📰 새 패치노트", n.patchNotes, { on -> update { it.copy(patchNotes = on) } }, "공식 위키 Changelog 에 새 패치가 올라오면")
        Divider(Modifier.padding(vertical = 6.dp))
        Text("이벤트 (시작 ${n.eventLeadMinutes}분 전)", style = SbText.caption(Sb.TextDim))
        val leads = listOf(1, 3, 5, 10, 15)
        Spacer(Modifier.height(6.dp))
        Segmented(leads.map { "${it}분" }, leads.indexOf(n.eventLeadMinutes).coerceAtLeast(0), { i -> update { it.copy(eventLeadMinutes = leads[i]) } })
        Spacer(Modifier.height(4.dp))
        Text("일정은 공식 위키 Events 페이지 기준이에요. 시장 전용 이벤트는 그 시장일 때만 열려요.", style = SbText.body(12, Sb.TextFaint))
        state.gameData.events.map { it.name }.distinct().forEach { name ->
            SwitchRow("${EventLabels.emoji(name)} ${EventLabels.label(name)}", n.eventEnabled(name),
                { on -> update { it.copy(events = it.events + (name to on)) } })
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
    }
}
