package sbhelper.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import sbhelper.ui.AppController
import sbhelper.ui.AppInfo
import sbhelper.ui.Platform
import sbhelper.ui.SkyblockApp
import sbhelper.ui.UpdateFlow

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_TARGET = "sbhelper.target" }

    private val app get() = application as SbApplication
    private lateinit var controller: AppController
    private var scanCallback: ((String) -> Unit)? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanCallback?.invoke(it) }
        scanCallback = null
    }

    private val platform = object : Platform {
        override val isDesktop = false
        override val versionName = AppInfo.VERSION
        override fun openUrl(url: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        override fun copyToClipboard(text: String) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SkyblockHelper", text))
        }
        // WorkManager 예약은 재부팅 후에도 유지되므로 별도 설정이 필요 없다
        override fun setStartWithSystem(enabled: Boolean) = true
        override fun qrMatrix(text: String): Array<BooleanArray>? = runCatching {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
            Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
        }.getOrNull()
        override fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun onBackgroundSettingChanged(enabled: Boolean) {
            BackgroundScheduler.apply(this@MainActivity, enabled)
        }
        override val updateAssetSuffix: String = ".apk"
        // 앱 안 업데이트: 받아 두고 → "지금 설치하고 다시 시작할까요?" → 설치
        override val inAppUpdater: sbhelper.ui.InAppUpdater by lazy { AndroidUpdater(this@MainActivity) }
        override fun installUpdate(bytes: ByteArray, fileName: String): Boolean {
            val f = java.io.File(java.io.File(cacheDir, "updates").apply { mkdirs() }, fileName).apply { writeBytes(bytes) }
            return SelfUpdate.install(this@MainActivity, f)
        }
        override val scanPairingCode: ((onResult: (String) -> Unit) -> Unit) = { onResult ->
            scanCallback = onResult
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("PC 에 표시된 연동 QR 코드를 비춰 주세요").setBeepEnabled(false))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        controller = AppController(app.repo, app.scope)
        lifecycleScope.launch {
            app.ensureLoaded()
            val device = app.repo.state.value.device
            BackgroundScheduler.apply(this@MainActivity, device.runInBackground)
            if (device.notificationsOnThisDevice) platform.requestNotificationPermission()
            // 새 버전 확인 (자동 업데이트가 켜져 있으면 내려받고 설치 화면을 띄운다)
            val release = if (device.autoUpdate) runCatching { app.repo.checkForUpdate() }.getOrNull() else null
            // 확인만 한다. 받기·설치는 화면의 "업데이트할까요?" 에 동의해야
            if (release == null) Unit
        }
        setContent {
            SkyblockApp(controller, platform, Modifier.safeDrawingPadding())
        }
        // 휴대폰 뒤로 버튼: 앱 안 이전 화면, 더 없으면 원래대로
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!controller.back()) { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        intent?.getStringExtra(EXTRA_TARGET)?.let { controller.openTarget(it) }
        // 설치 진행·실패 안내를 화면에
        lifecycleScope.launch { SelfUpdate.status.collect { msg -> if (msg != null) app.repo.setUpdateMessage(msg) } }
        // 받아 둔 업데이트가 있으면 "다시 시작" 안내
        platform.inAppUpdater.stagedVersion?.let { app.repo.setUpdateStaged(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TARGET)?.let { controller.openTarget(it) }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            app.ensureLoaded()
            app.repo.startLoops()
        }
    }

    override fun onStop() {
        super.onStop()
        // 화면을 떠나면 실시간 갱신을 멈추고, 백그라운드 작업(WorkManager)에 맡긴다
        app.repo.stopLoops()
        val s = app.repo.state.value
        if (s.device.runInBackground) {
            BackgroundScheduler.scheduleNextFromState(this, s)
        }
    }
}
