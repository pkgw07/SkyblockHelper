package sbhelper.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import sbhelper.core.SkyblockRepository
import sbhelper.core.api.HypixelApi
import sbhelper.core.data.CraftTarget
import sbhelper.core.data.PriceAlert
import sbhelper.core.model.ForgeProcess
import sbhelper.core.model.GameMode
import sbhelper.core.model.PetInfo
import sbhelper.core.model.ProfileSummary
import sbhelper.core.sync.GistSync
import sbhelper.core.util.Clock
import sbhelper.ui.AppController
import sbhelper.ui.Screen
import sbhelper.ui.SkyblockApp
import sbhelper.ui.generated.Res
import java.io.File
import java.nio.file.Files

/**
 * CI 에서 화면을 사진으로 찍는다 (창 없이 렌더링).
 * 시세·선거·뉴스는 실제 공식 API, 프로필은 예시 데이터.
 * 실행: ./gradlew :desktopApp:screenshots
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screenshots").apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val api = HypixelApi()
    val repo = SkyblockRepository(api, GistSync(sbhelper.core.api.defaultHttpClient()),
        FileStorage(Files.createTempDirectory("sbh").toFile()), { }, scope,
        { Res.readBytes("files/gamedata.json").decodeToString() })

    runBlocking {
        repo.load()
        runCatching { repo.refreshStatic() }
        runCatching { repo.refreshElectionAndNews() }
        runCatching { repo.refreshBazaar() }
        runCatching { repo.refreshPatchNotes() }
        runCatching { repo.refreshContests() }
    }
    val now = Clock.now()
    val hour = 3_600_000L
    val main = demoProfile("main", "Mango", GameMode.NORMAL, selected = true, forge = listOf(
        ForgeProcess("forge_1", 1, "REFINED_DIAMOND", now - 7 * hour - 50 * 60_000),
        ForgeProcess("forge_1", 2, "MITHRIL_DRILL_1", now - 1 * hour),
        ForgeProcess("forge_1", 3, "REFINED_MITHRIL", now - 6 * hour - 30 * 60_000),
        ForgeProcess("forge_1", 4, "FUEL_TANK", now - 20 * hour),
    ))
    val iron = demoProfile("iron", "Kiwi", GameMode.IRONMAN, selected = false, forge = listOf(
        ForgeProcess("forge_1", 1, "REFINED_DIAMOND", now - 2 * hour),
    ))
    repo.updateSynced {
        it.copy(username = "SkyblockFan", playerUuid = "00000000000000000000000000000000", apiKey = "demo",
            selectedProfileId = "main", watchlist = listOf("ENCHANTED_COAL", "ENCHANTED_DIAMOND"),
            craftTargets = listOf(CraftTarget("MITHRIL_DRILL_1", 1), CraftTarget("REFINED_DIAMOND", 2)),
            priceAlerts = listOf(PriceAlert("a1", "ENCHANTED_DIAMOND", target = 1_000.0, above = true)))
    }
    repo.previewProfiles(listOf(main, iron))
    runBlocking { repo.checkAlerts() }

    val controller = AppController(repo, scope)
    val platform = DesktopPlatform()
    // 패치노트 첫 글 본문을 미리 받아 둔다
    repo.state.value.patchNotes.firstOrNull()?.let { n ->
        runBlocking { runCatching { repo.wiki.page(n.page) }.getOrNull() }?.let { controller.patchNotes.bodies[n.page] = it }
        controller.patchNotes.selected = n.page
    }
    fun shot(name: String, screen: Screen, w: Int = 1320, h: Int = 860, profile: String = "main") {
        repo.updateSynced { it.copy(selectedProfileId = profile) }
        controller.screen = screen
        val scene = ImageComposeScene(w, h, Density(1f)) { SkyblockApp(controller, platform) }
        var t = 0L
        repeat(12) { scene.render(t); t += 400_000_000L; Thread.sleep(250) }
        val img = scene.render(t)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("saved $name")
    }
    shot("01-home", Screen.HOME)
    shot("10-plan", Screen.PLAN)
    shot("13-gear", Screen.GEAR)
    shot("11-patch", Screen.PATCH)
    shot("02-profile", Screen.PROFILE)
    shot("03-timers", Screen.TIMERS)
    shot("04-bazaar", Screen.BAZAAR)
    shot("05-auction", Screen.AUCTION)
    shot("06-ironman", Screen.IRONMAN, profile = "iron")
    shot("07-settings", Screen.SETTINGS)
    shot("08-mobile-home", Screen.HOME, 412, 900)
    shot("09-mobile-ironman", Screen.IRONMAN, 412, 900, profile = "iron")
    shot("12-mobile-plan", Screen.PLAN, 412, 900)
    shot("14-inventory", Screen.INVENTORY)
    controller.inventoryTab = 5; shot("15-missing-accessories", Screen.INVENTORY)
    controller.statsTab = 2; shot("16-hotm", Screen.STATS)
    controller.statsTab = 1; shot("17-networth", Screen.STATS)
    controller.statsTab = 8; shot("18-bestiary", Screen.STATS)
    controller.statsTab = 10; shot("19-minions", Screen.STATS)
    controller.statsTab = 3; shot("20-hotf", Screen.STATS)
    controller.go(Screen.SEARCH); controller.openItem("MITHRIL_PICKAXE"); shot("21-search-item", Screen.SEARCH)
    controller.wiki = null; controller.searchQuery = "mithril"; shot("22-search", Screen.SEARCH)
    controller.openEvent("Dark Auction"); shot("23-events", Screen.EVENTS)
    controller.openJacob(); shot("24-jacob", Screen.EVENTS)
    controller.statsTab = 5; shot("26-dungeons-slayer", Screen.STATS)
    controller.inventoryTab = 3; shot("27-wardrobe", Screen.INVENTORY)
    shot("28-mobile-wardrobe", Screen.INVENTORY, 412, 900)
    controller.statsTab = 5; shot("29-mobile-dungeons", Screen.STATS, 412, 900)
    controller.statsTab = 8; shot("30-mobile-bestiary", Screen.STATS, 412, 900)
    controller.statsTab = 0; shot("31-mobile-stats", Screen.STATS, 412, 900)
    // 스킬 가이드 테크트리: 스킬마다 다른 구성
    listOf("40-guide-mining" to 0, "41-guide-farming" to 1, "42-guide-foraging" to 2, "43-guide-fishing" to 3, "44-guide-hunting" to 4, "45-guide-combat" to 5, "46-guide-common" to 6)
        .forEach { (n, t) -> controller.gearTab = t; shot(n, Screen.GEAR, 1320, 3000) }
    controller.gearTab = 3; controller.gearSubTabs["FISHING"] = 2; shot("47-guide-fishing-lava", Screen.GEAR, 1320, 1600)
    controller.gearTab = 5; controller.gearSubTabs["COMBAT"] = 1; shot("48-guide-combat-armor", Screen.GEAR, 1320, 2400)
    controller.gearSubTabs["COMBAT"] = 4; shot("49-guide-combat-slayer", Screen.GEAR, 1320, 2600)
    controller.gearTab = 0; shot("50-mobile-guide-mining", Screen.GEAR, 412, 3000)
    controller.gearTab = 1; shot("51-mobile-guide-farming", Screen.GEAR, 412, 3000)
    shot("33-mobile-settings", Screen.SETTINGS, 412, 900)
    controller.go(Screen.SEARCH); controller.openItem("HYPERION"); shot("35-search-hyperion", Screen.SEARCH, 1320, 2000)
    controller.openItem("ENCHANTED_DIAMOND"); shot("36-search-enchanted-diamond", Screen.SEARCH, 1320, 2400)
    controller.openItem("TERMINATOR"); shot("37-mobile-search-terminator", Screen.SEARCH, 412, 2400)
    // 다른 플레이어 보기 (예시 데이터)
    controller.viewing = sbhelper.core.ViewedPlayer("OtherPlayer", "1".repeat(32), listOf(iron, main.copy(selected = false)), iron.profileId, null, null)
    shot("52-view-other-player", Screen.PROFILE)
    shot("53-mobile-view-other-player", Screen.STATS, 412, 900)
    controller.viewing = null
    shot("54-profile-lookup-row", Screen.PROFILE)
    // 이스터에그: Hyperion 그림 클릭 → 화면 전체 Implosion (터지는 중간 장면)
    run {
        repo.updateSynced { it.copy(selectedProfileId = "main") }
        controller.go(Screen.SEARCH); controller.openItem("HYPERION")
        val scene = ImageComposeScene(1320, 860, Density(1f)) { SkyblockApp(controller, platform) }
        var t = 0L
        repeat(10) { scene.render(t); t += 400_000_000L; Thread.sleep(200) }
        controller.implode(androidx.compose.ui.geometry.Offset(160f, 180f))
        repeat(3) { Thread.sleep(110); scene.render(t); t += 16_000_000L }
        File(out, "55-easter-implosion.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("saved 55-easter-implosion")
    }
    repo.updateDevice { it.copy(contentWidth = 2) }
    controller.statsTab = 8; shot("34-wide-content", Screen.STATS, 1920, 1000)
    repo.updateDevice { it.copy(contentWidth = 0) }
    repo.stopLoops()
    kotlin.system.exitProcess(0)
}

private fun demoProfile(id: String, name: String, mode: GameMode, selected: Boolean, forge: List<ForgeProcess>) = ProfileSummary(
    profileId = id, cuteName = name, selected = selected, gameMode = mode, memberCount = 1,
    purse = 18_450_210.0, bank = 152_000_000.0, skyblockXp = 28_640.0, fairySouls = 241,
    skillXp = mapOf(
        "FARMING" to 55_000_000.0, "MINING" to 112_000_000.0, "COMBAT" to 38_000_000.0, "FORAGING" to 6_200_000.0,
        "FISHING" to 9_800_000.0, "ENCHANTING" to 120_000_000.0, "ALCHEMY" to 52_000_000.0, "TAMING" to 41_000_000.0,
        "HUNTING" to 120_000.0, "CARPENTRY" to 3_100_000.0, "RUNECRAFTING" to 94_500.0, "SOCIAL" to 41_000.0,
    ),
    farmingCapBonus = 8,
    slayerXp = mapOf("zombie" to 1_250_000.0, "spider" to 410_000.0, "wolf" to 980_000.0, "enderman" to 120_000.0, "blaze" to 32_000.0),
    catacombsXp = 210_000_000.0, classXp = mapOf("mage" to 160_000_000.0, "archer" to 25_000_000.0, "healer" to 3_000_000.0),
    selectedClass = "mage", hotmXp = 1_400_000.0, quickForgeLevel = 14, forge = forge,
    sacks = mapOf("ENCHANTED_DIAMOND" to 312L, "DIAMOND" to 18_400L, "MITHRIL_ORE" to 220_000L, "ENCHANTED_MITHRIL" to 640L,
        "COAL" to 9_000L, "ENCHANTED_GOLD" to 1_200L, "GEMSTONE_MIXTURE" to 3L, "ENCHANTED_IRON" to 900L),
    collections = mapOf("WHEAT" to 210_000L, "INK_SACK:3" to 1_800L, "MITHRIL_ORE" to 920_000L, "DIAMOND" to 48_000L,
        "COAL" to 95_000L, "GOLD_INGOT" to 21_000L, "ENDER_PEARL" to 9_800L),
    essence = mapOf("WITHER" to 12_400L, "DRAGON" to 3_210L, "GOLD" to 980L, "DIAMOND" to 2_400L),
    pets = listOf(PetInfo("GOLDEN_DRAGON", "LEGENDARY", 2.1e7, true)),
    floorCompletions = mapOf(0 to 12, 1 to 30, 2 to 25, 3 to 40, 4 to 18, 5 to 9),
    slayerBossKills = mapOf("zombie" to mapOf(1 to 12, 2 to 40, 3 to 85, 4 to 220, 5 to 31), "spider" to mapOf(3 to 10, 4 to 64),
        "wolf" to mapOf(3 to 30, 4 to 150), "enderman" to mapOf(1 to 5, 2 to 48, 3 to 12), "blaze" to mapOf(1 to 9)),
    hotmNodes = mapOf("mining_speed" to 50, "mining_fortune" to 50, "forge_time" to 14, "daily_powder" to 1, "random_event" to 45,
        "mining_speed_boost" to 1, "titanium_insanium" to 30, "pickaxe_toss" to 1, "efficient_miner" to 60, "mining_experience" to 100),
    bestiaryKills = mapOf("zombie_1" to 4_200, "skeleton_6" to 900, "spider_1" to 650, "enderman_42" to 12_000, "wolf_15" to 300,
        "magma_cube_75" to 2_000, "blaze_100" to 1_400),
    craftedMinions = listOf("COBBLESTONE_1", "COBBLESTONE_5", "COBBLESTONE_11", "WHEAT_1", "WHEAT_9", "SNOW_11", "CLAY_4", "MITHRIL_7"),
    powders = mapOf("mithril" to 1_820_000L, "gemstone" to 3_400_000L, "glacite" to 640_000L),
    inventories = DemoNbt.inventories(),
    hotfNodes = mapOf("forest_speed" to 20, "timber" to 5, "sweep" to 30, "foraging_fortune" to 12, "center_of_the_forest" to 2),
    hotfXp = 240_000.0,
    wardrobe = DemoNbt.wardrobe(),
    member = demoMember(),
)

/** 화면 확인용 가짜 원본 데이터 (던전 입장·클리어, 슬레이어) — 실제 API 와 같은 위치 */
private fun demoMember() = kotlinx.serialization.json.buildJsonObject {
    fun floors(vararg pairs: Pair<Int, Int>) = kotlinx.serialization.json.buildJsonObject { pairs.forEach { (f, n) -> put("$f", kotlinx.serialization.json.JsonPrimitive(n)) } }
    put("dungeons", kotlinx.serialization.json.buildJsonObject {
        put("secrets", kotlinx.serialization.json.JsonPrimitive(18_420))
        put("selected_dungeon_class", kotlinx.serialization.json.JsonPrimitive("mage"))
        put("dungeon_types", kotlinx.serialization.json.buildJsonObject {
            put("catacombs", kotlinx.serialization.json.buildJsonObject {
                put("tier_completions", floors(0 to 12, 1 to 30, 2 to 25, 3 to 40, 4 to 18, 5 to 9, 6 to 120, 7 to 340))
                put("times_played", floors(0 to 13, 1 to 33, 2 to 27, 3 to 44, 4 to 21, 5 to 10, 6 to 131, 7 to 372))
                put("fastest_time_s_plus", floors(6 to 196_000, 7 to 312_000))
                put("fastest_time_s", floors(5 to 150_000, 6 to 181_000, 7 to 295_000))
                put("best_score", floors(5 to 290, 6 to 317, 7 to 305))
                put("highest_tier_completed", kotlinx.serialization.json.JsonPrimitive(7))
            })
            put("master_catacombs", kotlinx.serialization.json.buildJsonObject {
                put("tier_completions", floors(1 to 22, 2 to 15, 3 to 41, 4 to 8))
                put("times_played", floors(1 to 24, 2 to 17, 3 to 45, 4 to 11))
                put("best_score", floors(3 to 302))
            })
        })
    })
}

