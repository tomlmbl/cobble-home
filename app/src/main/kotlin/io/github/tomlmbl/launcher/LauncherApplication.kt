package io.github.tomlmbl.launcher

import android.app.Application
import io.github.tomlmbl.launcher.apps.AppRepository
import io.github.tomlmbl.launcher.calendar.CalendarRepository
import io.github.tomlmbl.launcher.context.AutoSwitch
import io.github.tomlmbl.launcher.home.EventFeed
import io.github.tomlmbl.launcher.home.GlancePanel
import io.github.tomlmbl.launcher.home.WallpaperTone
import io.github.tomlmbl.launcher.home.WeatherFeed
import io.github.tomlmbl.launcher.panel.Panel
import io.github.tomlmbl.launcher.settings.ConfigStore
import io.github.tomlmbl.launcher.settings.MeasureStore
import io.github.tomlmbl.launcher.signals.Detector
import io.github.tomlmbl.launcher.signals.WifiSignal
import io.github.tomlmbl.launcher.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob

/** Manual dependency wiring (no injection framework). */
class LauncherApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var apps: AppRepository
        private set
    lateinit var config: ConfigStore
        private set
    lateinit var wallpaper: WallpaperTone
        private set
    lateinit var wifi: WifiSignal
        private set
    lateinit var autoSwitch: AutoSwitch
        private set
    lateinit var weather: WeatherRepository
        private set
    /** What Home sees of the weather. */
    val weatherFeed: WeatherFeed get() = weather
    lateinit var calendar: CalendarRepository
        private set
    /** What Home sees of the calendar. */
    val eventFeed: EventFeed get() = calendar
    /** Weather and calendar panel, Home's only entry into `weather` and `calendar` besides its glance lines. */
    lateinit var panel: GlancePanel
        private set

    override fun onCreate() {
        super.onCreate()
        apps = AppRepository(this)
        config = ConfigStore(this, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        // An uninstalled dock app is forgotten; a favorite keeps its place, grayed out.
        apps.onUninstalled = { isFromPackage -> config.update("uninstalled") { it.forgetInDocks(isFromPackage) } }
        apps.onReloaded = { config.update("gone") { it.forgetInDocks(apps::isGone) } }
        // Started after the callbacks are wired: the first reload forgets uninstalled dock apps.
        apps.start()
        wallpaper = WallpaperTone(this, scope, config.config).also { it.start() }
        // Automatic switching: connections tracked for the life of the process, without location.
        wifi = WifiSignal(this).also { it.start() }
        autoSwitch = Detector(config, wifi, MeasureStore(this, scope), MainScope()).also { it.start() }
        // Weather: no request without a city, refreshed in the foreground only.
        weather = WeatherRepository(this, scope).also { it.start() }
        // Calendar: read only with Home visible and the permission granted, never stored.
        calendar = CalendarRepository(this, scope)
        panel = Panel(weather, calendar)
    }
}
