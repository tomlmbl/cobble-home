package io.github.tomlmbl.launcher.context

/**
 * Measures how well automatic switching works through manual corrections: local counts, never transmitted.
 * No duration or threshold: the counts are informational only.
 */
enum class ManualChoice {
    /** First manual choice after a signal-driven switch, on the same connection, toward a profile with a registered network. */
    CORRECTION,
    /** Choice of the connected network's profile, which neither switching nor a manual choice applied on this connection. */
    MISSED,
    /** Any other choice: profile without a network, same profile, Wi-Fi absent or unknown. */
    OTHER,
}

object Measure {

    /**
     * [current]: profile of the connected network, null with no known network. [switchedHere]: a signal-driven switch
     * was applied on the current connection, with no manual choice since. [chosenHere]: a manual choice already
     * happened on this connection. [networked]: profiles with at least one registered network.
     */
    fun classify(
        chosen: Profile,
        active: Profile?,
        current: Profile?,
        switchedHere: Boolean,
        chosenHere: Boolean,
        networked: Set<Profile>,
    ) = when {
        chosen == active -> ManualChoice.OTHER
        switchedHere && chosen in networked -> ManualChoice.CORRECTION
        !switchedHere && !chosenHere && current != null && chosen == current -> ManualChoice.MISSED
        else -> ManualChoice.OTHER
    }
}

/** Counts for the current period; [sinceDay]: first counted day (days since the epoch, local time). */
data class MeasureCounts(
    val sinceDay: Long? = null,
    val switches: Int = 0,
    val corrections: Int = 0,
    val missed: Int = 0,
    val manual: Int = 0,
) {
    fun switched(day: Long) = started(day).copy(switches = switches + 1)

    fun chosen(kind: ManualChoice, day: Long) = started(day).let {
        when (kind) {
            ManualChoice.CORRECTION -> it.copy(manual = manual + 1, corrections = corrections + 1)
            ManualChoice.MISSED -> it.copy(manual = manual + 1, missed = missed + 1)
            ManualChoice.OTHER -> it.copy(manual = manual + 1)
        }
    }

    private fun started(day: Long) = if (sinceDay == null) copy(sinceDay = day) else this

    fun encode() = buildString {
        sinceDay?.let { append("since\t").append(it).append('\n') }
        append("switches\t").append(switches).append('\n')
        append("corrections\t").append(corrections).append('\n')
        append("missed\t").append(missed).append('\n')
        append("manual\t").append(manual).append('\n')
    }

    companion object {
        /** Unreadable line: ignored, its count restarts from zero. */
        fun decode(text: String): MeasureCounts {
            val values = text.lineSequence().mapNotNull { line ->
                val fields = line.split('\t')
                fields.getOrNull(1)?.toLongOrNull()?.let { fields[0] to it }
            }.toMap()
            fun count(key: String) = values[key]?.toInt()?.coerceAtLeast(0) ?: 0
            return MeasureCounts(values["since"], count("switches"), count("corrections"), count("missed"), count("manual"))
        }
    }
}
