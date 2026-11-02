package sbhelper.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import sbhelper.core.api.SbJson

/** 앱 업데이트 내역 (일반 사용자용, 짧게). 앱 안 files/changelog.json */
@Serializable
data class ChangelogEntry(val version: String, val items: List<String> = emptyList())

object Changelog {
    fun parse(text: String): List<ChangelogEntry> =
        runCatching { SbJson.decodeFromString(ListSerializer(ChangelogEntry.serializer()), text) }.getOrDefault(emptyList())
}
