package io.github.tomlmbl.launcher.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.settings.ClockFont
import io.github.tomlmbl.launcher.settings.fontFamily
import io.github.tomlmbl.launcher.settings.fontWeight
import io.github.tomlmbl.launcher.ui.Tokens

/**
 * Favorites cap: a limit, not a number of places to fill. 6 favorites; the "Search apps" row completes the list.
 */
const val MAX_FAVORITES = 6

/**
 * Home's favorites maximum: whole rows between the clock block and the dock, at least one, at most [MAX_FAVORITES].
 */
fun favoriteCapacity(heightPx: Float, clockPx: Float, gapsPx: Float, rowPx: Float): Int =
    ((heightPx - clockPx - gapsPx) / rowPx).toInt().coerceIn(1, MAX_FAVORITES)

/**
 * Height counted for [favoriteCapacity]: without a dock, Home gains [freedPx] at the bottom but the maximum stays that
 * of Home with a dock, so turning profiles on or off neither shows nor hides any favorite.
 */
fun capacityHeight(heightPx: Float, docked: Boolean, freedPx: Float): Float = if (docked) heightPx else heightPx - freedPx

/**
 * Counter of the favorites editor and onboarding: "N favorites · up to M" below the limit; at the limit, its reason
 * (cap or room above the dock).
 */
@Composable
fun favoritesCounter(count: Int, capacity: Int): String = when {
    count < capacity -> pluralStringResource(R.plurals.favorites_count, count, count, capacity)
    capacity >= MAX_FAVORITES -> pluralStringResource(R.plurals.favorites_max, count, count)
    else -> stringResource(R.string.favorites_limit, count, capacity)
}

/**
 * Favorites maximum when Home is not displayed (onboarding counter): same heights as [HomeScreen], but measured on
 * text (clock in the chosen font, date, a favorite's name) instead of placed elements.
 * Invisible; takes Home's room to know its height, without the keyboard inset (opened by the onboarding search,
 * never on Home).
 */
@Composable
fun HomeCapacityProbe(clockFont: ClockFont, now: Long, eventAllowed: Boolean, onCapacity: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        val clock = TextStyle(fontSize = Tokens.clockSize, lineHeight = Tokens.clockLineHeight, fontFamily = clockFont.fontFamily(), fontWeight = clockFont.fontWeight())
        // Date, then the event line if the calendar is allowed (same rule as [HomeScreen]).
        val linePx = maxOf(measurer.measure(formatDate(now), Tokens.homeDetail).size.height.toFloat(), with(density) { Tokens.homeDetailLine.toPx() }) +
            with(density) { Tokens.Space.xs.toPx() }
        val clockPx = measurer.measure(formatTime(now), clock).size.height + linePx * if (eventAllowed) 2 else 1
        val rowPx = maxOf(with(density) { Tokens.favoriteRowHeight.toPx() }, measurer.measure("Ag", Tokens.favorite).size.height.toFloat())
        val gapsPx = with(density) { (Tokens.clockToFavorites + Tokens.favoritesToDock).toPx() }
        // The "Search apps" row takes one row's room, as on Home.
        val heightPx = with(density) { (maxHeight - Tokens.dockReserve).toPx() } - rowPx
        val capacity = favoriteCapacity(heightPx, clockPx, gapsPx, rowPx)
        LaunchedEffect(capacity) { onCapacity(capacity) }
    }
}
