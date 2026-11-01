package sbhelper.desktop

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 위키 전용 창 (Windows 의 Edge 를 앱 모드로).
 * - 탭·주소창 없는 창 하나를 계속 다시 쓴다 (다른 링크를 누르면 그 창에서 페이지만 바뀜)
 * - 위키 전용 프로필 폴더를 따로 써서 내 브라우저 기록·로그인과 섞이지 않는다
 * - 페이지에 "앱에서 보기" 버튼을 넣어, 누르면 앱의 검색 화면으로 (onOpenInApp)
 * - Edge 가 없거나 실패하면 false → 호출한 쪽이 기본 브라우저로 연다
 *
 * 창을 다시 쓰기 위해 Edge 개발자 도구 연결(127.0.0.1 에서만, 포트는 자동)을 쓴다. 이 프로필에는 위키만 열린다.
 */
object WikiWindow {
    /** 위키 창에서 "앱에서 보기" 를 누르면 (문서 제목) */
    @Volatile var onOpenInApp: ((String) -> Unit)? = null

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val ids = AtomicInteger(1)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var process: Process? = null
    private val lock = Any()

    private val profileDir get() = File(AppDirs.dataDir(), "wiki-window").apply { mkdirs() }

    /** 앱 창 오른쪽 옆 등 원하는 위치 (null 이면 Edge 가 정함) */
    @Volatile var bounds: IntArray? = null

    fun edgePath(): File? {
        if (!System.getProperty("os.name").lowercase().contains("win")) return null
        val roots = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA"))
        return roots.map { File(it, "Microsoft\\Edge\\Application\\msedge.exe") }.firstOrNull { it.isFile }
    }

    /** @return 열었으면 true */
    fun open(url: String): Boolean = synchronized(lock) {
        if (!url.startsWith("https://")) return false
        // 1) 이미 열린 창이면 그 창에서 이동
        if (navigate(url)) return true
        // 2) 새로 띄우기
        val edge = edgePath() ?: return false
        File(profileDir, "DevToolsActivePort").delete()
        val args = mutableListOf(edge.absolutePath, "--app=$url", "--user-data-dir=${profileDir.absolutePath}",
            "--remote-debugging-port=0", "--remote-allow-origins=http://127.0.0.1", "--no-first-run", "--no-default-browser-check",
            "--disable-features=msEdgeSidebarV2,msHubApps")
        bounds?.let { (x, y, w, h) -> args += "--window-position=$x,$y"; args += "--window-size=$w,$h" }
        process = runCatching { ProcessBuilder(args).start() }.getOrNull() ?: return false
        // 포트가 정해질 때까지 잠깐 기다렸다가 연결 (버튼 넣기용, 실패해도 창은 열림)
        Thread({ repeat(40) { if (connect() != null) return@Thread; Thread.sleep(250) } }, "wiki-window-connect").apply { isDaemon = true }.start()
        return true
    }

    private fun port(): Int? = runCatching { File(profileDir, "DevToolsActivePort").readLines().firstOrNull()?.trim()?.toInt() }.getOrNull()

    /** 열려 있는 위키 창(페이지)에 연결 */
    private fun connect(): WebSocket? {
        socket?.let { if (!it.isOutputClosed) return it }
        val port = port() ?: return null
        val list = runCatching {
            http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/list")).timeout(Duration.ofSeconds(2)).build(),
                HttpResponse.BodyHandlers.ofString()).body()
        }.getOrNull() ?: return null
        // 첫 번째 page 의 webSocketDebuggerUrl
        val wsUrl = Regex(""""type"\s*:\s*"page"[^}]*?"webSocketDebuggerUrl"\s*:\s*"([^"]+)"""").find(list)?.groupValues?.get(1)
            ?: Regex(""""webSocketDebuggerUrl"\s*:\s*"(ws://127\.0\.0\.1:\d+/devtools/page/[^"]+)"""").find(list)?.groupValues?.get(1)
            ?: return null
        val ws = runCatching {
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(2)).buildAsync(URI(wsUrl), Listener).get(3, TimeUnit.SECONDS)
        }.getOrNull() ?: return null
        socket = ws
        // "앱에서 보기" 버튼: 페이지마다 넣고, 누르면 앱으로 문서 제목을 보낸다
        send(ws, "Runtime.enable", "{}")
        send(ws, "Runtime.addBinding", """{"name":"sbhOpenInApp"}""")
        send(ws, "Page.enable", "{}")
        send(ws, "Page.addScriptToEvaluateOnNewDocument", """{"source":${jsonString(BUTTON_JS)}}""")
        send(ws, "Runtime.evaluate", """{"expression":${jsonString(BUTTON_JS)}}""")
        return ws
    }

    private fun navigate(url: String): Boolean {
        val ws = connect() ?: return false
        return runCatching {
            send(ws, "Page.navigate", """{"url":${jsonString(url)}}""")
            send(ws, "Page.bringToFront", "{}")
            true
        }.getOrElse { socket = null; false }
    }

    private fun send(ws: WebSocket, method: String, params: String) {
        ws.sendText("""{"id":${ids.getAndIncrement()},"method":"$method","params":$params}""", true).get(2, TimeUnit.SECONDS)
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** 위키 문서 오른쪽 아래에 떠 있는 버튼 */
    private val BUTTON_JS = """
        (function(){
          function add(){
            if (!document.body || document.getElementById('sbh-open')) return;
            var b = document.createElement('button');
            b.id = 'sbh-open'; b.textContent = '앱에서 보기';
            b.title = 'SkyblockHelper 검색 화면에서 이 문서를 열어요 (조합대·필요 조건·시세)';
            b.style.cssText = 'position:fixed;right:18px;bottom:18px;z-index:99999;padding:10px 14px;border-radius:10px;border:2px solid #ffaa00;background:#1b1d24;color:#ffaa00;font-weight:700;cursor:pointer;box-shadow:0 4px 14px rgba(0,0,0,.4)';
            b.onclick = function(){
              var t = (window.mw && mw.config && mw.config.get('wgTitle')) || document.title.replace(/ - .*$/, '');
              if (window.sbhOpenInApp) window.sbhOpenInApp(t);
            };
            document.body.appendChild(b);
          }
          if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', add); else add();
        })();
    """.trimIndent()

    /** Edge 가 보내는 이벤트: 버튼을 누르면 Runtime.bindingCalled */
    private object Listener : WebSocket.Listener {
        private val buf = StringBuilder()
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buf.append(data)
            if (last) {
                val msg = buf.toString(); buf.clear()
                if (msg.contains("\"Runtime.bindingCalled\"") && msg.contains("\"sbhOpenInApp\"")) {
                    // "payload":"문서 제목" — 짧은 글자만 받는다
                    Regex(""""payload"\s*:\s*"((?:[^"\\]|\\.){1,200})"""").find(msg)?.groupValues?.get(1)
                        ?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                        ?.let { title -> onOpenInApp?.invoke(title) }
                }
            }
            webSocket.request(1)
            return null
        }
        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (socket === webSocket) socket = null
            return null
        }
        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (socket === webSocket) socket = null
        }
    }
}
