package io.github.tomlmbl.launcher.home

import android.animation.ValueAnimator
import android.graphics.Rect
import android.text.format.DateFormat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.settings.ClockFont
import io.github.tomlmbl.launcher.settings.fontFamily
import io.github.tomlmbl.launcher.settings.fontWeight
import io.github.tomlmbl.launcher.ui.AppName
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.ScreenTransitions
import io.github.tomlmbl.launcher.ui.SheetIcon
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.LocalContactBlocked
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import io.github.tomlmbl.launcher.ui.contactSurface
import io.github.tomlmbl.launcher.ui.contactProgress
import io.github.tomlmbl.launcher.ui.deferredPress
import io.github.tomlmbl.launcher.ui.pressScale
import io.github.tomlmbl.launcher.ui.pressedIn
import io.github.tomlmbl.launcher.ui.rememberDot
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** A context menu entry: label, optional Material Symbols icon (`Menu`) and action. */
class MenuItem(val label: String, @param:DrawableRes val icon: Int? = null, val onClick: () -> Unit)

/**
 * Home: clock, date, stable favorites; active profile's dock and discreet indicator, nothing without profiles.
 * Swiping up opens the catalog, down the notification shade; long press on an empty area: Home menu. Never changes
 * favorites: it reports the user's intents upward.
 * [faded]: Home fades out under the slot picker, then comes back. Swiping up drives the catalog with the finger
 * ([onCatalogDrag], offset in px, then [onCatalogRelease], velocity in px/s), except with reduced animations, where
 * the catalog opens at the threshold. [contentAlpha] and [contentOffset] (px): opacity and vertical offset of the
 * content, at draw time only: the gesture is measured on a still surface and the gesture return targets stay at their
 * final place, as for the reveal.
 */
