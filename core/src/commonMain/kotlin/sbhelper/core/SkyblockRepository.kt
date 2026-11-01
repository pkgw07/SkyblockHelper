package sbhelper.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sbhelper.core.api.ApiException
import sbhelper.core.api.HypixelApi
import sbhelper.core.api.PatchNote
import sbhelper.core.api.SbJson
import sbhelper.core.api.WikiApi
import sbhelper.core.calc.EventOccurrence
import sbhelper.core.calc.ForgeCalc
import sbhelper.core.calc.ForgeSlotStatus
import sbhelper.core.calc.SkyblockCalendar
import sbhelper.core.data.DeviceSettings
import sbhelper.core.data.GameData
import sbhelper.core.data.KeyValueStorage
import sbhelper.core.data.NotificationRecord
import sbhelper.core.data.PriceAlert
import sbhelper.core.data.StoredState
import sbhelper.core.data.SyncedSettings
import sbhelper.core.model.Auction
import sbhelper.core.model.BazaarResponse
import sbhelper.core.model.CollectionsResponse
import sbhelper.core.model.ElectionResponse
import sbhelper.core.model.FireSale
import sbhelper.core.model.NewsItem
import sbhelper.core.model.ProfileParser
import sbhelper.core.model.ProfileSummary
import sbhelper.core.model.SkillsResponse
import sbhelper.core.model.SkyblockItem
import sbhelper.core.notify.AlertEngine
import sbhelper.core.notify.AlertInputs
import sbhelper.core.notify.AppNotification
import sbhelper.core.notify.Notifier
import sbhelper.core.sync.GistSync
import sbhelper.core.sync.PairingCode
import sbhelper.core.update.Release
import sbhelper.core.update.Updates
import sbhelper.core.util.Clock
import sbhelper.core.util.prettifyId

/** 화면이 보는 앱 전체 상태 */
data class AppState(
    val stored: StoredState = StoredState(),
    val gameData: GameData = GameData.EMPTY,
    val items: Map<String, SkyblockItem> = emptyMap(),
    val skills: SkillsResponse? = null,
    val collections: CollectionsResponse? = null,
    val election: ElectionResponse? = null,
    val news: List<NewsItem> = emptyList(),
    val fireSales: List<FireSale> = emptyList(),
    val bazaar: BazaarResponse? = null,
    val profiles: List<ProfileSummary> = emptyList(),
    val myAuctions: List<Auction> = emptyList(),
    val lastProfileSync: Long = 0,
    val loadingProfile: Boolean = false,
    val syncingDevices: Boolean = false,
    val profileError: String? = null,
    val networkError: String? = null,
    val syncMessage: String? = null,
    /** 위키 Changelog 최신 패치노트 */
    val patchNotes: List<PatchNote> = emptyList(),
    /** 새 앱 버전 (없으면 null) */
    val availableUpdate: Release? = null,
    /** 업데이트 다운로드 진행도 (null = 진행 안 함) */
    val updateProgress: Double? = null,
    val updateMessage: String? = null,
    /** 제이콥 농사대회 시작 시각 → 작물 (비공식: EliteBot) */
    val jacobCrops: Map<Long, List<String>> = emptyMap(),
    /** 경매장 아이템별 최저 BIN (순자산·장신구 가격용) */
    val lowestBin: Map<String, Double> = emptyMap(),
    val lowestBinAt: Long = 0,
    val loadingPrices: Boolean = false,
) {
    val priceBook get() = sbhelper.core.calc.PriceBook(bazaar?.products.orEmpty(), lowestBin, npcPrices)

    /** 이 대회의 작물 (시작 시각이 1분 안으로 맞는 것) */
    fun cropsAt(start: Long): List<String>? =
        jacobCrops.entries.firstOrNull { kotlin.math.abs(it.key - start) < 60_000 }?.value

    /** 농사대회 이벤트에만 작물을 붙인다 */
    fun cropsFor(e: EventOccurrence): List<String>? = if (e.name.contains("Jacob")) cropsAt(e.start) else null
    val synced get() = stored.synced
    val device get() = stored.device
    val accountLinked get() = synced.playerUuid.isNotBlank()

    val selectedProfile: ProfileSummary?
        get() = profiles.firstOrNull { it.profileId == synced.selectedProfileId }
            ?: profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()

    fun itemName(id: String): String = items[id]?.name ?: gameData.names[id] ?: when {
        id.startsWith("name:") -> id.removePrefix("name:")
        id.startsWith("pet:") -> id.removePrefix("pet:") + " Pet"
        else -> prettifyId(id)
    }
    fun itemTier(id: String): String? = items[id]?.tier

    val npcPrices: Map<String, Double>
        get() = items.values.mapNotNull { i -> i.npcSellPrice?.takeIf { it > 0 }?.let { i.id to it } }.toMap()

    fun forgeStatuses(): List<ForgeSlotStatus> {
        val p = selectedProfile ?: return emptyList()
        val termStart = SkyblockCalendar.currentTermStart(gameData, sbhelper.core.util.Clock.now())
        return p.forge.map { ForgeCalc.status(it, gameData, itemName(it.itemId), p.quickForgeLevel, election, termStart) }
    }

    fun upcomingEvents(now: Long): List<EventOccurrence> = SkyblockCalendar.upcoming(now, gameData, election)

    /** 바자 판매 수수료(비율). 위키 기본값 + 시장 효과(예: Derpy 4배) */
    val bazaarTaxRate: Double
        get() {
            val base = synced.bazaarTaxPercent ?: gameData.rules.bazaarTaxPercent
            val mult = election?.mayor?.name?.let { gameData.rules.taxMultiplierByMayor[it] } ?: 1.0
            return base * mult / 100.0
        }
}

