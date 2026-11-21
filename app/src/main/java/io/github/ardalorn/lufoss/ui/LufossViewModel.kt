package io.github.ardalorn.lufoss.ui

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.ApiClient
import io.github.ardalorn.lufoss.data.Fix
import io.github.ardalorn.lufoss.data.Geocoder
import io.github.ardalorn.lufoss.data.LoopPlanner
import io.github.ardalorn.lufoss.data.Place
import io.github.ardalorn.lufoss.data.Prefs
import io.github.ardalorn.lufoss.data.Region
import io.github.ardalorn.lufoss.data.Route
import io.github.ardalorn.lufoss.data.Router
import io.github.ardalorn.lufoss.data.fmtDist
import io.github.ardalorn.lufoss.data.fmtTime
import io.github.ardalorn.lufoss.device.CompassSource
import io.github.ardalorn.lufoss.device.LocationSource
import io.github.ardalorn.lufoss.device.Speaker
import io.github.ardalorn.lufoss.geo.LatLng
import io.github.ardalorn.lufoss.map.MapController
import io.github.ardalorn.lufoss.map.TileStore
import io.github.ardalorn.lufoss.nav.NavListener
import io.github.ardalorn.lufoss.nav.NavSession
import io.github.ardalorn.lufoss.nav.NavUi
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the sheet over the map shows. */
sealed interface Sheet {
    data object None : Sheet

    data object LoopPicker : Sheet

    data object PlaceCard : Sheet

    data object Plan : Sheet

    data object Walking : Sheet

    data class Arrived(val name: String) : Sheet
}

/** The location permission card. */
enum class PermCard { ASK, DENIED }

