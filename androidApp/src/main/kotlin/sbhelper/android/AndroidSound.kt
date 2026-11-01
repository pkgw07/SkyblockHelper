package sbhelper.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import sbhelper.core.util.Synth

/** 휴대폰 소리 재생 (core Synth 로 만든 소리). 알림 소리는 '알림 음량'을 따른다 */
object AndroidSound {
    @Volatile private var lastAlert = 0L

    fun play(samples: ShortArray, usage: Int = AudioAttributes.USAGE_GAME) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(Synth.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }

    fun alert(tone: Int, volume: Int, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAlert < 1_500) return
        lastAlert = now
        play(Synth.alert(tone, volume / 100f), AudioAttributes.USAGE_NOTIFICATION)
    }
}
