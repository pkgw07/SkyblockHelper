package sbhelper.desktop

import kotlinx.coroutines.runBlocking
import sbhelper.core.update.Release
import sbhelper.core.update.ReleaseAsset
import java.io.File

/**
 * CI 확인용: 설치된 앱 폴더(installedApp)에 대해, distDir 의 업데이트 파일로 앱 안 업데이트를 받아 둔다.
 * 실제 앱과 같은 DesktopUpdater 코드를 쓰고, 다운로드만 로컬 파일에서 읽는다.
 * 사용: updaterSelfTest -PselfTestArgs="<설치폴더\app>;<업데이트 파일 폴더>;<결과 파일>"
 */
fun main(args: Array<String>) = runBlocking {
    val (installedApp, distDir, resultFile) = args
    System.setProperty(Patches.PROP_BUNDLED, installedApp)
    // 설치본 Boot 의 기능 (CI 가 4번째 인자로 알려 줌: 1.4.1 설치본이면 빈 값)
    System.setProperty(Patches.PROP_BOOT_FEATURES, args.getOrElse(3) { "" })
    val files = File(distDir).listFiles().orEmpty().filter { it.isFile }
    val manifest = sbhelper.core.api.SbJson.parseToJsonElement(File(distDir, DesktopUpdater.MANIFEST).readText())
    val version = (manifest as kotlinx.serialization.json.JsonObject)["version"].toString().trim('"')
    val release = Release("v$version", assets = files.map { ReleaseAsset(it.name, it.toURI().toString(), it.length()) })
    val result = DesktopUpdater {}.stage(release) { url -> runCatching { File(java.net.URI(url)).readBytes() }.getOrNull() }
    val text = "stage=$result staged=${Patches.current()?.first} entries=${Patches.current()?.second?.count { it.startsWith("store:") }} " +
        "native=${Patches.current()?.second?.firstOrNull { it.startsWith("native:") }}"
    println(text)
    File(resultFile).writeText(text)
}
