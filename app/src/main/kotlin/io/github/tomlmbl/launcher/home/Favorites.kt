package io.github.tomlmbl.launcher.home

/** Saved favorite: app identity and last known name, to show it grayed out if the app goes away. */
data class Favorite(val appId: String, val label: String)

/**
 * Ordered list of favorites. Only user actions change it (stability invariant): no method sorts, removes an
 * uninstalled app or reorders by itself.
 * An app is a favorite at most once; an addition goes to the end of the list.
 */
data class Favorites(val items: List<Favorite> = emptyList()) {

    operator fun contains(appId: String) = items.any { it.appId == appId }

    /** Cap reached: no addition and no reinsertion. */
    val full get() = items.size >= MAX_FAVORITES

    fun add(favorite: Favorite) = if (favorite.appId in this || full) this else Favorites(items + favorite)

    /** Puts a favorite back at [index] (undo); bounded to the list. */
    fun insert(index: Int, favorite: Favorite): Favorites {
        if (favorite.appId in this || full) return this
        return Favorites(items.toMutableList().also { it.add(index.coerceIn(0, it.size), favorite) })
    }

    fun remove(appId: String) = Favorites(items.filterNot { it.appId == appId })

    /** Replaces a favorite in the same place. No effect if the replacement is already a favorite elsewhere. */
    fun replace(appId: String, by: Favorite): Favorites {
        if (by.appId != appId && by.appId in this) return this
        return Favorites(items.map { if (it.appId == appId) by else it })
    }

    fun move(from: Int, to: Int): Favorites {
        if (from !in items.indices || to !in items.indices || from == to) return this
        val list = items.toMutableList()
        list.add(to, list.removeAt(from))
        return Favorites(list)
    }

    /** Pairs each favorite with its installed app, or marks it missing; order is kept. */
    fun <A> resolve(installed: Map<String, A>): List<FavoriteSlot<A>> =
        items.map { fav -> installed[fav.appId]?.let { FavoriteSlot.Installed(fav, it) } ?: FavoriteSlot.Missing(fav) }
}

sealed interface FavoriteSlot<out A> {
    val favorite: Favorite

    data class Installed<A>(override val favorite: Favorite, val app: A) : FavoriteSlot<A>
    data class Missing(override val favorite: Favorite) : FavoriteSlot<Nothing>
}
