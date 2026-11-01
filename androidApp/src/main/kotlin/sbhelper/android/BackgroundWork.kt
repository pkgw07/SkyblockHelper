package sbhelper.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sbhelper.core.AppState
import sbhelper.core.notify.AlertEngine
import sbhelper.core.notify.AlertInputs
import sbhelper.core.util.Clock
import java.util.concurrent.TimeUnit

/**
 * 휴대폰 백그라운드 확인.
 * - 15분마다(안드로이드 최소 주기) 프로필/경매/가격을 확인
 * - 포지 완료·이벤트처럼 시각을 아는 알림은 그 시각에 맞춰 한 번 더 깨어난다
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SbApplication
        app.ensureLoaded()
        if (!app.repo.state.value.device.runInBackground) return Result.success()
        val next = runCatching { app.repo.backgroundTick() }.getOrNull()
        next?.let { BackgroundScheduler.scheduleAt(applicationContext, it) }
        AlertAlarm.reschedule(applicationContext)
        return Result.success()
    }
}

object BackgroundScheduler {
    private const val PERIODIC = "sbh-periodic"
    private const val NEXT = "sbh-next"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        } else {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(NEXT)
        }
    }

    fun scheduleAt(context: Context, time: Long) {
        val delay = (time - Clock.now()).coerceAtLeast(0) + 5_000 // 완료 직후 확인
        WorkManager.getInstance(context).enqueueUniqueWork(
            NEXT, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setConstraints(network).build(),
        )
    }

    fun scheduleNextFromState(context: Context, state: AppState) {
        val now = Clock.now()
        val inputs = AlertInputs(state.selectedProfile?.profileId, state.forgeStatuses(), state.upcomingEvents(now),
            state.myAuctions, state.bazaar?.products.orEmpty(), state.synced.priceAlerts, state::itemName)
        AlertEngine.nextWakeUp(inputs, state.synced.notify, now)?.let { scheduleAt(context, it) }
    }
}
