package sbhelper.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * ⚠ 비공식 출처. 공식 API·위키에 없는 정보라서 예외적으로 쓴다.
 * 운영자가 주소나 형식을 바꾸면 동작하지 않을 수 있으므로, 실패해도 다른 기능에 영향이 없게 null/원문을 돌려준다.
 */

/** 제이콥 농사대회 작물 일정 — EliteBot (elitebot.dev, 농사 커뮤니티 운영) */
class EliteBotApi(private val client: HttpClient = defaultHttpClient()) {

    /** 이번 스카이블럭 연도의 대회 시작 시각(ms) → 작물 이름 목록. 실패하면 빈 맵 */
    suspend fun upcomingContests(): Map<Long, List<String>> = runCatching {
        val res = client.get("https://api.elitebot.dev/contests/at/now") { header("User-Agent", HypixelApi.USER_AGENT) }
        if (!res.status.isSuccess()) return emptyMap()
        parse(SbJson.parseToJsonElement(res.bodyAsText()))
    }.getOrDefault(emptyMap())

    companion object {
        /** {"contests": {"<시각>": ["Wheat", ...]}} 형태를 찾아 읽는다 (초/밀리초 모두 허용) */
        fun parse(root: JsonElement): Map<Long, List<String>> {
            val contests = (root as? JsonObject)?.get("contests") as? JsonObject ?: return emptyMap()
            return contests.mapNotNull { (k, v) ->
                val t = k.toLongOrNull() ?: return@mapNotNull null
                val crops = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
                (if (t < 100_000_000_000L) t * 1000 else t) to crops
            }.toMap()
        }
    }
}

/** 패치노트 번역 — Google 번역 공개 주소 (키 없음) */
class Translator(private val client: HttpClient = defaultHttpClient()) {

    /**
     * 여러 줄을 한국어로. 실패한 줄은 null (나중에 그 줄만 다시 시도).
     * 요청 제한에 걸리지 않도록 묶음 사이에 잠깐 쉬고, 실패하면 한 번 더 시도한다.
     */
    suspend fun toKorean(lines: List<String>, only: Set<Int>? = null): List<String?> {
        val out = MutableList<String?>(lines.size) { null }
        val todo = lines.indices.filter { (only == null || it in only) && lines[it].isNotBlank() }
        lines.indices.filter { lines[it].isBlank() }.forEach { out[it] = lines[it] }
        var i = 0
        while (i < todo.size) {
            var j = i
            var size = 0
            while (j < todo.size && (j == i || size + lines[todo[j]].length < 1200)) { size += lines[todo[j]].length + 1; j++ }
            val idx = todo.subList(i, j)
            val joined = idx.joinToString("\n") { lines[it].replace('\n', ' ') }
            val parts = (translate(joined) ?: run { kotlinx.coroutines.delay(1200); translate(joined) })?.split('\n')
            if (parts != null && parts.size == idx.size) {
                idx.forEachIndexed { k, line -> out[line] = parts[k] }
            } else {
                for (line in idx) {
                    out[line] = translate(lines[line]) ?: run { kotlinx.coroutines.delay(800); translate(lines[line]) }
                    kotlinx.coroutines.delay(120)
                }
            }
            kotlinx.coroutines.delay(250)
            i = j
        }
        return out
    }

    private suspend fun translate(text: String): String? = runCatching {
        if (text.isBlank()) return text
        val res = client.get("https://translate.googleapis.com/translate_a/single") {
            header("User-Agent", "Mozilla/5.0")
            parameter("client", "gtx"); parameter("sl", "en"); parameter("tl", "ko"); parameter("dt", "t")
            parameter("q", text)
        }
        if (!res.status.isSuccess()) return null
        val root = SbJson.parseToJsonElement(res.bodyAsText()) as? JsonArray ?: return null
        val segments = root.firstOrNull() as? JsonArray ?: return null
        segments.joinToString("") { seg -> ((seg as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content.orEmpty() }
            .trimEnd('\n')
    }.getOrNull()
}
