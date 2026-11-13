package io.github.tomlmbl.launcher.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.location.LocationManager
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.lerp
import androidx.core.graphics.drawable.toDrawable
import io.github.tomlmbl.launcher.LauncherApplication
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.apps.App
import io.github.tomlmbl.launcher.catalog.Alphabet
import io.github.tomlmbl.launcher.context.Detection
import io.github.tomlmbl.launcher.context.Profile
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.context.Suggestions
import io.github.tomlmbl.launcher.home.appDetailsIntent
import io.github.tomlmbl.launcher.home.formatDate
import io.github.tomlmbl.launcher.home.formatTime
import io.github.tomlmbl.launcher.home.profileLabel
import io.github.tomlmbl.launcher.onboarding.DockAppsStep
import io.github.tomlmbl.launcher.onboarding.ProfileSelectionScreen
import io.github.tomlmbl.launcher.signals.WifiStatus
import io.github.tomlmbl.launcher.ui.LauncherTheme
import io.github.tomlmbl.launcher.ui.shownNow
import io.github.tomlmbl.launcher.weather.WeatherData
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.ScreenTransitions
import io.github.tomlmbl.launcher.ui.ScreenTransitions.fadeThroughAlpha
import io.github.tomlmbl.launcher.ui.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings page: "Launcher settings", "Clock style", "Text on wallpaper", choice of profiles and of their apps.
 */
private sealed interface Page {
    data object Main : Page
    data object ClockStyle : Page
    data object TextOnWallpaper : Page
    /** Choice of the three profiles; [enabling]: opened by the toggle (turning on), otherwise by "Change profiles". */
    data class ProfileSelection(val enabling: Boolean) : Page
    /** Apps of the added profiles without a kept dock, one profile after the other. */
    data class ProfileApps(val queue: List<Profile>) : Page
    /** "Automatic switching" and its explanation before the permission. */
    data object AutoSwitch : Page
    data object Explain : Page
    /** "Weather". */
    data object Weather : Page
    data object About : Page
    data object Licenses : Page
    data class LicenseText(val component: String) : Page
}

/** Page depth, for the direction of the shared axis. */
private val Page.depth get() = when (this) {
    Page.Main -> 0
    Page.Explain, Page.Licenses -> 2
    is Page.LicenseText -> 3
    else -> 1
}

/**
 * Launcher settings, in their own task: the Home gesture and Back from the main page are the system's, as for an
 * app, above the real Home.
 * Subpages chain on the shared axis; predictive back shrinks them with the gesture.
 * Once left, they close (like the Home screens); excluded from recents.
 */
class SettingsActivity : ComponentActivity() {

