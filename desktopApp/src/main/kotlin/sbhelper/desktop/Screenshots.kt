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
    controller.statsTab = 1; shot("16-hotm", Screen.STATS)
    controller.statsTab = 0; shot("17-networth", Screen.STATS)
    controller.statsTab = 2; shot("18-bestiary", Screen.STATS)
    controller.statsTab = 4; shot("19-minions", Screen.STATS)
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
    slayerBossKills = mapOf("zombie" to mapOf(4 to 20), "spider" to mapOf(3 to 10), "wolf" to mapOf(4 to 15), "enderman" to mapOf(2 to 5)),
    hotmNodes = mapOf("mining_speed" to 50, "mining_fortune" to 50, "forge_time" to 14, "daily_powder" to 1, "random_event" to 45,
        "mining_speed_boost" to 1, "titanium_insanium" to 30, "pickaxe_toss" to 1, "efficient_miner" to 60, "mining_experience" to 100),
    bestiaryKills = mapOf("zombie_1" to 4_200, "skeleton_6" to 900, "spider_1" to 650, "enderman_42" to 12_000, "wolf_15" to 300,
        "magma_cube_75" to 2_000, "blaze_100" to 1_400),
    craftedMinions = listOf("COBBLESTONE_1", "COBBLESTONE_5", "COBBLESTONE_11", "WHEAT_1", "WHEAT_9", "SNOW_11", "CLAY_4", "MITHRIL_7"),
    powders = mapOf("mithril" to 1_820_000L, "gemstone" to 3_400_000L, "glacite" to 640_000L),
    inventories = DemoNbt.inventories(),
)

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