@Composable
fun HomeScreen(
    now: Long,
    clockFont: ClockFont,
    weather: WeatherGlance?,
    event: EventGlance?,
    eventAllowed: Boolean,
    slots: List<FavoriteSlot<App>>?,
    color: suspend (App) -> Int?,
    onLaunch: (App, Rect?) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenShade: () -> Unit,
    onOpenClock: () -> Unit,
    onOpenPanel: () -> Unit,
    onEditFavorites: () -> Unit,
    onChooseFavorites: () -> Unit,
    onReplace: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    onCapacity: (Int) -> Unit,
    onMenuShown: (Boolean) -> Unit,
    onAppMenu: (App) -> Unit,
    profile: Profile?,
    dock: Dock?,
    installed: Map<String, App>?,
    icon: ThemedIcons,
    onDockSlot: (Int) -> Unit,
    onOpenProfiles: () -> Unit,
    dockAnchor: DockAnchor,
    faded: Boolean = false,
    onCatalogDrag: ((Float) -> Unit)? = null,
    onCatalogRelease: ((Float) -> Unit)? = null,
    /** Gesture taken over by the system: the catalog is decided like a release without velocity, without opening search. */
    onCatalogCancel: (() -> Unit)? = null,
    /** "Search" row at the end of the favorites list: search only, keyboard open. */
    onOpenSearch: (() -> Unit)? = null,
    contentAlpha: () -> Float = { 1f },
    contentOffset: () -> Float = { 0f },
    /** Changes at each return Home (Home button, screen off): the open menu closes. */
    menuReset: Int = 0,
    /** Unlock arrival progress of the element of a rank, from the bottom (0) upward: 1 in place. */
    arrival: (Int) -> Float = { 1f },
) {
    val arrivalSpec = Tokens.unlockArrival
    fun Modifier.arriving(rank: Int) = arrive(arrivalSpec) { arrival(rank) }
    val visible by animateFloatAsState(
        if (faded) 0f else 1f,
        if (faded) tween(Tokens.homeFadeOutMs, easing = Tokens.fadeOutEasing) else tween(Tokens.homeFadeInMs, easing = Tokens.fadeInEasing),
        label = "homeFade",
    )
    val fg = Tokens.onWallpaper
    val shadow = Tokens.homeShadow
    val detail = Tokens.homeDetail
    val swipePx = with(LocalDensity.current) { Tokens.swipeThreshold.toPx() }
    var menu by remember { mutableStateOf<Pair<Offset, List<MenuItem>>?>(null) }
    LaunchedEffect(menuReset) { menu = null }

    val editLabel = stringResource(R.string.edit_favorites)
    val homeMenu = listOf(
        MenuItem(editLabel, R.drawable.ic_edit, onEditFavorites),
        MenuItem(stringResource(R.string.wallpaper), R.drawable.ic_wallpaper, onWallpaper),
        MenuItem(stringResource(R.string.launcher_settings), R.drawable.ic_settings, onSettings),
    )
    val allApps = stringResource(R.string.all_apps)
    val searchLabel = stringResource(R.string.search_hint)
    val notifications = stringResource(R.string.notifications)
    val replaceLabel = stringResource(R.string.replace)
    val removeLabel = stringResource(R.string.remove)
    // Gestures survive recompositions: they read current values, not those of the first composition.
    val drag by rememberUpdatedState(onCatalogDrag)
    val dragRelease by rememberUpdatedState(onCatalogRelease)
    val dragCancel by rememberUpdatedState(onCatalogCancel)
    val openCatalog by rememberUpdatedState(onOpenCatalog)
    val openShade by rememberUpdatedState(onOpenShade)
    val swipe by rememberUpdatedState(swipePx)
    val longPressMenu by rememberUpdatedState(homeMenu)

    // Profiles on: room for the dock and the pill reserved, even while apps load.
    val docked = profile != null
    // Drag in progress: favorites and dock do not take their touch shape.
    val homeDragging = remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContactBlocked provides homeDragging) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                var fired = false
                // Finger-driven drag: decided at the first movement, upward, with animations on.
                var follow = false
                val tracker = VelocityTracker()
                fun release(velocity: Float) { if (follow) dragRelease?.invoke(velocity) }
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; fired = false; follow = false; tracker.resetTracking(); homeDragging.value = true },
                    onVerticalDrag = { change, amount ->
                        if (total == 0f) follow = amount < 0 && drag != null && ValueAnimator.areAnimatorsEnabled()
                        total += amount
                        if (follow) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            drag?.invoke(amount)
                        } else if (!fired && abs(total) > swipe) {
                            fired = true
                            if (total < 0) openCatalog() else openShade()
                        }
                    },
                    onDragEnd = { homeDragging.value = false; release(tracker.calculateVelocity().y) },
                    onDragCancel = { homeDragging.value = false; dragCancel.let { if (follow && it != null) it() else release(0f) } },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = it to longPressMenu }) }
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction(allApps) { onOpenCatalog(); true })
                    add(CustomAccessibilityAction(notifications) { onOpenShade(); true })
                    add(CustomAccessibilityAction(editLabel) { onEditFavorites(); true })
                }
            }
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = visible * contentAlpha() }
                .drawWithContent { translate(top = contentOffset()) { this@drawWithContent.drawContent() } },
        ) {
            // Stacked from the bottom: clock, 40 dp, favorites, 32 dp, dock room; the top stays empty for the shade
            // gesture and the long press. Nothing is placed before favorites load, so the clock does not jump.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                // Actual row height, which follows the font size; otherwise the minimum height.
                val density = LocalDensity.current
                val minRowPx = with(density) { Tokens.favoriteRowHeight.toPx() }
                val gapsPx = with(density) { (Tokens.clockToFavorites + Tokens.favoritesToDock).toPx() }
                var rowPx by remember { mutableFloatStateOf(minRowPx) }
                var clockPx by remember { mutableIntStateOf(0) }
                var datePx by remember { mutableIntStateOf(0) }
                var zoneContentPx by remember { mutableIntStateOf(0) }
                // Maximum computed bottom-up: what fits between the dock and the clock block. With the calendar
                // allowed, the event line counts even when absent (at the date's height): an event never removes
                // a favorite.
                val eventLinePx = if (eventAllowed) datePx + with(density) { Tokens.Space.xs.toPx() } else 0f
                // Without profiles, Home moves down but the maximum stays that of Home with a dock: turning profiles on
                // or off neither shows nor hides any favorite. The "Search apps" row takes one row's room.
                val freedPx = with(density) { (Tokens.dockReserve - Tokens.noDockReserve).toPx() }
                val searchRows = if (onOpenSearch != null) 1 else 0
                val rowFloor = rowPx.coerceAtLeast(minRowPx)
                val capacity = favoriteCapacity(capacityHeight(constraints.maxHeight.toFloat(), docked, freedPx) - searchRows * rowFloor, clockPx + datePx + eventLinePx, gapsPx, rowFloor)
                if (slots != null) {
                    // Arrival ranks from the bottom: dock as one block, "Search apps", favorites from the last, event,
                    // date line, clock.
                    val shownCount = if (slots.isEmpty()) 1 else slots.size.coerceAtMost(capacity)
                    val listBase = (if (docked) 1 else 0) + searchRows
                    val eventRank = listBase + shownCount
                    LaunchedEffect(capacity) { onCapacity(capacity) }
                    // Favorites beyond the maximum: kept but not shown; logged.
                    LaunchedEffect(capacity, slots.size) { if (slots.size > capacity) Log.i("Config", "shown $capacity of ${slots.size}") }
                    Column(Modifier.fillMaxWidth().padding(bottom = Tokens.favoritesToDock)) {
                        Column(Modifier.padding(horizontal = Tokens.screenMargin)) {
                            Column(Modifier.onSizeChanged { clockPx = it.height }.arriving(eventRank + 2)) {
                                Clock(now, clockFont, fg, shadow, onOpenClock)
                                Spacer(Modifier.height(Tokens.Space.xs))
                            }
                            // Date, weather and event: a single area, at least 48 dp, overflowing onto the gap with the
                            // favorites rather than moving them (like the indicator).
                            GlanceZone(onOpenPanel, onContentHeight = { zoneContentPx = it }) {
                                DateLine(now, weather, modifier = Modifier.onSizeChanged { datePx = it.height }.arriving(eventRank + 1))
                                EventLine(now, event, modifier = Modifier.padding(top = Tokens.Space.xs).arriving(eventRank))
                            }
                        }
                        val overflow = with(density) { (Tokens.touchTarget.roundToPx() - zoneContentPx).coerceAtLeast(0).toDp() }
                        Spacer(Modifier.height(Tokens.clockToFavorites - overflow))
                        if (slots.isEmpty()) {
                            Box(Modifier.arriving(listBase)) { ChooseFavorites(fg, shadow, onChooseFavorites) }
                        } else {
                            val shown = slots.take(capacity)
                            Column(
                                Modifier
                                    .onSizeChanged { rowPx = it.height.toFloat() / (shown.size + searchRows) }
                                    .padding(horizontal = Tokens.favoritesGroupInset),
                                verticalArrangement = Arrangement.spacedBy(Tokens.favoritesGroupGap),
                            ) {
                                shown.forEachIndexed { i, slot ->
                                    val surface = Tokens.favoriteShape(first = i == 0, last = i == shown.lastIndex && searchRows == 0)
                                    val radii = Tokens.favoriteRadii(first = i == 0, last = i == shown.lastIndex && searchRows == 0)
                                    val arriving = Modifier.arriving(listBase + shown.lastIndex - i)
                                    when (slot) {
                                        is FavoriteSlot.Installed -> FavoriteRow(
                                            slot.app.label, fg, shadow, appId = slot.app.id, dot = { rememberDot(slot.app, color) }, paused = slot.app.paused, surface = surface, radii = radii,
                                            otherProfile = slot.app.otherProfile, description = slot.app.description,
                                            onClick = { bounds -> onLaunch(slot.app, bounds) },
                                            onLongClick = { onAppMenu(slot.app) },
                                            modifier = arriving,
                                        )
                                        is FavoriteSlot.Missing -> FavoriteRow(
                                            slot.favorite.label, fg, shadow, dot = { Tokens.dotNeutral }, missing = true, surface = surface, radii = radii,
                                            onClick = { _ -> },
                                            onClickAt = { at ->
                                                menu = at to listOf(
                                                    MenuItem(replaceLabel) { onReplace(slot.favorite) },
                                                    MenuItem(removeLabel) { onRemove(slot.favorite) },
                                                )
                                            },
                                            onLongClick = { at -> menu = at to listOf(MenuItem(editLabel, R.drawable.ic_edit, onEditFavorites)) },
                                            modifier = arriving,
                                        )
                                    }
                                }
                                // Search in the list, magnifier in place of the dot; never a favorite (no editing, no app menu).
                                if (onOpenSearch != null) FavoriteRow(
                                    searchLabel, fg, shadow, dot = { null }, icon = R.drawable.ic_search,
                                    surface = Tokens.favoriteShape(first = shown.isEmpty(), last = true),
                                    radii = Tokens.favoriteRadii(first = shown.isEmpty(), last = true),
                                    // The "Search apps" row keeps the ripple, without a touch shape.
                                    withContact = false,
                                    onClick = { _ -> onOpenSearch() },
                                    onLongClick = { at -> menu = at to homeMenu },
                                    modifier = Modifier.arriving(listBase - 1),
                                )
                            }
                        }
                    }
                }
            }

            // Dock and profile indicator; without profiles, a margin only: Home moves down.
            Box(Modifier.fillMaxWidth().height(if (docked) Tokens.dockReserve else Tokens.noDockReserve), contentAlignment = Alignment.BottomCenter) {
                if (profile != null && dock != null && installed != null) {
                    // Hidden while the slot picker shows its preview, which leaves from here and comes back here.
                    Column(
                        Modifier.padding(bottom = Tokens.indicatorBottom).graphicsLayer { alpha = if (dockAnchor.lifted) 0f else 1f }.arriving(0),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // On any profile change, manual or signal-driven, the icons change slot by slot, from left
                        // to right.
                        val slide = with(LocalDensity.current) { Tokens.profileSwitchSlide.roundToPx() }
                        AnimatedContent(
                            profile to dock, Modifier.dockAnchor(dockAnchor), contentKey = { it.first },
                            transitionSpec = { ScreenTransitions.profileSwitch() },
                            label = "dock",
                        ) { (p, d) ->
                            DockRow(p, d, installed, icon, onLaunch, onDockSlot, slotModifier = { index ->
                                Modifier.animateEnterExit(ScreenTransitions.profileSlotEnter(index, slide), ScreenTransitions.profileSlotExit(index, slide))
                            })
                        }
                        // The indicator's touch target overflows upward onto the gap with the dock.
                        Spacer(Modifier.height(Tokens.dockToIndicator - (Tokens.touchTarget - Tokens.indicatorHeight)))
                        ProfileIndicator(profile, onClick = onOpenProfiles)
                    }
                }
            }
        }

        menu?.let { (at, items) -> ContextMenu(at, items, onDismiss = { menu = null }) }
        val menuShown = menu != null
        DisposableEffect(menuShown) {
            onMenuShown(menuShown)
            onDispose { onMenuShown(false) }
        }
    }
    }
}

