package sbhelper.core.util

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 소리 파일 없이 만드는 짧은 소리 (16bit mono PCM, 44.1kHz). PC·휴대폰이 같이 쓴다.
 * 알림음 종류: 0 차임, 1 경험치 구슬, 2 노트 블록, 3 짧은 삐
 */
object Synth {
    const val RATE = 44_100
    val TONES = listOf("차임", "경험치 구슬", "노트 블록", "짧은 삐")

    private fun pcm(seconds: Double, volume: Float, f: (t: Double) -> Double): ShortArray {
        val n = (RATE * seconds).toInt()
        val v = volume.coerceIn(0f, 1f)
        return ShortArray(n) { i ->
            val s = f(i.toDouble() / RATE) * v
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun note(t: Double, start: Double, len: Double, freq: Double, decay: Double, harmonics: Double = 0.2): Double {
        val x = t - start
        if (x < 0 || x > len) return 0.0
        val env = min(1.0, x / 0.008) * exp(-decay * x)
        return (sin(2 * PI * freq * x) + harmonics * sin(4 * PI * freq * x)) * env
    }

    /** 알림음. @param volume 0..1 */
    fun alert(tone: Int, volume: Float): ShortArray = when (tone) {
        // 경험치 구슬: 높고 맑은 '띵' 두 번 (음높이 살짝 다르게)
        1 -> pcm(0.45, volume) { t -> 0.42 * (note(t, 0.0, 0.25, 1568.0, 14.0, 0.05) + note(t, 0.12, 0.3, 1865.0, 12.0, 0.05)) }
        // 노트 블록 (플링): 세 음 올라가기
        2 -> pcm(0.6, volume) { t -> 0.38 * (note(t, 0.0, 0.3, 784.0, 9.0, 0.5) + note(t, 0.11, 0.3, 988.0, 9.0, 0.5) + note(t, 0.22, 0.38, 1175.0, 7.0, 0.5)) }
        // 짧은 삐 두 번
        3 -> pcm(0.32, volume) { t -> 0.32 * ((if (t < 0.09 || t in 0.16..0.25) sin(2 * PI * 1760 * t) else 0.0) * min(1.0, (t % 0.16) / 0.005)) }
        // 차임: 두 음
        else -> pcm(0.42, volume) { t -> 0.4 * (note(t, 0.0, 0.16, 988.0, 18.0) + note(t, 0.13, 0.29, 1319.0, 9.0)) }
    }

    /** 폭발음 (1.9.1 의 처음 소리: 둔탁한 저음 노이즈가 '펑' 하고 퍼지며 사라짐) */
    fun explosion(volume: Float, seed: Int = 7): ShortArray {
        val rnd = Random(seed)
        var low = 0.0; var low2 = 0.0
        return pcm(1.1, volume) { t ->
            val noise = rnd.nextDouble() * 2 - 1
            low += (noise - low) * 0.08
            low2 += (low - low2) * 0.15
            val env = min(1.0, t / 0.006) * exp(-4.2 * t)
            (low2 * 3.2 + noise * 0.06 * exp(-9 * t)) * env * 0.9
        }
    }

    /** 순간이동 '뿅' (엔더맨 텔레포트처럼 아래로 미끄러지는 소리) */
    fun warp(volume: Float): ShortArray {
        var phase = 0.0
        return pcm(0.32, volume) { t ->
            val f = 900.0 * exp(-5.5 * t) + 180
            phase += 2 * PI * f / RATE
            val wobble = sin(2 * PI * 38 * t) * 0.35
            0.3 * (sin(phase) + 0.4 * sin(phase * 2.01 + wobble)) * min(1.0, t / 0.01) * exp(-6 * t)
        }
    }

    /** 여러 소리를 시차를 두고 섞기 (예: 텔레포트 → 폭발) */
    fun mix(vararg parts: Pair<ShortArray, Double>): ShortArray {
        val len = parts.maxOf { (s, at) -> (at * RATE).toInt() + s.size }
        val acc = IntArray(len)
        parts.forEach { (s, at) -> val off = (at * RATE).toInt(); s.forEachIndexed { i, v -> acc[off + i] += v.toInt() } }
        return ShortArray(len) { acc[it].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    /** 이스터에그 소리 = 처음 폭발음 */
    fun witherImpact(volume: Float, seed: Int): ShortArray = explosion(volume)

    fun toBytes(s: ShortArray): ByteArray {
        val b = ByteArray(s.size * 2)
        s.forEachIndexed { i, v -> b[i * 2] = (v.toInt() and 0xFF).toByte(); b[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte() }
        return b
    }
}
