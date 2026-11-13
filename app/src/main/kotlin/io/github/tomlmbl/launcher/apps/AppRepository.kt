package io.github.tomlmbl.launcher.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withScale
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A launchable activity. Identity: component + user profile. */
class App(
    val id: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    internal val info: LauncherActivityInfo,
    /** Paused profile: the app is dimmed; on tap, the system offers to turn the profile back on. */
    val paused: Boolean = false,
    /** App from a profile other than the main one: glyph after the name, [description] for accessibility. */
    val otherProfile: Boolean = false,
    /** Name read by accessibility services: the label the system gives an app from another profile ("Work Calendar"). */
    val description: String = label,
)

/**
 * Single source of launchable apps, through LauncherApps (no QUERY_ALL_PACKAGES).
 * Reloads the list whenever a package is added, removed or changed, and when the locale changes.
 * Never logs an app name.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** One reload at a time: an older reload cannot finish after a newer one and overwrite its list. */
    private val reloads = Dispatchers.Default.limitedParallelism(1)
    private val icons = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)
    private val masks = LruCache<String, Bitmap>(ICON_CACHE_SIZE)
    private val colors = LruCache<String, Int>(COLOR_CACHE_SIZE)
    private var loading: Job? = null

    private val _apps = MutableStateFlow<List<App>?>(null)

    /** null until the first load has finished. */
    val apps: StateFlow<List<App>?> = _apps

    /**
     * Called when a package is uninstalled, with a predicate on the identity of that package's apps.
     * Never called for an update: LauncherApps then reports onPackageChanged (see the javadoc of
     * LauncherApps.Callback.onPackageRemoved). Used to forget the package's apps in the docks.
     */
    var onUninstalled: ((isFromPackage: (String) -> Boolean) -> Unit)? = null

    /**
     * Called after every reload: an uninstall made while the process was stopped, or a removed profile, has no
     * other signal; the affected dock apps are then forgotten through [isGone].
     */
    var onReloaded: (() -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            val prefix = "${userManager.getSerialNumberForUser(user)}|$packageName/"
            onUninstalled?.invoke { it.startsWith(prefix) }
            changed(packageName)
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::changed)
        override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) =
            packageNames.forEach(::changed)
        override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) =
            packageNames.forEach(::changed)
    }

    private val localeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            icons.evictAll()
            masks.evictAll()
            reload()
        }
    }

    /**
     * Profile paused, resumed, added or removed: no LauncherApps callback reports these; without the added and
     * removed broadcasts, the apps of a deleted profile stayed listed.
     */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        context.registerReceiver(localeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
        context.registerReceiver(profileReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
        })
        reload()
    }

    private fun changed(packageName: String) {
        icons.snapshot().keys.filter { it.contains("|$packageName/") }.forEach { icons.remove(it) }
        masks.snapshot().keys.filter { it.contains("|$packageName/") }.forEach { masks.remove(it) }
        colors.snapshot().keys.filter { it.contains("|$packageName/") }.forEach { colors.remove(it) }
        reload()
    }

    private fun reload() {
        loading?.cancel()
        loading = scope.launch(reloads) {
            val started = System.nanoTime()
            val main = Process.myUserHandle()
            val list = userManager.userProfiles.flatMap { user ->
                val paused = userManager.isQuietModeEnabled(user)
                launcherApps.getActivityList(null, user).map { info ->
                    App(
                        id = idOf(info.componentName, user),
                        label = info.label.toString(),
                        otherProfile = user != main,
                        description = if (user == main) info.label.toString()
                        else context.packageManager.getUserBadgedLabel(info.label, user).toString(),
                        component = info.componentName,
                        user = user,
                        info = info,
                        paused = paused,
                    )
                }
            }
            ensureActive()
            _apps.value = list
            Log.i(TAG, "apps=${list.size} loadMs=${(System.nanoTime() - started) / 1_000_000}")
            onReloaded?.invoke()
        }
    }

    /**
     * App to forget in the docks: its profile no longer exists, or its package is no longer installed in a running,
     * unlocked profile. When in doubt (paused or locked profile, unreadable identity, error), it is kept.
     */
    fun isGone(id: String): Boolean {
        val serial = id.substringBefore('|').toLongOrNull() ?: return false
        val component = ComponentName.unflattenFromString(id.substringAfter('|')) ?: return false
        val user = userManager.getUserForSerialNumber(serial) ?: return true
        if (userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)) return false
        return try {
            launcherApps.getApplicationInfo(component.packageName, 0, user)
            false
        } catch (_: PackageManager.NameNotFoundException) {
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * Themed dock icon: the app's monochrome glyph, scaled down, in the theme's [glyph] color, on a [background]
     * disc ringed by a thin [outline]; the work-profile badge is added by the system. Mask and composition are cached
     * in memory, the composition per color set: a theme or wallpaper change yields a new icon.
     */
    suspend fun themedIcon(app: App, sizePx: Int, glyph: Int, background: Int, outline: Int): ImageBitmap {
        val key = "$sizePx@$glyph@$background@$outline@${app.id}"
        icons.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bitmap = createBitmap(sizePx, sizePx)
            val canvas = Canvas(bitmap)
            val r = sizePx / 2f
            canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
            canvas.withClip(Path().apply { addCircle(r, r, r, Path.Direction.CW) }) {
                // Scaled-down glyph clipped to a circle, so leftovers of a generated mask never draw a square.
                withScale(GLYPH_SCALE, GLYPH_SCALE, r, r) {
                    clipPath(Path().apply { addCircle(r, r, r, Path.Direction.CW) })
                    drawBitmap(mask(app, sizePx), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(glyph, PorterDuff.Mode.SRC_IN) })
                }
            }
            val stroke = context.resources.displayMetrics.density * OUTLINE_DP
            canvas.drawCircle(r, r, r - stroke / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline; style = Paint.Style.STROKE; strokeWidth = stroke })
            val badged = context.packageManager.getUserBadgedIcon(bitmap.toDrawable(context.resources), app.user)
            val result = if (badged is BitmapDrawable && badged.bitmap === bitmap) bitmap else {
                createBitmap(sizePx, sizePx).also { out ->
                    badged.setBounds(0, 0, sizePx, sizePx)
                    badged.draw(Canvas(out))
                }
            }
            result.asImageBitmap().also { icons.put(key, it) }
        }
    }

    /**
     * Glyph mask at the disc's size: the adaptive icon's monochrome layer if it has one; otherwise background and
     * foreground go through [MonochromeMask.fromIcon]; a non-adaptive icon is scaled down and goes through
     * [MonochromeMask.fromLuminance]. An adaptive layer is 108 dp for 72 dp visible: it overflows by a quarter on
     * each side, and the [MonochromeMask.fromIcon] fallback reads its edges before the disc clips them.
     */
    private fun mask(app: App, sizePx: Int): Bitmap {
        val key = "$sizePx@${app.id}"
        masks.get(key)?.let { return it }
        val icon = app.info.getIcon(0)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val inset = sizePx / 4
        val adaptive = icon as? AdaptiveIconDrawable
        val monochrome = adaptive?.monochrome
        when {
            monochrome != null -> {
                monochrome.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                monochrome.draw(canvas)
            }
            adaptive != null -> {
                val full = sizePx + 2 * inset
                val layers = createBitmap(full, full)
                val layersCanvas = Canvas(layers)
                for (layer in listOfNotNull(adaptive.background, adaptive.foreground)) {
                    layer.setBounds(0, 0, full, full)
                    layer.draw(layersCanvas)
                }
                val pixels = IntArray(full * full)
                layers.getPixels(pixels, 0, full, 0, 0, full, full)
                layers.setPixels(MonochromeMask.fromIcon(pixels, full), 0, full, 0, 0, full, full)
                canvas.drawBitmap(layers, -inset.toFloat(), -inset.toFloat(), null)
                layers.recycle()
            }
            else -> {
                val inner = (sizePx * FALLBACK_SCALE).roundToInt()
                val offset = (sizePx - inner) / 2
                icon.setBounds(offset, offset, offset + inner, offset + inner)
                icon.draw(canvas)
                val pixels = IntArray(sizePx * sizePx)
                bitmap.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
                bitmap.setPixels(MonochromeMask.fromLuminance(pixels), 0, sizePx, 0, 0, sizePx, sizePx)
            }
        }
        return bitmap.also { masks.put(key, it) }
    }

    /** Dot color, or null for a neutral dot. Uses the unbadged icon: a badge would skew the hue. */
    suspend fun color(app: App): Int? {
        colors.get(app.id)?.let { return it.takeIf { it != NO_COLOR } }
        return withContext(Dispatchers.Default) {
            val drawable = app.info.getIcon(0)
            val bitmap = createBitmap(COLOR_SAMPLE_PX, COLOR_SAMPLE_PX)
            drawable.setBounds(0, 0, COLOR_SAMPLE_PX, COLOR_SAMPLE_PX)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(COLOR_SAMPLE_PX * COLOR_SAMPLE_PX)
            bitmap.getPixels(pixels, 0, COLOR_SAMPLE_PX, 0, 0, COLOR_SAMPLE_PX, COLOR_SAMPLE_PX)
            bitmap.recycle()
            IconColor.dominant(pixels).also { colors.put(app.id, it ?: NO_COLOR) }
        }
    }

    /** App identity: profile serial number and component. */
    fun idOf(component: ComponentName, user: UserHandle): String =
        "${userManager.getSerialNumberForUser(user)}|${component.flattenToShortString()}"

    fun launch(app: App, sourceBounds: Rect?) {
        try {
            launcherApps.startMainActivity(app.component, app.user, sourceBounds, null)
        } catch (e: RuntimeException) {
            // App uninstalled or disabled between display and tap: the list is about to reload.
            Log.w(TAG, "launch failed: ${e.javaClass.simpleName}")
            reload()
        }
    }

    /** The system "App info" screen. */
    fun openAppInfo(app: App, sourceBounds: Rect?) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, sourceBounds, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "app info failed: ${e.javaClass.simpleName}")
        }
    }

    /** The system uninstall confirmation; requires REQUEST_DELETE_PACKAGES (a normal permission). */
    fun uninstall(app: App) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "uninstall failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "Apps"
        const val ICON_CACHE_SIZE = 400
        const val COLOR_CACHE_SIZE = 1000
        const val COLOR_SAMPLE_PX = 32
        /** Non-adaptive icon fallback: share of the disc it fills. */
        const val FALLBACK_SCALE = 0.6f
        /** Scaled-down dock glyph: 20 in a 56 disc, rather than 30 in 48 in the original design. */
        const val GLYPH_SCALE = (20f / 56f) / (30f / 48f)
        /** Dock disc outline, in dp, drawn inside the disc. */
        const val OUTLINE_DP = 1f
        /** Marks an icon with no clear color; IconColor never produces it (zero alpha). */
        const val NO_COLOR = 0
    }
}
