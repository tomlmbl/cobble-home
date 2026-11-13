package io.github.tomlmbl.launcher.settings

/**
 * Summary of a write that changes favorites or a dock: counts and positions only, never an app identifier or name.
 * Favorites: each position of the new list gives the app's old position, "+" for an added app. Dock: each changed
 * slot, with "-" emptied, "+" filled, "<j" moved from slot j, "*" replaced by another app. Profile names come from
 * the fixed list, not from user data.
 */
object ConfigChange {

    fun describe(before: Config, after: Config): String? {
        val parts = mutableListOf<String>()
        val was = before.favorites.items.map { it.appId }
        val now = after.favorites.items.map { it.appId }
        if (was != now) {
            val order = now.joinToString(",") { id -> was.indexOf(id).let { if (it < 0) "+" else "$it" } }
            parts += "fav ${was.size}->${now.size} order=$order removed=${was.count { it !in now }}"
        }
        val profiles = (before.profiles.docks.keys + after.profiles.docks.keys).sortedBy { it.ordinal }
        for (profile in profiles) {
            val old = before.profiles.dock(profile).slots.map { it?.appId }
            val new = after.profiles.dock(profile).slots.map { it?.appId }
            if (old == new) continue
            val changed = new.indices.filter { new[it] != old.getOrNull(it) }.joinToString(",") { i ->
                val id = new[i]
                val from = old.indexOf(id)
                when {
                    id == null -> "$i-"
                    old.getOrNull(i) == null && from < 0 -> "$i+"
                    from >= 0 -> "$i<$from"
                    else -> "$i*"
                }
            }
            parts += "dock ${profile.name} $changed"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ; ")
    }

    /** State read at startup: number of favorites and occupied slots of each kept dock. */
    fun summary(config: Config): String =
        "fav=${config.favorites.items.size} docks=" +
            config.profiles.docks.entries.sortedBy { it.key.ordinal }
                .joinToString(",") { (profile, dock) -> "${profile.name}:${dock.slots.count { it != null }}" }
}
