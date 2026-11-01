package sbhelper.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** 플랫폼(데스크톱/Android)마다 다르게 동작하는 기능 */
interface Platform {
    val isDesktop: Boolean
    val versionName: String
    fun openUrl(url: String)
    fun copyToClipboard(text: String)
    /** 시스템 시작 시 자동 실행 설정. 성공하면 true */
    fun setStartWithSystem(enabled: Boolean): Boolean
    /** QR 코드 비트맵 (true = 검은 칸). 지원하지 않으면 null */
    fun qrMatrix(text: String): Array<BooleanArray>?
    /** 알림 권한 요청 (Android 13+) */
    fun requestNotificationPermission() {}
    /** 백그라운드 설정이 바뀌었을 때 (Android: WorkManager 예약/취소) */
    fun onBackgroundSettingChanged(enabled: Boolean) {}
    /** 알림 설정이 바뀌었을 때 (휴대폰: 정시 알람 다시 예약/취소) */
    fun onAlertSettingsChanged() {}
    /** 이 기기에서 앱을 지울 수 있는가 */
    val canUninstall: Boolean get() = false
    /**
     * 앱 삭제를 시작한다 (기기의 삭제 화면을 띄움).
     * @param removeData 설정·기록도 지우기 (휴대폰은 항상 같이 지워짐)
     * @return 화면에 보여 줄 안내 (없으면 null)
     */
    fun uninstall(removeData: Boolean): String? = null
    /** 소리 재생 (core Synth 로 만든 PCM). 지원하지 않으면 무시 */
    fun playSound(samples: ShortArray) {}
    /** 이 플랫폼용 업데이트 파일 이름 끝 (예: ".exe", ".apk"). null 이면 자동 업데이트 미지원 */
    val updateAssetSuffix: String? get() = null
    /** 내려받은 업데이트 설치를 시작한다. 성공하면 true */
    fun installUpdate(bytes: ByteArray, fileName: String): Boolean = false
    /** 앱 안 업데이트 (받아 두고 다시 시작하면 적용). null 이면 설치 파일 방식만 */
    val inAppUpdater: InAppUpdater? get() = null
    /** 휴대폰에서 QR 스캔으로 연동 코드를 읽는다. 지원하지 않으면 null */
    val scanPairingCode: ((onResult: (String) -> Unit) -> Unit)? get() = null
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }

/** PNG 바이트 -> 이미지 */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** 앱 안에서 받아 다시 시작하면 적용되는 업데이트 */
interface InAppUpdater {
    enum class Result { STAGED, NEEDS_INSTALLER, FAILED }

    /** 필요한 파일만 받아 둔다. [download] 는 주소 → 내용 (실패하면 null) */
    suspend fun stage(release: sbhelper.core.update.Release, download: suspend (String) -> ByteArray?): Result
    /** 다시 시작하면 적용될 버전 (없으면 null) */
    val stagedVersion: String?
    /** 프로그램을 다시 시작한다 */
    fun restart()
}
