package io.github.tomlmbl.launcher.home

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import io.github.tomlmbl.launcher.settings.Config
import io.github.tomlmbl.launcher.settings.TextOnWallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "Home"

/**
 * Color of text over the wallpaper: true for dark text. The only source of `darkText`, which drives Home's text, the
 * status bar icons, the veils and the dock disc. The user's choice ([TextOnWallpaper]) wins; in Automatic, a rule on
 * the wallpaper hints. Until the configuration is read: Automatic.
 */
class WallpaperTone(context: Context, private val scope: CoroutineScope, config: StateFlow<Config?>) {

    private val manager = WallpaperManager.getInstance(context)
    /** System wallpaper hints; `null` without colors (a live wallpaper that gives none) or before reading. */
    private val hints = MutableStateFlow<Int?>(null)
    val darkText: StateFlow<Boolean> = combine(config, hints) { c, h -> darkText(c?.textOnWallpaper ?: TextOnWallpaper.AUTOMATIC, h) }
        .distinctUntilChanged()
        .onEach { Log.i(TAG, "wallpaper darkText=$it") }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun start() {
        manager.addOnColorsChangedListener(
            { colors, which -> if (which and WallpaperManager.FLAG_SYSTEM != 0) hints.value = read(colors) },
            Handler(Looper.getMainLooper()),
        )
        // Initial read off the main thread: a call to the wallpaper service.
        scope.launch { hints.value = read(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }
    }

    // The log writes hints only, never a color.
    private fun read(colors: WallpaperColors?) =
        colors?.colorHints.also { Log.i(TAG, "wallpaper hints=$it colors=${colors != null}") }

    companion object {
        /**
         * Automatic rule: dark text unless the dark theme hint is set (mean luminance < 0.30, system dim included);
         * without colors, light text. Light text and Dark text override it.
         */
        fun darkText(choice: TextOnWallpaper, hints: Int?): Boolean = when (choice) {
            TextOnWallpaper.LIGHT -> false
            TextOnWallpaper.DARK -> true
            TextOnWallpaper.AUTOMATIC -> hints != null && hints and WallpaperColors.HINT_SUPPORTS_DARK_THEME == 0
        }
    }
}

/**
 * Opens the notification shade through the non-public API `StatusBarManager.expandNotificationsPanel()`.
 * On failure, does nothing: no message, the user keeps the system gesture from the top of the screen.
 */
fun expandNotificationShade(context: Context) {
    try {
        val service = context.getSystemService("statusbar") ?: return
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
    } catch (e: Throwable) {
        Log.w(TAG, "shade unavailable: ${e.javaClass.simpleName}")
    }
}

/** Tapping the clock opens the system Clock app; nothing if none handles it. */
fun openClockApp(context: Context) = startQuietly(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

/** System wallpaper picker. */
fun openWallpaperPicker(context: Context) = startQuietly(context, Intent(Intent.ACTION_SET_WALLPAPER))

/** The app's page in system settings (calendar, location). */
fun appDetailsIntent(packageName: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Opens [intent]; nothing but a log line if no app handles it. Shared with the panel. */
fun startQuietly(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "no handler: ${intent.action}")
    }
}
