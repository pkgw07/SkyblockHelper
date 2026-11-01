package sbhelper.core

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test

/**
 * 진단용 (LIVE_PROFILE=1 일 때만): 실제 프로필의 "항목 이름 구조"만 파일로 남긴다.
 * 값(코인·아이템·레벨), UUID·프로필 ID, 닉네임은 남기지 않는다.
 */
class ProfileProbe {
    private val uuidLike = Regex("^[0-9a-fA-F]{32}$|^[0-9a-fA-F-]{36}$")

    /** 한 줄에 한 항목: 경로 = 종류 */
    private fun tree(e: JsonElement?, path: String, out: MutableList<String>, depth: Int = 0) {
        when {
            depth > 9 -> out += "$path = …"
            e is JsonObject -> {
                if (e.isEmpty()) { out += "$path = {}"; return }
                // 키가 아주 많은 맵(컬렉션 아이템 등)은 키 목록만
                if (e.size > 60 && e.values.all { it is JsonPrimitive }) {
                    out += "$path = {${e.size}개: ${e.keys.map { redact(it) }.take(400).joinToString(",")}}"
                    return
                }
                e.forEach { (k, v) -> tree(v, "$path.${redact(k)}", out, depth + 1) }
            }
            e is JsonArray -> {
                out += "$path = [${e.size}]"
                e.firstOrNull()?.let { if (it !is JsonPrimitive) tree(it, "$path[0]", out, depth + 1) }
            }
            e is JsonPrimitive && e.isString -> out += "$path = str" + if (e.content.length > 60) "(${e.content.length})" else ""
            e is JsonPrimitive -> out += "$path = " + when (e.content) { "true", "false" -> "bool"; "null" -> "null"; else -> "num" }
            else -> out += "$path = null"
        }
    }

    private fun redact(k: String) = if (uuidLike.matches(k)) "<uuid>" else k

    /** 아이템 데이터(base64 NBT)가 들어 있는 모든 위치와 아이템 개수 */
    private fun itemSpots(e: JsonElement?, path: String, out: MutableList<String>, depth: Int = 0) {
        if (depth > 8 || e == null) return
        when (e) {
            is JsonObject -> e.forEach { (k, v) ->
                val p = "$path.${redact(k)}"
                if (v is JsonPrimitive && v.isString && v.content.length > 60 && v.content.startsWith("H4sI")) {
                    val items = sbhelper.core.nbt.ItemParser.parse(v.content)
                    out += "$p : 칸 ${items.size}, 아이템 ${items.count { it != null }}"
                } else itemSpots(v, p, out, depth + 1)
            }
            is JsonArray -> e.forEachIndexed { i, v -> if (i < 3) itemSpots(v, "$path[$i]", out, depth + 1) }
            else -> Unit
        }
    }

    /** 이름에 wardrobe 가 들어간 모든 항목 */
    private fun wardrobeKeys(e: JsonElement?, path: String, out: MutableList<String>, depth: Int = 0) {
        if (depth > 9 || e !is JsonObject) return
        e.forEach { (k, v) ->
            val p = "$path.${redact(k)}"
            if (k.contains("wardrobe", true)) out += "$p = " + when (v) { is JsonObject -> "{${v.keys.joinToString(",")}}"; is JsonArray -> "[${v.size}]"; else -> "값" }
            wardrobeKeys(v, p, out, depth + 1)
        }
    }

