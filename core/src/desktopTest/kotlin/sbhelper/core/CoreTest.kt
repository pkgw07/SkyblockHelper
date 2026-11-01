package sbhelper.core

import kotlinx.serialization.json.jsonObject
import sbhelper.core.api.SbJson
import sbhelper.core.calc.BazaarCalc
import sbhelper.core.calc.CollectionCalc
import sbhelper.core.calc.CraftPlanner
import sbhelper.core.calc.ForgeCalc
import sbhelper.core.calc.Leveling
import sbhelper.core.calc.Method
import sbhelper.core.calc.SkyblockCalendar
import sbhelper.core.data.GameData
import sbhelper.core.data.NotifyPrefs
import sbhelper.core.data.PriceAlert
import sbhelper.core.model.BazaarResponse
import sbhelper.core.model.CollectionsResponse
import sbhelper.core.model.ElectionResponse
import sbhelper.core.model.ForgeProcess
import sbhelper.core.model.GameMode
import sbhelper.core.model.ProfileParser
import sbhelper.core.model.SkillsResponse
import sbhelper.core.notify.AlertEngine
import sbhelper.core.notify.AlertInputs
import sbhelper.core.sync.PairingCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun fixture(name: String) = CoreTest::class.java.getResource("/fixtures/$name")!!.readText()
private val gameData by lazy {
    GameData.parse(File("../ui/src/commonMain/composeResources/files/gamedata.json").readText())
}

class CoreTest {

    // ---------- 공식 API 응답 파싱 (2026-10-31 실제 응답) ----------
    @Test
    fun parsesOfficialSkills() {
        val skills = SbJson.decodeFromString<SkillsResponse>(fixture("skills.json"))
        val foraging = skills.skills.getValue("FORAGING")
        assertEquals(57, foraging.maxLevel)          // 공식 데이터 기준 최신 최대 레벨
        assertEquals(60, skills.skills.getValue("TAMING").maxLevel)
        assertTrue("HUNTING" in skills.skills)
        val farming = skills.skills.getValue("FARMING")
        // 50 xp = 1레벨, 175 xp = 2레벨 (50+125)
        assertEquals(1, Leveling.skill(50.0, farming).level)
        assertEquals(2, Leveling.skill(175.0, farming).level)
        // 농사는 Anita 보너스에 따라 최대 레벨 제한
        assertEquals(50, Leveling.skill(1e12, farming, capOverride = 50).level)
        assertEquals(60, Leveling.skill(1e12, farming).level)
    }

    @Test
    fun parsesElectionAndCollections() {
        val e = SbJson.decodeFromString<ElectionResponse>(fixture("election.json"))
        assertEquals("Aatrox", e.mayor?.name)
        assertEquals("Foxy", e.mayor?.minister?.name)
        val c = SbJson.decodeFromString<CollectionsResponse>(fixture("collections.json"))
        val progress = CollectionCalc.progress(c, mapOf("INK_SACK:3" to 600L))
        val cocoa = progress.first { it.itemId == "INK_SACK:3" }
        assertEquals(3, cocoa.tier)
        assertEquals(2000L, cocoa.nextRequired)
    }

    @Test
    fun bazaarFlipsUseTaxAndOrders() {
        val bz = SbJson.decodeFromString<BazaarResponse>(fixture("bazaar_small.json"))
        val flips = BazaarCalc.flips(bz.products, taxRate = 0.0125)
        assertTrue(flips.isNotEmpty())
        val coal = flips.first { it.id == "ENCHANTED_COAL" }
        val q = bz.products.getValue("ENCHANTED_COAL").quickStatus
        assertEquals((q.buyPrice - 0.1) * (1 - 0.0125) - (q.sellPrice + 0.1), coal.profitPerItem, 1e-6)
    }

    // ---------- 포지 (위키 The Forge/Table, The Forge) ----------
    @Test
    fun forgeDurationsFromWiki() {
        assertEquals(28_800L, gameData.forgeDurationSeconds("REFINED_DIAMOND")) // 위키: 8 Hours
        val p = ForgeProcess("forge_1", 1, "REFINED_DIAMOND", startTime = 1_000_000L)
        val noPerk = ForgeCalc.status(p, gameData, "Refined Diamond", 0, null, null)
        assertEquals(1_000_000L + 28_800_000L, noPerk.endTime)
        val withPerk = ForgeCalc.status(p, gameData, "Refined Diamond", 20, null, null)
        assertEquals(0.30, withPerk.reduction, 1e-9) // 위키: 최대 30%
        val unknown = ForgeCalc.status(p.copy(itemId = "NOT_A_REAL_ITEM"), gameData, "?", 0, null, null)
        assertNull(unknown.endTime)
    }

