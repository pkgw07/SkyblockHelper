package sbhelper.core.util

import kotlin.math.abs
import kotlin.math.roundToLong

private val COLOR_CODE = Regex("§.")

/** 마인크래프트 색상 코드(§a, §l …)를 제거한다. */
fun String.stripColor(): String = COLOR_CODE.replace(this, "")

/** Hypixel API 설명문의 %%gray%% 같은 표기를 § 코드로 바꾼다. */
fun String.percentColorsToSection(): String = replace(Regex("%%([a-z_]+)%%")) { m ->
    val code = when (m.groupValues[1]) {
        "black" -> "0"; "dark_blue" -> "1"; "dark_green" -> "2"; "dark_aqua" -> "3"
        "dark_red" -> "4"; "dark_purple" -> "5"; "gold" -> "6"; "gray" -> "7"
        "dark_gray" -> "8"; "blue" -> "9"; "green" -> "a"; "aqua" -> "b"
        "red" -> "c"; "light_purple" -> "d"; "yellow" -> "e"; "white" -> "f"
        "bold" -> "l"; "italic" -> "o"; "reset" -> "r"
        else -> "r"
    }
    "§$code"
}

/** 1234 -> 1,234 / 1_234_567 -> 1.23M */
fun formatCoins(value: Double?): String {
    if (value == null || value.isNaN()) return "-"
    val sign = if (value < 0) "-" else ""
    val v = abs(value)
    return sign + when {
        v >= 1e9 -> "${fixed(v / 1e9, 2)}B"
        v >= 1e6 -> "${fixed(v / 1e6, 2)}M"
        v >= 1e4 -> "${fixed(v / 1e3, 1)}K"
        v >= 100 -> groupThousands(v.roundToLong())
        v % 1.0 == 0.0 -> v.roundToLong().toString()
        else -> fixed(v, 1)
    }
}

fun formatNumber(value: Long): String = (if (value < 0) "-" else "") + groupThousands(abs(value))

fun formatPercent(value: Double, digits: Int = 1): String = "${fixed(value * 100, digits)}%"

fun fixed(value: Double, digits: Int): String {
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val rounded = (abs(value) * factor).roundToLong()
    val whole = rounded / factor
    val frac = rounded % factor
    val sign = if (value < 0 && rounded != 0L) "-" else ""
    return if (digits == 0) "$sign${groupThousands(whole)}"
    else "$sign${groupThousands(whole)}.${frac.toString().padStart(digits, '0')}"
}

private fun groupThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

/** 남은 시간을 사람이 읽기 좋게. 예: 3일 4시간 / 2시간 5분 / 4분 09초 */
fun formatDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    val d = total / 86400
    val h = (total % 86400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        d > 0 -> "${d}일 ${h}시간"
        h > 0 -> "${h}시간 ${m}분"
        m > 0 -> "${m}분 ${s.toString().padStart(2, '0')}초"
        else -> "${s}초"
    }
}

/** ENCHANTED_COAL -> Enchanted Coal */
fun prettifyId(id: String): String =
    id.substringBefore(':').split('_').joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { it.uppercase() }
    }

fun toRoman(n: Int): String {
    if (n <= 0) return "0"
    val numerals = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var rem = n
    val sb = StringBuilder()
    for ((v, s) in numerals) while (rem >= v) { sb.append(s); rem -= v }
    return sb.toString()
}
