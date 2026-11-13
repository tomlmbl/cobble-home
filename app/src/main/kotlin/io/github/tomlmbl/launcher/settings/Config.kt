package io.github.tomlmbl.launcher.settings

import io.github.tomlmbl.launcher.context.Detection
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.DockApp
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.context.Ssid
import io.github.tomlmbl.launcher.home.Favorite
import io.github.tomlmbl.launcher.home.Favorites
import io.github.tomlmbl.launcher.home.MAX_FAVORITES

/** Clock font family, among the system's. */
enum class ClockFamily { SANS, SERIF, CONDENSED, MONO }

/** Clock font weight. */
enum class ClockWeight { LIGHT, REGULAR, BOLD }

data class ClockFont(val family: ClockFamily = ClockFamily.SANS, val weight: ClockWeight = ClockWeight.REGULAR)

/** Color of text over the wallpaper: following the wallpaper, or set by the user. */
enum class TextOnWallpaper { AUTOMATIC, LIGHT, DARK }

/** Result of adding to the dock from an app menu. */
sealed interface DockAdd {
    data class Added(val config: Config, val index: Int) : DockAdd
    /** Dock full: the user picks the slot to replace. */
    data object Full : DockAdd
    /** Refused: the app is a favorite. */
    data object Refused : DockAdd
}

/** Dock slot occupied by an app, kept for undo. */
data class DockPlace(val profile: Profile, val index: Int, val app: DockApp)

/** Change made from an app menu, undoable while the menu stays open. */
sealed interface MenuUndo {
    /** Removal from a dock. */
    data class Removed(val place: DockPlace) : MenuUndo
    /** App from one or more docks made a favorite. */
    data class ToFavorites(val appId: String, val places: List<DockPlace>) : MenuUndo
    /** Favorite moved into a dock: its place in the list and its slot. */
    data class ToDock(val favorite: Favorite, val position: Int, val place: DockPlace) : MenuUndo
    /** Favorite replaced when favorites are full: the old one, the app that replaced it and the slots it left. */
    data class Replaced(val old: Favorite, val appId: String, val places: List<DockPlace>) : MenuUndo
}

/**
 * Persisted launcher configuration: favorites, clock font, profiles and docks, automatic switching, onboarding
 * completion, color of text over the wallpaper.
 * Every change to favorites or docks goes through these methods, which enforce the favorite / dock exclusion:
 * an app is either a favorite or in the dock of a selected profile, never both.
 */
