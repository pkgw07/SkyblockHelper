package sbhelper.core.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import sbhelper.core.model.Auction
import sbhelper.core.model.AuctionsPage
import sbhelper.core.model.BazaarResponse
import sbhelper.core.model.CollectionsResponse
import sbhelper.core.model.ElectionResponse
import sbhelper.core.model.FireSalesResponse
import sbhelper.core.model.ItemsResponse
import sbhelper.core.model.MojangProfile
import sbhelper.core.model.NewsResponse
import sbhelper.core.model.PlayerAuctionsResponse
import sbhelper.core.model.SkillsResponse
import sbhelper.core.util.Clock

class ApiException(message: String, val status: Int? = null) : Exception(message)

val SbJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

fun defaultHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
    }
    expectSuccess = false
}

/**
 * Hypixel 공식 API v2 + Mojang 닉네임 조회.
 * 키가 필요 없는 엔드포인트: 바자, 경매장 목록, 아이템/스킬/컬렉션/선거 정보, 뉴스, 파이어세일
 * 키가 필요한 엔드포인트: 프로필, 플레이어 경매, 가든, 박물관
 */
class HypixelApi(
    private val client: HttpClient = defaultHttpClient(),
    private val apiKey: () -> String? = { null },
) {
    private val cache = HashMap<String, Pair<Long, String>>()
    private val cacheLock = Mutex()

    private suspend fun getText(url: String, ttlMillis: Long, needsKey: Boolean = false): String {
        cacheLock.withLock {
            cache[url]?.let { (at, body) -> if (Clock.now() - at < ttlMillis) return body }
        }
        val response: HttpResponse = try {
            client.get(url) {
                header("User-Agent", USER_AGENT)
                if (needsKey) {
                    val key = apiKey()?.trim().orEmpty()
                    if (key.isEmpty()) throw ApiException("이 기능은 Hypixel API 키가 필요해요. [설정 → 계정]에서 키를 입력해 주세요.")
                    header("API-Key", key)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요. (${e.message ?: e::class.simpleName})")
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw ApiException(explainStatus(response.status.value, body), response.status.value)
        }
        cacheLock.withLock { cache[url] = Clock.now() to body }
        return body
    }

    private suspend inline fun <reified T> get(path: String, ttlMillis: Long, needsKey: Boolean = false): T =
        SbJson.decodeFromString(getText(BASE + path, ttlMillis, needsKey))

    fun clearCache() { cache.clear() }

    // ----- 키 불필요 -----
    suspend fun bazaar(): BazaarResponse = get("/skyblock/bazaar", 20_000)
    suspend fun items(): ItemsResponse = get("/resources/skyblock/items", 6 * HOUR)
    suspend fun skills(): SkillsResponse = get("/resources/skyblock/skills", 6 * HOUR)
    suspend fun collections(): CollectionsResponse = get("/resources/skyblock/collections", 6 * HOUR)
    suspend fun election(): ElectionResponse = get("/resources/skyblock/election", 5 * MINUTE)
    suspend fun news(): NewsResponse = get("/skyblock/news", 30 * MINUTE)
    suspend fun fireSales(): FireSalesResponse = get("/skyblock/firesales", 5 * MINUTE)
    suspend fun auctionsPage(page: Int): AuctionsPage = get("/skyblock/auctions?page=$page", 50_000)

    /** 경매장 전체를 병렬로 받아 온다. onProgress(받은 페이지, 전체 페이지) */
    suspend fun allAuctions(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Auction> = coroutineScope {
        val first = auctionsPage(0)
        val total = first.totalPages.coerceAtLeast(1)
        var done = 1
        onProgress(done, total)
        val gate = Semaphore(6)
        val lock = Mutex()
        val rest = (1 until total).map { page ->
            async {
                val list = gate.withPermit {
                    runCatching { auctionsPage(page).auctions }.getOrDefault(emptyList())
                }
                lock.withLock { done++; onProgress(done, total) }
                list
            }
        }
        first.auctions + rest.flatMap { it.await() }
    }

    // ----- 키 필요 -----
    /** 프로필 원본(JSON). 내부 구조는 공식 문서에 자세히 나와 있지 않아 ProfileParser 에서 유연하게 읽는다. */
    suspend fun profiles(uuid: String): List<JsonObject> {
        val root = SbJson.parseToJsonElement(getText("$BASE/skyblock/profiles?uuid=$uuid", 60_000, needsKey = true)).jsonObject
        val arr = root["profiles"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    suspend fun playerAuctions(uuid: String): List<Auction> =
        get<PlayerAuctionsResponse>("/skyblock/auction?player=$uuid", 60_000, needsKey = true).auctions

    suspend fun garden(profileId: String): JsonObject? {
        val root = SbJson.parseToJsonElement(getText("$BASE/skyblock/garden?profile=$profileId", 2 * MINUTE, needsKey = true)).jsonObject
        return root["garden"] as? JsonObject
    }

    // ----- Mojang -----
    suspend fun uuidFor(username: String): MojangProfile {
        val name = username.trim().encodeURLPathPart()
        val urls = listOf(
            "https://api.mojang.com/users/profiles/minecraft/$name",
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/$name",
        )
        var last: Exception = ApiException("'$username' 닉네임을 찾을 수 없어요.")
        for (url in urls) {
            try {
                val text = getText(url, HOUR)
                if (text.isBlank()) continue
                return SbJson.decodeFromString<MojangProfile>(text)
            } catch (e: ApiException) {
                last = if (e.status == 404 || e.status == 204) ApiException("'$username' 닉네임을 찾을 수 없어요.") else e
            }
        }
        throw last
    }

    companion object {
        const val BASE = "https://api.hypixel.net/v2"
        const val USER_AGENT = "SkyblockHelper/1.0 (+https://github.com/pkgw07/SkyblockHelper)"
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        fun explainStatus(code: Int, body: String): String {
            val cause = runCatching {
                SbJson.parseToJsonElement(body).jsonObject["cause"]?.toString()?.trim('"')
            }.getOrNull()
            val msg = when (code) {
                400 -> "요청 정보가 부족해요."
                403 -> "API 키가 올바르지 않거나 만료되었어요. [설정 → 계정]에서 키를 확인해 주세요."
                404 -> "찾을 수 없어요."
                422 -> "요청 형식이 잘못되었어요."
                429 -> "요청이 너무 많아요. 잠시 후 자동으로 다시 시도해요."
                503 -> "Hypixel API가 데이터를 준비 중이에요. 잠시 후 다시 시도해 주세요."
                else -> "서버 오류 (HTTP $code)"
            }
            return if (cause.isNullOrBlank()) msg else "$msg ($cause)"
        }
    }
}
