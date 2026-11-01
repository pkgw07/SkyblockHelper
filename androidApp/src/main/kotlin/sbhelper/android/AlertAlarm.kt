package sbhelper.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.launch
import sbhelper.core.util.Clock

/**
 * 휴대폰 정시 알림.
 * 포지 완료·이벤트 시작 전·타이머 시각은 저장된 정보로 미리 알 수 있으므로, 그 시각에 정확히 깨어나(AlarmManager)
 * 인터넷 없이 바로 알림을 띄우고 다음 시각을 다시 예약한다.
 * (WorkManager 는 절전 상태에서 수십 분 늦어질 수 있어서 정시 알림에는 쓰지 않는다)
 */
object AlertAlarm {
    private const val ACTION = "sbhelper.android.ALERT_ALARM"

    private fun pending(context: Context, flags: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(context, 7001, Intent(context, AlertAlarmReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or flags)

    /** 설정에 맞게 다음 알람을 예약하거나 취소 */
    fun reschedule(context: Context) {
        val app = context.applicationContext as SbApplication
        app.scope.launch {
            app.ensureLoaded()
            val d = app.repo.state.value.device
            if (!d.phoneExactAlerts || !d.notificationsOnThisDevice) { cancel(context); return@launch }
            val at = app.repo.nextAlertAt()
            if (at == null) cancel(context) else schedule(context, at + 2_000)
        }
    }

    fun schedule(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val time = at.coerceAtLeast(Clock.now() + 1_000)
        // Android 12+: 정확한 알람 권한 (13+ 는 USE_EXACT_ALARM 으로 자동 허용). 없으면 조금 덜 정확한 방식으로
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun cancel(context: Context) {
        val pi = pending(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
    }
}

/** 알람이 울리면: 알림 확인 → 띄우기 → 다음 시각 예약 */
class AlertAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SbApplication
        val done = goAsync()
        app.scope.launch {
            try {
                app.ensureLoaded()
                val d = app.repo.state.value.device
                if (d.phoneExactAlerts && d.notificationsOnThisDevice) app.repo.checkAlerts()
                val at = app.repo.nextAlertAt()
                if (d.phoneExactAlerts && at != null) AlertAlarm.schedule(context, at + 2_000)
            } finally {
                done.finish()
            }
        }
    }
}

/** 재부팅·앱 업데이트 뒤 다시 예약 */
class AlertBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) AlertAlarm.reschedule(context)
    }
}
