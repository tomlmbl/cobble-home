package io.github.tomlmbl.launcher.signals

import io.github.tomlmbl.launcher.context.ContextEngine

/**
 * Start of a departure from a registered network, kept apart from [ConnectionTracker.lostAt], which serves returns.
 * A loss only counts if observed with Wi-Fi on and the SSID readable ([readable]: otherwise the return would go
 * unseen); otherwise the clock clears. Once stable ([ContextEngine.STABLE_MS]), the departure is kept: turning
 * Wi-Fi or location off afterwards no longer cancels it. Never inferred from a process start.
 * Pure functions.
 */
data class DepartureClock(val network: Long? = null, val lossAt: Long? = null) {

    /** Follows [tracker] at every evaluation, even with Home hidden, so no loss is missed; [now]: `elapsedRealtime`. */
    fun follow(tracker: ConnectionTracker, wifiOn: Boolean, readable: Boolean, now: Long): DepartureClock {
        val settled = lossAt != null && now - lossAt >= ContextEngine.STABLE_MS
        val loss = when {
            !wifiOn || !readable -> lossAt.takeIf { settled && tracker.network == null }
            tracker.network == network -> lossAt
            tracker.network == null && network != null -> tracker.lostAt
            else -> null
        }
        return DepartureClock(tracker.network, loss)
    }

    /**
     * Start of the departure: a loss without reconnection, or a network whose read SSID ([ssidRead]) is registered to
     * no profile ([registered] false); null with a registered network, even of a dormant profile, or an unreadable SSID.
     */
    fun awaySince(tracker: ConnectionTracker, registered: Boolean, ssidRead: Boolean): Long? = when {
        tracker.network == null -> lossAt
        !registered && ssidRead -> tracker.since
        else -> null
    }
}
