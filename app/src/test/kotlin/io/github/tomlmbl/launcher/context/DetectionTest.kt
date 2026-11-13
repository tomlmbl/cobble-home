package io.github.tomlmbl.launcher.context

import io.github.tomlmbl.launcher.context.ContextEngine.STABLE_MS
import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.TRAVEL
import io.github.tomlmbl.launcher.context.Profile.WORK
import io.github.tomlmbl.launcher.signals.ConnectionTracker
import io.github.tomlmbl.launcher.signals.DepartureClock
import io.github.tomlmbl.launcher.signals.parseSsid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Automatic switching: engine, connection tracking and network registration. */
class DetectionTest {

    private val home = Ssid.of("maison".encodeToByteArray())
    private val office = Ssid.of("bureau".encodeToByteArray())
    private val on = ContextState(
        Profiles(listOf(WORK, HOME, OUT), active = WORK),
        detection = Detection(enabled = true, networks = mapOf(home to HOME, office to WORK)),
    )

    private fun seen(profile: Profile?, connection: Long = 1, since: Long = 0, fresh: Boolean = false) =
        Observation(profile, connection, since, fresh)

    /** Signal observed then safe moment, as when returning Home. */
    private fun ContextState.visit(observation: Observation?, now: Long = STABLE_MS) =
        ContextEngine.atSafeMoment(ContextEngine.observe(this, observation, now))

    @Test
    fun stableNetworkSwitchesAtTheSafeMomentOnly() {
        val observed = ContextEngine.observe(on, seen(HOME), now = STABLE_MS)
        assertEquals(WORK, observed.active)
        assertEquals(HOME, observed.pending)
        assertEquals(HOME, ContextEngine.atSafeMoment(observed).active)
    }

    @Test
    fun networkConnectedForLessThanTheStableDelayWaits() {
        val early = ContextEngine.observe(on, seen(HOME, since = 1), now = STABLE_MS)
        assertNull(early.pending)
        assertEquals(STABLE_MS + 1, ContextEngine.stableAt(seen(HOME, since = 1)))
        assertEquals(HOME, early.visit(seen(HOME, since = 1), now = STABLE_MS + 1).active)
    }

    @Test
    fun switchingOffDetectionIgnoresSignals() {
        val off = on.copy(detection = on.detection.withEnabled(false))
        assertEquals(WORK, off.visit(seen(HOME)).active)
    }

    @Test
    fun manualChoiceHoldsWhileTheSignalStaysTheSame() {
        val atHome = on.visit(seen(HOME))
        val chosen = ContextEngine.choose(atHome, OUT)
        assertEquals(OUT, chosen.visit(seen(HOME), now = 10 * STABLE_MS).active)
    }

    @Test
    fun shortDropOnTheSameNetworkKeepsTheManualChoice() {
        val chosen = ContextEngine.choose(on.visit(seen(HOME)), OUT)
        assertEquals(OUT, chosen.visit(seen(HOME, connection = 2, since = 20_000, fresh = false), now = 40_000).active)
    }

    @Test
    fun comingBackToTheSameNetworkOverridesTheManualChoice() {
        val chosen = ContextEngine.choose(on.visit(seen(HOME)), OUT)
        assertEquals(HOME, chosen.visit(seen(HOME, connection = 2, since = 20_000, fresh = true), now = 40_000).active)
    }

    @Test
    fun aFreshConnectionProposesOnlyOnce() {
        val back = on.visit(seen(HOME, connection = 2, fresh = true))
        val chosen = ContextEngine.choose(back, OUT)
        assertEquals(OUT, chosen.visit(seen(HOME, connection = 2, fresh = true), now = 5 * STABLE_MS).active)
    }

    @Test
    fun anotherNetworkIsANewSignal() {
        val chosen = ContextEngine.choose(on.visit(seen(HOME)), OUT)
        assertEquals(WORK, chosen.visit(seen(WORK, connection = 2, since = 20_000), now = 40_000).active)
    }

    /** Departure from a registered network observed at [awaySince], evaluated at [now], then safe moment. */
    private fun ContextState.leave(awaySince: Long, now: Long, observation: Observation? = null) =
        ContextEngine.atSafeMoment(ContextEngine.observe(this, observation, now, awaySince))