data class Config(
    val favorites: Favorites = Favorites(),
    val clockFont: ClockFont = ClockFont(),
    val profiles: Profiles = Profiles(),
    val onboarded: Boolean = false,
    val detection: Detection = Detection(),
    val textOnWallpaper: TextOnWallpaper = TextOnWallpaper.AUTOMATIC,
) {

    fun isFavorite(appId: String) = appId in favorites

    fun inDock(appId: String) = profiles.inSelectedDock(appId)

    fun addFavorite(favorite: Favorite) = if (inDock(favorite.appId)) this else copy(favorites = favorites.add(favorite))

    fun replaceFavorite(appId: String, by: Favorite) =
        if (by.appId != appId && inDock(by.appId)) this else copy(favorites = favorites.replace(appId, by))

    fun removeFavorite(appId: String) = copy(favorites = favorites.remove(appId))

    /** Puts a removed favorite back in place ("Undo" in the favorites editor); no effect if the app moved into a dock meanwhile. */
    fun restoreFavorite(index: Int, favorite: Favorite) = if (inDock(favorite.appId)) this else copy(favorites = favorites.insert(index, favorite))

    /** Favorites chosen during onboarding, in selection order; a dock app is left out. */
    fun withFavorites(list: List<Favorite>) = copy(favorites = Favorites(list.filterNot { inDock(it.appId) }.distinctBy { it.appId }))

    /** A profile's apps chosen in dock order; the rest becomes "+". A favorite app is left out. */
    fun withDockApps(profile: Profile, apps: List<DockApp>): Config {
        val kept = apps.filterNot { isFavorite(it.appId) }.distinctBy { it.appId }.take(Dock.SIZE)
        return copy(profiles = profiles.withDock(profile, Dock(kept + List(Dock.SIZE - kept.size) { null })))
    }

    /** Replaces a slot. No effect if the app is a favorite or already elsewhere in this dock. */
    fun setDockSlot(profile: Profile, index: Int, app: DockApp): Config {
        if (isFavorite(app.appId)) return this
        return copy(profiles = profiles.withDock(profile, profiles.dock(profile).set(index, app)))
    }

    fun clearDockSlot(profile: Profile, index: Int) =
        copy(profiles = profiles.withDock(profile, profiles.dock(profile).clear(index)))

    fun removeFromDock(profile: Profile, appId: String): Config {
        val dock = profiles.dock(profile)
        val index = dock.indexOf(appId)
        return if (index < 0) this else clearDockSlot(profile, index)
    }

    /** "Add to dock": first free slot. */
    fun addToDock(profile: Profile, app: DockApp): DockAdd {
        if (isFavorite(app.appId)) return DockAdd.Refused
        val dock = profiles.dock(profile)
        if (app.appId in dock) return DockAdd.Added(this, dock.indexOf(app.appId))
        val index = dock.firstFree ?: return DockAdd.Full
        return DockAdd.Added(setDockSlot(profile, index, app), index)
    }

    /** Slots of [appId] in the docks of selected profiles, in profile order. */
    fun dockPlaces(appId: String): List<DockPlace> = profiles.selected.mapNotNull { profile ->
        val dock = profiles.dock(profile)
        val index = dock.indexOf(appId)
        dock.slots.getOrNull(index)?.let { DockPlace(profile, index, it) }
    }

    /** "Add to favorites" on a dock app: removed from every selected dock, appended to the list. */
    fun moveToFavorites(favorite: Favorite): Config =
        if (favorites.full) this else profiles.selected.fold(this) { config, profile -> config.removeFromDock(profile, favorite.appId) }.addFavorite(favorite)

    /**
     * Favorites full: [by] takes the place of favorite [appId], the others do not move; a dock app leaves its
     * dock.
     */
    fun replaceFavoriteFromMenu(appId: String, by: Favorite): Config =
        if (!isFavorite(appId) || isFavorite(by.appId)) this
        else profiles.selected.fold(this) { config, profile -> config.removeFromDock(profile, by.appId) }
            .copy(favorites = favorites.replace(appId, by))

    /** "Add" on a favorite: removed from favorites and put in the first free slot; dock full: nothing changes. */
    fun moveToDock(profile: Profile, app: DockApp): DockAdd {
        if (!isFavorite(app.appId)) return addToDock(profile, app)
        return removeFavorite(app.appId).addToDock(profile, app)
    }

    /** Replacement chosen when the dock is full; a favorite leaves the favorites. */
    fun replaceInDock(profile: Profile, index: Int, app: DockApp) = removeFavorite(app.appId).setDockSlot(profile, index, app)

    /** Reverts [undo]; no effect on what the user changed elsewhere meanwhile. */
    fun undo(undo: MenuUndo): Config = when (undo) {
        is MenuUndo.Removed -> setDockSlot(undo.place.profile, undo.place.index, undo.place.app)
        is MenuUndo.ToFavorites -> undo.places.fold(removeFavorite(undo.appId)) { config, place ->
            config.setDockSlot(place.profile, place.index, place.app)
        }
        is MenuUndo.Replaced -> undo.places.fold(copy(favorites = favorites.replace(undo.appId, undo.old))) { config, place ->
            config.setDockSlot(place.profile, place.index, place.app)
        }
        is MenuUndo.ToDock -> {
            val cleared = removeFromDock(undo.place.profile, undo.favorite.appId)
            if (cleared.inDock(undo.favorite.appId)) cleared
            else cleared.copy(favorites = cleared.favorites.insert(undo.position, undo.favorite))
        }
    }

    /** New profile selection; empty list: profiles turned off. */
    fun selectProfiles(choice: List<Profile>) = copy(profiles = profiles.select(choice, ::isFavorite))

    /** Marks a profile's dock as kept, even empty, once its selection step is done. */
    fun keepDock(profile: Profile) = copy(profiles = profiles.withDock(profile, profiles.dock(profile)))

    /** Forgets an uninstalled app in every dock; favorites keep it, grayed out. */
    fun forgetInDocks(predicate: (String) -> Boolean) = copy(profiles = profiles.forget(predicate))
}

