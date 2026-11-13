package io.github.tomlmbl.launcher.settings

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.home.profileLabel
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Tokens
import kotlin.math.abs

/** Compose family and weight of a clock font; system families only. */
fun ClockFont.fontFamily(): FontFamily = when (family) {
    ClockFamily.SANS -> FontFamily.SansSerif
    ClockFamily.SERIF -> FontFamily.Serif
    // Family built from a fixed `Typeface`: Compose does not apply the requested weight to it, so it is set here, to
    // the weight the condensed family actually draws (grayed out otherwise, never synthesized).
    ClockFamily.CONDENSED -> condensed.getValue(drawnWeight)
    ClockFamily.MONO -> FontFamily.Monospace
}

private val condensed: Map<ClockWeight, FontFamily> by lazy {
    val base = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    ClockWeight.entries.associateWith { FontFamily(Typeface.create(base, it.value(), false)) }
}

/**
 * Drawn weight: the chosen one if the family has it, otherwise regular, which the system would draw anyway. The
 * chosen weight stays saved and comes back with a family that has it.
 */
val ClockFont.drawnWeight: ClockWeight get() = if (family.hasWeight(weight)) weight else ClockWeight.REGULAR

fun ClockFont.fontWeight(): FontWeight = when (drawnWeight) {
    ClockWeight.LIGHT -> FontWeight.Light
    ClockWeight.REGULAR -> FontWeight.Normal
    ClockWeight.BOLD -> FontWeight.Bold
}

private fun ClockWeight.value() = when (this) {
    ClockWeight.LIGHT -> 300
    ClockWeight.REGULAR -> 400
    ClockWeight.BOLD -> 700
}

/**
 * True if the system font draws this weight itself: no synthetic bold and no fallback to a nearby weight. Read from
 * the text engine (font and weight picked for a glyph), once per process.
 */
fun ClockFamily.hasWeight(weight: ClockWeight): Boolean = weight == ClockWeight.REGULAR || weight in drawnWeights.getValue(this)

private val drawnWeights: Map<ClockFamily, Set<ClockWeight>> by lazy {
    ClockFamily.entries.associateWith { family ->
        val base = when (family) {
            ClockFamily.SANS -> Typeface.SANS_SERIF
            ClockFamily.SERIF -> Typeface.SERIF
            ClockFamily.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            ClockFamily.MONO -> Typeface.MONOSPACE
        }
        ClockWeight.entries.filterTo(mutableSetOf()) { weight ->
            val paint = Paint().apply { typeface = Typeface.create(base, weight.value(), false) }
            val glyphs = TextRunShaper.shapeTextRun("0", 0, 1, 0, 1, 0f, 0f, false, paint)
            // Variable font (Roboto): weight applied through the axis; otherwise `Float.MIN_VALUE` and the picked file's weight.
            val override = glyphs.getWeightOverride(0)
            val drawn = if (override >= 1f) override else glyphs.getFont(0).style.weight.toFloat()
            !glyphs.getFakeBold(0) && abs(drawn - weight.value()) < 50f
        }
    }
}

private val families = listOf(
    ClockFamily.SANS to R.string.font_sans,
    ClockFamily.SERIF to R.string.font_serif,
    ClockFamily.CONDENSED to R.string.font_condensed,
    ClockFamily.MONO to R.string.font_mono,
)

private val weights = listOf(
    ClockWeight.LIGHT to R.string.weight_light,
    ClockWeight.REGULAR to R.string.weight_regular,
    ClockWeight.BOLD to R.string.weight_bold,
)

