package io.github.nuadyr.lufoss.data

import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.geo.GeoMath
import io.github.nuadyr.lufoss.geo.LatLng
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** Places found by a search, plus an optional note to show above them. */
class SearchResult(val places: List<Place>, val note: String? = null)

private val NUMBER_START = Regex("""^\s*\d+[a-z]?(?:[-/]\d+[a-z]?)?[\s,]""", RegexOption.IGNORE_CASE)
private val HOUSE_AND_STREET = Regex("""^\s*(\d+[a-z]?(?:[-/]\d+[a-z]?)?)[\s,]+(.+)$""", RegexOption.IGNORE_CASE)
private const val NOMINATIM_DOWN_NOTE =
    "The exact address service did not answer; checking other map data sources."
private const val REGEX_SPECIALS = ".*+?^\$()|[]{}\\\""

/** True when the text starts with a house number, a suffix, a range or a split number. */
fun wantsNumber(text: String): Boolean = NUMBER_START.containsMatchIn(text)

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

private fun JSONObject.firstOf(vararg keys: String): String =
    keys.map { str(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

private inline fun <T> attempt(block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

private fun quoteRegex(value: String): String = buildString {
    value.trim().forEach { ch ->
        if (ch in REGEX_SPECIALS) append('\\')
        append(ch)
    }
}

/**
 * Place search and address lookup, all from free, keyless OpenStreetMap-based services:
 * Photon while typing, Nominatim for submitted searches, Overpass for numbered buildings.
 */
class Geocoder(private val api: ApiClient) {
    private val cache = TtlCache<SearchResult>()

    /** [country] is an upper-case two-letter code that results are limited to, or null for everywhere. */
    suspend fun search(text: String, exact: Boolean, country: String?): SearchResult {
        val key = (country ?: "*") + (if (exact) "!" else "") + text.lowercase()
        cache.get(key)?.let { return it }
        val result = geocode(text, exact, country)
        cache.put(key, result, if (result.places.isNotEmpty()) Cfg.CACHE_OK_MS else Cfg.CACHE_MISS_MS)
        return result
    }

    private suspend fun photon(text: String, country: String?): List<Place> {
        val limit = if (country != null) Cfg.SEARCH_FETCH_LIMIT else Cfg.SEARCH_LIMIT
        val body = api.fetchText("${Cfg.GEOCODE_URL}?limit=$limit&lang=en&q=${enc(text)}")
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val p = f.optJSONObject("properties") ?: continue
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (out.size >= Cfg.SEARCH_LIMIT) break
            if (country != null && p.str("countrycode") != country) continue
            val lat = c.optDouble(1)
            val lng = c.optDouble(0)
            if (!lat.isFinite() || !lng.isFinite()) continue
            val place = placeRecord(
                lat,
                lng,
                p.str("name"),
                p.str("housenumber"),
                p.str("street"),
                listOf(p.str("district"), p.str("city"), p.str("county"), p.str("state"), p.str("country")),
                wantsNumber(text) && p.str("housenumber").isEmpty()
            )
            if (seen.add(place.name + "|" + place.fullAddress)) out += place
        }
        return out
    }

    /** Nominatim knows house numbers, so a submitted search uses it first. */
    private suspend fun nominatim(text: String, country: String?): List<Place> {
        val cc = if (country != null) "&countrycodes=" + country.lowercase() else ""
        val url = "${Cfg.NOMINATIM_URL}?format=jsonv2&addressdetails=1&limit=${Cfg.SEARCH_LIMIT}" +
            "&accept-language=en$cc&q=${enc(text)}"
        val list = JSONArray(api.fetchText(url, tries = 1, timeoutMs = Cfg.NOMINATIM_TIMEOUT_MS))
        val seen = HashSet<String>()
        val out = ArrayList<Place>()
        for (i in 0 until list.length()) {
            val f = list.optJSONObject(i) ?: continue
            val a = f.optJSONObject("address") ?: JSONObject()
            val lat = f.str("lat").toDoubleOrNull() ?: continue
            val lng = f.str("lon").toDoubleOrNull() ?: continue
            val number = a.str("house_number")
            val place = placeRecord(
                lat,
                lng,
                f.str("name"),
                number,
                a.firstOf("road", "pedestrian", "residential", "footway", "path"),
                listOf(
                    a.str("suburb"),
                    a.str("neighbourhood"),
                    a.firstOf("city", "town", "village", "hamlet"),
                    a.str("county"),
                    a.str("postcode"),
                    a.str("country")
                ),
                wantsNumber(text) && number.isEmpty()
            )
            if (out.size < Cfg.SEARCH_LIMIT && seen.add(place.name + "|" + place.fullAddress)) out += place
        }
        return out
    }

    /** Looks up one numbered building on a street straight from OpenStreetMap. Null when none is near [spot]. */
    private suspend fun overpassHouse(number: String, street: String, spot: LatLng): LatLng? {
        val n = quoteRegex(number)
        val road = quoteRegex(street)
        val query = "[out:json][timeout:10];" +
            "nwr[\"addr:housenumber\"~\"^$n\$\",i][\"addr:street\"~\"^$road\$\",i]" +
            "(around:${Cfg.OVERPASS_AROUND_M},${spot.lat},${spot.lng});out center;"
        val body = api.fetchText(
            "${Cfg.OVERPASS_URL}?data=${enc(query)}",
            tries = 1,
            timeoutMs = Cfg.OVERPASS_TIMEOUT_MS
        )
        val elements = JSONObject(body).optJSONArray("elements") ?: return null
        var best: LatLng? = null
        var bestDistance = Double.MAX_VALUE
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i) ?: continue
            val centre = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else centre?.optDouble("lat") ?: Double.NaN
            val lng = if (e.has("lon")) e.optDouble("lon") else centre?.optDouble("lon") ?: Double.NaN
            if (!lat.isFinite() || !lng.isFinite()) continue
            val hit = LatLng(lat, lng)
            val d = GeoMath.metresBetween(spot, hit)
            if (d < bestDistance) {
                bestDistance = d
                best = hit
            }
        }
        return best
    }

    /** When the geocoders do not know "12 High Street", find High Street and ask Overpass for number 12 on it. */
    private suspend fun houseFromStreets(text: String, country: String?): List<Place> {
        val m = HOUSE_AND_STREET.find(text) ?: return emptyList()
        val number = m.groupValues[1]
        val streets = photon(m.groupValues[2], country).take(Cfg.STREET_CANDIDATES)
        return coroutineScope {
            streets.map { candidate ->
                async {
                    attempt {
                        val road = candidate.street.ifEmpty { candidate.name }
                        overpassHouse(number, road, candidate.latLng)?.let { hit ->
                            val details = candidate.addr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            placeRecord(hit.lat, hit.lng, "", number, road, details, false)
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun geocode(text: String, exact: Boolean, country: String?): SearchResult {
        if (!exact) return SearchResult(photon(text, country))
        val nominatimList = attempt { nominatim(text, country) }
        var list: List<Place> = nominatimList ?: emptyList()
        val note = if (nominatimList == null) NOMINATIM_DOWN_NOTE else null
        if (!wantsNumber(text)) {
            if (list.isEmpty()) list = photon(text, country)
            return SearchResult(list, note)
        }
        val photonList = attempt { photon(text, country) } ?: emptyList()
        val combined = list + photonList
        val exactMatches = combined.filter { it.houseNumber.isNotEmpty() }
        if (exactMatches.isNotEmpty()) {
            val seen = HashSet<String>()
            val only = exactMatches.filter { seen.add(it.name + "|" + it.fullAddress) }.take(Cfg.SEARCH_LIMIT)
            return SearchResult(only, note)
        }
        val houses = attempt { houseFromStreets(text, country) } ?: emptyList()
        val out = (houses + combined).take(Cfg.SEARCH_LIMIT)
        val closing = "The map data has no exact house-number match. These are street-level results; " +
            "use Choose on map to pin the entrance precisely."
        return SearchResult(out, if (houses.isEmpty()) note ?: closing else null)
    }

    /** The address at a spot, preferring a numbered building over a generic label. Null when nothing is known. */
    suspend fun reverse(lat: Double, lng: Double): Place? {
        val photonPlace = attempt { reversePhoton(lat, lng) }
        if (photonPlace != null && photonPlace.houseNumber.isNotEmpty()) return photonPlace
        val nominatimPlace = attempt { reverseNominatim(lat, lng) }
        val preferNominatim = nominatimPlace != null && prefersNominatim(nominatimPlace, photonPlace)
        return if (preferNominatim) nominatimPlace else photonPlace
    }

    private fun prefersNominatim(nominatimPlace: Place, photonPlace: Place?): Boolean =
        nominatimPlace.houseNumber.isNotEmpty() ||
            photonPlace == null ||
            (nominatimPlace.street.isNotEmpty() && photonPlace.street.isEmpty())

    private suspend fun reversePhoton(lat: Double, lng: Double): Place? {
        val body = api.fetchText(
            "${Cfg.REVERSE_URL}?lat=$lat&lon=$lng&lang=en",
            tries = 1,
            timeoutMs = Cfg.REVERSE_TIMEOUT_MS
        )
        val p = JSONObject(body).optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties") ?: return null
        val number = p.str("housenumber")
        val street = p.str("street")
        if (street.isEmpty() && number.isEmpty() && p.str("name").isEmpty()) return null
        return placeRecord(
            lat,
            lng,
            if (number.isNotEmpty()) "" else p.str("name"),
            number,
            street,
            listOf(p.str("district"), p.str("city"), p.str("county"), p.str("postcode"), p.str("country")),
            number.isEmpty() && street.isNotEmpty()
        )
    }

    private suspend fun reverseNominatim(lat: Double, lng: Double): Place {
        val url = "${Cfg.REVERSE_NOMINATIM_URL}?format=jsonv2&addressdetails=1&zoom=18&accept-language=en" +
            "&lat=$lat&lon=$lng"
        val f = JSONObject(api.fetchText(url, tries = 1, timeoutMs = Cfg.REVERSE_NOMINATIM_TIMEOUT_MS))
        val a = f.optJSONObject("address") ?: JSONObject()
        val street = a.firstOf("road", "pedestrian", "residential", "footway", "path")
        val number = a.str("house_number")
        return placeRecord(
            lat,
            lng,
            if (number.isNotEmpty()) "" else f.str("name"),
            number,
            street,
            listOf(
                a.str("suburb"),
                a.str("neighbourhood"),
                a.firstOf("city", "town", "village", "hamlet"),
                a.str("county"),
                a.str("postcode"),
                a.str("country")
            ),
            number.isEmpty() && street.isNotEmpty()
        )
    }
}
