package sbhelper.android

import android.content.Context
import android.os.Build
import java.io.File

/** 앱이 꺼진 이유 기록 (휴대폰 안에만 저장, 다음에 켜면 화면에 보여 주고 지움) */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun write(context: Context, e: Throwable, fatal: Boolean) {
        val lines = buildList {
            add((if (fatal) "앱 꺼짐" else "백그라운드 오류") + " · 버전 ${sbhelper.ui.AppInfo.VERSION} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}")
            var c: Throwable? = e
            var depth = 0
            while (c != null && depth < 4) {
                add((if (depth == 0) "" else "Caused by: ") + c::class.java.name + ": " + (c.message ?: "").take(200))
                c.stackTrace.take(if (depth == 0) 25 else 12).forEach { add("  at $it") }
                c = c.cause; depth++
            }
        }
        val f = File(context.filesDir, FILE)
        // 앱 꺼짐 기록이 이미 있으면 백그라운드 오류로 덮어쓰지 않는다
        if (!fatal && f.exists() && f.readText().startsWith("앱 꺼짐")) return
        f.writeText(lines.joinToString("\n"))
    }

    fun take(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return f.readText().also { f.delete() }
    }
}
