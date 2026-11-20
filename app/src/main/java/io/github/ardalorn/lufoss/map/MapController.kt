package io.github.ardalorn.lufoss.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.geo.LatLng
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the location button does next. */
enum class LocateMode { FREE, NORTH, HEADING }

/** The parts of the screen the map has to keep clear of. All sizes are in dp. */
class MapLayout(
    val wide: Boolean = false,
    val topInset: Double = 0.0,
    val bottomInset: Double = 0.0,
    val bannerBottom: Double = 0.0,
    val sheetWidth: Double = 0.0,
    val sheetHeight: Double = 0.0,
    val sheetVisible: Boolean = false
)

/**
 * The map camera: a centre point, a zoom and a bearing (the compass direction that points up the screen).
 * While walking it also follows the person's dot, turns the map to their heading and zooms in for turns.
 * All distances are in dp; gestures convert from pixels before calling in.
 */
class MapController(private val scope: CoroutineScope, private val reducedMotion: () -> Boolean) {
    var lat by mutableDoubleStateOf(Cfg.START_LAT)
    var lng by mutableDoubleStateOf(Cfg.START_LNG)
    var z by mutableDoubleStateOf(Cfg.START_Z)
    var bearing by mutableDoubleStateOf(0.0)

    /** The dot's smoothed position while walking. */
    var meV by mutableStateOf<LatLng?>(null)

    /** The way the person faces, degrees clockwise from north; null until known. */
    var heading by mutableStateOf<Double?>(null)
        private set

    var follow by mutableStateOf(false)
    var hdgUp by mutableStateOf(true)
    var navActive by mutableStateOf(false)

    var width = 0.0
        private set
    var height = 0.0
        private set

    // Inputs set by the navigation session.
    var me: LatLng? = null
    var navSim = false
    var navJoined = false
    var navAway = false
    var autoZoom = true
    var zT = Cfg.NAV_ZOOM
    var touching = false

    /** One signal per request for animation frames; the screen's frame loop drains it. */
    val frames = Channel<Unit>(Channel.CONFLATED)

    private var layout = MapLayout()
    private var anchTop = 0.0
    private var anchBottom = 0.0
    private var ancK = HALF
    private var fly: Fly? = null
    private var hdSrc = Cfg.SRC_NONE
    private var hdT = 0L
    private var blend = 1.0
    private var blendT = -1L
    private var camFrom = Cam(0.0, 0.0, 0.0, 0.0)
    private var recentreJob: Job? = null

    private class Cam(val lat: Double, val lng: Double, val z: Double, val b: Double)

    private class Fly(
        val fromLat: Double,
        val fromLng: Double,
        val fromZ: Double,
        val fromB: Double,
        val toLat: Double,
        val toLng: Double,
        val toZ: Double,
        val dB: Double,
        var start: Long = -1L
    )

    val locateMode: LocateMode
        get() = when {
            !navActive || !follow -> LocateMode.FREE
            hdgUp -> LocateMode.HEADING
            else -> LocateMode.NORTH
        }

    // ---- Layout ----

    fun setViewport(w: Double, h: Double) {
        width = w
        height = h
        updateAnchor()
    }

    fun setLayout(value: MapLayout) {
        layout = value
        updateAnchor()
    }

    /** The part of the screen that is free of the banner and the sheet: the dot sits inside it. */
    private fun updateAnchor() {
        val top = if (layout.bannerBottom > 0) layout.bannerBottom + Cfg.ANCHOR_MARGIN else 0.0
        val sheet = if (layout.wide || !layout.sheetVisible) 0.0 else layout.sheetHeight
        val bottom = height - layout.bottomInset - Cfg.ATTRIB_HEIGHT - Cfg.ANCHOR_MARGIN - sheet
        anchTop = top
        anchBottom = max(top + Cfg.FIT_MIN_AREA, bottom)
        if (follow) kick()
    }

    private fun anchorY(): Double = anchTop + (anchBottom - anchTop) * ancK

    // ---- Conversions ----

    /** The place under screen point (x, y). */
    fun geoAt(x: Double, y: Double): LatLng {
        val c = GeoMath.project(lat, lng, z)
        val m = GeoMath.toMap(x - width * HALF, y - height * HALF, bearing)
        return GeoMath.unproject(c.x + m.x, c.y + m.y, z)
    }

