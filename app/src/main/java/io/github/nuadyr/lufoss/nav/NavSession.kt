package io.github.nuadyr.lufoss.nav

import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.data.Fix
import io.github.nuadyr.lufoss.data.LoopPlanner
import io.github.nuadyr.lufoss.data.Place
import io.github.nuadyr.lufoss.data.Route
import io.github.nuadyr.lufoss.data.Router
import io.github.nuadyr.lufoss.data.arrowFor
import io.github.nuadyr.lufoss.data.fmtArrival
import io.github.nuadyr.lufoss.data.fmtDist
import io.github.nuadyr.lufoss.data.fmtTime
import io.github.nuadyr.lufoss.data.instruction
import io.github.nuadyr.lufoss.device.CompassSource
import io.github.nuadyr.lufoss.device.LocationSource
import io.github.nuadyr.lufoss.device.Speaker
import io.github.nuadyr.lufoss.geo.GeoMath
import io.github.nuadyr.lufoss.geo.LatLng
import io.github.nuadyr.lufoss.map.MapController
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the banner and the walking sheet show. [idx] is the last step already done. */
class NavUi(
    val arrow: String = "",
    val text: String = "",
    val dist: String = "",
    val eta: String = "",
    val etaDetail: String = "",
    val idx: Int = -1
)

/** How a walk reports back to the screen. */
interface NavListener {
    fun onUi(ui: NavUi)

    fun onFix(fix: Fix)

    fun onRoute(route: Route)

    fun onStatus(text: String)

    fun onError(text: String)

    fun onArrive()
}

/**
 * One walk along a route: follows GPS (or a preview that walks the route by itself), keeps track of progress,
 * says each turn at three distances, re-routes when the person leaves the route and drives the follow camera.
 */
