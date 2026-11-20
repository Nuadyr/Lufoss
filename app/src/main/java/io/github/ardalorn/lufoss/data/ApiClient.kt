package io.github.ardalorn.lufoss.data

import io.github.ardalorn.lufoss.Cfg
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * One shared HTTP client. It identifies the app (the OpenStreetMap services require that), keeps an HTTP cache
 * that honours the servers' cache headers, and retries a failed request once.
 */
class ApiClient(cacheDir: File?) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cache(cacheDir?.let { Cache(it, Cfg.HTTP_CACHE_BYTES) })
        .connectTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", Cfg.USER_AGENT).build())
        }
        .build()

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