    @Test
    fun leavingARegisteredNetworkSwitchesToOutAfterTheStableDelay() {
        val atHome = on.visit(seen(HOME))
        val early = ContextEngine.observe(atHome, null, now = 10_000 + STABLE_MS - 1, awaySince = 10_000)
        assertNull(early.pending)
        assertEquals(OUT, atHome.leave(awaySince = 10_000, now = 10_000 + STABLE_MS).active)
    }

    @Test
    fun anUnregisteredNetworkIsADeparture() {
        val atHome = on.visit(seen(HOME))
        assertEquals(OUT, atHome.leave(awaySince = 20_000, now = 30_000, observation = seen(null, connection = 2, since = 20_000)).active)
    }

    @Test
    fun aShortDropIsNotADeparture() {
        val atHome = on.visit(seen(HOME))
        val dropped = ContextEngine.observe(atHome, null, now = 13_000, awaySince = 10_000)
        assertEquals(HOME, dropped.visit(seen(HOME, connection = 2, since = 14_000), now = 20_000).active)
    }

    @Test
    fun departureOverridesAnEarlierManualChoice() {
        val chosen = ContextEngine.choose(on.visit(seen(HOME)), WORK)
        assertEquals(OUT, chosen.leave(awaySince = 10_000, now = 20_000).active)
    }

    @Test
    fun manualChoiceAfterADepartureHoldsUntilARegisteredNetwork() {
        val out = on.visit(seen(HOME)).leave(awaySince = 10_000, now = 20_000)
        val chosen = ContextEngine.choose(out, WORK)
        assertEquals(WORK, chosen.leave(awaySince = 10_000, now = 60_000).active)
        assertEquals(WORK, chosen.leave(awaySince = 70_000, now = 90_000, observation = seen(null, connection = 3, since = 70_000)).active)
        assertEquals(HOME, chosen.visit(seen(HOME, connection = 4, since = 100_000), now = 110_000).active)
    }

    @Test
    fun pendingOutWaitsForTheSafeMomentEvenIfTheClockStops() {
        val pending = ContextEngine.observe(on.visit(seen(HOME)), null, now = 20_000, awaySince = 10_000)
        assertEquals(OUT, pending.pending)
        assertEquals(HOME, pending.active)
        val kept = ContextEngine.observe(pending, null, now = 30_000)
        assertEquals(OUT, ContextEngine.atSafeMoment(kept).active)
        assertNull(ContextEngine.observe(pending, seen(HOME, connection = 2, since = 25_000), now = 26_000).pending)
    }

    @Test
    fun noDepartureWithoutOutWithoutAPreviousSignalOrWithDetectionOff() {
        val noOut = on.copy(profiles = Profiles(listOf(WORK, HOME, TRAVEL), active = WORK)).visit(seen(HOME))
        assertEquals(HOME, noOut.leave(awaySince = 10_000, now = 20_000).active)
        assertEquals(WORK, on.leave(awaySince = 10_000, now = 20_000).active)
        val off = on.visit(seen(HOME)).let { it.copy(detection = it.detection.copy(enabled = false)) }
        assertEquals(HOME, off.leave(awaySince = 10_000, now = 20_000).active)
    }

    @Test
    fun networkOfASleepingProfileIsNotADeparture() {
        val travel = Ssid.of("hotel".encodeToByteArray())
        val atHome = on.copy(detection = on.detection.associate(travel, TRAVEL)).visit(seen(HOME))
        assertEquals(HOME, atHome.visit(seen(TRAVEL, connection = 2, since = 10_000), now = 30_000).active)
    }

    /** Without an observed departure (Wi-Fi off, unreadable SSID, process start): the last context stays. */
    @Test
    fun noRecognisedNetworkKeepsTheLastContext() {
        val atHome = on.visit(seen(HOME))
        assertEquals(HOME, atHome.visit(null, now = 60_000).active)
        assertEquals(HOME, atHome.visit(seen(null, connection = 2), now = 60_000).active)
    }

    @Test
    fun pendingChangeIsDroppedWhenItsNetworkGoes() {
        val pending = ContextEngine.observe(on, seen(HOME), now = STABLE_MS)
        val gone = ContextEngine.observe(pending, null, now = STABLE_MS + 1)
        assertNull(gone.pending)
        assertEquals(WORK, ContextEngine.atSafeMoment(gone).active)
    }

