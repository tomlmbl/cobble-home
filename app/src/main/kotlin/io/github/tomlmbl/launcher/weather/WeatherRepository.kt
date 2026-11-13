package io.github.tomlmbl.launcher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.AtomicFile
import android.util.Log
import io.github.tomlmbl.launcher.BuildConfig
import io.github.tomlmbl.launcher.home.WeatherFeed
import io.github.tomlmbl.launcher.home.GlanceHour
import io.github.tomlmbl.launcher.home.WeatherGlance
import io.github.tomlmbl.launcher.ui.shownNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Weather: chosen city or nearest to the last known position, last value in a private file, refreshed in the
 * foreground only, after 3 h or when the network comes back, within the limits of [RefreshBudget]. The `Weather`
 * log holds states only, never a city, coordinates or typed text.
 */
class WeatherRepository(private val context: Context, private val scope: CoroutineScope) : WeatherFeed {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val fetching = Mutex()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _data = MutableStateFlow<WeatherData?>(null)
    /** null until the file has been read. */
    val data: StateFlow<WeatherData?> = _data.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _unit = MutableStateFlow(temperatureUnit())
    val unit: StateFlow<TemperatureUnit> = _unit.asStateFlow()

    override val glance: StateFlow<WeatherGlance?> = combine(_data, _unit) { data, unit ->
        data?.current?.let { f ->
            WeatherGlance(f.hours.map { GlanceHour(it.time, Sky.of(it.code).icon(it.isDay), unit.format(it.temperature)) }, f.fetchedAt)
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var shown = 0
    private val budget = RefreshBudget()

    // Default network lost: offline until the next one, announced by `onCapabilitiesChanged`. Reading `activeNetwork`
    // in `onLost` still returned the lost network (observed on a Nothing Phone).
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val was = _online.value
            _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (_online.value && !was) refresh()
        }

        override fun onLost(network: Network) {
            _online.value = false
        }
    }

    fun start() {
        // `screenshots` build: sample weather, no file, no network.
        if (BuildConfig.SCREENSHOTS) { _data.value = ScreenshotWeather.data(shownNow()); return }
        scope.launch(io) {
            _data.value = runCatching { WeatherFormat.decode(file.readFully().decodeToString()) }.getOrDefault(WeatherData())
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    /** Home or weather settings in the foreground: the network is tracked and the weather refreshed if needed. */
    override fun visible(visible: Boolean) {
        if (visible) {
            if (shown++ == 0) {
                _online.value = isOnline()
                _unit.value = temperatureUnit()
                runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
            }
            refresh()
        } else if (shown > 0 && --shown == 0) {
            runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        }
    }

    fun choose(city: City) {
        save { it.choose(city) }
        scope.launch(io) { fetch(force = true) }
    }

    fun off() = save { it.off() }

    /**
     * "Use my location": the nearest city, if a position is known; otherwise nothing changes. A request is forced
     * only if the city changes or the weather is missing.
     */
    suspend fun useLocation(): Boolean = withContext(io) {
        val city = nearestCity() ?: return@withContext false
        val before = _data.value
        save { it.located(city) }
        fetch(force = before?.city?.id != city.id || before.current == null)
        true
    }

    fun hasLocationPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Bundled list ready for searching, loaded off the main thread; empty if unreadable. */
    suspend fun loadCities(): CityIndex = withContext(Dispatchers.IO) {
        CityIndex(runCatching { readCities { it.toList() } }.getOrDefault(emptyList()))
    }

    /** Online search; null on failure. */
    suspend fun searchOnline(query: String): List<City>? = withContext(Dispatchers.IO) {
        try {
            OpenMeteo.search(query, Locale.getDefault().language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "search failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** The position is read again only if the weather must be requested: at most once per refresh period. */
    private fun refresh() {
        if (shown == 0 || BuildConfig.SCREENSHOTS) return
        scope.launch(io) {
            val data = _data.value ?: return@launch
            if (data.mode == WeatherMode.OFF || !due(data)) return@launch
            if (data.mode == WeatherMode.LOCATION) nearestCity()?.let { city -> if (city.id != data.city?.id) save { it.located(city) } }
            fetch(force = false)
        }
    }

    private fun due(data: WeatherData): Boolean {
        val now = System.currentTimeMillis()
        return Weather.needsRefresh(data.current?.fetchedAt, now) && budget.allows(now)
    }

    private suspend fun fetch(force: Boolean) {
        fetching.withLock {
            val data = _data.value ?: return
            val city = data.city ?: return
            if (BuildConfig.SCREENSHOTS || (!force && !due(data))) return
            if (!isOnline()) { _online.value = false; return }
            val now = System.currentTimeMillis()
            if (!force) budget.sent(now)
            val forecast = try {
                withContext(Dispatchers.IO) { OpenMeteo.forecast(city, now) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                budget.failed(now)
                Log.i(TAG, "refresh failed: ${e.javaClass.simpleName}")
                return
            }
            budget.succeeded()
            save { if (it.city?.id == forecast.cityId) it.copy(forecast = forecast) else it }
            Log.i(TAG, "refreshed")
        }
    }

    private fun save(transform: (WeatherData) -> WeatherData) {
        _data.update { it?.let(transform) }
        // Sample weather never replaces the file of the build installed afterwards.
        if (BuildConfig.SCREENSHOTS) return
        scope.launch(io) {
            val next = _data.value ?: return@launch
            val out = file.startWrite()
            runCatching { out.write(WeatherFormat.encode(next).encodeToByteArray()); file.finishWrite(out) }.onFailure { file.failWrite(out) }
        }
    }

    /** The system's last known position, in the foreground, without a location request; never kept. */
    private fun nearestCity(): City? {
        // Checked here: lint does not follow `hasLocationPermission`.
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val last = runCatching {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
        }.getOrNull() ?: run { Log.i(TAG, "no known location"); return null }
        return runCatching { readCities { Cities.nearest(it, last.latitude, last.longitude) } }
            .onFailure { Log.i(TAG, "city list unreadable: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    private fun <T> readCities(block: (Sequence<City>) -> T): T =
        context.assets.open(CITIES).bufferedReader().useLines { lines -> block(lines.mapNotNull(Cities::decode)) }

    private fun isOnline() = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private companion object {
        const val TAG = "Weather"
        const val FILE_NAME = "weather.txt"
        /** GeoNames list as text; the APK compresses it (`tools/cities/reduce.py`). */
        const val CITIES = "cities.tsv"
    }
}

/** °C or °F from the system's regional preference, then the country. */
fun temperatureUnit(): TemperatureUnit {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    return TemperatureUnit.of(locale.getUnicodeLocaleType("mu"), locale.country)
}
