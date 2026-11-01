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
/** 글 안의 위키 링크: text[start, end) 를 누르면 page 로 */
data class WikiLink(val start: Int, val end: Int, val page: String)

sealed interface DocBlock {
    data class Heading(val text: String, val level: Int, val links: List<WikiLink> = emptyList()) : DocBlock
    data class Paragraph(val text: String, val links: List<WikiLink> = emptyList()) : DocBlock
    data class Bullet(val text: String, val depth: Int, val links: List<WikiLink> = emptyList()) : DocBlock
    data class Image(val url: String) : DocBlock
}

/** 위키 검색 결과 */
data class WikiHit(val title: String, val snippet: String)

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

    /** 위키 문서 검색 (제목·본문) */
    suspend fun search(query: String, limit: Int = 12): List<WikiHit> {
        // 1) 제목 앞부분 일치 (opensearch) 2) 본문 검색
        val prefix = runCatching {
            val res = client.get(API) {
                header("User-Agent", HypixelApi.USER_AGENT)
                parameter("action", "opensearch"); parameter("search", query); parameter("limit", "8"); parameter("format", "json")
            }
            ((SbJson.parseToJsonElement(res.bodyAsText()) as? JsonArray)?.getOrNull(1) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        }.getOrDefault(emptyList()).map { WikiHit(it, "") }
        val full = runCatching { fullText(query, limit) }.getOrDefault(emptyList())
        return (prefix + full).distinctBy { it.title }.take(limit)
    }

    private suspend fun fullText(query: String, limit: Int): List<WikiHit> {
        val root = api("action" to "query", "list" to "search", "srsearch" to query, "srlimit" to limit.toString(),
            "srnamespace" to "0", "srprop" to "snippet")
        val arr = root["query"]?.jsonObject?.get("search") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val snip = (o["snippet"] as? JsonPrimitive)?.content.orEmpty().replace(Regex("<[^>]+>"), "").replace("&quot;", "\"")
            WikiHit(title, snip)
        }
    }

    /** 문서 대표 그림 주소 (위키 PageImages). 여러 제목을 한 번에 → 제목 → 그림 주소 */
    suspend fun pageImages(titles: List<String>): Map<String, String> {
        if (titles.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        titles.distinct().chunked(50).forEach { chunk ->
            val root = api("action" to "query", "titles" to chunk.joinToString("|"), "prop" to "pageimages",
                "piprop" to "original", "redirects" to "1")
            val q = root["query"]?.jsonObject ?: return@forEach
            val alias = HashMap<String, String>()
            listOf("normalized", "redirects").forEach { k ->
                (q[k] as? JsonArray)?.forEach { r ->
                    val o = r as? JsonObject ?: return@forEach
                    val from = (o["from"] as? JsonPrimitive)?.content ?: return@forEach
                    val to = (o["to"] as? JsonPrimitive)?.content ?: return@forEach
                    alias[to] = alias[from] ?: from
                }
            }
            q["pages"]?.jsonObject?.values?.forEach { pg ->
                val o = pg as? JsonObject ?: return@forEach
                val title = (o["title"] as? JsonPrimitive)?.content ?: return@forEach
                val src = (o["original"] as? JsonObject)?.get("source")?.let { (it as? JsonPrimitive)?.content } ?: return@forEach
                out[alias[title] ?: title] = src
            }
        }
        return out
    }

    /** 받은 문서 원문 (본문·툴팁이 같이 쓴다, 최근 60개) */
    private val rawCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 60
    }

    /** 위키 문서 원문 (위키 문법 그대로) */
    suspend fun wikitext(page: String): String {
        rawCache[page]?.let { return it }
        val root = api("action" to "parse", "page" to page, "prop" to "wikitext", "redirects" to "1")
        val text = root["parse"]?.jsonObject?.get("wikitext")?.jsonObject?.get("*")?.jsonPrimitive?.content
            ?: throw ApiException("위키 페이지를 찾을 수 없어요: $page")
        rawCache[page] = text
        return text
    }

    /** 아이템 문서의 게임 툴팁 (스탯·능력·세트 효과). 없으면 null */
    suspend fun itemTooltip(page: String, itemName: String?): WikiItemInfo.Tooltip? =
        WikiItemInfo.parse(wikitext(page), itemName)

    /** 위키 문서 본문 (패치노트·아이템·NPC·지역 등) */
    suspend fun page(page: String): List<DocBlock> {
        val text = wikitext(page)
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
    /** 위키 숨김 주석 <!-- … --> (여러 줄, 안 닫힌 것은 끝까지) */
    private val comment = Regex("""<!--.*?(-->|$)""", RegexOption.DOT_MATCHES_ALL)
    private val file = Regex("""\[\[File:([^|\]]+)[^\]]*]]""")
    private val link = Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]""")
    private val extLink = Regex("""\[(https?://\S+)\s+([^\]]+)]""")
    private val simpleTemplate = Regex("""\{\{([^{}|]+)\|([^{}]*)}}""")
    private val bareTemplate = Regex("""\{\{[^{}]*}}""")
    private val tag = Regex("""<[^>]+>""")

    // 링크 표시용 임시 기호: \u0001페이지\u0002보이는 글\u0003
    private const val L1 = '\u0001'
    private const val L2 = '\u0002'
    private const val L3 = '\u0003'
    private val pageLink = Regex("""\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|([^\]]*))?]]""")
    /** {{Item|이름|amount=2}}, {{NPCSprite|이름}}, {{Zone|이름}} 처럼 문서로 이어지는 틀 */
    private val linkTemplate = Regex("""\{\{(Item|ItemSprite|NPCSprite|NPC|Zone|ZoneSprite|Mob|MobSprite|Pet|PetSprite|Skl|Skill|Coll)\|([^{}|]+)((?:\|[^{}]*)?)}}""", RegexOption.IGNORE_CASE)
    /** {{stat|fuel|3,000}} → "3,000 Fuel", {{Stat|ms}} → "Mining Speed" */
    private val statTemplate = Regex("""\{\{stat\|([^{}|]+)(?:\|([^{}|]*))?(?:\|[^{}]*)?}}""", RegexOption.IGNORE_CASE)
    /** 앱에서 따로 보여 주거나 의미 없는 틀 (제작 트리, 조합법 표 등) */
    private val dropTemplate = Regex("""\{\{(DRL|RL|Crafting Usage|Recipe Tree|Forging Tree|Redirect|About|For|Distinguish|Otheruses|Other uses|Main|See also|Further|Hatnote|Disambig|Stub|Outdated|Cleanup)(\|[^{}]*)?}}""", RegexOption.IGNORE_CASE)
    private val STAT_NAMES = mapOf(
        "ms" to "Mining Speed", "mnf" to "Mining Fortune", "mf" to "Magic Find", "cc" to "Crit Chance", "cd" to "Crit Damage",
        "str" to "Strength", "def" to "Defense", "hp" to "Health", "int" to "Intelligence", "spd" to "Speed", "fer" to "Ferocity",
        "as" to "Bonus Attack Speed", "bp" to "Breaking Power", "ff" to "Farming Fortune", "fof" to "Foraging Fortune",
        "scc" to "Sea Creature Chance", "fs" to "Fishing Speed", "pp" to "Pristine", "hpr" to "Health Regen", "vit" to "Vitality",
        "td" to "True Defense", "mend" to "Mending", "pet luck" to "Pet Luck", "abs" to "Ability Damage",
    )

    /** 한 줄 정리: [[A|B]]→B, {{Green|10%}}→10%, 굵게 표시 제거 */
    fun inline(src: String): String = inlineLinks(src).first

    /** 한 줄 정리 + 링크 위치 */
    fun inlineLinks(src: String): Pair<String, List<WikiLink>> {
        var t = ref.replace(comment.replace(src, ""), "")
        t = file.replace(t, "")
        t = dropTemplate.replace(t, "")
        t = statTemplate.replace(t) { m ->
            val key = m.groupValues[1].trim()
            val stat = STAT_NAMES[key.lowercase()] ?: key.split(' ', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            val value = m.groupValues[2].trim()
            if (value.isEmpty()) stat else "$value $stat"
        }
        t = linkTemplate.replace(t) { m ->
            val kind = m.groupValues[1].lowercase()
            // {{Mob|Enderman;Endermen}}: ';' 앞은 문서, 뒤는 보이는 글
            val raw = m.groupValues[2].trim()
            val target = raw.substringBefore(';').trim()
            val params = m.groupValues[3]
            val amount = Regex("""amount=([\d,]+)""").find(params)?.groupValues?.get(1)
            val text = Regex("""\|\s*text\s*=\s*([^|]+)""").find(params)?.groupValues?.get(1)?.trim()
            val page = when (kind) { "skl", "skill" -> target.replace(Regex("""\s+\d+$"""), ""); "coll" -> target.replace(Regex("""\s+\d+$"""), ""); else -> target }
            val label = text ?: raw.substringAfter(';', target).trim().ifBlank { target }
            val shown = (if (amount != null) "$amount× " else "") + label
            "$L1$page$L2$shown$L3"
        }
        t = pageLink.replace(t) { m ->
            val page = m.groupValues[1].trim()
            if (page.contains(':')) m.groupValues[2].ifBlank { page }
            else "$L1$page$L2${m.groupValues[2].ifBlank { page }}$L3"
        }
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
        // 게임 색 코드(&7, §a …)는 글자에서 뺀다
        t = t.replace(Regex("""[&§][0-9a-fk-orA-FK-OR]"""), "")
        t = t.replace(Regex("""\s+"""), " ").trim()
        // 기호를 빼면서 링크 위치를 잰다
        val sb = StringBuilder()
        val links = mutableListOf<WikiLink>()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == L1) {
                val mid = t.indexOf(L2, i)
                val end = if (mid >= 0) t.indexOf(L3, mid) else -1
                if (mid < 0 || end < 0) { i++; continue }
                val page = t.substring(i + 1, mid)
                val shown = t.substring(mid + 1, end)
                val start = sb.length
                sb.append(shown)
                if (shown.isNotBlank()) links += WikiLink(start, sb.length, page)
                i = end + 1
            } else {
                if (c != L2 && c != L3) sb.append(c)
                i++
            }
        }
        return sb.toString() to links
    }

    /** 줄 끝까지 닫히지 않는 첫 번째 "{{" 의 위치 (없으면 -1) */
    private fun unclosedTemplateStart(line: String): Int {
        val stack = ArrayDeque<Int>()
        var i = 0
        while (i < line.length - 1) {
            if (line[i] == '{' && line[i + 1] == '{') { stack.addLast(i); i += 2; continue }
            if (line[i] == '}' && line[i + 1] == '}') { if (stack.isNotEmpty()) stack.removeLast(); i += 2; continue }
            i++
        }
        return stack.firstOrNull() ?: -1
    }

    fun blocks(src: String): List<DocBlock> {
        val out = mutableListOf<DocBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            val (p, links) = inlineLinks(paragraph.toString())
            if (p.isNotBlank()) out += DocBlock.Paragraph(p, links)
            paragraph.clear()
        }
        val row = mutableListOf<String>()
        fun flushRow() {
            if (row.isNotEmpty()) {
                val (t, links) = inlineLinks(row.joinToString(" · "))
                if (t.isNotBlank()) out += DocBlock.Bullet(t, 1, links)
                row.clear()
            }
        }
        var skipDepth = 0
        for (raw in ref.replace(comment.replace(src, ""), "").lines()) {
            val line = raw.trim()
            // 여러 줄짜리 틀({{Infobox … }}, {{History table … }} 등)은 건너뛴다
            if (skipDepth > 0) {
                skipDepth += line.count { it == '{' } / 2 - line.count { it == '}' } / 2
                if (skipDepth < 0) skipDepth = 0
                continue
            }
            if (line.startsWith("{{") && !line.startsWith("{|")) {
                val depth = Regex("""\{\{""").findAll(line).count() - Regex("""}}""").findAll(line).count()
                if (depth > 0) { flush(); skipDepth = depth; continue }
            }
            // 문장 중간에서 시작해 다음 줄로 이어지는 틀: 앞부분 글만 쓰고 틀 안(|A1 = … 같은 줄)은 건너뛴다
            if (!line.startsWith("{|") && !line.startsWith("|") && !line.startsWith("!")) {
                val open = unclosedTemplateStart(line)
                if (open > 0) {
                    paragraph.append(line.substring(0, open)).append(' ')
                    flush()
                    skipDepth = Regex("""\{\{""").findAll(line.substring(open)).count() - Regex("""}}""").findAll(line.substring(open)).count()
                    continue
                }
            }
            when {
                line.isEmpty() -> flush()
                line.startsWith("{{Update Link") || line.startsWith("{{Tabview") || line.startsWith("__") -> Unit
                line.startsWith("==") -> {
                    flush()
                    val level = line.takeWhile { it == '=' }.length
                    val (text, links) = inlineLinks(line.trim('=').trim().removePrefix("►").trim())
                    if (text.isNotBlank()) out += DocBlock.Heading(text, (level - 1).coerceIn(1, 3), links)
                }
                line.startsWith("*") || line.startsWith("#") -> {
                    flush()
                    val depth = line.takeWhile { it == '*' || it == '#' }.length
                    val (text, links) = inlineLinks(line.drop(depth))
                    if (text.isNotBlank()) out += DocBlock.Bullet(text, depth, links)
                }
                line.startsWith("[[File:") -> {
                    flush()
                    file.find(line)?.groupValues?.get(1)?.let { name ->
                        out += DocBlock.Image("${WikiApi.BASE}/w/Special:FilePath/" + name.trim().replace(' ', '_'))
                    }
                }
                line.startsWith("{|") || line.startsWith("|}") || line.startsWith("|-") -> {
                    // 표의 한 줄 = "칸 · 칸 · 칸"
                    flushRow()
                }
                line.startsWith("|") || line.startsWith("!") -> {
                    flush()
                    line.drop(1).split("||", "!!").forEach { cell ->
                        // 칸 앞의 꾸밈(rowspan="2" | …)은 뺀다
                        val bar = cell.indexOf('|')
                        val body = if (bar > 0 && cell.substring(0, bar).contains('=') && !cell.substring(0, bar).contains("[[") &&
                            !cell.substring(0, bar).contains("{{")) cell.substring(bar + 1) else cell
                        if (inline(body).isNotBlank()) row += body.trim()
                    }
                }
                else -> paragraph.append(line).append(' ')
            }
        }
        flushRow()
        flush()
        return out
    }
}
