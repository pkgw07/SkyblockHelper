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
import sbhelper.core.api.HypixelApi
import sbhelper.core.api.defaultHttpClient
import sbhelper.ui.decodeImage

/** 인터넷 이미지 (패치노트 그림 등). 메모리에 캐시한다. */
object ImageCache {
    private val client by lazy { defaultHttpClient() }
    private val cache = HashMap<String, ImageBitmap?>()
    private val lock = Mutex()

    suspend fun load(url: String): ImageBitmap? {
        lock.withLock { if (cache.containsKey(url)) return cache[url] }
        val img = runCatching {
            val res = client.get(url) { header("User-Agent", HypixelApi.USER_AGENT) }
            if (res.status.isSuccess()) decodeImage(res.readRawBytes()) else null
        }.getOrNull()
        lock.withLock { cache[url] = img }
        return img
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { img = ImageCache.load(url) }
    img?.let { Image(it, null, modifier, contentScale = ContentScale.FillWidth) }
}
