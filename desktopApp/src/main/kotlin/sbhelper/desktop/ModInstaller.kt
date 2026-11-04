package sbhelper.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sbhelper.core.SkyblockRepository
import sbhelper.core.update.Updates
import java.io.File

/**
 * 게임 모드 설치·자동 업데이트.
 *  - mods 폴더: 모드가 연결될 때 알려 준 곳 → 없으면 기본 %APPDATA%\.minecraft\mods
 *  - 최신 릴리스의 SkyblockHelper-mod-버전.jar 가 더 새것이면 받아서 예전 파일과 바꾼다
 *  - 게임이 켜져 있으면 파일이 잠겨 있으니, 게임을 끈 뒤에 바꾼다
 */
object ModInstaller {
    private val _status = MutableStateFlow("확인 중…")
    val status: StateFlow<String> = _status
    private val JAR = Regex("""^SkyblockHelper-mod-([0-9][0-9A-Za-z.\-+]*)\.jar$""")
    private val lock = Mutex()
    private lateinit var repo: SkyblockRepository
    private lateinit var scope: CoroutineScope

    fun start(scope: CoroutineScope, repo: SkyblockRepository) {
        this.repo = repo; this.scope = scope
        scope.launch {
            delay(15_000) // 앱 시작 직후 업데이트 확인이 끝난 뒤
            while (true) {
                runCatching { check(install = false) }
                delay(3 * 3_600_000L)
            }
        }
        // 게임을 끄면 미뤄 둔 업데이트를 바로
        scope.launch {
            var wasRunning = false
            GameWatch.state.collect { st ->
                if (wasRunning && !st.running) runCatching { check(install = false) }
                wasRunning = st.running
            }
        }
    }

    fun install() { if (::scope.isInitialized) scope.launch { runCatching { check(install = true) }.onFailure { _status.value = "설치하지 못했어요: ${it.message}" } } }

    fun modsDir(): File? {
        val d = repo.state.value.device.modsDir
        if (d.isNotBlank()) return File(d)
        val mc = System.getenv("APPDATA")?.let { File(it, ".minecraft") } ?: File(System.getProperty("user.home"), ".minecraft")
        return File(mc, "mods").takeIf { mc.isDirectory }
    }

    fun openFolder() {
        val dir = modsDir() ?: return
        dir.mkdirs()
        runCatching { java.awt.Desktop.getDesktop().open(dir) }
    }

    /** mods 폴더에 있는 이 모드 파일들 (버전과 함께) */
    private fun installed(dir: File): List<Pair<File, String>> =
        dir.listFiles().orEmpty().mapNotNull { f -> JAR.find(f.name)?.let { f to it.groupValues[1] } }

    /** @param install true: 설치 안 돼 있어도 설치 (버튼). false: 이미 설치돼 있을 때만 업데이트 (자동) */
    suspend fun check(install: Boolean) = lock.withLock {
        val dir = modsDir()
        if (dir == null) { _status.value = "마인크래프트 폴더를 못 찾았어요 · 게임에서 모드를 한 번 켜면 위치를 알 수 있어요"; return }
        val have = installed(dir)
        val haveVer = have.map { it.second }.maxWithOrNull { a, b -> if (Updates.isNewer(a, b)) 1 else if (Updates.isNewer(b, a)) -1 else 0 }
        val rel = repo.latestRelease ?: runCatching { repo.checkForUpdate() }.getOrNull().let { repo.latestRelease }
        val asset = rel?.assets?.firstOrNull { JAR.matches(it.name) }
        val latestVer = asset?.let { JAR.find(it.name)!!.groupValues[1] }
        when {
            asset == null || latestVer == null ->
                _status.value = if (haveVer != null) "$haveVer 설치됨" else "설치 안 됨"
            haveVer == null && !install ->
                _status.value = "설치 안 됨 · 최신 $latestVer"
            haveVer != null && !Updates.isNewer(latestVer, haveVer) && !(install && have.size > 1) ->
                _status.value = "$haveVer 설치됨 · 최신 버전"
            !install && !repo.state.value.device.modAutoUpdate ->
                _status.value = "$haveVer 설치됨 · 새 버전 $latestVer 있음"
            GameWatch.state.value.running ->
                _status.value = "게임을 끄면 $latestVer 로 ${if (haveVer == null) "설치" else "업데이트"}돼요"
            else -> {
                _status.value = "$latestVer 받는 중…"
                val bytes = repo.downloadFile(asset.url)
                if (bytes == null || bytes.size < 1000) { _status.value = "받지 못했어요 · 잠시 후 다시 시도해 주세요"; return }
                dir.mkdirs()
                val tmp = File(dir, asset.name + ".part")
                tmp.writeBytes(bytes)
                // 예전 버전 파일 지우기 (두 개가 있으면 게임이 켜지지 않음)
                have.forEach { (f, _) -> if (f.name != asset.name) f.delete() }
                val target = File(dir, asset.name)
                target.delete()
                if (!tmp.renameTo(target)) { tmp.delete(); _status.value = "파일을 바꾸지 못했어요 · 게임을 끄고 다시 시도해 주세요"; return }
                _status.value = "$latestVer 설치됨 · 최신 버전"
            }
        }
    }
}
