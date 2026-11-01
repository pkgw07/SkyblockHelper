package sbhelper.core

import kotlin.test.Test

/** 진단용 (LIVE_TRANSLATE=1 일 때만): 실제 최신 패치노트를 앱과 같은 방식으로 번역해 보고 결과를 센다 */
class TranslateDiag {
    @Test fun diag() = kotlinx.coroutines.runBlocking {
        if (System.getenv("LIVE_TRANSLATE") != "1") return@runBlocking
        val wiki = sbhelper.core.api.WikiApi()
        val tr = sbhelper.core.api.Translator()
        val out = StringBuilder()
        val notes = wiki.patchNotes(2026).take(3)
        for (n in notes) {
            val blocks = wiki.page(n.page)
            val lines = listOf(n.title) + blocks.map { b ->
                when (b) {
                    is sbhelper.core.api.DocBlock.Heading -> b.text
                    is sbhelper.core.api.DocBlock.Paragraph -> b.text
                    is sbhelper.core.api.DocBlock.Bullet -> b.text
                    is sbhelper.core.api.DocBlock.Image -> ""
                }
            }
            val t0 = System.currentTimeMillis()
            val res = tr.toKorean(lines)
            val sec = (System.currentTimeMillis() - t0) / 1000
            val nonBlank = lines.indices.filter { lines[it].isNotBlank() }
            val missing = nonBlank.filter { res[it] == null }
            val same = nonBlank.filter { res[it] != null && res[it] == lines[it] && lines[it].any { c -> c.isLetter() } }
            val firstMissing = missing.firstOrNull()
            out.append("[${n.page}] 줄 ${nonBlank.size}, 번역 안 됨 ${missing.size}, 원문 그대로 ${same.size}, ${sec}초, " +
                "총 글자 ${lines.sumOf { it.length }}, 막힘=${tr.blockedUntilMs > 0}, 첫 실패 줄 #$firstMissing" +
                (firstMissing?.let { " \"${lines[it].take(60)}\"" } ?: "") +
                (same.firstOrNull()?.let { " / 그대로 예: \"${lines[it].take(60)}\"" } ?: "") + "\n")
        }
        java.io.File("translate-diag.txt").writeText(out.toString())
        println(out)
    }
}
