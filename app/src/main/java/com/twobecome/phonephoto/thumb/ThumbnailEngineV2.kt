package com.twobecome.phonephoto.thumb

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.util.Log
import com.twobecome.phonephoto.config.AppProfile
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * ThumbnailEngineV2
 * - Memory LRU -> Disk -> Decode
 * - Request coalescing (in-flight map)
 * - Disk LRU by size cap (lastModified)
 * - EXIF orientation (rotate/mirror)
 * - Prefetch with concurrency cap
 */
class ThumbnailEngineV2(
    context: Context,
    private val defaultSize: Int = AppProfile.THUMB_SIZE,
    private val diskDirName: String = "thumb_cache",
    private val diskQuality: Int = AppProfile.DISK_QUALITY,
    private val maxConcurrentPrefetch: Int = AppProfile.PREFETCH_MAX,
    private val diskMaxBytes: Long = AppProfile.DISK_MAX_BYTES
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // LRU memory (KB)
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / AppProfile.LRU_DIVISOR / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val inFlightPrefetch = Collections.synchronizedSet(mutableSetOf<String>())
    private val activePrefetch = AtomicInteger(0)

    // Request coalescing: key -> Deferred<Bitmap?>
    private val inFlightMap = ConcurrentHashMap<String, Deferred<Pair<Bitmap?, HitSource?>>>()

    enum class HitSource { MEMORY, DISK, DECODE }

    private fun cacheDir(): File = File(appContext.cacheDir, diskDirName).apply { if (!exists()) mkdirs() }

    // Stable key from Uri (pathSegments preferred)
    private fun stableKeySeedFromUri(uri: Uri): String {
        val seg = uri.pathSegments?.joinToString("_") ?: ""
        return if (seg.isNotBlank()) seg else uri.toString()
    }

    private fun keyOf(uri: Uri, size: Int) = MessageDigest.getInstance("MD5")
        .digest((stableKeySeedFromUri(uri) + "_$size").toByteArray())
        .joinToString("") { "%02x".format(it) } + ".jpg"

    fun cacheKey(uri: Uri, size: Int = defaultSize) = keyOf(uri, size)
    fun hasInMemory(uri: Uri, size: Int = defaultSize) = memory.get(keyOf(uri, size)) != null
    fun hasOnDisk(uri: Uri, size: Int = defaultSize) = File(cacheDir(), keyOf(uri, size)).exists()
    fun clearMemory() = memory.evictAll()
    fun clearDisk() { cacheDir().listFiles()?.forEach { runCatching { it.delete() } } }

    private fun log(msg: String) { if (AppProfile.LOG_HIT) Log.i("ThumbHit", msg) }

    /** Coalesced loader that reports HitSource */
    suspend fun loadOrCreateWithHit(uri: Uri, size: Int = defaultSize): Pair<Bitmap?, HitSource?> {
        val key = cacheKey(uri, size)

        // 1) Memory
        memory.get(key)?.let {
            log("MEM key=$key")
            return it to HitSource.MEMORY
        }

        // Coalesce
        inFlightMap[key]?.let { return it.await() }
        val deferred = ioScope.async {
            // Re-check memory (double-checked after await starts)
            memory.get(key)?.let {
                log("MEM(key2) key=$key")
                return@async it to HitSource.MEMORY
            }
            // 2) Disk
            val onDisk = File(cacheDir(), key)
            if (onDisk.exists()) {
                BitmapFactory.decodeFile(onDisk.absolutePath)?.also { bmp ->
                    memory.put(key, bmp)
                    log("DISK key=$key")
                    return@async bmp to HitSource.DISK
                }
            }
            // 3) Decode (with EXIF)
            val created = decodeScaleApplyExif(uri, size) ?: return@async null to null
            memory.put(key, created)
            // Save to disk + trim LRU
            runCatching {
                FileOutputStream(onDisk).use { created.compress(Bitmap.CompressFormat.JPEG, diskQuality, it) }
                trimDiskIfNeeded()
            }
            log("DECODE key=$key")
            created to HitSource.DECODE
        }
        inFlightMap[key] = deferred
        return try {
            deferred.await()
        } finally {
            inFlightMap.remove(key)
        }
    }

    /** Simple version */
    suspend fun loadOrCreate(uri: Uri, size: Int = defaultSize): Bitmap? =
        loadOrCreateWithHit(uri, size).first

    /** Prefetch next-row same-column */
    fun prefetchNext(position: Int, columns: Int, items: List<Uri>, size: Int = defaultSize) {
        val next = position + columns
        if (next !in items.indices) return
        prefetchIndex(next, items, size)
    }

    fun prefetchIndex(index: Int, items: List<Uri>, size: Int = defaultSize) {
        if (index !in items.indices) return
        val uri = items[index]
        val key = cacheKey(uri, size)
        if (hasInMemory(uri, size) || hasOnDisk(uri, size)) return
        if (activePrefetch.get() >= maxConcurrentPrefetch) return
        if (!inFlightPrefetch.add(key)) return

        activePrefetch.incrementAndGet()
        ioScope.launch {
            try { loadOrCreate(uri, size) } finally {
                inFlightPrefetch.remove(key)
                activePrefetch.decrementAndGet()
            }
        }
    }

    // --- Decode & EXIF ---
    private fun decodeScaleApplyExif(uri: Uri, reqSize: Int): Bitmap? {
        val cr = appContext.contentResolver

        // Read EXIF (orientation) first — using fresh InputStream
        val exifOrientation = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_UNDEFINED
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

        // Bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        // Sample size
        var inSample = 1
        var halfW = bounds.outWidth / 2
        var halfH = bounds.outHeight / 2
        while (halfW / inSample >= reqSize && halfH / inSample >= reqSize) inSample *= 2

        // Decode
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = inSample })
        } ?: return null

        // Scale to reqSize (max dimension)
        val maxDim = max(decoded.width, decoded.height)
        val scale = reqSize / maxDim.toFloat()
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it != decoded) decoded.recycle() }
        } else decoded

        // Apply EXIF orientation/mirror on scaled bitmap
        val oriented = applyExif(scaled, exifOrientation)
        return oriented
    }

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> return src
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                m.setRotate(180f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.setRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.setRotate(-90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(270f)
            else -> return src
        }
        return try {
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (out != src) src.recycle()
            out
        } catch (e: Throwable) {
            src
        }
    }

    // --- Disk LRU (size cap) ---
    private fun dirSizeBytes(dir: File): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun trimDiskIfNeeded() {
        val dir = cacheDir()
        var total = dirSizeBytes(dir)
        if (total <= diskMaxBytes) return

        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in files) {
            if (total <= diskMaxBytes) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    override fun close() { ioScope.cancel() }
}
