package sbhelper.core.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import sbhelper.core.api.ApiException
import sbhelper.core.api.SbJson
import sbhelper.core.api.defaultHttpClient

@Serializable
data class ReleaseAsset(
    val name: String,
    @kotlinx.serialization.SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
data class Release(
    @kotlinx.serialization.SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    @kotlinx.serialization.SerialName("html_url") val pageUrl: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    val version get() = tag.removePrefix("v")
}

/**
 * 앱 업데이트(GitHub Releases)와 최신 게임 데이터(data 브랜치) 확인.
 */
class Updates(
    private val client: HttpClient = defaultHttpClient(),
    private val repo: String = REPO,
) {
    suspend fun latestRelease(): Release? {
        val res = try {
            client.get("https://api.github.com/repos/$repo/releases/latest") {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "SkyblockHelper")
            }
        } catch (e: Exception) {
            throw ApiException("업데이트 서버에 연결할 수 없어요.")
        }
        if (res.status.value == 404) return null
        if (!res.status.isSuccess()) throw ApiException("업데이트 확인 실패 (HTTP ${res.status.value})")
        return SbJson.decodeFromString(Release.serializer(), res.bodyAsText()).takeIf { !it.draft && !it.prerelease }
    }

    /** 업데이트 파일을 내려받는다. onProgress(0.0~1.0) */
    suspend fun download(url: String, onProgress: (Double) -> Unit = {}): ByteArray {
        return client.prepareGet(url) { header("User-Agent", "SkyblockHelper") }.execute { res ->
            if (!res.status.isSuccess()) throw ApiException("다운로드 실패 (HTTP ${res.status.value})")
            val total = res.contentLength() ?: -1L
            val channel = res.bodyAsChannel()
            val out = ArrayList<ByteArray>()
            var received = 0L
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n == -1) break
                if (n > 0) {
                    out += buf.copyOf(n)
                    received += n
                    if (total > 0) onProgress(received.toDouble() / total)
                }
                if (channel.isClosedForRead) break
            }
            val result = ByteArray(received.toInt())
            var pos = 0
            for (chunk in out) { chunk.copyInto(result, pos); pos += chunk.size }
            result
        }
    }

    /** 매일 위키에서 새로 만든 게임 데이터 (GitHub Actions → data 브랜치) */
    suspend fun latestGameData(): String? {
        val res = runCatching {
            client.get("https://raw.githubusercontent.com/$repo/data/gamedata.json") { header("User-Agent", "SkyblockHelper") }
        }.getOrNull() ?: return null
        return if (res.status.isSuccess()) res.bodyAsText() else null
    }

    /** data 브랜치의 아이콘 묶음 버전 (작은 텍스트) */
    suspend fun latestIconPackVersion(): String? = runCatching {
        val res = client.get("https://raw.githubusercontent.com/$repo/data/icons-version.txt") { header("User-Agent", "SkyblockHelper") }
        if (res.status.isSuccess()) res.bodyAsText().trim().takeIf { it.isNotEmpty() && it.length <= 64 } else null
    }.getOrNull()

    /** data 브랜치 파일 (바이너리) */
    suspend fun dataFile(name: String): ByteArray? = runCatching {
        download("https://raw.githubusercontent.com/$repo/data/$name")
    }.getOrNull()

    companion object {
        const val REPO = "pkgw07/SkyblockHelper"

        /** "1.2.10" > "1.2.9" */
        fun isNewer(candidate: String, current: String): Boolean {
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
}