/** All of the app's state and what the buttons do. The map camera lives in [map]. */
class LufossViewModel(app: Application) :
    AndroidViewModel(app),
    NavListener {
    private val api = ApiClient(File(app.cacheDir, "http"))
    private val geocoder = Geocoder(api)
    private val router = Router(api)
    private val loops = LoopPlanner(router)
    private val location = LocationSource(app)
    private val compass = CompassSource(app)
    private val speaker = Speaker(app)

    val prefs = Prefs(app)
    val home: String = Region.homeCountry()
    val homeName: String = if (home.isEmpty()) "" else Region.displayName(home)

    // ---- Messages ----

    /** Spoken to screen readers through a live region. */
    var status by mutableStateOf("")
        private set

    var error by mutableStateOf("")
        private set

    var tileError by mutableStateOf(false)
        private set

    // ---- The map and what is on it ----

    val map = MapController(viewModelScope) { reducedMotion() }
    val tiles = TileStore(api, viewModelScope) { failed -> tileError = failed }

    var start by mutableStateOf<Place?>(null)
        private set
    var dest by mutableStateOf<Place?>(null)
        private set
    var route by mutableStateOf<Route?>(null)
        private set
    var orig by mutableStateOf<LatLng?>(null)
        private set
    var meFix by mutableStateOf<Fix?>(null)
        private set

    var sheet by mutableStateOf<Sheet>(Sheet.None)
        private set
    var stepsOpen by mutableStateOf(false)
    var planning by mutableStateOf(false)
        private set
    var navActive by mutableStateOf(false)
        private set
    var navUi by mutableStateOf(NavUi())
        private set

    // ---- Dialogs and cards ----

    var picking by mutableStateOf(false)
        private set
    var startDialog by mutableStateOf(false)
        private set
    var aboutOpen by mutableStateOf(false)
    var perm by mutableStateOf<PermCard?>(null)
        private set
    var dialogError by mutableStateOf("")
        private set

    /** Bumps when the screen should show the system permission dialog. */
    var permRequest by mutableIntStateOf(0)
        private set

    /** Bumps when the screen should fit the route into the free part of the map, after layout settles. */
    var fitRequest by mutableIntStateOf(0)
        private set

    var voice by mutableStateOf(prefs.voice)
        private set
    var localOnly by mutableStateOf(prefs.localOnly)
        private set

    private var nav: NavSession? = null
    private var loopJob: Job? = null
    private var permCallback: ((Boolean) -> Unit)? = null
    private var afterStart: (() -> Unit)? = null

    private fun countryFilter(): CountryFilter? =
        if (home.isNotEmpty() && localOnly) CountryFilter(home, homeName) else null

    val search = SearchModel(viewModelScope, geocoder, ::countryFilter, ::say, ::fail)
    val startSearch = SearchModel(viewModelScope, geocoder, ::countryFilter, ::sayStartStatus) { dialogError = it }

    var startStatus by mutableStateOf("")
        private set

    init {
        start = prefs.start
        speaker.enabled = voice
        start?.let { saved ->
            map.lat = saved.lat
            map.lng = saved.lng
            map.z = Cfg.PLACE_ZOOM
            // Older saved coordinates may lack a house-level address: look it up again.
            if (saved.addr.isEmpty() || saved.houseNumber.isEmpty()) nameSpot(saved)
        }
        if (start == null) openStart()
    }

    // ---- Small helpers ----

    fun say(text: String) {
        status = text
    }

    private fun sayStartStatus(text: String) {
        startStatus = text
    }

    fun fail(text: String) {
        error = text
    }

    fun clearError() {
        error = ""
    }

    private fun reducedMotion(): Boolean = Settings.Global.getFloat(
        getApplication<Application>().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    fun requestFit() {
        fitRequest++
    }

    fun performFit() {
        route?.let { map.fit(it.lats, it.lngs) }
    }

    // ---- Search and places ----

    fun pickResult(place: Place) {
        if (picking) {
            // Choosing a start point: just move the map there.
            search.query = place.name
            search.end()
            map.flyTo(place.lat, place.lng, Cfg.PLACE_ZOOM)
            say("Now tap the map where your walks should begin.")
            return
        }
        dest = place
        route = null
        orig = null
        search.query = place.name
        search.end()
        showPlace()
        map.flyTo(place.lat, place.lng, Cfg.PLACE_ZOOM)
    }

    private fun showPlace() {
        cancelLoop()
        val d = dest ?: return
        sheet = Sheet.PlaceCard
        say("${d.name} selected.")
    }

    fun onTap(x: Double, y: Double) {
        if (navActive) return
        val g = map.geoAt(x, y)
        clearError()
        if (picking) {
            picking = false
            saveStart(Place(g.lat, g.lng, PINNED, "", PINNED), lookup = true, after = afterStart)
            return
        }
        val coords = String.format(Locale.UK, "%.5f, %.5f", g.lat, g.lng)
        dest = Place(g.lat, g.lng, "Dropped pin", coords, "Dropped pin, $coords")
        route = null
        orig = null
        search.end()
        showPlace()
    }

    fun clearAll() {
        cancelLoop()
        dest = null
        orig = null
        route = null
        endPick(false)
        sheet = Sheet.None
        say("")
        clearError()
    }

    fun toggleLocalOnly() {
        localOnly = !localOnly
        prefs.localOnly = localOnly
        search.rerun()
    }

    // ---- Directions ----

    fun directions() {
        if (planning) return
        cancelLoop()
        clearError()
        val s = start
        if (s == null) {
            say("Set a start point to get directions.")
            openStart(::directions)
            return
        }
        // Runs as the planning job, so the sheet can show progress and Close cancels the request.
        planning = true
        loopJob = viewModelScope.launch {
            val self = coroutineContext[Job]
            try {
                if (getRoute(s)) showRoute() else showPlace()
            } finally {
                if (loopJob === self) planning = false
            }
        }
    }

    private suspend fun getRoute(from: Place): Boolean {
        val d = dest ?: return false
        clearError()
        say("Finding a walking route…")
        return try {
            route = router.fetchRoute(listOf(from.latLng, d.latLng), from.name.ifEmpty { "Your location" })
            orig = from.latLng
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("No walking route could be found between those points.")
            say("")
            false
        }
    }

    private fun showRoute() {
        val r = route ?: return
        sheet = Sheet.Plan
        stepsOpen = false
        val time = fmtTime(r.dur)
        val head = if (r.loop) "Walk planned. $time round trip, " else "Walking route found. $time, "
        say(head + fmtDist(r.dist, true) + ".")
        requestFit()
    }

    // ---- Round-trip walks ----

    fun showLoopPicker() {
        if (navActive) return
        if (start == null) {
            say("Set a start point to plan a walk.")
            openStart(::showLoopPicker)
            return
        }
        cancelLoop()
        dest = null
        orig = null
        route = null
        search.end()
        clearError()
        sheet = Sheet.LoopPicker
        say("Choose how long to walk.")
    }

    fun runLoop(minutes: Int) {
        val s = start ?: return
        clearError()
        loopJob?.cancel()
        planning = true
        loopJob = viewModelScope.launch {
            val result = try {
                loops.plan(s, minutes) { attempt -> say("Planning a $minutes minute walk… attempt $attempt") }
            } finally {
                planning = false
            }
            if (result == null) {
                showLoopPicker()
                fail("No walk could be planned from your start point. Try a different start point.")
                return@launch
            }
            route = result.route.asLoop(minutes, result.overlap)
            orig = s.latLng
            dest = Place(s.lat, s.lng, s.label.ifEmpty { "your start point" }, "", "")
            showRoute()
        }
    }

    fun anotherLoop() {
        route?.let { if (it.loop) runLoop(it.minutes) }
    }

    private fun cancelLoop() {
        loopJob?.cancel()
        loopJob = null
        planning = false
    }

    // ---- Start point ----

    fun openStart(after: (() -> Unit)? = null) {
        if (navActive) return
        afterStart = after
        startSearch.end()
        startSearch.query = ""
        dialogError = ""
        startStatus = ""
        startDialog = true
    }

    fun dismissStart() {
        startDialog = false
        afterStart = null
        if (start == null) say("No start point is set yet. Use the flag button to set one.")
    }

    fun chooseStartOnMap() {
        picking = true
        startDialog = false
        search.end()
        clearError()
        say("Tap the map where your walks should begin.")
    }

    fun endPick(back: Boolean) {
        if (!picking) return
        picking = false
        say("")
        if (back) openStart(afterStart)
    }

    fun pickStartSuggestion(place: Place) = saveStart(place, lookup = false, after = afterStart)

    fun useGpsForStart() {
        dialogError = ""
        startStatus = ""
        clearError()
        val resume = afterStart
        afterStart = null
        startDialog = false
        say("Getting your GPS position…")
        withLocationPermission { granted ->
            if (!granted) {
                fail("GPS could not provide a usable position. Your saved start point has not changed.")
                return@withLocationPermission
            }
            viewModelScope.launch {
                val fix = try {
                    location.bestFix()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (fix == null) {
                    fail("GPS could not provide a usable position. Your saved start point has not changed.")
                } else {
                    saveStart(Place(fix.lat, fix.lng, CURRENT, "", CURRENT), lookup = true, after = resume)
                }
            }
        }
    }

    private fun saveStart(p: Place, lookup: Boolean, after: (() -> Unit)?) {
        val name = p.name.ifEmpty { PINNED }
        val full = p.fullAddress.ifEmpty { listOf(name, p.addr).filter { it.isNotBlank() }.joinToString(", ") }
        val saved = p.copy(name = name, fullAddress = full)
        start = saved
        prefs.start = saved
        afterStart = null
        picking = false
        startDialog = false
        search.end()
        val note = if (saved.approx) " (street only; house number not verified)." else "."
        say("Start point set to ${saved.label}$note")
        if (lookup && saved.houseNumber.isEmpty()) nameSpot(saved)
        map.flyTo(saved.lat, saved.lng, Cfg.PLACE_ZOOM)
        when {
            after != null -> after()
            dest != null && route != null && !navActive -> directions()
        }
    }

    /** Looks up the address of a start point given only as coordinates, and keeps the better answer. */
    private fun nameSpot(spot: Place) {
        viewModelScope.launch {
            val found = geocoder.reverse(spot.lat, spot.lng)
            if (start !== spot) return@launch
            if (found == null || (found.name == "Unnamed place" && found.addr.isEmpty())) {
                say("The start coordinates were saved, but no usable address or house number came back.")
                return@launch
            }
            val number = found.houseNumber.ifEmpty { spot.houseNumber }
            val keepHouse = spot.houseNumber.isNotEmpty() && found.houseNumber.isEmpty()
            val name = if (keepHouse) spot.name else found.name.ifEmpty { spot.name }
            val addr = if (keepHouse) spot.addr.ifEmpty { found.addr } else found.addr.ifEmpty { spot.addr }
            val ownFull = if (keepHouse) spot.fullAddress else found.fullAddress
            val updated = spot.copy(
                name = name,
                addr = addr,
                fullAddress = ownFull.ifEmpty { listOf(name, addr).filter { it.isNotBlank() }.joinToString(", ") },
                houseNumber = number,
                street = found.street.ifEmpty { spot.street },
                approx = number.isEmpty() && found.approx
            )
            start = updated
            prefs.start = updated
            route?.let { if (it.from == spot.name) route = it.withFrom(updated.label) }
            say(
                if (number.isNotEmpty()) {
                    "Start point address updated: ${updated.label}."
                } else {
                    "Only a street-level address was returned; the house number could not be verified."
                }
            )
        }
    }

    // ---- Location permission ----

    /** Runs [then] with true once location is allowed, or with false if the person declines. */
    fun withLocationPermission(then: (Boolean) -> Unit) {
        if (location.hasPermission()) {
            then(true)
            return
        }
        permCallback = then
        perm = PermCard.ASK
        say("Lufoss would like to use your location.")
    }

    fun onPermAllow() {
        clearError()
        permRequest++
    }

    fun onPermSkip() {
        val cb = permCallback
        permCallback = null
        perm = null
        say("Location stays off. You can still use Preview to try the walk.")
        cb?.invoke(false)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            val cb = permCallback
            permCallback = null
            perm = null
            cb?.invoke(true)
        } else {
            perm = PermCard.DENIED
            say("Location is blocked for this app.")
        }
    }

    // ---- Walking ----

    fun startWalk(sim: Boolean) {
        val r = route ?: return
        val d = dest ?: return
        val s = start ?: return
        if (!sim && !location.hasPermission()) {
            withLocationPermission { granted ->
                if (granted) startWalk(false) else fail(NEEDS_LOCATION)
            }
            return
        }
        clearError()
        stopNav()
        speaker.enabled = voice
        speaker.prime()
        search.end()
        val session = NavSession(r, d.latLng, s, sim, map, router, location, compass, speaker, viewModelScope, this)
        nav = session
        navActive = true
        navUi = NavUi()
        stepsOpen = false
        sheet = Sheet.Walking
        session.begin()
    }

    fun stopNav() {
        nav?.stop()
        nav = null
        navActive = false
        meFix = null
        speaker.stop()
    }

    fun endWalk() {
        stopNav()
        if (route != null) showRoute() else clearAll()
    }

    fun closeSheet() {
        stopNav()
        clearAll()
    }

    fun toggleVoice() {
        voice = !voice
        prefs.voice = voice
        speaker.enabled = voice
        if (voice) {
            speaker.prime()
            speaker.speak("Voice on")
        }
    }

    fun onLocate() {
        clearError()
        if (!navActive) return
        if (!map.follow) {
            map.recentre()
            say("Following you again.")
        } else {
            map.hdgUp = !map.hdgUp
            map.kick()
            say(if (map.hdgUp) "The map turns with your heading." else "North is at the top.")
        }
    }

    fun onCompass() {
        map.northUp()
        say("North is at the top.")
    }

    // ---- NavListener ----

    override fun onUi(ui: NavUi) {
        navUi = ui
    }

    override fun onFix(fix: Fix) {
        meFix = fix
    }

    override fun onRoute(route: Route) {
        this.route = route
        orig = route.lats.firstOrNull()?.let { LatLng(it, route.lngs[0]) }
    }

    override fun onStatus(text: String) = say(text)

    override fun onError(text: String) = fail(text)

    override fun onArrive() {
        val name = dest?.name.orEmpty()
        stopNav()
        sheet = Sheet.Arrived(name)
        say("You have arrived at $name.")
        speaker.speak("You have arrived.")
    }

    override fun onCleared() {
        nav?.stop()
        speaker.shutdown()
        super.onCleared()
    }

    private companion object {
        const val PINNED = "Pinned location"
        const val CURRENT = "Current location"
        const val NEEDS_LOCATION = "Start needs your location. Use Preview to try the walk without GPS."
    }
}
