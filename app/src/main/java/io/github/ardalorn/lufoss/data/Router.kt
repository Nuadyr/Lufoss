package io.github.ardalorn.lufoss.data

import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.geo.LatLng
import org.json.JSONObject

/** Turns the router's JSON answer into a [Route]. Kept apart from the network code so it can be tested. */
object RouteParser {
    /**
     * [points] are the stops in order. Stops in the middle become turns, not arrivals.
     * Throws when the answer holds no route.
     */
    fun parse(json: String, points: List<LatLng>, fromName: String): Route {
        val r = JSONObject(json).getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val n = coords.length()
        val lats = DoubleArray(n)
        val lngs = DoubleArray(n)
        val cum = DoubleArray(n)
        for (i in 0 until n) {
            val c = coords.getJSONArray(i)
            lngs[i] = c.getDouble(0)
            lats[i] = c.getDouble(1)
            if (i > 0) cum[i] = cum[i - 1] + GeoMath.segmentMetres(lats[i - 1], lngs[i - 1], lats[i], lngs[i])
        }
        val total = if (n > 0) cum[n - 1] else 0.0
        val distance = r.getDouble("distance")
        // The router's step lengths and our own sums differ slightly, so scale one onto the other
        // to keep each turn where it is drawn.
        val scale = total / (if (distance == 0.0) 1.0 else distance)
        val steps = mutableListOf<Step>()
        val starts = mutableListOf<Double>()
        val wps = mutableListOf<Waypoint>()
        var acc = 0.0
        var before = 0.0
        val legs = r.getJSONArray("legs")
        for (li in 0 until legs.length()) {
            val legSteps = legs.getJSONObject(li).getJSONArray("steps")
            for (si in 0 until legSteps.length()) {
                val s = legSteps.getJSONObject(si)
                val m = s.getJSONObject("maneuver")
                if (m.optString("type") == "arrive" && li < legs.length() - 1) {
                    before = m.optDouble("bearing_before", 0.0)
                    val stop = points[li + 1]
                    wps += Waypoint(stop.lat, stop.lng, acc * scale)
                    continue
                }
                var type = m.optString("type")
                var mod: String? = if (m.has("modifier")) m.optString("modifier") else null
                if (li > 0 && si == 0) {
                    type = "turn"
                    mod = turnMod(GeoMath.angDiff(before, m.optDouble("bearing_after", 0.0)))
                }
                val d = s.optDouble("distance", 0.0)
                steps += Step(d, s.optString("name", ""), type, mod)
                starts += acc * scale
                acc += d
            }
        }
        return Route(
            lats = lats,
            lngs = lngs,
            cum = cum,
            steps = steps,
            starts = starts.toDoubleArray(),
            wps = wps,
            dist = distance,
            dur = r.getDouble("duration"),
            total = total,
            from = fromName
        )
    }
}

/** Walking routes from the OpenStreetMap Germany routing service (free, no key). */
class Router(private val api: ApiClient) {
    /**
     * One walking route through every stop in order. Throws when no route exists. With [noUTurns] the router is
     * told to keep going straight through each stop instead of turning round there.
     */
    suspend fun fetchRoute(
        points: List<LatLng>,
        fromName: String,
        tries: Int = Cfg.FETCH_TRIES,
        timeoutMs: Long = Cfg.FETCH_TIMEOUT_MS,
        noUTurns: Boolean = false
    ): Route {
        val path = points.joinToString(";") { "${it.lng},${it.lat}" }
        val straight = if (noUTurns) "&continue_straight=true" else ""
        val body = api.fetchText(
            "${Cfg.ROUTE_URL}$path?overview=full&geometries=geojson&steps=true$straight",
            tries = tries,
            timeoutMs = timeoutMs
        )
        return RouteParser.parse(body, points, fromName)
    }
}
