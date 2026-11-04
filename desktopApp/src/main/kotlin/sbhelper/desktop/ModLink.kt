package sbhelper.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import sbhelper.core.SkyblockRepository
import sbhelper.core.api.SbJson
import sbhelper.core.calc.CraftPlanner
import sbhelper.core.calc.CraftStock
import sbhelper.ui.LiveGame
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 게임 모드(Minecraft 26.2)와의 연결. 이 컴퓨터 안(127.0.0.1)에서만 받는다.
 *  - 프로그램 → 모드: 시세, 제작 플래너 진행도, 타이머 (모드가 게임 화면에 그림)
 *  - 모드 → 프로그램: 스카이블록 안·지역, 실시간 인벤토리, 열어 본 상자 (공식 API 의 1~3분 지연 보완)
 */
object ModLink {
    const val PORT = 47321
    private val _state = MutableStateFlow(LiveGame())
    val state: StateFlow<LiveGame> = _state

    @Volatile private var client: Socket? = null
    private var inventory: Map<String, Long> = emptyMap()
    /** 상자 제목 → 내용 (엔더 상자·백팩·개인 금고만: 상점 화면 같은 건 세지 않음) */
    private val containers = HashMap<String, Map<String, Long>>()
    private val STORAGE = Regex("Ender Chest|Backpack|Personal Vault", RegexOption.IGNORE_CASE)

    fun start(scope: CoroutineScope, repo: SkyblockRepository) {
        Thread({
            val server = runCatching { ServerSocket(PORT, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return@Thread
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching { client?.close() }
                client = s
                Thread({ read(s) }, "sbh-modlink-read").apply { isDaemon = true; start() }
            }
        }, "sbh-modlink").apply { isDaemon = true; start() }

        // 보내기: 연결돼 있을 때만. 바뀐 것만 다시 보낸다
        scope.launch {
            var lastPrices = ""; var lastPlan = ""; var lastTimers = ""; var lastPriceAt = 0L; var lastClient: Socket? = null
            while (isActive) {
                val c = client
                if (c != null && !c.isClosed) {
                    if (c !== lastClient) { lastClient = c; lastPrices = ""; lastPlan = ""; lastTimers = "" }
                    val now = System.currentTimeMillis()
                    if (now - lastPriceAt > 60_000 || lastPrices.isEmpty()) {
                        val p = pricesJson(repo).toString()
                        if (p != lastPrices) { send(c, p); lastPrices = p }
                        lastPriceAt = now
                    }
                    val plan = planJson(repo).toString()
                    if (plan != lastPlan) { send(c, plan); lastPlan = plan }
                    val timers = timersJson(repo).toString()
                    if (timers != lastTimers) { send(c, timers); lastTimers = timers }
                }
                delay(2000)
            }
        }
    }

    private fun read(s: Socket) {
        _state.update { it.copy(connected = true) }
        runCatching {
            BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).forEachLine { line ->
                runCatching { handle(SbJson.parseToJsonElement(line).jsonObject) }
            }
        }
        if (client === s) {
            client = null
            containers.clear(); inventory = emptyMap()
            _state.value = LiveGame()
        }
    }

    private fun handle(m: JsonObject) {
        when (m["t"]?.jsonPrimitive?.content) {
            "loc" -> _state.update {
                it.copy(inSkyblock = m["sb"]?.jsonPrimitive?.content == "true", area = m["area"]?.jsonPrimitive?.content.orEmpty(),
                    purse = m["purse"]?.jsonPrimitive?.content.orEmpty())
            }
            "inv" -> { inventory = counts(m); publishStock() }
            "container" -> {
                val title = m["title"]?.jsonPrimitive?.content.orEmpty()
                if (STORAGE.containsMatchIn(title)) { containers[title] = counts(m); publishStock() }
            }
        }
    }

