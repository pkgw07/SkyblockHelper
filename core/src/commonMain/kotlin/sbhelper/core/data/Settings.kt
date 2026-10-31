package sbhelper.core.data

import kotlinx.serialization.Serializable
import sbhelper.core.calc.EventLabels

/**
 * 기기 간에 동기화되는 설정 (PC ↔ 휴대폰).
 * updatedAt 이 더 최신인 쪽이 이긴다.
 */
@Serializable
data class SyncedSettings(
    val username: String = "",
    val playerUuid: String = "",
    val apiKey: String = "",
    val selectedProfileId: String = "",
    val notify: NotifyPrefs = NotifyPrefs(),
    /** null 이면 위키의 기본 수수료(gamedata.rules.bazaarTaxPercent) */
    val bazaarTaxPercent: Double? = null,
    val priceAlerts: List<PriceAlert> = emptyList(),
    val watchlist: List<String> = emptyList(),
    val craftTargets: List<CraftTarget> = emptyList(),
    val updatedAt: Long = 0,
)

@Serializable
data class NotifyPrefs(
    val forge: Boolean = true,
    val auctions: Boolean = true,
    val priceAlerts: Boolean = true,
    val patchNotes: Boolean = true,
    /** 위키 이벤트 이름 → 알림 켜짐. 없는 이벤트는 기본값(EventLabels.enabledByDefault) */
    val events: Map<String, Boolean> = emptyMap(),
    val eventLeadMinutes: Int = 5,
) {
    fun eventEnabled(name: String) = events[name] ?: EventLabels.enabledByDefault(name)
}

@Serializable
enum class PriceSide { INSTA_BUY, INSTA_SELL }

@Serializable
data class PriceAlert(
    val id: String,
    val itemId: String,
    val side: PriceSide = PriceSide.INSTA_BUY,
    /** true: 가격이 target 이상이 되면 / false: 이하가 되면 */
    val above: Boolean = false,
    val target: Double,
    val enabled: Boolean = true,
)

@Serializable
data class CraftTarget(val itemId: String, val amount: Int = 1)

/** 이 기기에만 해당하는 설정 */
@Serializable
data class DeviceSettings(
    /** 창을 닫아도 트레이/백그라운드에서 계속 알림 */
    val runInBackground: Boolean = true,
    /** 컴퓨터(또는 휴대폰) 시작 시 자동 실행 */
    val startWithSystem: Boolean = false,
    val startMinimized: Boolean = false,
    /** 이 기기에서 알림을 받을지 */
    val notificationsOnThisDevice: Boolean = true,
    val pollMinutes: Int = 3,
    val deviceName: String = "",
    val sync: SyncCredentials = SyncCredentials(),
    val onboardingDone: Boolean = false,
    /** 새 버전이 나오면 자동으로 내려받아 설치 */
    val autoUpdate: Boolean = true,
    /** 마지막으로 확인한 패치노트(위키 Changelog) 키 */
    val lastSeenPatch: String = "",
)

@Serializable
data class SyncCredentials(
    val githubToken: String = "",
    val gistId: String = "",
    val lastSyncAt: Long = 0,
) {
    val enabled get() = githubToken.isNotBlank()
}

/** 저장되는 앱 상태 전체 */
@Serializable
data class StoredState(
    val synced: SyncedSettings = SyncedSettings(),
    val device: DeviceSettings = DeviceSettings(),
    /** 이미 보낸 알림 키 (중복 방지) */
    val sentAlertKeys: List<String> = emptyList(),
    val history: List<NotificationRecord> = emptyList(),
)

@Serializable
data class NotificationRecord(
    val key: String,
    val title: String,
    val body: String,
    val category: String,
    val time: Long,
)

/** 플랫폼별 파일 저장소 (데스크톱: 홈 폴더, Android: 앱 내부 저장소) */
interface KeyValueStorage {
    fun read(name: String): String?
    fun write(name: String, text: String)
}
