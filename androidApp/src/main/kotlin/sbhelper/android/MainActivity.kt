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
        // 앱 안에서 바로 설치 (PackageInstaller). 안 되면 예전처럼 설치 화면을 연다
        override fun installUpdate(bytes: ByteArray, fileName: String): Boolean =
            SelfUpdate.install(this@MainActivity, bytes) || runCatching {
                val dir = java.io.File(cacheDir, "updates").apply { mkdirs() }
                val file = java.io.File(dir, fileName).apply { writeBytes(bytes) }
                val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.updates", file)
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
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
            val release = runCatching { app.repo.checkForUpdate() }.getOrNull()
            if (release != null && device.autoUpdate) UpdateFlow.run(controller, platform)
        }
        setContent {
            SkyblockApp(controller, platform, Modifier.safeDrawingPadding())
        }
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