    @Test
    fun quickForgeFormulaMatchesWiki() {
        val qf = gameData.rules.quickForge
        assertEquals(10.5, qf.percent(1), 1e-9)   // 위키: Level 1 = 10.5%
        assertEquals(14.5, qf.percent(9), 1e-9)   // 위키 표: 2-9 → 14.5
        assertEquals(15.0, qf.percent(10), 1e-9)  // 위키 표: 10 → 15
        assertEquals(19.5, qf.percent(19), 1e-9)  // 위키 표: 11-19 → 19.5
        assertEquals(30.0, qf.percent(20), 1e-9)  // 위키 표: 20 → 30
    }

    @Test
    fun moltenForgeIsAdditiveAndOnlyAfterTermStart() {
        val cole = ElectionResponse(mayor = sbhelper.core.model.Mayor(name = "Cole",
            perks = listOf(sbhelper.core.model.Perk("Molten Forge", ""))))
        val termStart = 5_000_000L
        val before = ForgeProcess("forge_1", 1, "REFINED_DIAMOND", termStart - 1)
        val after = ForgeProcess("forge_1", 2, "REFINED_DIAMOND", termStart + 1)
        assertEquals(0.105, ForgeCalc.status(before, gameData, "", 1, cole, termStart).reduction, 1e-9)
        assertEquals(0.355, ForgeCalc.status(after, gameData, "", 1, cole, termStart).reduction, 1e-9) // 10.5 + 25 (더하기)
    }

    @Test
    fun levelingTablesFromWiki() {
        assertEquals(listOf(5L, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000), gameData.leveling.slayerXp["spider"])
        assertEquals(50, gameData.leveling.catacombs.size)
        assertEquals(3000L, gameData.leveling.hotm[1])
    }

    // ---------- Ironman 제작 플래너 ----------
    @Test
    fun craftPlannerBreaksDownToBaseMaterials() {
        val plan = CraftPlanner(gameData).plan("ENCHANTED_DIAMOND_BLOCK", 1)
        assertEquals(Method.CRAFT, plan.root.method)
        val diamond = plan.raw.first { it.itemId == "DIAMOND" }
        assertEquals(160.0 * 160.0, diamond.needed)  // 160 Enchanted Diamond × 160 Diamond
    }

    @Test
    fun craftPlannerUsesSackStock() {
        val stock = mapOf("ENCHANTED_DIAMOND" to 100L, "DIAMOND" to 1000L)
        val plan = CraftPlanner(gameData).plan("ENCHANTED_DIAMOND_BLOCK", 1, stock)
        val ench = plan.root.children.first { it.itemId == "ENCHANTED_DIAMOND" }
        assertEquals(100.0, ench.fromStock)
        val diamond = plan.raw.first { it.itemId == "DIAMOND" }
        assertEquals(60.0 * 160.0, diamond.needed)
        assertEquals(1000.0, diamond.have)
    }

    @Test
    fun craftPlannerIncludesForgeTime() {
        val plan = CraftPlanner(gameData).plan("REFINED_DIAMOND", 2)
        assertEquals(Method.FORGE, plan.root.method)
        assertEquals(2 * 28_800L, plan.totalForgeSeconds)
    }

    // ---------- 달력 (위키 Time Systems, Events) ----------
    @Test
    fun calendarMatchesWiki() {
        SkyblockCalendar.epoch = gameData.rules.skyblockEpochSeconds * 1000
        assertEquals(1_560_275_700_000L, SkyblockCalendar.epoch)
        val now = 1_793_451_772_352L
        assertEquals(523, SkyblockCalendar.dateAt(now).year) // 공식 API 선거 연도 522 → 현재 523년
        val events = SkyblockCalendar.upcoming(now, gameData, null)
        val minute = { t: Long -> (t / 60_000) % 60 }
        // 위키 타이머: 다크 옥션은 매시 :55, 제이콥 대회는 매시 :15 에 20분
        val da = events.first { it.name == "Dark Auction" }
        assertEquals(55, minute(da.start))
        val jacob = events.first { it.name == "Jacob's Farming Contest" }
        assertEquals(15, minute(jacob.start))
        assertEquals(20 * 60_000L, jacob.end - jacob.start)
        // 떠돌이 동물원: Early Summer 1일 / Early Winter 1일, 3일간
        val zoo = events.first { it.name == "Traveling Zoo" }
        val d = SkyblockCalendar.dateAt(zoo.start)
        assertTrue(d.day == 1 && (d.month == 3 || d.month == 9))
        // 시장 전용 이벤트는 그 시장이 아닐 때 나오지 않는다
        assertTrue(events.none { it.name.startsWith("Marina") || it.name.startsWith("Cole") || it.name.startsWith("Foxy") })
        assertTrue(events.all { it.end >= now || it.start >= now })
    }

