package io.github.tomlmbl.launcher.onboarding

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.catalog.AppPicker
import io.github.tomlmbl.launcher.catalog.Alphabet
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.home.DockChoicePreview
import io.github.tomlmbl.launcher.home.ThemedIcons
import io.github.tomlmbl.launcher.home.favoritesCounter
import io.github.tomlmbl.launcher.home.formatTime
import io.github.tomlmbl.launcher.home.profileLabel
import io.github.tomlmbl.launcher.settings.ClockFont
import io.github.tomlmbl.launcher.settings.fontFamily
import io.github.tomlmbl.launcher.settings.fontWeight
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.PlainButton
import io.github.tomlmbl.launcher.ui.PrimaryButton
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.ui.rememberDot

/** Onboarding step: profiles, apps for each profile, favorites, Home role last. */
sealed interface Step {
    data object Profiles : Step
    data class Apps(val profile: Profile) : Step
    data object Favorites : Step
    data object Role : Step
}

/**
 * Onboarding steps, the Home role step left out if already held. Until profiles are chosen ([selected] null), the
 * default flow counts three of them ("Step 1 of 6"); the total is computed again after the choice.
 */
fun onboardingSteps(selected: List<Profile>?, roleHeld: Boolean): List<Step> =
    listOf(Step.Profiles) +
        (selected ?: Profile.entries.take(Profiles.COUNT)).map { Step.Apps(it) } +
        Step.Favorites +
        listOfNotNull(Step.Role.takeIf { !roleHeld })

/**
 * Shared footer (`Step footer`): "Skip" on the left, filled main button on the right, and between them the counter
 * that says why the button is disabled.
 */
@Composable
fun StepFooter(skip: String?, onSkip: () -> Unit, primary: String, primaryEnabled: Boolean, onPrimary: () -> Unit, counter: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Tokens.screenMargin, end = Tokens.screenMargin, top = Tokens.Space.m, bottom = Tokens.footerBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (skip != null) PlainButton(skip, onSkip)
        BasicText(
            counter.orEmpty(), style = Tokens.hint.copy(textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f).padding(horizontal = Tokens.Space.m),
        )
        PrimaryButton(primary, onClick = onPrimary, enabled = primaryEnabled, minWidth = Tokens.primaryMinWidth)
    }
}

/** Step header: number, title and subtitle; no number outside onboarding (from "Launcher settings"). */
@Composable
fun StepHeader(step: Int?, total: Int, title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(start = Tokens.screenMargin, end = Tokens.screenMargin, top = Tokens.Space.xxl), verticalArrangement = Arrangement.spacedBy(Tokens.Space.m)) {
        if (step != null) BasicText(stringResource(R.string.step_of, step, total), style = Tokens.section)
        BasicText(title, style = stepTitle(step), modifier = Modifier.semantics { heading() })
        if (subtitle != null) BasicText(subtitle, style = Tokens.hint)
    }
}

/** Step title: onboarding style, or screen style outside onboarding (from "Launcher settings"). */
@Composable
private fun stepTitle(step: Int?): TextStyle = if (step != null) Tokens.onboardingTitle else Tokens.screenTitle

/**
 * Choice of the three profiles, during onboarding or from "Launcher settings". Exactly three: at the third, the
 * other rows are grayed out; the main action enables at three, and the counter shows progress.
 * Tap order is kept (Profiles.select).
 */
@Composable
fun ProfileSelectionScreen(
    initial: List<Profile>,
    step: Int?,
    total: Int,
    primary: String,
    skip: String?,
    onSkip: () -> Unit,
    onConfirm: (List<Profile>) -> Unit,
    modifier: Modifier = Modifier,
    /** Title and text for the entry point: onboarding by default; "Change profiles" or turning profiles on from settings. */
    title: String = stringResource(R.string.choose_profiles),
    hint: String = stringResource(R.string.choose_profiles_hint),
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var chosen by remember(initial) { mutableStateOf(initial) }
    Column(modifier.fillMaxSize()) {
        StepHeader(step, total, title, hint)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Tokens.screenMargin, end = Tokens.screenMargin, top = Tokens.stepContentTop),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.ml),
        ) {
            Profile.entries.forEach { profile ->
                val on = profile in chosen
                val enabled = on || chosen.size < Profiles.COUNT
                Row(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else Tokens.disabledAlpha)
                        .clip(Tokens.highlightShape)
                        .background(if (on) Tokens.fieldBackground else Color.Transparent)
                        .toggleable(value = on, enabled = enabled, role = Role.Checkbox) {
                            chosen = if (on) chosen - profile else chosen + profile
                        }
                        .heightIn(min = Tokens.rowMinHeight)
                        .padding(start = Tokens.Space.xl, end = Tokens.Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(profileLabel(profile), style = Tokens.label, modifier = Modifier.weight(1f))
                    Glyph(
                        if (on) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank, Tokens.glyphSize,
                        if (on) Tokens.onWallpaper else Tokens.onWallpaperMuted,
                    )
                }
            }
            extra()
        }
        StepFooter(
            skip, onSkip, primary, chosen.size == Profiles.COUNT, { onConfirm(chosen) },
            counter = pluralStringResource(R.plurals.profiles_selected, chosen.size, chosen.size, Profiles.COUNT),
        )
    }
}

