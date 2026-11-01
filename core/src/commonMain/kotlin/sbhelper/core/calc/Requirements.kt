package sbhelper.core.calc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import sbhelper.core.AppState

/**
 * 조건 한 줄.
 * @param text 한국어 설명 (예: "Diamond 컬렉션 IV 단계")
 * @param page 누르면 열 위키 문서 (없으면 null)
 * @param met 내 프로필 기준 달성 여부 (알 수 없으면 null)
 * @param mine 내 현재 값 (예: "지금 III 단계")
 */
data class Requirement(val text: String, val page: String? = null, val met: Boolean? = null, val mine: String? = null)

/**
 * 제작 조건(위키 조합법 requirement)과 사용 조건(공식 아이템 API requirements)을 사람이 읽는 글로.
 * 달성 여부는 지금 고른 프로필로 판단한다.
 */
object Requirements {
    private val SLAYER_KEYS = mapOf(
        "zombie" to "zombie", "revenant" to "zombie", "spider" to "spider", "tarantula" to "spider", "wolf" to "wolf", "sven" to "wolf",
        "enderman" to "enderman", "voidgloom" to "enderman", "blaze" to "blaze", "inferno" to "blaze", "vampire" to "vampire", "riftstalker" to "vampire",
    )
    private val SKILL_NAMES = mapOf(
        "FARMING" to "농사", "MINING" to "채광", "COMBAT" to "전투", "FORAGING" to "벌목", "FISHING" to "낚시", "ENCHANTING" to "인챈트",
        "ALCHEMY" to "연금술", "TAMING" to "길들이기", "CARPENTRY" to "목공", "RUNECRAFTING" to "룬 제작", "SOCIAL" to "소셜", "HUNTING" to "사냥",
    )

    fun roman(n: Int): String {
        if (n <= 0) return "$n"
        val v = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var x = n; val sb = StringBuilder()
        for ((k, s) in v) while (x >= k) { sb.append(s); x -= k }
        return sb.toString()
    }

    private fun fromRoman(s: String): Int? {
        val m = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        if (s.isEmpty() || s.any { it !in m }) return null
        var total = 0
        for (i in s.indices) { val v = m[s[i]]!!; total += if (i + 1 < s.length && m[s[i + 1]]!! > v) -v else v }
        return total
    }

    // ---------------------------------------------------------------- 내 진행도
    private fun collectionTier(state: AppState, name: String): Int? {
        val res = state.collections ?: return null
        val amounts = state.selectedProfile?.collections ?: return null
        return CollectionCalc.progress(res, amounts).firstOrNull { it.name.equals(name, true) || it.itemId.equals(name, true) }?.tier
    }

    private fun slayerLevel(state: AppState, boss: String): Int? {
        val p = state.selectedProfile ?: return null
        val table = state.gameData.leveling.slayerXp[boss] ?: return null
        return Leveling.slayer(p.slayerXp[boss] ?: 0.0, table).level
    }

    private fun skillLevel(state: AppState, key: String): Int? {
        val p = state.selectedProfile ?: return null
        val def = state.skills?.skills?.get(key) ?: return null
        val xp = p.skillXp?.get(key) ?: return null
        return Leveling.skill(xp, def, p.skillCap(key, def.maxLevel)).level
    }

    private fun catacombsLevel(state: AppState): Int? {
        val xp = state.selectedProfile?.catacombsXp ?: return null
        val t = state.gameData.leveling.catacombs.takeIf { it.isNotEmpty() } ?: return null
        return Leveling.catacombs(xp, t).level
    }

    private fun hotmLevel(state: AppState): Int? {
        val xp = state.selectedProfile?.hotmXp ?: return null
        val t = state.gameData.leveling.hotm.takeIf { it.isNotEmpty() } ?: return null
        return Leveling.hotm(xp, t).level
    }

    private fun highestFloor(state: AppState, master: Boolean): Int? {
        val p = state.selectedProfile ?: return null
        val comp = if (master) p.masterFloorCompletions else p.floorCompletions
        return comp.filterValues { it > 0 }.keys.maxOrNull() ?: -1
    }

    // ---------------------------------------------------------------- 위키 조합법의 제작 조건
    /** 예: "[[Diamond#IV|Diamond IV (4)]]", "[[Zombie Slayer#V|Zombie Slayer V (5)]]" */
    fun recipe(state: AppState, wikitext: String): Requirement? {
        if (wikitext.isBlank()) return null
        val m = Regex("""\[\[([^\]#|]+)(?:#([^\]|]*))?(?:\|([^\]]*))?]]""").find(wikitext)
        val page = m?.groupValues?.get(1)?.trim()
        val shown = (m?.groupValues?.get(3)?.ifBlank { null } ?: page ?: wikitext).replace(Regex("<[^>]+>"), "").trim()
        val level = Regex("""\((\d+)\)""").find(shown)?.groupValues?.get(1)?.toIntOrNull()
            ?: m?.groupValues?.get(2)?.let(::fromRoman)
        val base = shown.replace(Regex("""\s*\(\d+\)\s*$"""), "").replace(Regex("""\s+[IVXLCM]+$"""), "").trim()
        if (page == null || level == null) return Requirement(shown, page)
        if (base.endsWith("Slayer", true)) {
            val boss = SLAYER_KEYS[base.removeSuffix("Slayer").trim().lowercase()]
            val mine = boss?.let { slayerLevel(state, it) }
            return Requirement("$base ${level}레벨", page, mine?.let { it >= level }, mine?.let { "지금 ${it}레벨" })
        }
        if (base.equals("Heart of the Mountain", true)) {
            val mine = hotmLevel(state)
            return Requirement("Heart of the Mountain ${roman(level)} 단계", page, mine?.let { it >= level }, mine?.let { "지금 ${roman(it)}" })
        }
        val mine = collectionTier(state, base)
        return Requirement("$base 컬렉션 ${roman(level)} 단계", page, mine?.let { it >= level }, mine?.let { "지금 ${if (it == 0) "0" else roman(it)} 단계" })
    }

