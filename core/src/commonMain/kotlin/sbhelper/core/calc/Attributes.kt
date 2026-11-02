package sbhelper.core.calc

import sbhelper.core.data.GameData

/** 내 Attribute 하나: 흡수한 샤드 수 → 레벨, 다음 레벨까지 */
data class MyAttribute(val attr: GameData.Attribute, val stacks: Int, val level: Int, val nextAt: Int?, val owned: Long)

object AttributeCalc {
    /** Attribute 이름 → 프로필 API 키 (예: Moonglade Mastery → moonglade_mastery) */
    fun key(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun level(stacks: Int, cumulative: List<Int>): Int = cumulative.count { stacks >= it }

    fun of(data: GameData, stacks: Map<String, Int>, owned: Map<String, Long>): List<MyAttribute> = data.attributes.map { a ->
        val st = stacks[key(a.name)] ?: 0
        val table = data.attributeLevels[a.rarity].orEmpty()
        val lv = level(st, table)
        MyAttribute(a, st, lv, table.getOrNull(lv), owned[a.sb] ?: owned[a.id] ?: 0L)
    }
}
