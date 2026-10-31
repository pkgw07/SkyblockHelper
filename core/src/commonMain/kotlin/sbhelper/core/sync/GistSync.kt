package sbhelper.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import sbhelper.core.api.ApiException
import sbhelper.core.api.SbJson
import sbhelper.core.data.SyncedSettings
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * PC ↔ 휴대폰 동기화.
 * 서버를 따로 운영하지 않고, 사용자의 GitHub 계정에 "비공개 Gist" 하나를 만들어 설정을 저장한다.
 * 두 기기가 같은 GitHub 토큰(gist 권한)을 쓰면 같은 Gist 를 찾아 자동으로 맞춘다.
 */
class GistSync(private val client: HttpClient) {

    data class Remote(val gistId: String, val settings: SyncedSettings?)

    private suspend fun call(token: String, block: suspend HttpClient.() -> HttpResponse): String {
        val res = try { client.block() } catch (e: Exception) {
            throw ApiException("GitHub 에 연결할 수 없어요: ${e.message}")
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) {
            throw ApiException(
                when (res.status.value) {
                    401 -> "GitHub 토큰이 올바르지 않아요."
                    403, 404 -> "GitHub 토큰에 gist 권한이 있는지 확인해 주세요."
                    else -> "GitHub 오류 (HTTP ${res.status.value})"
                }, res.status.value,
            )
        }
        return text
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) {
        header("Authorization", "Bearer ${token.trim()}")
        header("Accept", "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
        header("User-Agent", "SkyblockHelper")
    }

    /** gistId 가 없으면 토큰 소유자의 Gist 목록에서 찾아본다. */
    suspend fun find(token: String, knownGistId: String): Remote? {
        if (knownGistId.isNotBlank()) return Remote(knownGistId, download(token, knownGistId))
        val list = call(token) { get("$BASE/gists?per_page=100") { auth(token) } }
        val gist = SbJson.parseToJsonElement(list).jsonArray.firstOrNull {
            it.jsonObject["description"]?.jsonPrimitive?.content == DESCRIPTION
        } ?: return null
        val id = gist.jsonObject["id"]!!.jsonPrimitive.content
        return Remote(id, download(token, id))
    }

    suspend fun download(token: String, gistId: String): SyncedSettings? {
        val text = call(token) { get("$BASE/gists/$gistId") { auth(token) } }
        val file = SbJson.parseToJsonElement(text).jsonObject["files"]?.jsonObject?.get(FILE_NAME) as? JsonObject
            ?: return null
        val content = (file["content"] as? JsonPrimitive)?.content ?: return null
        return runCatching { SbJson.decodeFromString(SyncedSettings.serializer(), content) }.getOrNull()
    }

    /** 업로드하고 gistId 를 돌려준다. */
    suspend fun upload(token: String, gistId: String, settings: SyncedSettings): String {
        val content = SbJson.encodeToString(SyncedSettings.serializer(), settings)
        val body = buildJsonObject {
            put("description", DESCRIPTION)
            if (gistId.isBlank()) put("public", false)
            putJsonObject("files") { putJsonObject(FILE_NAME) { put("content", content) } }
        }.toString()
        val text = if (gistId.isBlank()) {
            call(token) { post("$BASE/gists") { auth(token); contentType(ContentType.Application.Json); setBody(body) } }
        } else {
            call(token) { patch("$BASE/gists/$gistId") { auth(token); contentType(ContentType.Application.Json); setBody(body) } }
        }
        return SbJson.parseToJsonElement(text).jsonObject["id"]!!.jsonPrimitive.content
    }

    /**
     * 동기화: 원격과 로컬 중 최신 것을 고른다.
     * @return (최종 설정, gistId)
     */
    suspend fun sync(token: String, gistId: String, local: SyncedSettings): Pair<SyncedSettings, String> {
        val remote = find(token, gistId)
        val remoteSettings = remote?.settings
        return if (remoteSettings != null && remoteSettings.updatedAt > local.updatedAt) {
            remoteSettings to remote.gistId
        } else {
            local to upload(token, remote?.gistId.orEmpty(), local)
        }
    }

    companion object {
        const val BASE = "https://api.github.com"
        const val DESCRIPTION = "SkyblockHelper Sync (자동 생성 — 삭제하면 기기 연동이 끊겨요)"
        const val FILE_NAME = "skyblockhelper-sync.json"
    }
}

/**
 * 연동 코드: 다른 기기에 토큰을 쉽게 옮기기 위한 문자열 (QR 코드로도 보여 준다).
 * 형식: "SBH1:" + base64(token|gistId)
 */
@OptIn(ExperimentalEncodingApi::class)
object PairingCode {
    private const val PREFIX = "SBH1:"

    fun encode(token: String, gistId: String): String =
        PREFIX + Base64.UrlSafe.encode("$token|$gistId".encodeToByteArray())

    fun decode(code: String): Pair<String, String>? {
        val c = code.trim()
        if (!c.startsWith(PREFIX)) return null
        val raw = runCatching { Base64.UrlSafe.decode(c.removePrefix(PREFIX)).decodeToString() }.getOrNull() ?: return null
        val parts = raw.split('|')
        if (parts.size != 2 || parts[0].isBlank()) return null
        return parts[0] to parts[1]
    }
}
