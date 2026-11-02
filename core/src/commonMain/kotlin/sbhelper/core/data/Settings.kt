package sbhelper.core.data

import kotlinx.datetime.toLocalDateTime

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
    /** 제작 플래너에서 마지막으로 보던 목표 (다른 화면에 갔다 와도, 다시 켜도 그대로) */
    val craftCurrent: CraftTarget? = null,
    /** 아이언맨 진행 가이드에서 체크한 항목 (기기 간 동기화) */
    val ironmanChecked: Set<String> = emptySet(),
    /** 제작 플래너 Recipe tree 에서 끈 재료: 목표 아이템 → 끈 재료 ID (기기 간 동기화, 다시 켜도 유지) */
    val craftExcluded: Map<String, Set<String>> = emptyMap(),
    /** 오늘 할 일: 체크한 항목 키 → 체크한 시각 (초기화 시각이 지나면 다시 빈 칸) */
    val dailyDone: Map<String, Long> = emptyMap(),
    /** 오늘 할 일에 직접 추가한 항목 (매일 이 기기 시각 자정에 초기화) */
    val dailyCustom: List<String> = emptyList(),
    /** 오늘 할 일에서 숨긴 기본 항목 */
    val dailyHidden: Set<String> = emptySet(),
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
    /** 추가 타이머 키(ExtraTimers.KINDS) → 알림 켜짐 */
    val timers: Map<String, Boolean> = emptyMap(),
) {
    fun eventEnabled(name: String) = events[name] ?: EventLabels.enabledByDefault(name)
    fun timerEnabled(key: String) = timers[key] ?: sbhelper.core.calc.ExtraTimers.enabledByDefault(key)
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
    /** 알림이 뜰 때 소리 (PC: 차임 소리, 휴대폰: 소리 있는 알림) */
    val alertSound: Boolean = true,
    /** 마지막으로 '새로워진 점' 을 보여 준 앱 버전 (업데이트 뒤 한 번만 보여 주려고) */
    val lastSeenVersion: String = "",
    /** 알림 방해 금지 시간대 (이 기기, 현지 시각 기준 시) */
    val quietEnabled: Boolean = false,
    val quietStartHour: Int = 0,
    val quietEndHour: Int = 8,
    /** 0 = 소리만 끄기, 1 = 알림도 안 띄우기 (기록에는 남음) */
    val quietMode: Int = 0,
    /** 알림 소리 크기 0~100 */
    val alertVolume: Int = 70,
    /** 알림 소리 종류 (Synth.TONES 순서) */
    val alertTone: Int = 0,
    /** 휴대폰: 앱이 꺼져 있어도 정해진 시각에 정확히 알림 (정확한 알람) */
    val phoneExactAlerts: Boolean = true,
    val pollMinutes: Int = 1,
    /** 기기 간 동기화 주기(분) */
    val syncMinutes: Int = 2,
    /** 농사대회 작물 표시 (비공식 출처 EliteBot) */
    val showContestCrops: Boolean = true,
    /** 패치노트를 한국어로 번역해서 보기 */
    val translatePatchNotes: Boolean = true,
    /** 글자 크기 배율 */
    val textScale: Float = 1.15f,
    /** 글자 굵기: 0 = 기본, 1 = 굵게(기본값), 2 = 아주 굵게 */
    val fontWeight: Int = 1,
    /** 가운데 화면 최대 너비: 0 = 기본(1280), 1 = 넓게(1600), 2 = 아주 넓게(2000), 3 = 창 끝까지 */
    val contentWidth: Int = 0,
    /** PC: 위키를 전용 창(Edge 앱 모드)으로 열기 — 끄면 기본 브라우저 */
    val wikiWindow: Boolean = true,
    /** PC 게임 오버레이: Hypixel SkyBlock 에 들어가면 게임 화면 구석에 제작 플래너 */
    val overlayEnabled: Boolean = true,
    /** 제작 플래너: 자루 / 인벤토리 재료를 가진 것으로 셀지 (다시 켜도 유지) */
    val plannerUseSacks: Boolean = true,
    val plannerUseInventory: Boolean = true,
    /** 0 왼쪽 위, 1 오른쪽 위, 2 왼쪽 아래, 3 오른쪽 아래 */
    val overlayCorner: Int = 1,
    /** 배경 불투명도 % (화면에는 투명도 = 100 - 이 값 으로 보여 줌) */
    val overlayOpacity: Int = 80,
    /** overlayCorner = 4 (직접 지정) 일 때 위치: 게임 창 안에서 왼쪽 위 모서리의 비율 (0..1) */
    val overlayX: Float = -1f,
    val overlayY: Float = -1f,
    /** 오버레이 글씨 크기 % / 상자 너비(dp) / 재료 줄 수 */
    val overlayTextScale: Int = 100,
    val overlayWidth: Int = 270,
    val overlayRows: Int = 5,
    /** 게임을 켜면 자동으로 테두리 없는 전체 화면 (F11 대신, 오버레이가 항상 보임) */
    val overlayAutoBorderless: Boolean = false,
    val deviceName: String = "",
    val sync: SyncCredentials = SyncCredentials(),
    val onboardingDone: Boolean = false,
    /** 새 버전이 나왔는지 자동으로 확인 (설치는 물어보고 나서) */
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
    /** 바이너리 파일 (아이콘 묶음 등). 지원하지 않으면 null */
    fun readBytes(name: String): ByteArray? = null
    fun writeBytes(name: String, bytes: ByteArray) {}
}

/** 지금이 방해 금지 시간인가 (시작 > 끝이면 자정을 넘는 구간, 예: 23시~8시) */
fun DeviceSettings.isQuiet(nowMillis: Long): Boolean {
    if (!quietEnabled || quietStartHour == quietEndHour) return false
    val hour = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).hour
    return if (quietStartHour < quietEndHour) hour in quietStartHour until quietEndHour
    else hour >= quietStartHour || hour < quietEndHour
}

