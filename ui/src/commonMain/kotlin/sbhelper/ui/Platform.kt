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
    /** 이 플랫폼용 업데이트 파일 이름 끝 (예: ".exe", ".apk"). null 이면 자동 업데이트 미지원 */
    val updateAssetSuffix: String? get() = null
    /** 내려받은 업데이트 설치를 시작한다. 성공하면 true */
    fun installUpdate(bytes: ByteArray, fileName: String): Boolean = false
    /** 휴대폰에서 QR 스캔으로 연동 코드를 읽는다. 지원하지 않으면 null */
    val scanPairingCode: ((onResult: (String) -> Unit) -> Unit)? get() = null
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }

/** PNG 바이트 -> 이미지 */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
