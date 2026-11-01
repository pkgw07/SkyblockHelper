package sbhelper.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import sbhelper.core.update.Release
import sbhelper.core.update.Updates
import sbhelper.ui.AppInfo
import sbhelper.ui.InAppUpdater
import java.io.File

/**
 * 휴대폰 앱 안 업데이트.
 *
 * 1) 새 APK 를 앱 안에 받아 둔다 (stage)
 * 2) "지금 설치하고 다시 시작할까요?" → 설치 (PackageInstaller)
 *    Android 12 이상은 자기 자신을 업데이트할 때 확인 화면 없이 설치되고, 설치가 끝나면 앱을 다시 연다.
 * 3) 실패하면 이유를 화면에 알려 준다 (예: 예전 버전과 서명 키가 다름)
 */
class AndroidUpdater(private val context: Context) : InAppUpdater {
    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    override val stagedVersion: String?
        get() = dir.listFiles()?.mapNotNull { f -> f.name.removeSuffix(".apk").takeIf { f.name.endsWith(".apk") } }
            ?.filter { Updates.isNewer(it, AppInfo.VERSION) }
            ?.maxWithOrNull { a, b -> if (Updates.isNewer(a, b)) 1 else -1 }

    override suspend fun stage(release: Release, download: suspend (String) -> ByteArray?): InAppUpdater.Result {
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return InAppUpdater.Result.NEEDS_INSTALLER
        val bytes = download(asset.url) ?: return InAppUpdater.Result.FAILED
        if (asset.size > 0 && bytes.size.toLong() != asset.size) return InAppUpdater.Result.FAILED
        return withContext(Dispatchers.IO) {
            // 지난 받은 파일은 지운다
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "${release.version}.part")
            tmp.writeBytes(bytes)
            if (tmp.renameTo(File(dir, "${release.version}.apk"))) InAppUpdater.Result.STAGED else InAppUpdater.Result.FAILED
        }
    }

    /** 받아 둔 APK 설치. 설치가 끝나면 Android 가 앱을 닫고, 새 버전이 다시 열린다 */
    override fun restart() {
        val v = stagedVersion ?: return
        val f = File(dir, "$v.apk")
        SelfUpdate.status.value = "새 버전 $v 을 설치하는 중이에요. 잠시 후 앱이 다시 열려요."
        if (!SelfUpdate.install(context, f)) SelfUpdate.status.value = "⚠ 설치를 시작하지 못했어요. Releases 페이지에서 직접 받아 주세요."
    }
}

object SelfUpdate {
    private const val ACTION_STATUS = "sbhelper.android.UPDATE_STATUS"

    /** 화면에 보여 줄 설치 진행·실패 안내 */
    val status = MutableStateFlow<String?>(null)

    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateStatusReceiver::class.java).setAction(ACTION_STATUS).putExtra("apk", apk.absolutePath)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
        true
    }.getOrDefault(false)

    private fun channel(context: Context): NotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("updates", "앱 업데이트", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return nm
    }

    fun notify(context: Context, title: String, text: String) {
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, "updates")
            .setSmallIcon(R.drawable.ic_stat_sb)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { channel(context).notify(9001, n) }
    }

    /** 서명이 달라 설치할 수 없을 때: 다운로드 폴더에 APK 를 저장해서 앱을 지운 뒤 바로 설치할 수 있게 */
    fun saveToDownloads(context: Context, apk: File): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "SkyblockHelper-${apk.name}")
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
            true
        } else false
    }.getOrDefault(false)
}

/** 설치 진행 결과 */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android 가 한 번 확인을 묻는 경우 (처음 업데이트, Android 11 이하 등)
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                SelfUpdate.status.value = "설치 확인 화면에서 '업데이트'를 눌러 주세요."
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> SelfUpdate.status.value = "설치 완료! 앱을 다시 여는 중이에요."
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                val apk = intent.getStringExtra("apk")?.let(::File)
                val saved = apk != null && apk.exists() && SelfUpdate.saveToDownloads(context, apk)
                val text = "지금 앱과 새 버전의 서명 키가 달라서 그대로 업데이트할 수 없어요 (v1.3.0 이하에서 넘어올 때 한 번만 생겨요). " +
                    "앱을 한 번 지운 뒤 " + (if (saved) "다운로드 폴더의 SkyblockHelper APK 를 열어" else "Releases 페이지에서 새 APK 를 받아") + " 설치해 주세요."
                SelfUpdate.status.value = "⚠ $text"
                SelfUpdate.notify(context, "업데이트를 설치하지 못했어요", text)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> SelfUpdate.status.value = "업데이트 설치를 취소했어요. 다시 시작 버튼으로 언제든 설치할 수 있어요."
            PackageInstaller.STATUS_FAILURE_STORAGE -> SelfUpdate.status.value = "⚠ 저장 공간이 부족해서 설치하지 못했어요."
            else -> SelfUpdate.status.value = "⚠ 설치하지 못했어요 ($status${if (msg.isNotBlank()) ": $msg" else ""})"
        }
    }
}

/** 새 버전으로 바뀌면: 앱을 다시 열고 (막히면) 알림으로 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Android 10 이상은 백그라운드에서 화면을 여는 것을 막을 수 있어서, 알림도 함께 보낸다
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        SelfUpdate.notify(context, "업데이트 완료 · v${AppInfo.VERSION}", "새 버전이 적용됐어요. 앱이 열리지 않았다면 눌러서 열어 주세요.")
    }
}
