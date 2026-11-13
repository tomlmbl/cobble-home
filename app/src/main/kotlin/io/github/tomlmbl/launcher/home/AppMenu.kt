package io.github.tomlmbl.launcher.home

import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.border
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.settings.Config
import io.github.tomlmbl.launcher.settings.MenuUndo
import io.github.tomlmbl.launcher.ui.SheetIcon
import io.github.tomlmbl.launcher.ui.SheetRow
import io.github.tomlmbl.launcher.ui.SheetSurface
import io.github.tomlmbl.launcher.ui.SheetTitle
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.rememberDot

/**
 * App menu, opened by a long press in the catalog or on a favorite (then with "Edit favorites").
 * Two cards: "Home screen" (favorites, then one dock per profile) and "App" (info, uninstall); one row per place,
 * its state as subtitle, a single action on the right. The whole row carries the action; TalkBack reads the verb
 * and its consequence.
 * Favorite and dock are mutually exclusive: moving from one to the other needs no confirmation, since the
 * consequence is written. After a removal or a move, the menu stays open and the affected row offers "Undo".
 * Content only: the sheet is placed by Home, shared with the full-dock and full-favorites choices
 * ([ScreenTransitions.sheetSwap]).
 */
@Composable
fun AppMenuSheet(
    app: App,
    config: Config,
    fromFavorite: Boolean,
    undo: MenuUndo?,
    onFavorite: () -> Unit,
    onDock: (Profile) -> Unit,
    onUndo: () -> Unit,
    onEditFavorites: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val favorite = config.isFavorite(app.id)
    val docks = config.dockPlaces(app.id).map { profileLabel(it.profile) }
    Column {
        SheetTitle(app.label)
        MenuGroup(stringResource(R.string.home_screen)) {
            when (undo) {
                is MenuUndo.ToDock -> MenuPlaceRow(
                    R.drawable.ic_star, stringResource(R.string.favorites), stringResource(R.string.removed_undo),
                    MenuAction.Undo, stringResource(R.string.undo_favorite_action), onUndo,
                )
                // Back from the full-favorites choice: the app took a favorite's place, undoable in the row like other moves.
                is MenuUndo.Replaced -> MenuPlaceRow(
                    R.drawable.ic_star_filled, stringResource(R.string.favorites), stringResource(R.string.replaced_favorite_undo, undo.old.label),
                    MenuAction.Undo, stringResource(R.string.undo_replaced_favorite_action, undo.old.label), onUndo,
                )
                else -> {
                    val consequence = when {
                        favorite -> null
                        docks.size == 1 -> stringResource(R.string.removes_from_dock, docks[0])
                        docks.size == 2 -> stringResource(R.string.removes_from_docks, docks[0], docks[1])
                        docks.size >= 3 -> stringResource(R.string.removes_from_all_docks, docks[0], docks[1], docks[2])
                        else -> null
                    }
                    // Favorites full: the row offers to replace a favorite, with the reason.
                    val full = !favorite && config.favorites.full
                    val state = when {
                        favorite -> stringResource(R.string.in_favorites)
                        full -> pluralStringResource(R.plurals.favorites_max, MAX_FAVORITES, MAX_FAVORITES)
                        else -> consequence
                    }
                    val verb = stringResource(when { favorite -> R.string.remove_favorite; full -> R.string.replace_favorite; else -> R.string.add_favorite })
                    MenuPlaceRow(
                        if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star, stringResource(R.string.favorites), state,
                        when { favorite -> MenuAction.Remove; full -> MenuAction.Replace; else -> MenuAction.Add },
                        listOfNotNull(verb, if (full) state else consequence).joinToString(". "), onFavorite,
                    )
                }
            }
            if (fromFavorite) MenuPlaceRow(
                R.drawable.ic_edit, stringResource(R.string.edit_favorites), null, MenuAction.Open, stringResource(R.string.edit_favorites), onEditFavorites,
            )
            if (config.profiles.enabled) config.profiles.selected.forEach { profile ->
                val dock = config.profiles.dock(profile)
                val name = profileLabel(profile)
                val index = dock.indexOf(app.id)
                val removed = when (undo) {
                    is MenuUndo.Removed -> undo.place.profile == profile
                    is MenuUndo.ToFavorites -> undo.places.any { it.profile == profile }
                    else -> false
                }
                val full = dock.slots.none { it == null }
                val (state, action) = when {
                    removed -> stringResource(R.string.removed_undo) to MenuAction.Undo
                    index >= 0 -> stringResource(R.string.slot_number, index + 1) to MenuAction.Remove
                    full -> stringResource(R.string.dock_full) to MenuAction.Replace
                    else -> (if (favorite) stringResource(R.string.removes_from_favorites) else null) to MenuAction.Add
                }
                val description = when {
                    removed -> stringResource(if (undo is MenuUndo.ToFavorites) R.string.undo_favorite_dock_action else R.string.undo_remove_action, name)
                    index >= 0 -> stringResource(R.string.remove_from_dock_action, name, index + 1)
                    full -> stringResource(R.string.dock_full_action, name)
                    favorite -> stringResource(R.string.move_to_dock_action, name)
                    else -> stringResource(R.string.add_to_dock_action, name)
                }
                MenuPlaceRow(
                    R.drawable.ic_dock_to_bottom, stringResource(R.string.dock_label, name), state, action, description,
                    onClick = { if (removed) onUndo() else onDock(profile) },
                )
            }
        }
        MenuGroup(stringResource(R.string.menu_app)) {
            MenuPlaceRow(R.drawable.ic_info, stringResource(R.string.app_info), null, null, stringResource(R.string.app_info), onAppInfo)
            MenuPlaceRow(R.drawable.ic_delete, stringResource(R.string.uninstall), null, null, stringResource(R.string.uninstall), onUninstall)
        }
    }
}