    /** Puts place [g] at screen point (x, y) with the given zoom and bearing. */
    private fun placeAt(x: Double, y: Double, g: LatLng, zoom: Double, b: Double) {
        val p = GeoMath.project(g.lat, g.lng, zoom)
        val m = GeoMath.toMap(x - width * HALF, y - height * HALF, b)
        val r = GeoMath.unproject(p.x - m.x, p.y - m.y, zoom)
        z = zoom
        bearing = GeoMath.norm(b)
        lat = GeoMath.clampLat(r.lat)
        lng = r.lng
    }

    /** The view centre that puts place [g] at the anchor with the given zoom and bearing. */
    private fun lockCentre(g: LatLng, zoom: Double, b: Double): LatLng {
        val p = GeoMath.project(g.lat, g.lng, zoom)
        val m = GeoMath.toMap(0.0, anchorY() - height * HALF, b)
        val r = GeoMath.unproject(p.x - m.x, p.y - m.y, zoom)
        return LatLng(GeoMath.clampLat(r.lat), r.lng)
    }

    // ---- Moving the view ----

    fun kick() {
        frames.trySend(Unit)
    }

    fun stopFly() {
        fly = null
    }

    fun panBy(dx: Double, dy: Double) {
        val c = GeoMath.project(lat, lng, z)
        val m = GeoMath.toMap(dx, dy, bearing)
        val r = GeoMath.unproject(c.x - m.x, c.y - m.y, z)
        lat = GeoMath.clampLat(r.lat)
        lng = ((r.lng + LNG_WRAP_OFFSET) % FULL_TURN) - HALF_TURN
    }

    fun zoomAt(x: Double, y: Double, target: Double) {
        stopFly()
        val zoom = target.coerceIn(Cfg.MIN_Z, Cfg.MAX_VIEW_Z)
        if (navActive) {
            // The person's own zoom wins over the automatic zoom until they re-centre.
            autoZoom = false
            armRecentre()
        }
        if (follow && meV != null) {
            // While following, zoom keeps the dot at its spot on the screen.
            z = zoom
            kick()
            return
        }
        placeAt(x, y, geoAt(x, y), zoom, bearing)
    }

    fun zoomCentre(dz: Double) = zoomAt(width * HALF, height * HALF, z + dz)

    fun rotateAt(x: Double, y: Double, b: Double) {
        stopFly()
        placeAt(x, y, geoAt(x, y), z, b)
    }

    fun rotateBy(d: Double) {
        stopFly()
        follow = false
        bearing = GeoMath.norm(bearing + d)
        armRecentre()
    }

    fun flyTo(toLat: Double, toLng: Double, toZ: Double, toBearing: Double? = null) {
        stopFly()
        val tb = toBearing ?: bearing
        if (reducedMotion()) {
            lat = toLat
            lng = toLng
            z = toZ
            bearing = GeoMath.norm(tb)
            return
        }
        fly = Fly(lat, lng, z, bearing, toLat, toLng, toZ, GeoMath.angDiff(bearing, tb))
        kick()
    }

