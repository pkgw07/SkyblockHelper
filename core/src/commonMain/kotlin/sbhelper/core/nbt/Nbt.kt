package sbhelper.core.nbt

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 마인크래프트 NBT 값 (인벤토리 데이터 형식) */
sealed interface Nbt {
    data class Num(val value: Double) : Nbt
    data class Str(val value: String) : Nbt
    data class Bytes(val value: ByteArray) : Nbt
    data class Ints(val value: IntArray) : Nbt
    data class Longs(val value: LongArray) : Nbt
    data class ListTag(val items: List<Nbt>) : Nbt
    data class Compound(val map: Map<String, Nbt>) : Nbt {
        operator fun get(key: String) = map[key]
        fun compound(key: String) = map[key] as? Compound
        fun string(key: String) = (map[key] as? Str)?.value
        fun number(key: String) = (map[key] as? Num)?.value
        fun list(key: String) = (map[key] as? ListTag)?.items.orEmpty()
    }
}

/** gzip 해제 (플랫폼별) */
expect fun gunzip(data: ByteArray): ByteArray

/** 큰 엔디언 바이너리 NBT 읽기 */
class NbtReader(private val b: ByteArray) {
    private var p = 0
    private fun u8() = b[p++].toInt() and 0xFF
    private fun i16() = ((u8() shl 8) or u8()).toShort().toInt()
    private fun u16() = (u8() shl 8) or u8()
    private fun i32() = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
    private fun i64() = (i32().toLong() shl 32) or (i32().toLong() and 0xFFFFFFFFL)
    private fun str(): String {
        val len = u16()
        val s = b.decodeToString(p, p + len)
        p += len
        return s
    }

    private fun payload(type: Int): Nbt = when (type) {
        1 -> Nbt.Num(b[p++].toDouble())
        2 -> Nbt.Num(i16().toDouble())
        3 -> Nbt.Num(i32().toDouble())
        4 -> Nbt.Num(i64().toDouble())
        5 -> Nbt.Num(Float.fromBits(i32()).toDouble())
        6 -> Nbt.Num(Double.fromBits(i64()))
        7 -> { val n = i32(); val arr = b.copyOfRange(p, p + n); p += n; Nbt.Bytes(arr) }
        8 -> Nbt.Str(str())
        9 -> { val t = u8(); val n = i32(); Nbt.ListTag(List(n) { payload(t) }) }
        10 -> {
            val m = LinkedHashMap<String, Nbt>()
            while (true) {
                val t = u8()
                if (t == 0) break
                val name = str()
                m[name] = payload(t)
            }
            Nbt.Compound(m)
        }
        11 -> { val n = i32(); Nbt.Ints(IntArray(n) { i32() }) }
        12 -> { val n = i32(); Nbt.Longs(LongArray(n) { i64() }) }
        else -> error("NBT 형식 오류: $type")
    }

    /** 이름 있는 루트 태그 */
    fun readRoot(): Nbt {
        val t = u8()
        str()
        return payload(t)
    }

    companion object {
        /** Hypixel API 의 base64(gzip(NBT)) 문자열 → 루트 */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromApi(base64: String): Nbt.Compound? = runCatching {
            NbtReader(gunzip(Base64.decode(base64))).readRoot() as Nbt.Compound
        }.getOrNull()
    }
}
