package io.github.nuadyr.lufoss.data

import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.geo.LatLng

/** A place the person can search for, pin or start from. */
data class Place(
    val lat: Double,
    val lng: Double,
    val name: String,
    val addr: String,
    val fullAddress: String,
    val houseNumber: String = "",
    val street: String = "",
    val approx: Boolean = false
) {
    val latLng: LatLng get() = LatLng(lat, lng)

    /** The single line used wherever the whole address is shown. */
    val label: String
        get() = fullAddress.ifBlank { listOf(name, addr).filter { it.isNotBlank() }.joinToString(", ") }
}

/** One location reading. [acc] is infinite when the device could not say how accurate it is. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val acc: Double,
    val t: Long,
    val course: Double? = null,
    val speed: Double? = null
) {
    val latLng: LatLng get() = LatLng(lat, lng)
}

/** One manoeuvre. [d] is the length of the step in metres; [mod] is the router's turn word, if any. */
data class Step(val d: Double, val name: String, val type: String, val mod: String?)

/** A stop in the middle of a route (a corner of a round-trip walk). [at] is the distance along the route. */
data class Waypoint(val lat: Double, val lng: Double, val at: Double)

/** A walking route with the cumulative distance along its line, so progress can be measured. */
class Route(
    val lats: DoubleArray,
    val lngs: DoubleArray,
    val cum: DoubleArray,
    val steps: List<Step>,
    val starts: DoubleArray,
    val wps: List<Waypoint>,
    val dist: Double,
    val dur: Double,
    val total: Double,
    val from: String,
    val loop: Boolean = false,
    val minutes: Int = 0,
    val overlap: Double = 0.0
) {
    val size: Int get() = lats.size

    fun asLoop(minutes: Int, overlap: Double): Route =
        Route(lats, lngs, cum, steps, starts, wps, dist, dur, total, from, true, minutes, overlap)

    fun withFrom(label: String): Route =
        Route(lats, lngs, cum, steps, starts, wps, dist, dur, total, label, loop, minutes, overlap)
}

/** Builds a [Place], preferring a numbered street address over a generic label. */
@Suppress("LongParameterList")
fun placeRecord(
    lat: Double,
    lng: Double,
    preferredName: String?,
    houseNumber: String?,
    street: String?,
    details: List<String?> = emptyList(),
    approx: Boolean = false
): Place {
    val number = houseNumber.orEmpty().trim()
    val road = street.orEmpty().trim()
    val numberedStreet = listOf(number, road).filter { it.isNotEmpty() }.joinToString(" ")
    val preferred = preferredName.orEmpty().trim()
    val name = when {
        numberedStreet.isNotEmpty() && (preferred.isEmpty() || preferred == road || preferred == number) ->
            numberedStreet
        preferred.isNotEmpty() -> preferred
        numberedStreet.isNotEmpty() -> numberedStreet
        else -> details.firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Unnamed place"
    }
    val extra = if (name == numberedStreet) "" else numberedStreet
    val candidates: List<String?> = listOf<String?>(extra) + details
    val parts = mutableListOf<String>()
    candidates.forEach { part ->
        val value = part.orEmpty().trim()
        if (value.isNotEmpty() && value != name && value !in parts) {
            parts += value
        }
    }
    val fullAddress = (listOf(name) + parts).filter { it.isNotEmpty() }.joinToString(", ")
    return Place(
        lat = lat,
        lng = lng,
        name = name,
        addr = parts.joinToString(", ").take(Cfg.ADDR_MAX),
        fullAddress = fullAddress,
        houseNumber = number,
        street = road,
        approx = approx
    )
}
