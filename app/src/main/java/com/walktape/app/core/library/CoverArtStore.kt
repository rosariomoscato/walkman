package com.walktape.app.core.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Per-profile cover cache: bounded disk LRU (default 250 MB per SPEC) plus a small
 * in-memory bitmap cache. Files never leave the profile directory, so removal of a
 * profile drops its covers.
 */
class CoverArtStore(
    private val coversDir: File,
    private val clientProvider: suspend () -> OpenSubsonicClient?,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val inFlight = Mutex()

    init { coversDir.mkdirs() }

    suspend fun bitmap(coverArtId: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (coverArtId.isEmpty()) return@withContext null
        val key = "$coverArtId@$sizePx"
        memory.get(key)?.let { return@withContext it }
        val file = fileFor(coverArtId, sizePx)
        val bytes = if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            file.readBytes()
        } else {
            inFlight.withLock {
                if (file.exists()) {
                    file.setLastModified(System.currentTimeMillis())
                    file.readBytes()
                } else {
                    when (val result = clientProvider()?.coverArt(coverArtId, sizePx)) {
                        is ApiResult.Success -> {
                            if (decode(result.value, sizePx) == null) return@withLock null
                            file.parentFile?.mkdirs()
                            val temporary = File(file.parentFile, "${file.name}.partial")
                            temporary.writeBytes(result.value)
                            if (!temporary.renameTo(file)) {
                                temporary.delete()
                                return@withLock null
                            }
                            trim()
                            result.value
                        }
                        else -> return@withLock null
                    }
                }
            } ?: return@withContext null
        }
        decode(bytes, sizePx)?.also { memory.put(key, it) }
    }

    private fun trim() {
        val files = coversDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun fileFor(coverArtId: String, sizePx: Int): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest("$coverArtId|$sizePx".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(coversDir, hash)
    }

    private fun decode(bytes: ByteArray, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 250L * 1024 * 1024
    }
}
