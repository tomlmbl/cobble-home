package io.github.tomlmbl.launcher.context

/** Fixed list of contexts, called "profiles" in the UI. The order is that of the selection screen. */
enum class Profile { WORK, HOME, OUT, WORKOUT, TRAVEL }

/** App in a dock slot: identity and last known name. */
data class DockApp(val appId: String, val label: String)

/**
 * A profile's dock: exactly three fixed slots; null is a "+" slot.
 * An app appears at most once. Only user actions and forgetting an uninstalled app change it.
 */
data class Dock(val slots: List<DockApp?> = List(SIZE) { null }) {

    init {
        require(slots.size == SIZE)
    }

    operator fun contains(appId: String) = slots.any { it?.appId == appId }

    fun indexOf(appId: String) = slots.indexOfFirst { it?.appId == appId }

    /** First free slot, or null if the dock is full. */
    val firstFree: Int? get() = slots.indexOfFirst { it == null }.takeIf { it >= 0 }

    /** Puts an app in this slot. No effect if it already occupies another slot of this dock. */
    fun set(index: Int, app: DockApp): Dock {
        if (index !in slots.indices) return this
        val at = indexOf(app.appId)
        if (at >= 0 && at != index) return this
        return Dock(slots.toMutableList().also { it[index] = app })
    }

    fun clear(index: Int) = if (index in slots.indices) Dock(slots.toMutableList().also { it[index] = null }) else this

    /** Empties the slots of the apps matched by the predicate; the others do not move. */
    fun forget(predicate: (String) -> Boolean) = Dock(slots.map { slot -> slot?.takeUnless { predicate(it.appId) } })

    companion object {
        const val SIZE = 3
    }
}

/**
 * Profiles: ordered selection (none or exactly three), active profile, and the docks of every profile, including
 * those of a removed profile, kept dormant. A profile present in `docks` has a kept dock, even if empty; an absent
 * profile was never configured.
 */
data class Profiles(
    val selected: List<Profile> = emptyList(),
    val active: Profile? = null,
    val docks: Map<Profile, Dock> = emptyMap(),
) {

    init {
        require(selected.isEmpty() || (selected.size == COUNT && selected.distinct().size == COUNT))
    }

    val enabled: Boolean get() = selected.isNotEmpty()

    fun dock(profile: Profile) = docks[profile] ?: Dock()

    /** True if the app is in the dock of a selected profile; a dormant dock blocks nothing. */
    fun inSelectedDock(appId: String) = selected.any { appId in dock(it) }

    fun withDock(profile: Profile, dock: Dock) = copy(docks = docks + (profile to dock))

    /** Forgets an app in every dock, selected or dormant. */
    fun forget(predicate: (String) -> Boolean) = copy(docks = docks.mapValues { it.value.forget(predicate) })

    /**
     * New selection. [choice] lists the wanted profiles in the order the user tapped them.
     * A kept profile keeps its place; an added profile takes the place of a removed one, in place order for the
     * removed and tap order for the added. The replacement of the active profile becomes active.
     * Docks of removed profiles stay dormant. When a kept dock comes back, an app that has become a favorite
     * leaves its slot free ([isFavorite]). An empty list turns profiles off.
     */
    fun select(choice: List<Profile>, isFavorite: (String) -> Boolean): Profiles {
        val wanted = choice.distinct()
        if (wanted.isEmpty()) return copy(selected = emptyList(), active = null)
        require(wanted.size == COUNT)
        val added = wanted.filter { it !in selected }.toMutableList()
        val order = if (selected.isEmpty()) wanted else selected.map { if (it in wanted) it else added.removeAt(0) }
        val newActive = when {
            active != null && active in order -> active
            active != null && active in selected -> order[selected.indexOf(active)]
            else -> order.first()
        }
        val returning = order.filter { it !in selected && it in docks }
        val cleaned = docks + returning.associateWith { dock(it).forget(isFavorite) }
        return Profiles(order, newActive, cleaned)
    }

    /** Selected profiles without a kept dock: the app selection screen is offered for them. */
    fun needingApps(previous: Profiles) = selected.filter { it !in previous.selected && it !in previous.docks }

    companion object {
        const val COUNT = 3
    }
}
