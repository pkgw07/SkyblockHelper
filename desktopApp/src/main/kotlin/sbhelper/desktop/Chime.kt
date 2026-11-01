package sbhelper.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** 알림 소리: 짧은 두 음 차임 (소리 파일 없이 만들어 재생) */
object Chime {
    @Volatile private var lastPlay = 0L

    fun play() {
        val now = System.currentTimeMillis()
        if (now - lastPlay < 1_500) return // 알림이 몰려도 한 번만
        lastPlay = now
        Thread({ runCatching { playBlocking() } }, "chime").apply { isDaemon = true }.start()
    }

    private fun playBlocking() {
        val rate = 44_100f
        val notes = listOf(988.0 to 0.13, 1319.0 to 0.22) // B5 → E6
        val samples = ArrayList<Byte>()
        for ((freq, sec) in notes) {
            val n = (rate * sec).toInt()
            for (i in 0 until n) {
                val t = i / rate
                // 부드럽게 시작하고 사라지게
                val env = minOf(1.0, i / (rate * 0.01)) * Math.exp(-3.5 * t / sec)
                val v = (Math.sin(2 * Math.PI * freq * t) * 0.35 + Math.sin(4 * Math.PI * freq * t) * 0.08) * env
                val s = (v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                samples += (s and 0xFF).toByte(); samples += ((s shr 8) and 0xFF).toByte()
            }
        }
        val format = AudioFormat(rate, 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()
        val bytes = samples.toByteArray()
        line.write(bytes, 0, bytes.size)
        line.drain()
        line.close()
    }
}
