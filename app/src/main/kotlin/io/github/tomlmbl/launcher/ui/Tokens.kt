package io.github.tomlmbl.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.tomlmbl.launcher.catalog.RailMagnifier

/** True for dark text over the wallpaper; provided by the activity, from [io.github.tomlmbl.launcher.home.WallpaperTone]. */
val LocalDarkText = staticCompositionLocalOf { false }

/** True on a theme surface (sheet, menu, bubble); set by [OnSurface]. */
val LocalOnSurface = staticCompositionLocalOf { false }

/**
 * Curve of `ScalingWorkspaceRevealAnim.SCALE_INTERPOLATOR` (Launcher3 `android-16.0.0_r1`): a path of two cubics,
 * (0,0) → (0.15,0.3952) through (0.045,0.0356) and (0.0975,0.2055), then → (1,1) through (0.235,0.6855) and (0.235,1).
 * Each segment is mapped to a `CubicBezierEasing` over its own frame (no Android API, for JVM tests).
 */
internal val pixelRevealEasing: Easing = run {
    val first = CubicBezierEasing(0.3f, 0.090081f, 0.65f, 0.519990f)
    val second = CubicBezierEasing(0.1f, 0.479993f, 0.1f, 1f)
    Easing { t -> if (t < 0.15f) 0.3952f * first.transform(t / 0.15f) else 0.3952f + 0.6048f * second.transform((t - 0.15f) / 0.85f) }
}

/**
 * Gesture return: average of [pixelRevealEasing] and M3's `EmphasizedDecelerate` (0.05, 0.7, 0.1, 1).
 * A start brisk enough to carry the gesture's momentum without a jump, an end as long as both (27 %, 42 % and 83 % of
 * the path at 5 %, 10 % and 30 % of the duration).
 */
internal val gestureRevealEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f).let { decelerate ->
    Easing { t -> (pixelRevealEasing.transform(t) + decelerate.transform(t)) / 2f }
}

/**
 * Home content reveal: linear progress in time over [durationMs]; scale from [startScale] along [easing], blur from
 * [blur] along [blurEasing] over the first [blurMs] ms; linear opacity over the first [alphaMs] ms.
 */
class HomeReveal(
    val durationMs: Int,
    val startScale: Float,
    val alphaMs: Int,
    val easing: Easing,
    val blur: Dp,
    /** Blur clearing duration, at most [durationMs], and its curve. */
    val blurMs: Int = durationMs,
    val blurEasing: Easing = easing,
)

/**
 * Visual values, taken from the variables and text styles of the design file (names in backticks are its variable
 * and style names). Surface colors come from the dynamic scheme ([LauncherTheme]).
 * Spacings without a design variable are named in code ([Space]).
 */
