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

/** 이스터에그 폭발음: 저음이 섞인 노이즈가 "펑" 하고 퍼지며 사라짐 (마인크래프트 폭발 느낌) */
object Boom {
    fun play() {
        Thread({ runCatching { playBlocking() } }, "boom").apply { isDaemon = true }.start()
    }

    private fun playBlocking() {
        val rate = 44_100f
        val sec = 1.1
        val n = (rate * sec).toInt()
        val bytes = ByteArray(n * 2)
        val rnd = java.util.Random(7)
        var low = 0.0; var low2 = 0.0
        for (i in 0 until n) {
            val t = (i / rate).toDouble()
            val noise = rnd.nextDouble() * 2 - 1
            // 두 번 거른 노이즈 = 둔탁한 저음 + 약간의 지글거림
            low += (noise - low) * 0.08
            low2 += (low - low2) * 0.15
            val attack = minOf(1.0, t / 0.006)
            val env = attack * Math.exp(-4.2 * t)
            val v = (low2 * 3.2 + noise * 0.06 * Math.exp(-9 * t)) * env * 0.9
            val s = (v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[i * 2] = (s and 0xFF).toByte(); bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        val format = javax.sound.sampled.AudioFormat(rate, 16, 1, true, false)
        val line = javax.sound.sampled.AudioSystem.getSourceDataLine(format)
        line.open(format); line.start()
        line.write(bytes, 0, bytes.size)
        line.drain(); line.close()
    }
}
