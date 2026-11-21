package io.github.nuadyr.lufoss.geo

import io.github.nuadyr.lufoss.Cfg
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

data class LatLng(val lat: Double, val lng: Double)

data class Xy(val x: Double, val y: Double)

/** Web Mercator projection, compass bearings and small-distance helpers. */
object GeoMath {
    const val DEG = PI / 180.0
    const val METRES_PER_DEG_LAT = 111_320.0

    fun clampLat(lat: Double): Double = lat.coerceIn(-Cfg.MAX_LAT, Cfg.MAX_LAT)

    /** Size of the whole world in pixels at zoom [z]. */
    fun worldSize(z: Double): Double = Cfg.TILE * 2.0.pow(z)

    fun project(lat: Double, lng: Double, z: Double): Xy {
        val n = worldSize(z)
        val s = sin(clampLat(lat) * DEG)
        return Xy((lng + 180.0) / 360.0 * n, (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * n)
    }

    fun unproject(x: Double, y: Double, z: Double): LatLng {
        val n = worldSize(z)
        val t = PI - 2 * PI * y / n
        return LatLng(180.0 / PI * atan(sinh(t)), x / n * 360.0 - 180.0)
    }

    /** Wraps any angle into 0 until 360. */
    fun norm(d: Double): Double = ((d % 360.0) + 360.0) % 360.0

    /** Shortest signed turn from angle [a] to angle [b], in degrees. */
    fun angDiff(a: Double, b: Double): Double = norm(b - a + 180.0) - 180.0

    /** Turns a screen offset into a map offset for a map whose top points along compass bearing [bearing]. */
    fun toMap(dx: Double, dy: Double, bearing: Double): Xy {
        val r = bearing * DEG
        val c = cos(r)
        val s = sin(r)
        return Xy(dx * c - dy * s, dx * s + dy * c)
    }

    fun metresBetween(a: LatLng, b: LatLng): Double =
        hypot((b.lat - a.lat) * METRES_PER_DEG_LAT, (b.lng - a.lng) * METRES_PER_DEG_LAT * cos(a.lat * DEG))

    /** Metres between two nearby points, using the mean latitude for the east-west scale. */
    fun segmentMetres(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dx = (bLng - aLng) * METRES_PER_DEG_LAT * cos((aLat + bLat) * PI / 360.0)
        val dy = (bLat - aLat) * METRES_PER_DEG_LAT
        return hypot(dx, dy)
    }

    /** Compass bearing from [a] to [b], 0 until 360. */
    fun bearingTo(a: LatLng, b: LatLng): Double {
        val dl = (b.lng - a.lng) * DEG
        val la = a.lat * DEG
        val lb = b.lat * DEG
        val y = sin(dl) * cos(lb)
        val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dl)
        return norm(atan2(y, x) / DEG)
    }

    /** The point [dist] metres from [p] along compass bearing [bearing]. */
    fun offsetPoint(p: LatLng, dist: Double, bearing: Double): LatLng {
        val a = bearing * DEG
        return LatLng(
            p.lat + dist * cos(a) / METRES_PER_DEG_LAT,
            p.lng + dist * sin(a) / (METRES_PER_DEG_LAT * cos(p.lat * DEG))
        )
    }
}
