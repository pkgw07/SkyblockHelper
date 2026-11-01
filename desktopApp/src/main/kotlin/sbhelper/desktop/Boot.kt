package sbhelper.desktop

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

/**
 * 프로그램 시작점.
 *
 * 앱 안에서 받은 업데이트(새 jar 파일)가 있으면 그것으로 실행하고, 없거나 문제가 있으면 설치된 그대로 실행한다.
 * 설치 프로그램을 다시 돌리지 않아도 "받기 → 다시 시작" 만으로 새 버전이 적용된다.
 *
 * 이 파일은 설치본에 들어 있는 것만 쓰이므로, 최대한 단순하게 유지한다 (다른 앱 클래스를 부르지 않음).
 */
object Boot {
    @JvmStatic
    fun main(args: Array<String>) {
        // 업데이트 후 다시 시작: 이전 프로그램이 완전히 끝날 때까지 기다린다
        args.firstOrNull { it.startsWith("--after-pid=") }?.removePrefix("--after-pid=")?.toLongOrNull()?.let { pid ->
            runCatching { ProcessHandle.of(pid).ifPresent { it.onExit().get(20, TimeUnit.SECONDS) } }
        }
        val rest = args.filterNot { it.startsWith("--after-pid=") }.toTypedArray()

        val bundled = bundledDir()
        if (bundled != null) System.setProperty(Patches.PROP_BUNDLED, bundled.absolutePath)
        val loader = if (bundled != null) runCatching { Patches.loader(bundled) }.getOrNull() else null
        if (loader != null) {
            val started = System.currentTimeMillis()
            try {
                System.setProperty(Patches.PROP_RUNNING, loader.second)
                Thread.currentThread().contextClassLoader = loader.first
                val cls = Class.forName("sbhelper.desktop.MainKt", true, loader.first)
                cls.getMethod("main", Array<String>::class.java).invoke(null, rest)
                return
            } catch (e: Throwable) {
                // 새 버전이 바로 실패하면 표시해 두고 설치된 버전으로 실행
                if (System.currentTimeMillis() - started < 60_000) Patches.markBroken(loader.second, e)
                System.clearProperty(Patches.PROP_RUNNING)
                Thread.currentThread().contextClassLoader = Boot::class.java.classLoader
            }
        }
        // 설치된 버전 실행 (같은 패키지에 main 이 여러 개라 이름으로 찾는다)
        Boot::class.java.classLoader.loadClass("sbhelper.desktop.MainKt")
            .getMethod("main", Array<String>::class.java).invoke(null, rest)
    }

    /** 설치된 jar 들이 있는 폴더 (개발 중 실행이면 null) */
    private fun bundledDir(): File? = runCatching {
        if (System.getProperty("jpackage.app-path") == null) return null
        val f = File(Boot::class.java.protectionDomain.codeSource.location.toURI())
        if (f.isFile && f.name.endsWith(".jar")) f.parentFile else null
    }.getOrNull()
}

/** 앱 안 업데이트 파일 보관소: <데이터 폴더>/patch/ */
object Patches {
    const val PROP_BUNDLED = "sbhelper.bundled.dir"
    const val PROP_RUNNING = "sbhelper.patch.version"

    fun dir(): File = File(AppDirs.dataDir(), "patch").apply { mkdirs() }
    fun jarsDir(): File = File(dir(), "jars").apply { mkdirs() }
    private fun currentFile() = File(dir(), "current.txt")
    private fun brokenFile() = File(dir(), "broken.txt")

    /** current.txt: 첫 줄 = 버전, 나머지 = "store:<보관소 파일>" 또는 "app:<설치 폴더 jar>" (순서 = 클래스패스 순서) */
    fun current(): Pair<String, List<String>>? = runCatching {
        val lines = currentFile().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) null else lines.first() to lines.drop(1)
    }.getOrNull()

    fun writeCurrent(version: String, jars: List<String>) {
        val tmp = File(dir(), "current.txt.tmp")
        tmp.writeText((listOf(version) + jars).joinToString("\n"))
        val target = currentFile()
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    fun clear() { currentFile().delete() }

    fun isBroken(version: String) = runCatching { brokenFile().readLines().any { it.substringBefore('\t') == version } }.getOrDefault(false)

    fun markBroken(version: String, e: Throwable) {
        runCatching { brokenFile().appendText("$version\t${e.javaClass.name}: ${e.cause?.message ?: e.message}\n") }
        clear()
    }

    /** 받은 업데이트로 실행할 클래스 로더와 그 버전. 쓸 수 없으면 null */
    fun loader(bundled: File): Pair<ClassLoader, String>? {
        val (version, jars) = current() ?: return null
        val installed = sbhelper.ui.AppInfo.VERSION
        if (!newer(version, installed) || isBroken(version)) {
            // 설치 프로그램으로 같거나 더 새 버전을 깔았으면 받아 둔 파일은 필요 없다
            clear(); cleanup(emptySet()); return null
        }
        val store = jarsDir()
        val files = jars.map { e ->
            when {
                e.startsWith("store:") -> File(store, e.removePrefix("store:"))
                e.startsWith("app:") -> File(bundled, e.removePrefix("app:"))
                else -> return null
            }.takeIf { it.isFile } ?: return null
        }
        cleanup(jars.filter { it.startsWith("store:") }.map { it.removePrefix("store:") }.toSet())
        return URLClassLoader(files.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader()) to version
    }

    /** 더 이상 쓰지 않는 jar 지우기 */
    fun cleanup(keep: Set<String>) {
        jarsDir().listFiles()?.forEach { f -> if (f.name !in keep) runCatching { f.delete() } }
    }

    fun newer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