object Tokens {
    private val light = Color.White
    private val dark = Color(0xDE000000)
    private val shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 4f)
    private val darkText: Boolean @Composable @ReadOnlyComposable get() = LocalDarkText.current
    private val onSurface: Boolean @Composable @ReadOnlyComposable get() = LocalOnSurface.current
    private val colors @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    /**
     * Foreground text: over the wallpaper, light or dark according to its tone (`Wallpaper` collection); on a theme
     * surface, the `onSurface` role.
     */
    val onWallpaper: Color @Composable @ReadOnlyComposable get() = when {
        onSurface -> colors.onSurface
        darkText -> dark
        else -> light
    }
    val onWallpaperMuted: Color @Composable @ReadOnlyComposable get() = when {
        onSurface -> colors.onSurfaceVariant
        else -> onWallpaper.copy(alpha = if (darkText) 0.6f else 0.7f)
    }
    /** Shadow of text over a dark background; none on a theme surface. */
    private val textShadow: Shadow? @Composable @ReadOnlyComposable get() = if (darkText || onSurface) null else shadow
    val fieldBackground: Color @Composable @ReadOnlyComposable get() = when {
        onSurface -> colors.surfaceContainerHigh
        else -> onWallpaper.copy(alpha = if (darkText) 0.08f else 0.14f)
    }
    /** Text of a filled neutral button over the wallpaper (`color/onWallpaperInverse`); on a surface, `surface`. */
    val onWallpaperInverse: Color @Composable @ReadOnlyComposable get() = when {
        onSurface -> colors.surface
        darkText -> light
        else -> dark
    }
    val highlight: Color @Composable @ReadOnlyComposable get() = onWallpaper.copy(alpha = if (darkText || onSurface) 0.08f else 0.12f)
    /**
     * Uniform veil behind app lists and onboarding, system bars included (`color/scrim`): black at 24 % under light
     * text, white at 56 % under dark text. Drawn in Compose, catalog included: the system layer under the blur keeps
     * its minimum ([sheetDimBehind]).
     */
    val scrim: Color @Composable @ReadOnlyComposable get() = if (darkText) Color.White.copy(alpha = 0.56f) else Color.Black.copy(alpha = scrimAlpha)
    private const val scrimAlpha = 0.24f
    /** Home's full-screen flat veil (`color/homeOverlay`): black at 24 % under light text, white at 24 % under dark text. */
    val homeOverlay: Color @Composable @ReadOnlyComposable get() = (if (darkText) Color.White else Color.Black).copy(alpha = 0.24f)
    /**
     * Gradient under the dock (`color/dockGradient`), added to [homeOverlay], only under light text and with a dock:
     * black, transparent at the top, 40 % at 45 % of its height, then constant.
     */
    val dockGradient = Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.4f), 1f to Color.Black.copy(alpha = 0.4f))
    val dockGradientHeight = 200.dp
    /** Minimal dim of the blurred layer under a sheet: SurfaceFlinger ignores this layer without a dim (probed value). */
    const val sheetDimBehind = 0.01f
    /** Dot of an app with no clear color. */
    val dotNeutral = Color(0xFF9E9E9E)

    // Theme surfaces.
    val sheetBackground: Color @Composable @ReadOnlyComposable get() = colors.surfaceContainerLow
    /** Sheet over the blur (`color/surfaceContainerLowBlurred`); [sheetBackground] is opaque when blur is off. */
    val sheetBlurredBackground: Color @Composable @ReadOnlyComposable get() = colors.surfaceContainerLow.copy(alpha = 0.72f)
    val menuBackground: Color @Composable @ReadOnlyComposable get() = colors.surfaceContainer
    val bubbleBackground: Color @Composable @ReadOnlyComposable get() = colors.surfaceContainerHigh
    /** Full-screen veil behind a sheet: light, Home stays visible; black 24 % under light text, white 16 % under dark text. */
    val sheetScrim: Color @Composable @ReadOnlyComposable get() = if (darkText) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.24f)
    /**
     * Veil in a sheet's shape, under its translucent surface (`color/sheetVeil`): follows the theme, black at 40 % in
     * dark, white at 40 % in light (design value).
     */
    val sheetVeil: Color @Composable @ReadOnlyComposable get() = (if (isSystemInDarkTheme()) Color.Black else Color.White).copy(alpha = 0.4f)
    val onSheet: Color @Composable @ReadOnlyComposable get() = colors.onSurface
    val onSheetMuted: Color @Composable @ReadOnlyComposable get() = colors.onSurfaceVariant
    /** App menu: row surface, "+" button background, outline of the other actions (sheet text at 6, 14 and 25 %). */
    val menuCardRow: Color @Composable @ReadOnlyComposable get() = colors.onSurface.copy(alpha = 0.06f)
    val menuActionFill: Color @Composable @ReadOnlyComposable get() = colors.onSurface.copy(alpha = 0.14f)
    val menuActionOutline: Color @Composable @ReadOnlyComposable get() = colors.onSurface.copy(alpha = 0.25f)

    // Typography: sizes from the `type/*` variables, design text styles (weight, line height).
    private val bodyLineHeight = 1.3.em
    private val titleLineHeight = 1.2.em
    /** Letter spacing of section titles (`Catalog/Section`, `Sheet/Section`: 0.1 %). */
    private val sectionTracking = 0.001.em
    private fun style(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = bodyLineHeight, shadow: Shadow? = null) =
        TextStyle(color = color, fontSize = size, fontWeight = weight, lineHeight = lineHeight, shadow = shadow)

    /** Text scale: clock and app names, other sizes reduced in proportion. */
    val clockSize = 56.sp
    /** `Home/Clock`: line height 100 %. */
    val clockLineHeight = 1.em
    private val homeDetailSize = 14.sp
    /** Like `Catalog/Label`. */
    private val favoriteSize = 16.sp
    private val screenTitleSize = 22.sp
    private val onboardingTitleSize = 28.sp

    /** `Catalog/Label`: an app's name, labels over the wallpaper. */
    val label: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, 16.sp, shadow = textShadow)
    /** `Catalog/Section`: titles, onboarding step and trailing row notes, in sentence case. */
    val section: TextStyle @Composable @ReadOnlyComposable get() =
        style(onWallpaperMuted, 14.sp, FontWeight.Medium).copy(letterSpacing = sectionTracking)
    /** Hints and counters over the wallpaper (`Home/Detail`, `Sheet/Body`), dimmed. */
    val hint: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaperMuted, homeDetailSize)
    /** Settings group title (`Catalog/Section` in the accent color). */
    val settingsSection: TextStyle @Composable @ReadOnlyComposable get() =
        style(colors.primary, 14.sp, FontWeight.Medium).copy(letterSpacing = sectionTracking)
    /** `List/Supporting`: a setting's second line. */
    val supporting: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaperMuted, 14.sp, lineHeight = 1.25.em)
    /** Open-Meteo attribution (weather sheet, weather settings): small, centered, dimmer than `List/Supporting`. */
    val attribution: TextStyle @Composable @ReadOnlyComposable get() =
        style(onWallpaperMuted.copy(alpha = onWallpaperMuted.alpha * attributionAlpha), 12.sp, lineHeight = 1.33.em).copy(textAlign = TextAlign.Center)
    val railLetter: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, 10.sp, FontWeight.Medium)
    /** `Screen/Title`. Settings bar titles (`AppBar/Large` 28, `AppBar/Small` 22): M3 typography. */
    val screenTitle: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, screenTitleSize, lineHeight = titleLineHeight, shadow = textShadow)
    val onboardingTitle: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, onboardingTitleSize, lineHeight = titleLineHeight, shadow = textShadow)
    /** `Catalog/Bubble`, on the rail bubble. */
    val bubble: TextStyle @Composable @ReadOnlyComposable get() = style(colors.onSurface, 28.sp, FontWeight.Medium)
    /** "↵ Open" cue and "Clear" button of the catalog (`Sheet/Body` in wallpaper colors). */
    val enterHint: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, homeDetailSize)
    /** Digit of an order mark (`Sheet/Body` on `onWallpaper`). */
    val orderMark: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaperInverse, homeDetailSize)
    /** `Menu/Label` on a filled neutral button. */
    val buttonLabel: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaperInverse, 16.sp)
    /** `Menu/Label` over the wallpaper, without shadow: tonal and text buttons. */
    val buttonText: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, 16.sp)
    /** "Undo" bar (`Undo bar`): text on `inverseSurface`. */
    val undoText: TextStyle @Composable @ReadOnlyComposable get() = style(colors.inverseOnSurface, homeDetailSize)
    /** `Menu/Label`: sheet rows. */
    val menuLabel: TextStyle @Composable @ReadOnlyComposable get() = style(colors.onSurface, 16.sp)
    /** `Sheet/Body`: Home menu rows, a sheet's body text. */
    val sheetBody: TextStyle @Composable @ReadOnlyComposable get() = style(colors.onSurface, homeDetailSize)
    val sheetTitle: TextStyle @Composable @ReadOnlyComposable get() = style(colors.onSurface, 18.sp, FontWeight.Medium)
    /** `Sheet/Section`: 14 sp like `Catalog/Section`. */
    val sheetSection: TextStyle @Composable @ReadOnlyComposable get() =
        style(colors.onSurfaceVariant, 14.sp, FontWeight.Medium).copy(letterSpacing = sectionTracking)
    /** `Home/Favorite` and `Home/Detail`, over the wallpaper. Clock: size only, font and weight set by the user. */
    val favorite: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, favoriteSize, shadow = textShadow)
    val homeDetail: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, homeDetailSize, shadow = textShadow)

    // Sizes (`size/*`).
    val dotSize = 10.dp
    /**
     * Magnifier of the "Search apps" row: Material Symbols at 20 dp, its circle (center at 9.5/24, diameter 13/24, i.e.
     * about 10 dp like a dot) brought onto the box center, hence onto the dots' axis.
     */
    val favoriteIconSize = 20.dp
    val favoriteIconLensShift = favoriteIconSize * (0.5f - 9.5f / 24f)
    val rowMinHeight = 56.dp
    val railWidth = 28.dp
    /** Rail width while dragging (`Rail`, Scrubbing state): room for the bubble. */
    val railScrubWidth = 72.dp
    /** Catalog search field and section title. */
    val fieldHeight = 56.dp
    val sectionHeaderHeight = 40.dp
    /** Material Symbols icons: search field, "↵ Open" cue. */
    val glyphSize = 24.dp
    val enterGlyphSize = 18.dp
    /** Glyph of an app from another profile, after the name, and its gap. */
    val profileGlyphSize = 16.dp
    val profileGlyphGap = 6.dp
    /** Weather glyph in the date line (18 in the design). */
    val homeDetailGlyph = 18.dp
    /** High and low arrows: in `sp`, they follow the text size (14 sp). */
    val rangeGlyph = 16.sp
    /** Shadow of weather pictures in the light theme, design values: black 24 %, radius 4 and offset 1 (32 dp), 8 and 2 (64 dp). */
    val weatherShadow = Color.Black.copy(alpha = 0.24f)
    val weatherShadowBlur = 4.dp
    val weatherShadowOffset = 1.dp
    val weatherShadowBlurLarge = 8.dp
    val weatherShadowOffsetLarge = 2.dp
    /**
     * Height of the date and event lines (`Date line`, `Event line`: 22 in the design); without it, a line takes its
     * text's trimmed height (about 19) and the 4 dp gap between them seems missing.
     */
    val homeDetailLine = 22.dp
    /** "Clear" button of the no-result state. */
    val chipHeight = 40.dp
    val chipMinWidth = 96.dp
    val bubbleSize = 64.dp
    val touchTarget = 48.dp
    /** Top bar of a screen over the wallpaper, like the small settings bar (`size/appBarSmall`). */
    val topBarHeight = 64.dp
    /** Buttons over the wallpaper: filled or text, tonal "Add app"; minimum width of "Next" (`Step footer`). */
    val buttonHeight = 48.dp
    val tonalButtonHeight = 52.dp
    val primaryMinWidth = 112.dp
    /** Text button ("Skip") and the "Undo" bar action. */
    val buttonMinWidth = 72.dp
    val undoActionWidth = 72.dp
    /** Onboarding: gap between the header and the profile list or the dock preview; 83 and 75 dp in the design. */
    val stepContentTop = 80.dp
    /** Order mark of the onboarding pickers: numbered disc, ring if the app is not selected. */
    val orderMarkSize = 28.dp
    /**
     * Miniature Home of the Home role step: a reduction of the real Home, without details or profile indicator; clock,
     * five favorites and the search row as a grouped list anchored at the bottom, three dock discs.
     * Measured on the design's mini Home illustration.
     */
    const val miniHomeRows = 5
    val miniHomeWidth = 200.dp
    val miniHomeHeight = 360.dp
    val miniHomeShape = RoundedCornerShape(28.dp)
    val miniHomeTop = 60.dp
    val miniHomeBottom = 34.dp
    val miniClockSize = 34.sp
    val miniClockLineHeight = 40.sp
    /** List inset from the frame (the clock keeps `Space.xl`). */
    val miniListInset = 10.dp
    val miniRowHeight = 27.dp
    val miniRowGap = 1.dp
    val miniRowOuterCorner = 12.dp
    val miniRowInnerCorner = 3.dp
    /** Gap between the dot and the row edge, then start of the bar. */
    val miniDotStart = 12.dp
    val miniBarStart = 26.dp
    val miniDot = 6.dp
    val miniSearchRing = 7.dp
    val miniBarHeight = 5.dp
    /** Bar lengths of the five favorites, then of the search row. */
    val miniBarLengths = listOf(56.dp, 44.dp, 36.dp, 64.dp, 48.dp)
    val miniSearchBar = 44.dp
    val miniDockGap = 16.dp
    val miniDockDisc = 24.dp
    val miniDockSpacing = 10.dp
    /** Settings: group row (`Card`), clock preview over the wallpaper ("Clock style"). */
    val settingRowHeight = 72.dp
    /** Single-line settings action row ("Automatic switching"; `Card` row without a second line). */
    val settingActionRowHeight = 56.dp
    val clockPreviewHeight = 200.dp
    val clockPreviewRadius = 28.dp
    /** Footer of the favorites editor and onboarding (`Footer`, `Step footer`): buttons above the gesture area. */
    val footerBottom = 16.dp
    /** Favorite row: 64 dp, also for the editor and the ordered picker. */
    val favoriteRowHeight = 64.dp
    /** Room reserved for the dock and the profile indicator. */
    val dockReserve = 128.dp
    /**
     * Without profiles: margin between the favorites and the gesture area in place of [dockReserve]; the profile pill's
     * one ([indicatorBottom]). Home moves down by the difference.
     */
    val noDockReserve = 24.dp
    /** Vertical distance that triggers the catalog (upward) or the shade (downward). */
    val swipeThreshold = 48.dp
    /** Dock disc at the slot's size. */
    val dockIconSize = 56.dp
    val dockSlotSize = 56.dp
    val indicatorHeight = 32.dp
    /** Home without favorites: minimum height of the prompt. */
    val placeholderMinHeight = 120.dp
    /** Sheet handle (`Sheet handle`). */
    val handleWidth = 32.dp
    val handleHeight = 4.dp
    /** Height of the handle area. */
    val handleArea = 24.dp
    /** Radio button of `Sheet row`: diameter, mark. */
    val radioSize = 20.dp
    val radioMark = 10.dp
    /** A profile's dock preview in the profile sheet: icons and gap. */
    val dockPreviewIcon = 28.dp
    val dockPreviewSpacing = 4.dp
    /** Full-dock choice: gap between slots. */
    val dockFullSpacing = 32.dp
    /** Slot picker: sheet height relative to the room above (578 and 337 dp in the design). */
    const val dockSlotSheetWeight = 1.7f
    /** Favorite picker sheet: same ratio (485 and 390 dp in the design, below the status bar). */
    const val pickSheetWeight = 1.24f

    // Spacings (`space/*`).
    /** Side margin of Home and the catalog (`space/screenMargin`). */
    val screenMargin = 32.dp
    /** Inset of a catalog row's highlighted background from the edges. */
    val rowInset = 16.dp
    /** Ripple inset of a floating menu item. */
    val menuItemInset = Space.m
    /** Side overflow of the clock's ripple beyond the text. */
    val clockPressOutset = Space.m
    /** Press shrink: scale of the touched content, tuned by hand on a device. */
    const val pressScale = 0.95f
    /**
     * Touch feedback of favorites and dock: the favorite row becomes a pill (veil doubled, size unchanged), the dock
     * disc a rounded square of [contactDockRadius]; the design's spring.
     */
    const val contactStiffness = 800f
    const val contactDamping = 0.6f
    val contactDockRadius = 18.dp
    /** Catalog rail: right margin, margin above the field, letters' inset from its right edge. */
    val railEnd = 16.dp
    /** Top padding of the catalog list, as a share of the height left to the list (a quarter). */
    const val catalogTopReach = 1f / 4
    val railBottom = 16.dp
    val railLetterEnd = 8.dp
    /**
     * Edge-to-edge catalog edges ("strong bottom", design values).
     * Bottom: the zone starts [listEdgeAboveField] above the field; opacity 1, then [listEdgeAlpha] at the top of the
     * field, [listEdgeFieldText] below it (its text) and at the bottom of the list; blur up to [listEdgeBlur] at the
     * bottom of the list.
     */
    val listEdgeAboveField = 24.dp
    val listEdgeFieldText = 16.dp
    val listEdgeBlur = 24.dp
    val listEdgeAlpha = floatArrayOf(1f, 0.5f, 0.35f, 0.15f)
    /** Top: status bar and [listTopEdge] beyond; opacity 0, 0.4 at the bottom of the bar, then 1; blur from [listTopEdgeBlur] to 0. */
    val listTopEdge = 24.dp
    val listTopEdgeBlur = 16.dp
    val listTopEdgeAlpha = floatArrayOf(0f, 0.4f, 1f, 1f)
    /** "↵ Open" cue: left inset. */
    val enterHintStart = 10.dp
    /** Between dot and name (`space/dotLabel`). */
    val dotLabel = 20.dp
    /** Home stacked from the bottom: between the clock block and favorites, then between favorites and the dock room. */
    val clockToFavorites = 40.dp
    /**
     * Favorites as a grouped list: one surface per row, no outline, large radii at the start and end of the group
     * (Material 3 Expressive grouped lists), 2 dp gaps between rows. Group inset: the dot keeps the 32 dp axis.
     */
    val favoritesGroupInset = 16.dp
    val favoritesGroupGap = 2.dp
    private val favoriteOuterRadius = 24.dp
    private val favoriteInnerRadius = 6.dp
    /** Corners of [favoriteShape] (top-start, top-end, bottom-end, bottom-start), for the touch feedback. */
    fun favoriteRadii(first: Boolean, last: Boolean): List<Dp> {
        val top = if (first) favoriteOuterRadius else favoriteInnerRadius
        val bottom = if (last) favoriteOuterRadius else favoriteInnerRadius
        return listOf(top, top, bottom, bottom)
    }
    fun favoriteShape(first: Boolean, last: Boolean) = favoriteRadii(first, last).let { (topStart, topEnd, bottomEnd, bottomStart) ->
        RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)
    }
    val favoritesToDock = 44.dp
    /** Between the dock slots and the profile pill (`space/dockToIndicator`). */
    val dockToIndicator = 16.dp
    /** Between the profile pill and the gesture area (`space/indicatorBottom`). */
    val indicatorBottom = 24.dp
    val dockSpacing = 24.dp
    /** Horizontal margin of sheets and app menus (`space/screenMargin`). */
    val sheetMargin = screenMargin
    /** Sheet (`Sheet`): top margin before the handle, bottom margin above the system bars. */
    val sheetTop = 8.dp
    val sheetBottom = 16.dp
    /** Home menu (`Menu`): width, row inset. */
    val menuWidth = 240.dp

    /** Spacings without a design variable, named in code. */
    object Space {
        val xxs = 2.dp
        val xs = 4.dp
        val s = 6.dp
        val m = 8.dp
        val ml = 12.dp
        val l = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
    }

    // Outlines.
    val hairline = 1.dp
    /** Outline of an empty dock slot. */
    val emptySlotBorder = 1.5.dp
    /** Targeted dock slot, ringed with the accent. */
    val targetBorder = 2.dp
    /** Outline opacity of the empty-Home prompt. */
    const val placeholderBorderAlpha = 0.5f

    /** `Elevation/Menu`: drop shadow of 8, offset by 2, black at 25 %. */
    val menuElevation = 3.dp

    // Radii (`radius/*`).
    /** `radius/sheet`. */
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val menuShape = RoundedCornerShape(16.dp)
    val highlightShape = RoundedCornerShape(16.dp)
    /** `radius/undoBar`. */
    val undoBarShape = RoundedCornerShape(16.dp)
    val cardShape = RoundedCornerShape(16.dp)
    /** App menu: 20 dp cards, 16 dp inset, rows 10 dp from the edge, 40 dp action. */
    val menuCardShape = RoundedCornerShape(20.dp)
    val menuCardInset = 16.dp
    val menuRowPadding = 10.dp
    val menuActionSize = 40.dp
    val handleShape = RoundedCornerShape(50)
    val pillShape = RoundedCornerShape(50)

    // Opacities (`alpha/*`).
    const val missingAlpha = 0.45f
    const val disabledAlpha = 0.4f
    const val attributionAlpha = 0.7f
    /** Opacity of the rail letters when not dragging; the current section's letter stays opaque. */
    const val railIdleAlpha = 0.5f

    /** Share of the window height taken by the rail at rest, anchored at the bottom: the thumb zone. */
    const val railHeightFraction = 0.4f
    /** Rail magnifier: size and opacity by distance to the finger, in letters. */
    val railMagnifier = RailMagnifier(
        scales = floatArrayOf(3f, 2.2f, 1.6f, 1.2f),
        alphas = floatArrayOf(1f, 1f, 1f, 1f, 0.6f, 0.35f),
        farAlpha = 0.15f,
    )

    /** Window blur radius (`blur/window`). */
    val windowBlur = 40.dp
    /** Share of the Home ↔ catalog progress at which the blur is complete (as in Launcher3). */
    const val catalogBlurEnd = 1f
    /** Curve of the catalog blur radius: slow start, since sharpness changes mostly at small radii (on trial). */
    val catalogBlurEasing = Easing { t -> t * t * (3f - 2f * t) }

    // Durations (`motion/*`).
    const val railMagnifierFadeMs = 150
    /**
     * Rail jump (values on trial): the list jumps to [railJumpRun] short of the section, then scrolls to it in
     * [railJumpMs] (M3 `DurationShort4`), standard decelerate curve ([fadeInEasing]).
     */
    val railJumpRun = 48.dp
    const val railJumpMs = 200
    /**
     * Home fade under the slot picker and appearance of blur and veil (catalog, sheets): 200 ms going, 250 ms coming
     * back (`motion/homeFadeOutMs`, `motion/homeFadeInMs`; M3 `DurationShort4`, `DurationMedium1`).
     */
    const val homeFadeOutMs = 200
    const val homeFadeInMs = 250
    /** M3 standard curves: accelerate on disappearance, decelerate on appearance. */
    val fadeOutEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val fadeInEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
    /** Gesture return: delay after which the surface is removed if the system has not signaled the end. */
    const val gestureReturnTimeoutMs = 2000L
    /** Duration of the "Undo" bar (`motion/undoMs`). */
    const val undoMs = 10_000L

    /**
     * Favorites editor row coming back ("Undo", addition): it only appears once its neighbors are almost in place,
     * otherwise it overlaps the one still sliding. Values on trial.
     */
    const val favoriteRowInDelayMs = 200
    const val favoriteRowInMs = 150

    /**
     * Profile change, manual or signal-driven: each dock slot goes from the old app to the new one with a fade and a
     * slight slide to the right, from left to right, staggered from one slot to the next. Values on trial (150 and
     * 100 ms felt too fast, then 300 and 150 ms too staggered and a bit brisk).
     */
    const val profileSwitchSlotMs = 400
    const val profileSwitchStaggerMs = 100
    val profileSwitchSlide = 8.dp

    // M3 Expressive springs: damping and stiffness from `ExpressiveMotionTokens`, material3 1.4.0.
    // Spatial: what moves; effects: what has no position (opacity).
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effectsDefault() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsFast() = spring<T>(dampingRatio = 1f, stiffness = 3800f)
    /** Menu: start and end scale of the M3 menu (`ClosedScaleTarget`, `Menu.kt`). */
    const val menuClosedScale = 0.8f

    // Full-screen transitions: Material shared axis and fade through, M3 `DurationLong1` duration and
    // `EasingEmphasized` curve; slide, entry scale and fade threshold from MDC Android
    // (`MaterialSharedAxis`, `MaterialFadeThrough`, `FadeThroughProvider`).
    const val screenTransitionMs = 450
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Share of the duration during which the leaving screen fades; the entering one appears afterwards. */
    const val fadeThroughThreshold = 0.35f
    const val fadeThroughScale = 0.92f
    /** Shared axis slide. */
    val sharedAxisSlide = 30.dp
    /**
     * Finger-driven Home ↔ catalog, after Launcher3 on phones (`all_apps_starting_vertical_translate`,
     * `ALL_APPS_STATE_TRANSITION_MANUAL`): distance travelled by the content, and share of it beyond which a release
     * opens the catalog (and below which it closes it). A fling faster than [catalogVelocityThreshold] per second
     * decides by its direction alone (`base_swift_detector_fling_release_velocity`, 1 dp/ms).
     */
    val catalogCourse = 300.dp
    const val catalogCommit = 0.4f
    /** Search only (`CatalogMotion`): Home fades until 75 % of the course, instead of 35 %. */
    const val searchHomeFadeEnd = 0.75f
    /**
     * Search only: Home only rises half the course; the field only appears from 65 %, so that on the way back it fades
     * first, before Home returns (M3 fade through; at 30 % the return felt clumsy).
     */
    const val searchHomeRise = 0.5f
    const val searchFieldFrom = 0.65f
    val catalogVelocityThreshold = 1000.dp

    // Sheets, after the M3 modal sheet: appearance and settling on the spatial spring, disappearance on the fast effects
    // spring; closing beyond half the height or on a fling faster than the M3 velocity threshold.
    val sheetVelocityThreshold = 125.dp
    /** Beyond this share of the window, a sheet rises on the slow spatial spring (threshold on trial). */
    const val tallSheetFraction = 0.5f
    /** Predictive back: maximum shrink of the sheet, from its bottom edge, and curve applied to the progress. */
    val sheetBackScaleX = 48.dp
    val sheetBackScaleY = 24.dp
    val predictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    /**
     * Home content when an app opens and on return by button or app exit, like the Pixel launcher (Launcher3
     * `android-16.0.0_r1`, `QuickstepTransitionManager`): scale 0.97 ↔ 1 in 350 ms, decelerate; opacity 0 ↔ 1 in
     * 217 ms, linear; no blur.
     */
    val homeReveal = HomeReveal(
        durationMs = 350, startScale = 0.97f, alphaMs = 217,
        easing = Easing { 1f - (1f - it) * (1f - it) * (1f - it) }, blur = 0.dp,
    )
    /**
     * Gesture return to Home, after the Pixel launcher (Launcher3 `android-16.0.0_r1`, `ScalingWorkspaceRevealAnim`):
     * scale 0.85 → 1, opacity 0 → 1 in 200 ms linear, veils on the scale curve; tuned by hand: [gestureRevealEasing]
     * in 800 ms (Pixel: its own curve in 1,000 ms). Blur clearing: 10 dp cleared in 500 ms on M3 `Legacy`, which keeps
     * it during the appearance.
     */
    val homeRevealGesture = HomeReveal(
        durationMs = 800, startScale = 0.85f, alphaMs = 200,
        easing = gestureRevealEasing,
        blur = 10.dp,
        blurMs = 500,
        blurEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
    )
    /**
     * Unlock: Home's elements arrive one by one from the bottom, in the spirit of the Pixel launcher's unlock (opacity
     * and scale per element, staggered); starting values, to tune by hand: [gestureRevealEasing] in 800 ms from 0.9,
     * opacity in 200 ms, 25 ms between elements. Starts 50 ms after Home starts, about when SystemUI begins its own
     * fade and rise of Home (30 to 55 ms, measured on one Android 16 device). All elements scale around one point at
     * 90 % of the screen height, about where under-display fingerprint sensors sit (their position is not public).
     */
    val unlockArrival = io.github.tomlmbl.launcher.home.UnlockArrival(
        durationMs = 800, startScale = 0.9f, alphaMs = 200, staggerMs = 25, startDelayMs = 50,
        easing = gestureRevealEasing, pivotY = 0.9f, unlockWaitMs = 500,
    )
    /** Most elements ranked by the unlock arrival: dock, "Search apps", six favorites, event, date line, clock. */
    const val unlockArrivalRanks = 11
    /** Predictive back of a full-screen page: maximum shrink, around the middle of the screen. */
    const val predictiveBackScale = 0.9f

    val homeShadow: Shadow? @Composable @ReadOnlyComposable get() = textShadow

    /** Single accent: the targeted dock slot. */
    val accent: Color @Composable @ReadOnlyComposable get() = colors.primary
    val undoBackground: Color @Composable @ReadOnlyComposable get() = colors.inverseSurface
    /**
     * Profile indicator: thin `outlineVariant` outline without background, name in Home's text color without shadow
     * (`Profile indicator`).
     */
    val indicatorOutline: Color @Composable @ReadOnlyComposable get() = colors.outlineVariant
    val indicatorLabel: TextStyle @Composable @ReadOnlyComposable get() = style(onWallpaper, homeDetailSize)

    /**
     * Weather and calendar panel: neutral event band, next event in the accent under a `primaryVeil` veil (16 %); stale
     * or missing weather dimmed to 45 %; `outlineVariant` divider.
     */
    val eventStripe: Color @Composable @ReadOnlyComposable get() = colors.outline
    val nextEventStripe: Color @Composable @ReadOnlyComposable get() = colors.primary
    val nextEventVeil: Color @Composable @ReadOnlyComposable get() = colors.primary.copy(alpha = 0.16f)
    val panelDivider: Color @Composable @ReadOnlyComposable get() = colors.outlineVariant
    const val staleWeatherAlpha = 0.45f
    /** `size/hourCellWidth`, `size/eventStripeWidth`; 36 dp high band, 40 dp conditions glyph. */
    val hourCellWidth = 52.dp
    val eventStripeWidth = 4.dp
    val eventStripeHeight = 36.dp
    val panelGlyph = 40.dp
    /**
     * Meteocons pictures of the panel, larger than the 40 and 24 dp glyphs to make up for their internal margins;
     * "Now" twice the hours' size to mark the hierarchy.
     */
    val panelPicture = 64.dp
    val hourPicture = 32.dp

    /**
     * Glyph of the themed dock icons, Launcher3 colors: `system_accent1_700` (light theme), `system_accent1_200` (dark
     * theme). Over the wallpaper, chosen by the text color: the dark palette under light text; on a theme surface, by
     * the system theme.
     */
    val themedIcon: Color @Composable @ReadOnlyComposable get() {
        val pale = if (onSurface) isSystemInDarkTheme() else !darkText
        return colorResource(if (pale) android.R.color.system_accent1_200 else android.R.color.system_accent1_700)
    }
    /** Rows of the miniature Home: stronger than `favoriteSurface`, which is invisible at this size. */
    val miniHomeSurface: Color @Composable @ReadOnlyComposable get() = onWallpaper.copy(alpha = if (darkText) 0.05f else 0.07f)
    /** Bars standing for names in the miniature Home. */
    val miniHomeBar: Color @Composable @ReadOnlyComposable get() = onWallpaper.copy(alpha = if (darkText) 0.30f else 0.38f)
    /** Dock disc (`color/dockIconVeil`): neutral veil, white at 16 % under light text, black at 8 % under dark text. */
    val dockIconVeil: Color @Composable @ReadOnlyComposable get() = onWallpaper.copy(alpha = if (darkText) 0.08f else 0.16f)
    /** Thin outline of the dock disc: white at 5.6 % on a dark background, black at 5.6 % on a light one. */
    val dockIconOutline: Color @Composable @ReadOnlyComposable get() = (if (darkText) Color.Black else Color.White).copy(alpha = 0.056f)
    /** Favorite rows as a grouped list: white or black at 4 % depending on the text; on touch, a pill. */
    val favoriteSurface: Color @Composable @ReadOnlyComposable get() = (if (darkText) Color.Black else Color.White).copy(alpha = 0.04f)
}