@Composable
private fun Clock(now: Long, font: ClockFont, fg: Color, shadow: androidx.compose.ui.graphics.Shadow?, onClick: () -> Unit) {
    // Ripple under the text, so the shadow and the chosen font's descenders are not clipped, overflowing on the sides
    // so it does not hug the digits; the touch target stays the text's.
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.clickable(interaction, indication = null, onClick = onClick).semantics(mergeDescendants = true) { heading() }) {
        PressHighlight(interaction)
        BasicText(
            formatTime(now),
            style = TextStyle(color = fg, fontSize = Tokens.clockSize, lineHeight = Tokens.clockLineHeight, shadow = shadow, fontFamily = font.fontFamily(), fontWeight = font.fontWeight()),
            modifier = Modifier.pressScale(interaction, Offset(0f, 0.5f)),
        )
    }
}

/** Ripple of a Home area, overflowing on the sides; to be placed first in a `Box`. */
@Composable
private fun BoxScope.PressHighlight(interaction: MutableInteractionSource) {
    Box(
        Modifier
            .matchParentSize()
            .layout { measurable, constraints ->
                val outset = Tokens.clockPressOutset.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + 2 * outset, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-outset, 0) }
            }
            .pressedIn(deferredPress(interaction), Tokens.cardShape)
    )
}

