package sbhelper.core

import kotlinx.coroutines.runBlocking
import sbhelper.core.api.HypixelApi
import sbhelper.core.calc.SkyblockCalendar
import kotlin.test.Test
import kotlin.test.assertTrue

/** 실제 Hypixel API 호출 확인. LIVE_API=1 일 때만 실행 (CI 나 인터넷 연결된 PC). */
class LiveApiTest {
    private val enabled = System.getenv("LIVE_API") == "1"

    @Test
    fun publicEndpointsWork() = runBlocking {
        if (!enabled) return@runBlocking
        val api = HypixelApi()
        val bazaar = api.bazaar()
        assertTrue(bazaar.products.size > 100)
        val items = api.items().items
        assertTrue(items.size > 1000)
        val skills = api.skills().skills
        assertTrue("FARMING" in skills)
        val election = api.election()
        assertTrue(election.mayor?.name?.isNotBlank() == true)
        val page = api.auctionsPage(0)
        assertTrue(page.totalPages > 0)
        val mojang = api.uuidFor("Technoblade")
        assertTrue(mojang.id.length == 32)
        // 공식 위키: 최신 패치노트와 본문
        val wiki = sbhelper.core.api.WikiApi()
        val notes = wiki.patchNotes(2026)
        assertTrue(notes.isNotEmpty())
        val body = wiki.page(notes.first().page)
        assertTrue(body.isNotEmpty())
        println("LIVE OK: bazaar=${bazaar.products.size} items=${items.size} skills=${skills.keys} " +
            "mayor=${election.mayor?.name} auctionPages=${page.totalPages} " +
            "sbDate=${SkyblockCalendar.dateAt(System.currentTimeMillis())} latestPatch=${notes.first().title} blocks=${body.size}")
    }
}