    @Test fun probe() = kotlinx.coroutines.runBlocking {
        if (System.getenv("LIVE_PROFILE") != "1") return@runBlocking
        val key = System.getenv("HYPIXEL_API_KEY").orEmpty()
        val player = System.getenv("PLAYER").orEmpty()
        val dir = File(System.getenv("PROBE_OUT") ?: "probe").apply { mkdirs() }
        val report = StringBuilder()
        if (key.isBlank() || player.isBlank()) { File(dir, "report.txt").writeText("키 또는 닉네임 없음"); return@runBlocking }
        val http = sbhelper.core.api.defaultHttpClient()
        val api = sbhelper.core.api.HypixelApi(http) { key }
        val uuid = api.uuidFor(player).id
        suspend fun get(path: String): JsonObject =
            sbhelper.core.api.SbJson.parseToJsonElement(http.get("https://api.hypixel.net$path") { header("API-Key", key) }.bodyAsText()).jsonObject
        val root = get("/v2/skyblock/profiles?uuid=$uuid")
        val profiles = (root["profiles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        report.appendLine("success=${root["success"]} 프로필 ${profiles.size}개")
        profiles.forEachIndexed { i, p ->
            val selected = (p["selected"] as? JsonPrimitive)?.content == "true"
            val members = p["members"] as? JsonObject
            val me = members?.get(uuid) as? JsonObject
            val lines = mutableListOf<String>()
            // 내 멤버 데이터 + 프로필 공통(은행 등). 다른 코옵 멤버 데이터는 넣지 않는다
            tree(JsonObject(p.filterKeys { it != "members" }), "profile", lines)
            tree(me, "member", lines)
            File(dir, "profile-$i${if (selected) "-selected" else ""}.txt").writeText(lines.joinToString("\n"))
            val spots = mutableListOf<String>(); itemSpots(me, "member", spots); itemSpots(JsonObject(p.filterKeys { it != "members" }), "profile", spots)
            val wk = mutableListOf<String>(); wardrobeKeys(p.mapKeys { it.key }.let { JsonObject(it) }, "profile", wk)
            report.appendLine("  아이템 위치(#$i): " + spots.joinToString(" | "))
            report.appendLine("  wardrobe 항목(#$i): " + wk.joinToString(" | ").ifEmpty { "없음" })
            // loadout 안의 숫자가 아닌 항목 이름 (지금 입은 세트 위치 찾기)
            val lo = me?.get("loadout") as? JsonObject
            report.appendLine("  loadout 항목(#$i): " + (lo?.entries?.joinToString(" | ") { (k, v) ->
                k + ((v as? JsonObject)?.keys?.filter { it.toIntOrNull() == null }?.let { if (it.isEmpty()) "" else "{" + it.joinToString(",") + "}" } ?: "=" + (if (v is JsonPrimitive) "값" else "?"))
            } ?: "없음") + " · inventory.wardrobe_equipped_slot=" + ((me?.get("inventory") as? JsonObject)?.containsKey("wardrobe_equipped_slot") ?: false))
            report.appendLine("프로필 #$i selected=$selected 멤버 ${members?.size} 내 데이터 ${lines.size}줄 game_mode=${(p["game_mode"] as? JsonPrimitive)?.content ?: "normal"}")
            // 앱 파서가 찾은 것 (값 대신 개수·있음/없음)
            val parsed = sbhelper.core.model.ProfileParser.parse(p, uuid)
            if (parsed != null) report.appendLine(
                "  파서: hotmXp=${parsed.hotmXp != null} hotmNodes=${parsed.hotmNodes.size} quickForge>0=${parsed.quickForgeLevel > 0} " +
                    "hotfXp=${parsed.hotfXp != null} hotfNodes=${parsed.hotfNodes.size} timeTower=${parsed.timeTowerCharges != null} " +
                    "forge=${parsed.forge.size} sacks=${parsed.sacks?.size} collections=${parsed.collections?.size} inventories=${parsed.inventories.keys.sorted()} " +
                    "bestiary=${parsed.bestiaryKills.size} minions=${parsed.craftedMinions.size} pets=${parsed.pets.size} powders=${parsed.powders.keys} " +
                    "skills=${parsed.skillXp?.keys?.sorted()} " +
                    "wardrobe=${parsed.wardrobe?.let { w -> "세트 ${w.armor.size}개, 아이템 ${w.armor.values.sumOf { l -> l.count { d -> sbhelper.core.nbt.ItemParser.parse(d).any { it != null } } }}개, 장비 ${w.equipment.size}, 로드아웃 ${w.loadouts.size}, 착용 ${w.equippedArmor != null}" } ?: "없음"} " +
                    "slayerBosses=${parsed.slayerBossKills.size} floors=${parsed.floorCompletions.size}")
            val pid = (p["profile_id"] as? JsonPrimitive)?.content ?: return@forEachIndexed
            if (selected) {
                runCatching {
                    val museum = get("/v2/skyblock/museum?profile=$pid")
                    val m = (museum["members"] as? JsonObject)?.get(uuid)
                    val ml = mutableListOf<String>(); tree(m, "museum.member", ml)
                    File(dir, "museum.txt").writeText("success=${museum["success"]}\n" + ml.joinToString("\n"))
                }.onFailure { report.appendLine("museum 실패: ${it.message}") }
                runCatching {
                    val garden = get("/v2/skyblock/garden?profile=$pid")
                    val gl = mutableListOf<String>(); tree(garden["garden"], "garden", gl)
                    File(dir, "garden.txt").writeText("success=${garden["success"]}\n" + gl.joinToString("\n"))
                }.onFailure { report.appendLine("garden 실패: ${it.message}") }
            }
        }
        File(dir, "report.txt").writeText(report.toString())
    }
}
