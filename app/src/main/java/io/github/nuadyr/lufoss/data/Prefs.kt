package io.github.nuadyr.lufoss.data

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/** The few things kept on the device: the default start point, the country filter and the voice setting. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lufoss", Context.MODE_PRIVATE)

    var start: Place?
        get() = readStart()
        set(value) {
            sp.edit().apply {
                if (value == null) remove(KEY_START) else putString(KEY_START, writeStart(value))
            }.apply()
        }

    var localOnly: Boolean
        get() = sp.getBoolean(KEY_LOCAL, true)
        set(value) = sp.edit().putBoolean(KEY_LOCAL, value).apply()

    var voice: Boolean
        get() = sp.getBoolean(KEY_VOICE, true)
        set(value) = sp.edit().putBoolean(KEY_VOICE, value).apply()

    private fun readStart(): Place? {
        val text = sp.getString(KEY_START, null) ?: return null
        return try {
            val o = JSONObject(text)
            val lat = o.getDouble("lat")
            val lng = o.getDouble("lng")
            if (!lat.isFinite() || !lng.isFinite()) return null
            Place(
                lat = lat,
                lng = lng,
                name = o.optString("name", "Pinned location"),
                addr = o.optString("addr", ""),
                fullAddress = o.optString("fullAddress", ""),
                houseNumber = o.optString("houseNumber", ""),
                street = o.optString("street", ""),
                approx = o.optBoolean("approx", false)
            )
        } catch (e: JSONException) {
            null
        }
    }

    private fun writeStart(p: Place): String = JSONObject()
        .put("lat", p.lat)
        .put("lng", p.lng)
        .put("name", p.name)
        .put("addr", p.addr)
        .put("fullAddress", p.fullAddress)
        .put("houseNumber", p.houseNumber)
        .put("street", p.street)
        .put("approx", p.approx)
        .toString()

    private companion object {
        const val KEY_START = "start"
        const val KEY_LOCAL = "local"
        const val KEY_VOICE = "voice"
    }
}