/** 화면 확인용 가짜 인벤토리 (실제 API 와 같은 gzip NBT 형식) */
private object DemoNbt {
    private fun item(id: String, name: String, lore: List<String>, count: Int = 1, legacy: Int = 1): (java.io.DataOutputStream) -> Unit = { o ->
        o.writeByte(2); o.writeUTF("id"); o.writeShort(legacy)
        o.writeByte(1); o.writeUTF("Count"); o.writeByte(count)
        o.writeByte(2); o.writeUTF("Damage"); o.writeShort(0)
        o.writeByte(10); o.writeUTF("tag")
        o.writeByte(10); o.writeUTF("display")
        o.writeByte(8); o.writeUTF("Name"); o.writeUTF(name)
        o.writeByte(9); o.writeUTF("Lore"); o.writeByte(8); o.writeInt(lore.size); lore.forEach { o.writeUTF(it) }
        o.writeByte(0)
        o.writeByte(10); o.writeUTF("ExtraAttributes")
        o.writeByte(8); o.writeUTF("id"); o.writeUTF(id)
        o.writeByte(0)
        o.writeByte(0)
        o.writeByte(0)
    }

    private fun encode(slots: List<((java.io.DataOutputStream) -> Unit)?>): String {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(java.util.zip.GZIPOutputStream(bytes)).use { o ->
            o.writeByte(10); o.writeUTF("")
            o.writeByte(9); o.writeUTF("i"); o.writeByte(10); o.writeInt(slots.size)
            slots.forEach { w -> w?.invoke(o); if (w == null) o.writeByte(0) }
            o.writeByte(0)
        }
        return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /** 옷장: 칸마다 아이템 하나씩 (실제 API 의 member.loadout 형식) */
    fun wardrobe(): sbhelper.core.model.Wardrobe {
        fun one(id: String, name: String, tier: String) = encode(listOf(item(id, name, listOf("", "§6§l$tier"), legacy = 298)))
        val necron = listOf(one("NECRON_HELMET", "§6Necron's Helmet", "LEGENDARY DUNGEON HELMET"), one("NECRON_CHESTPLATE", "§6Necron's Chestplate", "LEGENDARY DUNGEON CHESTPLATE"),
            one("NECRON_LEGGINGS", "§6Necron's Leggings", "LEGENDARY DUNGEON LEGGINGS"), one("NECRON_BOOTS", "§6Necron's Boots", "LEGENDARY DUNGEON BOOTS"))
        val divan = listOf(one("DIVAN_HELMET", "§6Helmet of Divan", "LEGENDARY HELMET"), one("DIVAN_CHESTPLATE", "§6Chestplate of Divan", "LEGENDARY CHESTPLATE"),
            one("DIVAN_LEGGINGS", "§6Leggings of Divan", "LEGENDARY LEGGINGS"), one("DIVAN_BOOTS", "§6Boots of Divan", "LEGENDARY BOOTS"))
        val farm = listOf(null, one("FERMENTO_CHESTPLATE", "§6Fermento Chestplate", "LEGENDARY CHESTPLATE"),
            one("FERMENTO_LEGGINGS", "§6Fermento Leggings", "LEGENDARY LEGGINGS"), one("FERMENTO_BOOTS", "§6Fermento Boots", "LEGENDARY BOOTS"))
        return sbhelper.core.model.Wardrobe(mapOf(1 to necron, 2 to divan, 5 to farm, 11 to necron), mapOf(1 to listOf(null, null, null, null)), 1, null,
            listOf(sbhelper.core.model.Loadout(1, "§a던전", 1, null), sbhelper.core.model.Loadout(2, "채광", 2, null)))
    }

    fun inventories(): Map<String, String> {
        val sword = item("HYPERION", "§dHeroic Hyperion §6✪✪✪✪✪", listOf("§7Damage: §c+260", "§7Strength: §c+150", "", "§aScroll Abilities:", "§6Ability: Wither Impact §eRIGHT CLICK", "", "§d§lMYTHIC DUNGEON SWORD"))
        val pick = item("DIVAN_DRILL", "§6Divan's Drill", listOf("§7Mining Speed: §a+1,800", "§7Mining Fortune: §a+150", "", "§6§lLEGENDARY DRILL"))
        val aote = item("ASPECT_OF_THE_END", "§9Aspect of the End", listOf("§7Damage: §c+100", "", "§9§lRARE SWORD"))
        val pearl = item("ENDER_PEARL", "§fEnder Pearl", listOf("§f§lCOMMON"), 16)
        val boots = item("NECRON_BOOTS", "§6Necron's Boots", listOf("§7Health: §a+145", "", "§6§lLEGENDARY DUNGEON BOOTS"))
        val helm = item("WARDEN_HELMET", "§5Warden Helmet", listOf("§7Health: §a+300", "", "§5§lEPIC HELMET"))
        val ring = item("HEGEMONY_ARTIFACT", "§dHegemony Artifact", listOf("§d§lMYTHIC ACCESSORY"))
        val tal = item("SPEED_TALISMAN", "§fSpeed Talisman", listOf("§f§lCOMMON ACCESSORY"))
        val inv = MutableList<((java.io.DataOutputStream) -> Unit)?>(36) { null }
        inv[0] = sword; inv[1] = pick; inv[2] = aote; inv[8] = pearl; inv[12] = pearl; inv[20] = aote
        return mapOf(
            "inv_contents" to encode(inv),
            "inv_armor" to encode(listOf(boots, null, null, helm)),
            "talisman_bag" to encode(listOf(ring, tal, null, null, null, null, null, null, null)),
            "ender_chest_contents" to encode(listOf(sword, null, pearl) + List(42) { null }),
        )
    }
}
