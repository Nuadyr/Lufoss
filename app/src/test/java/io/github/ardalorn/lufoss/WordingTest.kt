package io.github.ardalorn.lufoss

import io.github.ardalorn.lufoss.data.Step
import io.github.ardalorn.lufoss.data.fmtDist
import io.github.ardalorn.lufoss.data.fmtTime
import io.github.ardalorn.lufoss.data.instruction
import io.github.ardalorn.lufoss.data.turnMod
import org.junit.Assert.assertEquals
import org.junit.Test

class WordingTest {
    @Test
    fun distancesRoundToTenMetresThenSwitchToKilometres() {
        assertEquals("450 m", fmtDist(447.0))
        assertEquals("10 m", fmtDist(2.0))
        assertEquals("1.2 km", fmtDist(1234.0))
        assertEquals("450 metres", fmtDist(447.0, spoken = true))
        assertEquals("1.2 kilometres", fmtDist(1234.0, spoken = true))
    }

    @Test
    fun timesShowMinutesThenHours() {
        assertEquals("1 min", fmtTime(10.0))
        assertEquals("7 min", fmtTime(420.0))
        assertEquals("1 h 5 min", fmtTime(3900.0))
    }

    @Test
    fun turnsAreNamedFromTheChangeInDirection() {
        assertEquals("straight", turnMod(5.0))
        assertEquals("slight right", turnMod(40.0))
        assertEquals("left", turnMod(-90.0))
        assertEquals("sharp left", turnMod(-140.0))
        assertEquals("uturn", turnMod(175.0))
    }

    @Test
    fun instructionsReadNaturally() {
        assertEquals("Turn left onto High Street", instruction(Step(10.0, "High Street", "turn", "left"), false))
        assertEquals("Walk along Mill Lane", instruction(Step(10.0, "Mill Lane", "depart", null), false))
        assertEquals("Arrive back at your start", instruction(Step(0.0, "", "arrive", null), true))
    }
}