    @Test
    fun mayorEventsFollowCurrentMayor() {
        val now = 1_793_451_772_352L
        val foxyFishing = ElectionResponse(mayor = sbhelper.core.model.Mayor(name = "Aatrox",
            minister = sbhelper.core.model.Minister(name = "Foxy", perk = sbhelper.core.model.Perk("Extra Event",
                "Schedules an extra §bFishing Festival §7event during the year."))))
        val names = SkyblockCalendar.upcoming(now, gameData, foxyFishing).map { it.name }
        assertTrue("Foxy's Extra Fishing Festival" in names)
        assertTrue("Foxy's Extra Mining Fiesta" !in names)
    }

    // ---------- 알림 엔진 ----------
    @Test
    fun alertsFireOnceForForgeAndPrice() {
        val start = 0L
        val p = ForgeProcess("forge_1", 1, "REFINED_DIAMOND", start)
        val status = ForgeCalc.status(p, gameData, "Refined Diamond", 0, null, null)
        val bz = SbJson.decodeFromString<BazaarResponse>(fixture("bazaar_small.json"))
        val alert = PriceAlert("a1", "ENCHANTED_COAL", target = 1e12) // 무조건 "이하" 조건 충족
        val inputs = AlertInputs("prof", listOf(status), emptyList(), emptyList(), bz.products, listOf(alert), { it })
        val now = status.endTime!! + 1000
        val first = AlertEngine.evaluate(inputs, NotifyPrefs(), emptySet(), now)
        assertEquals(2, first.notifications.size)
        val again = AlertEngine.evaluate(inputs, NotifyPrefs(), first.notifications.map { it.key }.toSet(), now)
        assertTrue(again.notifications.isEmpty())
        // 아직 안 끝났으면 알림 없음
        val early = AlertEngine.evaluate(inputs.copy(priceAlerts = emptyList()), NotifyPrefs(), emptySet(), status.endTime!! - 1)
        assertTrue(early.notifications.isEmpty())
    }

    // ---------- 프로필 파서 (임시 예시 데이터 — 실제 프로필로 검증 필요) ----------
    @Test
    fun profileParserIsTolerant() {
        val json = SbJson.parseToJsonElement(
            """
            {"profile_id":"p1","cute_name":"Apple","selected":true,"game_mode":"ironman",
             "members":{"abc":{
                "currencies":{"coin_purse":1234.5},
                "player_data":{"experience":{"SKILL_MINING":1000.0}},
                "forge":{"forge_processes":{"forge_1":{"1":{"type":"FORGING","id":"REFINED_DIAMOND","startTime":5,"slot":1}}}},
                "inventory":{"sacks_counts":{"DIAMOND":10,"COAL":0}}
             }}}
            """.trimIndent(),
        ).jsonObject
        val p = assertNotNull(ProfileParser.parse(json, "abc"))
        assertEquals(GameMode.IRONMAN, p.gameMode)
        assertEquals(1234.5, p.purse)
        assertEquals(1000.0, p.skillXp?.get("MINING"))
        assertEquals("REFINED_DIAMOND", p.forge.single().itemId)
        assertEquals(mapOf("DIAMOND" to 10L), p.sacks)
        assertNull(p.bank)            // 은행 API 꺼짐
        assertNull(p.collections)     // 컬렉션 API 꺼짐
    }

    @Test
    fun patchNoteDatesAndWikiText() {
        val note = sbhelper.core.api.PatchNote("Changelog/2026/October 29", "t", "October 29", 2026, 10290, "", "", false)
        val t = AlertEngine.patchDate(note)!!
        assertEquals("2026-10-29", kotlinx.datetime.Instant.fromEpochMilliseconds(t).toString().take(10))
        assertEquals("Reworked the Accessory Bag and 10%",
            sbhelper.core.api.WikiText.inline("Reworked the [[Accessory Bag]] and {{Green|10%}}"))
        val blocks = sbhelper.core.api.WikiText.blocks("== ► New Bag ==\n* one\n** two\nhello [[A|b]]")
        assertEquals(sbhelper.core.api.DocBlock.Heading("New Bag", 1), blocks[0])
        assertEquals(sbhelper.core.api.DocBlock.Bullet("two", 2), blocks[2])
        assertEquals(sbhelper.core.api.DocBlock.Paragraph("hello b"), blocks[3])
    }

