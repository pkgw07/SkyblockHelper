package sbhelper.core.calc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import sbhelper.core.model.SkyblockItem

/** 내 박물관 (공식 museum API) */
data class MuseumState(
    /** 기증한 칸: 아이템 ID 또는 갑옷 세트 ID */
    val donated: Set<String> = emptySet(),
    val special: Int = 0,
    val value: Double? = null,
)

/** 박물관 한 칸 (아이템 하나 또는 갑옷 세트 하나) */
data class MuseumSlot(
    val key: String,
    val category: String,
    val xp: Int,
    val stage: String?,
    /** 세트면 조각들, 아니면 자기 자신 */
    val pieces: List<String>,
    val isSet: Boolean,
    val donated: Boolean,
    /** 상위 아이템 기증으로 인정됨 */
    val viaParent: String?,
)

/**
 * 박물관 계산 (공식 아이템 API 의 museum_data 기준).
 * - 상위 아이템을 기증하면 하위 아이템도 기증한 것 (parent)
 * - ✪ 같은 변형은 기본 아이템과 같은 칸 (mapped_item_ids)
 * - 갑옷은 세트 단위
 */
object MuseumCalc {
    val CATEGORY_KO = linkedMapOf(
        "COMBAT" to "전투", "DUNGEONEERING" to "던전", "FARMING" to "농사", "FISHING" to "낚시",
        "MINING" to "채광", "FORAGING" to "벌목", "HUNTING" to "사냥",
    )
    val STAGES = listOf("STARTER", "AMATEUR", "INTERMEDIATE", "SKILLED", "EXPERT", "PROFESSIONAL", "MASTER")
    val STAGE_KO = mapOf("STARTER" to "시작", "AMATEUR" to "초보", "INTERMEDIATE" to "중급", "SKILLED" to "숙련",
        "EXPERT" to "전문", "PROFESSIONAL" to "프로", "MASTER" to "마스터")

    /** 공식 museum API 응답 → 내 기증 목록 */
    fun parse(root: JsonObject?, uuid: String): MuseumState? {
        val me = (root?.get("members") as? JsonObject)?.get(uuid) as? JsonObject ?: return null
        val items = (me["items"] as? JsonObject)?.keys.orEmpty()
        val special = (me["special"] as? JsonArray)?.size ?: 0
        val value = (me["value"] as? JsonPrimitive)?.doubleOrNull
        return MuseumState(items, special, value)
    }

    fun slots(items: Map<String, SkyblockItem>, museum: MuseumState?): List<MuseumSlot> {
        val donated = museum?.donated.orEmpty()
        // 상위 아이템 연결: 이 아이템 → 상위
        val parentOf = HashMap<String, String>()
        items.values.forEach { it.museum?.parent?.forEach { (child, parent) -> parentOf[child] = parent } }
        fun coveredBy(id: String): String? {
            var cur = parentOf[id]
            var guard = 0
            while (cur != null && guard++ < 10) {
                if (cur in donated) return cur
                cur = parentOf[cur]
            }
            return null
        }
        val out = mutableListOf<MuseumSlot>()
        val sets = LinkedHashMap<String, MutableList<SkyblockItem>>()
        items.values.forEach { item ->
            val m = item.museum ?: return@forEach
            if (m.armorSet.isNotEmpty()) m.armorSet.keys.forEach { sets.getOrPut(it) { mutableListOf() } += item }
            else if (m.donationXp != null) {
                val direct = item.id in donated || m.mapped.any { it in donated }
                val via = if (direct) null else coveredBy(item.id)
                out += MuseumSlot(item.id, m.category ?: "COMBAT", m.donationXp, m.gameStage, listOf(item.id), false, direct || via != null, via)
            }
        }
        sets.forEach { (setId, pieces) ->
            val first = pieces.first().museum!!
            val stage = pieces.mapNotNull { it.museum?.gameStage }.minByOrNull { STAGES.indexOf(it).let { i -> if (i < 0) 99 else i } }
            val direct = setId in donated
            val via = if (direct) null else coveredBy(setId)
            out += MuseumSlot(setId, first.category ?: "COMBAT", first.armorSet[setId] ?: 0, stage, pieces.map { it.id }, true, direct || via != null, via)
        }
        return out
    }

    /** 테크트리 ✔ 표시용: 박물관에 있는(또는 상위 기증으로 인정된) 아이템 ID 들 */
    fun ownedIds(items: Map<String, SkyblockItem>, museum: MuseumState?): Set<String> {
        if (museum == null) return emptySet()
        return slots(items, museum).filter { it.donated }.flatMap { it.pieces }.toSet()
    }
}
