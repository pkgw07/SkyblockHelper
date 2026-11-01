package sbhelper.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import sbhelper.core.data.KeyValueStorage
import sbhelper.core.notify.AppNotification
import sbhelper.core.notify.Notifier
import sbhelper.ui.AppInfo
import sbhelper.ui.Platform
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

class DesktopPlatform : Platform {
    override val isDesktop = true
    /** 설정의 '위키를 전용 창으로 열기' */
    var wikiWindowEnabled: () -> Boolean = { false }
    override val versionName = AppInfo.VERSION

    /**
     * 브라우저로 열기. 화면이 멈추지 않게 백그라운드에서, Windows 는 셸로 바로 연다
     * (Desktop.browse 는 처음 부를 때 몇 초씩 걸리는 경우가 있음)
     */
    override fun openUrl(url: String) {
        Thread({
            val win = System.getProperty("os.name").lowercase().contains("win")
            // 웹 주소만 (파일 경로 등이 셸로 실행되지 않게)
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@Thread
            // 위키는 전용 창 (Edge 앱 모드) — 안 되면 아래 기본 브라우저
            if (url.startsWith(sbhelper.core.api.WikiApi.BASE) && wikiWindowEnabled()) {
                if (WikiWindow.bounds == null) runCatching {
                    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                    val w = maxOf(900, (screen.width * 0.45).toInt()).coerceAtMost(screen.width)
                    WikiWindow.bounds = intArrayOf(screen.x + screen.width - w, screen.y, w, screen.height)
                }
                if (runCatching { WikiWindow.open(url) }.getOrDefault(false)) return@Thread
            }
            val ok = win && runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start() }.isSuccess
            if (!ok) runCatching { Desktop.getDesktop().browse(URI(url)) }
        }, "open-url").apply { isDaemon = true }.start()
    }

    override fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override fun setStartWithSystem(enabled: Boolean): Boolean = Autostart.set(enabled)

    /** Windows 에서만 자동 업데이트(설치 파일 .exe) */
    override val updateAssetSuffix: String? = run {
        val path = System.getProperty("jpackage.app-path") ?: return@run null
        // 설치 프로그램으로 설치한 경우만 (압축판은 Releases 에서 직접 받기)
        if (System.getProperty("os.name").lowercase().contains("win") && path.contains("\\Programs\\SkyblockHelper")) ".exe" else null
    }

    /** 설치 파일을 임시 폴더에 저장하고 실행한 뒤, 설치가 덮어쓸 수 있도록 프로그램을 끝낸다 */
    var onQuitForUpdate: () -> Unit = {}

    /** 앱 안 업데이트: 바뀐 파일만 받아 두고 다시 시작하면 적용 */
    private val updater = DesktopUpdater { onQuitForUpdate() }
    override val inAppUpdater: sbhelper.ui.InAppUpdater? = updater.takeIf { it.available }

    override fun installUpdate(bytes: ByteArray, fileName: String): Boolean = runCatching {
        val file = java.io.File(System.getProperty("java.io.tmpdir"), fileName)
        file.writeBytes(bytes)
        // 조용히 설치하고, 설치가 끝나면 설치 프로그램이 앱을 다시 실행한다
        // 설치 기록(문제 확인용): %TEMP%\SkyblockHelper-update.log
        val log = java.io.File(System.getProperty("java.io.tmpdir"), "SkyblockHelper-update.log").absolutePath
        ProcessBuilder(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/LOG=$log").start()
        // 설치 프로그램은 이 프로그램이 완전히 꺼질 때까지 기다렸다가 설치하고, 끝나면 다시 실행한다
        Thread { Thread.sleep(500); onQuitForUpdate() }.start()
        true
    }.getOrDefault(false)

    override fun qrMatrix(text: String): Array<BooleanArray>? = runCatching {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
    }.getOrNull()
}