    private var page by mutableStateOf<Page>(Page.Main)
    private var query by mutableStateOf("")
    private var roleHeld by mutableStateOf(true)
    private var now by mutableLongStateOf(shownNow())
    // The Home role dialog may stop the activity: it does not close while the dialog is open.
    private var roleRequested = false
    // Location dialog, system settings or browser: they stop the activity, and the user comes back here.
    private var systemSettingsOpened = false
    /** Denied at the last location dialog: "Automatic switching" explains it, the toggle stays off. */
    private var locationDenied by mutableStateOf(false)
    private var unlinked by mutableStateOf<Unlinked?>(null)
    /** Reads the system state again (permission, location, Wi-Fi) when returning to "Automatic switching". */
    private var statusTick by mutableIntStateOf(0)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            now = shownNow()
        }
    }

    private var themeBackground: Drawable? = null

    /** Preview frame of a page ("Clock style", "Text on wallpaper") in the window and its page's opacity, while composed. */
    private var preview by mutableStateOf<RoundRect?>(null)
    private var previewAlpha by mutableStateOf<State<Float>?>(null)

    private val launcher get() = application as LauncherApplication
    private val store get() = launcher.config

    override fun onCreate(savedInstanceState: Bundle?) {
        // "Set a city" in the panel opens "Weather" directly; Back returns to "Launcher settings".
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_WEATHER, false)) page = Page.Weather
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        themeBackground = window.decorView.background
        // Translucent surface: otherwise, derived from the opaque theme, the hole under the "Clock style" preview would stay black.
        window.setFormat(PixelFormat.TRANSLUCENT)
        val repository = launcher.apps

        setContent {
            val apps by repository.apps.collectAsState()
            val config by store.config.collectAsState()
            val darkText by launcher.wallpaper.darkText.collectAsState()
            val locale = LocalConfiguration.current.locales[0]
            val alphabet = remember(locale) { Alphabet(locale) }
            val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                roleRequested = false
                refreshRole()
            }
            val profiles = config?.profiles
            // Precise and approximate location requested together; approximate only: switching is turned on and
            // "Automatic switching" explains that it cannot see the network name.
            val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val any = result.values.any { it }
                locationDenied = !any
                if (any) store.update("settings.detectionOn") { it.copy(detection = it.detection.withEnabled(true)) }
                statusTick++
                go(Page.AutoSwitch)
            }
            val requestLocation = {
                systemSettingsOpened = true
                locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            val weather = launcher.weather
            val inFavorites = stringResource(R.string.in_favorites)
            val notInFavorites: (App) -> String? = { app -> inFavorites.takeIf { config?.isFavorite(app.id) == true } }

            LauncherTheme(darkText) {
                val backProgress = remember { Animatable(0f) }
                var backFrom by remember { mutableStateOf<Page?>(null) }
                val scope = rememberCoroutineScope()
                PredictiveBackHandler(enabled = page != Page.Main) { progress ->
                    backFrom = page
                    try {
                        progress.collect { backProgress.snapTo(Tokens.predictiveBackEasing.transform(it.progress)) }
                        back()
                        // The page being left keeps its shrink during the back transition.
                        scope.launch {
                            delay(Tokens.screenTransitionMs.toLong())
                            backFrom = null
                            backProgress.snapTo(0f)
                        }
                    } catch (e: CancellationException) {
                        backProgress.animateTo(0f, Tokens.spatialDefault())
                        backFrom = null
                    }
                }
                val slide = with(LocalDensity.current) { Tokens.sharedAxisSlide.roundToPx() }
                val pages = updateTransition(page, label = "settings")
                // "Clock style" and "Text on wallpaper" show the wallpaper under the preview. Under the pages, the surface is
                // painted everywhere except under the preview, where the wallpaper appears and disappears with the page
                // (its fade) and follows scrolling and the predictive back shrink, in the same frame as Compose. The window
                // shows the wallpaper only during the page's life (see `showWallpaper`).
                val surface = MaterialTheme.colorScheme.surface
                Box(Modifier.fillMaxSize().drawBehind {
                    val hole = preview
                    if (hole == null) drawRect(surface)
                    else {
                        val frame = Path().apply { addRoundRect(hole) }
                        clipPath(frame, ClipOp.Difference) { drawRect(surface) }
                        drawPath(frame, surface, alpha = 1f - (previewAlpha?.value ?: 0f))
                    }
                }) {
                    pages.AnimatedContent(
                        transitionSpec = { ScreenTransitions.sharedAxisX(forward = targetState.depth > initialState.depth, slide) },
                    ) { p ->
                        // A page left by the gesture shrinks with it; the transition plays on commit.
                        Box(Modifier.graphicsLayer {
                            if (p == backFrom) {
                                val scale = lerp(1f, Tokens.predictiveBackScale, backProgress.value)
                                scaleX = scale
                                scaleY = scale
                            }
                        }) {
                            val padded = Modifier.safeDrawingPadding().imePadding()
                            val version = remember { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() }
                            when (p) {
                                Page.Main -> SettingsScreen(
                                    font = config?.clockFont ?: ClockFont(),
                                    profiles = profiles ?: Profiles(),
                                    onChangeProfiles = { go(Page.ProfileSelection(enabling = false)) },
                                    // Turning on goes through the choice of three profiles; the toggle stays off if cancelled.
                                    onProfilesEnabled = { on -> if (on) go(Page.ProfileSelection(enabling = true)) else store.selectProfiles(emptyList()) },
                                    roleHeld = roleHeld,
                                    onRequestRole = {
                                        roleRequested = true
                                        roleRequest.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
                                    },
                                    onClockStyle = { go(Page.ClockStyle) },
                                    textOnWallpaper = config?.textOnWallpaper ?: TextOnWallpaper.AUTOMATIC,
                                    onTextOnWallpaper = { go(Page.TextOnWallpaper) },
                                    onBack = { finish() },
                                    autoSummary = autoSummary(config, profiles),
                                    onAutoSwitching = { go(Page.AutoSwitch) },
                                    weatherSummary = weather.data.collectAsState().value?.city?.name ?: stringResource(R.string.weather_off),
                                    onWeather = { go(Page.Weather) },
                                    version = version,
                                    onAbout = { go(Page.About) },
                                )
                                Page.About -> AboutScreen(
                                    version = version,
                                    onOpenLink = ::openLink,
                                    onLicenses = { go(Page.Licenses) },
                                    onBack = { back() },
                                    modifier = padded,
                                )
                                Page.Licenses -> LicensesScreen(onOpen = { go(Page.LicenseText(it)) }, onBack = { back() }, modifier = padded)
                                is Page.LicenseText -> LicenseTextScreen(p.component, onBack = { back() }, modifier = padded)
                                Page.Weather -> {
                                    DisposableEffect(Unit) {
                                        weather.visible(true)
                                        onDispose { weather.visible(false) }
                                    }
                                    // "Weather" page state, which lives and dies with the page.
                                    val search = remember { WeatherSearch(weather, scope) }
                                    LaunchedEffect(Unit) { search.load() }
                                    val trimmed = query.trim()
                                    LaunchedEffect(search.index, trimmed) { search.update(trimmed) }
                                    // Approximate location only for the weather, requested when "Use my location" is tapped.
                                    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                                        if (granted) search.useLocation() else search.locationRefused()
                                    }
                                    val data by weather.data.collectAsState()
                                    val connected by weather.online.collectAsState()
                                    WeatherScreen(
                                        data = data ?: WeatherData(),
                                        now = now,
                                        query = query,
                                        onQueryChange = { query = it },
                                        local = search.local,
                                        online = search.online,
                                        connected = connected,
                                        location = search.location,
                                        onSearchOnline = { search.searchOnline(trimmed) },
                                        onChoose = { city -> weather.choose(city); back() },
                                        onUseLocation = {
                                            if (weather.hasLocationPermission()) search.useLocation()
                                            else {
                                                systemSettingsOpened = true
                                                locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                            }
                                        },
                                        onRemove = { weather.off() },
                                        onOpenAppSettings = ::openAppSettings,
                                        onBack = { back() },
                                        modifier = padded,
                                    )
                                }
                                Page.AutoSwitch -> {
                                    val wifi = launcher.wifi
                                    DisposableEffect(Unit) {
                                        wifi.acquire()
                                        // Location turned off or back on while "Automatic switching" is open: no network callback reports it.
                                        val locationMode = object : BroadcastReceiver() {
                                            override fun onReceive(context: Context, intent: Intent) { statusTick++ }
                                        }
                                        registerReceiver(locationMode, IntentFilter(LocationManager.MODE_CHANGED_ACTION))
                                        onDispose {
                                            unregisterReceiver(locationMode)
                                            wifi.release()
                                        }
                                    }
                                    val reading by wifi.reading.collectAsState()
                                    val tracker by wifi.tracker.collectAsState()
                                    val status = remember(statusTick, reading, tracker) { wifi.status() }
                                    val detection = config?.detection ?: Detection()
                                    LaunchedEffect(unlinked) { if (unlinked != null) { delay(Tokens.undoMs); unlinked = null } }
                                    AutoSwitchScreen(
                                        detection = detection,
                                        selected = profiles?.selected.orEmpty(),
                                        status = status,
                                        current = reading?.ssid,
                                        denied = locationDenied,
                                        unlinked = unlinked,
                                        onEnabled = { on ->
                                            when {
                                                !on -> store.update("settings.detectionOff") { it.copy(detection = it.detection.withEnabled(false)) }
                                                status == WifiStatus.NO_PERMISSION -> go(Page.Explain)
                                                else -> store.update("settings.detectionOn") { it.copy(detection = it.detection.withEnabled(true)) }
                                            }
                                        },
                                        onUseCurrent = { profile -> reading?.ssid?.let { ssid -> store.update("settings.useNetwork") { it.copy(detection = it.detection.associate(ssid, profile)) } } },
                                        onUnlink = { ssid, profile ->
                                            store.update("settings.dissociate") { it.copy(detection = it.detection.dissociate(ssid)) }
                                            unlinked = Unlinked(ssid, profile)
                                        },
                                        onUndo = {
                                            unlinked?.let { u -> store.update("settings.undoDissociate") { it.copy(detection = it.detection.associate(u.ssid, u.profile)) } }
                                            unlinked = null
                                        },
                                        onLossSeen = { store.update("settings.lossSeen") { it.copy(detection = it.detection.lossSeen()) } },
                                        onOpenLocationSettings = { openSystem(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                                        onOpenAppSettings = ::openAppSettings,
                                        // Only on tap, never by itself: the system offers to upgrade to precise location.
                                        onAllowPrecise = requestLocation,
                                        onBack = { back() },
                                    )
                                }
                                Page.Explain -> ExplainScreen(
                                    onContinue = requestLocation,
                                    onNotNow = { back() },
                                    onBack = { back() },
                                )
                                Page.ClockStyle -> {
                                    WallpaperUnderPreview(transition.fadeThroughAlpha())
                                    ClockStyleScreen(
                                        font = config?.clockFont ?: ClockFont(),
                                        time = formatTime(now),
                                        date = formatDate(now),
                                        onFontChange = { font -> store.update("settings.clockFont") { it.copy(clockFont = font) } },
                                        onBack = { back() },
                                        onPreview = { preview = it },
                                    )
                                }
                                Page.TextOnWallpaper -> {
                                    WallpaperUnderPreview(transition.fadeThroughAlpha())
                                    TextOnWallpaperScreen(
                                        choice = config?.textOnWallpaper ?: TextOnWallpaper.AUTOMATIC,
                                        font = config?.clockFont ?: ClockFont(),
                                        time = formatTime(now),
                                        date = formatDate(now),
                                        onChoice = { choice -> store.update("settings.textOnWallpaper") { it.copy(textOnWallpaper = choice) } },
                                        onBack = { back() },
                                        onPreview = { preview = it },
                                    )
                                }
                                // Same screens as onboarding, on the settings surface.
                                is Page.ProfileSelection -> OnSurface {
                                    ProfileSelectionScreen(
                                        title = stringResource(if (p.enabling) R.string.enable_profiles_title else R.string.change_profiles),
                                        hint = stringResource(if (p.enabling) R.string.enable_profiles_hint else R.string.change_profiles_screen_hint),
                                        initial = profiles?.selected.orEmpty(),
                                        step = null, total = 0,
                                        primary = stringResource(R.string.done),
                                        skip = stringResource(R.string.cancel),
                                        onSkip = { back() },
                                        onConfirm = { choice ->
                                            val queue = store.selectProfiles(choice)
                                            go(if (queue.isEmpty()) Page.Main else Page.ProfileApps(queue))
                                        },
                                        modifier = padded,
                                    )
                                }
                                is Page.ProfileApps -> OnSurface {
                                    val profile = p.queue.first()
                                    val next = { query = ""; go(p.queue.drop(1).let { if (it.isEmpty()) Page.Main else Page.ProfileApps(it) }) }
                                    DockAppsStep(
                                        profile = profile, step = null, total = 0,
                                        initial = emptyList(),
                                        apps = apps.orEmpty(),
                                        suggested = Suggestions.forProfile(profile, apps.orEmpty()) { it.component.packageName },
                                        alphabet = alphabet,
                                        query = query, onQueryChange = { query = it }, color = repository::color,
                                        icon = repository::themedIcon,
                                        excluded = notInFavorites,
                                        onSkip = { store.update("settings.keepDock") { it.keepDock(profile) }; next() },
                                        onDone = { chosen -> store.setDockApps(profile, chosen); next() },
                                        modifier = padded,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        statusTick++
        refreshRole()
        now = shownNow()
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    override fun onResume() {
        super.onResume()
        // Back from the dialog or the system settings: leaving settings will close them again.
        systemSettingsOpened = false
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(timeReceiver)
        if (!isChangingConfigurations && !roleRequested && !systemSettingsOpened) finish()
    }

    /** Wallpaper under a page's preview, with the page's opacity, while it is composed. */
    @Composable
    private fun WallpaperUnderPreview(alpha: State<Float>) {
        DisposableEffect(alpha) {
            previewAlpha = alpha
            showWallpaper(true)
            onDispose {
                previewAlpha = null
                showWallpaper(false)
            }
        }
    }

    /**
     * Wallpaper under the window during the life of a preview page ("Clock style", "Text on wallpaper"), from entry to
     * the end of exit: the system shows and removes it with a delay, while the preview is still covered by the surface
     * (start of the page's fade, end of its exit). Elsewhere, an opaque window: a permanent wallpaper would turn the
     * settings opening animation into a fade over Home.
     */
    private fun showWallpaper(show: Boolean) {
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(themeBackground)
        }
    }

    private fun openAppSettings() = openSystem(appDetailsIntent(packageName))

    private fun openSystem(intent: Intent) {
        systemSettingsOpened = true
        runCatching { startActivity(intent) }.onFailure { systemSettingsOpened = false }
    }

    /** "Automatic switching" summary in "Launcher settings": off, or on with the profile of the known network. */
    @Composable
    private fun autoSummary(config: Config?, profiles: Profiles?): String {
        val detection = config?.detection
        if (detection == null || !detection.enabled) return stringResource(R.string.auto_off)
        val active = profiles?.active
        return if (active != null && active == detection.acknowledged) stringResource(R.string.auto_on_profile, profileLabel(active))
        else stringResource(R.string.auto_on)
    }

    private fun go(to: Page) {
        page = to
    }



    /**
     * Project link in the browser, kept open like the system settings: Back from the browser returns to "About"
     * (otherwise `onStop` closed the settings). Without a browser, nothing.
     */
    private fun openLink(url: String) {
        systemSettingsOpened = true
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.onFailure { systemSettingsOpened = false }
    }

    /**
     * Back from a subpage: "Clock style", "Text on wallpaper", the profile choice, "Automatic switching" and "About"
     * return to settings; the permission explanation to "Automatic switching"; the licenses to "About"; a license text
     * to the licenses.
     */
    private fun back() {
        query = ""
        page = when (page) {
            Page.Explain -> Page.AutoSwitch
            Page.Licenses -> Page.About
            is Page.LicenseText -> Page.Licenses
            else -> Page.Main
        }
    }

    private fun refreshRole() {
        roleHeld = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    companion object {
        private const val EXTRA_WEATHER = "weather"

        /** Settings opened on "Weather", in their own task. */
        fun weatherIntent(context: Context): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_WEATHER, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
