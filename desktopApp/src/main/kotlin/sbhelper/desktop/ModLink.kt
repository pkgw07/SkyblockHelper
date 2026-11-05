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
    /** 상자 키 → 게임에서 본 시각. 게임을 꺼도 남겨 두고, 공식 API 가 더 새로워지면 API 를 쓴다 */
    private val containersAt = HashMap<String, Long>()

    @Volatile private var repoRef: SkyblockRepository? = null
    @Volatile private var scopeRef: CoroutineScope? = null

    fun start(scope: CoroutineScope, repo: SkyblockRepository) {
        repoRef = repo
        scopeRef = scope
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
                publish()
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
            // 게임이 꺼져도 마지막으로 본 상자 내용은 남긴다 (API 가 따라잡으면 publish 에서 빠짐)
            _state.update { it.copy(connected = false, inSkyblock = false, area = "", purse = "") }
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
            "inv" -> { containers["inv"] = counts(m); containersAt["inv"] = System.currentTimeMillis(); publish() }
            "timer.set" -> repoRef?.let { gameTimer(it, m) }
            "ah.search" -> repoRef?.let { auctionSearch(it, m) }
            "player.status" -> repoRef?.let { playerStatus(it, m) }
            "plan.select", "plan.add", "plan.remove", "plan.amount", "plan.exclude", "plan.option" -> repoRef?.let { planCommand(it, m) }
            "container" -> {
                val key = CraftStock.liveKey(m["title"]?.jsonPrimitive?.content.orEmpty())
                if (key != null) { containers[key] = counts(m); containersAt[key] = System.currentTimeMillis(); publish() }
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

    /** 게임 안 경매장 검색 창: 마지막으로 받은 경매장 전체에서 이름으로 찾기 (싼 순) */
    private fun auctionSearch(repo: SkyblockRepository, m: JsonObject) {
        val q = m["q"]?.jsonPrimitive?.content.orEmpty().trim().lowercase()
        val binOnly = m["bin"]?.jsonPrimitive?.content != "false"
        val all = repo.auctionsCache
        val now = System.currentTimeMillis()
        if (all.isEmpty()) scopeRef?.launch { runCatching { repo.refreshLowestBins(force = true) } }
        val found = if (q.length < 2) emptyList() else all.asSequence()
            .filter { it.end > now && (!binOnly || it.bin) && it.itemName.replace(Regex("§."), "").lowercase().contains(q) }
            .sortedBy { if (it.bin) it.startingBid else maxOf(it.startingBid, it.highestBidAmount) }
            .take(80).toList()
        val c = client ?: return
        send(c, buildJsonObject {
            put("t", "ah.results")
            put("q", q)
            put("loading", all.isEmpty())
            put("updated", repo.state.value.lowestBinAt)
            putJsonArray("list") {
                found.forEach { a ->
                    add(buildJsonObject {
                        put("uuid", a.uuid); put("name", a.itemName.replace(Regex("§."), "")); put("tier", a.tier)
                        put("price", if (a.bin) a.startingBid else maxOf(a.startingBid, a.highestBidAmount)); put("bin", a.bin); put("end", a.end)
                    })
                }
            }
        }.toString())
    }

    /** Player Stalking: 닉네임 → 접속 중인지 · 어느 게임인지 (공식 status API, 내 API 키) */
    private fun playerStatus(repo: SkyblockRepository, m: JsonObject) {
        val name = m["name"]?.jsonPrimitive?.content.orEmpty().trim()
        if (name.isEmpty()) return
        scopeRef?.launch {
            val out = runCatching {
                val profile = repo.api.uuidFor(name)
                val session = repo.api.status(profile.id)
                buildJsonObject {
                    put("t", "player.status"); put("name", profile.name)
                    put("online", session["online"]?.jsonPrimitive?.content == "true")
                    put("gameType", session["gameType"]?.jsonPrimitive?.content.orEmpty())
                    put("mode", session["mode"]?.jsonPrimitive?.content.orEmpty())
                    put("map", session["map"]?.jsonPrimitive?.content.orEmpty())
                }
            }.getOrElse { e -> buildJsonObject { put("t", "player.status"); put("name", name); put("error", e.message ?: "확인하지 못했어요") } }
            client?.let { send(it, out.toString()) }
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

    /**
     * 게임에서 본 상자 내용 중 아직 공식 API 보다 새로운 것만 내보낸다.
     * API 는 게임보다 1~3분 늦으니, 프로필을 받은 시각 3분 전보다 나중에 본 것은 게임 쪽이 더 새롭다고 본다.
     */
    private fun publish() {
        val sync = repoRef?.state?.value?.lastProfileSync ?: 0L
        val fresh = containers.filterKeys { (containersAt[it] ?: 0L) > sync - 3 * 60_000L }
        _state.update { if (it.containers == fresh) it else it.copy(containers = HashMap(fresh)) }
    }

    /** 게임 모드가 게임 화면에서 읽은 타이머 (Kat · 펫 NPC · Heavy Pearl) → 동기화 설정에 저장 (게임을 꺼도 알림, 휴대폰에도) */
    private fun gameTimer(repo: SkyblockRepository, m: JsonObject) {
        val key = m["key"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return
        val at = m["at"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
        val t = sbhelper.core.data.GameTimer(
            kind = m["kind"]?.jsonPrimitive?.content ?: "kat",
            label = m["label"]?.jsonPrimitive?.content.orEmpty(),
            at = at,
            start = m["start"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            seenAt = System.currentTimeMillis(),
        )
        repo.updateSynced { s ->
            if (s.gameTimers[key]?.let { it.at == t.at && it.label == t.label } == true) return@updateSynced s
            // 끝난 지 이틀 지난 건 정리
            val keep = s.gameTimers.filterValues { System.currentTimeMillis() - it.at < 2 * 86_400_000L }
            s.copy(gameTimers = keep + (key to t))
        }
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
