package sbhelper.core.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 위키 Changelog 의 패치노트 한 건 */
@Serializable
data class PatchNote(
    val page: String,
    val title: String,
    val date: String,
    val year: Int,
    val dateInt: Int,
    val description: String,
    val link: String,
    /** Alpha Network(테스트 서버) 패치인가 */
    val alpha: Boolean,
) {
    val key get() = page
}

/** 패치노트 본문을 화면에 그리기 위한 덩어리 */
sealed interface DocBlock {
    data class Heading(val text: String, val level: Int) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data class Bullet(val text: String, val depth: Int) : DocBlock
    data class Image(val url: String) : DocBlock
}

/**
 * 공식 Hypixel SkyBlock 위키 (hypixelskyblock.minecraft.wiki) 접근.
 * 패치노트 목록은 위키의 구조화 데이터(Bucket:Changelog), 본문은 각 Changelog 페이지에서 가져온다.
 */
class WikiApi(private val client: HttpClient = defaultHttpClient()) {

    private suspend fun api(vararg params: Pair<String, String>): JsonObject {
        val res = try {
            client.get(API) {
                header("User-Agent", HypixelApi.USER_AGENT)
                params.forEach { (k, v) -> parameter(k, v) }
                parameter("format", "json")
            }
        } catch (e: Exception) {
            throw ApiException("위키에 연결할 수 없어요. (${e.message})")
        }
        if (!res.status.isSuccess()) throw ApiException("위키 오류 (HTTP ${res.status.value})", res.status.value)
        return SbJson.parseToJsonElement(res.bodyAsText()).jsonObject
    }

    /** 최근 패치노트 (최신순) */
    suspend fun patchNotes(year: Int, limit: Int = 40): List<PatchNote> {
        val fields = "'page_name','year','date_int','date','ahn','link','title','description'"
        fun query(y: Int) = "bucket('changelog').select($fields).where('year',$y).orderBy('date_int','desc').limit($limit).run()"
        val rows = (api("action" to "bucket", "query" to query(year))["bucket"] as? JsonArray).orEmpty() +
            (api("action" to "bucket", "query" to query(year - 1))["bucket"] as? JsonArray).orEmpty()
        return rows.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
            PatchNote(
                page = s("page_name"),
                title = s("title").ifBlank { s("page_name").substringAfterLast('/') },
                date = s("date"),
                year = s("year").toIntOrNull() ?: year,
                dateInt = s("date_int").toIntOrNull() ?: 0,
                description = WikiText.inline(s("description")),
                link = s("link"),
                alpha = o.containsKey("ahn"),
            )
        }.sortedWith(compareByDescending<PatchNote> { it.year }.thenByDescending { it.dateInt }).take(limit)
    }

    /** 패치노트 본문 */
    suspend fun page(page: String): List<DocBlock> {
        val root = api("action" to "parse", "page" to page, "prop" to "wikitext", "redirects" to "1")
        val text = root["parse"]?.jsonObject?.get("wikitext")?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: throw ApiException("위키 페이지를 찾을 수 없어요: $page")
        // 탭으로 나뉜 페이지(Tabview)는 첫 번째 탭들을 이어 붙인다
        val tabs = Regex("""\|-\|([^|\n]+)\|""").findAll(text).map { it.groupValues[1].trim() }.toList()
        if (text.contains("{{Tabview") && tabs.isNotEmpty()) {
            return tabs.flatMap { tab -> listOf(DocBlock.Heading(tab.substringAfterLast('/'), 1)) + page(tab) }
        }
        return WikiText.blocks(text)
    }

    companion object {
        const val BASE = "https://hypixelskyblock.minecraft.wiki"
        const val API = "$BASE/api.php"
        fun pageUrl(page: String) = "$BASE/w/" + page.replace(' ', '_')
    }
}

/** 위키 문법 → 읽을 수 있는 글자 */
object WikiText {
    private val ref = Regex("""<ref[^>]*>.*?</ref>|<ref[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
    private val file = Regex("""\[\[File:([^|\]]+)[^\]]*]]""")
    private val link = Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]""")
    private val extLink = Regex("""\[(https?://\S+)\s+([^\]]+)]""")
    private val simpleTemplate = Regex("""\{\{([^{}|]+)\|([^{}]*)}}""")
    private val bareTemplate = Regex("""\{\{[^{}]*}}""")
    private val tag = Regex("""<[^>]+>""")

    /** 한 줄 정리: [[A|B]]→B, {{Green|10%}}→10%, 굵게 표시 제거 */
    fun inline(src: String): String {
        var t = ref.replace(src, "")
        t = file.replace(t, "")
        repeat(4) {
            t = simpleTemplate.replace(t) { m ->
                val args = m.groupValues[2].split('|').filterNot { '=' in it }
                args.lastOrNull().orEmpty()
            }
        }
        t = bareTemplate.replace(t, "")
        t = link.replace(t) { it.groupValues[1] }
        t = extLink.replace(t) { it.groupValues[2] }
        t = t.replace("<br>", " ").replace("<br/>", " ").replace("<br />", " ")
        t = tag.replace(t, "")
        t = t.replace("'''", "").replace("''", "").replace("&nbsp;", " ")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    fun blocks(src: String): List<DocBlock> {
        val out = mutableListOf<DocBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            val p = inline(paragraph.toString())
            if (p.isNotBlank()) out += DocBlock.Paragraph(p)
            paragraph.clear()
        }
        for (raw in ref.replace(src, "").lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("{{Update Link") || line.startsWith("{{Tabview") || line.startsWith("__") -> Unit
                line.startsWith("==") -> {
                    flush()
                    val level = line.takeWhile { it == '=' }.length
                    val text = inline(line.trim('=').trim().removePrefix("►").trim())
                    if (text.isNotBlank()) out += DocBlock.Heading(text, (level - 1).coerceIn(1, 3))
                }
                line.startsWith("*") || line.startsWith("#") -> {
                    flush()
                    val depth = line.takeWhile { it == '*' || it == '#' }.length
                    val text = inline(line.drop(depth))
                    if (text.isNotBlank()) out += DocBlock.Bullet(text, depth)
                }
                line.startsWith("[[File:") -> {
                    flush()
                    file.find(line)?.groupValues?.get(1)?.let { name ->
                        out += DocBlock.Image("${WikiApi.BASE}/w/Special:FilePath/" + name.trim().replace(' ', '_'))
                    }
                }
                line.startsWith("{|") || line.startsWith("|") || line.startsWith("!") -> {
                    // 표는 간단히 줄글로
                    val cells = inline(line.trimStart('{', '|', '!', '-', '}'))
                    if (cells.isNotBlank() && !cells.contains("class=")) paragraph.append(cells).append(' ')
                }
                else -> paragraph.append(line).append(' ')
            }
        }
        flush()
        return out
    }
}