/**
 * 앱의 두뇌. 데이터를 불러오고, 주기적으로 새로 고치고, 알림을 판단하고, 기기 간 동기화를 한다.
 * 데스크톱과 Android 가 똑같이 이 클래스를 쓴다.
 */
class SkyblockRepository(
    val api: HypixelApi,
    private val gistSync: GistSync,
    private val storage: KeyValueStorage,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val loadGameData: suspend () -> String,
    val wiki: WikiApi = WikiApi(),
    private val eliteBot: sbhelper.core.api.EliteBotApi = sbhelper.core.api.EliteBotApi(),
    private val translator: sbhelper.core.api.Translator = sbhelper.core.api.Translator(),
    private val updates: Updates = Updates(),
    /** 이 앱의 버전 (업데이트 비교용) */
    private val appVersion: String = "0.0.0",
) {
    private val _state = MutableStateFlow(AppState())
    /** 진단용 원본 (화면 상태에는 넣지 않음) */
    private var lastRawProfiles: List<kotlinx.serialization.json.JsonObject> = emptyList()
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val saveLock = Mutex()
    private var loops: List<Job> = emptyList()
    private var syncJob: Job? = null

    // ------------------------------------------------------------------ 시작
    suspend fun load() {
        val stored = storage.read(STATE_FILE)?.let {
            runCatching { SbJson.decodeFromString(StoredState.serializer(), it) }.getOrNull()
        } ?: StoredState()
        // 최신 위키 데이터(이전에 받아 둔 것)가 있으면 그것을, 없으면 앱에 들어 있는 것을 쓴다
        val bundled = runCatching { GameData.parse(loadGameData()) }.getOrDefault(GameData.EMPTY)
        val cached = storage.read(GAMEDATA_FILE)?.let { runCatching { GameData.parse(it) }.getOrNull() }
        val data = if (cached != null && cached.source.generated > bundled.source.generated) cached else bundled
        applyGameData(data)
        _state.update { it.copy(stored = stored) }
    }

    private fun applyGameData(data: GameData) {
        SkyblockCalendar.epoch = data.rules.skyblockEpochSeconds * 1000
        _state.update { it.copy(gameData = data) }
    }

    /** 매일 위키에서 새로 만든 게임 데이터를 받는다 (GitHub data 브랜치) */
    suspend fun refreshGameData() {
        val text = updates.latestGameData() ?: return
        val data = runCatching { GameData.parse(text) }.getOrNull() ?: return
        if (data.forge.isEmpty() || data.events.isEmpty()) return
        if (data.source.generated > state.value.gameData.source.generated) {
            storage.write(GAMEDATA_FILE, text)
            applyGameData(data)
        }
    }

    /** 농사대회 작물 (비공식 출처, 설정에서 끌 수 있음) */
    suspend fun refreshContests() {
        if (!state.value.device.showContestCrops) { _state.update { it.copy(jacobCrops = emptyMap()) }; return }
        val crops = eliteBot.upcomingContests()
        if (crops.isNotEmpty()) _state.update { it.copy(jacobCrops = crops) }
    }

    /**
     * 패치노트 본문 한국어 번역 (비공식: Google 번역).
     * 번역된 줄만 저장하고, 실패한 줄(null)은 다음에 다시 시도한다. 돌려주는 목록의 null = 원문 그대로 보여 줄 줄.
     */
    suspend fun translatePatch(page: String, lines: List<String>): List<String?> {
        val ser = kotlinx.serialization.serializer<Map<String, List<String?>>>()
        val cache = storage.read(TRANSLATION_FILE)?.let { runCatching { SbJson.decodeFromString(ser, it) }.getOrNull() }.orEmpty()
        val prev = cache[page]?.takeIf { it.size == lines.size }
        val missing = lines.indices.filter { prev?.getOrNull(it) == null && lines[it].isNotBlank() }.toSet()
        if (prev != null && missing.isEmpty()) return prev
        val fresh = translator.toKorean(lines, if (prev == null) null else missing)
        val merged = lines.indices.map { prev?.getOrNull(it) ?: fresh[it] }
        val updated = (cache + (page to merged)).entries.toList().takeLast(60).associate { it.toPair() }
        storage.write(TRANSLATION_FILE, SbJson.encodeToString(ser, updated))
        return merged
    }

    /** 위키 Changelog 의 최신 패치노트 */
    suspend fun refreshPatchNotes() {
        val year = kotlinx.datetime.Instant.fromEpochMilliseconds(Clock.now()).toString().take(4).toIntOrNull() ?: return
        val notes = wiki.patchNotes(year)
        if (notes.isNotEmpty()) _state.update { it.copy(patchNotes = notes) }
    }

    /** GitHub Releases 에서 새 버전 확인 */
    suspend fun checkForUpdate(): Release? {
        val latest = updates.latestRelease() ?: return null
        val newer = latest.takeIf { Updates.isNewer(it.version, appVersion) }
        _state.update { it.copy(availableUpdate = newer) }
        return newer
    }

    /** 업데이트 파일 다운로드 (설치는 플랫폼이 담당) */
    suspend fun downloadUpdate(assetUrl: String): ByteArray? = try {
        _state.update { it.copy(updateProgress = 0.0, updateMessage = "업데이트를 내려받는 중…") }
        val bytes = updates.download(assetUrl) { p -> _state.update { it.copy(updateProgress = p) } }
        _state.update { it.copy(updateProgress = 1.0, updateMessage = "설치를 시작해요…") }
        bytes
    } catch (e: ApiException) {
        _state.update { it.copy(updateProgress = null, updateMessage = "⚠ ${e.message}") }
        null
    }

    fun setUpdateMessage(message: String?) = _state.update { it.copy(updateMessage = message, updateProgress = null) }

    /** 앱이 실행되는 동안 도는 주기 작업들 */
    fun startLoops() {
        if (loops.isNotEmpty()) return
        loops = listOf(
            every(6 * 3_600_000L) { refreshStatic() },
            every(5 * 60_000L) { refreshElectionAndNews() },
            every(60_000L) { refreshBazaar() },
            scope.launch {
                while (isActive) {
                    refreshProfile()
                    delay(state.value.device.pollMinutes.coerceIn(1, 60) * 60_000L)
                }
            },
            every(10_000L) { checkAlerts() },
            scope.launch {
                while (isActive) {
                    if (state.value.device.sync.enabled) runCatching { syncDevices() }
                    delay(state.value.device.syncMinutes.coerceIn(1, 60) * 60_000L)
                }
            },
            every(30 * 60_000L) { refreshContests() },
            every(30 * 60_000L) { refreshPatchNotes() },
            every(6 * 3_600_000L) { refreshGameData() },
            every(30 * 60_000L) { if (state.value.accountLinked) refreshLowestBins() },
        )
    }

    fun stopLoops() { loops.forEach { it.cancel() }; loops = emptyList() }

    private fun every(periodMs: Long, block: suspend () -> Unit) = scope.launch {
        while (isActive) {
            runCatching { block() }
            delay(periodMs)
        }
    }

    /**
     * 백그라운드 1회 실행 (Android WorkManager 등). 데이터를 새로 받고 알림을 보낸다.
     * @return 다음에 깨어나야 할 시각 (없으면 null)
     */
    suspend fun backgroundTick(): Long? {
        if (state.value.items.isEmpty()) runCatching { refreshStatic() }
        runCatching { refreshElectionAndNews() }
        if (state.value.synced.priceAlerts.any { it.enabled }) runCatching { refreshBazaar() }
        runCatching { refreshProfile() }
        if (state.value.device.sync.enabled) runCatching { syncDevices() }
        runCatching { refreshPatchNotes() }
        runCatching { refreshContests() }
        checkAlerts()
        return AlertEngine.nextWakeUp(alertInputs(state.value, Clock.now()), state.value.synced.notify, Clock.now())
    }

    // ------------------------------------------------------------------ 데이터 새로 고침
    suspend fun refreshStatic() {
        val items = api.items().items.associateBy { it.id }
        val skills = runCatching { api.skills() }.getOrNull()
        val collections = runCatching { api.collections() }.getOrNull()
        _state.update { it.copy(items = items, skills = skills ?: it.skills, collections = collections ?: it.collections, networkError = null) }
    }

    suspend fun refreshElectionAndNews() {
        val election = runCatching { api.election() }.getOrNull()
        val news = runCatching { api.news().items }.getOrNull()
        val sales = runCatching { api.fireSales().sales }.getOrNull()
        _state.update { s ->
            s.copy(election = election ?: s.election, news = news ?: s.news, fireSales = sales ?: s.fireSales)
        }
    }

    suspend fun refreshBazaar() {
        try {
            val bz = api.bazaar()
            _state.update { it.copy(bazaar = bz, networkError = null) }
        } catch (e: ApiException) {
            _state.update { it.copy(networkError = e.message) }
        }
    }

    suspend fun refreshProfile() {
        val s = state.value
        if (!s.accountLinked || s.synced.apiKey.isBlank()) return
        _state.update { it.copy(loadingProfile = true) }
        try {
            val uuid = s.synced.playerUuid
            val raw = api.profiles(uuid)
            lastRawProfiles = raw
            val profiles = raw.mapNotNull { ProfileParser.parse(it, uuid) }
                .sortedByDescending { it.selected }
            val selected = profiles.firstOrNull { it.profileId == s.synced.selectedProfileId }
                ?: profiles.firstOrNull { it.selected }
            val auctions = if (selected?.gameMode?.canTrade == true) {
                runCatching { api.playerAuctions(uuid).filter { it.profileId.isBlank() || it.profileId == selected.profileId } }.getOrDefault(emptyList())
            } else emptyList()
            _state.update {
                it.copy(profiles = profiles, myAuctions = auctions, lastProfileSync = Clock.now(),
                    loadingProfile = false, profileError = null)
            }
        } catch (e: ApiException) {
            _state.update { it.copy(loadingProfile = false, profileError = e.message) }
        }
    }

    /** 화면 미리보기·스크린샷 전용: 예시 프로필을 넣는다 */
    fun previewProfiles(profiles: List<ProfileSummary>) {
        _state.update { it.copy(profiles = profiles, lastProfileSync = Clock.now()) }
    }

    /** 경매장 전체를 읽어 아이템별 최저 BIN 을 만든다 (무거워서 30분마다) */
    suspend fun refreshLowestBins(force: Boolean = false) {
        if (!force && Clock.now() - state.value.lowestBinAt < 30 * 60_000L) return
        _state.update { it.copy(loadingPrices = true) }
        try {
            val bins = sbhelper.core.calc.PriceBook.lowestBins(api.allAuctions())
            _state.update { it.copy(lowestBin = bins, lowestBinAt = Clock.now(), loadingPrices = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loadingPrices = false) }
        }
    }

    /** 진단: 마지막으로 받은 내 프로필의 키 구조 (값은 빼고) */
    fun profileStructure(): String {
        val uuid = state.value.synced.playerUuid.replace("-", "")
        val profile = lastRawProfiles.firstOrNull { (it["profile_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == state.value.selectedProfile?.profileId }
            ?: lastRawProfiles.firstOrNull() ?: return "프로필을 아직 불러오지 못했어요."
        val member = (profile["members"] as? kotlinx.serialization.json.JsonObject)?.get(uuid)
        return "SkyblockHelper ${appVersion} profile structure\n" + ProfileParser.structure(member, maxDepth = 4)
    }

    // ------------------------------------------------------------------ 계정
    /** 닉네임 + API 키로 계정 연결 */
    suspend fun linkAccount(username: String, apiKey: String): Result<Unit> = runCatching {
        val profile = api.uuidFor(username)
        updateSynced { it.copy(username = profile.name, playerUuid = profile.id, apiKey = apiKey.trim(), selectedProfileId = "") }
        api.clearCache()
        refreshProfile()
        state.value.profileError?.let { throw ApiException(it) }
    }

    fun unlinkAccount() {
        updateSynced { it.copy(username = "", playerUuid = "", selectedProfileId = "") }
        _state.update { it.copy(profiles = emptyList(), myAuctions = emptyList()) }
    }

    fun selectProfile(profileId: String) {
        updateSynced { it.copy(selectedProfileId = profileId) }
        scope.launch { refreshProfile() }
    }

    // ------------------------------------------------------------------ 설정
    fun updateSynced(transform: (SyncedSettings) -> SyncedSettings) {
        _state.update { s ->
            s.copy(stored = s.stored.copy(synced = transform(s.stored.synced).copy(updatedAt = Clock.now())))
        }
        persist()
        scheduleSync()
    }

    fun updateDevice(transform: (DeviceSettings) -> DeviceSettings) {
        _state.update { s -> s.copy(stored = s.stored.copy(device = transform(s.stored.device))) }
        persist()
    }

    fun addPriceAlert(alert: PriceAlert) = updateSynced { it.copy(priceAlerts = it.priceAlerts + alert) }
    fun removePriceAlert(id: String) = updateSynced { it.copy(priceAlerts = it.priceAlerts.filterNot { a -> a.id == id }) }
    fun toggleWatch(itemId: String) = updateSynced {
        it.copy(watchlist = if (itemId in it.watchlist) it.watchlist - itemId else it.watchlist + itemId)
    }

    fun clearHistory() {
        _state.update { s -> s.copy(stored = s.stored.copy(history = emptyList())) }
        persist()
    }

    // ------------------------------------------------------------------ 기기 연동
    fun pairingCode(): String? {
        val sync = state.value.device.sync
        return if (sync.enabled) PairingCode.encode(sync.githubToken, sync.gistId) else null
    }

    /** GitHub 토큰 또는 다른 기기의 연동 코드로 연동을 켠다 */
    suspend fun enableSync(tokenOrCode: String): Result<Unit> = runCatching {
        val decoded = PairingCode.decode(tokenOrCode)
        val token = decoded?.first ?: tokenOrCode.trim()
        val gistId = decoded?.second.orEmpty()
        updateDevice { it.copy(sync = it.sync.copy(githubToken = token, gistId = gistId)) }
        syncDevices()
        state.value.syncMessage?.takeIf { it.startsWith("⚠") }?.let { throw ApiException(it) }
    }

    fun disableSync() = updateDevice { it.copy(sync = it.sync.copy(githubToken = "", gistId = "")) }

    suspend fun syncDevices() {
        val s = state.value
        val creds = s.device.sync
        if (!creds.enabled) return
        _state.update { it.copy(syncingDevices = true) }
        try {
            val (merged, gistId) = gistSync.sync(creds.githubToken, creds.gistId, s.synced)
            val remoteWon = merged.updatedAt > s.synced.updatedAt
            _state.update { st ->
                st.copy(
                    stored = st.stored.copy(
                        synced = if (remoteWon) merged else st.stored.synced,
                        device = st.stored.device.copy(sync = creds.copy(gistId = gistId, lastSyncAt = Clock.now())),
                    ),
                    syncingDevices = false,
                    syncMessage = if (remoteWon) "다른 기기의 설정을 받아왔어요." else "동기화 완료",
                )
            }
            persist()
            if (remoteWon && merged.playerUuid != s.synced.playerUuid) refreshProfile()
        } catch (e: ApiException) {
            _state.update { it.copy(syncingDevices = false, syncMessage = "⚠ ${e.message}") }
        }
    }

    private fun scheduleSync() {
        if (!state.value.device.sync.enabled) return
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(3_000) // 연달아 바꿀 때 한 번만 올리도록
            syncDevices()
        }
    }

    // ------------------------------------------------------------------ 알림
    private fun alertInputs(s: AppState, now: Long) = AlertInputs(
        profileId = s.selectedProfile?.profileId,
        forge = s.forgeStatuses(),
        events = s.upcomingEvents(now),
        myAuctions = s.myAuctions,
        bazaar = s.bazaar?.products.orEmpty(),
        priceAlerts = s.synced.priceAlerts,
        itemName = s::itemName,
        patchNotes = s.patchNotes,
        jacobCrops = s.jacobCrops,
    )

    suspend fun checkAlerts() {
        val now = Clock.now()
        val s = state.value
        val sent = s.stored.sentAlertKeys.toSet()
        val result = AlertEngine.evaluate(alertInputs(s, now), s.synced.notify, sent, now)
        if (result.notifications.isEmpty() && result.forgetKeys.none { it in sent }) return
        _state.update { st ->
            val newKeys = (st.stored.sentAlertKeys.filterNot { it in result.forgetKeys } + result.notifications.map { it.key })
                .takeLast(MAX_KEYS)
            val history = (result.notifications.map { it.toRecord() } + st.stored.history).take(MAX_HISTORY)
            st.copy(stored = st.stored.copy(sentAlertKeys = newKeys, history = history))
        }
        persist()
        if (s.device.notificationsOnThisDevice) result.notifications.forEach { runCatching { notifier.show(it) } }
    }

    /** 설정 화면의 "테스트 알림" */
    fun sendTestNotification() {
        notifier.show(AppNotification("test:${Clock.now()}", "🔔 테스트 알림", "알림이 정상적으로 동작해요!",
            sbhelper.core.notify.AlertCategory.SYSTEM, Clock.now()))
    }

    private fun AppNotification.toRecord() = NotificationRecord(key, title, body, category.name, time)

    // ------------------------------------------------------------------ 저장
    private fun persist() {
        val snapshot = state.value.stored
        scope.launch {
            saveLock.withLock {
                storage.write(STATE_FILE, SbJson.encodeToString(StoredState.serializer(), snapshot))
            }
        }
    }

    companion object {
        const val STATE_FILE = "state.json"
        const val GAMEDATA_FILE = "gamedata.json"
        const val TRANSLATION_FILE = "translations.json"
        private const val MAX_KEYS = 3000
        private const val MAX_HISTORY = 200
    }
}
