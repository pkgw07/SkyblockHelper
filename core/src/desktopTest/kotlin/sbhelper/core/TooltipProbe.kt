package sbhelper.core

import kotlin.test.Test

/** 진단용 (TOOLTIP_PROBE=문서|아이템;…): 실제 위키 문서의 툴팁 줄 */
class TooltipProbe {
    @Test fun probe() = kotlinx.coroutines.runBlocking {
        val list = System.getenv("TOOLTIP_PROBE")?.split(';')?.filter { it.isNotBlank() } ?: return@runBlocking
        val wiki = sbhelper.core.api.WikiApi(sbhelper.core.api.defaultHttpClient())
        val out = StringBuilder()
        for (e in list) {
            val (page, item) = e.split('|').let { it[0] to it.getOrNull(1) }
            val t = runCatching { wiki.itemTooltip(page, item) }.getOrNull()
            out.appendLine("## $page / $item"); t?.lines?.forEach { out.appendLine("  " + it) } ?: out.appendLine("  (없음)")
        }
        java.io.File(System.getenv("PROBE_OUT") ?: "tooltip.txt").writeText(out.toString())
    }
}