    private fun counts(m: JsonObject): Map<String, Long> =
        (m["items"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.toLongOrNull()?.let { k to it } }?.toMap().orEmpty()

    private fun publishStock() {
        val all = HashMap<String, Long>()
        (listOf(inventory) + containers.values).forEach { m -> m.forEach { (k, v) -> all[k] = (all[k] ?: 0L) + v } }
        _state.update { it.copy(stock = all) }
    }

    private fun send(s: Socket, text: String) {
        runCatching { val o: OutputStream = s.getOutputStream(); o.write((text + "\n").toByteArray(Charsets.UTF_8)); o.flush() }
    }

    /** 아이템 ID → [바자 즉시 판매, 바자 즉시 구매, 최저 BIN, NPC] */
    private fun pricesJson(repo: SkyblockRepository) = buildJsonObject {
        val st = repo.state.value
        val ids = (st.bazaar?.products?.keys.orEmpty() + st.lowestBin.keys + st.npcPrices.keys).toSet()
        put("t", "prices")
        putJsonObject("data") {
            ids.forEach { id ->
                val q = st.bazaar?.products?.get(id)?.quickStatus
                put(id, buildJsonArray {
                    add(JsonPrimitive(q?.sellPrice ?: -1.0)); add(JsonPrimitive(q?.buyPrice ?: -1.0))
                    add(JsonPrimitive(st.lowestBin[id] ?: -1.0)); add(JsonPrimitive(st.npcPrices[id] ?: -1.0))
                })
            }
        }
    }

    /** 제작 플래너 진행도 (앱과 같은 계산: 자루 + 인벤토리(게임 실시간 우선), 끈 재료 제외) */
    private fun planJson(repo: SkyblockRepository) = buildJsonObject {
        put("t", "plan")
        val st = repo.state.value
        val t = st.synced.craftCurrent ?: return@buildJsonObject
        val d = st.device
        val p = st.selectedProfile
        val live = _state.value.stock
        val inv = if (!d.plannerUseInventory) emptyMap() else live ?: p?.inventories?.let { CraftStock.inventory(it) }.orEmpty()
        val stock = CraftStock.merge(if (d.plannerUseSacks) p?.sacks.orEmpty() else emptyMap(), inv)
        val skip = st.synced.craftExcluded[t.itemId].orEmpty()
        val planner = CraftPlanner(st.gameData)
        val raw = planner.rawProgress(t.itemId, t.amount, stock, skip)
        val missing = raw.filter { it.missing > 0 }
        val pct = if (missing.isEmpty()) 1.0 else planner.progress(t.itemId, t.amount, stock, skip) { k ->
            st.bazaar?.products?.get(k)?.quickStatus?.buyPrice?.takeIf { it > 0 } ?: st.npcPrices[k]
        }
        val rows = missing.take(d.overlayRows.coerceIn(1, 20))
        put("title", st.itemName(t.itemId).replace(Regex("§."), "") + if (t.amount > 1) " ×${t.amount}" else "")
        put("pct", pct)
        put("more", missing.size - rows.size)
        putJsonArray("rows") {
            rows.forEach { r ->
                add(buildJsonObject {
                    put("id", r.itemId); put("name", st.itemName(r.itemId).replace(Regex("§."), ""))
                    put("have", r.have); put("need", kotlin.math.ceil(r.needed))
                })
            }
        }
    }

    /** 다가오는 타이머: 포지 + 알림 타이머 (가까운 순) */
    private fun timersJson(repo: SkyblockRepository) = buildJsonObject {
        put("t", "timers")
        val st = repo.state.value
        val now = System.currentTimeMillis()
        val list = buildList {
            st.forgeStatuses().forEach { f -> f.endTime?.let { add("포지 #${f.process.slot} ${f.itemName}" to it) } }
            st.extraTimers(now).filter { !it.done && it.at > now }.forEach { add("${it.emoji} ${it.title}" to it.at) }
        }.sortedBy { it.second }.take(8)
        putJsonArray("list") { list.forEach { (label, at) -> add(buildJsonObject { put("label", label); put("at", at) }) } }
    }
}