/** 트레이 알림으로 보낸다. 트레이가 준비되기 전 알림은 버린다. */
class DesktopNotifier : Notifier {
    @Volatile var sink: ((AppNotification) -> Unit)? = null
    @Volatile var toldAboutTray = false
    override fun show(notification: AppNotification) { sink?.invoke(notification) }
}

object AppDirs {
    fun dataDir(): File {
        val os = System.getProperty("os.name").lowercase()
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let { File(it, "SkyblockHelper") }
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/SkyblockHelper")
            else -> null
        } ?: File(System.getProperty("user.home"), ".skyblock_helper")
        return base.apply { mkdirs() }
    }
}

class FileStorage(private val dir: File) : KeyValueStorage {
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun readBytes(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()
    override fun writeBytes(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }
    override fun write(name: String, text: String) {
        // 쓰는 도중 꺼져도 파일이 깨지지 않도록 임시 파일에 쓰고 바꿔치기
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }
}

/** 컴퓨터 시작 시 자동 실행 */
object Autostart {
    private const val NAME = "SkyblockHelper"

    /** jpackage 로 설치된 실행 파일 경로 (개발 중 실행이면 null) */
    private val appPath: String? get() = System.getProperty("jpackage.app-path")

    fun set(enabled: Boolean): Boolean {
        val exe = appPath ?: return false
        val os = System.getProperty("os.name").lowercase()
        return runCatching {
            when {
                os.contains("win") -> {
                    val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
                    val cmd = if (enabled) listOf("reg", "add", key, "/v", NAME, "/t", "REG_SZ", "/d", "\"$exe\" --minimized", "/f")
                    else listOf("reg", "delete", key, "/v", NAME, "/f")
                    ProcessBuilder(cmd).start().waitFor() == 0 || !enabled
                }
                os.contains("mac") -> {
                    val plist = File(System.getProperty("user.home"), "Library/LaunchAgents/net.skyblockhelper.plist")
                    if (enabled) {
                        plist.parentFile.mkdirs()
                        plist.writeText("""
                            <?xml version="1.0" encoding="UTF-8"?>
                            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                            <plist version="1.0"><dict>
                              <key>Label</key><string>net.skyblockhelper</string>
                              <key>ProgramArguments</key><array><string>$exe</string><string>--minimized</string></array>
                              <key>RunAtLoad</key><true/>
                            </dict></plist>
                        """.trimIndent())
                    } else plist.delete()
                    true
                }
                else -> {
                    val desktop = File(System.getProperty("user.home"), ".config/autostart/skyblockhelper.desktop")
                    if (enabled) {
                        desktop.parentFile.mkdirs()
                        desktop.writeText("[Desktop Entry]\nType=Application\nName=SkyblockHelper\nExec=\"$exe\" --minimized\n")
                    } else desktop.delete()
                    true
                }
            }
        }.getOrDefault(false)
    }
}

/** 프로그램을 두 번 켜면 기존 창을 앞으로 가져온다 */
object SingleInstance {
    private const val PORT = 47_821
    @Volatile var onShowRequest: (() -> Unit)? = null
    private var server: ServerSocket? = null

    fun acquire(): Boolean {
        return try {
            val s = ServerSocket(PORT, 1, InetAddress.getLoopbackAddress())
            server = s
            Thread({
                while (!s.isClosed) {
                    runCatching { s.accept().use { onShowRequest?.invoke() } }
                }
            }, "single-instance").apply { isDaemon = true }.start()
            true
        } catch (e: Exception) {
            // 이미 다른 인스턴스가 포트를 쓰고 있음 → 보여 달라고 요청
            runCatching { Socket(InetAddress.getLoopbackAddress(), PORT).close() }
            false
        }
    }
}

/** 마우스가 올라가 있는지 추적 */
fun Modifier.hoverTracker(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Enter -> onChange(true)
                PointerEventType.Exit -> onChange(false)
            }
        }
    }
}