    @Test
    fun pendingChangeSurvivesARepeatedObservation() {
        val pending = ContextEngine.observe(on, seen(HOME), now = STABLE_MS)
        assertEquals(HOME, ContextEngine.observe(pending, seen(HOME), now = 2 * STABLE_MS).pending)
    }

    @Test
    fun manualChoiceDuringAnInteractionCancelsThePendingSignal() {
        val pending = ContextEngine.observe(on, seen(HOME), now = STABLE_MS)
        assertEquals(OUT, ContextEngine.atSafeMoment(ContextEngine.choose(pending, OUT)).active)
    }

    @Test
    fun networkOfASleepingProfileDoesNothing() {
        val travel = Ssid.of("hotel".encodeToByteArray())
        val state = on.copy(detection = on.detection.associate(travel, TRAVEL))
        assertEquals(WORK, state.visit(seen(TRAVEL)).active)
    }

    @Test
    fun withoutProfilesNothingSwitches() {
        val none = on.copy(profiles = Profiles())
        assertNull(none.visit(seen(HOME)).active)
    }

    @Test
    fun enablingAppliesTheCurrentNetwork() {
        val atHome = ContextEngine.choose(on.visit(seen(HOME)), OUT)
        val reEnabled = atHome.copy(detection = atHome.detection.withEnabled(false).withEnabled(true))
        assertEquals(HOME, reEnabled.visit(seen(HOME), now = 5 * STABLE_MS).active)
    }

    @Test
    fun aNetworkBelongsToOneProfileAndAProfileCanHaveSeveral() {
        val second = Ssid.of("maison-5g".encodeToByteArray())
        val detection = on.detection.associate(second, HOME).associate(office, HOME)
        assertEquals(setOf(home, second, office), detection.networksOf(HOME))
        assertTrue(detection.networksOf(WORK).isEmpty())
        assertFalse(home in detection.dissociate(home).networks)
    }

    @Test
    fun ssidKeepsAnyByte() {
        val odd = byteArrayOf(0x09, 0x0a, 0x00, 0xff.toByte(), 'a'.code.toByte())
        assertArrayEquals(odd, Ssid.of(odd).bytes())
        assertEquals("café", Ssid.of("café".encodeToByteArray()).display())
        assertEquals("Ssid(…)", home.toString())
    }

    @Test
    fun trackerDatesTheFirstNetworkFromWhenItWasSeen() {
        val tracker = ConnectionTracker().available(network = 7, at = 500)
        assertEquals(seen(HOME, connection = 7, since = 500, fresh = false), tracker.observation(HOME))
    }

    @Test
    fun trackerMarksAReturnAfterTheStableDelayWithoutWifi() {
        val lost = ConnectionTracker().available(7, 0).lost(7, 1_000)
        assertNull(lost.observation(HOME))
        assertTrue(lost.available(8, 1_000 + STABLE_MS).fresh)
        assertFalse(lost.available(8, 1_000 + STABLE_MS - 1).fresh)
    }

    @Test
    fun ssidIsReadFromQuotedOrHexTextAndNeverFromUnknown() {
        // Permission denied or location off: "<unknown ssid>", never a network.
        assertNull(parseSsid(null))
        assertNull(parseSsid(""))
        assertNull(parseSsid("<unknown ssid>"))
        assertNull(parseSsid("\"\""))
        assertEquals(Ssid.of("maison".encodeToByteArray()), parseSsid("\"maison\""))
        assertEquals(Ssid.of(byteArrayOf(0xff.toByte(), 0x01)), parseSsid("FF01"))
        assertNull(parseSsid("F0Z"))
    }

    @Test
    fun aDirectSwitchToAnotherNetworkIsNotAReturn() {
        // Old loss, then B announced before A is lost.
        val current = ConnectionTracker().available(7, 0).lost(7, 1_000).available(8, 2_000)
        assertFalse(current.available(9, 10 * STABLE_MS).fresh)
    }

