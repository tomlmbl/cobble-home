package io.github.tomlmbl.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.tomlmbl.launcher.R
import io.github.tomlmbl.launcher.catalog.SearchField
import io.github.tomlmbl.launcher.ui.Glyph
import io.github.tomlmbl.launcher.ui.OnSurface
import io.github.tomlmbl.launcher.ui.Tokens
import io.github.tomlmbl.launcher.weather.Cities
import io.github.tomlmbl.launcher.weather.City
import io.github.tomlmbl.launcher.weather.CityIndex
import io.github.tomlmbl.launcher.weather.WeatherData
import io.github.tomlmbl.launcher.weather.WeatherMode
import io.github.tomlmbl.launcher.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Online search: nothing requested, in progress, failed, results (without those already offered by the list). */
sealed interface OnlineSearch {
    data object Idle : OnlineSearch
    data object Running : OnlineSearch
    data object Failed : OnlineSearch
    data class Done(val cities: List<City>) : OnlineSearch
}

/** Result of "Use my location": no known position, or permission denied. */
enum class LocationProblem { UNKNOWN, NOT_ALLOWED }

/**
 * "Weather" page state, for the page's lifetime: bundled list, results for the typed text, online search (cancelled
 * when the text changes) and last result of "Use my location".
 */
@Stable
class WeatherSearch(private val weather: WeatherRepository, private val scope: CoroutineScope) {
    var index by mutableStateOf<CityIndex?>(null)
        private set
    var local by mutableStateOf(emptyList<City>())
        private set
    var online by mutableStateOf<OnlineSearch>(OnlineSearch.Idle)
        private set
    var location by mutableStateOf<LocationProblem?>(null)
        private set
    private var onlineJob: Job? = null

    suspend fun load() {
        index = weather.loadCities()
    }

    /** New text: local search, the previous online search is forgotten. */
    suspend fun update(query: String) {
        onlineJob?.cancel()
        online = OnlineSearch.Idle
        local = index?.let { withContext(Dispatchers.Default) { it.search(query) } }.orEmpty()
    }

    fun searchOnline(query: String) {
        onlineJob?.cancel()
        online = OnlineSearch.Running
        onlineJob = scope.launch {
            online = weather.searchOnline(query)?.let { OnlineSearch.Done(Cities.withoutShown(it, local)) } ?: OnlineSearch.Failed
        }
    }

    fun useLocation() {
        location = null
        scope.launch { if (!weather.useLocation()) location = LocationProblem.UNKNOWN }
    }

    fun locationRefused() {
        location = LocationProblem.NOT_ALLOWED
    }
}

