package io.github.ardalorn.lufoss.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Map pictures from OpenStreetMap's standard raster service (XYZ order, free, no key).
 * Everything here runs on the main thread except the download and the decoding.
 */
class TileStore(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onError: (Boolean) -> Unit
) {
    /** Changes whenever a picture arrives, so drawing code that reads it is drawn again. */
    var version by mutableIntStateOf(0)
        private set

    private class Entry(val image: ImageBitmap, val created: Long)

    private val cache = object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > Cfg.TILE_CACHE_MAX
    }
    private val pending = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val limiter = Semaphore(Cfg.TILE_PARALLEL)
    private var errorShown = false

    /** The picture if it is in memory. Otherwise starts loading it and returns null. */
    fun get(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = keyOf(z, x, y)
        fresh(key)?.let { return it }
        if (key in pending) return null
        val failed = failedAt[key]
        if (failed != null && now() - failed < Cfg.TILE_RETRY_AFTER_MS) return null
        load(key, z, x, y)
        return null
    }

    /** The picture if it is in memory; never starts a download. */
    fun cached(z: Int, x: Int, y: Int): ImageBitmap? = fresh(keyOf(z, x, y))

    private fun fresh(key: String): ImageBitmap? {
        val entry = cache[key] ?: return null
        if (now() - entry.created > Cfg.TILE_MEMORY_TTL_MS) {
            cache.remove(key)
            return null
        }
        return entry.image
    }

    private fun load(key: String, z: Int, x: Int, y: Int) {
        pending += key
        scope.launch {
            try {
                val bytes = limiter.withPermit {
                    api.fetchBytes("${Cfg.TILE_URL}$z/$x/$y.png", tries = 1, timeoutMs = Cfg.FETCH_TIMEOUT_MS)
                }
                val image = withContext(Dispatchers.Default) { decode(bytes) }
                if (image == null) {
                    markFailed(key)
                } else {
                    cache[key] = Entry(image, now())
                    failedAt.remove(key)
                    if (errorShown) {
                        errorShown = false
                        onError(false)
                    }
                    version++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markFailed(key)
            } finally {
                pending -= key
            }
        }
    }

    private fun markFailed(key: String) {
        failedAt[key] = now()
        if (!errorShown) {
            errorShown = true
            onError(true)
        }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private fun keyOf(z: Int, x: Int, y: Int): String = "$z/$x/$y"

    private fun now(): Long = System.nanoTime() / NS_PER_MS

    private companion object {
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
        const val NS_PER_MS = 1_000_000L
    }
}
