package sbhelper.core.nbt

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sbhelper.core.api.SbJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 인벤토리 칸의 아이템 하나 (게임 툴팁 그대로) */
data class SbItem(
    val slot: Int,
    /** 스카이블럭 아이템 ID (ExtraAttributes.id). 바닐라 아이템이면 null */
    val skyblockId: String?,
    /** § 색 코드가 들어 있는 이름 */
    val name: String,
    val lore: List<String>,
    val count: Int,
    /** 마인크래프트 1.8 아이템 번호와 데미지 값 */
    val legacyId: Int,
    val damage: Int,
    /** 머리(스컬) 아이템의 스킨 텍스처 주소 */
    val skinUrl: String?,
    val tier: String?,
    val reforge: String?,
    val stars: Int,
    val recombobulated: Boolean,
    val hotPotato: Int,
    val enchants: Map<String, Int>,
    val pet: PetData?,
    /** 장신구 가방 등에서 비활성/빈 칸이면 true */
    val empty: Boolean = false,
) {
    val plainName get() = name.replace(Regex("§."), "")
}

data class PetData(val type: String, val tier: String, val exp: Double, val heldItem: String?, val skin: String?)

/** 인벤토리 종류 (Hypixel API member.inventory 의 키) */
enum class InventoryKind(val key: String, val label: String, val columns: Int) {
    INVENTORY("inv_contents", "인벤토리", 9),
    ARMOR("inv_armor", "갑옷", 1),
    EQUIPMENT("equipment_contents", "장비", 1),
    ENDER_CHEST("ender_chest_contents", "엔더 상자", 9),
    WARDROBE("wardrobe_contents", "옷장", 9),
    VAULT("personal_vault_contents", "개인 금고", 9),
    ACCESSORY("talisman_bag", "장신구 가방", 9),
    SACKS("sacks_bag", "자루 가방", 9),
    POTIONS("potion_bag", "포션 가방", 9),
    FISHING("fishing_bag", "낚시 가방", 9),
    QUIVER("quiver", "화살통", 9),
    BACKPACK("backpack", "백팩", 9),
}

object ItemParser {
    private val tierWords = listOf("VERY SPECIAL", "SPECIAL", "SUPREME", "DIVINE", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON", "ULTIMATE")

    /** base64 NBT → 칸 번호 순서의 아이템 목록 (빈 칸 포함) */
    fun parse(base64: String?): List<SbItem?> {
        if (base64.isNullOrBlank()) return emptyList()
        val root = NbtReader.fromApi(base64) ?: return emptyList()
        return root.list("i").mapIndexed { i, tag -> (tag as? Nbt.Compound)?.let { item(i, it) } }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun item(slot: Int, c: Nbt.Compound): SbItem? {
        val legacy = c.number("id")?.toInt() ?: return null
        if (c.map.isEmpty()) return null
        val tag = c.compound("tag")
        val display = tag?.compound("display")
        val extra = tag?.compound("ExtraAttributes")
        val name = display?.string("Name") ?: "?"
        val lore = display?.list("Lore")?.mapNotNull { (it as? Nbt.Str)?.value }.orEmpty()
        val lastLine = lore.lastOrNull { it.isNotBlank() }?.replace(Regex("§."), "")?.uppercase().orEmpty()
        val tier = tierWords.firstOrNull { lastLine.startsWith(it) || lastLine.contains("$it ") || lastLine.endsWith(it) }
            ?.replace(' ', '_')
        val skin = tag?.compound("SkullOwner")?.compound("Properties")?.list("textures")?.firstOrNull()
            ?.let { (it as? Nbt.Compound)?.string("Value") }
            ?.let { v ->
                runCatching {
                    val json = SbJson.parseToJsonElement(Base64.decode(v).decodeToString()).jsonObject
                    json["textures"]?.jsonObject?.get("SKIN")?.jsonObject?.get("url")?.jsonPrimitive?.content
                        ?.replace("http://", "https://")
                }.getOrNull()
            }
        val pet = extra?.string("petInfo")?.let { s ->
            runCatching {
                val o = SbJson.parseToJsonElement(s) as JsonObject
                fun str(k: String) = (o[k] as? JsonPrimitive)?.content
                PetData(str("type") ?: "?", str("tier") ?: "COMMON", str("exp")?.toDoubleOrNull() ?: 0.0, str("heldItem"), str("skin"))
            }.getOrNull()
        }
        return SbItem(
            slot = slot,
            skyblockId = extra?.string("id"),
            name = name,
            lore = lore,
            count = c.number("Count")?.toInt() ?: 1,
            legacyId = legacy,
            damage = c.number("Damage")?.toInt() ?: 0,
            skinUrl = skin,
            tier = tier ?: pet?.tier,
            reforge = extra?.string("modifier"),
            stars = (extra?.number("upgrade_level") ?: extra?.number("dungeon_item_level") ?: 0.0).toInt(),
            recombobulated = (extra?.number("rarity_upgrades") ?: 0.0) > 0,
            hotPotato = (extra?.number("hot_potato_count") ?: 0.0).toInt(),
            enchants = extra?.compound("enchantments")?.map?.mapNotNull { (k, v) -> (v as? Nbt.Num)?.let { k to it.value.toInt() } }?.toMap().orEmpty(),
            pet = pet,
        )
    }
}