/** Time [now] in the system format (12 or 24 h). */
@Composable
fun formatTime(now: Long): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    return DateFormat.format(pattern, now).toString()
}

@Composable
internal fun formatDate(now: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), Date(now)).toString()
}

/** Panel area: tappable across the full width and at least [Tokens.touchTarget] high. */
@Composable
private fun GlanceZone(onClick: () -> Unit, onContentHeight: (Int) -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.open_panel)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.touchTarget)
            .clickable(interaction, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        PressHighlight(interaction)
        Column(Modifier.onSizeChanged { onContentHeight(it.height) }) { content() }
    }
}

/**
 * Date, then the current hour's weather if any: dimmed, without age, after 4 h, absent after 12 h. The date is
 * always shown.
 */
@Composable
private fun DateLine(now: Long, weather: WeatherGlance?, modifier: Modifier = Modifier) {
    val freshness = weather?.freshness(now)
    val hour = weather?.at(now)
    Row(modifier.heightIn(min = Tokens.homeDetailLine), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
        BasicText(formatDate(now), style = Tokens.homeDetail)
        if (hour != null && freshness != Freshness.HIDDEN) {
            val aged = freshness == Freshness.AGED
            val style = if (aged) Tokens.homeDetail.copy(color = Tokens.onWallpaperMuted) else Tokens.homeDetail
            BasicText("·", style = style)
            Glyph(hour.icon, Tokens.homeDetailGlyph, style.color)
            BasicText(hour.temperature, style = style)
        }
    }
}

