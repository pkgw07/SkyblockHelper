package sbhelper.core.api

/**
 * 위키 아이템 문서의 {{ItemInfo}} (무기·도구·장비) 또는 {{SetInfo}}/{{SetInfoPiece}} (방어구)에서
 * 게임 툴팁처럼 보여 줄 줄들을 만든다. 색은 마인크래프트 색 코드(§).
 */
object WikiItemInfo {
    data class Tooltip(val lines: List<String>, val type: String?, val rarity: String?)

    private val RARITY = mapOf("c" to "COMMON", "u" to "UNCOMMON", "r" to "RARE", "e" to "EPIC", "l" to "LEGENDARY", "m" to "MYTHIC",
        "d" to "DIVINE", "s" to "SPECIAL", "vs" to "VERY SPECIAL")
    private val RARITY_COLOR = mapOf("COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6", "MYTHIC" to "§d",
        "DIVINE" to "§b", "SPECIAL" to "§c", "VERY SPECIAL" to "§c")
    /** 게임에서 빨간색으로 보이는 스탯 (나머지는 초록) */
    private val RED = setOf("damage", "strength", "crit_chance", "crit_damage", "attack_speed", "bonus_attack_speed", "ferocity", "ability_damage")
    private val PERCENT = setOf("crit_chance", "crit_damage", "attack_speed", "bonus_attack_speed", "ability_damage", "sea_creature_chance",
        "double_hook_chance", "trophy_chance", "treasure_chance")
    private val NAMES = mapOf("attack_speed" to "Bonus Attack Speed", "crit_chance" to "Crit Chance", "crit_damage" to "Crit Damage", "mining_speed" to "Mining Speed",
        "mining_fortune" to "Mining Fortune", "breaking_power" to "Breaking Power", "true_defense" to "True Defense", "magic_find" to "Magic Find",
        "pet_luck" to "Pet Luck", "sea_creature_chance" to "Sea Creature Chance", "fishing_speed" to "Fishing Speed", "health_regen" to "Health Regen",
        "farming_fortune" to "Farming Fortune", "foraging_fortune" to "Foraging Fortune", "cold_resistance" to "Cold Resistance", "ability_damage" to "Ability Damage")
    /** 스탯이 아닌 항목 */
    private val NOT_STAT = setOf("type", "name", "rarity", "hidden", "image", "lore", "id", "special_effect", "description", "shot_cooldown", "reforge", "enchant",
        "gemstone", "gemstones", "notes", "durability")

    private val COLOR_TEMPLATES = mapOf("green" to 'a', "red" to 'c', "yellow" to 'e', "gold" to '6', "aqua" to 'b', "gray" to '7', "grey" to '7',
        "dark gray" to '8', "darkgray" to '8', "blue" to '9', "purple" to '5', "pink" to 'd', "white" to 'f', "light purple" to 'd', "dark green" to '2',
        "dark aqua" to '3', "dark red" to '4', "dark purple" to '5', "dark blue" to '1')

    /** 틀 이름 다음부터 같은 깊이의 }} 까지 */
    private fun block(src: String, start: Int): String? {
        var d = 0; var i = start
        while (i < src.length - 1) {
            if (src[i] == '{' && src[i + 1] == '{') { d++; i += 2; continue }
            if (src[i] == '}' && src[i + 1] == '}') { d--; i += 2; if (d == 0) return src.substring(start, i); continue }
            i++
        }
        return null
    }

