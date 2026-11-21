package io.github.ardalorn.lufoss.data

import io.github.ardalorn.lufoss.Cfg
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * One shared HTTP client. It identifies the app (the OpenStreetMap services require that), keeps a disk cache that
 * never lives longer than [Cfg.HTTP_CACHE_TTL_S] (one hour), and retries a failed request once.
 *
 * The one-hour limit has two parts: responses are stored with a freshness of at most an hour whatever the server
 * says, and the whole cache is wiped on the first request after an hour has passed since the last wipe, so stale
 * entries (search words, route end points, map areas) do not stay on the device.
 */
class ApiClient(cacheDir: File?) {
    private val cache: Cache? = cacheDir?.let { Cache(it, Cfg.HTTP_CACHE_BYTES) }
    private val stamp: File? = cacheDir?.let { File(it.parentFile, it.name + ".stamp") }

    @Volatile
    private var purgedAt: Long = stamp?.takeIf { it.exists() }?.lastModified() ?: 0L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .connectTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", Cfg.USER_AGENT).build())
        }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val original = response.header("Cache-Control")
            val capped = CachePolicy.capCacheControl(original, Cfg.HTTP_CACHE_TTL_S)
            if (capped == original.orEmpty()) {
                response
            } else {
                response.newBuilder().header("Cache-Control", capped).build()
            }
        }
        .build()

    /** Wipes the disk cache when an hour has passed since the last wipe. Safe to call often. */
    private suspend fun purgeIfDue() {
        val store = cache ?: return
        if (!CachePolicy.isExpired(purgedAt, System.currentTimeMillis(), Cfg.HTTP_CACHE_TTL_MS)) return
        withContext(Dispatchers.IO) {
            synchronized(this@ApiClient) {
                val now = System.currentTimeMillis()
                if (!CachePolicy.isExpired(purgedAt, now, Cfg.HTTP_CACHE_TTL_MS)) return@synchronized
                try {
                    store.evictAll()
                    stamp?.let {
                        it.writeText("")
                        it.setLastModified(now)
                    }
                    purgedAt = now
                } catch (e: IOException) {
                    // Try again on a later request.
                } catch (e: SecurityException) {
                    // Try again on a later request.
                }
            }
        }
    }

    /** Fetches [url] as text. Throws [IOException] when every attempt failed. */
    suspend fun fetchText(
        url: String,
        tries: Int = Cfg.FETCH_TRIES,
        timeoutMs: Long = Cfg.FETCH_TIMEOUT_MS,
        retryMs: Long = Cfg.FETCH_RETRY_MS
    ): String = String(fetchBytes(url, tries, timeoutMs, retryMs), Charsets.UTF_8)

    /** Fetches [url] as bytes. Throws [IOException] when every attempt failed. */
    suspend fun fetchBytes(
        url: String,
        tries: Int = Cfg.FETCH_TRIES,
        timeoutMs: Long = Cfg.FETCH_TIMEOUT_MS,
        retryMs: Long = Cfg.FETCH_RETRY_MS
    ): ByteArray {
        purgeIfDue()
        for (attempt in 0 until tries) {
            try {
                val call = client.newCall(Request.Builder().url(url).header("Accept", "*/*").build())
                call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                call.await().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) return body.bytes()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Try again below.
            } catch (e: IllegalArgumentException) {
                throw IOException("Bad address: $url", e)
            }
            if (attempt < tries - 1) delay(retryMs * (attempt + 1))
        }
        throw IOException("The server did not answer.")
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        }
    )
}
