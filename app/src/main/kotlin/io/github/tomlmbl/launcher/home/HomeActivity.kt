package io.github.tomlmbl.launcher.home

import android.app.KeyguardManager
import android.app.role.RoleManager
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import io.github.tomlmbl.launcher.ui.HomeReveal
import io.github.tomlmbl.launcher.ui.blockTouches
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import io.github.tomlmbl.launcher.ui.ScreenTransitions
import io.github.tomlmbl.launcher.ui.shownNow
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.tomlmbl.launcher.LauncherApplication
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.catalog.Alphabet
import io.github.tomlmbl.launcher.catalog.AppPicker
import io.github.tomlmbl.launcher.catalog.CatalogScreen
import io.github.tomlmbl.launcher.context.ContextEngine
import io.github.tomlmbl.launcher.context.ContextState
import io.github.tomlmbl.launcher.context.Dock
import io.github.tomlmbl.launcher.context.DockApp
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.context.Suggestions
import io.github.tomlmbl.launcher.onboarding.DockAppsStep
import io.github.tomlmbl.launcher.onboarding.FavoritesStep
import io.github.tomlmbl.launcher.onboarding.ProfileSelectionScreen
import io.github.tomlmbl.launcher.onboarding.RoleStep
import io.github.tomlmbl.launcher.onboarding.Step
import io.github.tomlmbl.launcher.onboarding.onboardingSteps
import io.github.tomlmbl.launcher.settings.ClockFont
import io.github.tomlmbl.launcher.settings.Config
import io.github.tomlmbl.launcher.settings.DockAdd
import io.github.tomlmbl.launcher.settings.DockPlace
import io.github.tomlmbl.launcher.settings.MenuUndo
import io.github.tomlmbl.launcher.settings.SettingsActivity
import io.github.tomlmbl.launcher.ui.CatalogDeck
import io.github.tomlmbl.launcher.ui.CatalogEntry
import io.github.tomlmbl.launcher.ui.CatalogMotion
import io.github.tomlmbl.launcher.ui.LauncherTheme
import io.github.tomlmbl.launcher.ui.Sheet
import io.github.tomlmbl.launcher.ui.SheetHost
import io.github.tomlmbl.launcher.ui.LocalBackdrop
import io.github.tomlmbl.launcher.ui.backdropSource
import io.github.tomlmbl.launcher.ui.rememberBackdrop
import kotlin.math.abs
import io.github.tomlmbl.launcher.ui.SheetIcon
import io.github.tomlmbl.launcher.ui.SheetRow
import io.github.tomlmbl.launcher.ui.SheetSection
import io.github.tomlmbl.launcher.ui.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Screen shown by the Home activity. */
private sealed interface Screen {
    data object Home : Screen
    data object Catalog : Screen
    data object EditFavorites : Screen
    /** Replacing a dock slot. */
    data class DockSlot(val profile: Profile, val index: Int) : Screen
}

/** Displayed page, key of the screen transitions: a screen, or an onboarding step by its rank. */
private sealed interface Page {
    data class Of(val screen: Screen) : Page
    data class Step(val index: Int) : Page
}

/**
 * Transition between pages: shared axis for onboarding steps, crossfade elsewhere.
 * Home and catalog form a single page, driven by [CatalogDeck].
 */
private fun transitionFor(from: Page, to: Page, slide: Int): ContentTransform = when {
    from is Page.Step && to is Page.Step -> ScreenTransitions.sharedAxisX(forward = to.index > from.index, slide)
    else -> ScreenTransitions.fadeThrough()
}

/** Sheet over the current screen. */
private sealed interface Overlay {
    data object Profiles : Overlay
    /** Weather and calendar panel. */
    data object Panel : Overlay
    data class AppMenu(val appId: String, val fromFavorite: Boolean) : Overlay
    data class DockFull(val appId: String, val profile: Profile) : Overlay
    /** Favorites full, opened from an app menu, which comes back afterwards. */
    data class FavoritesFull(val appId: String, val fromFavorite: Boolean) : Overlay
    /** App choice for favorites: appended to the list, or replacing one in the same place. */
    data class Pick(val replacing: Favorite?) : Overlay
}

/**
 * Home activity: Home, catalog, favorites editor, pickers and onboarding; settings have their own activity.
 * Every screen and sheet closes on returning Home, on the Home button and when an app opens; Back goes up one
 * screen, without leaving Home.
 * Onboarding shows until it is finished; Back there returns to the previous step.
 */
class HomeActivity : ComponentActivity() {

    /**
     * Home content: 1 in place, 0 faded out behind an app; advances linearly in time, scale and opacity derive from it
     * ([homeReveal]).
     */
    private val reveal = Animatable(1f)
    // Animation target and request: played in composition, the only provider of a frame clock.
    private var revealTarget by mutableFloatStateOf(1f)
    private var revealRun by mutableIntStateOf(0)
    /** Reveal played: the gesture return one, or the one for the button, app exit and opening. */
    private var revealSpec by mutableStateOf(Tokens.homeReveal)
    /**
     * Reveal center, in window coordinates: the dot or icon targeted by the gesture return, which thus stays still
     * under the contract's surface; otherwise the center of Home.
     */
    private var revealPivot by mutableStateOf<Offset?>(null)
    private val unlockWatch = UnlockWatch()
    /**
     * Unlock arrival: time elapsed in the sequence, in ms; past its end, every element is in place. Played in
     * composition at each [arrivalRun].
     */
    private val arrival = Animatable(ARRIVAL_DONE)
    private var arrivalRun by mutableIntStateOf(0)
    private var screen by mutableStateOf<Screen>(Screen.Home)
    /** Current catalog entry: search only from below, catalog from the side, or the original entry. */
    private var catalogEntry by mutableStateOf(CatalogEntry.Up)
    /** Home ↔ catalog: progress driven by the finger or animated when [screen] changes. */
    private val deck = CatalogDeck()
    private var overlay by mutableStateOf<Overlay?>(null)
    /** Undoable change from an app menu, offered in its row while the menu stays open. */
    private var undo by mutableStateOf<MenuUndo?>(null)
    /** "Undo" meant for the menu about to open (back from the full-favorites choice). */
    private var nextUndo: MenuUndo? = null
    private var query by mutableStateOf("")
    private var roleHeld by mutableStateOf(true)
    private var now by mutableLongStateOf(shownNow())
    /** Finger down on Home: no switch during a gesture. */
    private var pointerDown by mutableStateOf(false)
    /** Home or favorite menu open: Home is not idle. */
    private var homeMenu by mutableStateOf(false)
    /** Incremented by [reset]: closes the Home menu. */
    private var menuReset by mutableIntStateOf(0)
    /** TalkBack explore-by-touch in progress (hover): no switch either. */
    private var hovering by mutableStateOf(false)

