package io.github.tomlmbl.launcher.signals

import io.github.tomlmbl.launcher.context.ContextEngine
import io.github.tomlmbl.launcher.context.Observation
import io.github.tomlmbl.launcher.context.Profile

/**
 * Tracks Wi-Fi connections while the process lives, with no SSID and no location permission.
 * Fed by a network callback without flags: networks appearing and lost, monotonic clock. Pure functions.
 * The first network seen after the process starts has no known past: it starts when seen, and is not a return.
 */
data class ConnectionTracker(
    val network: Long? = null,
    val since: Long = 0L,
    val fresh: Boolean = false,
    val lostAt: Long? = null,
) {

    fun available(network: Long, at: Long): ConnectionTracker {
        if (network == this.network) return this
        // A return requires that no network was tracked: moving straight from one network to another is not one.
        val fresh = this.network == null && lostAt?.let { at - it >= ContextEngine.STABLE_MS } == true
        return ConnectionTracker(network, at, fresh, lostAt)
    }

    fun lost(network: Long, at: Long): ConnectionTracker =
        if (network == this.network) copy(network = null, lostAt = at, fresh = false) else this

    /** Observation for the engine; [profile] comes from the SSID read in the foreground. */
    fun observation(profile: Profile?): Observation? = network?.let { Observation(profile, it, since, fresh) }
}