/**
 * Text format of the configuration file: a versioned header line, then one line per item, tab-separated fields.
 * Unknown lines and values are ignored, so that an older version can read a newer file without losing favorites.
 */
object ConfigFormat {
    private const val HEADER = "launcher-config\t1"
    private const val FAVORITE = "fav"
    private const val CLOCK = "clock"
    private const val ONBOARDED = "onboarded"
    /** Color of text over the wallpaper, written only if set by the user. */
    private const val TEXT = "text"
    private const val PROFILES = "profiles"
    private const val ACTIVE = "active"
    /** A profile's kept dock, even empty; its apps follow on `slot` lines. */
    private const val DOCK = "dock"
    private const val SLOT = "slot"
    /** Automatic switching: toggle, profile of the last signal, encrypted networks, lost networks. */
    private const val AUTO = "auto"
    private const val SIGNAL = "signal"
    private const val NETWORK = "net"
    private const val NETWORKS_LOST = "netlost"

    /** [codec] encrypts SSIDs; without it, no network is written and the loss is recorded. */
    fun encode(config: Config, codec: SecretCodec = SecretCodec.Unavailable): String = buildString {
        append(HEADER).append('\n')
        config.favorites.items.forEach { append(FAVORITE).append('\t').append(clean(it.appId)).append('\t').append(clean(it.label)).append('\n') }
        append(CLOCK).append('\t').append(config.clockFont.family.name).append('\t').append(config.clockFont.weight.name).append('\n')
        if (config.onboarded) append(ONBOARDED).append('\n')
        if (config.textOnWallpaper != TextOnWallpaper.AUTOMATIC) append(TEXT).append('\t').append(config.textOnWallpaper.name).append('\n')
        val profiles = config.profiles
        if (profiles.enabled) append(PROFILES).append(profiles.selected.joinToString("") { "\t" + it.name }).append('\n')
        profiles.active?.let { append(ACTIVE).append('\t').append(it.name).append('\n') }
        profiles.docks.toSortedMap().forEach { (profile, dock) ->
            append(DOCK).append('\t').append(profile.name).append('\n')
            dock.slots.forEachIndexed { index, app ->
                if (app != null) {
                    append(SLOT).append('\t').append(profile.name).append('\t').append(index)
                        .append('\t').append(clean(app.appId)).append('\t').append(clean(app.label)).append('\n')
                }
            }
        }
        val detection = config.detection
        if (detection.enabled) append(AUTO).append('\n')
        detection.acknowledged?.let { append(SIGNAL).append('\t').append(it.name).append('\n') }
        var lost = detection.networksLost
        detection.networks.toList().sortedBy { it.second }.forEach { (ssid, profile) ->
            val sealed = codec.seal(ssid.bytes())
            if (sealed == null) lost = true
            else append(NETWORK).append('\t').append(profile.name).append('\t').append(sealed).append('\n')
        }
        detection.unreadable.toSortedMap().forEach { (sealed, profile) ->
            append(NETWORK).append('\t').append(profile.name).append('\t').append(sealed).append('\n')
        }
        if (lost) append(NETWORKS_LOST).append('\n')
    }

