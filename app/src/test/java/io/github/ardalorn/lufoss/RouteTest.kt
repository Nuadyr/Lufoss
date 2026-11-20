package io.github.ardalorn.lufoss

import io.github.ardalorn.lufoss.data.LoopCandidate
import io.github.ardalorn.lufoss.data.LoopPlanner
import io.github.ardalorn.lufoss.data.RouteParser
import io.github.ardalorn.lufoss.data.placeRecord
import io.github.ardalorn.lufoss.geo.LatLng
import io.github.ardalorn.lufoss.nav.RouteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    // A straight walk north in two legs: a stop in the middle becomes a turn, not an arrival.
    private val json = """
        {"routes":[{"distance":251.4,"duration":190.0,
          "geometry":{"coordinates":[[0.0,51.0],[0.0,51.001],[0.001,51.001],[0.002,51.001]]},
          "legs":[
            {"steps":[
              {"distance":111.3,"name":"First Road","maneuver":{"type":"depart","bearing_after":0}},
              {"distance":0.0,"name":"","maneuver":{"type":"arrive","bearing_before":0}}]},
            {"steps":[
              {"distance":140.1,"name":"Second Road","maneuver":{"type":"depart","bearing_after":90}},
              {"distance":0.0,"name":"","maneuver":{"type":"arrive","bearing_before":90}}]}
          ]}]}
    """.trimIndent()

    private val points = listOf(LatLng(51.0, 0.0), LatLng(51.001, 0.0), LatLng(51.001, 0.002))

    @Test
    fun parserBuildsStepsAndAWaypoint() {
        val route = RouteParser.parse(json, points, "Start")
        assertEquals(4, route.size)
        assertEquals(3, route.steps.size)
        assertEquals("right", route.steps[1].mod)
        assertEquals(1, route.wps.size)
        assertEquals(route.cum[1], route.wps[0].at, 5.0)
        assertEquals("Start", route.from)
    }

    @Test
    fun snapFindsTheNearestPointOnTheLine() {
        val route = RouteParser.parse(json, points, "Start")
        val s = RouteMath.snap(route, 51.0005, 0.0001)
        assertTrue(s.d < 15.0)
        assertEquals(55.0, s.along, 6.0)
    }

    @Test
    fun pointAtWalksAlongTheRoute() {
        val route = RouteParser.parse(json, points, "Start")
        val p = RouteMath.pointAt(route, route.total)
        assertEquals(51.001, p.lat, 1e-6)
        assertEquals(0.002, p.lng, 1e-6)
    }

    @Test
    fun overlapIsZeroForARouteThatNeverDoublesBack() {
        val straight = RouteParser.parse(json, points, "Start")
        assertEquals(0.0, LoopPlanner.overlapShare(straight), 1e-9)
    }

    @Test
    fun aCleanLoopAlwaysBeatsAnOverlappingOne() {
        val route = RouteParser.parse(json, points, "Start")
        val clean = LoopCandidate(route, 0.3, 0.0)
        val messy = LoopCandidate(route, 0.0, 0.2)
        assertTrue(LoopPlanner.better(clean, messy))
        assertFalse(LoopPlanner.better(messy, clean))
        assertTrue(LoopPlanner.better(clean, null))
    }

    @Test
    fun placeRecordPrefersANumberedStreet() {
        val p = placeRecord(51.0, 0.0, "High Street", "12", "High Street", listOf("Ely", "Cambridgeshire"))
        assertEquals("12 High Street", p.name)
        assertEquals("12 High Street, Ely, Cambridgeshire", p.fullAddress)
        assertEquals("12", p.houseNumber)
    }
}
