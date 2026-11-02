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
import sbhelper.core.data.isQuiet
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
        assertEquals(sbhelper.core.api.DocBlock.Paragraph("hello b", listOf(sbhelper.core.api.WikiLink(6, 7, "A"))), blocks[3])
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

    @Test
    fun wardrobeFromLoadoutAndDungeonRuns() {
        // 실제 프로필과 같은 위치 (2026-11 진단으로 확인): member.loadout.armor.<n>.<SLOT>.data
        val member = SbJson.parseToJsonElement("""{
            "loadout":{"armor":{"equipped_set":2,"1":{"HELMET":{"type":0,"data":"x"},"BOOTS":{"data":"y"}},"2":{"CHESTPLATE":{"data":"z"}}},
                       "equipment":{"1":{"EQUIPMENT_SLOT_1":{"data":"e"}}},
                       "loadouts":{"1":{"name":"Dungeon","armor_set_id":2,"equipment_set_id":1}}},
            "dungeons":{"dungeon_types":{"catacombs":{"tier_completions":{"7":10},"times_played":{"7":12,"6":3}}}}
        }""").jsonObject
        val w = assertNotNull(ProfileParser.wardrobe(member))
        assertEquals(listOf("x", null, null, "y"), w.armor[1])
        assertEquals(listOf(null, "z", null, null), w.armor[2])
        assertEquals(2, w.equippedArmor)
        assertEquals("e", w.equipment[1]?.first())
        assertEquals(2, w.loadouts.single().armorSet)
        val d = assertNotNull(sbhelper.core.calc.ProfileDetailsCalc.compute(member, null, GameData()))
        val f7 = d.floors.first { it.floor == 7 && !it.master }
        assertEquals(10, f7.completions); assertEquals(12, f7.timesPlayed)
        assertEquals(0, d.floors.first { it.floor == 6 }.completions)
    }

    @Test
    fun wikiTemplatesAndRequirements() {
        val (t1, l1) = sbhelper.core.api.WikiText.inlineLinks("hits {{MobSprite|Enderman;Endermen}} with {{stat|fuel|3,000}} and {{Item|Rough Onyx Gemstone|text=Onyx}} {{DRL|/Divan's Drill/}}")
        assertEquals("hits Endermen with 3,000 Fuel and Onyx", t1)
        assertEquals(listOf("Enderman", "Rough Onyx Gemstone"), l1.map { it.page })
        // 문장 중간에서 시작하는 여러 줄 틀의 안쪽(|A1 = …)은 본문에 섞이지 않는다
        val blocks = sbhelper.core.api.WikiText.blocks("Made here {{Crafting Recipe Table\n|A1 = Diamond, 32\n|Output = X\n}}\nAfter.")
        assertEquals(listOf("Made here", "After."), blocks.map { (it as sbhelper.core.api.DocBlock.Paragraph).text })
        val b2 = sbhelper.core.api.WikiText.blocks("{{Redirect|Term|the devices|Terminal}}\nReal text.\n<!--* hidden\n* also hidden-->\n* Shown")
        assertEquals(listOf("Real text.", "Shown"), b2.map { when (it) { is sbhelper.core.api.DocBlock.Paragraph -> it.text; is sbhelper.core.api.DocBlock.Bullet -> it.text; else -> "?" } })
        val st = AppState()
        assertEquals("Diamond 컬렉션 IV 단계", sbhelper.core.calc.Requirements.recipe(st, "[[Diamond#IV|Diamond IV (4)]]")?.text)
        assertEquals("Zombie Slayer 5레벨", sbhelper.core.calc.Requirements.recipe(st, "[[Zombie Slayer#V|Zombie Slayer V (5)]]")?.text)
        val r = sbhelper.core.calc.Requirements.item(st, SbJson.parseToJsonElement("""{"type":"DUNGEON_TIER","dungeon_type":"CATACOMBS","tier":7}""").jsonObject)
        assertEquals("카타콤 F7 클리어", r.text)
    }

    @Test
    fun guideConditions() {
        val r = sbhelper.core.calc.Requirements.parseGuide("Fishing 24, Lily Pad VII") { it == "Lily Pad" }
        assertEquals(listOf("SKILL", "COLLECTION"), r.map { it["type"].toString().trim('"') })
        val t = sbhelper.core.calc.Requirements.parseGuide("Enderman Slayer VII, Cata 27") { false }
        assertEquals(listOf("SLAYER", "DUNGEON_SKILL"), t.map { it["type"].toString().trim('"') })
        assertEquals(1, sbhelper.core.calc.Requirements.parseGuide("F7 클리어") { false }.size)
        assertEquals(0, sbhelper.core.calc.Requirements.parseGuide("Necron VI, 해당 보스 층에서 사용") { false }.size)
        // 가이드에 나오는 이름이 300개 이상, 모든 스킬에 탭이 있음
        assertTrue(sbhelper.core.data.SkillGuide.allNames().size > 300)
        assertTrue(sbhelper.core.data.SkillGuide.skills.all { it.tabs.isNotEmpty() })
    }

    @Test
    fun composterStoppedOnce() {
        val data = sbhelper.core.data.GameData(timerRules = sbhelper.core.data.GameData.TimerRules(composter = sbhelper.core.data.GameData.Composter()))
        val now = 1_793_500_000_000L
        val empty = SbJson.parseToJsonElement(
            """{"composter_data":{"organic_matter":100,"fuel_units":0,"last_save":${now - 3_600_000},"upgrades":{}}}""").jsonObject
        val t = sbhelper.core.calc.ExtraTimers.all(now, data, null, empty).first { it.key == "composter" }
        assertTrue(t.done)
        val prefs = NotifyPrefs()
        val inputs = AlertInputs(null, emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), { it }, extraTimers = listOf(t))
        val first = AlertEngine.evaluate(inputs, prefs, emptySet(), now).notifications
        assertEquals(1, first.size)
        // 같은 멈춤은 다시 알리지 않음
        assertEquals(0, AlertEngine.evaluate(inputs, prefs, first.map { it.key }.toSet(), now + 60_000).notifications.size)
    }

    @Test
    fun wikiTooltipAndCraftCost() {
        val aote = """{{ItemInfo
|type = Sword
|rarity = r
|damage = 100
|strength = 100
|item_ability_name = Instant Transmission
|item_ability_description = Teleports you {{Green|8}} blocks ahead.
|item_ability_activation = Right Click
|item_ability_mana_cost = 50
}}"""
        val t = assertNotNull(sbhelper.core.api.WikiItemInfo.parse(aote, null))
        assertTrue("§7Damage: §c+100" in t.lines, t.lines.toString())
        assertTrue(t.lines.any { it.startsWith("§6Ability: Instant Transmission") })
        assertTrue(t.lines.any { it.contains("§a8§7") })
        assertTrue(t.lines.last().contains("RARE SWORD"))
        val set = """{{SetInfo
|{{SetInfoPiece
 |type = Chestplate
 |name = Necron's Chestplate
 |rarity = l
 |strength = 40
 |crit_damage = 30
 }}
|name = Necron's Armor
|rarity = l
|full_set_bonus_name = Witherborn
|full_set_bonus_description = Spawns a wither.
}}"""
        val n = assertNotNull(sbhelper.core.api.WikiItemInfo.parse(set, "Necron's Chestplate"))
        assertTrue("§7Crit Damage: §c+30%" in n.lines, n.lines.toString())
        assertTrue(n.lines.any { it == "§6Full Set Bonus: Witherborn" })
        // 제작 비용: A = 2 B + 1 C(소울바운드, 가격 없음), B = 3 D. B 는 사면 10, D 는 2 → B 를 만드는 게 쌈(6)
        val data = GameData(crafting = mapOf(
            "A" to listOf(GameData.CraftRecipe(mapOf("B" to 2.0, "C" to 1.0))),
            "B" to listOf(GameData.CraftRecipe(mapOf("D" to 3.0))),
        ))
        val price = mapOf("B" to 10.0, "D" to 2.0)
        val d = assertNotNull(sbhelper.core.calc.CraftCost.direct(data, "A") { price[it] })
        assertEquals(20.0, d.total); assertEquals(listOf("C"), d.unpriced.map { it.id })
        val c = assertNotNull(sbhelper.core.calc.CraftCost.cheapest(data, "A") { price[it] })
        assertEquals(12.0, c.total); assertEquals(listOf("D"), c.buy.map { it.id }); assertEquals(listOf("C"), c.unpriced.map { it.id })
    }

    @Test
    fun priceHistoryAndChangelog() {
        var h = sbhelper.core.calc.PriceHistory().track("A")
        val p = { _: String -> 10.0 to 8.0 }
        h = h.record(0, listOf("A"), p)
        h = h.record(60_000, listOf("A"), p) // 10분 안 → 그대로
        assertEquals(1, h.series["A"]!!.size)
        h = h.record(sbhelper.core.calc.PriceHistory.STEP, listOf("A"), p)
        assertEquals(2, h.series["A"]!!.size)
        val log = sbhelper.core.data.Changelog.parse(File("../ui/src/commonMain/composeResources/files/changelog.json").readText())
        assertTrue(log.isNotEmpty() && log.first().items.isNotEmpty())
        assertTrue(!sbhelper.core.data.DeviceSettings().isQuiet(0)); assertTrue(sbhelper.core.data.DeviceSettings(quietEnabled = true, quietStartHour = 0, quietEndHour = 24).isQuiet(0))
    }

    @Test
    fun craftProgressAndToday() {
        // A = 2 B + 1 C, B = 3 D. D 2코인, C 6코인 → 전체 2*3*2 + 6 = 18
        val data = GameData(crafting = mapOf(
            "A" to listOf(GameData.CraftRecipe(mapOf("B" to 2.0, "C" to 1.0))),
            "B" to listOf(GameData.CraftRecipe(mapOf("D" to 3.0))),
        ))
        val price = mapOf("D" to 2.0, "C" to 6.0)
        val p = CraftPlanner(data)
        assertEquals(0.0, p.progress("A", 1, emptyMap()) { price[it] })
        // 중간 재료 B 하나 = D 3개 = 6코인 → 6/18
        assertEquals(1.0 / 3, p.progress("A", 1, mapOf("B" to 1L)) { price[it] }, 1e-9)
        assertEquals(1.0, p.progress("A", 1, mapOf("B" to 2L, "C" to 1L)) { price[it] }, 1e-9)
        // 기본 재료 표시: D 6개 필요, B(=D 3개) 1개 가짐 → D 3/6
        val raw = p.rawProgress("A", 1, mapOf("B" to 1L))
        assertEquals(6.0, raw.first { it.itemId == "D" }.needed); assertEquals(3.0, raw.first { it.itemId == "D" }.have)
        // 오늘 할 일: 체크 → 초기화 전까지 완료
        val rules = GameData(timerRules = GameData.TimerRules(fetchurReset = "UTC 00:00"))
        val now = 1_700_000_000_000L
        var s = sbhelper.core.data.SyncedSettings()
        val item = sbhelper.core.calc.Today.items(now, rules, s, emptyList(), emptyList(), null).first { it.key == "fetchur" }
        assertTrue(!item.done)
        s = sbhelper.core.calc.Today.toggle(s, item, now)
        assertTrue(sbhelper.core.calc.Today.items(now + 1000, rules, s, emptyList(), emptyList(), null).first { it.key == "fetchur" }.done)
        assertTrue(!sbhelper.core.calc.Today.items(now + 86_400_000L, rules, s, emptyList(), emptyList(), null).first { it.key == "fetchur" }.done)
    }

    @Test
    fun minionProfitFromWikiData() {
        val data = GameData.parse(File("../ui/src/commonMain/composeResources/files/gamedata.json").readText())
        val cobble = data.minions.first { it.name == "Cobblestone" }
        assertEquals(12, cobble.tiers.size)
        // Cobblestone I: 14초, 2행동 → 하루 86400/28 = 3085.7개
        val none = sbhelper.core.calc.MinionSetup()
        val r = assertNotNull(sbhelper.core.calc.MinionProfit.tier(cobble, 1, none, sbhelper.core.calc.SellTo.NPC, { null }, { 1.0 }, { null }))
        assertEquals(86400.0 / 28, r.outputs.first { it.id == "COBBLESTONE" }.perDay, 1e-6)
        // +25% 속도 연료
        val lava = data.minionFuels.first { it.id == "ENCHANTED_LAVA_BUCKET" }
        val r2 = assertNotNull(sbhelper.core.calc.MinionProfit.tier(cobble, 1, none.copy(fuel = lava), sbhelper.core.calc.SellTo.NPC, { null }, { 1.0 }, { null }))
        assertEquals(86400.0 / 28 * 1.25, r2.outputs.first().perDay, 1e-6)
        // 다음 단계(II): Cobblestone 160개 → 하루 3085개면 0.05일
        val next = assertNotNull(sbhelper.core.calc.MinionProfit.nextTier(data, cobble, 1, r, 1, emptyMap()))
        assertEquals(160.0, next.needs.first { it.id == "COBBLESTONE" }.need)
        assertTrue((next.days ?: 99.0) < 0.1)
        // V 단계: Enchanted Cobblestone 8개 = Cobblestone 1280개로 분해
        val r4 = assertNotNull(sbhelper.core.calc.MinionProfit.tier(cobble, 4, none, sbhelper.core.calc.SellTo.NPC, { null }, { 1.0 }, { null }))
        val n5 = assertNotNull(sbhelper.core.calc.MinionProfit.nextTier(data, cobble, 4, r4, 1, emptyMap()))
        assertEquals(1280.0, n5.needs.first { it.id == "COBBLESTONE" }.need)
        assertEquals(7, sbhelper.core.calc.MinionProfit.craftedTier(cobble, listOf("COBBLESTONE_3", "COBBLESTONE_7", "WHEAT_9")))
    }
}
