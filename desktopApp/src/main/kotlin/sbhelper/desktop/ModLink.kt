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
import sbhelper.core.data.CraftTarget
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
 *  - 모드 → 프로그램: 게임 안 제작 플래너에서 바꾼 목표 · 수량 · 끈 재료 (앱 화면과 같은 설정을 바꾼다)
 */
object ModLink {
    const val PORT = 47321
    private val _state = MutableStateFlow(LiveGame())
    val state: StateFlow<LiveGame> = _state

    @Volatile private var client: Socket? = null
    /** 상자 키(inv, ender:3, backpack:2, vault) → 내용. 상점 화면 같은 건 세지 않음 */
    private val containers = HashMap<String, Map<String, Long>>()

    @Volatile private var repoRef: SkyblockRepository? = null

    fun start(scope: CoroutineScope, repo: SkyblockRepository) {
        repoRef = repo
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
            var lastPrices = ""; var lastPlan = ""; var lastTimers = ""; var lastPriceAt = 0L; var lastClient: Socket? = null; var lastCraftCount = -1
            while (isActive) {
                val c = client
                if (c != null && !c.isClosed) {
                    if (c !== lastClient) {
                        lastClient = c; lastPrices = ""; lastPlan = ""; lastTimers = ""; lastCraftCount = -1
                    }
                    // 목표로 고를 수 있는 아이템 목록 (게임 데이터를 불러오면 다시)
                    val gd = repo.state.value.gameData
                    val craftCount = gd.crafting.size + gd.forge.size
                    if (craftCount != lastCraftCount) { send(c, craftablesJson(repo).toString()); lastCraftCount = craftCount }
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
            containers.clear()
            _state.value = LiveGame()
        }
    }

    /** 모드가 알려 준 mods 폴더 저장 (자동 업데이트용) */
    @Volatile var onModsDir: (String) -> Unit = {}

    private fun handle(m: JsonObject) {
        when (m["t"]?.jsonPrimitive?.content) {
            "hello" -> {
                _state.update { it.copy(modVersion = m["mod"]?.jsonPrimitive?.content.orEmpty()) }
                m["modsDir"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { onModsDir(it) }
            }
            "loc" -> _state.update {
                it.copy(inSkyblock = m["sb"]?.jsonPrimitive?.content == "true", area = m["area"]?.jsonPrimitive?.content.orEmpty(),
                    purse = m["purse"]?.jsonPrimitive?.content.orEmpty())
            }
            "inv" -> { containers["inv"] = counts(m); publish() }
            "plan.select", "plan.add", "plan.remove", "plan.amount", "plan.exclude", "plan.option" -> repoRef?.let { planCommand(it, m) }
            "container" -> {
                val key = CraftStock.liveKey(m["title"]?.jsonPrimitive?.content.orEmpty())
                if (key != null) { containers[key] = counts(m); publish() }
            }
        }
    }

    /** 게임 안 제작 플래너에서 온 변경: 앱 화면과 같은 설정을 바꾼다 */
    private fun planCommand(repo: SkyblockRepository, m: JsonObject) {
        val id = m["id"]?.jsonPrimitive?.content.orEmpty()
        val amount = m["amount"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 100_000) ?: 1
        when (m["t"]?.jsonPrimitive?.content) {
            "plan.select" -> repo.updateSynced { s -> s.craftTargets.firstOrNull { it.itemId == id }?.let { s.copy(craftCurrent = it) } ?: s }
            "plan.add" -> if (id.isNotBlank()) repo.updateSynced { s ->
                val t = CraftTarget(id, amount)
                s.copy(craftTargets = (listOf(t) + s.craftTargets.filterNot { it.itemId == id }).take(12), craftCurrent = t)
            }
            "plan.remove" -> repo.updateSynced { s ->
                val left = s.craftTargets.filterNot { it.itemId == id }
                s.copy(craftTargets = left, craftCurrent = if (s.craftCurrent?.itemId == id) left.firstOrNull() else s.craftCurrent)
            }
            "plan.amount" -> repo.updateSynced { s ->
                val t = CraftTarget(id, amount)
                s.copy(craftTargets = s.craftTargets.map { if (it.itemId == id) t else it },
                    craftCurrent = if (s.craftCurrent?.itemId == id) t else s.craftCurrent)
            }
            "plan.exclude" -> {
                val target = m["target"]?.jsonPrimitive?.content.orEmpty()
                val off = m["off"]?.jsonPrimitive?.content == "true"
                if (target.isNotBlank() && id.isNotBlank()) repo.updateSynced { s ->
                    val cur = s.craftExcluded[target].orEmpty()
                    val next = if (off) cur + id else cur - id
                    s.copy(craftExcluded = if (next.isEmpty()) s.craftExcluded - target else s.craftExcluded + (target to next))
                }
            }
            "plan.option" -> repo.updateDevice { d ->
                d.copy(plannerUseSacks = m["sacks"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: d.plannerUseSacks,
                    plannerUseInventory = m["inventory"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: d.plannerUseInventory)
            }
        }
    }

    /** 게임 안에서 목표로 고를 수 있는 아이템 (제작 · 포지 레시피가 있는 것): [ID, 이름] */
    private fun craftablesJson(repo: SkyblockRepository) = buildJsonObject {
        put("t", "craftables")
        val st = repo.state.value
        putJsonArray("list") {
            (st.gameData.crafting.keys + st.gameData.forge.keys).toSortedSet().forEach { id ->
                add(buildJsonArray { add(JsonPrimitive(id)); add(JsonPrimitive(st.itemName(id).replace(Regex("§."), ""))) })
            }
        }
    }

    private fun counts(m: JsonObject): Map<String, Long> =
        (m["items"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.toLongOrNull()?.let { k to it } }?.toMap().orEmpty()

    private fun publish() { _state.update { it.copy(containers = HashMap(containers)) } }

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
        val d = st.device
        // 게임 안 플래너 화면용: 목표 목록 · 옵션
        putJsonArray("targets") {
            st.synced.craftTargets.forEach { t ->
                add(buildJsonObject { put("id", t.itemId); put("name", st.itemName(t.itemId).replace(Regex("§."), "")); put("amount", t.amount) })
            }
        }
        put("sacks", d.plannerUseSacks)
        put("inventory", d.plannerUseInventory)
        val t = st.synced.craftCurrent ?: return@buildJsonObject
        put("current", t.itemId)
        put("amount", t.amount)
        val p = st.selectedProfile
        // 상자마다: 게임에서 이번에 본 건 실시간, 아닌 건 공식 API
        val inv = if (!d.plannerUseInventory) emptyMap()
            else CraftStock.combine(p?.inventories?.let { CraftStock.byContainer(it) }.orEmpty(), _state.value.containers)
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
        // 모든 기본 재료 (끈 재료 포함, 게임 안에서 켜고 끄기용)
        val all = if (skip.isEmpty()) raw else planner.rawProgress(t.itemId, t.amount, stock, emptySet())
        putJsonArray("all") {
            val ids = all.map { it.itemId }.toMutableList()
            skip.filterNot { it in ids }.forEach { ids += it }
            val byId = raw.associateBy { it.itemId }
            ids.forEach { id ->
                val r = byId[id] ?: all.firstOrNull { it.itemId == id }
                add(buildJsonObject {
                    put("id", id); put("name", st.itemName(id).replace(Regex("§."), ""))
                    put("have", r?.have ?: 0.0); put("need", kotlin.math.ceil(r?.needed ?: 0.0)); put("off", id in skip)
                })
            }
        }
    }

    /**
     * 타이머: 포지(칸마다) + 컴포스터·Time Tower 같은 알림 타이머. 다 된 것 먼저, 나머지는 가까운 순.
     * kind 로 종류를 알려 주면 모드에서 종류별로 켜고 끈다. 마인크래프트 글꼴에 없는 이모지는 빼고 보낸다.
     */
    private fun timersJson(repo: SkyblockRepository) = buildJsonObject {
        put("t", "timers")
        val st = repo.state.value
        val now = System.currentTimeMillis()
        data class T(val kind: String, val label: String, val at: Long, val done: Boolean, val start: Long?)
        val list = buildList {
            st.forgeStatuses().forEach { f ->
                f.endTime?.let { end ->
                    add(T("forge", "포지 #${f.process.slot} ${f.itemName.replace(Regex("§."), "")}", end, now >= end, f.process.startTime.takeIf { it in 1 until end }))
                }
            }
            st.extraTimers(now).forEach { add(T(it.key, it.title, it.at, it.done, null)) }
        }.sortedWith(compareBy<T>({ !it.done }, { it.at }))
        putJsonArray("list") {
            list.forEach { t ->
                add(buildJsonObject {
                    put("kind", t.kind); put("label", t.label); put("at", t.at); put("done", t.done)
                    t.start?.let { put("start", it) }
                })
            }
        }
    }
}
