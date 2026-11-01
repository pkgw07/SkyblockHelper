package sbhelper.core.nbt

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

actual fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
