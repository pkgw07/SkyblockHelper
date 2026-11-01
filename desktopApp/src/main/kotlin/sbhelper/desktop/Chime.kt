package sbhelper.desktop

import sbhelper.core.util.Synth
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** 소리 재생 (소리는 core 의 Synth 로 만든 것) */
object Sound {
    @Volatile private var lastAlert = 0L

    fun play(samples: ShortArray) {
        Thread({
            runCatching {
                val format = AudioFormat(Synth.RATE.toFloat(), 16, 1, true, false)
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format); line.start()
                val bytes = Synth.toBytes(samples)
                line.write(bytes, 0, bytes.size)
                line.drain(); line.close()
            }
        }, "sound").apply { isDaemon = true }.start()
    }

    /** 알림 소리 (알림이 몰려도 1.5초에 한 번) */
    fun alert(tone: Int, volume: Int, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAlert < 1_500) return
        lastAlert = now
        play(Synth.alert(tone, volume / 100f))
    }
}