    fun decode(text: String, codec: SecretCodec = SecretCodec.Unavailable): Config {
        val lines = text.lineSequence().map { it.split('\t') }.toList()
        val favorites = lines
            .filter { it.size >= 2 && it[0] == FAVORITE && it[1].isNotEmpty() }
            .map { Favorite(appId = it[1], label = it.getOrElse(2) { "" }) }
            // Favorites cap: a longer saved list (from an older, higher maximum) is cut down to its first favorites,
            // in their order; the only exception to the stability of [Favorites].
            .distinctBy { it.appId }
            .take(MAX_FAVORITES)
        val clock = lines.lastOrNull { it[0] == CLOCK }?.let { fields ->
            ClockFont(
                family = ClockFamily.entries.find { it.name == fields.getOrNull(1) } ?: ClockFamily.SANS,
                weight = ClockWeight.entries.find { it.name == fields.getOrNull(2) } ?: ClockWeight.REGULAR,
            )
        } ?: ClockFont()
        return Config(
            Favorites(favorites), clock, decodeProfiles(lines, favorites.map { it.appId }.toSet()), lines.any { it[0] == ONBOARDED },
            decodeDetection(lines, codec),
            lines.lastOrNull { it[0] == TEXT }?.let { fields -> TextOnWallpaper.entries.find { it.name == fields.getOrNull(1) } }
                ?: TextOnWallpaper.AUTOMATIC,
        )
    }

    /**
     * A lost network (missing key, corrupted value) is erased and the loss recorded, so settings ask the user to
     * register it again; a temporarily unreadable network is kept encrypted.
     */
    private fun decodeDetection(lines: List<List<String>>, codec: SecretCodec): Detection {
        var lost = lines.any { it[0] == NETWORKS_LOST }
        val networks = LinkedHashMap<Ssid, Profile>()
        val unreadable = LinkedHashMap<String, Profile>()
        lines.filter { it.size >= 3 && it[0] == NETWORK }.forEach { fields ->
            val profile = profile(fields[1]) ?: return@forEach
            when (val opened = codec.open(fields[2])) {
                is Opened.Value -> if (opened.bytes.isEmpty()) lost = true else networks[Ssid.of(opened.bytes)] = profile
                Opened.Lost -> lost = true
                Opened.Retry -> unreadable[fields[2]] = profile
            }
        }
        return Detection(
            enabled = lines.any { it[0] == AUTO },
            networks = networks,
            acknowledged = lines.lastOrNull { it[0] == SIGNAL }?.getOrNull(1)?.let(::profile),
            networksLost = lost,
            unreadable = unreadable,
        )
    }

    /** An invalid selection (other than zero or three distinct profiles) is read as "no profiles". */
    private fun decodeProfiles(lines: List<List<String>>, favorites: Set<String>): Profiles {
        val selected = lines.lastOrNull { it[0] == PROFILES }?.drop(1)?.mapNotNull(::profile).orEmpty()
            .takeIf { it.size == Profiles.COUNT && it.distinct().size == Profiles.COUNT }.orEmpty()
        val active = lines.lastOrNull { it[0] == ACTIVE }?.getOrNull(1)?.let(::profile)?.takeIf { it in selected }
        val docks = lines.filter { it[0] == DOCK }.mapNotNull { profile(it.getOrNull(1)) }.associateWith { Dock() }.toMutableMap()
        lines.filter { it.size >= 4 && it[0] == SLOT && it[3].isNotEmpty() }.forEach { fields ->
            val profile = profile(fields[1]) ?: return@forEach
            val index = fields[2].toIntOrNull() ?: return@forEach
            // A hand-edited file must not break the favorite / dock exclusion: the favorite wins.
            if (profile in selected && fields[3] in favorites) return@forEach
            docks[profile] = (docks[profile] ?: Dock()).set(index, DockApp(fields[3], fields.getOrElse(4) { "" }))
        }
        return Profiles(selected, active ?: selected.firstOrNull(), docks)
    }

    private fun profile(name: String?) = Profile.entries.find { it.name == name }

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
