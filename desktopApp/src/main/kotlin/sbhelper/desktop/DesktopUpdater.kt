package sbhelper.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import sbhelper.core.api.SbJson
import sbhelper.core.update.Release
import sbhelper.ui.AppInfo
import sbhelper.ui.InAppUpdater
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 데스크톱 앱 안 업데이트.
 *
 * 릴리스에 올라간 update-manifest.json 과 비교해서 바뀐 jar 파일만 받아 <데이터 폴더>/patch 에 넣어 둔다.
 * 다시 시작하면 [Boot] 가 새 jar 로 실행한다. 자바 런타임이나 네이티브 파일이 바뀐 버전은 설치 파일로 업데이트한다.
 */
class DesktopUpdater(private val onQuit: () -> Unit) : InAppUpdater {
    private val bundled: File? = System.getProperty(Patches.PROP_BUNDLED)?.let(::File)?.takeIf { it.isDirectory }

    /** 이 컴퓨터에서 앱 안 업데이트를 쓸 수 있는지 (설치본/압축판으로 실행 중일 때) */
    val available get() = bundled != null

    override val stagedVersion: String?
        get() = Patches.current()?.first?.takeIf { Patches.newer(it, AppInfo.VERSION) && !Patches.isBroken(it) }

    private class Jar(val name: String, val sha256: String, val size: Long, val pack: String)

    override suspend fun stage(release: Release, download: suspend (String) -> ByteArray?): InAppUpdater.Result {
        val app = bundled ?: return InAppUpdater.Result.NEEDS_INSTALLER
        val manifestAsset = release.assets.firstOrNull { it.name == MANIFEST } ?: return InAppUpdater.Result.NEEDS_INSTALLER
        val text = download(manifestAsset.url)?.decodeToString() ?: return InAppUpdater.Result.FAILED
        return withContext(Dispatchers.IO) {
            val root = runCatching { SbJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext InAppUpdater.Result.FAILED
            val version = root["version"]?.jsonPrimitive?.content ?: return@withContext InAppUpdater.Result.FAILED
            val javaMajor = root["javaMajor"]?.jsonPrimitive?.int
            val nativeHash = root["nativeHash"]?.jsonPrimitive?.content
            val jars = root["jars"]?.jsonArray?.map { e ->
                val o = e.jsonObject
                Jar(o["name"]!!.jsonPrimitive.content, o["sha256"]!!.jsonPrimitive.content.lowercase(),
                    o["size"]!!.jsonPrimitive.long, o["pack"]?.jsonPrimitive?.content ?: "libs")
            } ?: return@withContext InAppUpdater.Result.FAILED

            if (Patches.isBroken(version)) return@withContext InAppUpdater.Result.NEEDS_INSTALLER
            // 런타임(자바)이나 네이티브 파일이 다르면 jar 만 바꿔서는 안 된다
            if (javaMajor != Runtime.version().feature()) return@withContext InAppUpdater.Result.NEEDS_INSTALLER
            if (nativeHash != nativeHash(app)) return@withContext InAppUpdater.Result.NEEDS_INSTALLER

            val store = Patches.jarsDir()
            val entries = ArrayList<String>()
            val missing = ArrayList<Jar>()
            for (j in jars) {
                val stored = File(store, storeName(j))
                when {
                    matches(File(app, j.name), j) -> entries += "app:${j.name}"
                    matches(stored, j) -> entries += "store:${stored.name}"
                    else -> { missing += j; entries += "store:${stored.name}" }
                }
            }
            for (pack in missing.map { it.pack }.distinct()) {
                val asset = release.assets.firstOrNull { it.name == "SkyblockHelper-$version-patch-$pack.zip" }
                    ?: return@withContext InAppUpdater.Result.NEEDS_INSTALLER
                val bytes = download(asset.url) ?: return@withContext InAppUpdater.Result.FAILED
                val wanted = missing.filter { it.pack == pack }.associateBy { it.name }
                ZipInputStream(bytes.inputStream()).use { zin ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        val j = wanted[File(entry.name).name] ?: continue
                        val tmp = File(store, storeName(j) + ".part")
                        tmp.outputStream().use { zin.copyTo(it) }
                        val target = File(store, storeName(j))
                        if (matches(tmp, j)) { target.delete(); tmp.renameTo(target) } else tmp.delete()
                    }
                }
            }
            if (missing.any { !matches(File(store, storeName(it)), it) }) return@withContext InAppUpdater.Result.FAILED
            Patches.writeCurrent(version, entries)
            InAppUpdater.Result.STAGED
        }
    }

    override fun restart() {
        val exe = System.getProperty("jpackage.app-path") ?: return
        runCatching { ProcessBuilder(exe, "--after-pid=${ProcessHandle.current().pid()}").start() }
            .onSuccess { onQuit() }
    }

    companion object {
        const val MANIFEST = "update-manifest.json"

        /** 같은 이름이라도 내용이 다르면 다른 파일로 보관 (실행 중인 jar 는 Windows 에서 덮어쓸 수 없다) */
        private fun storeName(j: Jar) = j.sha256.take(16) + "-" + j.name

        private fun matches(f: File, j: Jar) = f.isFile && f.length() == j.size && sha256(f) == j.sha256

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** 설치 폴더의 jar·설정 파일이 아닌 파일들(네이티브 라이브러리 등)의 지문 — CI 의 tools/make_patch.py 와 같은 방식 */
        fun nativeHash(appDir: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            appDir.walkTopDown().filter { it.isFile && !it.name.endsWith(".jar") && !it.name.endsWith(".cfg") }
                .map { it.relativeTo(appDir).invariantSeparatorsPath to it }
                .sortedBy { it.first }
                .forEach { (rel, f) -> md.update("$rel:${sha256(f)}\n".toByteArray()) }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
