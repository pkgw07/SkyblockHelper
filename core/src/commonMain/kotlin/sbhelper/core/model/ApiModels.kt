package sbhelper.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Hypixel 공식 API v2 응답 모델 (https://api.hypixel.net 문서 기준).
 * 모르는 필드는 무시하도록 Json 설정(ignoreUnknownKeys)을 쓰므로, 필요한 필드만 적는다.
 */

// ---------- /v2/skyblock/bazaar ----------
@Serializable
data class BazaarResponse(
    val lastUpdated: Long = 0,
    val products: Map<String, BazaarProduct> = emptyMap(),
)

@Serializable
data class BazaarProduct(
    @SerialName("product_id") val productId: String,
    @SerialName("quick_status") val quickStatus: QuickStatus,
)

/**
 * 주의: Hypixel 표기는 헷갈린다.
 *  - buyPrice  = 즉시 구매가 (가장 싼 판매 주문)
 *  - sellPrice = 즉시 판매가 (가장 비싼 구매 주문)
 */
@Serializable
data class QuickStatus(
    val sellPrice: Double = 0.0,
    val sellVolume: Long = 0,
    val sellMovingWeek: Long = 0,
    val sellOrders: Long = 0,
    val buyPrice: Double = 0.0,
    val buyVolume: Long = 0,
    val buyMovingWeek: Long = 0,
    val buyOrders: Long = 0,
)

// ---------- /v2/resources/skyblock/items ----------
@Serializable
data class ItemsResponse(val items: List<SkyblockItem> = emptyList())

@Serializable
data class SkyblockItem(
    val id: String,
    val name: String = id,
    val material: String? = null,
    val tier: String? = null,
    val category: String? = null,
    @SerialName("npc_sell_price") val npcSellPrice: Double? = null,
    val description: String? = null,
    val skin: SkinValue? = null,
    /** 박물관 기증 정보 (공식 아이템 API) */
    @SerialName("museum_data") val museum: MuseumData? = null,
    /** 쓰려면 필요한 조건 (공식 아이템 API: SKILL, SLAYER, DUNGEON_TIER, HEART_OF_THE_MOUNTAIN …) */
    val requirements: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    /** 던전 안에서 쓰려면 필요한 조건 */
    @SerialName("catacombs_requirements") val catacombsRequirements: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    /** 기본 스탯 (공식 아이템 API, 키는 대문자 또는 소문자) */
    val stats: Map<String, Double> = emptyMap(),
    @SerialName("gemstone_slots") val gemstoneSlots: List<GemSlot> = emptyList(),
    /** "SOLO" / "COOP" 이면 거래 불가 */
    val soulbound: String? = null,
)

@Serializable
data class GemSlot(@SerialName("slot_type") val type: String = "")

@Serializable
data class MuseumData(
    @SerialName("donation_xp") val donationXp: Int? = null,
    val category: String? = null,
    /** {이 아이템: 상위 아이템} — 상위 아이템을 기증하면 이 아이템도 기증한 것으로 친다 */
    val parent: Map<String, String> = emptyMap(),
    /** 갑옷 세트로 기증: {세트 ID: 경험치} */
    @SerialName("armor_set_donation_xp") val armorSet: Map<String, Int> = emptyMap(),
    @SerialName("game_stage") val gameStage: String? = null,
    /** 같은 칸으로 치는 다른 아이템 (예: ✪ 붙은 던전 아이템) */
    @SerialName("mapped_item_ids") val mapped: List<String> = emptyList(),
)

@Serializable
data class SkinValue(val value: String? = null)

// ---------- /v2/resources/skyblock/skills ----------
@Serializable
data class SkillsResponse(val version: String? = null, val skills: Map<String, SkillDefinition> = emptyMap())

@Serializable
data class SkillDefinition(
    val name: String,
    val maxLevel: Int,
    val levels: List<SkillLevelDef> = emptyList(),
)

@Serializable
data class SkillLevelDef(val level: Int, val totalExpRequired: Double)

// ---------- /v2/resources/skyblock/collections ----------
@Serializable
data class CollectionsResponse(val collections: Map<String, CollectionCategory> = emptyMap())

@Serializable
data class CollectionCategory(val name: String, val items: Map<String, CollectionDef> = emptyMap())

@Serializable
data class CollectionDef(val name: String, val maxTiers: Int = 0, val tiers: List<CollectionTier> = emptyList())

@Serializable
data class CollectionTier(val tier: Int, val amountRequired: Long, val unlocks: List<String> = emptyList())

// ---------- /v2/resources/skyblock/election ----------
@Serializable
data class ElectionResponse(
    val lastUpdated: Long = 0,
    val mayor: Mayor? = null,
    val current: Election? = null,
)

@Serializable
data class Mayor(
    val key: String = "",
    val name: String = "",
    val perks: List<Perk> = emptyList(),
    val minister: Minister? = null,
    val election: Election? = null,
)

@Serializable
data class Minister(val key: String = "", val name: String = "", val perk: Perk? = null)

@Serializable
data class Perk(val name: String = "", val description: String = "", val minister: Boolean = false)

@Serializable
data class Election(val year: Int = 0, val candidates: List<Candidate> = emptyList())

@Serializable
data class Candidate(val key: String = "", val name: String = "", val perks: List<Perk> = emptyList(), val votes: Long = 0)

// ---------- /v2/skyblock/news ----------
@Serializable
data class NewsResponse(val items: List<NewsItem> = emptyList())

@Serializable
data class NewsItem(val title: String = "", val text: String = "", val link: String = "")

// ---------- /v2/skyblock/firesales ----------
@Serializable
data class FireSalesResponse(val sales: List<FireSale> = emptyList())

@Serializable
data class FireSale(
    @SerialName("item_id") val itemId: String = "",
    val start: Long = 0,
    val end: Long = 0,
    val amount: Int = 0,
    val price: Int = 0,
)

// ---------- /v2/skyblock/auctions , /v2/skyblock/auction ----------
@Serializable
data class AuctionsPage(
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalAuctions: Int = 0,
    val lastUpdated: Long = 0,
    val auctions: List<Auction> = emptyList(),
)

@Serializable
data class PlayerAuctionsResponse(val auctions: List<Auction> = emptyList())

@Serializable
data class Auction(
    val uuid: String,
    val auctioneer: String = "",
    @SerialName("profile_id") val profileId: String = "",
    val start: Long = 0,
    val end: Long = 0,
    @SerialName("item_name") val itemName: String = "",
    @SerialName("item_lore") val itemLore: String = "",
    val tier: String = "COMMON",
    val category: String = "",
    @SerialName("starting_bid") val startingBid: Double = 0.0,
    @SerialName("highest_bid_amount") val highestBidAmount: Double = 0.0,
    val bin: Boolean = false,
    val claimed: Boolean = false,
    val bids: List<Bid> = emptyList(),
    /** 아이템 NBT (base64). 응답에 따라 문자열 또는 {data} 객체 */
    @SerialName("item_bytes") val itemBytes: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class Bid(val bidder: String = "", val amount: Double = 0.0, val timestamp: Long = 0)

// ---------- Mojang ----------
@Serializable
data class MojangProfile(val id: String, val name: String)