/** Single action on the right of a menu row: add as a filled button, the others as outlined buttons. */
private enum class MenuAction(@DrawableRes val icon: Int, val filled: Boolean) {
    Add(R.drawable.ic_add, true),
    Remove(R.drawable.ic_remove, false),
    Replace(R.drawable.ic_swap_horiz, false),
    Undo(R.drawable.ic_undo, false),
    Open(R.drawable.ic_chevron_right, false),
}

/** Named menu group: title, then a card of rows separated by 2 dp, as in settings. */
@Composable
private fun ColumnScope.MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    BasicText(
        title, style = Tokens.sheetSection,
        modifier = Modifier.padding(start = Tokens.sheetMargin, end = Tokens.sheetMargin, top = Tokens.Space.l, bottom = Tokens.Space.m).semantics { heading() },
    )
    Column(
        Modifier.padding(horizontal = Tokens.menuCardInset).fillMaxWidth().clip(Tokens.menuCardShape),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs),
        content = content,
    )
}

/**
 * Row for one place: icon, place name, state as subtitle, action on the right ([action] null: the row alone).
 * The whole row is tappable; TalkBack reads [description] instead of the content.
 */
@Composable
private fun MenuPlaceRow(
    @DrawableRes icon: Int,
    label: String,
    state: String?,
    action: MenuAction?,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tokens.menuCardRow)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = Tokens.rowMinHeight)
            .padding(start = Tokens.Space.l, end = Tokens.Space.m, top = Tokens.menuRowPadding, bottom = Tokens.menuRowPadding),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetIcon(icon)
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs)) {
            BasicText(label, style = Tokens.menuLabel)
            if (state != null) BasicText(state, style = Tokens.sheetBody.copy(color = Tokens.onSheetMuted))
        }
        if (action != null) Box(
            Modifier
                .size(Tokens.menuActionSize)
                .clip(CircleShape)
                .then(if (action.filled) Modifier.background(Tokens.menuActionFill) else Modifier.border(Tokens.hairline, Tokens.menuActionOutline, CircleShape))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { SheetIcon(action.icon) }
    }
}

/** Dock full: the three slots in dock order; tap the one to replace. "Cancel" changes nothing. */
@Composable
fun DockFullSheet(
    profile: Profile,
    appLabel: String,
    dock: Dock,
    installed: Map<String, App>,
    icon: ThemedIcons,
    onReplace: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        SheetTitle(stringResource(R.string.dock_full_title, profileLabel(profile)), stringResource(R.string.replace_which, appLabel))
        Row(
            Modifier.fillMaxWidth().padding(vertical = Tokens.Space.l),
            horizontalArrangement = Arrangement.spacedBy(Tokens.dockFullSpacing, Alignment.CenterHorizontally),
        ) {
            dock.slots.forEachIndexed { index, slot ->
                val app = slot?.let { installed[it.appId] }
                val label = app?.label ?: slot?.label.orEmpty()
                val image = themedIcon(app, icon, Tokens.dockSlotSize)
                Column(
                    Modifier.clip(Tokens.highlightShape).clickable { onReplace(index) }.semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.m),
                ) {
                    if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(Tokens.dockSlotSize))
                    else Spacer(Modifier.size(Tokens.dockSlotSize))
                    BasicText(label, style = Tokens.sheetBody, modifier = Modifier.heightIn(min = Tokens.Space.xl).clearAndSetSemantics {})
                }
            }
        }
        SheetRow(onClick = onDismiss) { BasicText(stringResource(R.string.cancel), style = Tokens.menuLabel) }
    }
}

/**
 * Favorites full, modeled on the full-dock choice: favorites in their order; tap the one [appLabel] replaces, in
 * its place. "Cancel" changes nothing.
 */
@Composable
fun FavoritesFullSheet(
    appLabel: String,
    favorites: List<Favorite>,
    installed: Map<String, App>,
    color: suspend (App) -> Int?,
    onReplace: (Favorite) -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        SheetTitle(stringResource(R.string.favorites_full_title), stringResource(R.string.replace_which, appLabel))
        favorites.forEach { favorite ->
            val app = installed[favorite.appId]
            val dot = rememberDot(app, color)
            val label = app?.label ?: favorite.label
            SheetRow(onClick = { onReplace(favorite) }, modifier = Modifier.semantics { contentDescription = label }) {
                Box(Modifier.size(Tokens.dotSize).background(dot ?: Tokens.dotNeutral, CircleShape))
                BasicText(label, style = Tokens.menuLabel, modifier = Modifier.weight(1f).clearAndSetSemantics {})
            }
        }
        SheetRow(onClick = onDismiss) { BasicText(stringResource(R.string.cancel), style = Tokens.menuLabel) }
    }
}

/**
 * Favorite picker in a sheet, over Home ("Choose favorites", "Replace") or over the favorites editor ("Add app"):
 * [content] is the app picker, search anchored at the bottom. Design height; with the keyboard open, the sheet
 * takes all the room above it. A tap above closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritePickSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val keyboard = WindowInsets.isImeVisible
    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .imePadding(),
    ) {
        if (!keyboard) Spacer(Modifier.weight(1f))
        SheetSurface(Modifier.weight(Tokens.pickSheetWeight).fillMaxWidth(), fill = true, content = content)
    }
}

