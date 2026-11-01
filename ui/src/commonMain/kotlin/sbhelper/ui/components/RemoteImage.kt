package sbhelper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import sbhelper.core.api.HypixelApi
import sbhelper.core.api.defaultHttpClient
import sbhelper.ui.decodeImage

/** 인터넷 이미지 (패치노트 그림 등). 메모리에 캐시한다. */
object ImageCache {
    private val client by lazy { defaultHttpClient() }
    private val cache = HashMap<String, ImageBitmap>()
    /** 실패한 주소 → 다시 시도해도 되는 시각 (잠깐 실패한 것을 영원히 포기하지 않게) */
    private val failedUntil = HashMap<String, Long>()
    private val inFlight = HashMap<String, kotlinx.coroutines.Deferred<ImageBitmap?>>()
    private val lock = Mutex()
    /** 위키에 한꺼번에 너무 많이 요청하지 않게 (동시에 6개까지) */
    private val gate = kotlinx.coroutines.sync.Semaphore(6)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** 아이콘 묶음에서 (인터넷 없이) */
    fun fromPack(id: String?, name: String?): ImageBitmap? {
        val key = "pack:" + (id ?: name?.lowercase())
        cache[key]?.let { return it }
        val png = sbhelper.core.data.IconPack.png(id, name) ?: return null
        return decodeImage(png)?.also { cache[key] = it }
    }

    suspend fun load(url: String): ImageBitmap? {
        val job = lock.withLock {
            cache[url]?.let { return it }
            if ((failedUntil[url] ?: 0L) > sbhelper.core.util.Clock.now()) return null
            inFlight.getOrPut(url) {
                scope.async {
                    val img = gate.withPermit {
                        runCatching {
                            val res = client.get(url) { header("User-Agent", HypixelApi.USER_AGENT) }
                            if (res.status.isSuccess()) decodeImage(res.readRawBytes()) else null
                        }.getOrNull()
                    }
                    lock.withLock {
                        inFlight.remove(url)
                        if (img != null) cache[url] = img else failedUntil[url] = sbhelper.core.util.Clock.now() + 2 * 60_000
                    }
                    img
                }
            }
        }
        return job.await()
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { img = ImageCache.load(url) }
    img?.let { Image(it, null, modifier, contentScale = ContentScale.FillWidth) }
}
