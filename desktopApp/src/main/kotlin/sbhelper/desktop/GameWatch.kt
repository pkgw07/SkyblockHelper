package sbhelper.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * 마인크래프트 감지 (Windows 전용, 게임에는 아무것도 넣지 않음).
 *
 *  - 창 전환: Windows 의 '맨 앞 창이 바뀜' 신호(SetWinEventHook EVENT_SYSTEM_FOREGROUND)를 받는다. 계속 확인(폴링)하지 않음.
 *  - 기록 파일: 마인크래프트 창을 본 뒤, 그 창이 살아 있는 동안만 latest.log 의 새 줄을 2초마다 읽는다
 *    ("Welcome to Hypixel SkyBlock!" → 스카이블록 안). 창이 닫히면 읽기를 멈춘다.
 *  - 단축키 Ctrl+Shift+O: 오버레이 숨기기/보이기 (같은 신호 스레드에서 RegisterHotKey)
 */
object GameWatch {
    data class State(
        /** 마인크래프트 창이 살아 있음 */
        val running: Boolean = false,
        /** 마인크래프트가 맨 앞 창 */
        val foreground: Boolean = false,
        /** Hypixel SkyBlock 안 */
        val inSkyblock: Boolean = false,
        /** 단축키로 숨김 */
        val hidden: Boolean = false,
        /** 지금 읽는 기록 파일 */
        val logFile: String? = null,
        /** 기록 파일을 못 찾음 → 게임 화면에서 항상 보여 줌 */
        val noLog: Boolean = false,
        /** 설정의 '미리 보기' (몇 초) */
        val preview: Boolean = false,
        /** 위치 직접 정하기 중 (끌어서 옮김, 클릭 통과 끔) */
        val placing: Boolean = false,
    ) {
        /** 오버레이 창을 만들어 둘지 */
        val active get() = preview || placing || (running && (inSkyblock || noLog))
        /** 지금 화면에 보일지 */
        val shown get() = preview || placing || (foreground && !hidden)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val _status = MutableStateFlow("마인크래프트를 기다리는 중")
    val statusText: StateFlow<String> = _status

    private fun set(f: (State) -> State) {
        _state.update(f)
        val st = _state.value
        _status.value = when {
            !st.running -> "마인크래프트를 기다리는 중"
            st.noLog -> "마인크래프트 찾음 · 게임 화면에서 보여요"
            st.inSkyblock -> "스카이블록 안 · 게임 화면에서 보여요"
            else -> "마인크래프트 찾음 · 스카이블록에 들어가면 보여요"
        } + if (st.hidden) " (Ctrl+Shift+O 로 숨김)" else ""
    }

    /** 위치 직접 정하기 시작 / 끝 */
    fun place() { if (started) set { it.copy(placing = true) } }
    fun endPlace() { set { it.copy(placing = false) } }

    /** 게임 없이 10초 동안 화면 구석에 띄워 보기 */
    fun preview() {
        if (!started) return
        set { it.copy(preview = true) }
        scope.launch { delay(10_000); set { it.copy(preview = false) } }
    }

    @Volatile var mcWindow: Pointer? = null
        private set

    private var started = false
    private var tailJob: Job? = null
    private lateinit var scope: CoroutineScope
    // 콜백 객체가 GC 로 사라지면 신호가 끊기므로 붙잡아 둔다
    private var proc: WinEventProc? = null

    val supported: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun start(scope: CoroutineScope) {
        if (started || !supported) return
        started = true
        this.scope = scope
        Thread({ runCatching { messageLoop() }.onFailure { System.err.println("GameWatch: ${it.message}") } }, "sbh-gamewatch").apply {
            isDaemon = true
            start()
        }
    }

    // ------------------------------------------------------------------ 창 전환 신호
    private fun messageLoop() {
        val u = U32.INSTANCE
        val cb = object : WinEventProc {
            override fun callback(hook: Pointer?, event: Int, hwnd: Pointer?, idObject: Int, idChild: Int, thread: Int, time: Int) {
                if (hwnd != null) onForeground(hwnd)
            }
        }
        proc = cb
        u.SetWinEventHook(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND, null, cb, 0, 0, WINEVENT_OUTOFCONTEXT or WINEVENT_SKIPOWNPROCESS)
        u.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_SHIFT or MOD_NOREPEAT, 'O'.code)
        // 켤 때 이미 마인크래프트가 맨 앞이면
        u.GetForegroundWindow()?.let { onForeground(it) }
        val msg = Msg()
        while (u.GetMessageW(msg, null, 0, 0) > 0) {
            if (msg.message == WM_HOTKEY) set { it.copy(hidden = !it.hidden) }
            u.TranslateMessage(msg)
            u.DispatchMessageW(msg)
        }
    }

    private fun onForeground(hwnd: Pointer) {
        val isMc = isMinecraft(hwnd)
        if (isMc) {
            if (mcWindow != hwnd) mcWindow = hwnd
            set { it.copy(running = true, foreground = true) }
            if (tailJob?.isActive != true) tailJob = scope.launch { tailWhileRunning() }
        } else {
            set { it.copy(foreground = false) }
        }
    }

