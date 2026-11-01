package sbhelper.android

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sbhelper.core.SkyblockRepository
import sbhelper.core.api.HypixelApi
import sbhelper.core.api.defaultHttpClient
import sbhelper.core.data.KeyValueStorage
import sbhelper.core.notify.AppNotification
import sbhelper.core.notify.Notifier
import sbhelper.core.sync.GistSync
import sbhelper.ui.generated.Res
import java.io.File

/** 앱 전체에서 하나만 쓰는 저장소(Repository)를 들고 있는다. 화면과 백그라운드 작업이 공유한다. */
class SbApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: SkyblockRepository
        private set
    private val loadLock = Mutex()
    private var loaded = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val http = defaultHttpClient()
        var ref: SkyblockRepository? = null
        repo = SkyblockRepository(
            api = HypixelApi(http) { ref?.state?.value?.synced?.apiKey },
            gistSync = GistSync(http),
            storage = AndroidStorage(filesDir),
            notifier = AndroidNotifier(this),
            scope = scope,
            loadGameData = { Res.readBytes("files/gamedata.json").decodeToString() },
            appVersion = sbhelper.ui.AppInfo.VERSION,
        )
        ref = repo
    }

    /** 저장된 설정을 한 번만 불러온다 */
    suspend fun ensureLoaded() = loadLock.withLock {
        if (!loaded) { repo.load(); loaded = true }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = getString(R.string.channel_alerts_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "alerts"
    }
}

class AndroidStorage(private val dir: File) : KeyValueStorage {
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}

class AndroidNotifier(private val context: Context) : Notifier {
    override fun show(notification: AppNotification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // 누르면 그 알림의 화면으로 (예: 제이콥 농사대회 → 작물 페이지)
        val open = PendingIntent.getActivity(
            context, notification.key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TARGET, notification.target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, SbApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sb)
            .setColor(0xFFFFAA00.toInt())
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notification.key.hashCode(), n) }
    }
}