/**
 * Ongoing event, otherwise the next one: glyph and title like the date, time dimmed; nothing without an event,
 * without permission or once the event is over. Title on one line, truncated before the time.
 */
@Composable
private fun EventLine(now: Long, event: EventGlance?, modifier: Modifier = Modifier) {
    val zone = remember(now) { ZoneId.systemDefault() }
    val time = event?.time(now, zone) ?: return
    val style = Tokens.homeDetail
    Row(modifier.heightIn(min = Tokens.homeDetailLine), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.m), verticalAlignment = Alignment.CenterVertically) {
        Glyph(R.drawable.ic_event, Tokens.homeDetailGlyph, style.color)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
            BasicText(
                event.title.ifBlank { stringResource(R.string.untitled_event) }, style = style, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            BasicText(eventTime(time), style = style.copy(color = Tokens.onWallpaperMuted), maxLines = 1)
        }
    }
}

@Composable
private fun eventTime(time: EventTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (time) {
        is EventTime.Until -> stringResource(R.string.event_until, formatTime(time.end))
        is EventTime.InMinutes -> pluralStringResource(R.plurals.event_in_minutes, time.minutes, time.minutes)
        is EventTime.Today -> stringResource(R.string.event_at, formatTime(time.begin))
        is EventTime.Tomorrow -> stringResource(R.string.event_tomorrow, formatTime(time.begin))
        is EventTime.Later -> stringResource(R.string.event_later, time.day.getDisplayName(DateTextStyle.SHORT, locale), formatTime(time.begin))
    }
}

@Composable
private fun ChooseFavorites(fg: Color, shadow: androidx.compose.ui.graphics.Shadow?, onClick: () -> Unit) {
    // Never an empty Home without an action; nothing is filled in on the user's behalf.
    Box(
        Modifier
            .padding(horizontal = Tokens.screenMargin)
            .fillMaxWidth()
            .heightIn(min = Tokens.placeholderMinHeight)
            .border(Tokens.hairline, fg.copy(alpha = Tokens.placeholderBorderAlpha), Tokens.cardShape)
            .clip(Tokens.cardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("+  " + stringResource(R.string.choose_favorites), style = Tokens.homeDetail.copy(color = fg, shadow = shadow))
    }
}

@Composable
private fun FavoriteRow(
    label: String,
    fg: Color,
    shadow: androidx.compose.ui.graphics.Shadow?,
    modifier: Modifier = Modifier,
    appId: String? = null,
    dot: @Composable () -> Color?,
    missing: Boolean = false,
    paused: Boolean = false,
    otherProfile: Boolean = false,
    description: String = label,
    /** Grouped list: shape of the row's surface, by its place in the group. */
    surface: Shape,
    /** Corners of [surface] (the surface becomes a pill on touch). */
    radii: List<Dp>,
    /** Icon in place of the dot, centered on its axis (the "Search" row). */
    @DrawableRes icon: Int? = null,
    /** Touch shape; the "Search apps" row has none. */
    withContact: Boolean = true,
    onClick: (Rect?) -> Unit,
    onClickAt: ((Offset) -> Unit)? = null,
    onLongClick: (Offset) -> Unit,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val uninstalled = stringResource(R.string.uninstalled, label)
    val pausedState = stringResource(R.string.paused)
    val interaction = remember { MutableInteractionSource() }
    // Touch feedback: a touch shape instead of a ripple and a shrinking name.
    val contact = withContact
    val progress = if (contact) contactProgress(deferredPress(interaction)) else null
    val surfaceColor = Tokens.favoriteSurface
    // The full width is tappable; a long press on the background stays possible in the empty top.
    // The launch animation starts from the dot and the name, not the whole row.
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.favoriteRowHeight - Tokens.favoritesGroupGap)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { if (onClickAt != null) onClickAt(origin) else onClick(bounds) },
                onLongClick = { onLongClick(origin) },
            )
            // Tap across the full width, ripple inset like the catalog's highlighted row; in a grouped list, inside the
            // row's surface.
            .then(
                if (contact) Modifier.contactSurface({ progress!!.value }, { surfaceColor }, radii)
                else Modifier.background(Tokens.favoriteSurface, surface).pressedIn(deferredPress(interaction), surface)
            )
            .padding(horizontal = Tokens.screenMargin - Tokens.favoritesGroupInset)
            .then(if (missing) Modifier.alpha(Tokens.missingAlpha).semantics { this.contentDescription = uninstalled } else Modifier)
            .then(if (paused) Modifier.alpha(Tokens.disabledAlpha).semantics { stateDescription = pausedState } else Modifier)
            .then(if (otherProfile) Modifier.semantics { contentDescription = description } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.onGloballyPositioned { c ->
                bounds = c.windowRect()
                origin = c.boundsInWindow().let { Offset(it.left, it.bottom) }
            }.then(if (contact) Modifier else Modifier.pressScale(interaction, Offset(0f, 0.5f))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = dot()
            // Optical alignment: the magnifier's circle, not the glyph's box, is concentric with the dots.
            if (icon != null) Box(Modifier.size(Tokens.dotSize).wrapContentSize(unbounded = true).offset(Tokens.favoriteIconLensShift, Tokens.favoriteIconLensShift)) {
                Glyph(icon, Tokens.favoriteIconSize, Tokens.themedIcon)
            } else Box(
                Modifier
                    .size(Tokens.dotSize)
                    .then(if (appId != null) Modifier.returnTarget(appId, color?.let { ReturnLook.Dot(it.toArgb()) }) else Modifier)
                    .background(color ?: Color.Transparent, CircleShape)
            )
            Spacer(Modifier.width(Tokens.dotLabel))
            AppName(label, otherProfile, Tokens.favorite.copy(color = fg, shadow = shadow))
        }
    }
}