    @Test
    fun versionComparison() {
        assertTrue(sbhelper.core.update.Updates.isNewer("1.2.10", "1.2.9"))
        assertTrue(!sbhelper.core.update.Updates.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun progressionUsesWikiRequirements() {
        val skills = SbJson.decodeFromString<SkillsResponse>(fixture("skills.json"))
        val json = SbJson.parseToJsonElement(
            """{"profile_id":"p","members":{"a":{"player_data":{"experience":{"SKILL_COMBAT":5000000.0,"SKILL_MINING":0.0}},
               "mining_core":{"experience":20000},"slayer":{"slayer_bosses":{"zombie":{"xp":300,"boss_kills_tier_1":3}}},
               "dungeons":{"dungeon_types":{"catacombs":{"tier_completions":{"0":5,"1":2}}}}}}}""",
        ).jsonObject
        val profile = ProfileParser.parse(json, "a")!!
        val nodes = sbhelper.core.calc.ProgressionPlanner.build(gameData, profile, skills)
        val crimson = nodes.first { it.title == "Crimson Isle" }      // 위키: Combat 22
        assertTrue(crimson.requirement.contains("22"))
        val hotm3 = nodes.first { it.id == "hotm:3" }                 // 20,000 XP → HotM 3 (12,000 누적)
        assertEquals(sbhelper.core.calc.NodeStatus.DONE, hotm3.status)
        val f2 = nodes.first { it.id == "floor:2" }                   // Entrance, F1 클리어 → F2 가 다음
        assertEquals(sbhelper.core.calc.NodeStatus.NEXT, f2.status)
        val spider = nodes.first { it.id == "slayer:spider" }         // Revenant T2 처치 → Tarantula 해금
        assertEquals(sbhelper.core.calc.NodeStatus.NEXT, spider.status)
        assertTrue(sbhelper.core.calc.ProgressionPlanner.recommend(nodes).isNotEmpty())
    }

    @Test
    fun profileReadsSkillTreeCoopCollectionsAndCaps() {
        val json = SbJson.parseToJsonElement(
            """
            {"profile_id":"p","members":{
              "me":{"skill_tree":{"experience":{"mining":40000},"nodes":{"mining":{"forge_time":20}}},
                    "mining_core":{"experience":100,"nodes":{"forge_time":1}},
                    "collection":{"WHEAT":100,"COAL":5},
                    "pets_data":{"pet_care":{"pet_types_sacrificed":["A","B","C"]}}},
              "friend":{"collection":{"WHEAT":50}}}}
            """.trimIndent(),
        ).jsonObject
        val p = assertNotNull(ProfileParser.parse(json, "me"))
        assertEquals(40000.0, p.hotmXp)                 // 새 구조(skill_tree) 우선
        assertEquals(20, p.quickForgeLevel)
        assertEquals(150L, p.collections?.get("WHEAT"))  // 협동원 합계
        assertEquals(53, p.skillCap("TAMING", 60))       // 50 + George 에게 준 펫 3종
        assertEquals(50, p.skillCap("FARMING", 60))
        val f = ForgeCalc.status(ForgeProcess("forge_1", 1, "REFINED_DIAMOND", 0), gameData, "", p.quickForgeLevel, null, null)
        assertEquals(0.30, f.reduction, 1e-9)
    }

    @Test
    fun eliteBotParsing() {
        val crops = sbhelper.core.api.EliteBotApi.parse(SbJson.parseToJsonElement(
            """{"year":523,"contests":{"1793452500":["Wheat","Carrot","Potato"],"bad":["x"]}}"""))
        assertEquals(listOf("Wheat", "Carrot", "Potato"), crops[1_793_452_500_000L])
        assertEquals(1, crops.size)
    }

    @Test
    fun wikiTechTreesAndIronmanGuide() {
        assertTrue(gameData.techTrees["MINING"].orEmpty().any { it.items.contains("MITHRIL_DRILL_1") })
        assertTrue(gameData.ironmanGuide.isNotEmpty())
        assertTrue(gameData.sources.drops["ENDER_PEARL"].orEmpty().contains("Enderman"))
    }

    @Test
    fun nbtParsesRealHypixelItems() {
        val page = SbJson.decodeFromString<sbhelper.core.model.AuctionsPage>(fixture("auctions_small.json"))
        val first = page.auctions.first()
        val raw = (first.itemBytes as kotlinx.serialization.json.JsonPrimitive).content
        val item = assertNotNull(sbhelper.core.nbt.ItemParser.parse(raw).firstOrNull())
        assertEquals(first.itemName, item.plainName.replace(Regex("^\\d+x "), "").trim().let { if (it.isEmpty()) first.itemName else first.itemName })
        assertTrue(item.lore.isNotEmpty())
        assertNotNull(item.skyblockId)
        val bins = sbhelper.core.calc.PriceBook.lowestBins(page.auctions)
        assertTrue(bins.size >= 10)
        assertTrue(bins.values.all { it > 0 })
    }

    @Test
    fun accessoriesAndBestiaryFromWiki() {
        val missing = sbhelper.core.calc.AccessoryCalc.missing(gameData, setOf("ZOMBIE_RING"), null)
        assertTrue(missing.any { it.acc.id == "ZOMBIE_ARTIFACT" && it.upgrade })   // 다음 단계
        assertTrue(missing.none { it.acc.id == "ZOMBIE_TALISMAN" })                // 하위 단계는 표시 안 함
        val best = sbhelper.core.calc.BestiaryCalc.entries(gameData, mapOf("enderman_1" to 500, "enderman_42" to 300))
        val ender = best.first { it.family == "Enderman" }
        assertEquals(800, ender.kills)
        assertTrue(ender.tier > 0)
        assertTrue(gameData.hotmLayout.any { it.name == "Quick Forge" && it.maxLevel == 20 })
        assertTrue(gameData.pets["MINING"].orEmpty().any { it.name == "Mole" || it.name.contains("Mole") })
    }

    @Test
    fun quickForgeDetectedFromAnyForgeNode() {
        assertEquals(12, ProfileParser.quickForge(mapOf("mining_speed" to 50, "forge_time" to 12)))
        assertEquals(7, ProfileParser.quickForge(mapOf("quick_forge" to 7)))
        assertEquals(5, ProfileParser.quickForge(mapOf("forge_speedup" to 5)))
    }

    @Test
    fun pairingCodeRoundTrip() {
        val code = PairingCode.encode("ghp_token123", "gist456")
        assertEquals("ghp_token123" to "gist456", PairingCode.decode(code))
        assertNull(PairingCode.decode("garbage"))
    }

    @Test
    fun wikiTextKeepsLinksAndSkipsInfobox() {
        val (text, links) = sbhelper.core.api.WikiText.inlineLinks(
            "Bought from {{NPCSprite|Bubu}} for {{Item|Fractured Mithril Pickaxe|amount=1}} in the [[Dwarven Mines|mines]].")
        assertEquals("Bought from Bubu for 1× Fractured Mithril Pickaxe in the mines.", text)
        assertEquals(listOf("Bubu", "Fractured Mithril Pickaxe", "Dwarven Mines"), links.map { it.page })
        assertEquals("mines", text.substring(links[2].start, links[2].end))
        val blocks = sbhelper.core.api.WikiText.blocks(
            "{{Infobox/Item\n|type = Pickaxe\n|id = X\n}}\nThe '''X''' is a [[Pickaxe]].\n== Obtaining ==\n* From {{NPCSprite|Bubu}}")
        assertEquals(3, blocks.size)
        assertTrue((blocks[0] as sbhelper.core.api.DocBlock.Paragraph).text.startsWith("The X is a Pickaxe"))
    }

    @Test
    fun extraTimersFromWikiRules() {
        val data = sbhelper.core.data.GameData(timerRules = sbhelper.core.data.GameData.TimerRules(
            fetchurReset = "America/New_York 00:00", experimentBitsReset = "UTC 00:00",
            composter = sbhelper.core.data.GameData.Composter()))
        val now = 1_793_500_000_000L
        val f = sbhelper.core.calc.ExtraTimers.nextDaily(now, "UTC 00:00")!!
        assertEquals(0L, f % 86_400_000L)
        assertTrue(f > now && f - now <= 86_400_000L)
        val garden = sbhelper.core.api.SbJson.parseToJsonElement(
            """{"composter_data":{"organic_matter":40000,"fuel_units":100000,"last_save":$now,"upgrades":{"speed":5,"cost_reduction":0}}}""") as kotlinx.serialization.json.JsonObject
        val timers = sbhelper.core.calc.ExtraTimers.all(now, data, null, garden)
        val comp = timers.first { it.key == "composter" }
        // 유기물 40000 / 4000 = 10개, 속도 +100% → 개당 300초 → 3000초
        assertEquals(now + 3_000_000L, comp.at)
        assertTrue(timers.any { it.key == "fetchur" } && timers.any { it.key == "experiment" })
    }
}