    /** Shows every point, clear of the search bar and the sheet. [extra] is one more point to include. */
    fun fit(lats: DoubleArray, lngs: DoubleArray, extra: LatLng? = null) {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        for (i in lats.indices) {
            minLat = min(minLat, lats[i])
            maxLat = max(maxLat, lats[i])
            minLng = min(minLng, lngs[i])
            maxLng = max(maxLng, lngs[i])
        }
        if (extra != null) {
            minLat = min(minLat, extra.lat)
            maxLat = max(maxLat, extra.lat)
            minLng = min(minLng, extra.lng)
            maxLng = max(maxLng, extra.lng)
        }
        if (minLat > maxLat) return
        val a = GeoMath.project(maxLat, minLng, 0.0)
        val b = GeoMath.project(minLat, maxLng, 0.0)
        val sheetBelow = !layout.wide && layout.sheetVisible
        val padTop = Cfg.FIT_PAD_TOP + layout.topInset
        val padRight = Cfg.FIT_PAD_RIGHT
        val padBottom = Cfg.FIT_PAD_BOTTOM + layout.bottomInset + if (sheetBelow) layout.sheetHeight else 0.0
        val padLeft = if (layout.wide && layout.sheetVisible) {
            layout.sheetWidth + Cfg.FIT_PAD_SHEET_SIDE
        } else {
            Cfg.FIT_PAD_LEFT
        }
        val aw = max(Cfg.FIT_MIN_AREA, width - padLeft - padRight)
        val ah = max(Cfg.FIT_MIN_AREA, height - padTop - padBottom)
        val dx = if (b.x - a.x == 0.0) Cfg.EPSILON else b.x - a.x
        val dy = if (b.y - a.y == 0.0) Cfg.EPSILON else b.y - a.y
        val zoom = (ln(min(aw / dx, ah / dy)) / ln(2.0)).coerceIn(Cfg.MIN_Z, Cfg.FIT_MAX_Z)
        val k = 2.0.pow(zoom)
        val mx = (a.x + b.x) * HALF * k + (width * HALF - (padLeft + aw * HALF))
        val my = (a.y + b.y) * HALF * k + (height * HALF - (padTop + ah * HALF))
        val c = GeoMath.unproject(mx, my, zoom)
        flyTo(c.lat, c.lng, zoom, 0.0)
    }

    // ---- Follow camera ----

    /** Advances every animation by one frame. Returns true while another frame is needed. */
    fun frame(now: Long, dt: Long): Boolean {
        var busy = false
        val jump = reducedMotion()
        val dtMs = dt.toDouble()
        val f = fly
        if (f != null) {
            if (f.start < 0) f.start = now
            val k = min(1.0, (now - f.start).toDouble() / Cfg.FLY_MS)
            val e = 1 - (1 - k).pow(Cfg.EASE_POWER)
            lat = f.fromLat + (f.toLat - f.fromLat) * e
            lng = f.fromLng + (f.toLng - f.fromLng) * e
            z = f.fromZ + (f.toZ - f.fromZ) * e
            bearing = GeoMath.norm(f.fromB + f.dB * e)
            if (k >= 1.0) fly = null else busy = true
        }
        if (navActive) busy = glideDot(jump, dtMs) || busy
        val target = if (hdgUp) Cfg.NAV_ANCHOR else HALF
        if (abs(target - ancK) > Cfg.ANCHOR_DONE) {
            ancK += if (jump) target - ancK else (target - ancK) * (1 - exp(-dtMs / Cfg.ZOOM_SMOOTH_MS))
            busy = true
        } else {
            ancK = target
        }
        if (follow && navActive) busy = followCamera(now, jump, dtMs) || busy
        return busy
    }

    /** Glides the dot to the newest fix. */
    private fun glideDot(jump: Boolean, dtMs: Double): Boolean {
        val m = me ?: return false
        val cur = meV
        if (cur == null || jump) {
            meV = LatLng(m.lat, m.lng)
            return false
        }
        val k = 1 - exp(-dtMs / Cfg.ME_SMOOTH_MS)
        val dLat = m.lat - cur.lat
        val dLng = m.lng - cur.lng
        if (abs(dLat) + abs(dLng) > Cfg.POS_DONE_DEG) {
            meV = LatLng(cur.lat + dLat * k, cur.lng + dLng * k)
            return true
        }
        meV = LatLng(m.lat, m.lng)
        return false
    }