/** Menu near the finger, position in window coordinates; a tap elsewhere or Back closes it. */
@Composable
fun ContextMenu(at: Offset, items: List<MenuItem>, onDismiss: () -> Unit) {
    // Touched point in the menu, as a fraction of its size: origin of the appearance, like the M3 menu.
    val origin = remember { floatArrayOf(0f, 0f) }
    val position = remember(at) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val offset = IntOffset(
                    at.x.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    at.y.roundToInt().coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                )
                if (popupContentSize.width > 0 && popupContentSize.height > 0) {
                    origin[0] = ((at.x - offset.x) / popupContentSize.width).coerceIn(0f, 1f)
                    origin[1] = ((at.y - offset.y) / popupContentSize.height).coerceIn(0f, 1f)
                }
                return offset
            }
        }
    }
    // Appearance and disappearance: scale on the fast spatial spring, opacity on the fast effects spring; the menu is
    // only removed once faded, and accepts no touch while fading.
    val expanded = remember { MutableTransitionState(false).apply { targetState = true } }
    val open = expanded.targetState
    val close = { expanded.targetState = false }
    LaunchedEffect(Unit) { snapshotFlow { expanded.isIdle && !expanded.currentState }.first { it }; onDismiss() }
    val transition = rememberTransition(expanded, label = "menu")
    val scale by transition.animateFloat({ Tokens.spatialFast() }, label = "scale") { if (it) 1f else Tokens.menuClosedScale }
    val alpha by transition.animateFloat({ Tokens.effectsFast() }, label = "alpha") { if (it) 1f else 0f }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = close,
        properties = PopupProperties(focusable = true),
    ) {
        OnSurface {
            // `Menu`: 240 dp, 48 dp rows, icon and label, `Elevation/Menu` shadow.
            Column(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(origin[0], origin[1])
                    }
                    .padding(Tokens.menuElevation)
                    .width(Tokens.menuWidth)
                    .shadow(Tokens.menuElevation, Tokens.menuShape)
                    .background(Tokens.menuBackground, Tokens.menuShape)
                    .padding(vertical = Tokens.Space.m),
            ) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.menuItemInset)
                            .clip(Tokens.highlightShape)
                            .clickable(enabled = open) { close(); item.onClick() }
                            .heightIn(min = Tokens.touchTarget)
                            .padding(horizontal = Tokens.Space.l - Tokens.menuItemInset),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.ml),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.icon?.let { SheetIcon(it) }
                        BasicText(item.label, style = Tokens.sheetBody)
                    }
                }
            }
        }
    }
}
