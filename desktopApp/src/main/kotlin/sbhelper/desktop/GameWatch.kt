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

/**
 * 마인크래프트 감지 (Windows 전용, 게임에는 아무것도 넣지 않음). 게임 기록·서버는 보지 않고 '마인크래프트 창인지' 만 본다.
 *
 *  - 창 전환: Windows 의 '맨 앞 창이 바뀜' 신호(SetWinEventHook EVENT_SYSTEM_FOREGROUND)를 받는다.
 *  - 게임이 켜져 있는 동안만 2초마다 창이 살아 있는지·다시 만들어졌는지(F11) 확인한다.
 *  - 단축키 (게임이 맨 앞일 때만): Ctrl+Shift+O 오버레이 켜기/끄기, Ctrl+Shift+F 테두리 없는 전체 화면
 */
object GameWatch {
    data class State(
        /** 마인크래프트 창이 살아 있음 */
        val running: Boolean = false,
        /** 마인크래프트가 맨 앞 창 */
        val foreground: Boolean = false,
        /** 단축키(Ctrl+Shift+O)로 끔 */
        val hidden: Boolean = false,
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
        val active get() = preview || placing || running
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
            st.hidden -> "꺼짐 · 게임 중 Ctrl+Shift+O 로 켜기"
            st.fullscreen && !st.borderless -> "켜짐 · F11 전체 화면에서 안 보이면 F11 을 끄고 Ctrl+Shift+F"
            else -> "켜짐 · 게임 중 Ctrl+Shift+O 로 끄기"
        }
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
    private var watchJob: Job? = null
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
            if (watchJob?.isActive != true) watchJob = scope.launch { watchWhileRunning() }
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

    // ------------------------------------------------------------------ 게임이 켜져 있는 동안만: 창 확인 (2초마다)
    private suspend fun watchWhileRunning() {
        while (scope.isActive) {
            val u = U32.INSTANCE
            // F11 을 누르면 1.8.9 는 게임 창을 새로 만든다 → 새 창을 찾아 이어 간다 (오버레이가 사라지지 않게)
            val fg = u.GetForegroundWindow()
            val fgIsMc = fg != null && (isMinecraft(fg) || sameProcess(fg, mcWindow))
            if (fg != null && isMinecraft(fg) && fg != mcWindow) mcWindow = fg
            if (mcWindow == null || !u.IsWindow(mcWindow)) mcWindow = findMinecraft()
            val w = mcWindow
            if (w == null) {
                // 마인크래프트가 꺼짐 → 오버레이 없애기
                set { it.copy(running = false, foreground = false, fullscreen = false, borderless = false) }
                return
            }
            // 창 전환 신호를 놓쳤을 때도 맞게
            val full = coversMonitor(w)
            if (_state.value.foreground != fgIsMc || _state.value.fullscreen != full) {
                set { it.copy(foreground = fgIsMc, fullscreen = full, topTick = if (fgIsMc && !it.foreground) it.topTick + 1 else it.topTick) }
            }
            if (autoBorderless() && !full && !_state.value.borderless) toggleBorderless()
            delay(2000)
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
