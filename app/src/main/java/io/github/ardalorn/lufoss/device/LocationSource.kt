package io.github.ardalorn.lufoss.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.Fix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Location readings from the platform's own providers, with no Google services. */
class LocationSource(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * A stream of fresh fixes. GPS (precise, slow, needs sky) and the network (fast, rough) run side by side.
     * Cached fixes are skipped. The stream fails with [SecurityException] when permission is missing.
     */
    @SuppressLint("MissingPermission")
    fun fixes(): Flow<Fix> = callbackFlow {
        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / NS_PER_MS
                if (ageMs > Cfg.MAX_FIX_AGE_MS) return
                trySend(readFix(location))
            }
        }
        var any = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, UPDATE_MS, 0f, listener, Looper.getMainLooper())
                any = true
            }
        }
        if (!any) close(IllegalStateException("Location is switched off on this device."))
        awaitClose { manager.removeUpdates(listener) }
    }

    /** The most accurate fix seen until one is good enough or time runs out. Null when none arrived. */
    suspend fun bestFix(): Fix? {
        var best: Fix? = null
        withTimeoutOrNull(Cfg.REFINE_MAX_MS) {
            fixes().first { fix ->
                val b = best
                if (b == null || fix.acc < b.acc || (fix.acc == b.acc && fix.t > b.t)) best = fix
                (best?.acc ?: Double.MAX_VALUE) <= Cfg.GOOD_ACC_M
            }
        }
        return best
    }

    private fun readFix(l: Location): Fix = Fix(
        lat = l.latitude,
        lng = l.longitude,
        // A missing or zero accuracy means unknown, not perfect.
        acc = if (l.hasAccuracy() && l.accuracy > 0f) l.accuracy.toDouble() else Double.POSITIVE_INFINITY,
        t = System.currentTimeMillis(),
        course = if (l.hasBearing()) l.bearing.toDouble() else null,
        speed = if (l.hasSpeed()) l.speed.toDouble() else null
    )

    private companion object {
        const val UPDATE_MS = 1_000L
        const val NS_PER_MS = 1_000_000L
    }
}
