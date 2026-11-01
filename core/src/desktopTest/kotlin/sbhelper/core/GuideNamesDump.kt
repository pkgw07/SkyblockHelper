package sbhelper.core

import kotlin.test.Test

/** 진단용 (GUIDE_NAMES=파일): 가이드에 나오는 이름 목록 */
class GuideNamesDump {
    @Test fun dump() {
        val out = System.getenv("GUIDE_NAMES") ?: return
        java.io.File(out).writeText(sbhelper.core.data.SkillGuide.allNames().sorted().joinToString("\n"))
    }
}
