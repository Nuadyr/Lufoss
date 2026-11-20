package io.github.ardalorn.lufoss

import io.github.ardalorn.lufoss.geo.GeoMath
import io.github.ardalorn.lufoss.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    @Test
    fun projectAndUnprojectRoundTrip() {
        val xy = GeoMath.project(51.5074, -0.1278, 12.0)
        val back = GeoMath.unproject(xy.x, xy.y, 12.0)
        assertEquals(51.5074, back.lat, 1e-9)
        assertEquals(-0.1278, back.lng, 1e-9)
    }

    @Test
    fun normWrapsIntoOneTurn() {
        assertEquals(350.0, GeoMath.norm(-10.0), 1e-9)
        assertEquals(10.0, GeoMath.norm(370.0), 1e-9)
    }

    @Test
    fun angDiffTakesTheShortWay() {
        assertEquals(20.0, GeoMath.angDiff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, GeoMath.angDiff(10.0, 350.0), 1e-9)
    }

    @Test
    fun bearingToNorthIsZeroAndEastIsNinety() {
        val a = LatLng(51.0, 0.0)
        assertEquals(0.0, GeoMath.bearingTo(a, LatLng(52.0, 0.0)), 1e-6)
        assertEquals(90.0, GeoMath.bearingTo(a, LatLng(51.0, 1.0)), 0.5)
    }

    @Test
    fun offsetPointMovesTheRightDistance() {
        val a = LatLng(51.0, 0.0)
        val b = GeoMath.offsetPoint(a, 1000.0, 90.0)
        assertEquals(1000.0, GeoMath.metresBetween(a, b), 1.0)
    }

    @Test
    fun toMapTurnsByTheBearing() {
        val m = GeoMath.toMap(0.0, -10.0, 90.0)
        assertEquals(10.0, m.x, 1e-9)
        assertEquals(0.0, m.y, 1e-9)
    }
}