    // ---------------------------------------------------------------- 공식 아이템 API 의 사용 조건
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.n(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull?.toInt()
    private fun nice(s: String) = s.replace('_', ' ').lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    fun item(state: AppState, r: JsonObject): Requirement = when (r.s("type")) {
        "SKILL" -> {
            val key = r.s("skill").orEmpty().uppercase(); val lv = r.n("level") ?: 0
            val mine = skillLevel(state, key)
            Requirement("${SKILL_NAMES[key] ?: nice(key)}(${nice(key)}) 스킬 ${lv}레벨", nice(key), mine?.let { it >= lv }, mine?.let { "지금 ${it}레벨" })
        }
        "SLAYER" -> {
            val boss = r.s("slayer_boss_type").orEmpty().lowercase(); val lv = r.n("level") ?: 0
            val mine = slayerLevel(state, boss)
            Requirement("${nice(boss)} Slayer ${lv}레벨", "${nice(boss)} Slayer", mine?.let { it >= lv }, mine?.let { "지금 ${it}레벨" })
        }
        "DUNGEON_SKILL" -> {
            val lv = r.n("level") ?: 0; val mine = catacombsLevel(state)
            Requirement("카타콤(Catacombs) ${lv}레벨", "Catacombs", mine?.let { it >= lv }, mine?.let { "지금 ${it}레벨" })
        }
        "DUNGEON_TIER" -> {
            val t = r.n("tier") ?: 0; val master = r.s("dungeon_type").orEmpty().contains("MASTER")
            val mine = highestFloor(state, master)
            val label = if (master) "M$t" else if (t == 0) "입구(Entrance)" else "F$t"
            Requirement("카타콤 $label 클리어", "Catacombs", mine?.let { it >= t }, mine?.let { if (it < 0) "아직 클리어 없음" else "최고 ${if (master) "M" else "F"}$it" })
        }
        "HEART_OF_THE_MOUNTAIN" -> {
            val t = r.n("tier") ?: 0; val mine = hotmLevel(state)
            Requirement("Heart of the Mountain ${roman(t)} 단계", "Heart of the Mountain", mine?.let { it >= t }, mine?.let { "지금 ${roman(it)}" })
        }
        "COLLECTION" -> {
            val c = r.s("collection").orEmpty(); val t = r.n("tier") ?: 0
            val name = state.items[c]?.name?.replace(Regex("§."), "") ?: nice(c)
            val mine = collectionTier(state, c) ?: collectionTier(state, name)
            Requirement("$name 컬렉션 ${roman(t)} 단계", name, mine?.let { it >= t }, mine?.let { "지금 ${if (it == 0) "0" else roman(it)} 단계" })
        }
        "GARDEN_LEVEL" -> Requirement("가든(Garden) ${r.n("level") ?: 0}레벨", "The Garden")
        "KUUDRA_COMPLETION" -> Requirement("Kuudra ${nice(r.s("tier").orEmpty())} 클리어", "Kuudra")
        "CRIMSON_ISLE_REPUTATION" -> Requirement("${nice(r.s("faction").orEmpty())} 평판 ${r.n("reputation") ?: 0}", "Crimson Isle")
        "TROPHY_FISHING" -> Requirement("트로피 피싱 ${nice(r.s("reward").orEmpty())} 보상", "Trophy Fishing")
        "TARGET_PRACTICE" -> Requirement("사격 연습(Target Practice) ${nice(r.s("mode").orEmpty())}", "Target Practice")
        "CHOCOLATE_FACTORY" -> Requirement("초콜릿 공장(Chocolate Factory) ${r.n("tier") ?: ""}", "Chocolate Factory")
        "PROFILE_AGE" -> Requirement("프로필 나이 ${r.n("minimum_age") ?: ""}${r.s("minimum_age_unit")?.let { " " + nice(it) } ?: ""}")
        else -> Requirement(r.entries.filter { it.key != "type" }.joinToString(", ") { (k, v) -> "$k ${(v as? JsonPrimitive)?.content ?: v}" }
            .let { (r.s("type")?.let(::nice) ?: "조건") + if (it.isNotBlank()) " ($it)" else "" })
    }
}
