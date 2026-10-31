package sbhelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sbhelper.core.api.SbJson
import sbhelper.core.api.defaultHttpClient
import sbhelper.ui.decodeImage
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 플레이어 스킨은 Mojang 공식 서버에서 받는다.
 * sessionserver.mojang.com → textures.minecraft.net 스킨 PNG → 얼굴(8x8)과 모자 레이어를 잘라 그림.
 */
object SkinCache {
    private val client by lazy { defaultHttpClient() }
    private val cache = HashMap<String, ImageBitmap?>()
    private val lock = Mutex()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun skin(uuid: String): ImageBitmap? {
        lock.withLock { if (cache.containsKey(uuid)) return cache[uuid] }
        val image = runCatching {
            val profile = client.get("https://sessionserver.mojang.com/session/minecraft/profile/$uuid").bodyAsText()
            val prop = SbJson.parseToJsonElement(profile).jsonObject["properties"]!!.jsonArray
                .first { it.jsonObject["name"]?.jsonPrimitive?.content == "textures" }
            val decoded = Base64.decode(prop.jsonObject["value"]!!.jsonPrimitive.content).decodeToString()
            val url = SbJson.parseToJsonElement(decoded).jsonObject["textures"]!!.jsonObject["SKIN"]!!
                .jsonObject["url"]!!.jsonPrimitive.content.replace("http://", "https://")
            decodeImage(client.get(url).readRawBytes())
        }.getOrNull()
        lock.withLock { cache[uuid] = image }
        return image
    }
}

@Composable
fun PlayerHead(uuid: String?, name: String, size: Dp = 40.dp) {
    var skin by remember(uuid) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uuid) { if (!uuid.isNullOrBlank()) skin = SkinCache.skin(uuid) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Sb.SurfaceHigh)
            .border(1.dp, Sb.BorderStrong, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val img = skin
        if (img != null) {
            Canvas(Modifier.size(size)) {
                val dst = IntSize(this.size.width.toInt(), this.size.height.toInt())
                // 얼굴 (8,8) 8x8, 모자 레이어 (40,8) 8x8 — 픽셀이 뭉개지지 않도록 FilterQuality.None
                drawImage(img, IntOffset(8, 8), IntSize(8, 8), dstSize = dst, filterQuality = FilterQuality.None)
                drawImage(img, IntOffset(40, 8), IntSize(8, 8), dstSize = dst, filterQuality = FilterQuality.None)
            }
        } else {
            Text(name.take(1).uppercase().ifEmpty { "?" }, style = SbText.pixel((size.value * 0.45f).toInt(), Sb.Gold, bold = true))
        }
    }
}
