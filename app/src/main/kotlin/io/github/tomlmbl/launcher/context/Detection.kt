package io.github.tomlmbl.launcher.context

/**
 * A Wi-Fi network name (SSID), as hexadecimal bytes: an SSID may contain any byte.
 * In clear only in memory; encrypted on disk. Never log it.
 */
@JvmInline
value class Ssid(val hex: String) {

    fun bytes(): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Name shown in settings; bytes that are not valid UTF-8 are replaced. */
    fun display(): String = bytes().decodeToString()

    override fun toString() = "Ssid(…)"

    companion object {
        fun of(bytes: ByteArray) = Ssid(bytes.joinToString("") { "%02x".format(it) })
    }
}

/**
 * Automatic switching: the "Automatic switching" toggle, networks registered to profiles, and the profile of the
 * last signal taken into account. A network belongs to one profile only; a profile may have several.
 * Networks of a dormant profile stay stored, with no effect.
 */
data class Detection(
    val enabled: Boolean = false,
    val networks: Map<Ssid, Profile> = emptyMap(),
    /** Profile of the last signal taken into account: a manual choice holds as long as the signal does not change. */
    val acknowledged: Profile? = null,
    /** Connection of that signal, never written to or read from the configuration (held by `Detector`): one connection proposes only once. */
    val acknowledgedConnection: Long? = null,
    /** Unreadable networks (Keystore key lost): erased, and settings ask the user to register them again. */
    val networksLost: Boolean = false,
    /**
     * Temporarily unreadable networks (transient Keystore failure): no effect, written back unchanged and read again
     * at the next process start. Key: encrypted value.
     */
    val unreadable: Map<String, Profile> = emptyMap(),
) {

    fun associate(ssid: Ssid, profile: Profile) = copy(networks = networks + (ssid to profile))

    fun dissociate(ssid: Ssid) = copy(networks = networks - ssid)

    fun networksOf(profile: Profile) = networks.filterValues { it == profile }.keys

    /** The user has seen in settings that their networks were lost. */
    fun lossSeen() = copy(networksLost = false)

    /** Turning it on starts with no signal taken into account: the current network applies. */
    fun withEnabled(on: Boolean) = if (on == enabled) this else copy(enabled = on, acknowledged = null, acknowledgedConnection = null)
}

/**
 * What Wi-Fi reports at evaluation time.
 * [profile]: profile registered to the connected network, null with no network, an unknown or a redacted one.
 * [connection]: identity of the connection (the system's network identifier), new for every connection.
 * [since]: known start of the connection, monotonic clock; after the process is recreated, later than the real start.
 * [fresh]: the connection follows an absence of Wi-Fi of at least [ContextEngine.STABLE_MS] (a return, not a blip).
 */
data class Observation(val profile: Profile?, val connection: Long, val since: Long, val fresh: Boolean)
