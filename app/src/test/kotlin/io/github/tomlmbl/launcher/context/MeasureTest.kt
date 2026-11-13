package io.github.tomlmbl.launcher.context

import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.WORK
import org.junit.Assert.assertEquals
import org.junit.Test

/** Measure through manual corrections. */
class MeasureTest {

    private val networked = setOf(WORK, HOME)

    @Test
    fun choosingANetworkedProfileAfterASwitchIsACorrection() {
        assertEquals(ManualChoice.CORRECTION, Measure.classify(WORK, active = HOME, current = HOME, switchedHere = true, chosenHere = false, networked))
    }

    @Test
    fun choosingAProfileWithoutNetworkIsExpectedUse() {
        assertEquals(ManualChoice.OTHER, Measure.classify(OUT, active = HOME, current = HOME, switchedHere = true, chosenHere = false, networked))
        assertEquals(ManualChoice.OTHER, Measure.classify(OUT, active = HOME, current = null, switchedHere = false, chosenHere = false, networked))
    }

    @Test
    fun choosingTheCurrentNetworkProfileWithoutASwitchIsMissed() {
        assertEquals(ManualChoice.MISSED, Measure.classify(HOME, active = WORK, current = HOME, switchedHere = false, chosenHere = false, networked))
    }

    @Test
    fun goingBackAfterAnOwnManualChoiceIsNotCounted() {
        assertEquals(ManualChoice.OTHER, Measure.classify(WORK, active = HOME, current = WORK, switchedHere = false, chosenHere = true, networked))
    }

    @Test
    fun otherChoicesAreNotCounted() {
        assertEquals(ManualChoice.OTHER, Measure.classify(HOME, active = HOME, current = HOME, switchedHere = true, chosenHere = false, networked))
        assertEquals(ManualChoice.OTHER, Measure.classify(WORK, active = HOME, current = null, switchedHere = false, chosenHere = false, networked))
        assertEquals(ManualChoice.OTHER, Measure.classify(WORK, active = OUT, current = HOME, switchedHere = false, chosenHere = false, networked))
    }

    @Test
    fun countsStartOnTheFirstEventAndRoundTrip() {
        val counts = MeasureCounts()
            .switched(day = 20_000)
            .chosen(ManualChoice.CORRECTION, day = 20_001)
            .chosen(ManualChoice.MISSED, day = 20_002)
            .chosen(ManualChoice.OTHER, day = 20_002)
        assertEquals(MeasureCounts(20_000, switches = 1, corrections = 1, missed = 1, manual = 3), counts)
        assertEquals(counts, MeasureCounts.decode(counts.encode()))
    }

    @Test
    fun unreadableLinesAreIgnored() {
        assertEquals(MeasureCounts(switches = 2), MeasureCounts.decode("switches\t2\nmissed\tx\n\ngarbage"))
    }
}
