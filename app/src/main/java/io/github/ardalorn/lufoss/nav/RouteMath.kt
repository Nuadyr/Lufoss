package io.github.ardalorn.lufoss.nav

import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.Route
import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.geo.LatLng
import kotlin.math.cos
import kotlin.math.hypot

/** Where a position sits on a route: its distance [d] from the line and the distance [along] the line. */
class Snap(val d: Double, val along: Double)

/** Progress along a route line. */
object RouteMath {
    /**
     * The closest point on the route to (lat, lng), looking only at the stretch from [from] to [to] metres along.
     * Looking forward (and a little back) means a route that doubles back cannot confuse progress.
     */
    fun snap(route: Route, lat: Double, lng: Double, from: Double = 0.0, to: Double = Double.POSITIVE_INFINITY): Snap {
        val k = GeoMath.METRES_PER_DEG_LAT
        val kx = k * cos(lat * GeoMath.DEG)
        var best = Snap(Double.POSITIVE_INFINITY, 0.0)
        for (i in 1 until route.size) {
            if (route.cum[i] < from) continue
            if (route.cum[i - 1] > to) break
            val ax = (route.lngs[i - 1] - lng) * kx
            val ay = (route.lats[i - 1] - lat) * k
            val bx = (route.lngs[i] - lng) * kx
            val by = (route.lats[i] - lat) * k
            val dx = bx - ax
            val dy = by - ay
            val len2 = (dx * dx + dy * dy).let { if (it == 0.0) Cfg.EPSILON else it }
            val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < best.d) best = Snap(d, route.cum[i - 1] + t * (route.cum[i] - route.cum[i - 1]))
        }
        return best
    }

    /** The point [dist] metres along the route. */
    fun pointAt(route: Route, dist: Double): LatLng {
        if (route.size < 2) return LatLng(route.lats.firstOrNull() ?: 0.0, route.lngs.firstOrNull() ?: 0.0)
        var i = 1
        while (i < route.size - 1 && route.cum[i] < dist) i++
        val span = route.cum[i] - route.cum[i - 1]
        val t = if (span == 0.0) 0.0 else ((dist - route.cum[i - 1]) / span).coerceIn(0.0, 1.0)
        return LatLng(
            route.lats[i - 1] + (route.lats[i] - route.lats[i - 1]) * t,
            route.lngs[i - 1] + (route.lngs[i] - route.lngs[i - 1]) * t
        )
    }

    /** The direction of the route ahead of [along], or null when it is too short to tell. */
    fun routeHeading(route: Route, along: Double): Double? {
        val a = pointAt(route, along)
        val b = pointAt(route, minOf(route.total, along + Cfg.ROUTE_LOOKAHEAD_M))
        return if (GeoMath.metresBetween(a, b) < 1.0) null else GeoMath.bearingTo(a, b)
    }
}