    fun isMinecraft(hwnd: Pointer): Boolean {
        val u = U32.INSTANCE
        val buf = CharArray(256)
        val cls = String(buf, 0, u.GetClassNameW(hwnd, buf, buf.size).coerceAtLeast(0))
        val title = String(buf, 0, u.GetWindowTextW(hwnd, buf, buf.size).coerceAtLeast(0))
        // GLFW30 = 1.13 이후 버전, LWJGL = 1.8.9 (Forge·Lunar·Badlion 포함)
        return cls == "GLFW30" || cls == "LWJGL" ||
            Regex("^(Minecraft|Lunar Client|Badlion Client)", RegexOption.IGNORE_CASE).containsMatchIn(title)
    }

    /** 마인크래프트 창 위치 (화면 픽셀: 왼, 위, 오른, 아래) */
    fun mcRect(): IntArray? {
        val w = mcWindow ?: return null
        val r = IntArray(4)
        return if (U32.INSTANCE.GetWindowRect(w, r)) r else null
    }

    // ------------------------------------------------------------------ 기록 파일 (마인크래프트가 켜져 있을 때만)
    private suspend fun tailWhileRunning() {
        // 실행 중인 마인크래프트의 게임 폴더(--gameDir)에서 기록 파일 찾기 (런처마다 위치가 달라서)
        val gameDir = mcWindow?.let { gameDirOf(it) }
        val tail = LogTail(gameDir)
        while (scope.isActive) {
            val w = mcWindow
            if (w == null || !U32.INSTANCE.IsWindow(w)) {
                // 마인크래프트가 꺼짐 → 읽기 멈춤, 오버레이 없애기
                mcWindow = null
                set { it.copy(running = false, foreground = false, inSkyblock = false, logFile = null, noLog = false) }
                return
            }
            tail.poll()?.let { inSb -> set { it.copy(inSkyblock = inSb) } }
            val noLog = tail.file == null
            if (_state.value.logFile != tail.file?.path || _state.value.noLog != noLog) set { it.copy(logFile = tail.file?.path, noLog = noLog) }
            delay(2000)
        }
    }

