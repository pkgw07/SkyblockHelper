package sbhelper.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
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

    /** 요청이 너무 많으면 Google 이 잠시 막는다(sorry 페이지). 막힌 동안은 요청하지 않는다 */
    private var blockedUntil = 0L

    /** 지금 막혀 있으면 풀리는 시각(ms), 아니면 0 */
    val blockedUntilMs get() = if (blocked()) blockedUntil else 0L

    /**
     * 여러 줄을 한국어로. 실패한 줄은 null (나중에 그 줄만 다시 시도).
     *
     * 요청 수를 줄이려고 큰 묶음(약 1,800자)으로 보내고, 줄 수가 맞지 않으면 묶음을 반으로 나눠 다시 보낸다
     * (예전처럼 한 줄씩 보내면 요청이 많아져 금방 막히고, 아래쪽이 번역되지 않았다).
     */
    suspend fun toKorean(lines: List<String>, only: Set<Int>? = null): List<String?> {
        val out = MutableList<String?>(lines.size) { null }
        val todo = lines.indices.filter { (only == null || it in only) && lines[it].isNotBlank() }
        lines.indices.filter { lines[it].isBlank() }.forEach { out[it] = lines[it] }
        var i = 0
        while (i < todo.size) {
            var j = i
            var size = 0
            while (j < todo.size && (j == i || size + lines[todo[j]].length < MAX_CHARS)) { size += lines[todo[j]].length + 1; j++ }
            translateGroup(lines, todo.subList(i, j), out, depth = 0)
            if (blocked()) break
            kotlinx.coroutines.delay(400)
            i = j
        }
        return out
    }

    private suspend fun translateGroup(lines: List<String>, idx: List<Int>, out: MutableList<String?>, depth: Int) {
        if (idx.isEmpty() || blocked()) return
        val joined = idx.joinToString("\n") { lines[it].replace('\n', ' ') }
        val parts = translate(joined)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (parts != null && parts.size == idx.size) {
            idx.forEachIndexed { k, line -> out[line] = parts[k] }
            return
        }
        if (parts != null && idx.size == 1) { out[idx[0]] = parts.joinToString(" "); return }
        if (parts == null || depth > 6) return
        // 줄 수가 어긋남 → 반으로 나눠 다시
        kotlinx.coroutines.delay(300)
        val half = idx.size / 2
        translateGroup(lines, idx.subList(0, half), out, depth + 1)
        translateGroup(lines, idx.subList(half, idx.size), out, depth + 1)
    }

    private fun blocked() = sbhelper.core.util.Clock.now() < blockedUntil

    private suspend fun translate(text: String): String? {
        if (text.isBlank()) return text
        if (blocked()) return null
        return primary(text) ?: backup(text) ?: run {
            // 둘 다 막힘 → 3분 동안 쉬었다가 나중에 다시
            blockedUntil = sbhelper.core.util.Clock.now() + 3 * 60_000
            null
        }
    }

    /** translate.googleapis.com (POST: 긴 글도 주소 길이 제한 없음) */
    private suspend fun primary(text: String): String? = runCatching {
        val res = client.submitForm(
            "https://translate.googleapis.com/translate_a/single",
            io.ktor.http.parameters { append("client", "gtx"); append("sl", "en"); append("tl", "ko"); append("dt", "t"); append("q", text) },
        ) { header("User-Agent", "Mozilla/5.0") }
        if (!res.status.isSuccess()) return null
        val root = SbJson.parseToJsonElement(res.bodyAsText()) as? JsonArray ?: return null
        val segments = root.firstOrNull() as? JsonArray ?: return null
        segments.joinToString("") { seg -> ((seg as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content.orEmpty() }
            .trimEnd('\n')
    }.getOrNull()

    /** 예비 주소 (clients5.google.com) — 응답: ["번역"] 또는 [["번역","en"]] */
    private suspend fun backup(text: String): String? = runCatching {
        val res = client.submitForm(
            "https://clients5.google.com/translate_a/t",
            io.ktor.http.parameters { append("client", "dict-chrome-ex"); append("sl", "en"); append("tl", "ko"); append("q", text) },
        ) { header("User-Agent", "Mozilla/5.0") }
        if (!res.status.isSuccess()) return null
        val root = SbJson.parseToJsonElement(res.bodyAsText()) as? JsonArray ?: return null
        when (val first = root.firstOrNull()) {
            is JsonPrimitive -> first.content
            is JsonArray -> (first.firstOrNull() as? JsonPrimitive)?.content
            else -> null
        }?.trimEnd('\n')
    }.getOrNull()

    companion object {
        const val MAX_CHARS = 1800
    }
}
