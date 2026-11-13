package io.github.tomlmbl.launcher.signals

import android.os.SystemClock
import android.util.Log
import io.github.tomlmbl.launcher.context.AutoSwitch
import io.github.tomlmbl.launcher.context.ContextEngine
import io.github.tomlmbl.launcher.context.ContextState
import io.github.tomlmbl.launcher.context.Measure
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.settings.ConfigStore
import io.github.tomlmbl.launcher.settings.MeasureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Automatic switching: connects Wi-Fi to the engine while Home is visible, holds the pending change and applies it
 * when idle, or before Home is shown on return if the connection is already stable.
 * [scope]: main thread. `Detect` log: states, profiles and durations, written only when they change; never a
 * network name or identifier. Counts switches and manual choices for the local measure ([Measure]).
 */
class Detector(
    private val store: ConfigStore,
    private val wifi: WifiSignal,
    private val measure: MeasureStore,
    private val scope: CoroutineScope,
) : AutoSwitch {

    private var pending: Profile? = null
    private var visible = false
    private var rest = false
    private var timer: Job? = null
    /** Connection of the last signal-driven switch, forgotten at the next manual choice; connection of the last manual choice. */
    private var switchedConnection: Long? = null
    private var chosenConnection: Long? = null
    /**
     * Connection of the last signal taken into account, held here: the configuration does not write it, and a reread of
     * the file used to lose it, so a returning network proposed its profile again over a manual choice.
     */
    private var acknowledgedConnection: Long? = null
    /** Profile of the last signal read from the configuration: its return to null (switching turned off, then on) forgets the connection. */
    private var seenAcknowledged: Profile? = null
    private var departure = DepartureClock()
    /** Departure switch applied without a connection, to count its correction. */
    private var switchedAway = false
    private var enabled = false
    private var logged: String? = null

    fun start() {
        scope.launch { combine(store.config, wifi.tracker, wifi.reading, wifi.enabled) { _, _, _, _ -> }.collect { evaluate() } }
    }

    override fun visible(visible: Boolean) {
        if (visible == this.visible) return
        this.visible = visible
        if (visible) wifi.acquire() else {
            wifi.release()
            timer?.cancel()
        }
        evaluate()
    }

    override fun atRest(rest: Boolean) {
        this.rest = rest
        evaluate()
    }

    override fun chosen(profile: Profile) {
        pending = null
        val config = store.config.value ?: return
        val detection = config.detection
        if (!detection.enabled) return
        val connection = wifi.tracker.value.network
        val reading = wifi.reading.value?.takeIf { it.network == connection }
        val kind = Measure.classify(
            chosen = profile,
            active = config.profiles.active,
            current = reading?.ssid?.let { detection.networks[it] },
            switchedHere = (connection != null && connection == switchedConnection) || switchedAway,
            chosenHere = connection != null && connection == chosenConnection,
            networked = detection.networks.values.toSet(),
        )
        switchedConnection = null
        switchedAway = false
        chosenConnection = connection
        log("manual=$profile kind=$kind")
        measure.update { counts, day -> counts.chosen(kind, day) }
    }

    private fun evaluate() {
        val config = store.config.value ?: return
        val acknowledged = config.detection.acknowledged
        if (!config.detection.enabled || !enabled || (acknowledged == null && seenAcknowledged != null)) acknowledgedConnection = null
        seenAcknowledged = acknowledged
        enabled = config.detection.enabled
        val detection = config.detection.copy(acknowledgedConnection = acknowledgedConnection)
        val tracker = wifi.tracker.value
        val now = SystemClock.elapsedRealtime()
        departure = departure.follow(tracker, wifi.wifiEnabled(), wifi.ssidReadable(), now)
        // Switching off: no departure, even a kept one.
        if (!detection.enabled) departure = DepartureClock(tracker.network)
        if (!visible || !detection.enabled) {
            pending = null
            log(if (detection.enabled) "idle" else "off")
            return
        }
        val reading = wifi.reading.value
        // The SSID is matched to the tracked connection only if both refer to the same network; otherwise wait for the other callback.
        if (tracker.network != null && reading?.network != tracker.network) return
        val observation = tracker.observation(reading?.ssid?.let { detection.networks[it] })
        val awaySince = departure.awaySince(tracker, registered = observation?.profile != null, ssidRead = reading?.ssid != null)
        val state = ContextEngine.observe(ContextState(config.profiles, pending, detection), observation, now, awaySince)
        pending = state.pending
        // Idleness enters the logged state only if a switch is waiting for it: every touch changes it.
        val signal = observation?.let { it.profile?.name ?: "unknown" } ?: "none"
        log(
            "signal=$signal pending=$pending" + if (pending != null) " rest=$rest" else "",
            detail = listOfNotNull(
                observation?.let { "connectedMs=${now - it.since}" + if (it.fresh) " return" else "" },
                awaySince?.let { "awayMs=${now - it}" },
            ).joinToString(" ").ifEmpty { null },
        )
        acknowledgedConnection = state.detection.acknowledgedConnection
        if (state.detection.acknowledged != detection.acknowledged) {
            store.update("detector.acknowledge") { it.copy(detection = it.detection.copy(acknowledged = state.detection.acknowledged)) }
        }
        timer?.cancel()
        val wakeAt = listOfNotNull(
            observation?.takeIf { it.profile != null }?.let(ContextEngine::stableAt),
            awaySince?.plus(ContextEngine.STABLE_MS),
        ).filter { it > now }.minOrNull()
        if (wakeAt != null) {
            timer = scope.launch {
                delay(wakeAt - now)
                evaluate()
            }
        }
        if (pending != null && rest) apply()
    }

    private fun apply() {
        val profile = pending ?: return
        pending = null
        switchedConnection = wifi.tracker.value.network
        switchedAway = switchedConnection == null
        wifi.tracker.value.let { log("applied=$profile connectedMs=${SystemClock.elapsedRealtime() - it.since}", force = true) }
        measure.update { counts, day -> counts.switched(day) }
        store.update("detector.switch") { it.copy(profiles = ContextEngine.atSafeMoment(ContextState(it.profiles, profile)).profiles) }
    }

    /** Writes [state] if it differs from the previous one ([detail] goes with it without counting in the comparison). */
    private fun log(state: String, detail: String? = null, force: Boolean = false) {
        if (!force && state == logged) return
        logged = state
        Log.i(TAG, if (detail != null) "$state ($detail)" else state)
    }

    private companion object {
        const val TAG = "Detect"
    }
}