    /** 창 → 프로세스 → 실행 명령의 --gameDir (한 번만, 1초 정도) */
    private fun gameDirOf(hwnd: Pointer): File? = runCatching {
        val pid = IntArray(1)
        U32.INSTANCE.GetWindowThreadProcessId(hwnd, pid)
        if (pid[0] == 0) return null
        val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-CimInstance Win32_Process -Filter \"ProcessId=${pid[0]}\").CommandLine")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val m = Regex("""--gameDir\s+(?:"([^"]+)"|(\S+))""").find(out) ?: return null
        File(m.groupValues[1].ifEmpty { m.groupValues[2] }).takeIf { it.isDirectory }
    }.getOrNull()

    /** latest.log 의 새 줄만 읽기 (처음 열 때는 마지막 256KB 로 지금 상태를 알아냄) */
    class LogTail(private val gameDir: File? = null) {
        var file: File? = null
            private set
        private var pos = 0L

        /** @return 스카이블록 안/밖이 바뀌었으면 그 값, 아니면 null */
        fun poll(): Boolean? {
            // 지금 쓰이고 있는(최근 6시간 안에 바뀐) 기록 파일 중 가장 최근 것
            val recent = System.currentTimeMillis() - 6 * 3_600_000L
            val newest = (listOfNotNull(gameDir?.let { File(it, "logs/latest.log") }) + candidates())
                .filter { it.isFile && it.lastModified() > recent }.maxByOrNull { it.lastModified() }
            if (newest == null) { file = null; return null }
            var result: Boolean? = null
            if (newest != file) {
                file = newest
                pos = (newest.length() - 256 * 1024).coerceAtLeast(0)
                result = false
            }
            val f = file ?: return result
            val len = f.length()
            if (len < pos) pos = 0 // 새 파일로 바뀜 (게임 다시 시작)
            if (len == pos) return result
            runCatching {
                RandomAccessFile(f, "r").use { raf ->
                    raf.seek(pos)
                    val bytes = ByteArray((len - pos).coerceAtMost(4L * 1024 * 1024).toInt())
                    raf.readFully(bytes)
                    // 끝이 줄 중간이면 다음에 다시 읽게
                    val lastNl = bytes.lastIndexOf('\n'.code.toByte())
                    if (lastNl < 0) return result
                    pos += lastNl + 1
                    String(bytes, 0, lastNl + 1, Charsets.ISO_8859_1).lineSequence().forEach { line ->
                        scan(line)?.let { result = it }
                    }
                }
            }
            return result
        }

        companion object {
            fun candidates(): List<File> {
                val appData = System.getenv("APPDATA")?.let(::File)
                val home = File(System.getProperty("user.home"))
                // Lunar 는 버전마다 폴더가 달라서 logs/game 안의 기록도 본다
                val lunarGame = File(home, ".lunarclient/logs/game").listFiles { f -> f.isFile && f.name.endsWith(".log") }.orEmpty().toList()
                val lunarOffline = File(home, ".lunarclient/offline").listFiles { f -> f.isDirectory }.orEmpty().map { File(it, "logs/latest.log") }
                return listOfNotNull(
                    appData?.let { File(it, ".minecraft/logs/latest.log") },                          // 바닐라·Forge·Lunar(기본 게임 폴더)
                    appData?.let { File(it, ".minecraft/logs/blclient/minecraft/latest.log") },       // Badlion
                    File(home, ".lunarclient/offline/multiver/logs/latest.log"),                      // Lunar
                ) + lunarOffline + lunarGame
            }

            /** 한 줄 → 스카이블록 들어감(true) / 나감(false) / 상관없음(null) */
            fun scan(line: String): Boolean? = when {
                "Welcome to Hypixel SkyBlock!" in line -> true
                "Stopping!" in line || "Connecting to " in line || "Disconnecting from" in line ||
                    "joined the lobby!" in line || "You were spawned in Limbo" in line -> false
                else -> null
            }
        }
    }

    // ------------------------------------------------------------------ 오버레이 창 (클릭 통과 + 항상 위)
    /** 클릭이 게임으로 그대로 가고, 작업 표시줄·Alt+Tab 에 안 나오고, 눌러도 포커스를 가져가지 않게 */
    fun makeClickThrough(window: java.awt.Window, on: Boolean = true) {
        if (!supported) return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            val u = U32.INSTANCE
            val ex = u.GetWindowLongW(hwnd, GWL_EXSTYLE) or WS_EX_LAYERED or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE
            // 위치를 정하는 동안만 마우스를 받는다 (끌어서 옮기기)
            u.SetWindowLongW(hwnd, GWL_EXSTYLE, if (on) ex or WS_EX_TRANSPARENT else ex and WS_EX_TRANSPARENT.inv())
        }
    }

    /** F11 전체 화면에서도 위에 있도록 '항상 위' 를 다시 건다 (게임이 맨 앞일 때만 1초마다) */
    fun keepOnTop(window: java.awt.Window) {
        if (!supported) return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            U32.INSTANCE.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_SHOWWINDOW)
        }
    }

    // ------------------------------------------------------------------ Windows API (user32)
    private const val EVENT_SYSTEM_FOREGROUND = 0x0003
    private const val WINEVENT_OUTOFCONTEXT = 0x0000
    private const val WINEVENT_SKIPOWNPROCESS = 0x0002
    private const val WM_HOTKEY = 0x0312
    private const val HOTKEY_ID = 0x5348
    private const val MOD_CONTROL = 0x0002
    private const val MOD_SHIFT = 0x0004
    private const val MOD_NOREPEAT = 0x4000
    private const val GWL_EXSTYLE = -20
    private const val WS_EX_LAYERED = 0x00080000
    private const val WS_EX_TRANSPARENT = 0x00000020
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_NOACTIVATE = 0x08000000
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_SHOWWINDOW = 0x0040
    private val HWND_TOPMOST: Pointer = Pointer.createConstant(-1L)

    interface WinEventProc : StdCallLibrary.StdCallCallback {
        fun callback(hook: Pointer?, event: Int, hwnd: Pointer?, idObject: Int, idChild: Int, thread: Int, time: Int)
    }

    @Structure.FieldOrder("hwnd", "message", "wParam", "lParam", "time", "x", "y", "priv")
    class Msg : Structure() {
        @JvmField var hwnd: Pointer? = null
        @JvmField var message: Int = 0
        @JvmField var wParam: Pointer? = null
        @JvmField var lParam: Pointer? = null
        @JvmField var time: Int = 0
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0
        @JvmField var priv: Int = 0
    }

    @Suppress("FunctionName")
    interface U32 : StdCallLibrary {
        fun SetWinEventHook(eventMin: Int, eventMax: Int, hmod: Pointer?, proc: WinEventProc, pid: Int, tid: Int, flags: Int): Pointer?
        fun RegisterHotKey(hwnd: Pointer?, id: Int, mods: Int, vk: Int): Boolean
        fun GetMessageW(msg: Msg, hwnd: Pointer?, min: Int, max: Int): Int
        fun TranslateMessage(msg: Msg): Boolean
        fun DispatchMessageW(msg: Msg): Pointer?
        fun GetForegroundWindow(): Pointer?
        fun GetClassNameW(hwnd: Pointer, buf: CharArray, max: Int): Int
        fun GetWindowTextW(hwnd: Pointer, buf: CharArray, max: Int): Int
        fun IsWindow(hwnd: Pointer?): Boolean
        fun GetWindowRect(hwnd: Pointer, rect: IntArray): Boolean
        fun GetWindowThreadProcessId(hwnd: Pointer, pid: IntArray): Int
        fun GetWindowLongW(hwnd: Pointer, index: Int): Int
        fun SetWindowLongW(hwnd: Pointer, index: Int, value: Int): Int
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean

        companion object {
            val INSTANCE: U32 by lazy { Native.load("user32", U32::class.java) }
        }
    }
}
