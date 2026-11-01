package sbhelper.core

import kotlin.test.Test

/** 진단용 (WIKI_PROBE=문서1;문서2): 문서별 Obtaining·Usage 절 블록을 출력 */
class WikiObtainProbe {
    @Test fun probe() = kotlinx.coroutines.runBlocking {
        val pages = System.getenv("WIKI_PROBE")?.split(';')?.filter { it.isNotBlank() } ?: return@runBlocking
        val wiki = sbhelper.core.api.WikiApi(sbhelper.core.api.defaultHttpClient())
        val out = StringBuilder()
        for (p in pages) {
            val r = runCatching { wiki.page(p) }
            val blocks = r.getOrNull()
            if (blocks == null) { out.appendLine("## $p 실패 ${r.exceptionOrNull()?.message}"); continue }
            out.appendLine("## $p")
            var cur: String? = null
            blocks.forEach { b ->
                if (b is sbhelper.core.api.DocBlock.Heading && b.level <= 1) cur = b.text
                if (cur in setOf("Obtaining", "Usage", "Uses", "Obtain")) out.appendLine("  [$cur] " + b.toString().take(220))
            }
        }
        java.io.File(System.getenv("PROBE_OUT") ?: "wiki-probe.txt").writeText(out.toString())
    }
}