class NavSession(
    initialRoute: Route,
    private val dest: LatLng,
    private val startPlace: Place,
    private val sim: Boolean,
    private val map: MapController,
    private val router: Router,
    private val location: LocationSource,
    private val compass: CompassSource,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
    private val listener: NavListener
) {
    var route: Route = initialRoute
        private set

    private var idx = -1
    private var along = 0.0
    private var lowAcc = false
    private var lastReroute = 0L
    private var prev: LatLng? = null
    private var saidStep = -1
    private var saidStage = -1
    private var lead = ""
    private var declination = 0f
    private var haveDeclination = false
    private var active = false
    private var watchJob: Job? = null
    private var compassJob: Job? = null
    private var rerouteJob: Job? = null
    private var lastFix: Fix? = null

    fun begin() {
        active = true
        lastReroute = now()
        val first = route.steps.firstOrNull()?.let { instruction(it, route.loop) + ". " }.orEmpty()
        lead = "Starting navigation. $first"
        map.beginNavigation(sim)
        if (sim) {
            watchJob = scope.launch { runPreview() }
        } else {
            watchJob = scope.launch { watchLocation() }
            compassJob = scope.launch { watchCompass() }
        }
        map.engageFollow()
    }

    fun stop() {
        if (!active) return
        active = false
        watchJob?.cancel()
        compassJob?.cancel()
        rerouteJob?.cancel()
        map.endNavigation()
    }

    // ---- Sources of position and heading ----

    private suspend fun runPreview() {
        var d = 0.0
        while (true) {
            val p = RouteMath.pointAt(route, d)
            d += Cfg.SIM_STEP_M
            onFix(Fix(p.lat, p.lng, 0.0, System.currentTimeMillis()))
            delay(Cfg.SIM_TICK_MS)
        }
    }

    private suspend fun watchLocation() {
        try {
            location.fixes().collect { onFix(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            listener.onError("Location permission was refused. Allow it to follow your walk, or use Preview.")
        } catch (e: IllegalStateException) {
            listener.onError(e.message ?: "Lost your location signal.")
        }
    }

    private suspend fun watchCompass() {
        if (!compass.available) return
        compass.headings { declination }.collect { map.setHeading(it, Cfg.SRC_COMPASS) }
    }

    // ---- One position reading ----

    fun onFix(fix: Fix) {
        if (!active) return
        if (!sim && fix.acc > Cfg.MAX_NAV_ACC_M) {
            // Ignore coarse fixes while walking.
            if (!lowAcc) {
                lowAcc = true
                listener.onStatus("Waiting for a more accurate location…")
            }
            return
        }
        lowAcc = false
        lastFix = fix
        if (!haveDeclination && !sim) {
            declination = CompassSource.declinationAt(fix.lat, fix.lng)
            haveDeclination = true
        }
        map.me = fix.latLng
        listener.onFix(fix)
        val reach = if (route.loop) along + Cfg.LOOP_AHEAD_M else Double.POSITIVE_INFINITY
        var s = RouteMath.snap(route, fix.lat, fix.lng, max(0.0, along - Cfg.SNAP_BACK_M), reach)
        if (s.d > Cfg.REROUTE_M && along > 0) {
            // The forward search found nothing near: look everywhere.
            val g = RouteMath.snap(route, fix.lat, fix.lng, 0.0, reach)
            if (g.d < s.d) s = g
        }
        if (!sim && !map.navJoined) {
            if (s.d > Cfg.REROUTE_M) {
                pointToRoute(fix, s.d)
                return
            }
            map.navJoined = true
            map.navAway = false
            map.engageFollow()
        }
        if (s.d > Cfg.REROUTE_M && !sim && now() - lastReroute > Cfg.REROUTE_MIN_MS) {
            lastReroute = now()
            reroute(fix)
            return
        }
        along = s.along
        updateHeading(fix, s.along)
        val remaining = route.total - s.along
        if (remaining <= Cfg.ARRIVE_M) {
            listener.onArrive()
            return
        }
        if (route.steps.isEmpty()) return
        announceAndPaint(remaining)
    }

    /** Not on the route yet: no new route from GPS, just point to its start. */
    private fun pointToRoute(fix: Fix, distance: Double) {
        listener.onUi(NavUi(arrow = "⚑", text = "Head to the route", dist = fmtDist(distance) + " away"))
        if (!map.navAway) {
            map.navAway = true
            map.follow = false
            val text = "You are ${fmtDist(distance, true)} from the route. Walk to its start to begin."
            listener.onStatus(text)
            speaker.speak(text)
            map.fit(route.lats, route.lngs, fix.latLng)
        }
        map.kick()
    }

    private fun announceAndPaint(remaining: Double) {
        var i = 0
        route.starts.forEachIndexed { j, d -> if (d <= along) i = j }
        i = max(i, idx) // Never step backwards.
        idx = i
        val next = min(idx + 1, route.steps.lastIndex)
        val toTurn = route.starts[next] - along
        val stage = when {
            toTurn <= Cfg.ANNOUNCE_NEAR_M -> Cfg.STAGE_TURN
            toTurn <= Cfg.ANNOUNCE_FAR_M -> Cfg.STAGE_NEAR
            else -> Cfg.STAGE_FAR
        }
        if (saidStep != next) {
            saidStep = next
            saidStage = -1
        }
        if (stage > saidStage) {
            saidStage = stage
            val text = spokenStep(next, toTurn, stage)
            listener.onStatus(text)
            speaker.speak(text)
        }
        paint(remaining, next)
        map.zT = when {
            toTurn <= Cfg.ZOOM_NEAR_M -> Cfg.NAV_ZOOM_NEAR
            toTurn <= Cfg.ZOOM_MID_M -> Cfg.NAV_ZOOM_MID
            else -> Cfg.NAV_ZOOM
        }
        map.kick()
    }

    /** Wording for the next turn: far ("In 450 metres, turn left"), close, and at the turn ("Turn left"). */
    private fun spokenStep(i: Int, distance: Double, stage: Int): String {
        val step = route.steps[i]
        val said = lead
        lead = ""
        val words = instruction(step, route.loop)
        if (stage >= Cfg.STAGE_TURN) {
            return if (said.isNotEmpty()) said + "Then " + words.lowercase() else words
        }
        val opening = if (said.isNotEmpty()) "Then in " else "In "
        return said + opening + fmtDist(max(0.0, distance), true) + ", " + words.lowercase()
    }

    private fun paint(remaining: Double, next: Int) {
        val step = route.steps[next]
        val speed = route.dist / max(1.0, route.dur)
        listener.onUi(
            NavUi(
                arrow = arrowFor(step),
                text = instruction(step, route.loop),
                dist = "in " + fmtDist(max(0.0, route.starts[next] - along)),
                eta = fmtTime(remaining / speed),
                etaDetail = fmtDist(remaining) + " · arrive " + fmtArrival(remaining / speed),
                idx = idx
            )
        )
    }

    // ---- Which way the person faces ----

    private fun updateHeading(fix: Fix, alongNow: Double) {
        if (sim) {
            map.setHeading(RouteMath.routeHeading(route, alongNow), Cfg.SRC_ROUTE)
            return
        }
        val here = fix.latLng
        var h: Double? = null
        val course = fix.course
        val speed = fix.speed
        val last = prev
        val movedEnough = last != null && GeoMath.metresBetween(last, here) >= Cfg.MOVE_BEARING_M
        if (course != null && speed != null && speed >= Cfg.MOVING_SPEED_MS) {
            h = course
        } else if (fix.acc <= Cfg.GOOD_ACC_M && last != null && movedEnough) {
            h = GeoMath.bearingTo(last, here)
        }
        if (last == null || movedEnough) prev = here
        if (h != null) {
            map.setHeading(h, Cfg.SRC_GPS)
        } else {
            map.setHeading(RouteMath.routeHeading(route, alongNow), Cfg.SRC_ROUTE)
        }
    }

    // ---- Off the route ----

    private fun reroute(from: Fix) {
        rerouteJob?.cancel()
        rerouteJob = scope.launch {
            val fresh = if (route.loop) loopReroute(from) else plainReroute(from)
            if (fresh != null && active) {
                route = fresh
                idx = -1
                along = 0.0
                saidStep = -1
                saidStage = -1
                lead = "Rerouted. "
                listener.onRoute(fresh)
                onFix(from)
            }
        }
    }

    private suspend fun plainReroute(from: Fix): Route? = try {
        router.fetchRoute(listOf(from.latLng, dest), "Your location")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        listener.onError("No walking route could be found between those points.")
        null
    }

    /** Off the loop while walking: route from where the person is through the corners still ahead, then home. */
    private suspend fun loopReroute(from: Fix): Route? = try {
        val ahead = route.wps.filter { it.at > along + Cfg.LOOP_WP_PASSED_M }.map { LatLng(it.lat, it.lng) }
        val fresh = router.fetchRoute(listOf(from.latLng) + ahead + startPlace.latLng, "Your location")
        fresh.asLoop(route.minutes, LoopPlanner.overlapShare(fresh))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun now(): Long = System.nanoTime() / NS_PER_MS

    private companion object {
        const val NS_PER_MS = 1_000_000L
    }
}
