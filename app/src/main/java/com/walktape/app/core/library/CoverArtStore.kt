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
 * Per-profile cover cache: bounded disk LRU (default 250 MB per SPEC, limit editable at
 * runtime) plus a small in-memory bitmap cache. Files never leave the profile directory,
 * so removal of a profile drops its covers. The offline gate serves disk-only content
 * when the profile is in offline-only mode.
 */
class CoverArtStore(
    private val coversDir: File,
    private val clientProvider: suspend () -> OpenSubsonicClient?,
    maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxBytesProvider: () -> Long = { maxBytes },
    private val allowNetwork: () -> Boolean = { true },
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
        val bytes = file(coverArtId, sizePx, fetchIfMissing = true)
            ?.let { runCatching { it.readBytes() }.getOrNull() } ?: return@withContext null
        decode(bytes, sizePx)?.also { memory.put(key, it) }
    }

    /**
     * Cached cover file for a (profile store, art id, size); optionally fetched over the
     * network when the profile gate allows it. Android Auto reads cover art through this
     * path (W10-T01): offline-only profiles stay strictly disk-served.
     */
    suspend fun file(coverArtId: String, sizePx: Int, fetchIfMissing: Boolean): File? = withContext(Dispatchers.IO) {
        if (coverArtId.isEmpty()) return@withContext null
        val file = fileFor(coverArtId, sizePx)
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext file
        }
        if (!fetchIfMissing || !allowNetwork()) return@withContext null
        inFlight.withLock {
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                return@withLock file
            }
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
                    file
                }
                else -> null
            }
        }
    }

    /** Best-effort offline prefetch right after a download completes (network is up then). */
    suspend fun prefetch(coverArtId: String?, sizePx: Int = 512) {
        if (coverArtId.isNullOrBlank() || !allowNetwork()) return
        bitmap(coverArtId, sizePx)
    }

    /** Honest on-disk accounting used by the space settings. */
    suspend fun diskBytes(): Long = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0
    }

    /** Clears the disk cache (memory entries simply expire); safe while nothing decodes. */
    suspend fun clearDisk(): Boolean = withContext(Dispatchers.IO) {
        memory.evictAll()
        inFlight.withLock {
            coversDir.listFiles()?.all { it.delete() } ?: true
        }
    }

    suspend fun trimTo(bytes: Long) = withContext(Dispatchers.IO) { inFlight.withLock { trim(bytes) } }

    private fun trim(overrideLimit: Long? = null) {
        val maxBytes = overrideLimit ?: maxBytesProvider()
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
