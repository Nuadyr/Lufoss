package io.github.ardalorn.lufoss.data

import java.util.Locale
import java.util.TimeZone

/** The person's home country, used to limit place suggestions to it when they ask for that. */
object Region {
    /** Time zones that map to one country, written as zone:country pairs. */
    private const val ZONES = "Europe/London:GB Europe/Belfast:GB Europe/Dublin:IE Europe/Paris:FR " +
        "Europe/Berlin:DE Europe/Madrid:ES Europe/Rome:IT Europe/Amsterdam:NL Europe/Brussels:BE " +
        "Europe/Lisbon:PT Europe/Zurich:CH Europe/Vienna:AT Europe/Stockholm:SE Europe/Oslo:NO " +
        "Europe/Copenhagen:DK Europe/Helsinki:FI Europe/Warsaw:PL Europe/Prague:CZ Europe/Athens:GR " +
        "Europe/Budapest:HU Europe/Bucharest:RO Pacific/Auckland:NZ Asia/Tokyo:JP Asia/Kolkata:IN " +
        "Asia/Calcutta:IN Asia/Singapore:SG Asia/Dubai:AE Asia/Seoul:KR Asia/Manila:PH " +
        "Africa/Johannesburg:ZA Africa/Lagos:NG Africa/Nairobi:KE America/Toronto:CA " +
        "America/Vancouver:CA America/Edmonton:CA America/Winnipeg:CA America/Halifax:CA " +
        "America/Mexico_City:MX America/Sao_Paulo:BR America/Argentina/Buenos_Aires:AR " +
        "America/New_York:US America/Chicago:US America/Denver:US America/Los_Angeles:US " +
        "America/Phoenix:US America/Anchorage:US America/Detroit:US Pacific/Honolulu:US"

    private val zoneCountries: Map<String, String> =
        ZONES.split(' ').associate { it.substringBefore(':') to it.substringAfter(':') }

    /** Upper-case two-letter country code from the device region, else from the time zone, else empty. */
    fun homeCountry(): String {
        val region = Locale.getDefault().country.uppercase(Locale.ROOT)
        if (region.length == 2) return region
        val zone = TimeZone.getDefault().id
        return zoneCountries[zone] ?: if (zone.startsWith("Australia/")) "AU" else ""
    }

    /** The country's English name, for the "Only in ..." button. */
    fun displayName(code: String): String {
        val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.UK)
        return name.ifBlank { code }
    }
}