    /** 맨 위 단계의 |key = value 들 (안쪽 틀의 | 는 무시) */
    private fun params(block: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val body = block.removePrefix("{{").removeSuffix("}}")
        var d = 0; var sq = 0; val cur = StringBuilder(); val parts = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (i + 1 < body.length && c == '{' && body[i + 1] == '{') { d++; cur.append("{{"); i += 2; continue }
            if (i + 1 < body.length && c == '}' && body[i + 1] == '}') { d--; cur.append("}}"); i += 2; continue }
            if (i + 1 < body.length && c == '[' && body[i + 1] == '[') { sq++; cur.append("[["); i += 2; continue }
            if (i + 1 < body.length && c == ']' && body[i + 1] == ']') { sq--; cur.append("]]"); i += 2; continue }
            if (c == '|' && d == 0 && sq == 0) { parts += cur.toString(); cur.clear(); i++; continue }
            cur.append(c); i++
        }
        parts += cur.toString()
        parts.drop(1).forEach { p ->
            val eq = p.indexOf('=')
            if (eq > 0 && !p.substring(0, eq).contains("{{")) out[p.substring(0, eq).trim().lowercase()] = p.substring(eq + 1).trim()
        }
        return out
    }

    /** 위키 글 → 색 코드가 있는 한 줄들 (<br> 로 나뉨) */
    fun colored(text: String, base: String = "§7"): List<String> {
        var t = text
        // {{Green|8}} → ¤a8¤r  (inline 이 § 를 지우므로 임시 기호)
        repeat(3) {
            t = Regex("""\{\{([A-Za-z ]+)\|([^{}|]*)}}""").replace(t) { m ->
                val code = COLOR_TEMPLATES[m.groupValues[1].trim().lowercase()]
                if (code != null) "¤$code${m.groupValues[2]}¤r" else m.value
            }
        }
        return t.split(Regex("""<br\s*/?>""")).map { part ->
            val clean = WikiText.inline(part).replace("¤r", base).replace('¤', '§')
            base + clean
        }.filter { it.replace(Regex("§."), "").isNotBlank() }
    }

    private fun statLine(key: String, raw: String): String? {
        val v0 = WikiText.inline(raw).trim()
        if (v0.isEmpty() || !v0.any { it.isDigit() }) return null
        var v = if (v0.first().isDigit()) "+$v0" else v0
        if (key in PERCENT && !v.endsWith("%")) v += "%"
        val name = NAMES[key] ?: key.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        return "§7$name: " + (if (key in RED) "§c" else "§a") + v
    }

    private fun build(p: Map<String, String>, set: Map<String, String>?): Tooltip {
        val lines = mutableListOf<String>()
        // 스탯
        p.forEach { (k, v) ->
            if (k in NOT_STAT || k.contains("ability") || k.contains("bonus") || k.endsWith("_name") || k.endsWith("_description")) return@forEach
            statLine(k, v)?.let { lines += it }
        }
        p["shot_cooldown"]?.let { lines += "§7Shot Cooldown: §a${WikiText.inline(it)}" }
        fun effects(src: Map<String, String>) {
            src["special_effect"]?.let { lines += ""; lines += colored(it) }
            // 능력: item_ability_name / item_ability2_name …
            src.keys.mapNotNull { Regex("""^(item_ability\d*)_name$""").find(it)?.groupValues?.get(1) }.distinct().forEach { pre ->
                lines += ""
                val act = src["${pre}_activation"]?.let { " §e§l" + WikiText.inline(it).uppercase() }.orEmpty()
                lines += "§6Ability: " + WikiText.inline(src["${pre}_name"].orEmpty()) + act
                src["${pre}_description"]?.let { lines += colored(it) }
                src["${pre}_mana_cost"]?.let { lines += "§8Mana Cost: §3" + WikiText.inline(it) }
                src["${pre}_soulflow_cost"]?.let { lines += "§8Soulflow Cost: §3" + WikiText.inline(it) }
                src["${pre}_cooldown"]?.let { lines += "§8Cooldown: §a" + WikiText.inline(it) }
            }
            // 세트·조각 보너스: full_set_bonus_name, piece_bonus_name …
            src.keys.mapNotNull { Regex("""^(.*bonus\d*)_name$""").find(it)?.groupValues?.get(1) }.distinct().forEach { pre ->
                lines += ""
                val label = when { pre.startsWith("full_set") -> "Full Set Bonus"; pre.startsWith("piece") -> "Piece Bonus"; else -> "Bonus" }
                lines += "§6$label: " + WikiText.inline(src["${pre}_name"].orEmpty())
                src["${pre}_description"]?.let { lines += colored(it) }
            }
        }
        effects(p)
        if (set != null) effects(set)
        val rarity = (p["rarity"] ?: set?.get("rarity"))?.trim()?.lowercase()?.let { RARITY[it] ?: it.uppercase() }
        val type = p["type"]?.let { WikiText.inline(it) }
        if (rarity != null) { lines += ""; lines += (RARITY_COLOR[rarity] ?: "§f") + "§l" + rarity + (type?.let { " " + it.uppercase() } ?: "") }
        return Tooltip(lines, type, rarity)
    }

    /**
     * @param itemName 방어구 세트 문서에서 이 부위를 찾을 때 (예: "Necron's Chestplate")
     */
    fun parse(wikitext: String, itemName: String?): Tooltip? {
        // 1) 방어구 세트: 이 부위의 SetInfoPiece + 세트 공통 효과
        val setStart = wikitext.indexOf("{{SetInfo\n").takeIf { it >= 0 } ?: wikitext.indexOf("{{SetInfo|").takeIf { it >= 0 } ?: wikitext.indexOf("{{SetInfo ")
        if (setStart >= 0 && itemName != null) {
            val setBlock = block(wikitext, setStart)
            if (setBlock != null) {
                var i = setBlock.indexOf("{{SetInfoPiece")
                while (i >= 0) {
                    val b = block(setBlock, i) ?: break
                    val p = params(b)
                    if (p["name"]?.let { WikiText.inline(it) }.equals(itemName, true)) return build(p, params(setBlock))
                    i = setBlock.indexOf("{{SetInfoPiece", i + b.length)
                }
            }
        }
        // 2) 무기·도구·장비
        val start = wikitext.indexOf("{{ItemInfo")
        if (start >= 0) block(wikitext, start)?.let { return build(params(it), null) }
        return null
    }
}