/**
 * A profile's apps: up to three, in dock order; the number on an app is its slot, a tap removes it.
 * Dock preview under the title, hidden while the keyboard is open. Static suggestions first, never preselected;
 * favorites stay visible, grayed out. Search above the footer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DockAppsStep(
    profile: Profile,
    step: Int?,
    total: Int,
    initial: List<App>,
    apps: List<App>,
    suggested: List<App>,
    alphabet: Alphabet,
    query: String,
    onQueryChange: (String) -> Unit,
    color: suspend (App) -> Int?,
    icon: ThemedIcons,
    excluded: (App) -> String?,
    onSkip: () -> Unit,
    onDone: (List<App>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember(profile) { mutableStateOf(initial) }
    val name = profileLabel(profile)
    val keyboard = WindowInsets.isImeVisible
    AppPicker(
        title = stringResource(R.string.choose_apps, name),
        apps = apps, suggested = suggested, suggestedTitle = stringResource(R.string.suggested_for, name),
        alphabet = alphabet, query = query, onQueryChange = onQueryChange, color = color,
        mark = { app -> orderMark(chosen, app) },
        excluded = excluded,
        onPick = { app ->
            chosen = toggled(chosen, app, full = chosen.size >= Dock.SIZE)
        },
        modifier = modifier,
        margin = Tokens.screenMargin,
        ordered = true,
        headline = {
            StepHeader(step, total, stringResource(R.string.choose_apps, name), stringResource(R.string.choose_apps_hint))
            if (!keyboard) {
                DockChoicePreview(chosen, icon, Modifier.align(Alignment.CenterHorizontally).padding(top = Tokens.stepContentTop, bottom = Tokens.Space.l))
            }
        },
    ) {
        StepFooter(stringResource(R.string.skip), onSkip, stringResource(R.string.next), true, { onDone(chosen) })
    }
}

/**
 * Favorites: selection order; alphabetical list without suggestions; dock apps grayed out.
 * "N of M favorites" counter; at Home's maximum, the other apps are grayed out.
 * The selection is held by the caller ([chosen]) to survive going back to the previous step.
 */
@Composable
fun FavoritesStep(
    step: Int?,
    total: Int,
    chosen: List<App>,
    onChosenChange: (List<App>) -> Unit,
    capacity: Int,
    apps: List<App>,
    alphabet: Alphabet,
    query: String,
    onQueryChange: (String) -> Unit,
    color: suspend (App) -> Int?,
    excluded: (App) -> String?,
    onSkip: () -> Unit,
    onDone: (List<App>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val full = chosen.size >= capacity
    AppPicker(
        title = stringResource(R.string.choose_favorites_title),
        apps = apps, suggested = emptyList(), suggestedTitle = null,
        alphabet = alphabet, query = query, onQueryChange = onQueryChange, color = color,
        mark = { app -> orderMark(chosen, app) },
        excluded = excluded,
        onPick = { app ->
            onChosenChange(toggled(chosen, app, full))
        },
        modifier = modifier,
        margin = Tokens.screenMargin,
        ordered = true,
        full = full,
        headline = { StepHeader(step, total, stringResource(R.string.choose_favorites_title), stringResource(R.string.favorites_hint)) },
    ) {
        StepFooter(
            stringResource(R.string.skip), onSkip, stringResource(R.string.next), true, { onDone(chosen) },
            counter = favoritesCounter(chosen.size, capacity),
        )
    }
}

/**
 * Home role, last step: miniature Home; "Not now" finishes onboarding, and the reminder stays in settings.
 */
@Composable
fun RoleStep(
    step: Int?,
    total: Int,
    favorites: List<App>,
    clockFont: ClockFont,
    now: Long,
    color: suspend (App) -> Int?,
    onNotNow: () -> Unit,
    onSetDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        StepHeader(step, total, stringResource(R.string.make_home), stringResource(R.string.make_home_text))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            MiniHome(favorites, clockFont, now, color)
        }
        BasicText(
            stringResource(R.string.make_home_hint), style = Tokens.hint,
            modifier = Modifier.padding(start = Tokens.screenMargin, end = Tokens.screenMargin, bottom = Tokens.stepContentTop),
        )
        StepFooter(stringResource(R.string.not_now), onNotNow, stringResource(R.string.set_default), true, onSetDefault)
    }
}