    // Onboarding: current step, profiles chosen at the first step (known before the file is written), finish requested.
    private var onboardingIndex by mutableIntStateOf(0)
    private var onboardingProfiles by mutableStateOf<List<Profile>?>(null)
    // Favorites checked at the favorites step, kept when going back a step; null until the step has been opened.
    private var onboardingFavorites by mutableStateOf<List<App>?>(null)
    /** Favorites checked at the favorites step before a recreation (locale, font size), restored once apps are loaded. */
    private var restoredFavorites: List<String>? = null
    private var onboardingDone by mutableStateOf(false)
    private var finishAfterRole = false

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            now = shownNow()
        }
    }

    private val returnTargets = ReturnTargets()
    private val gestureReturn by lazy { GestureReturn(this, returnTargets) }

    private val launcher get() = application as LauncherApplication
    private val store get() = launcher.config
    private val autoSwitch get() = launcher.autoSwitch
    private val weather get() = launcher.weatherFeed
    private val events get() = launcher.eventFeed

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            onboardingIndex = state.getInt(KEY_STEP)
            onboardingProfiles = state.getStringArrayList(KEY_PROFILES)?.mapNotNull { name -> Profile.entries.find { it.name == name } }
            restoredFavorites = state.getStringArrayList(KEY_FAVORITES)
        }
        val repository = launcher.apps

        setContent {
            val apps by repository.apps.collectAsState()
            val loaded by store.config.collectAsState()
            val darkText by launcher.wallpaper.darkText.collectAsState()
            val weatherGlance by weather.glance.collectAsState()
            val nextEvent by events.next.collectAsState()
            val eventAllowed by events.allowed.collectAsState()
            var capacity by remember { mutableIntStateOf(Int.MAX_VALUE) }
            val locale = LocalConfiguration.current.locales[0]
            val alphabet = remember(locale) { Alphabet(locale) }
            val roleRequest = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                refreshRole()
                if (finishAfterRole) finishOnboarding()
            }
            val requestRole = {
                val rm = getSystemService(RoleManager::class.java)
                roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }

            val config = loaded
            val favorites = config?.favorites
            val installed = remember(apps) { apps?.associateBy { it.id } }
            LaunchedEffect(installed) {
                val ids = restoredFavorites ?: return@LaunchedEffect
                if (installed == null) return@LaunchedEffect
                onboardingFavorites = ids.mapNotNull(installed::get)
                restoredFavorites = null
            }
            // Until apps are loaded, no favorite is shown as uninstalled.
            val slots = if (favorites != null && installed != null) favorites.resolve(installed) else null
            val profiles = config?.profiles
            val active = profiles?.let { ContextState(it).active }

            val inFavorites = stringResource(R.string.in_favorites)
            val inDockLabel = stringResource(R.string.in_dock)
            val notInFavorites: (App) -> String? = { app -> inFavorites.takeIf { config?.isFavorite(app.id) == true } }
            val notInDock: (App) -> String? = { app -> inDockLabel.takeIf { config?.inDock(app.id) == true } }
            fun suggested(profile: Profile) = Suggestions.forProfile(profile, apps.orEmpty()) { it.component.packageName }

            // Status bar icons matched to the text, on every screen (the catalog veil is light).
            // Applied again on theme change: the system then resets the bars to their default appearance.
            val onboarding = config != null && !config.onboarded && !onboardingDone && apps != null
            // Automatic switching: idle Home is the only moment a signal-driven switch applies.
            val onboardingNow by rememberUpdatedState(onboarding)
            val appsLoaded by rememberUpdatedState(apps != null)
            LaunchedEffect(Unit) {
                snapshotFlow { !onboardingNow && appsLoaded && screen == Screen.Home && overlay == null && !homeMenu && !pointerDown && !hovering && deck.progress.value == 0f }
                    .collect { autoSwitch.atRest(it) }
            }
            val lightBars = darkText
            val uiMode = LocalConfiguration.current.uiMode
            LaunchedEffect(lightBars, uiMode) {
                val style = if (lightBars) SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                else SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // A removal is no longer undoable once the menu is closed or replaced; only the menu coming back from the
            // full-favorites choice receives the replacement's undo.
            LaunchedEffect(overlay) { undo = nextUndo; nextUndo = null }
            val focusManager = LocalFocusManager.current

            val steps = if (onboarding) onboardingSteps(onboardingProfiles, roleHeld) else emptyList()

            // A launcher is not left with Back: the gesture goes up one screen, otherwise it is ignored.
            BackHandler { back(onboarding) }

            LauncherTheme(darkText) {
            CompositionLocalProvider(LocalReturnTargets provides returnTargets) {
            val current = screen
            val list = apps
            // Uniform veil behind app lists and onboarding, system bars included.
            val veiled = onboarding || current is Screen.Catalog
            // Blurred catalog background: the system's dim layer under Home's window, blurred, hence a blurred
            // wallpaper; veil only when the system turns blur off. SurfaceFlinger ignores this layer without a dim.
            val blurBehind = with(LocalDensity.current) { Tokens.windowBlur.roundToPx() }
            val catalogBlur = current is Screen.Catalog && list != null
            // Sheets: same layer, which blurs the wallpaper; veil and blur of the current screen in Compose
            // (`Backdrop`), above the drawn Home. The layer keeps a minimal dim, otherwise it is ignored.
            val sheetBlur = list != null && (overlay != null || current is Screen.DockSlot)
            // Gradual appearance and disappearance, instant with reduced animations; the catalog's follow its
            // progress.
            val sheetLevel by animateFloatAsState(if (sheetBlur) 1f else 0f, fade(sheetBlur), label = "sheetBlur")
            LaunchedEffect(Unit) {
                var shown = false
                // Veils drawn in Compose (`WallpaperVeils`): the layer keeps its minimum. A layer dim handed over to a
                // Compose veil made the opening flicker (updated in different frames).
                // Blur follows its own curve, the veil that of Home fading out.
                snapshotFlow { Triple(deck.progress.value, sheetLevel, catalogEntry) }.collect { (p, sh, entry) ->
                    val veil = CatalogMotion.veil(p, entry)
                    val blur = maxOf(CatalogMotion.blur(p), sh)
                    val level = maxOf(veil, blur)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = (blurBehind * blur).roundToInt()
                        dimAmount = Tokens.sheetDimBehind * level
                    }
                    val flags = WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    if (level > 0f && !shown) window.addFlags(flags) else if (level == 0f && shown) window.clearFlags(flags)
                    shown = level > 0f
                }
            }
            val backdrop = rememberBackdrop()
            // Position of Home's dock, where the slot picker's preview starts from.
            val homeDock = remember { DockAnchor() }
            Box(Modifier.fillMaxSize().background(if (veiled && !catalogBlur && list != null) Tokens.scrim else Color.Transparent)) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                val padded = Modifier.safeDrawingPadding().imePadding()
                // Favorites maximum for the onboarding counter, Home not being displayed yet.
                if (onboarding) HomeCapacityProbe(config.clockFont, now, eventAllowed) { capacity = it }
                val page = when {
                    onboarding && list != null && steps.isNotEmpty() -> Page.Step(onboardingIndex.coerceAtMost(steps.lastIndex))
                    // The slot picker sits on Home, which stays the current page; the catalog shares it.
                    current is Screen.DockSlot || current is Screen.Catalog || list == null -> Page.Of(Screen.Home)
                    else -> Page.Of(current)
                }
                // Chained screens. Predictive back: the current screen shrinks alone (showing it over the screen below
                // would double-expose the blurred background); the back transition plays on commit, from the shrunk
                // screen. Cancelled, the screen returns to its size.
                LaunchedEffect(revealRun) { reveal.animateTo(revealTarget, tween(revealSpec.durationMs, easing = LinearEasing)) }
                LaunchedEffect(arrivalRun) {
                    if (arrivalRun == 0) return@LaunchedEffect
                    val spec = Tokens.unlockArrival
                    delay(spec.startDelayMs)
                    // Home stopped during the delay: the sequence was cancelled.
                    if (arrival.value != 0f) return@LaunchedEffect
                    val total = spec.totalMs(Tokens.unlockArrivalRanks)
                    arrival.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
                    arrival.snapTo(ARRIVAL_DONE)
                }
                val pages = remember { SeekableTransitionState(page) }
                // Screen closed while Home was stopped (app open, Home gesture): without a transition, the window comes
                // straight back to Home, which plays its reveal.
                LaunchedEffect(page) { if (reveal.value < 1f) pages.snapTo(page) else pages.animateTo(page) }
                val previous by rememberUpdatedState(previousPage(page, onboarding))
                val backProgress = remember { Animatable(0f) }
                var backFrom by remember { mutableStateOf<Page?>(null) }
                LaunchedEffect(pages.currentState) { if (pages.currentState != backFrom) { backFrom = null; backProgress.snapTo(0f) } }
                // Home ↔ catalog: progress catches up with the current screen, with a spring from a release's velocity,
                // otherwise over the fade durations. No transition when Home was stopped or when coming back from
                // another screen, the whole page then having its own transition.
                var lastScreen by remember { mutableStateOf(current) }
                val catalogBack = remember { Animatable(0f) }
                LaunchedEffect(current) {
                    val from = lastScreen
                    lastScreen = current
                    if (current !is Screen.Catalog) focusManager.clearFocus()
                    val target = when (current) {
                        Screen.Catalog -> 1f
                        Screen.Home, is Screen.DockSlot -> 0f
                        else -> return@LaunchedEffect
                    }
                    val velocity = deck.takeVelocity()
                    when {
                        reveal.value < 1f || (from !is Screen.Catalog && current !is Screen.Catalog) -> deck.progress.snapTo(target)
                        velocity != null -> deck.progress.animateTo(target, Tokens.spatialDefault(), velocity)
                        else -> deck.progress.animateTo(
                            target,
                            tween(if (target == 1f) Tokens.homeFadeOutMs else Tokens.homeFadeInMs, easing = Tokens.emphasizedEasing),
                        )
                    }
                    if (target == 0f) catalogBack.snapTo(0f)
                }
                // Predictive back from the catalog: it shrinks with the gesture; once committed, progress returns to 0.
                PredictiveBackHandler(enabled = current is Screen.Catalog && overlay == null) { progress ->
                    try {
                        progress.collect { catalogBack.snapTo(Tokens.predictiveBackEasing.transform(it.progress)) }
                        back(onboarding)
                    } catch (e: CancellationException) {
                        catalogBack.animateTo(0f, Tokens.spatialDefault())
                    }
                }
                val catalogList = rememberLazyListState()
                // Once closed, it starts again from the top of the list.
                LaunchedEffect(Unit) {
                    snapshotFlow { deck.progress.value == 0f }.collect { closed -> if (closed && screen !is Screen.Catalog) catalogList.scrollToItem(0) }
                }
                val coursePx = with(LocalDensity.current) { Tokens.catalogCourse.toPx() }
                val catalogVelocity = with(LocalDensity.current) { Tokens.catalogVelocityThreshold.toPx() }
                val scope = rememberCoroutineScope()
                val swipePx = with(LocalDensity.current) { Tokens.swipeThreshold.toPx() }
                // Accumulated downward drag, without tracking (animations off).
                var dismissDrag by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(current) { dismissDrag = 0f }
                fun dragDeck(dy: Float) {
                    scope.launch { deck.progress.snapTo((deck.progress.value - dy / coursePx).coerceIn(0f, 1f)) }
                }
                // Release (as in Launcher3): a fling decides by its direction, otherwise by the share of travel covered.
                fun releaseDeck(vy: Float) {
                    val open = if (abs(vy) > catalogVelocity) vy < 0 else deck.progress.value > Tokens.catalogCommit
                    val target = if (open) Screen.Catalog else Screen.Home
                    val velocity = -vy / coursePx
                    if (screen == target) scope.launch { deck.progress.animateTo(if (open) 1f else 0f, Tokens.spatialDefault(), velocity) }
                    else { deck.release(velocity); screen = target }
                }
                // Search from Home: the "Search apps" row under the favorites opens search only, keyboard open; swiping
                // up opens the catalog (keyboard closed).
                var searchRequested by remember { mutableStateOf(false) }
                var gestureStart by remember { mutableStateOf(false) }
                val searchOnly = catalogEntry == CatalogEntry.Search
                fun openSearch() {
                    catalogEntry = CatalogEntry.Search
                    searchRequested = true
                    if (screen != Screen.Catalog) { deck.release(0f); screen = Screen.Catalog }
                }
                fun dragFromHome(dy: Float) {
                    if (!gestureStart) { gestureStart = true; catalogEntry = CatalogEntry.Up; query = "" }
                    dragDeck(dy)
                }
                fun releaseFromHome(vy: Float) {
                    gestureStart = false
                    releaseDeck(vy)
                }
                fun browseCatalog() {
                    catalogEntry = CatalogEntry.Up
                    query = ""
                    if (screen != Screen.Catalog) { deck.release(0f); screen = Screen.Catalog }
                }
                if (previous != null) PredictiveBackHandler { progress ->
                    backFrom = page
                    try {
                        progress.collect { backProgress.snapTo(Tokens.predictiveBackEasing.transform(it.progress)) }
                        back(onboarding)
                    } catch (e: CancellationException) {
                        backProgress.animateTo(0f, Tokens.spatialDefault())
                        backFrom = null
                    }
                }
                val slide = with(LocalDensity.current) { Tokens.sharedAxisSlide.roundToPx() }
                rememberTransition(pages).AnimatedContent(
                    // Screens closed while an app was in front: Home comes back with its reveal, without a screen transition.
                    transitionSpec = { if (reveal.value < 1f) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null) else transitionFor(initialState, targetState, slide) },
                ) { p ->
                Box(Modifier.graphicsLayer {
                    if (p == backFrom) {
                        val scale = lerp(1f, Tokens.predictiveBackScale, backProgress.value)
                        scaleX = scale
                        scaleY = scale
                    }
                }) {
                when {
                    p is Page.Step && list != null && config != null -> when (val step = steps.getOrNull(p.index)) {
                        null -> Unit
                        Step.Profiles -> ProfileSelectionScreen(
                            initial = onboardingProfiles ?: config.profiles.selected,
                            step = p.index + 1, total = steps.size,
                            primary = stringResource(R.string.next),
                            skip = stringResource(R.string.skip),
                            onSkip = { store.selectProfiles(emptyList()); onboardingProfiles = emptyList(); onboardingIndex++ },
                            onConfirm = { choice -> store.selectProfiles(choice); onboardingProfiles = choice; onboardingIndex++ },
                            modifier = padded,
                        )
                        is Step.Apps -> DockAppsStep(
                            profile = step.profile, step = p.index + 1, total = steps.size,
                            initial = config.profiles.dock(step.profile).slots.mapNotNull { s -> s?.let { installed?.get(it.appId) } },
                            apps = list, suggested = suggested(step.profile), alphabet = alphabet,
                            query = query, onQueryChange = { query = it }, color = repository::color,
                            icon = repository::themedIcon,
                            excluded = notInFavorites,
                            onSkip = { store.update("onboarding.keepDock") { it.keepDock(step.profile) }; query = ""; onboardingIndex++ },
                            onDone = { chosen -> store.setDockApps(step.profile, chosen); query = ""; onboardingIndex++ },
                            modifier = padded,
                        )
                        Step.Favorites -> FavoritesStep(
                            step = p.index + 1, total = steps.size,
                            chosen = onboardingFavorites ?: slots.orEmpty().filterIsInstance<FavoriteSlot.Installed<App>>().map { it.app },
                            onChosenChange = { onboardingFavorites = it },
                            capacity = capacity,
                            apps = list, alphabet = alphabet, query = query, onQueryChange = { query = it },
                            color = repository::color, excluded = notInDock,
                            onSkip = { query = ""; nextOrFinish(steps.size) },
                            onDone = { chosen ->
                                store.update("onboarding.favorites") { c -> c.withFavorites(chosen.map { Favorite(it.id, it.label) }) }
                                query = ""
                                nextOrFinish(steps.size)
                            },
                            modifier = padded,
                        )
                        Step.Role -> RoleStep(
                            step = p.index + 1, total = steps.size,
                            favorites = slots.orEmpty().filterIsInstance<FavoriteSlot.Installed<App>>().map { it.app },
                            clockFont = config.clockFont, now = now, color = repository::color,
                            onNotNow = { finishOnboarding() },
                            onSetDefault = { finishAfterRole = true; requestRole() },
                            modifier = padded,
                        )
                    }
                    // Under the slot picker, Home stays composed and fades out. The catalog sits on it, driven by the same
                    // progress: a hidden layer is neither drawn, nor touchable, nor read by TalkBack.
                    p !is Page.Of || p.screen is Screen.Home || list == null -> Box {
                    val onCatalog = current is Screen.Catalog
                    // Catalog composed once the page is in place (screen transition over, two more frames), then kept with
                    // it: opening it does not recreate it, and a recreated page (back from the favorites editor) does not
                    // add its cost to the transition. A drag composes it right away.
                    var catalogReady by remember { mutableStateOf(false) }
                    LaunchedEffect(list != null) {
                        if (list == null) return@LaunchedEffect
                        snapshotFlow { transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible }.first { it }
                        withFrameNanos { }
                        withFrameNanos { }
                        catalogReady = true
                    }
                    // Wallpaper veils, outside Home's reveal so they cover the whole screen.
                    WallpaperVeils(darkText, withDock = active != null, entry = { catalogEntry }, reveal = { revealSpec.easing.transform(reveal.value) }) { deck.progress.value }
                    // The active layer is on top and receives touches: Home, then the catalog once chosen.
                    Box(Modifier.zIndex(if (onCatalog) 0f else 1f).homeReveal({ revealSpec }, { revealPivot }) { reveal.value }.then(if (onCatalog) Modifier.clearAndSetSemantics { } else Modifier)) { HomeScreen(
                        now = now,
                        clockFont = config?.clockFont ?: ClockFont(),
                        weather = weatherGlance,
                        event = nextEvent,
                        eventAllowed = eventAllowed,
                        slots = slots,
                        color = repository::color,
                        onLaunch = { app, bounds -> launchApp(app, bounds) },
                        onOpenCatalog = { catalogReady = true; browseCatalog() },
                        onOpenShade = { expandNotificationShade(this@HomeActivity) },
                        onOpenClock = { openClockApp(this@HomeActivity) },
                        onOpenPanel = { overlay = Overlay.Panel },
                        onEditFavorites = { screen = Screen.EditFavorites },
                        onChooseFavorites = { overlay = Overlay.Pick(replacing = null) },
                        onReplace = { overlay = Overlay.Pick(replacing = it) },
                        onRemove = { fav -> store.update("home.missingRemove") { it.removeFavorite(fav.appId) } },
                        onWallpaper = { openWallpaperPicker(this@HomeActivity) },
                        // Settings in their own task.
                        onSettings = { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        onCapacity = { capacity = it },
                        onMenuShown = { homeMenu = it },
                        menuReset = menuReset,
                        onAppMenu = { overlay = Overlay.AppMenu(it.id, fromFavorite = true) },
                        profile = active,
                        dock = active?.let { profiles.dock(it) },
                        installed = installed,
                        icon = repository::themedIcon,
                        onDockSlot = { index -> active?.let { screen = Screen.DockSlot(it, index) } },
                        onOpenProfiles = { overlay = Overlay.Profiles },
                        dockAnchor = homeDock,
                        faded = current is Screen.DockSlot,
                        // Apps not loaded yet: the catalog would not open, so Home does not follow the finger.
                        onCatalogDrag = if (list == null) null else { dy -> catalogReady = true; dragFromHome(dy) },
                        onCatalogRelease = ::releaseFromHome,
                        // Gesture taken over by the system: decided like a release without velocity.
                        onCatalogCancel = { releaseFromHome(0f) },
                        onOpenSearch = if (list == null) null else { { catalogReady = true; openSearch() } },
                        contentAlpha = { CatalogMotion.homeAlpha(deck.progress.value, catalogEntry) },
                        contentOffset = { CatalogMotion.homeOffset(deck.progress.value, coursePx, catalogEntry) },
                        arrival = { rank -> Tokens.unlockArrival.progress(arrival.value, rank) },
                    )
                    // Home inert under the catalog (no touch goes through); while closing it becomes live again:
                    // dragging reopens it.
                    if (onCatalog) Box(Modifier.matchParentSize().blockTouches())
                    }
                    val catalogShown by remember { derivedStateOf { deck.progress.value > 0f } }
                    if (list != null && (catalogReady || onCatalog || catalogShown)) {
                        val settled by remember { derivedStateOf { deck.progress.value >= 1f } }
                        Box(
                            Modifier
                                .zIndex(if (onCatalog) 1f else 0f)
                                .graphicsLayer {
                                    alpha = CatalogMotion.catalogAlpha(deck.progress.value, catalogEntry)
                                    val back = lerp(1f, Tokens.predictiveBackScale, catalogBack.value)
                                    scaleX = back
                                    scaleY = back
                                }
                                // Offset at draw time: the downward drag is measured on a still list.
                                .drawWithContent {
                                    // Search only: no offset, the field only moves with the keyboard.
                                    translate(top = CatalogMotion.catalogOffset(deck.progress.value, coursePx, catalogEntry)) { this@drawWithContent.drawContent() }
                                }
                                .then(if (onCatalog) Modifier else Modifier.clearAndSetSemantics { }),
                        ) {
                            CatalogScreen(
                                apps = list,
                                alphabet = alphabet,
                                query = query,
                                onQueryChange = { query = it },
                                color = repository::color,
                                onLaunch = { app, bounds -> launchApp(app, bounds) },
                                // Edge to edge: the catalog handles bars and keyboard itself.
                                modifier = Modifier,
                                // The search keyboard closes: it would hide the bottom of the menu.
                                onLongPress = { focusManager.clearFocus(); overlay = Overlay.AppMenu(it.id, fromFavorite = false) },
                                listState = catalogList,
                                // Downward, back to Home; with animations off: at the threshold, without tracking.
                                onDismissDrag = { dy ->
                                    if (ValueAnimator.areAnimatorsEnabled()) dragDeck(dy)
                                    else { dismissDrag += dy; if (dismissDrag > swipePx) { dismissDrag = 0f; back(onboarding) } }
                                },
                                onDismissRelease = ::releaseDeck,
                                dismissing = { deck.progress.value < 1f },
                                searchRequested = searchRequested,
                                onSearchRequestHandled = { searchRequested = false },
                                searchOnly = searchOnly,
                                onExitSearch = { if (screen == Screen.Catalog) { back(onboarding) } },
                            )
                            // Not in place yet, or leaving: no touch.
                            if (!onCatalog || !settled) Box(Modifier.matchParentSize().blockTouches())
                        }
                    }
                    }
                    p.screen is Screen.EditFavorites -> EditFavoritesScreen(
                        slots = slots.orEmpty(),
                        capacity = capacity,
                        color = repository::color,
                        onMove = { from, to -> store.update("edit.move") { it.copy(favorites = it.favorites.move(from, to)) } },
                        onRemove = { fav -> store.update("edit.remove") { it.removeFavorite(fav.appId) } },
                        onRestore = { index, fav -> store.update("edit.restore") { it.restoreFavorite(index, fav) } },
                        onAdd = { overlay = Overlay.Pick(replacing = null) },
                        onDone = { screen = Screen.Home },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
                // Leaving screen: no touch, like a leaving sheet.
                if (transition.targetState != EnterExitState.Visible) Box(Modifier.matchParentSize().blockTouches())

                }
                }
                }

                // Sheet veil, above the current screen, with its fade.
                if (sheetLevel > 0f) Box(Modifier.fillMaxSize().background(Tokens.sheetScrim.copy(alpha = Tokens.sheetScrim.alpha * sheetLevel)))

                // Slot picker, then the sheets over the current screen.
                // Each stays composed during its exit: the slot picker slides closed while Home comes back.
                SheetHost(current as? Screen.DockSlot, onDismiss = { query = ""; screen = Screen.Home }) { slot ->
                if (profiles != null && installed != null && list != null) {
                    // Choice shown at once in the preview, without waiting for the configuration write;
                    // kept when another slot of the same dock is targeted.
                    var chosen by remember(slot.profile) { mutableStateOf<Dock?>(null) }
                    var cleared by remember(slot) { mutableStateOf(false) }
                    val dock = chosen ?: profiles.dock(slot.profile)
                    val here = dock.slots.getOrNull(slot.index)
                    val name = profileLabel(slot.profile)
                    val inThisDock = stringResource(R.string.in_dock)
                    val currentLabel = stringResource(R.string.current_app)
                    val close = { query = ""; screen = Screen.Home }
                    DockSlotSheet(
                        profile = slot.profile, dock = dock, index = slot.index,
                        installed = installed, icon = repository::themedIcon, anchor = homeDock,
                        onSlot = { screen = Screen.DockSlot(slot.profile, it) }, onDismiss = close,
                    ) {
                        AppPicker(
                            title = stringResource(R.string.dock_slot_title, name, slot.index + 1),
                            apps = list, suggested = suggested(slot.profile),
                            suggestedTitle = stringResource(R.string.suggested_for, name),
                            alphabet = alphabet, query = query, onQueryChange = { query = it }, color = repository::color,
                            titleStyle = Tokens.sheetTitle,
                            margin = Tokens.sheetMargin,
                            markStyle = Tokens.sheetSection,
                            mark = { app -> currentLabel.takeIf { app.id == here?.appId } },
                            excluded = { app ->
                                notInFavorites(app) ?: inThisDock.takeIf { app.id != here?.appId && app.id in dock }
                            },
                            // The sheet stays open on the slot: the search clears and the keyboard closes, the preview
                            // comes back with the new icon.
                            onPick = { app ->
                                cleared = false
                                chosen = dock.set(slot.index, DockApp(app.id, app.label))
                                store.update("dockSlot.set") { it.setDockSlot(slot.profile, slot.index, DockApp(app.id, app.label)) }
                                query = ""
                                focusManager.clearFocus()
                            },
                            header = {
                                // "Clear this slot" first, under the title. The sheet stays open: the preview shows the
                                // empty slot and the row collapses; it comes back if an app is chosen.
                                AnimatedVisibility(here != null && !cleared, exit = fadeOut(Tokens.effectsFast()) + shrinkVertically(Tokens.effectsDefault())) {
                                    SheetRow(onClick = {
                                        cleared = true
                                        chosen = dock.clear(slot.index)
                                        store.update("dockSlot.clear") { it.clearDockSlot(slot.profile, slot.index) }
                                    }) {
                                        SheetIcon(R.drawable.ic_remove_circle)
                                        BasicText(stringResource(R.string.clear_slot), style = Tokens.menuLabel)
                                    }
                                }
                            },
                        )
                    }
                }
                }
                SheetHost(overlay, onDismiss = ::closeOverlay) { o ->
                when (o) {
                    Overlay.Panel -> launcher.panel.Sheet(now, onDismiss = { overlay = null })
                    Overlay.Profiles -> if (profiles != null && installed != null) ProfileSheet(
                        selected = profiles.selected, active = active,
                        dock = profiles::dock, installed = installed, icon = repository::themedIcon,
                        onChoose = { profile -> choose(profile); overlay = null },
                        onDismiss = { overlay = null },
                    )
                    // App menu, full-dock and full-favorites choices: a single sheet (same call site, so same surface),
                    // whose content switches from one to the other without closing it.
                    is Overlay.AppMenu, is Overlay.DockFull, is Overlay.FavoritesFull -> Sheet(onDismiss = { overlay = null }) {
                        AnimatedContent(o, contentKey = { it::class }, transitionSpec = { ScreenTransitions.sheetSwap() }, label = "menuSheet") { m ->
                            when (m) {
                                is Overlay.AppMenu -> {
                                    val app = installed?.get(m.appId)
                                    if (app != null && config != null) AppMenuSheet(
                                        app = app, config = config, fromFavorite = m.fromFavorite, undo = undo,
                                        onFavorite = { favoriteAction(config, app, m.fromFavorite) },
                                        onDock = { profile -> dockAction(config, app, profile) },
                                        onUndo = { undo?.let { u -> store.update("menu.undo") { it.undo(u) } }; undo = null },
                                        onEditFavorites = { overlay = null; screen = Screen.EditFavorites },
                                        onAppInfo = { overlay = null; repository.openAppInfo(app, null) },
                                        onUninstall = { overlay = null; repository.uninstall(app) },
                                    )
                                }
                                is Overlay.DockFull -> {
                                    val app = installed?.get(m.appId)
                                    if (app != null && profiles != null && installed != null) DockFullSheet(
                                        profile = m.profile, appLabel = app.label, dock = profiles.dock(m.profile),
                                        installed = installed, icon = repository::themedIcon,
                                        onReplace = { index ->
                                            store.update("dockFull.replace") { it.replaceInDock(m.profile, index, DockApp(app.id, app.label)) }
                                            overlay = null
                                        },
                                        onDismiss = { overlay = null },
                                    )
                                }
                                is Overlay.FavoritesFull -> {
                                    val app = installed?.get(m.appId)
                                    if (app != null && config != null && installed != null) FavoritesFullSheet(
                                        appLabel = app.label, favorites = config.favorites.items, installed = installed, color = repository::color,
                                        onReplace = { old ->
                                            nextUndo = MenuUndo.Replaced(old, app.id, config.dockPlaces(app.id))
                                            store.update("favoritesFull.replace") { it.replaceFavoriteFromMenu(old.appId, Favorite(app.id, app.label)) }
                                            overlay = Overlay.AppMenu(app.id, m.fromFavorite)
                                        },
                                        onDismiss = { overlay = null },
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                    is Overlay.Pick -> if (list != null) FavoritePickSheet(onDismiss = ::closeOverlay) {
                        // An app that is already a favorite is not offered; a dock app is grayed out.
                        AppPicker(
                            title = o.replacing?.let { stringResource(R.string.replace_favorite_title, installed?.get(it.appId)?.label ?: it.label) }
                                ?: stringResource(R.string.add_favorite),
                            apps = list.filterNot { favorites != null && it.id in favorites },
                            suggested = emptyList(), suggestedTitle = null,
                            alphabet = alphabet, query = query, onQueryChange = { query = it }, color = repository::color,
                            titleStyle = Tokens.sheetTitle,
                            margin = Tokens.sheetMargin,
                            markStyle = Tokens.sheetSection,
                            mark = { null },
                            excluded = notInDock,
                            onPick = { app -> pick(app, o.replacing) },
                            header = { if (query.isBlank()) SheetSection(stringResource(R.string.all_apps)) },
                        )
                    }
                }
                }
            }
            }
            }
            }
        }
    }

    private fun nextOrFinish(total: Int) {
        if (onboardingIndex + 1 >= total) finishOnboarding() else onboardingIndex++
    }

    private fun finishOnboarding() {
        onboardingDone = true
        finishAfterRole = false
        store.update("onboarding.done") { it.copy(onboarded = true) }
        reset()
    }


    /** Manual profile choice: applied at once. */
    private fun choose(profile: Profile) {
        autoSwitch.chosen(profile)
        store.update("profile.choose") { it.copy(profiles = ContextEngine.choose(ContextState(it.profiles), profile).profiles) }
    }

    /**
     * Favorites row of an app menu: removes, adds, or moves an app from the docks to the favorites; in the last case
     * the menu stays open and its dock rows offer "Undo". Favorites full: the full-favorites choice.
     */
    private fun favoriteAction(config: Config, app: App, fromFavorite: Boolean) {
        val favorite = Favorite(app.id, app.label)
        when {
            config.isFavorite(app.id) -> { store.update("menu.removeFavorite") { it.removeFavorite(app.id) }; overlay = null }
            config.favorites.full -> overlay = Overlay.FavoritesFull(app.id, fromFavorite)
            config.inDock(app.id) -> {
                undo = MenuUndo.ToFavorites(app.id, config.dockPlaces(app.id))
                store.update("menu.moveToFavorites") { it.moveToFavorites(favorite) }
            }
            else -> { store.update("menu.addFavorite") { it.addFavorite(favorite) }; overlay = null }
        }
    }

    /**
     * Dock row of an app menu: removes while keeping the menu open, with "Undo" in the row; adds to the first free
     * slot, a favorite then leaving the favorites (menu open, "Undo"); otherwise the full-dock choice.
     */
    private fun dockAction(config: Config, app: App, profile: Profile) {
        val dock = config.profiles.dock(profile)
        val index = dock.indexOf(app.id)
        val dockApp = DockApp(app.id, app.label)
        when {
            index >= 0 -> {
                store.update("menu.removeFromDock") { it.removeFromDock(profile, app.id) }
                undo = MenuUndo.Removed(DockPlace(profile, index, dock.slots[index] ?: dockApp))
            }
            else -> when (val result = config.moveToDock(profile, dockApp)) {
                is DockAdd.Added -> {
                    store.update("menu.moveToDock") { (it.moveToDock(profile, dockApp) as? DockAdd.Added)?.config ?: it }
                    val position = config.favorites.items.indexOfFirst { it.appId == app.id }
                    if (position >= 0) undo = MenuUndo.ToDock(Favorite(app.id, app.label), position, DockPlace(profile, result.index, dockApp))
                    else overlay = null
                }
                DockAdd.Full -> overlay = Overlay.DockFull(app.id, profile)
                DockAdd.Refused -> Unit
            }
        }
    }

    /** One choice per opening: the sheet closes onto the screen it was opened from. */
    private fun pick(app: App, replacing: Favorite?) {
        val favorite = Favorite(app.id, app.label)
        store.update("pick") { config ->
            replacing?.let { config.replaceFavorite(it.appId, favorite) } ?: config.addFavorite(favorite)
        }
        closeOverlay()
    }

    /** Closes the sheet; the picker's search does not outlive its sheet, the catalog's stays. */
    private fun closeOverlay() {
        if (overlay is Overlay.Pick) query = ""
        overlay = null
    }

    override fun onStart() {
        super.onStart()
        // Back from an app (gesture, button, app exit) or screen wake-up: Home plays its reveal.
        revealTarget = 1f
        revealRun++
        // After an unlock, Home's elements arrive one by one instead of the reveal; with reduced animations, Home shows
        // at once.
        val keyguard = getSystemService(KeyguardManager::class.java)
        val deviceLocked = keyguard.isDeviceLocked
        val keyguardLocked = keyguard.isKeyguardLocked
        val pending = unlockWatch.pending
        val unlocked = unlockWatch.onStart(deviceLocked, keyguardLocked)
        // States only: makes the trigger checkable on devices whose keyguard timing differs.
        if (pending || keyguardLocked) Log.i("Unlock", "deviceLocked=$deviceLocked keyguardLocked=$keyguardLocked pending=$pending arrival=$unlocked")
        if (unlocked && ValueAnimator.areAnimatorsEnabled()) {
            lifecycleScope.launch {
                reveal.snapTo(1f)
                arrival.snapTo(0f)
            }
            arrivalRun++
        }
        refreshRole()
        // Wi-Fi is read right now: an already stable connection switches before display.
        autoSwitch.visible(true)
        weather.visible(true)
        events.visible(true)
        now = shownNow()
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    /** Opens [app]; Home fades out behind it without delaying the launch. */
    private fun launchApp(app: App, bounds: Rect?) {
        launcher.apps.launch(app, bounds)
        revealTarget = 0f
        revealRun++
    }

    override fun onStop() {
        super.onStop()
        unlockWatch.onStop(getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        lifecycleScope.launch {
            reveal.snapTo(0f)
            arrival.snapTo(ARRIVAL_DONE)
        }
        // Reveal for the button or app exit; a gesture return replaces it in onNewIntent.
        revealSpec = Tokens.homeReveal
        revealPivot = null
        gestureReturn.onTarget = null
        // Catalog closed right away (reset): without frames, its progress would only move on return.
        lifecycleScope.launch { deck.progress.snapTo(0f) }
        unregisterReceiver(timeReceiver)
        autoSwitch.visible(false)
        weather.visible(false)
        events.visible(false)
        gestureReturn.clear(immediate = true)
        // Blur and veil of the catalog and sheets removed right away: their fade only plays with frames, so it would
        // otherwise come back over Home on return.
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        reset()
    }

    /** Onboarding step and choices not yet written, kept if the activity is recreated. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, onboardingIndex)
        onboardingProfiles?.let { list -> outState.putStringArrayList(KEY_PROFILES, ArrayList(list.map { it.name })) }
        (onboardingFavorites?.map { it.id } ?: restoredFavorites)?.let { outState.putStringArrayList(KEY_FAVORITES, ArrayList(it)) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> pointerDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pointerDown = false
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Touchscreen hover: TalkBack's explore-by-touch sends no touch, only hover events. Mouse and stylus, which hover
     * without a gesture in progress, do not count.
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> hovering = true
            MotionEvent.ACTION_HOVER_EXIT -> hovering = false
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button or gesture while Home is already displayed.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) reset()
        // Gesture return from an app: animation toward its dot or icon, and Home reveal like the Pixel launcher, as
        // soon as the gesture ends. Center set before replying: the target is often reported during answer().
        if (reveal.value < 1f && GestureReturn.isGesture(intent)) {
            revealSpec = Tokens.homeRevealGesture
            revealRun++
            gestureReturn.onTarget = { revealPivot = Offset(it.exactCenterX(), it.exactCenterY()) }
        }
        gestureReturn.answer(intent, launcher.apps::idOf)
    }

    /** Page that Back leads to from [page], like [back]; null if it leads nowhere (Home). */
    private fun previousPage(page: Page, onboarding: Boolean): Page? = when (page) {
        is Page.Step -> if (onboarding && page.index > 0) Page.Step(page.index - 1) else null
        is Page.Of -> when (page.screen) {
            Screen.Home, is Screen.DockSlot -> null
            else -> Page.Of(Screen.Home)
        }
    }

    private fun back(onboarding: Boolean) {
        when {
            overlay != null -> closeOverlay()
            onboarding -> { query = ""; if (onboardingIndex > 0) onboardingIndex-- }
            else -> when (screen) {
                Screen.Home -> Unit
                else -> reset()
            }
        }
    }

    /** Closes screens and sheets; onboarding keeps its step. */
    private fun reset() {
        screen = Screen.Home
        overlay = null
        undo = null
        query = ""
        menuReset++
        hovering = false
    }

    /** Blur and veil fade: 200 ms on appearance, 250 ms on disappearance. */
    private fun fade(showing: Boolean) =
        if (showing) tween<Float>(Tokens.homeFadeOutMs, easing = Tokens.fadeInEasing) else tween(Tokens.homeFadeInMs, easing = Tokens.fadeOutEasing)

    private fun refreshRole() {
        roleHeld = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    private companion object {
        const val KEY_STEP = "onboarding.step"
        const val KEY_PROFILES = "onboarding.profiles"
        const val KEY_FAVORITES = "onboarding.favorites"
        /** Unlock arrival over: every element in place. */
        const val ARRIVAL_DONE = Float.MAX_VALUE
    }
}

/**
 * Veils over the wallpaper under Home and the catalog, following [catalogProgress]: Home's flat veil becomes the
 * catalog's (same black under light text, white from 24 to 56 % under dark text), in a single layer; the gradient
 * under the dock (light text and dock only) fades with Home's content. Read at draw time: dragging does not
 * recompose.
 */
@Composable
private fun BoxScope.WallpaperVeils(
    darkText: Boolean,
    withDock: Boolean,
    entry: () -> CatalogEntry,
    reveal: () -> Float,
    catalogProgress: () -> Float,
) {
    val overlay = Tokens.homeOverlay
    val catalog = Tokens.scrim
    val gradient = with(LocalDensity.current) { Tokens.dockGradientHeight.toPx() }
    Spacer(Modifier.matchParentSize().drawBehind {
        val p = catalogProgress()
        // Home reveal: the veils follow the scale curve, like the Pixel launcher.
        val r = reveal()
        val veil = lerp(overlay, catalog, CatalogMotion.veil(p, entry()))
        drawRect(veil.copy(alpha = veil.alpha * r))
        val home = CatalogMotion.homeAlpha(p, entry()) * r
        if (home > 0f && !darkText && withDock) {
            translate(top = size.height - gradient) { drawRect(Tokens.dockGradient, size = Size(size.width, gradient), alpha = home) }
        }
    })
}

/**
 * Scale, blur and opacity of Home's content according to [progress] and [spec]. The scale is applied at draw time
 * only: the layout, hence the position of dots and icons given to the gesture return, stays the final position.
 * Centered on [pivot] (window coordinates) if given, otherwise on Home.
 */
private fun Modifier.homeReveal(spec: () -> HomeReveal, pivot: () -> Offset?, progress: () -> Float) = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInWindow() }
        .graphicsLayer {
            val s = spec()
            val p = progress()
            alpha = (p * s.durationMs / s.alphaMs).coerceAtMost(1f)
            val blur = s.blur.toPx() * (1f - s.blurEasing.transform((p * s.durationMs / s.blurMs).coerceAtMost(1f)))
            renderEffect = if (blur >= 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        .drawWithContent {
            val s = spec()
            val scale = lerp(s.startScale, 1f, s.easing.transform(progress()))
            scale(scale, pivot()?.minus(origin) ?: center) { this@drawWithContent.drawContent() }
        }
}
