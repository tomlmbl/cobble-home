package io.github.tomlmbl.launcher.context

/**
 * Context engine: pure functions, no Android API.
 * The active context is persisted in [Profiles.active]; a pending change lives in memory only.
 * Signals go through [observe] then [propose], and are never applied outside a safe moment.
 */
data class ContextState(val profiles: Profiles, val pending: Profile? = null, val detection: Detection = Detection()) {
    /** Displayed profile, or null without profiles. */
    val active: Profile? get() = profiles.active?.takeIf { it in profiles.selected }
}

object ContextEngine {

    /** How long a network must stay connected before it triggers a switch. */
    const val STABLE_MS = 5_000L

    /** Manual choice: applied at once, since it is the interaction itself; cancels any pending change. */
    fun choose(state: ContextState, profile: Profile): ContextState {
        if (profile !in state.profiles.selected) return state
        return state.copy(profiles = state.profiles.copy(active = profile), pending = null)
    }

    /** Change proposed outside an interaction: it waits for the next safe moment. */
    fun propose(state: ContextState, profile: Profile): ContextState {
        if (profile !in state.profiles.selected) return state.copy(pending = null)
        return state.copy(pending = profile.takeIf { it != state.active })
    }

    /**
     * Wi-Fi signal: a network registered to a selected profile, connected for [STABLE_MS], proposes that profile if it
     * differs from the last signal taken into account or if the connection is a return ([Observation.fresh]).
     * Otherwise nothing: a manual choice holds, and the last context stays when no known network is around, except
     * on departure. A pending change is dropped if the network that proposed it is gone.
     * [awaySince]: start of the departure from a registered network, null if none was observed (`Detector`).
     */
    fun observe(state: ContextState, observation: Observation?, now: Long, awaySince: Long? = null): ContextState {
        val detection = state.detection
        if (!detection.enabled || !state.profiles.enabled) return state.copy(pending = null)
        val profile = observation?.profile?.takeIf { it in state.profiles.selected }
            ?: return away(state, awaySince, now)
        val kept = state.copy(pending = state.pending?.takeIf { it == profile })
        if (now - observation.since < STABLE_MS) return kept
        if (observation.connection == detection.acknowledgedConnection) return kept
        val changed = profile != detection.acknowledged || observation.fresh
        val acknowledged = kept.copy(detection = detection.copy(acknowledged = profile, acknowledgedConnection = observation.connection))
        return if (changed) propose(acknowledged, profile) else acknowledged
    }

    /**
     * Departure: no registered network for [STABLE_MS] after a signal from another profile proposes Out, once,
     * even over a manual choice. Once Out is taken into account, a manual choice holds until the next registered
     * network; a pending Out stays pending until the safe moment. Without Out among the profiles, nothing.
     */
    private fun away(state: ContextState, awaySince: Long?, now: Long): ContextState {
        val left = state.detection.acknowledged
        if (left == Profile.OUT) return state.copy(pending = state.pending?.takeIf { it == Profile.OUT })
        if (awaySince == null || left == null || Profile.OUT !in state.profiles.selected) return state.copy(pending = null)
        if (now - awaySince < STABLE_MS) return state.copy(pending = null)
        return propose(state.copy(detection = state.detection.copy(acknowledged = Profile.OUT, acknowledgedConnection = null)), Profile.OUT)
    }

    /** Time at which a not-yet-stable connection must be evaluated again. */
    fun stableAt(observation: Observation) = observation.since + STABLE_MS

    /** Safe moment: before Home is shown, or Home idle. Applies the pending change. */
    fun atSafeMoment(state: ContextState): ContextState {
        val pending = state.pending ?: return state
        if (pending !in state.profiles.selected) return state.copy(pending = null)
        return state.copy(profiles = state.profiles.copy(active = pending), pending = null)
    }
}