    private fun followCamera(now: Long, jump: Boolean, dtMs: Double): Boolean {
        val dot = meV ?: return false
        val tz = if (autoZoom) zT else z
        val tb = if (hdgUp) heading ?: bearing else 0.0
        if (blend < 1) {
            // Swing onto the person.
            if (blendT < 0) blendT = now
            blend = if (jump) 1.0 else min(1.0, (now - blendT).toDouble() / Cfg.FOLLOW_ENGAGE_MS)
            val e = 1 - (1 - blend).pow(Cfg.EASE_POWER)
            val from = camFrom
            val zoom = from.z + (tz - from.z) * e
            val b = GeoMath.norm(from.b + GeoMath.angDiff(from.b, tb) * e)
            val c = lockCentre(dot, zoom, b)
            z = zoom
            bearing = b
            lat = from.lat + (c.lat - from.lat) * e
            lng = from.lng + (c.lng - from.lng) * e
            return true
        }
        var busy = false
        val dz = tz - z
        val db = GeoMath.angDiff(bearing, tb)
        if (abs(dz) > Cfg.ZOOM_DONE || abs(db) > Cfg.BEARING_DONE) {
            busy = true
            z += if (jump) dz else dz * (1 - exp(-dtMs / Cfg.ZOOM_SMOOTH_MS))
            bearing = GeoMath.norm(bearing + if (jump) db else db * (1 - exp(-dtMs / Cfg.BEARING_SMOOTH_MS)))
        } else {
            z = tz
            bearing = GeoMath.norm(bearing + db)
        }
        val c = lockCentre(dot, z, bearing)
        lat = c.lat
        lng = c.lng
        return busy
    }

    fun engageFollow() {
        stopFly()
        follow = true
        camFrom = Cam(lat, lng, z, bearing)
        blend = if (meV != null) 0.0 else 1.0
        blendT = -1L
        kick()
    }

    fun recentre() {
        recentreJob?.cancel()
        if (!navActive) return
        autoZoom = true
        engageFollow()
    }

    /** After the person lets go of the map while walking, start the countdown to following them again. */
    fun armRecentre() {
        recentreJob?.cancel()
        if (!navActive || follow) return
        recentreJob = scope.launch {
            delay(Cfg.AUTO_RECENTRE_MS)
            if (navActive && !follow && !touching && (navSim || navJoined) && !navAway) recentre()
        }
    }

    /** Switches the camera into walking mode: no dot yet, heading-up, automatic zoom. */
    fun beginNavigation(sim: Boolean) {
        navActive = true
        navSim = sim
        navJoined = false
        navAway = false
        autoZoom = true
        zT = Cfg.NAV_ZOOM
        hdgUp = true
        me = null
        meV = null
        resetHeading()
    }

    /** Leaves walking mode and turns the map back to north up. */
    fun endNavigation() {
        recentreJob?.cancel()
        navActive = false
        navSim = false
        navJoined = false
        navAway = false
        me = null
        meV = null
        follow = false
        resetHeading()
        if (bearing != 0.0) flyTo(lat, lng, z, 0.0)
    }

    fun beginTouch() {
        touching = true
        stopFly()
        recentreJob?.cancel()
    }

    fun endTouch() {
        touching = false
        armRecentre()
    }

    fun northUp() {
        if (navActive) hdgUp = false
        if (navActive && follow) kick() else flyTo(lat, lng, z, 0.0)
    }

    // ---- Heading: GPS direction of travel while moving, else the compass, else the route ahead ----

    fun resetHeading() {
        heading = null
        hdSrc = Cfg.SRC_NONE
        hdT = 0L
    }

    fun setHeading(h: Double?, src: Int) {
        if (h == null || !h.isFinite()) return
        // A preview walk follows the route, not the phone.
        if (navActive && navSim && src != Cfg.SRC_ROUTE) return
        val now = System.nanoTime() / NS_PER_MS
        if (src < hdSrc && now - hdT < Cfg.HEADING_HOLD_MS) return
        val gain = when (src) {
            Cfg.SRC_GPS -> Cfg.GAIN_GPS
            Cfg.SRC_COMPASS -> Cfg.GAIN_COMPASS
            else -> Cfg.GAIN_ROUTE
        }
        val cur = heading
        if (cur == null || src != hdSrc) {
            heading = GeoMath.norm(h)
        } else {
            val d = GeoMath.angDiff(cur, h)
            if (src == Cfg.SRC_COMPASS && abs(d) < Cfg.HEADING_DEADBAND_DEG) {
                hdT = now
                return
            }
            heading = GeoMath.norm(cur + d * gain)
        }
        hdSrc = src
        hdT = now
        kick()
    }

    private companion object {
        const val HALF = 0.5
        const val HALF_TURN = 180.0
        const val FULL_TURN = 360.0
        const val LNG_WRAP_OFFSET = 540.0
        const val NS_PER_MS = 1_000_000L
    }
}