/**
 * "Launcher settings": opaque screen in theme colors, large collapsing bar with Back.
 * Groups: Home screen (Home role, tonal button if not held), Profiles (toggle; "Change profiles" and "Automatic
 * switching" hidden without profiles), Appearance (subpages [ClockStyleScreen] and [TextOnWallpaperScreen]) and
 * About. Every choice is saved at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    font: ClockFont,
    profiles: Profiles,
    onChangeProfiles: () -> Unit,
    onProfilesEnabled: (Boolean) -> Unit,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    onClockStyle: () -> Unit,
    textOnWallpaper: TextOnWallpaper,
    onTextOnWallpaper: () -> Unit,
    onBack: () -> Unit,
    /** "Automatic switching" summary; the row follows "Change profiles". */
    autoSummary: String,
    onAutoSwitching: () -> Unit,
    /** Weather city, or "Off". */
    weatherSummary: String,
    onWeather: () -> Unit,
    /** App version, under "About". */
    version: String,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) = OnSurface {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.launcher_settings)) },
            navigationIcon = { BackButton(onBack) },
            scrollBehavior = scroll,
        )
        Body {
            // Group shown only if the launcher is not the default Home app: nothing to do otherwise.
            if (!roleHeld) Group(stringResource(R.string.home_screen)) {
                SettingRow(R.drawable.ic_home, stringResource(R.string.default_home_app), stringResource(R.string.default_home_not_set)) {
                    FilledTonalButton(onClick = onRequestRole) { Text(stringResource(R.string.set_default_short), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            Group(stringResource(R.string.profiles)) {
                val on = profiles.enabled
                SettingRow(
                    R.drawable.ic_switch_account, stringResource(R.string.profiles),
                    if (on) profiles.selected.map { profileLabel(it) }.joinToString(" · ") else stringResource(R.string.profiles_off),
                    modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onProfilesEnabled),
                ) { Switch(checked = on, onCheckedChange = null) }
                if (on) {
                    SettingRow(
                        R.drawable.ic_tune, stringResource(R.string.change_profiles), stringResource(R.string.change_profiles_hint),
                        modifier = Modifier.clickable(role = Role.Button, onClick = onChangeProfiles),
                    ) { Chevron() }
                    SettingRow(
                        R.drawable.ic_wifi, stringResource(R.string.auto_switching), autoSummary,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onAutoSwitching),
                    ) { Chevron() }
                }
            }
            Group(stringResource(R.string.weather)) {
                SettingRow(
                    R.drawable.ic_partly_cloudy_day, stringResource(R.string.weather_city), weatherSummary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onWeather),
                ) { Chevron() }
            }
            Group(stringResource(R.string.appearance)) {
                SettingRow(
                    R.drawable.ic_schedule, stringResource(R.string.clock_style), fontSummary(font),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onClockStyle),
                ) { Chevron() }
                SettingRow(
                    R.drawable.ic_wallpaper, stringResource(R.string.text_on_wallpaper), stringResource(textChoices.getValue(textOnWallpaper)),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onTextOnWallpaper),
                ) { Chevron() }
            }
            // No group title: the row already says "About".
            Group(null) {
                SettingRow(
                    R.drawable.ic_info, stringResource(R.string.about_app, stringResource(R.string.app_name)), stringResource(R.string.about_summary, version),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onAbout),
                ) { Chevron() }
            }
        }
    }
}

/**
 * Clock style: preview over the wallpaper (frame left transparent, the window shows the wallpaper), font as a radio
 * list, each name in its family, weight as a segmented button, each label in the chosen font at its weight, grayed
 * out with an explanation line if the font lacks it. [onPreview] receives the preview frame in the window,
 * predictive back shrink included, then `null` when the page goes away.
 */
@Composable
fun ClockStyleScreen(
    font: ClockFont,
    time: String,
    date: String,
    onFontChange: (ClockFont) -> Unit,
    onBack: () -> Unit,
    onPreview: (RoundRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreviewPage(stringResource(R.string.clock_style), font, time, date, onBack, onPreview, modifier) {
        OnSurface {
            Column {
                Label(stringResource(R.string.clock_font), top = Tokens.Space.xxl)
                Card {
                    families.forEach { (family, label) ->
                        RadioRow(font.family == family, onSelect = { onFontChange(font.copy(family = family)) }) {
                            BasicText(stringResource(label), style = Tokens.label.merge(TextStyle(fontFamily = ClockFont(family = family).fontFamily())))
                        }
                    }
                }
                Label(stringResource(R.string.clock_weight), top = Tokens.Space.xxl)
                // Chosen weight kept from one font to another; the drawn one is selected.
                val drawn = font.drawnWeight
                val missing = weights.filter { !font.family.hasWeight(it.first) }.map { stringResource(it.second) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = if (missing.isEmpty()) Tokens.Space.l else Tokens.Space.s)) {
                    weights.forEachIndexed { index, (weight, label) ->
                        SegmentedButton(
                            selected = drawn == weight,
                            onClick = { onFontChange(font.copy(weight = weight)) },
                            // Weight the system font lacks: grayed out rather than synthesized; the outline stays
                            // whole so the group still reads as one segmented button.
                            enabled = font.family.hasWeight(weight),
                            colors = SegmentedButtonDefaults.colors(disabledInactiveBorderColor = MaterialTheme.colorScheme.outline),
                            shape = SegmentedButtonDefaults.itemShape(index, weights.size),
                        ) {
                            // Each weight written in the chosen font, at that weight, like the font list.
                            val sample = font.copy(weight = weight)
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = sample.fontFamily(), fontWeight = sample.fontWeight()))
                        }
                    }
                }
                // Why weights are grayed out: the system font lacks them.
                if (missing.isNotEmpty()) {
                    val hint = if (missing.size == 1) stringResource(R.string.weight_missing_one, missing[0])
                    else stringResource(R.string.weight_missing_two, missing[0], missing[1])
                    BasicText(hint, style = Tokens.supporting, modifier = Modifier.padding(horizontal = Tokens.Space.l).padding(bottom = Tokens.Space.l))
                }
            }
        }
    }
}

/**
 * "Text on wallpaper": same preview as "Clock style", texts in the chosen colors; three radio choices, Automatic
 * with its supporting line. The choice applies at once, to the preview and to Home.
 */