    @Test
    fun trackerIgnoresOtherNetworksAndRepeats() {
        val tracker = ConnectionTracker().available(7, 0)
        assertEquals(tracker, tracker.lost(9, 100))
        assertEquals(tracker, tracker.available(7, 200))
    }

    @Test
    fun clockStartsOnALossSeenWithWifiOn() {
        val connected = ConnectionTracker().available(7, 0)
        val clock = DepartureClock().follow(connected, wifiOn = true, readable = true, now = 0)
        val lost = connected.lost(7, 1_000)
        assertEquals(1_000L, clock.follow(lost, wifiOn = true, readable = true, now = 0).awaySince(lost, registered = false, ssidRead = false))
    }

    @Test
    fun wifiTurnedOffThenOnIsNotADeparture() {
        val connected = ConnectionTracker().available(7, 0)
        val lost = connected.lost(7, 1_000)
        val off = DepartureClock().follow(connected, wifiOn = true, readable = true, now = 0).follow(lost, wifiOn = false, readable = true, now = 0)
        assertNull(off.awaySince(lost, registered = false, ssidRead = false))
        assertNull(off.follow(lost, wifiOn = true, readable = true, now = 0).awaySince(lost, registered = false, ssidRead = false))
    }

    @Test
    fun noNetworkAtProcessStartIsNotADeparture() {
        val none = ConnectionTracker()
        assertNull(DepartureClock().follow(none, wifiOn = true, readable = true, now = 0).awaySince(none, registered = false, ssidRead = false))
    }

    @Test
    fun clockOnAnUnregisteredNetworkNeedsAReadableSsid() {
        val other = ConnectionTracker().available(8, 2_000)
        val clock = DepartureClock().follow(other, wifiOn = true, readable = true, now = 0)
        assertEquals(2_000L, clock.awaySince(other, registered = false, ssidRead = true))
        assertNull(clock.awaySince(other, registered = false, ssidRead = false))
        assertNull(clock.awaySince(other, registered = true, ssidRead = true))
    }

    @Test
    fun reconnectingStopsTheClock() {
        val lost = ConnectionTracker().available(7, 0).lost(7, 1_000)
        val clock = DepartureClock(network = 7).follow(lost, wifiOn = true, readable = true, now = 0)
        assertEquals(1_000L, clock.lossAt)
        val back = lost.available(9, 3_000)
        assertNull(clock.follow(back, wifiOn = true, readable = true, now = 0).awaySince(back, registered = true, ssidRead = true))
        assertNull(clock.follow(back, wifiOn = true, readable = true, now = 0).lossAt)
    }

    @Test
    fun aSettledDepartureSurvivesWifiOrLocationTurnedOff() {
        // Stable departure, then Wi-Fi or location turned off before returning Home.
        val connected = ConnectionTracker().available(7, 0)
        val lost = connected.lost(7, 1_000)
        val counted = DepartureClock().follow(connected, wifiOn = true, readable = true, now = 0).follow(lost, wifiOn = true, readable = true, now = 1_000)
        val settled = 1_000 + STABLE_MS
        assertEquals(1_000L, counted.follow(lost, wifiOn = false, readable = true, now = settled).lossAt)
        assertEquals(1_000L, counted.follow(lost, wifiOn = true, readable = false, now = settled).lossAt)
        // Before the 5 s, the departure exclusions still apply.
        assertNull(counted.follow(lost, wifiOn = false, readable = true, now = settled - 1).lossAt)
        // Finding a registered network again ends the departure.
        val back = lost.available(9, settled + 1)
        assertNull(counted.follow(lost, wifiOn = false, readable = true, now = settled).follow(back, wifiOn = true, readable = true, now = settled + 1).lossAt)
    }

    @Test
    fun noDepartureWhenTheReturnCouldNotBeSeen() {
        val connected = ConnectionTracker().available(7, 0)
        val lost = connected.lost(7, 1_000)
        val clock = DepartureClock().follow(connected, wifiOn = true, readable = true, now = 0)
        assertNull(clock.follow(lost, wifiOn = true, readable = false, now = 0).awaySince(lost, registered = false, ssidRead = false))
        val counted = clock.follow(lost, wifiOn = true, readable = true, now = 0)
        assertNull(counted.follow(lost, wifiOn = true, readable = false, now = 0).lossAt)
    }
}