/**
 * "Weather": current city or "No city", "Use my location", then the field at the bottom; typing searches the
 * bundled list ([local]) without sending anything; "Search online" queries Open-Meteo only when tapped. Choosing a
 * city saves it; × removes it and turns the weather off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    data: WeatherData,
    now: Long,
    query: String,
    onQueryChange: (String) -> Unit,
    local: List<City>,
    online: OnlineSearch,
    connected: Boolean,
    location: LocationProblem?,
    onSearchOnline: () -> Unit,
    onChoose: (City) -> Unit,
    onUseLocation: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = OnSurface {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val typing = query.trim().length >= 2
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        // While typing, a small bar: with the keyboard up, the large one hid the first results.
        if (typing) TopAppBar(title = { Text(stringResource(R.string.weather)) }, navigationIcon = { BackButton(onBack) })
        else LargeTopAppBar(
            title = { Text(stringResource(R.string.weather)) },
            navigationIcon = { BackButton(onBack) },
            scrollBehavior = scroll,
        )
        // Results above the field, within thumb reach, as in the catalog.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = typing).padding(horizontal = Tokens.Space.l),
            verticalArrangement = if (typing) Arrangement.Bottom else Arrangement.Top,
        ) {
            if (typing) Results(query.trim(), local, online, connected, onSearchOnline, onChoose)
            else Current(data, now, location, onUseLocation, onRemove, onOpenAppSettings)
        }
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { local.firstOrNull()?.let(onChoose) },
            hint = stringResource(if (data.city == null) R.string.search_city else R.string.search_another_city),
        )
        if (!typing) {
            BasicText(
                stringResource(R.string.weather_attribution),
                style = Tokens.attribution,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.xxl).padding(bottom = Tokens.Space.l),
            )
        }
    }
}

@Composable
private fun Current(
    data: WeatherData,
    now: Long,
    location: LocationProblem?,
    onUseLocation: () -> Unit,
    onRemove: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val city = data.city
    if (city == null) {
        Group(null) { SettingRow(R.drawable.ic_cloud_off, stringResource(R.string.no_city), stringResource(R.string.no_city_hint)) }
    } else {
        Group(stringResource(R.string.weather_city)) {
            val byLocation = data.mode == WeatherMode.LOCATION
            val fetched = data.current?.fetchedAt
            val age = fetched?.let { ((now - it).coerceAtLeast(0) / 60_000L).toInt().let { m -> pluralStringResource(R.plurals.updated_minutes_ago, m, m) } }
                ?: stringResource(R.string.not_loaded_yet)
            val origin = if (byLocation) stringResource(R.string.nearest_to_you) else countryName(city.country)
            val remove = stringResource(R.string.remove_city)
            SettingRow(if (byLocation) R.drawable.ic_my_location else R.drawable.ic_location_on, city.name, "$origin · $age") {
                IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = remove }) {
                    Glyph(R.drawable.ic_close, Tokens.glyphSize, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (data.mode != WeatherMode.LOCATION) {
        Group(null) {
            val hint = when (location) {
                LocationProblem.UNKNOWN -> stringResource(R.string.location_unknown)
                LocationProblem.NOT_ALLOWED -> stringResource(R.string.location_not_allowed)
                null -> stringResource(R.string.use_my_location_hint)
            }
            val action = if (location == LocationProblem.NOT_ALLOWED) onOpenAppSettings else onUseLocation
            SettingRow(
                R.drawable.ic_my_location, stringResource(R.string.use_my_location), hint,
                modifier = Modifier.clickable(role = Role.Button, onClick = action),
            )
        }
    }
    Spacer(Modifier.padding(bottom = Tokens.Space.l))
}

@Composable
private fun Results(
    query: String,
    local: List<City>,
    online: OnlineSearch,
    connected: Boolean,
    onSearchOnline: () -> Unit,
    onChoose: (City) -> Unit,
) {
    if (local.isEmpty()) {
        Group(null) { SettingRow(R.drawable.ic_search_off, stringResource(R.string.no_city_match, query), stringResource(R.string.no_city_match_hint)) }
    } else {
        // Best result closest to the field and the thumb, as in the catalog: it stays visible when the keyboard leaves
        // room for only part of the list.
        Group(null) { local.asReversed().forEach { CityRow(it, onChoose) } }
    }
    when (online) {
        is OnlineSearch.Done -> Group(stringResource(R.string.from_open_meteo)) {
            if (online.cities.isEmpty()) SettingRow(R.drawable.ic_search_off, stringResource(R.string.no_online_match), null)
            else online.cities.take(MAX_ONLINE).asReversed().forEach { CityRow(it, onChoose) }
        }
        else -> Group(null) {
            val running = online == OnlineSearch.Running
            val hint = when {
                !connected -> stringResource(R.string.offline)
                running -> stringResource(R.string.searching)
                online == OnlineSearch.Failed -> stringResource(R.string.search_failed)
                else -> stringResource(R.string.sends_to_open_meteo, query)
            }
            val title = if (connected) stringResource(R.string.search_online_for, query) else stringResource(R.string.search_online)
            val enabled = connected && !running
            SettingRow(
                if (connected) R.drawable.ic_travel_explore else R.drawable.ic_cloud_off, title, hint,
                modifier = Modifier.alpha(if (connected) 1f else Tokens.disabledAlpha)
                    .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onSearchOnline) else Modifier),
            )
        }
    }
    Spacer(Modifier.padding(bottom = Tokens.Space.m))
}

@Composable
private fun CityRow(city: City, onChoose: (City) -> Unit) {
    val place = remember(city) { listOf(city.region, countryName(city.country)).filter { it.isNotEmpty() }.distinct().joinToString(" · ") }
    SettingRow(
        R.drawable.ic_location_on, city.name, place.ifEmpty { null },
        modifier = Modifier.clickable(role = Role.Button) { onChoose(city) },
    )
}

/** Country name in the device language, from the platform. */
private fun countryName(code: String): String =
    runCatching { Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()?.ifEmpty { null } ?: code

private const val MAX_ONLINE = 5