@Composable
fun TextOnWallpaperScreen(
    choice: TextOnWallpaper,
    font: ClockFont,
    time: String,
    date: String,
    onChoice: (TextOnWallpaper) -> Unit,
    onBack: () -> Unit,
    onPreview: (RoundRect?) -> Unit,
    modifier: Modifier = Modifier,
) = PreviewPage(stringResource(R.string.text_on_wallpaper), font, time, date, onBack, onPreview, modifier) {
    OnSurface {
        Column {
            Label(stringResource(R.string.text_color), top = Tokens.Space.xxl)
            Card {
                textChoices.forEach { (value, label) ->
                    val automatic = value == TextOnWallpaper.AUTOMATIC
                    RadioRow(
                        choice == value, onSelect = { onChoice(value) },
                        minHeight = if (automatic) Tokens.settingRowHeight else Tokens.rowMinHeight, vertical = Tokens.Space.m,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs)) {
                            BasicText(stringResource(label), style = Tokens.sheetBody)
                            if (automatic) BasicText(stringResource(R.string.text_automatic_hint), style = Tokens.supporting)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Tokens.Space.l))
        }
    }
}

private val textChoices = linkedMapOf(
    TextOnWallpaper.AUTOMATIC to R.string.text_automatic,
    TextOnWallpaper.LIGHT to R.string.text_light,
    TextOnWallpaper.DARK to R.string.text_dark,
)

/**
 * Settings page with a preview: bar, then the clock preview over the wallpaper (frame left transparent, the window
 * shows the wallpaper), texts in wallpaper-based colors as on Home; [content] below.
 * [onPreview] receives the preview frame in the window, predictive back shrink included, then `null` when the page
 * goes away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewPage(
    title: String,
    font: ClockFont,
    time: String,
    date: String,
    onBack: () -> Unit,
    onPreview: (RoundRect?) -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    // Surface painted everywhere except under the preview, tracked while scrolling, in page coordinates: the
    // predictive back shrink, applied above, does not offset them.
    var hole by remember { mutableStateOf(Rect.Zero) }
    var area by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val radius = with(LocalDensity.current) { Tokens.clockPreviewRadius.toPx() }
    DisposableEffect(Unit) { onDispose { onPreview(null) } }
    Column(modifier.fillMaxSize()) {
        OnSurface {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surface),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .onGloballyPositioned { area = it }
                .drawBehind {
                    val preview = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(radius))) }
                    clipPath(preview, ClipOp.Difference) { drawRect(surface) }
                }
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = Tokens.Space.l),
        ) {
            // Preview: the only transparent area of the screen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Tokens.clockPreviewHeight)
                    .onGloballyPositioned { preview ->
                        area?.let { hole = it.localBoundingBoxOf(preview, clipBounds = false) }
                        val bounds = preview.boundsInWindow(clipBounds = false)
                        val r = radius * bounds.width / preview.size.width.coerceAtLeast(1)
                        onPreview(RoundRect(bounds, CornerRadius(r)))
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(Modifier.padding(horizontal = Tokens.Space.xxl).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(Tokens.Space.xs)) {
                    BasicText(time, style = Tokens.favorite.copy(fontSize = Tokens.clockSize, lineHeight = Tokens.clockLineHeight, fontFamily = font.fontFamily(), fontWeight = font.fontWeight()))
                    BasicText(date, style = Tokens.homeDetail)
                }
            }
            content()
        }
    }
}

@Composable
private fun fontSummary(font: ClockFont): String =
    stringResource(families.first { it.first == font.family }.second) + " · " + stringResource(weights.first { it.first == font.drawnWeight }.second)

@Composable
internal fun BackButton(onBack: () -> Unit) {
    val label = stringResource(R.string.back)
    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = label }) {
        Glyph(R.drawable.ic_arrow_back, Tokens.glyphSize, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = Tokens.Space.l),
        content = content,
    )
}

/** Settings group: accent title, then a card of rows separated by 2 dp (`Card`). */
@Composable
internal fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Label(title, top = Tokens.Space.l) else Spacer(Modifier.height(Tokens.Space.l))
    Card(content)
}

@Composable
internal fun Label(title: String, top: androidx.compose.ui.unit.Dp) {
    BasicText(title, style = Tokens.settingsSection, modifier = Modifier.padding(start = Tokens.Space.l, top = top, bottom = Tokens.Space.m).semantics { heading() })
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Tokens.cardShape), verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs), content = content)
}

/** Settings row (72 dp): icon, title and optional second line, trailing element; [modifier] carries the row's action. */
@Composable
internal fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = Tokens.settingRowHeight,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(modifier)
            .heightIn(min = minHeight)
            .padding(horizontal = Tokens.Space.l, vertical = Tokens.Space.m),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, Tokens.glyphSize, MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.xxs)) {
            BasicText(title, style = Tokens.sheetBody)
            supporting?.let { BasicText(it, style = Tokens.supporting) }
        }
        trailing()
    }
}

@Composable
internal fun Chevron() = Glyph(R.drawable.ic_chevron_right, Tokens.glyphSize, MaterialTheme.colorScheme.onSurfaceVariant)

/** Radio row in a settings card: the whole row selects. */
@Composable
private fun RadioRow(
    selected: Boolean,
    onSelect: () -> Unit,
    minHeight: Dp = Tokens.rowMinHeight,
    vertical: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = minHeight)
            .padding(horizontal = Tokens.Space.l, vertical = vertical),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        content()
    }
}