/**
 * Decorative miniature Home: frame open onto the wallpaper, clock in the chosen font, five favorites (dots of the
 * first chosen favorites, neutral otherwise) and the search row as a grouped list anchored at the bottom, three
 * dock discs with the dock veil. Always with a dock, even without profiles (design choice).
 */
@Composable
private fun MiniHome(favorites: List<App>, clockFont: ClockFont, now: Long, color: suspend (App) -> Int?) {
    val rows: List<App?> = List(Tokens.miniHomeRows) { favorites.getOrNull(it) }
    val bar = Tokens.miniHomeBar
    val surface = Tokens.miniHomeSurface
    Column(
        Modifier
            .size(Tokens.miniHomeWidth, Tokens.miniHomeHeight)
            .border(Tokens.emptySlotBorder, Tokens.onWallpaperMuted, Tokens.miniHomeShape)
            .padding(top = Tokens.miniHomeTop, bottom = Tokens.miniHomeBottom)
            .clearAndSetSemantics {},
    ) {
        BasicText(
            formatTime(now),
            style = Tokens.homeDetail.copy(fontSize = Tokens.miniClockSize, lineHeight = Tokens.miniClockLineHeight, fontFamily = clockFont.fontFamily(), fontWeight = clockFont.fontWeight()),
            modifier = Modifier.padding(start = Tokens.Space.xl),
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(horizontal = Tokens.miniListInset),
            verticalArrangement = Arrangement.spacedBy(Tokens.miniRowGap),
        ) {
            val last = Tokens.miniHomeRows
            for (index in 0..last) {
                val top = if (index == 0) Tokens.miniRowOuterCorner else Tokens.miniRowInnerCorner
                val bottom = if (index == last) Tokens.miniRowOuterCorner else Tokens.miniRowInnerCorner
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Tokens.miniRowHeight)
                        .background(surface, RoundedCornerShape(top, top, bottom, bottom))
                        .padding(start = Tokens.miniDotStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index < last) {
                        val app = rows[index]
                        val dot = if (app != null) rememberDot(app, color) else Tokens.dotNeutral
                        Box(Modifier.size(Tokens.miniDot).background(dot ?: Color.Transparent, CircleShape))
                        Spacer(Modifier.width(Tokens.miniBarStart - Tokens.miniDot))
                        Box(Modifier.size(Tokens.miniBarLengths[index], Tokens.miniBarHeight).background(bar, Tokens.pillShape))
                    } else {
                        Box(Modifier.size(Tokens.miniSearchRing).border(Tokens.hairline, Tokens.onWallpaperMuted, CircleShape))
                        Spacer(Modifier.width(Tokens.miniBarStart - Tokens.miniSearchRing))
                        Box(Modifier.size(Tokens.miniSearchBar, Tokens.miniBarHeight).background(bar, Tokens.pillShape))
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.miniDockGap))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(Tokens.miniDockSpacing)) {
            repeat(Dock.SIZE) { Box(Modifier.size(Tokens.miniDockDisc).background(Tokens.dockIconVeil, CircleShape)) }
        }
    }
}

/** Mark of an app in an ordered choice: its place, starting at 1; null if not selected. */
private fun orderMark(chosen: List<App>, app: App): String? =
    chosen.indexOfFirst { it.id == app.id }.takeIf { it >= 0 }?.let { (it + 1).toString() }

/** Tapping an app in an ordered choice: removes it if present, otherwise appends it, unless the choice is full. */
private fun toggled(chosen: List<App>, app: App, full: Boolean): List<App> = when {
    chosen.any { it.id == app.id } -> chosen.filterNot { it.id == app.id }
    full -> chosen
    else -> chosen + app
}
