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
        /** 게임 창이 화면을 꽉 채움 (F11 또는 테두리 없는 전체 화면) */
        val fullscreen: Boolean = false,
        /** 테두리 없는 전체 화면으로 바꿔 둔 상태 (Ctrl+Shift+F) */
        val borderless: Boolean = false,
        /** 게임이 맨 앞으로 올 때마다 늘어남 → 오버레이가 바로 '항상 위' 를 다시 건다 */
        val topTick: Int = 0,
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
            st.fullscreen && !st.borderless -> "전체 화면(F11)에서 안 보이면 F11 을 끄고 Ctrl+Shift+F 를 누르세요"
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
        // 켤 때 이미 마인크래프트가 맨 앞이면
        u.GetForegroundWindow()?.let { onForeground(it) }
        val msg = Msg()
        while (u.GetMessageW(msg, null, 0, 0) > 0) {
            if (msg.message == WM_HOTKEY) {
                if (Pointer.nativeValue(msg.wParam) == HOTKEY_BORDERLESS.toLong()) toggleBorderless()
                else set { it.copy(hidden = !it.hidden) }
            }
            u.TranslateMessage(msg)
            u.DispatchMessageW(msg)
        }
    }

    private fun onForeground(hwnd: Pointer) {
        val isMc = isMinecraft(hwnd) || sameProcess(hwnd, mcWindow)
        // 단축키는 게임이 맨 앞일 때만 잡는다 (다른 프로그램의 Ctrl+Shift+O/F 를 빼앗지 않게)
        hotkeys(isMc)
        if (isMc) {
            if (mcWindow != hwnd) mcWindow = hwnd
            set { it.copy(running = true, foreground = true, topTick = it.topTick + 1, fullscreen = coversMonitor(hwnd)) }
            if (tailJob?.isActive != true) tailJob = scope.launch { tailWhileRunning() }
        } else {
            set { it.copy(foreground = false) }
        }
    }

    /** 두 창이 같은 프로그램(프로세스)인지 — 게임이 따로 띄운 창으로 앞이 바뀌어도 게임으로 본다 */
    private fun sameProcess(a: Pointer, b: Pointer?): Boolean {
        if (b == null) return false
        val u = U32.INSTANCE
        val pa = IntArray(1); val pb = IntArray(1)
        u.GetWindowThreadProcessId(a, pa); u.GetWindowThreadProcessId(b, pb)
        return pa[0] != 0 && pa[0] == pb[0]
    }

    private var hotkeysOn = false

    /** 신호 스레드에서만 부른다 (RegisterHotKey 는 부른 스레드로 메시지가 옴) */
    private fun hotkeys(on: Boolean) {
        if (on == hotkeysOn) return
        val u = U32.INSTANCE
        if (on) {
            u.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_SHIFT or MOD_NOREPEAT, 'O'.code)
            u.RegisterHotKey(null, HOTKEY_BORDERLESS, MOD_CONTROL or MOD_SHIFT or MOD_NOREPEAT, 'F'.code)
        } else {
            u.UnregisterHotKey(null, HOTKEY_ID)
            u.UnregisterHotKey(null, HOTKEY_BORDERLESS)
        }
        hotkeysOn = on
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

    /** 설정: 게임을 켜면 자동으로 테두리 없는 전체 화면 */
    @Volatile var autoBorderless: () -> Boolean = { false }

    /** 보이는 마인크래프트 창 찾기 (창 종류 이름으로) */
    private fun findMinecraft(): Pointer? {
        val u = U32.INSTANCE
        return listOf("GLFW30", "LWJGL").firstNotNullOfOrNull { cls -> u.FindWindowW(com.sun.jna.WString(cls), null)?.takeIf { u.IsWindow(it) } }
    }

    /** 창이 자기 모니터를 꽉 채우는지 */
    private fun coversMonitor(hwnd: Pointer): Boolean {
        val u = U32.INSTANCE
        val r = IntArray(4)
        if (!u.GetWindowRect(hwnd, r)) return false
        val m = monitorRect(hwnd) ?: return false
        return r[0] <= m[0] && r[1] <= m[1] && r[2] >= m[2] && r[3] >= m[3]
    }

    private fun monitorRect(hwnd: Pointer): IntArray? {
        val u = U32.INSTANCE
        val mon = u.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return null
        val info = IntArray(10).also { it[0] = 40 } // MONITORINFO: cbSize, rcMonitor, rcWork, dwFlags
        return if (u.GetMonitorInfoW(mon, info)) intArrayOf(info[1], info[2], info[3], info[4]) else null
    }

    // 테두리 없는 전체 화면: 원래 창 모양·위치를 기억했다가 되돌린다 (게임 파일은 건드리지 않고 창 모양만 바꿈)
    private var savedStyle = 0
    private var savedRect: IntArray? = null

    /** 게임 창 ↔ 테두리 없는 전체 화면 (Ctrl+Shift+F). F11(독점 전체 화면) 대신 쓰면 오버레이가 항상 보인다 */
    fun toggleBorderless() {
        val w = mcWindow ?: findMinecraft() ?: return
        val u = U32.INSTANCE
        runCatching {
            if (!_state.value.borderless) {
                if (coversMonitor(w) && (u.GetWindowLongW(w, GWL_STYLE) and WS_CAPTION) == 0) return // 이미 F11 전체 화면
                val m = monitorRect(w) ?: return
                savedStyle = u.GetWindowLongW(w, GWL_STYLE)
                savedRect = IntArray(4).also { u.GetWindowRect(w, it) }
                u.SetWindowLongW(w, GWL_STYLE, savedStyle and (WS_CAPTION or WS_THICKFRAME or WS_SYSMENU or WS_MINIMIZEBOX or WS_MAXIMIZEBOX or WS_MAXIMIZE).inv())
                u.SetWindowPos(w, null, m[0], m[1], m[2] - m[0], m[3] - m[1], SWP_FRAMECHANGED or SWP_NOZORDER or SWP_NOACTIVATE)
                set { it.copy(borderless = true, fullscreen = true, topTick = it.topTick + 1) }
            } else {
                val r = savedRect
                if (savedStyle != 0) u.SetWindowLongW(w, GWL_STYLE, savedStyle)
                if (r != null) u.SetWindowPos(w, null, r[0], r[1], r[2] - r[0], r[3] - r[1], SWP_FRAMECHANGED or SWP_NOZORDER or SWP_NOACTIVATE)
                set { it.copy(borderless = false, fullscreen = false) }
            }
        }
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
            val u = U32.INSTANCE
            // F11 을 누르면 1.8.9 는 게임 창을 새로 만든다 → 새 창을 찾아 이어 간다 (오버레이가 사라지지 않게)
            val fg = u.GetForegroundWindow()
            val fgIsMc = fg != null && (isMinecraft(fg) || sameProcess(fg, mcWindow))
            if (fg != null && isMinecraft(fg) && fg != mcWindow) mcWindow = fg
            if (mcWindow == null || !u.IsWindow(mcWindow)) mcWindow = findMinecraft()
            val w = mcWindow
            if (w == null) {
                // 마인크래프트가 꺼짐 → 읽기 멈춤, 오버레이 없애기
                set { it.copy(running = false, foreground = false, inSkyblock = false, logFile = null, noLog = false, fullscreen = false, borderless = false) }
                return
            }
            // 창 전환 신호를 놓쳤을 때도 맞게
            val full = coversMonitor(w)
            if (_state.value.foreground != fgIsMc || _state.value.fullscreen != full) {
                set { it.copy(foreground = fgIsMc, fullscreen = full, topTick = if (fgIsMc && !it.foreground) it.topTick + 1 else it.topTick) }
            }
            if (autoBorderless() && !full && !_state.value.borderless) toggleBorderless()
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
            // 지금 쓰이고 있는(최근 6시간 안에 바뀐) 기록 파일 중 가장 최근 것.
            // 지금 읽는 파일이 3분 안에 바뀌었으면 계속 그 파일만 (기록 파일이 둘일 때 왔다 갔다 하며 상태가 초기화되지 않게)
            val now = System.currentTimeMillis()
            val cur = file
            val newest = if (cur != null && cur.isFile && now - cur.lastModified() < 3 * 60_000L) cur
                else (listOfNotNull(gameDir?.let { File(it, "logs/latest.log") }) + candidates())
                    .filter { it.isFile && it.lastModified() > now - 6 * 3_600_000L }.maxByOrNull { it.lastModified() }
            if (newest == null) { file = null; return null }
            var result: Boolean? = null
            if (newest != file) {
                // 새로 읽는 파일: 마지막 256KB 에서 지금 상태를 다시 알아낸다 (신호가 없으면 상태 그대로)
                file = newest
                pos = (newest.length() - 256 * 1024).coerceAtLeast(0)
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
            /**
             * 한 줄 → 스카이블록 들어감(true) / 나감(false) / 상관없음(null).
             * Hub·섬 이동 같은 서버 이동(Hypixel 안)은 나간 것으로 보지 않는다.
             */
            fun scan(line: String): Boolean? = when {
                "Welcome to Hypixel SkyBlock!" in line || "You are playing on profile:" in line -> true
                "Stopping!" in line -> false                                   // 게임 종료
                "joined the lobby!" in line -> false                           // Hypixel 로비 (스카이블록 밖)
                "You were spawned in Limbo" in line -> false                   // 잠수 대기실
                // 다른 서버로 접속 (Hypixel 이 아닌 곳). Hypixel 안의 서버 이동은 무시
                "Connecting to " in line && !line.contains("hypixel", ignoreCase = true) -> false
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
    private const val HOTKEY_BORDERLESS = 0x5349
    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val WS_MAXIMIZE = 0x01000000
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020
    private const val MONITOR_DEFAULTTONEAREST = 2
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
        fun FindWindowW(cls: com.sun.jna.WString?, title: com.sun.jna.WString?): Pointer?
        fun UnregisterHotKey(hwnd: Pointer?, id: Int): Boolean
        fun MonitorFromWindow(hwnd: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: IntArray): Boolean
        fun GetWindowLongW(hwnd: Pointer, index: Int): Int
        fun SetWindowLongW(hwnd: Pointer, index: Int, value: Int): Int
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean

        companion object {
            val INSTANCE: U32 by lazy { Native.load("user32", U32::class.java) }
        }
    }
}
