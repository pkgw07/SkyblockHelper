package sbhelper.core.util

/** 현재 시각(ms). 테스트에서 시간을 바꿀 수 있도록 한 곳에 모아 둔다. */
object Clock {
    var override: (() -> Long)? = null
    fun now(): Long = override?.invoke() ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
