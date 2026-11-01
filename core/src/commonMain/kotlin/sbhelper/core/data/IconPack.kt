package sbhelper.core.data

import kotlinx.serialization.Serializable
import sbhelper.core.api.SbJson

/**
 * 아이템 아이콘 묶음 (매일 CI 가 공식 위키 그림으로 만들어 data 브랜치에 올림, tools/icon_pack.py).
 * 한 번 받아 두면 인터넷 없이 바로 아이콘을 그린다.
 */
object IconPack {
    @Serializable
    data class Index(val version: String = "", val items: Map<String, List<Int>> = emptyMap(), val names: Map<String, String> = emptyMap())

    @kotlin.concurrent.Volatile private var blob: ByteArray? = null
    @kotlin.concurrent.Volatile private var index: Index = Index()

    val version: String get() = index.version
    val size: Int get() = index.items.size

    fun load(indexJson: String?, bytes: ByteArray?): Boolean {
        if (indexJson == null || bytes == null) return false
        val idx = runCatching { SbJson.decodeFromString(Index.serializer(), indexJson) }.getOrNull() ?: return false
        if (idx.items.values.any { it.size != 2 || it[0] + it[1] > bytes.size }) return false
        blob = bytes
        index = idx
        return true
    }

    /** 아이템 ID 로, 없으면 이름(위키 문서 제목)으로 PNG 바이트 */
    fun png(id: String?, name: String?): ByteArray? {
        val b = blob ?: return null
        val key = id?.takeIf { it in index.items } ?: name?.lowercase()?.trim()?.let { index.names[it] } ?: return null
        val (off, len) = index.items[key] ?: return null
        return b.copyOfRange(off, off + len)
    }
}
